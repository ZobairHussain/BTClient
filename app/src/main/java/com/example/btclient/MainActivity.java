package com.example.btclient;

import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    Button switchParent,send, listDevices;
    ListView listView;
    TextView msg_box,status;
    EditText writeMsg;

    BluetoothAdapter bluetoothAdapter;
    BluetoothDevice[] btArray;
    int bluetoothDevicePosition;

    ArrayList<String> stringArrayList = new ArrayList<String>();
    ArrayAdapter<String> arrayAdapter;

    ClientClass clientClass;

    SendReceive sendReceive;

    static final int STATE_LISTENING = 1;
    static final int STATE_CONNECTING=2;
    static final int STATE_CONNECTED=3;
    static final int STATE_CONNECTION_FAILED=4;
    static final int STATE_DISCONNECTED=5;
    static final int STATE_MESSAGE_RECEIVED=6;

    int REQUEST_ENABLE_BLUETOOTH=1;

    private static final String APP_NAME = "BTChat";
    private static final UUID MY_UUID=UUID.fromString("8ce255c0-223a-11e0-ac64-0803450c9a66");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewByIdes();
        bluetoothAdapter=BluetoothAdapter.getDefaultAdapter();

        if(!bluetoothAdapter.isEnabled())
        {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent,REQUEST_ENABLE_BLUETOOTH);
        }

        /*Code for available devices*/
        IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(myBluetoothReceiver, intentFilter);
        arrayAdapter = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_list_item_1, stringArrayList);
        listView.setAdapter(arrayAdapter);

        implementListeners();
    }

    BroadcastReceiver myBluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(BluetoothDevice.ACTION_FOUND.equals(action)){
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                stringArrayList.add(device.getName());
                arrayAdapter.notifyDataSetChanged();
            }
        }
    };

    private void implementListeners() {

        listDevices.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                bluetoothAdapter.startDiscovery();
                Set<BluetoothDevice> bt=bluetoothAdapter.getBondedDevices();
                String[] strings=new String[bt.size()];
                btArray=new BluetoothDevice[bt.size()];
                int index=0;

                if( bt.size()>0)
                {
                    for(BluetoothDevice device : bt)
                    {
                        btArray[index]= device;
                        strings[index]=device.getName();
                        index++;
                    }
                    arrayAdapter=new ArrayAdapter<String>(getApplicationContext(),android.R.layout.simple_list_item_1,strings);
                    listView.setAdapter(arrayAdapter);
                }
            }
        });

        switchParent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String DISCONNECTING_REQUEST = "-1";
                sendReceive.write(DISCONNECTING_REQUEST.getBytes());

                bluetoothDevicePosition++;
                if(bluetoothDevicePosition == btArray.length){
                    bluetoothDevicePosition = 0;
                }
                if(clientClass.isAlive()){
                    clientClass.end();
                }
                ClientClass clientClass=new ClientClass(btArray[bluetoothDevicePosition]);
                clientClass.start();
                status.setText("Connecting to " + btArray[bluetoothDevicePosition].getName());
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                clientClass = new ClientClass(btArray[position]);
                clientClass.start();
                bluetoothDevicePosition = position;
                status.setText("Connecting to " + btArray[bluetoothDevicePosition].getName());
            }
        });

        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String string= String.valueOf(writeMsg.getText());
                sendReceive.write(string.getBytes());
            }
        });
    }

    Handler handler=new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {

            switch (msg.what)
            {
                case STATE_LISTENING:
                    status.setText("Listening");
                    break;
                case STATE_CONNECTING:
                    status.setText("Connecting to " + btArray[bluetoothDevicePosition].getName());
                    break;
                case STATE_CONNECTED:
                    status.setText("Connected to " + btArray[bluetoothDevicePosition].getName());
                    break;
                case STATE_CONNECTION_FAILED:
                    status.setText("Connection Failed");
                    break;
                case STATE_MESSAGE_RECEIVED:
                    byte[] readBuff= (byte[]) msg.obj;
                    String tempMsg=new String(readBuff,0,msg.arg1);
                    msg_box.setText(tempMsg);
                    break;
            }
            return true;
        }
    });

    private void findViewByIdes() {
        switchParent=(Button) findViewById(R.id.switch_parent);
        send=(Button) findViewById(R.id.send);
        listView=(ListView) findViewById(R.id.listview);
        msg_box =(TextView) findViewById(R.id.msg);
        status=(TextView) findViewById(R.id.status);
        writeMsg=(EditText) findViewById(R.id.writemsg);
        listDevices=(Button) findViewById(R.id.listDevices);
    }

    private class ClientClass extends Thread
    {
        private BluetoothDevice device;
        private BluetoothSocket socket;

        public ClientClass (BluetoothDevice device1)
        {
            device=device1;

            try {
                socket=device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run()
        {
            try {
                socket.connect();
                Message message=Message.obtain();
                message.what=STATE_CONNECTED;
                handler.sendMessage(message);

                sendReceive=new SendReceive(socket);
                sendReceive.start();

            } catch (IOException e) {
                e.printStackTrace();
                Message message=Message.obtain();
                message.what=STATE_CONNECTION_FAILED;
                handler.sendMessage(message);
            }
        }

        public void end()
        {
            try {
                socket.close();
                Message message=Message.obtain();
                message.what=STATE_DISCONNECTED;
                handler.sendMessage(message);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class SendReceive extends Thread
    {
        private final BluetoothSocket bluetoothSocket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        public SendReceive (BluetoothSocket socket)
        {
            bluetoothSocket=socket;
            InputStream tempIn=null;
            OutputStream tempOut=null;

            try {
                tempIn=bluetoothSocket.getInputStream();
                tempOut=bluetoothSocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            inputStream=tempIn;
            outputStream=tempOut;
        }

        public void run()
        {
            byte[] buffer=new byte[1024];
            int bytes;

            while (true)
            {
                try {
                    bytes=inputStream.read(buffer);
                    handler.obtainMessage(STATE_MESSAGE_RECEIVED,bytes,-1,buffer).sendToTarget();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void write(byte[] bytes)
        {
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}