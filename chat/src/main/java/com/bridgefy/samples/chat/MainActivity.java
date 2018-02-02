package com.bridgefy.samples.chat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.bridgefy.samples.chat.entities.MessageEvent;
import com.bridgefy.samples.chat.entities.Peer;
import com.bridgefy.samples.chat.service.SendQueue;
import com.bridgefy.sdk.client.Bridgefy;
import com.bridgefy.sdk.client.BridgefyClient;
import com.bridgefy.sdk.client.Device;
import com.bridgefy.sdk.client.Message;
import com.bridgefy.sdk.client.MessageListener;
import com.bridgefy.sdk.client.RegistrationListener;
import com.bridgefy.sdk.client.Session;
import com.bridgefy.sdk.client.StateListener;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements ServiceConnection {

    private String TAG = "MainActivity";

    static final String INTENT_EXTRA_NAME = "peerName";
    static final String INTENT_EXTRA_UUID = "peerUuid";
    static final String INTENT_EXTRA_TYPE = "deviceType";
    static final String INTENT_EXTRA_MSG  = "message";
    static final String INTENT_EXTRA_MSGTYPE = "messageType";
    static final String INTENT_EXTRA_MSGSENDTS = "sendtimestamp";
    static final String INTENT_EXTRA_DATALEN = "DataLen";
    static final String BROADCAST_CHAT    = "Broadcast";

    private Intent sendQueueIntent;
    private SendQueue.Binder sendQueueBinder;

    PeersRecyclerViewAdapter peersAdapter =
            new PeersRecyclerViewAdapter(new ArrayList<Peer>());


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.i("MainActivity", "onCreate ");
        // Configure the Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(getTitle());

        RecyclerView recyclerView = findViewById(R.id.peer_list);
        recyclerView.setAdapter(peersAdapter);

        sendQueueIntent = new Intent(MainActivity.this, SendQueue.class);
        bindService(sendQueueIntent, this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Log.i(TAG, "onServiceConnected");
        Bridgefy.initialize(getApplicationContext(), new RegistrationListener() {
            @Override
            public void onRegistrationSuccessful(BridgefyClient bridgefyClient) {
                // Start Bridgefy
                Toast.makeText(getBaseContext(), "registration successful!",
                        Toast.LENGTH_LONG).show();
                startBridgefy();
            }

            @Override
            public void onRegistrationFailed(int errorCode, String message) {
                Toast.makeText(getBaseContext(), getString(R.string.registration_error),
                        Toast.LENGTH_LONG).show();
            }
        });

        sendQueueBinder = (SendQueue.Binder) service;
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        Log.i(TAG, "onServiceDisconnected");
        Bridgefy.stop();
    }

//    private Handler hander = new Handler(){
//        @Override
//        public void handleMessage(Message msg) {
//            super.handleMessage(msg);
//            textView.setText(msg.getData().getString("data"));
//        }
//    };

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (isFinishing())
            Log.i(TAG, "onDestroy");
            Bridgefy.stop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_broadcast:
                startActivity(new Intent(getBaseContext(), ChatActivity.class)
                        .putExtra(INTENT_EXTRA_NAME, BROADCAST_CHAT)
                        .putExtra(INTENT_EXTRA_UUID, BROADCAST_CHAT));
                return true;
        }
        return false;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }


    /**
     *      BRIDGEFY METHODS
     */
    private void startBridgefy() {
        Bridgefy.start(messageListener, stateListener);
    }


    private MessageListener messageListener = new MessageListener() {
        @Override
        public void onMessageReceived(Message message) {
            // direct messages carrying a Device name represent device handshakes
            int msgType = -1;
            msgType = extractBridgefyMsgType(message);
            if (msgType == -1) {
                Log.w(TAG, "onMessageReceived: msgtype: " + msgType);
                return;
            }

            if (message.getContent().get("device_name") != null) {
                Peer peer = new Peer(message.getSenderId(),
                        (String) message.getContent().get("device_name"));
                peer.setNearby(true);
                peer.setDeviceType(extractType(message));
                peersAdapter.addPeer(peer);
                Toast.makeText(getApplicationContext(), "device " + message.getContent().get("device_name") +" connected!", Toast.LENGTH_LONG).show();
            // any other direct message should be treated as such
            } else {
                String incomingMessage = (String) message.getContent().get("text");
                LocalBroadcastManager.getInstance(getBaseContext()).sendBroadcast(
                        new Intent(message.getSenderId())
                                .putExtra(INTENT_EXTRA_MSG, incomingMessage)
                                .putExtra(INTENT_EXTRA_MSGTYPE, msgType));
            }
        }

        @Override
        public void onMessageSent(Message message) {
            super.onMessageSent(message);
//            Intent intent;
            if (message.getSenderId() == null) {
                Log.i(TAG, "message sent ! time used: " + (System.currentTimeMillis() - message.getDateSent()));
                EventBus.getDefault().post(new MessageEvent("open!"));
//                intent = new Intent(BROADCAST_CHAT);
//                intent.putExtra(INTENT_EXTRA_MSGTYPE, com.bridgefy.samples.chat.entities.Message.TYPE_MSGSENT);
//                LocalBroadcastManager.getInstance(getBaseContext()).sendBroadcast(intent);
            }
// else {
//                intent = new Intent(message.getSenderId());
//                intent.putExtra(INTENT_EXTRA_MSGTYPE, com.bridgefy.samples.chat.entities.Message.TYPE_MSGSENT);
//            }

        }

        @Override
        public void onMessageDataProgress(UUID message, long progress, long fullSize) {
            super.onMessageDataProgress(message, progress, fullSize);
        }

        @Override
        public void onBroadcastMessageReceived(Message message) {
            // we should not expect to have connected previously to the device that originated
            // the incoming broadcast message, so device information is included in this packet
            String deviceName  = null;
            String incomingMsg = null;
            int msgType = -1;
            byte[] data = null;
            String fileName = null;
            int dataLen = -1;

            Peer.DeviceType deviceType = Peer.DeviceType.ANDROID;
            Long sendTimestamp = message.getDateSent();
            Intent intent = new Intent(BROADCAST_CHAT);

            deviceName = (String) message.getContent().get("device_name");
            msgType = extractBridgefyMsgType(message);;
            if (deviceName == null || msgType == -1) {
                Log.w(TAG, "onBroadcastMessageReceived: msgtype: " + msgType
                        + "deviceName: " + (deviceName.isEmpty() ? "NULL" : deviceName));
                return;
            }
            if (msgType == com.bridgefy.samples.chat.entities.Message.TYPE_FILE) {
                data = (byte[]) message.getContent().get("Data");
                fileName = (String) message.getContent().get("text");
                if (data == null || fileName == null) {
                    Log.w(TAG, "onBroadcastMessageReceived: "
                            + "fileName: " + (fileName.isEmpty() ? "NULL" : fileName)
                            + "fileData: " + (data == null ? "NULL" : data.length));
                    return;
                }

                //save file to local filesystem
                File file = new File(Environment.getExternalStorageDirectory()
                        .getAbsolutePath() + File.separator + fileName);
                FileOutputStream fos = null;
                try {
                    fos = new FileOutputStream(file);
                    fos.write(data);
                    fos.close();
                    dataLen = data.length;
                } catch (IOException e) {
                    e.printStackTrace();
                    if (fos != null) {
                        try {
                            fos.close();
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                    }
                    Log.w(TAG, "onBroadcastMessageReceived: save file error!");
                    return;
                }

                intent.putExtra(INTENT_EXTRA_MSG, fileName)
                        .putExtra(INTENT_EXTRA_DATALEN, dataLen);
            } else if (msgType == com.bridgefy.samples.chat.entities.Message.TYPE_MESSAGE){
                incomingMsg = (String) message.getContent().get("text");
                intent.putExtra(INTENT_EXTRA_MSG, incomingMsg);
            } else if (msgType == com.bridgefy.samples.chat.entities.Message.TYPE_DATA) {
                incomingMsg = (String) message.getContent().get("text");
                data = (byte[]) message.getContent().get("Data");
                dataLen = data.length;
                intent.putExtra(INTENT_EXTRA_MSG, incomingMsg)
                        .putExtra(INTENT_EXTRA_DATALEN, dataLen);
            } else {
                Log.d(TAG, "onBroadcastMessageReceived: msgtype " + msgType);
                return;
            }

            intent.putExtra(INTENT_EXTRA_NAME, deviceName)
                    .putExtra(INTENT_EXTRA_TYPE, deviceType)
                    .putExtra(INTENT_EXTRA_MSGSENDTS, sendTimestamp)
                    .putExtra(INTENT_EXTRA_MSGTYPE, msgType);

            LocalBroadcastManager.getInstance(getBaseContext()).sendBroadcast(intent);
        }
    };

    private int extractBridgefyMsgType(Message message) {
        int eventOrdinal;
        Object eventObj = message.getContent().get("msg_type");
        if (eventObj instanceof Double) {
            eventOrdinal = ((Double) eventObj).intValue();
        } else {
            eventOrdinal = (Integer) eventObj;
        }
        return eventOrdinal;
    }

    private Peer.DeviceType extractType(Message message) {
        int eventOrdinal;
        Object eventObj = message.getContent().get("device_type");
        if (eventObj instanceof Double) {
            eventOrdinal = ((Double) eventObj).intValue();
        } else {
            eventOrdinal = (Integer) eventObj;
        }
        return Peer.DeviceType.values()[eventOrdinal];
    }

    StateListener stateListener = new StateListener() {
        @Override
        public void onDeviceConnected(final Device device, Session session) {
            // send our information to the Device
            Message.Builder builder = new Message.Builder();
            HashMap<String, Object> map = new HashMap<>();
            map.put("device_name", Build.MANUFACTURER + " " + Build.MODEL);
            map.put("device_type", Peer.DeviceType.ANDROID.ordinal());
            map.put("msg_type", com.bridgefy.samples.chat.entities.Message.TYPE_MESSAGE);
            builder.setContent(map).setReceiverId(device.getUserId());
            sendQueueBinder.getSendQueue().addMessageToSendQueue(builder.build());
//            device.sendMessage(map);
        }

        @Override
        public void onDeviceLost(Device peer) {
            Log.w(TAG, "onDeviceLost: " + peer.getUserId());
            Toast.makeText(getApplicationContext(), "device " + peer.getDeviceName() +" lost!", Toast.LENGTH_LONG).show();
            peersAdapter.removePeer(peer);
        }

        @Override
        public void onStartError(String message, int errorCode) {
            Log.e(TAG, "onStartError: " + message);

            if (errorCode == StateListener.INSUFFICIENT_PERMISSIONS) {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 0);
            }
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            // Start Bridgefy
            startBridgefy();

        } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
            Toast.makeText(this, "Location permissions needed to start peers discovery.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }


    /**
     *      RECYCLER VIEW CLASSES
     */
    class PeersRecyclerViewAdapter
            extends RecyclerView.Adapter<PeersRecyclerViewAdapter.PeerViewHolder> {

        private final List<Peer> peers;

        PeersRecyclerViewAdapter(List<Peer> peers) {
            this.peers = peers;
        }

        @Override
        public int getItemCount() {
            return peers.size();
        }

        void addPeer(Peer peer) {
            int position = getPeerPosition(peer.getUuid());
            if (position > -1) {
                peers.set(position, peer);
                notifyItemChanged(position);
            } else {
                peers.add(peer);
                notifyItemInserted(peers.size() - 1);
            }
        }

        void removePeer(Device lostPeer) {
            int position = getPeerPosition(lostPeer.getUserId());
            if (position > -1) {
                Peer peer = peers.get(position);
                peer.setNearby(false);
                peers.set(position, peer);
                notifyItemChanged(position);
            }
        }

        private int getPeerPosition(String peerId) {
            for (int i = 0; i < peers.size(); i++) {
                if (peers.get(i).getUuid().equals(peerId))
                    return i;
            }
            return -1;
        }

        @Override
        public PeerViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.peer_row, parent, false);
            return new PeerViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final PeerViewHolder peerHolder, int position) {
            peerHolder.setPeer(peers.get(position));
        }

        class PeerViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

            final TextView mContentView;
            Peer peer;

            PeerViewHolder(View view) {
                super(view);
                mContentView = view.findViewById(R.id.peerName);
                view.setOnClickListener(this);
            }

            void setPeer(Peer peer) {
                this.peer = peer;

                switch (peer.getDeviceType()) {
                    case ANDROID:
                        this.mContentView.setText(peer.getDeviceName() + " (android)");
                        break;

                    case IPHONE:
                        this.mContentView.setText(peer.getDeviceName() + " (iPhone)");
                        break;
                }

                if (peer.isNearby()) {
                    this.mContentView.setTextColor(Color.BLACK);
                } else {
                    this.mContentView.setTextColor(Color.GRAY);
                }
            }

            public void onClick(View v) {
                startActivity(new Intent(getBaseContext(), ChatActivity.class)
                        .putExtra(INTENT_EXTRA_NAME, peer.getDeviceName())
                        .putExtra(INTENT_EXTRA_UUID, peer.getUuid()));
            }
        }
    }
}
