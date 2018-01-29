package com.bridgefy.samples.chat.entities;

import com.google.gson.Gson;

/**
 * @author dekaru on 5/9/17.
 */

public class Message {

    public final static int INCOMING_MESSAGE = 0;
    public final static int OUTGOING_MESSAGE = 1;

    public final static int TYPE_MESSAGE = 0;
    public final static int TYPE_FILE = 1;

    private int    direction;
    private String deviceName;
    private String text;
    private String timeStr;
    private String fileName;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getFileDataLen() {
        return fileDataLen;
    }

    public void setFileDataLen(int fileDataLen) {
        this.fileDataLen = fileDataLen;
    }

    private int fileDataLen;

    public Message(String text) {
        this.text = text;
    }

    public String getTimeStr() { return timeStr;}

    public void setTimeStr(String timeStr) { this.timeStr = timeStr;}

    public int getDirection() {
        return direction;
    }

    public void setDirection(int direction) {
        this.direction = direction;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getText() {
        return text;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }


    public static Message create(String json) {
        return new Gson().fromJson(json, Message.class);
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
