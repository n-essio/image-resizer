package it.ness.alexander.first.model.pojo;

public class ImageEvent {

    private String uuid;

    private String format;

    public ImageEvent(String uuid, String format) {
        this.uuid = uuid;
        this.format = format;
    }

    public String getUuid() {
        return uuid;
    }

    public String getFormat() {
        return format;
    }
}