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
    public final static int TYPE_DATA = 2;
    public final static int TYPE_MSGSENT = 3;

    private int    direction;
    private String deviceName;
    private String text;
    private String timeStr;
    private int msgType;
    private int dataLen;

    public int getDataLen() {
        return dataLen;
    }

    public void setDataLen(int dataLen) {
        this.dataLen = dataLen;
    }

    public int getMsgType() {
        return msgType;
    }

    public void setMsgType(int msgType) {
        this.msgType = msgType;
    }



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
