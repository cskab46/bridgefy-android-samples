package com.bridgefy.samples.chat;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
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
import com.bridgefy.samples.chat.service.SendQueue;
import com.bridgefy.sdk.client.BFEngineProfile;
import com.bridgefy.sdk.client.Bridgefy;
import com.bridgefy.sdk.client.BridgefyClient;
import com.bridgefy.sdk.client.RegistrationListener;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
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

import ru.bartwell.exfilepicker.ExFilePicker;
import ru.bartwell.exfilepicker.data.ExFilePickerResult;

public class ChatActivity extends AppCompatActivity implements ServiceConnection {

    private final String TAG = "ChatActivity";
    private String conversationName;
    private String conversationId;
    private boolean autoTest = false;
    private Intent sendQueueIntent;

    private final int EX_FILE_PICKER_RESULT = 0xfa01;
    private String startDirectory = null;// 记忆上一次访问的文件目录路径

    private SendQueue.Binder sendQueueBinder;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(android.os.Message msg) {
            switch (msg.what) {
                case 1:
                    messagesAdapter.addMessage((Message) msg.obj);
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

        Log.i("ChatActivity", "onCreate ");
        // recover our Peer object
        conversationName = getIntent().getStringExtra(INTENT_EXTRA_NAME);
        conversationId = getIntent().getStringExtra(INTENT_EXTRA_UUID);

        // Configure the Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // Enable the Up button
        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setTitle(conversationName);
            ab.setDisplayHomeAsUpEnabled(true);
        }
        sendQueueIntent = new Intent(ChatActivity.this, SendQueue.class);
        bindService(sendQueueIntent, this, Context.BIND_AUTO_CREATE);

        LocalBroadcastManager.getInstance(getBaseContext())
                .registerReceiver(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        Message message;
                        int msgType = intent.getIntExtra(MainActivity.INTENT_EXTRA_MSGTYPE, -1);
                        if (msgType == Message.TYPE_FILE) {
                            message = new Message(intent.getStringExtra(MainActivity.INTENT_EXTRA_MSG));
                            message.setDataLen(intent.getIntExtra(MainActivity.INTENT_EXTRA_DATALEN, 0));
                        } else if (msgType == Message.TYPE_DATA) {
                            message = new Message(intent.getStringExtra(MainActivity.INTENT_EXTRA_MSG));
                            message.setDataLen(intent.getIntExtra(MainActivity.INTENT_EXTRA_DATALEN, 0));
                        } else {
                            message = new Message(intent.getStringExtra(MainActivity.INTENT_EXTRA_MSG));
                        }
                        message.setMsgType(msgType);
                        message.setDeviceName(intent.getStringExtra(MainActivity.INTENT_EXTRA_NAME));
                        message.setDirection(Message.INCOMING_MESSAGE);
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
    public void onServiceConnected(ComponentName name, IBinder service) {
        sendQueueBinder = (SendQueue.Binder) service;
        Log.i(TAG, "onServiceConnected");
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        Log.i(TAG, "onServiceDisconnected");
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
                Toast.makeText(getBaseContext(), "AutoTest: " + String.valueOf(autoTest), Toast.LENGTH_LONG).show();
                messagesAdapter.hideBtnEditText();
                return true;
        }
        return false;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    private com.bridgefy.sdk.client.Message generateTestMsg(byte data) {

        byte[] datafill = new byte[1000000];
        HashMap<String, Object> content = new HashMap<>();
        com.bridgefy.sdk.client.Message.Builder builder = new com.bridgefy.sdk.client.Message.Builder();

        content.put("device_type", Peer.DeviceType.ANDROID.ordinal());
        content.put("device_name", Build.MANUFACTURER + " " + Build.MODEL);
        Arrays.fill(datafill, data);
        byte[] byteArr = new byte[1];
        byteArr[0] = data;
        content.put("text", new String(byteArr));
        content.put("Data", datafill);
        content.put("msg_type", Message.TYPE_DATA);
        builder.setContent(content);

        return builder.build();
    }

    @OnClick({R.id.btn_test_msg})
    public void onMessageAutoSend(View v) {
        // we put extra information in broadcast packets since they won't be bound to a session
        for (int i = 0; i < 3; i++) {
            String text = String.valueOf(i%10);
            sendQueueBinder.getSendQueue().addMessageToSendQueue(generateTestMsg(text.getBytes()[0]));
            Log.i(TAG, "addMessageToSendQueue! time: " + System.currentTimeMillis());

            Message message = new Message(text);
            message.setMsgType(Message.TYPE_DATA);
            message.setDirection(Message.OUTGOING_MESSAGE);

            android.os.Message mesToHandler = android.os.Message.obtain(mHandler);
            mesToHandler.what = 1;
            mesToHandler.obj = message;
            mesToHandler.sendToTarget();
//        synchronized (this) {
//            Thread autoTestThread = new Thread(new Runnable() {
//                @Override
//                public void run() {
//                    //do auto test
//                    // create a HashMap object to send
//                    HashMap<String, Object> content = new HashMap<>();
//                    com.bridgefy.sdk.client.Message.Builder builder = new com.bridgefy.sdk.client.Message.Builder();
//                    content.put("device_type", Peer.DeviceType.ANDROID.ordinal());
//                    content.put("device_name", Build.MANUFACTURER + " " + Build.MODEL);
//
//                    // we put extra information in broadcast packets since they won't be bound to a session
//                    for (int i = 0; i < 3; i++) {
////                        try {
////                            Thread.sleep(50);
////                        } catch (InterruptedException e) {
////                            e.printStackTrace();
////                        }
//
//                        byte[] datafill = new byte[1000000];
//                        String text = String.valueOf(i%10);
//                        Arrays.fill(datafill, text.getBytes()[0]);
////                        Arrays.fill(datafill, 'a');
//                        Log.i(this.getClass().getName(), "char: " + text + "datafill: " + datafill);
//
//                        content.put("text", text);
//                        content.put("Data", datafill);
//                        content.put("msg_type", Message.TYPE_DATA);
//                        builder.setContent(content);
//                        Log.i(TAG, "message sent start! time used: " + System.currentTimeMillis());
//                        Bridgefy.sendBroadcastMessage(builder.build(),
//                                BFEngineProfile.BFConfigProfileLongReach);
//                        msgSentflag.block(3000);
//                        msgSentflag.close();
//                        Message message = new Message(text);
//                        message.setMsgType(Message.TYPE_DATA);
//                        message.setDirection(Message.OUTGOING_MESSAGE);
//
//                        android.os.Message mesToHandler = android.os.Message.obtain(mHandler);
//                        mesToHandler.what = 1;
//                        mesToHandler.obj = message;
//                        mesToHandler.sendToTarget();
//
//
//                    }
//                }
//            });
//            autoTestThread.start();
        }
    }

    @OnClick({R.id.btn_test_file})
    public void onFileAutoSend(View v) {
        Toast.makeText(getBaseContext(), "this is test for snd file!", Toast.LENGTH_LONG).show();
        //Todo file transfer

        ExFilePicker exFilePicker = new ExFilePicker();
        exFilePicker.setCanChooseOnlyOneItem(true);// 单选
        exFilePicker.setQuitButtonEnabled(true);

        if (TextUtils.isEmpty(startDirectory)) {
            exFilePicker.setStartDirectory(Environment.getExternalStorageDirectory().getPath());
        } else {
            exFilePicker.setStartDirectory(startDirectory);
        }

        exFilePicker.setChoiceType(ExFilePicker.ChoiceType.FILES);
        exFilePicker.start(this, EX_FILE_PICKER_RESULT);

        return;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == EX_FILE_PICKER_RESULT) {
            ExFilePickerResult result = ExFilePickerResult.getFromIntent(data);
            if (result != null && result.getCount() > 0) {
                String path = result.getPath();

                List<String> names = result.getNames();
                for (int i = 0; i < names.size(); i++) {
                    File f = new File(path, names.get(i));
                    byte fileContent[] = new byte[(int) f.length()];
                    try {
                        Uri uri = Uri.fromFile(f); //这里获取了真实可用的文件资源
                        Toast.makeText(this, "选择文件:" + uri.getPath(), Toast.LENGTH_SHORT)
                                .show();

                        FileInputStream fin = null;
                        try {
                            fin = new FileInputStream(f);
                            fin.read(fileContent);

                            HashMap<String, Object> content = new HashMap<>();
                            com.bridgefy.sdk.client.Message.Builder builder = new com.bridgefy.sdk.client.Message.Builder();
                            content.put("device_type", Peer.DeviceType.ANDROID.ordinal());
                            content.put("device_name", Build.MANUFACTURER + " " + Build.MODEL);
                            content.put("msg_type", Message.TYPE_FILE);

                            content.put("text", f.getName());
                            content.put("Data", fileContent);
                            builder.setContent(content);
                            Bridgefy.sendBroadcastMessage(builder.build(),
                                    BFEngineProfile.BFConfigProfileLongReach);

                            //更新ui
                            Message message = new Message(f.getName() + "size: " + f.length());
                            message.setDirection(Message.OUTGOING_MESSAGE);
                            android.os.Message mesToHandler = android.os.Message.obtain(mHandler);
                            mesToHandler.what = 1;
                            mesToHandler.obj = message;
                            mesToHandler.sendToTarget();

                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            if (fin != null) {
                                fin.close();
                            }
                        }

                        startDirectory = path;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
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
            content.put("msg_type", Message.TYPE_MESSAGE);
            // send message text to device
            if (conversationId.equals(BROADCAST_CHAT)) {
                // we put extra information in broadcast packets since they won't be bound to a session
                content.put("device_name", Build.MANUFACTURER + " " + Build.MODEL);
                content.put("device_type", Peer.DeviceType.ANDROID.ordinal());
                com.bridgefy.sdk.client.Message.Builder builder = new com.bridgefy.sdk.client.Message.Builder();
                builder.setContent(content);
                Bridgefy.sendBroadcastMessage(builder.build(),
                        BFEngineProfile.BFConfigProfileLongReach);
            } else {
                com.bridgefy.sdk.client.Message.Builder builder = new com.bridgefy.sdk.client.Message.Builder();
                builder.setContent(content).setReceiverId(conversationId);

                Bridgefy.sendMessage(builder.build(),
                        BFEngineProfile.BFConfigProfileLongReach);
            }
        }
    }

    /**
     * RECYCLER VIEW CLASSES
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
                    this.txtMessage.setText(message.getDeviceName() + ":\n");
                    switch (message.getMsgType()) {
                        case Message.TYPE_MESSAGE:
                            this.txtMessage.setText(message.getText());
                            break;
                        case Message.TYPE_FILE:
                            this.txtMessage.setText("fileName: " + message.getText()
                                    + " length: " + message.getDataLen()
                                    + "\ntime: " + message.getTimeStr());
                            break;
                        case Message.TYPE_DATA:
                            this.txtMessage.setText("Data: " + message.getText()
                                    + " length: " + message.getDataLen()
                                    + "\ntime: " + message.getTimeStr());
                            break;
                        default:
                            break;
                    }
                } else {
                    this.txtMessage.setText(message.getText());
                }
            }
        }
    }
}
