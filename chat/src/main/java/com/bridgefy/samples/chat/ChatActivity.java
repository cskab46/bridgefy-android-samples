package com.bridgefy.samples.chat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.bridgefy.samples.chat.entities.Message;
import com.bridgefy.samples.chat.entities.Peer;
import com.bridgefy.sdk.client.BFEngineProfile;
import com.bridgefy.sdk.client.Bridgefy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static android.os.Message.obtain;
import static com.bridgefy.samples.chat.MainActivity.BROADCAST_CHAT;
import static com.bridgefy.samples.chat.MainActivity.INTENT_EXTRA_NAME;
import static com.bridgefy.samples.chat.MainActivity.INTENT_EXTRA_UUID;


public class ChatActivity extends AppCompatActivity {

    private String conversationName;
    private String conversationId;
    private boolean autoTest = false;
    private long receiveSizeCount = 0;
    private long lastReceiveSizeCount = 0;

    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(android.os.Message msg){
            switch (msg.what) {
                case 1:
                    messagesAdapter.addMessage((Message)msg.obj);
                    break;
                default:
                    break;
            }
        }

    };

    @BindView(R.id.eTxtMessage)
    EditText txtMessage;

    MessagesRecyclerViewAdapter messagesAdapter =
            new MessagesRecyclerViewAdapter(new ArrayList<Message>());


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        ButterKnife.bind(this);

        // recover our Peer object
        conversationName = getIntent().getStringExtra(INTENT_EXTRA_NAME);
        conversationId   = getIntent().getStringExtra(INTENT_EXTRA_UUID);

        // Configure the Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // Enable the Up button
        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setTitle(conversationName);
            ab.setDisplayHomeAsUpEnabled(true);
        }

        // transfer speed thread
        Thread transSpeedThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    TimerTask timerTask_speed = new TimerTask() {

                        @Override
                        public void run() {
                            long speed = receiveSizeCount - lastReceiveSizeCount;
                            Log.i(this.getClass().getName(),"getSpeed,count=" + receiveSizeCount + ",lastCount=" + lastReceiveSizeCount + ",speed=" + speed);
                            lastReceiveSizeCount = receiveSizeCount;
                        }
                    };
                    Timer timer_speed = new Timer();
                    timer_speed.schedule(timerTask_speed, 0, 1000);// 1s判断一次。计算实时速度，
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
//        transSpeedThread.start();

        // register the receiver to listen for incoming messages
        LocalBroadcastManager.getInstance(getBaseContext())
                .registerReceiver(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        Message message = new Message(intent.getStringExtra(MainActivity.INTENT_EXTRA_MSG));
                        message.setDeviceName(intent.getStringExtra(MainActivity.INTENT_EXTRA_NAME));
                        message.setDirection(Message.INCOMING_MESSAGE);
                        receiveSizeCount += message.getText().length();
                        long sendTimestamp = intent.getLongExtra(MainActivity.INTENT_EXTRA_MSGSENDTS, 0);
                        long recvTimestap = System.currentTimeMillis();
                        message.setTimeStr(String.valueOf(recvTimestap - sendTimestamp));
                        messagesAdapter.addMessage(message);
                    }
                }, new IntentFilter(conversationId));

        // configure the recyclerview
        RecyclerView messagesRecyclerView = findViewById(R.id.message_list);
        LinearLayoutManager mLinearLayoutManager = new LinearLayoutManager(this);
        mLinearLayoutManager.setReverseLayout(true);
        messagesRecyclerView.setLayoutManager(mLinearLayoutManager);
        messagesRecyclerView.setAdapter(messagesAdapter);
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
                autoTest = (autoTest == true) ? false : true;
                Toast.makeText(getBaseContext(),"AutoTest: " + String.valueOf(autoTest),Toast.LENGTH_LONG).show();
                messagesAdapter.hideBtnEditText();
                return true;
        }
        return false;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @OnClick({R.id.btn_test_msg})
    public void onMessageAutoSend(View v) {
        synchronized (this) {
            Thread autoTestThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    //do auto test
                    // create a HashMap object to send
                    HashMap<String, Object> content = new HashMap<>();
                    com.bridgefy.sdk.client.Message.Builder builder=new com.bridgefy.sdk.client.Message.Builder();
                    content.put("device_type", Peer.DeviceType.ANDROID.ordinal());
                    content.put("device_name", Build.MANUFACTURER + " " + Build.MODEL);

                    // we put extra information in broadcast packets since they won't be bound to a session
                    for (int i = 0; i < 1; i++) {
                        try {
                            Thread.sleep(80);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        char[] datafill = new char[10000000];
                        Arrays.fill(datafill, String.valueOf(i%10).toCharArray()[0]);
                        String dataFill = new String(datafill);
                        Log.i(this.getClass().getName(), "char: " + datafill[0]);
                        Log.i(this.getClass().getName(), "datafill: " + dataFill);
                        content.put("text", dataFill);
                        builder.setContent(content);
                        Bridgefy.sendBroadcastMessage(builder.build(),
                                BFEngineProfile.BFConfigProfileLongReach);
                        Message message = new Message(String.valueOf(i));
                        message.setDirection(Message.OUTGOING_MESSAGE);

                        android.os.Message mesToHandler = android.os.Message.obtain(mHandler);
                        mesToHandler.what = 1;
                        mesToHandler.obj = message;
                        mesToHandler.sendToTarget();
                    }
                }
            });
            autoTestThread.start();
        }
    }

    @OnClick({R.id.btn_test_file})
    public void onFileAutoSend(View v) {
        Toast.makeText(getBaseContext(),"this is test for snd file!", Toast.LENGTH_LONG).show();
        return;
    }

    @OnClick({R.id.btnSend})
    public void onMessageSend(View v) {
        // get the message and push it to the views
        String messageString = txtMessage.getText().toString();
        if (messageString.trim().length() > 0) {
            // update the views
            txtMessage.setText("");
            Message message = new Message(messageString);
            message.setDirection(Message.OUTGOING_MESSAGE);
            messagesAdapter.addMessage(message);

            // create a HashMap object to send
            HashMap<String, Object> content = new HashMap<>();
            content.put("text", messageString);

            // send message text to device
            if (conversationId.equals(BROADCAST_CHAT)) {
                // we put extra information in broadcast packets since they won't be bound to a session
                content.put("device_name", Build.MANUFACTURER + " " + Build.MODEL);
                content.put("device_type", Peer.DeviceType.ANDROID.ordinal());

                com.bridgefy.sdk.client.Message.Builder builder=new com.bridgefy.sdk.client.Message.Builder();
                builder.setContent(content);
                Bridgefy.sendBroadcastMessage(builder.build(),
                        BFEngineProfile.BFConfigProfileLongReach);
            } else {

                com.bridgefy.sdk.client.Message.Builder builder=new com.bridgefy.sdk.client.Message.Builder();
                builder.setContent(content).setReceiverId(conversationId);

                Bridgefy.sendMessage(builder.build(),
                        BFEngineProfile.BFConfigProfileLongReach);
            }
        }
    }


    /**
     *      RECYCLER VIEW CLASSES
     */
    class MessagesRecyclerViewAdapter
            extends RecyclerView.Adapter<MessagesRecyclerViewAdapter.MessageViewHolder> {

        private final List<Message> messages;

        MessagesRecyclerViewAdapter(List<Message> messages) {
            this.messages = messages;
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        void addMessage(Message message) {
            messages.add(0, message);
            notifyDataSetChanged();
        }

        public void hideBtnEditText() {
            View btn = findViewById(R.id.btnSend);
            EditText editText = findViewById(R.id.eTxtMessage);

            if (autoTest) {
                btn.setVisibility(View.VISIBLE);
                editText.setEnabled(true);
                editText.setVisibility(View.VISIBLE);
            } else {
                btn.setVisibility(View.GONE);
                editText.setEnabled(false);
                editText.setVisibility(View.GONE);
            }
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return messages.get(position).getDirection();
        }

        @Override
        public MessageViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View messageView = null;

            switch (viewType) {
                case Message.INCOMING_MESSAGE:
                    messageView = LayoutInflater.from(viewGroup.getContext()).
                            inflate((R.layout.message_row_incoming), viewGroup, false);
                    break;
                case Message.OUTGOING_MESSAGE:
                    messageView = LayoutInflater.from(viewGroup.getContext()).
                            inflate((R.layout.message_row_outgoing), viewGroup, false);
                    break;
            }

            return new MessageViewHolder(messageView);
        }

        @Override
        public void onBindViewHolder(final MessageViewHolder messageHolder, int position) {
            messageHolder.setMessage(messages.get(position));
        }

        class MessageViewHolder extends RecyclerView.ViewHolder {
            final TextView txtMessage;
            Message message;

            MessageViewHolder(View view) {
                super(view);
                txtMessage = view.findViewById(R.id.txtMessage);
            }

            void setMessage(Message message) {
                this.message = message;

                if (message.getDirection() == Message.INCOMING_MESSAGE &&
                        conversationId.equals(BROADCAST_CHAT)) {
                    Log.i(this.getClass().getName(), "message: " + message.getText().charAt(1));
//                    this.txtMessage.setText(message.getDeviceName() + ":\n" + message.getText());
                    this.txtMessage.setText(message.getDeviceName() + ":\nlength: " + message.getText().length() + " * " + message.getText().charAt(1) + "\ntime: " + message.getTimeStr());
                } else {
                    this.txtMessage.setText(message.getText());
                }
            }
        }
    }
}
