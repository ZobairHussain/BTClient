package com.example.btclient;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
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
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    Button switchParent,send, listDevices;
    ListView listView;
    TextView msg_box, status;
    EditText writeMsg;

    BluetoothAdapter bluetoothAdapter;
    BluetoothDevice[] btArray;
    BluetoothDevice bluetoothDevice;
    int bluetoothDevicePosition;

    ArrayAdapter<String> arrayAdapter;

    static SendReceive sendReceive;

    static final int COMMUNICATION_STATE_LISTENING = 1;
    static final int COMMUNICATION_STATE_CONNECTING =2;
    static final int COMMUNICATION_STATE_CONNECTED =3;
    static final int COMMUNICATION_STATE_CONNECTION_FAILED =4;
    static final int COMMUNICATION_STATE_DISCONNECTED =5;
    static final int COMMUNICATION_STATE_MESSAGE_RECEIVED =6;
    int COMMUNICATION_STATE = -1;

    int REQUEST_ENABLE_BLUETOOTH=1;
    String DISCONNECTING_REQUEST = "-1";

    private static final String APP_NAME = "BTChat";
    private static final UUID MY_UUID=UUID.fromString("8ce255c0-223a-11e0-ac64-0803450c9a66");

    @RequiresApi(api = Build.VERSION_CODES.M)
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

        implementListeners();
    }

    private void implementListeners() {

        listDevices.setOnClickListener(view -> {
            Set<BluetoothDevice> bt=bluetoothAdapter.getBondedDevices();
            String[] deviceName=new String[bt.size()];
            btArray=new BluetoothDevice[bt.size()];

            if( bt.size()>0)
            {
                int index = 0;
                for(BluetoothDevice device : bt)
                {
                    btArray[index]= device;
                    deviceName[index]=device.getName();
                    index++;
                }
                arrayAdapter=new ArrayAdapter<String>(getApplicationContext(),android.R.layout.simple_list_item_1, deviceName);
                listView.setAdapter(arrayAdapter);
            }
        });

        switchParent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(COMMUNICATION_STATE == COMMUNICATION_STATE_CONNECTED){
                    sendReceive.write(DISCONNECTING_REQUEST.getBytes());
                }
                bluetoothDevicePosition++;
                if(bluetoothDevicePosition == btArray.length){
                    bluetoothDevicePosition = 0;
                }

                ClientClass clientClass=new ClientClass(btArray[bluetoothDevicePosition]);
                clientClass.start();
                //status.setText("Connecting to " + btArray[bluetoothDevicePosition].getName());
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if(COMMUNICATION_STATE == COMMUNICATION_STATE_CONNECTED){
                    sendReceive.write(DISCONNECTING_REQUEST.getBytes());
                }
                ClientClass clientClass = new ClientClass(btArray[position]);
                clientClass.start();
                bluetoothDevicePosition = position;
                //status.setText("Connecting to " + btArray[bluetoothDevicePosition].getName());

                bluetoothDevice = btArray[position];

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
                case COMMUNICATION_STATE_LISTENING:
                    COMMUNICATION_STATE = COMMUNICATION_STATE_LISTENING;
                    status.setText("Listening");
                    break;
                case COMMUNICATION_STATE_CONNECTING:
                    COMMUNICATION_STATE = COMMUNICATION_STATE_CONNECTING;
                    status.setText("Connecting to " + btArray[bluetoothDevicePosition].getName());
                    break;
                case COMMUNICATION_STATE_CONNECTED:
                    COMMUNICATION_STATE = COMMUNICATION_STATE_CONNECTED;
                    status.setText("Connected to " + btArray[bluetoothDevicePosition].getName());
                    break;
                case COMMUNICATION_STATE_CONNECTION_FAILED:
                    COMMUNICATION_STATE = COMMUNICATION_STATE_CONNECTION_FAILED;
                    status.setText("Connection Failed");
                    break;
                case COMMUNICATION_STATE_MESSAGE_RECEIVED:
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
        msg_box =(TextView) findViewById(R.id.msg);
        status=(TextView) findViewById(R.id.status);
        writeMsg=(EditText) findViewById(R.id.writemsg);
        listDevices=(Button) findViewById(R.id.listDevices);
        listView=(ListView) findViewById(R.id.listview);
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
                message.what= COMMUNICATION_STATE_CONNECTED;
                handler.sendMessage(message);

                sendReceive=new SendReceive(socket);
                sendReceive.start();

            } catch (IOException e) {
                e.printStackTrace();
                Message message=Message.obtain();
                message.what= COMMUNICATION_STATE_CONNECTION_FAILED;
                handler.sendMessage(message);
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
                    handler.obtainMessage(COMMUNICATION_STATE_MESSAGE_RECEIVED,bytes,-1,buffer).sendToTarget();
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