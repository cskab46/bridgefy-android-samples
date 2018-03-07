package com.bridgefy.samples.chat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
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

import com.bridgefy.samples.chat.entities.Peer;
import com.bridgefy.sdk.client.Bridgefy;
import com.bridgefy.sdk.client.BridgefyClient;
import com.bridgefy.sdk.client.Device;
import com.bridgefy.sdk.client.Message;
import com.bridgefy.sdk.client.MessageListener;
import com.bridgefy.sdk.client.RegistrationListener;
import com.bridgefy.sdk.client.Session;
import com.bridgefy.sdk.client.StateListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private String TAG = "MainActivity";
    private boolean isRegistered = false;
    String username;
    static final String INTENT_EXTRA_NAME = "peerName";
    static final String INTENT_EXTRA_UUID = "peerUuid";
    static final String INTENT_EXTRA_TYPE = "deviceType";
    static final String INTENT_EXTRA_MSG  = "message";
    static final String BROADCAST_CHAT    = "Broadcast";
    static final String INTENT_EXTRA_MSGSENDTS = "sendtimestamp";

    PeersRecyclerViewAdapter peersAdapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Configure the Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(getTitle());

        // load our username
        username = getSharedPreferences(Constants.PREFS_NAME, 0).getString(Constants.PREFS_USERNAME, null);

        if (isThingsDevice(this)) {
            //enabling bluetooth automatically
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            bluetoothAdapter.enable();
        } else {
            // check that we have permissions, otherwise fire the IntroActivity
            if ((ContextCompat.checkSelfPermission(getApplicationContext(),
                    Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) ||
                    (username == null)) {
                startActivity(new Intent(getBaseContext(), IntroActivity.class));
                finish();
            } else {
                peersAdapter = new PeersRecyclerViewAdapter(new ArrayList<Peer>());
                RecyclerView recyclerView = findViewById(R.id.peer_list);
                recyclerView.setAdapter(peersAdapter);
                TextView ContentView;
                ContentView = findViewById(R.id.peerName);
                ContentView.setText(username);

                Bridgefy.initialize(getApplicationContext(), new RegistrationListener() {
                    @Override
                    public void onRegistrationSuccessful(BridgefyClient bridgefyClient) {
                        // Start Bridgefy
                        startBridgefy();
                        isRegistered = true;
                    }

                    @Override
                    public void onRegistrationFailed(int errorCode, String message) {
                        Toast.makeText(getBaseContext(), getString(R.string.registration_error),
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        }


    }

    @Override
    protected void onDestroy() {
        if (isRegistered &&  isFinishing())
            Bridgefy.stop();

        super.onDestroy();
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

    public boolean isThingsDevice(Context context) {
        final PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature("android.hardware.type.embedded");
    }

    private MessageListener messageListener = new MessageListener() {
        @Override
        public void onMessageReceived(Message message) {
            // direct messages carrying a Device name represent device handshakes
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
                                .putExtra(INTENT_EXTRA_MSG, incomingMessage));
            }

            if (isThingsDevice(MainActivity.this)) {
                //if it's an Android Things device, reply automatically
                HashMap<String, Object> content = new HashMap<>();
                content.put("text", "Beep boop. I'm a bot.");

                Message.Builder builder=new Message.Builder();
                builder.setContent(content).setReceiverId(message.getSenderId());
                Bridgefy.sendMessage(builder.build());

            }
        }

        @Override
        public void onBroadcastMessageReceived(Message message) {
            // we should not expect to have connected previously to the device that originated
            // the incoming broadcast message, so device information is included in this packet
            String incomingMsg = (String) message.getContent().get("text");
            String deviceName  = (String) message.getContent().get("device_name");
            Peer.DeviceType deviceType = Peer.DeviceType.ANDROID;
            Long sendTimestamp = message.getDateSent();

            LocalBroadcastManager.getInstance(getBaseContext()).sendBroadcast(
                    new Intent(BROADCAST_CHAT)
                            .putExtra(INTENT_EXTRA_NAME, deviceName)
                            .putExtra(INTENT_EXTRA_TYPE, deviceType)
                            .putExtra(INTENT_EXTRA_MSGSENDTS, sendTimestamp)
                            .putExtra(INTENT_EXTRA_MSG,  incomingMsg));
        }
    };

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
            HashMap<String, Object> map = new HashMap<>();
//            map.put("device_name", Build.MANUFACTURER + " " + Build.MODEL);
            map.put("device_name", username);
            map.put("device_type", Peer.DeviceType.ANDROID.ordinal());
            device.sendMessage(map);
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
                mContentView.setText(username);
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
