package org.tensorflow.lite.examples.detection;

import androidx.recyclerview.widget.RecyclerView;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.tensorflow.lite.examples.detection.dummy.DummyContent.DummyItem;
import org.tensorflow.lite.examples.detection.tflite.SimilarityClassifier;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.List;

import static org.tensorflow.lite.examples.detection.R.drawable.ic_baseline_switch_camera_24;

/**
 * {@link RecyclerView.Adapter} that can display a {@link DummyItem}.
 * TODO: Replace the implementation with code for your data type.
 */
public class MyItemRecyclerViewAdapter extends RecyclerView.Adapter<MyItemRecyclerViewAdapter.ViewHolder> {
    final String root =
            Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "tensorflow/bmp";
    final File myDir = new File(root);
    private final List<DummyItem> mValues;
    private SimilarityClassifier detector;
    public MyItemRecyclerViewAdapter(List<DummyItem> items,SimilarityClassifier dec) {
        mValues = items;
        detector=dec;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.fragment_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        holder.mIdView.setText(mValues.get(position).id);
//        holder.mContentView.setText(mValues.get(position).content);
//        holder.imageView.setImageResource(ic_baseline_switch_camera_24);

        holder.imageView.setImageBitmap(mValues.get(position).bmp);
        holder.btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 按下Button要做的事
                Log.d("press", String.valueOf(holder.getAdapterPosition()));
                detector.del(mValues.get(holder.getAdapterPosition()).id);
//                String fname = mValues.get(position).id+".png";
                String fname=mValues.get(holder.getAdapterPosition()).id+".png";
                Log.d("file_name",myDir+fname);
                File file = new File(myDir, fname);
                file.delete();
                removeItem(holder.getAdapterPosition());


            }
        });



    }
    public void removeItem(int position){
        mValues.remove(position);
        notifyItemRemoved(position);
    }
    @Override
    public int getItemCount() {
        return mValues.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public final ImageView imageView;
        public final TextView mIdView;
//        public final TextView mContentView;
        public DummyItem mItem;
        public final Button btn;

        public ViewHolder(View view) {
            super(view);
            mView = view;
            imageView = (ImageView)view.findViewById(R.id.imageView);
            mIdView = (TextView) view.findViewById(R.id.item_number);
//            mContentView = (TextView) view.findViewById(R.id.content);
            btn =(Button)view.findViewById(R.id.button2);

        }
        @Override
        public String toString() {
            return super.toString() + " '" ;
        }
    }
}