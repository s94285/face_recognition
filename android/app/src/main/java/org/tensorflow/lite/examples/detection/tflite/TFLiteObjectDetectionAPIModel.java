/* Copyright 2019 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package org.tensorflow.lite.examples.detection.tflite;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Environment;
import android.os.Trace;
import android.util.Log;
import android.util.Pair;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.examples.detection.env.Logger;
import org.tensorflow.lite.nnapi.NnApiDelegate;

/**
 * Wrapper for frozen detection models trained using the Tensorflow Object Detection API:
 * - https://github.com/tensorflow/models/tree/master/research/object_detection
 * where you can find the training code.
 *
 * To use pretrained models in the API or convert to TF Lite models, please see docs for details:
 * - https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/detection_model_zoo.md
 * - https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/running_on_mobile_tensorflowlite.md#running-our-model-on-android
 */
public class TFLiteObjectDetectionAPIModel
        implements SimilarityClassifier {

  private static final Logger LOGGER = new Logger();

  //private static final int OUTPUT_SIZE = 512;
  private static final int OUTPUT_SIZE = 192;

  // Only return this many results.
  private static final int NUM_DETECTIONS = 1;

  // Float model
  private static final float IMAGE_MEAN = 128.0f;
  private static final float IMAGE_STD = 128.0f;

  // Number of threads in the java app
  private static final int NUM_THREADS = 4;
  private boolean isModelQuantized;
  // Config values.
  private int inputSize;
  // Pre-allocated buffers.
  private Vector<String> labels = new Vector<String>();
  private int[] intValues;
  // outputLocations: array of shape [Batchsize, NUM_DETECTIONS,4]
  // contains the location of detected boxes
  private float[][][] outputLocations;
  // outputClasses: array of shape [Batchsize, NUM_DETECTIONS]
  // contains the classes of detected boxes
  private float[][] outputClasses;
  // outputScores: array of shape [Batchsize, NUM_DETECTIONS]
  // contains the scores of detected boxes
  private float[][] outputScores;
  // numDetections: array of shape [Batchsize]
  // contains the number of detected boxes
  private float[] numDetections;

  private float[][] embeedings;

  private ByteBuffer imgData;

  private Interpreter tfLite;

  // use interpreter options to init interpreter
  private final Interpreter.Options tfliteOptions = new Interpreter.Options();

// Face Mask Detector Output
  private float[][] output;
  private final String root =
          Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "tensorflow/register.ser";
  private HashMap<String, Recognition> registered = new HashMap<>();
  public void register(String name, Recognition rec) {
    registered.put(name, rec);
    try {
      FileOutputStream fileOut =
              new FileOutputStream(root);
      ObjectOutputStream out = new ObjectOutputStream(fileOut);
      out.writeObject(registered);
      out.close();
      fileOut.close();
      System.out.printf("Serialized data is saved in storage/emulated/0/tensorflow/register.ser");
    } catch (IOException i) {
      i.printStackTrace();
    }
  }

  private TFLiteObjectDetectionAPIModel() {
    try {
      FileInputStream fileIn = new FileInputStream(root);
      ObjectInputStream in = new ObjectInputStream(fileIn);
      registered = (HashMap<String, Recognition>) in.readObject();
      in.close();
      fileIn.close();
    } catch (IOException i) {
      i.printStackTrace();
      return;
    } catch (ClassNotFoundException c) {
      System.out.println("file not found");
      c.printStackTrace();
      return;
    }
  }

  /** Memory-map the model file in Assets. */
  private static MappedByteBuffer loadModelFile(AssetManager assets, String modelFilename)
      throws IOException {
    AssetFileDescriptor fileDescriptor = assets.openFd(modelFilename);
    FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
    FileChannel fileChannel = inputStream.getChannel();
    long startOffset = fileDescriptor.getStartOffset();
    long declaredLength = fileDescriptor.getDeclaredLength();
    return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
  }

  /**
   * Initializes a native TensorFlow session for classifying images.
   *
   * @param assetManager The asset manager to be used to load assets.
   * @param modelFilename The filepath of the model GraphDef protocol buffer.
   * @param labelFilename The filepath of label file for classes.
   * @param inputSize The size of image input
   * @param isQuantized Boolean representing model is quantized or not
   */
  public static SimilarityClassifier create(
      final AssetManager assetManager,
      final String modelFilename,
      final String labelFilename,
      final int inputSize,
      final boolean isQuantized)
      throws IOException {

    final TFLiteObjectDetectionAPIModel d = new TFLiteObjectDetectionAPIModel();

    String actualFilename = labelFilename.split("file:///android_asset/")[1];
    Log.d("ActualFilename",actualFilename);
    InputStream labelsInput = assetManager.open(actualFilename);
    BufferedReader br = new BufferedReader(new InputStreamReader(labelsInput));
    String line;

    while ((line = br.readLine()) != null) {
      Log.d("name",line);
      d.labels.add(line);
    }
    br.close();

    d.inputSize = inputSize;
    d.tfliteOptions.setNumThreads(NUM_THREADS);
//    d.tfliteOptions.addDelegate(new NnApiDelegate());
    d.tfliteOptions.setUseNNAPI(false);
    Log.d("TFLiteObjectDetection","Use NNAPI");

    try {
      //TODO: Change Interpreter Constructor Function
//      d.tfLite = new Interpreter(loadModelFile(assetManager, modelFilename),d.tfliteOptions);
      d.tfLite = new Interpreter(loadModelFile(assetManager, modelFilename));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

//    d.tfLite.modifyGraphWithDelegate();
    d.isModelQuantized = isQuantized;
    // Pre-allocate buffers.
    int numBytesPerChannel;
    if (isQuantized) {
      numBytesPerChannel = 1; // Quantized
    } else {
      numBytesPerChannel = 4; // Floating point
    }
    d.imgData = ByteBuffer.allocateDirect(1 * d.inputSize * d.inputSize * 3 * numBytesPerChannel);
    d.imgData.order(ByteOrder.nativeOrder());
    d.intValues = new int[d.inputSize * d.inputSize];

//    d.tfLite.setNumThreads(NUM_THREADS);
    d.outputLocations = new float[1][NUM_DETECTIONS][4];
    d.outputClasses = new float[1][NUM_DETECTIONS];
    d.outputScores = new float[1][NUM_DETECTIONS];
    d.numDetections = new float[1];
    return d;
  }

  // looks for the nearest embeeding in the dataset (using L2 norm)
  // and retrurns the pair <id, distance>
  private Pair<String, Float> findNearest(float[] emb) {

    Pair<String, Float> ret = null;
    for (Map.Entry<String, Recognition> entry : registered.entrySet()) {
        final String name = entry.getKey();
        final float[] knownEmb = ((float[][]) entry.getValue().getExtra())[0];

        float distance = 0;
        for (int i = 0; i < emb.length; i++) {
              float diff = emb[i] - knownEmb[i];
              distance += diff*diff;
        }
        distance = (float) Math.sqrt(distance);
        if (ret == null || distance < ret.second) {
            ret = new Pair<>(name, distance);
        }
    }

    return ret;

  }

  @Override
  public List<Recognition> recognizeImage(final Bitmap bitmap, boolean storeExtra) {
    // Log this method so that it can be analyzed with systrace.
    Trace.beginSection("recognizeImage");

    Trace.beginSection("preprocessBitmap");
    // Preprocess the image data from 0-255 int to normalized float based
    // on the provided parameters.
    bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

    imgData.rewind();
    for (int i = 0; i < inputSize; ++i) {
      for (int j = 0; j < inputSize; ++j) {
        int pixelValue = intValues[i * inputSize + j];
        if (isModelQuantized) {
          // Quantized model
          imgData.put((byte) ((pixelValue >> 16) & 0xFF));
          imgData.put((byte) ((pixelValue >> 8) & 0xFF));
          imgData.put((byte) (pixelValue & 0xFF));
        } else { // Float model
          imgData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
          imgData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
          imgData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
        }
      }
    }
    Trace.endSection(); // preprocessBitmap

    // Copy the input data into TensorFlow.
    Trace.beginSection("feed");


    Object[] inputArray = {imgData};

    Trace.endSection();

// Here outputMap is changed to fit the Face Mask detector
    Map<Integer, Object> outputMap = new HashMap<>();

    embeedings = new float[1][OUTPUT_SIZE];
    outputMap.put(0, embeedings);


    // Run the inference call.
    Trace.beginSection("run");
    //tfLite.runForMultipleInputsOutputs(inputArray, outputMapBack);
    tfLite.runForMultipleInputsOutputs(inputArray, outputMap);
    Trace.endSection();

    String res = "[";
    for (int i = 0; i < embeedings[0].length; i++) {
      res += embeedings[0][i];
      if (i < embeedings[0].length - 1) res += ", ";
    }
    res += "]";
    Log.d("embedding",res);


    float distance = Float.MAX_VALUE;
    String id = "0";
    String label = "?";

    if (registered.size() > 0) {
        //LOGGER.i("dataset SIZE: " + registered.size());
        final Pair<String, Float> nearest = findNearest(embeedings[0]);
        if (nearest != null) {

            final String name = nearest.first;
            label = name;
            distance = nearest.second;

            LOGGER.i("nearest: " + name + " - distance: " + distance);
        }
    }


    final int numDetectionsOutput = 1;
    final ArrayList<Recognition> recognitions = new ArrayList<>(numDetectionsOutput);
    Recognition rec = new Recognition(
            id,
            label,
            distance,
            new RectF());

    recognitions.add( rec );

    if (storeExtra) {
        rec.setExtra(embeedings);
    }

    Trace.endSection();
    return recognitions;
  }

  @Override
  public void enableStatLogging(final boolean logStats) {}

  @Override
  public String getStatString() {

    return "";
  }

  @Override
  public String[] getName() {
    Iterator iterator = registered.keySet().iterator();
    List<String> list = new ArrayList<String>();
    while (iterator.hasNext()){
      String key = (String)iterator.next();
      System.out.println(key+"="+registered.get(key));
      list.add(key);
    }
    String[] name = list.toArray(new String[0]);
    return name;
  }
  public void del(String which){
    registered.remove(which);
    try {
      FileOutputStream fileOut =
              new FileOutputStream(root);
      ObjectOutputStream out = new ObjectOutputStream(fileOut);
      out.writeObject(registered);
      out.close();
      fileOut.close();
      System.out.printf("Serialized data is saved in storage/emulated/0/tensorflow/register.ser");
    } catch (IOException i) {
      i.printStackTrace();
    }
  }

  @Override
  public void setNumThreads(int num_threads) {
    if (tfLite != null) tfLite.setNumThreads(num_threads);
  }

  @Override
  public void setUseNNAPI(boolean isChecked) {
    Log.d("TFLiteObjectDetection","setUseNNAPI");
    //TODO: Make use of NNAPI
    if (tfLite != null) tfLite.setUseNNAPI(isChecked);
  }
}
