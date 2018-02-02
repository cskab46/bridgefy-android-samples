package com.bridgefy.samples.chat.service;

import android.app.Service;
import android.content.Intent;
import android.os.ConditionVariable;
import android.os.IBinder;
import android.util.Log;

import com.bridgefy.samples.chat.entities.MessageEvent;
import com.bridgefy.sdk.client.BFEngineProfile;
import com.bridgefy.sdk.client.Bridgefy;
import com.bridgefy.sdk.client.Message;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by Administrator on 2018/2/2 0002.
 */

public class SendQueue extends Service {

    private ConditionVariable msgSentflag =  new  ConditionVariable();
    private Queue<com.bridgefy.sdk.client.Message> queue = new LinkedBlockingQueue<>();
    private Boolean myflags = false;
    private String data = "data";
    private static final String TAG = "SendQueue";

    @Override
    public IBinder onBind(Intent intent) {
        return new Binder();
    }

    public class Binder extends android.os.Binder{
        public void setData(String data){
            SendQueue.this.data = data;
        }
        public SendQueue getSendQueue(){
            return SendQueue.this;
        }
    }

    public boolean addMessageToSendQueue(Message msg) {
        return queue.offer(msg);
    }

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate");
        super.onCreate();
        EventBus.getDefault().register(this);

        new Thread(){
            @Override
            public void run() {
                super.run();
                myflags = true;
                while(myflags){
                    com.bridgefy.sdk.client.Message message = queue.poll();
                    if (message != null) {
                        message.setDateSent(System.currentTimeMillis());
                        if (message.getReceiverId() != null) {
                            Bridgefy.sendMessage(message,
                                    BFEngineProfile.BFConfigProfileLongReach);
                        } else {
                            Bridgefy.sendBroadcastMessage(message,
                                    BFEngineProfile.BFConfigProfileLongReach);
                            Log.i(TAG, "message sent start! time used: " + System.currentTimeMillis());
                        }
                    }
                    msgSentflag.block(3000);
                    msgSentflag.close();
                }
                Log.d(TAG, "Service down!");
            }

        }.start();
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand");
        data = intent.getStringExtra("data");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");
        super.onDestroy();
        myflags = false;
        if(EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void Event(MessageEvent messageEvent) {
        msgSentflag.open();
    }

}
