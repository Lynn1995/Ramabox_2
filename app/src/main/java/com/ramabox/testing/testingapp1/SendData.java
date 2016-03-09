package com.ramabox.testing.testingapp1;

import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;




public class SendData extends AppCompatActivity {

    /* thread to read the data */
    public handler_thread handlerThread;

    /* declare a FT311 UART interface variable */
    public UARTInterface uartInterface;
    StringBuffer readSB = new StringBuffer();

    /* graphical objects */
    EditText readText,writeText;
    Button writeButton, configButton;

    /* local variables */
    byte[] writeBuffer;
    byte[] readBuffer;
    char[] readBufferToChar;
    int[] actualNumBytes;

    int numBytes;
    byte status;

    int baudRate; /* baud rate */
    byte stopBit; /* 1:1stop bits, 2:2 stop bits */
    byte dataBit; /* 8:8bit, 7: 7bit */
    byte parity; /* 0: none, 1: odd, 2: even, 3: mark, 4: space */
    byte flowControl; /* 0:none, 1: flow control(CTS,RTS) */
    public boolean bConfiged = false;
    public SharedPreferences sharePrefSettings;
    Drawable originalDrawable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_data);

        sharePrefSettings = getSharedPreferences("UARTLBPref", 0);
        /* create editable text objects */
        readText = (EditText) findViewById(R.id.readText);
        writeText = (EditText) findViewById(R.id.writeText);
        configButton = (Button) findViewById(R.id.configBtn);
        writeButton = (Button) findViewById(R.id.writeBtn);

        originalDrawable = configButton.getBackground();

      /* allocate buffer */
        writeBuffer = new byte[64];
        readBuffer = new byte[4096];
        readBufferToChar = new char[4096];
        actualNumBytes = new int[1];

      /* setup the baud rate list */
        baudRate = 9600;
      /* stop bits */
        stopBit = 1;
      /* daat bits */
        dataBit = 8;
      /* parity */
        parity = 0;
      /* flow control */
        flowControl = 0;

        configButton.setOnClickListener(new View.OnClickListener() {

            // @Override
            public void onClick(View v) {

                if(!bConfiged){
                    bConfiged = true;
                    uartInterface.SetConfig(baudRate, dataBit, stopBit, parity, flowControl);
                    savePreference();
                }

                if(bConfiged){
                    configButton.setText("Configured");
                }
            }

        });

      /* handle write click */
        writeButton.setOnClickListener(new View.OnClickListener() {

            // @Override
            public void onClick(View v) {

                if (writeText.length() != 0x00)
                {
                    writeData();
                }
            }
        });

        uartInterface = new UARTInterface(this, sharePrefSettings);

        handlerThread = new handler_thread(handler);
        handlerThread.start();


    }


    protected void cleanPreference(){
        SharedPreferences.Editor editor = sharePrefSettings.edit();
        editor.remove("configed");
        editor.remove("baudRate");
        editor.remove("stopBit");
        editor.remove("dataBit");
        editor.remove("parity");
        editor.remove("flowControl");
        editor.apply();
    }

    protected void savePreference() {
        if(bConfiged){
            sharePrefSettings.edit().putString("configed", "TRUE").apply();
            sharePrefSettings.edit().putInt("baudRate", baudRate).apply();
            sharePrefSettings.edit().putInt("stopBit", stopBit).apply();
            sharePrefSettings.edit().putInt("dataBit", dataBit).apply();
            sharePrefSettings.edit().putInt("parity", parity).apply();
            sharePrefSettings.edit().putInt("flowControl", flowControl).apply();
        }
        else{
            sharePrefSettings.edit().putString("configed", "FALSE").apply();
        }
    }

    protected void restorePreference() {
        String key_name = sharePrefSettings.getString("configed", "");
        bConfiged = true == key_name.contains("TRUE");

        baudRate = sharePrefSettings.getInt("baudRate", 9600);
        stopBit = (byte)sharePrefSettings.getInt("stopBit", 1);
        dataBit = (byte)sharePrefSettings.getInt("dataBit", 8);
        parity = (byte)sharePrefSettings.getInt("parity", 0);
        flowControl = (byte)sharePrefSettings.getInt("flowControl", 0);

        if(bConfiged){
            configButton.setText("Configured");
            configButton.setBackgroundColor(0xff888888); // color GRAY:0xff888888

        }
        else{
            configButton.setBackgroundDrawable(originalDrawable);
        }
    }

    public void onBackPressed() {
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        // Ideally should implement onResume() and onPause()
        // to take appropriate action when the activity looses focus
        super.onResume();
        if( 2 == uartInterface.ResumeAccessory() )
        {
            cleanPreference();
            restorePreference();
        }
    }

    @Override
    protected void onPause() {
        // Ideally should implement onResume() and onPause()
        // to take appropriate action when the activity looses focus
        super.onPause();
    }

    @Override
    protected void onStop() {
        // Ideally should implement onResume() and onPause()
        // to take appropriate action when the activity looses focus
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        uartInterface.DestroyAccessory(bConfiged);
        super.onDestroy();
    }


    final Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {

            for(int i=0; i<actualNumBytes[0]; i++)
            {
                readBufferToChar[i] = (char)readBuffer[i];
            }
            appendData(readBufferToChar, actualNumBytes[0]);
        }
    };

    /* usb input data handler */
    private class handler_thread extends Thread {
        Handler mHandler;

        /* constructor */
        handler_thread(Handler h) {
            mHandler = h;
        }

        public void run() {
            Message msg;

            while (true) {

                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                status = uartInterface.ReadData(4096, readBuffer,actualNumBytes);

                if (status == 0x00 && actualNumBytes[0] > 0) {
                    msg = mHandler.obtainMessage();
                    mHandler.sendMessage(msg);
                }

            }
        }
    }

    public void writeData()
    {
        String srcStr = writeText.getText().toString();
        String destStr = "";

                destStr = srcStr;

        numBytes = destStr.length();
        for (int i = 0; i < numBytes; i++) {
            writeBuffer[i] = (byte)destStr.charAt(i);
        }
        uartInterface.SendData(numBytes, writeBuffer);

    }

    public void appendData(char[] data, int len)
    {
        if(len >= 1)
            readSB.append(String.copyValueOf(data, 0, len));

        readText.setText(readSB);

    }


}
