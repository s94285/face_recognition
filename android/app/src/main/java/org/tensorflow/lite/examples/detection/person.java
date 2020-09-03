package org.tensorflow.lite.examples.detection;

public class person {
    private int id;
    private int image;
    private String name;

    public person() {
        super();
    }

    public person(int image, String name) {
        super();
        this.image = image;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getImage() {
        return image;
    }

    public void setImage(int image) {
        this.image = image;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
