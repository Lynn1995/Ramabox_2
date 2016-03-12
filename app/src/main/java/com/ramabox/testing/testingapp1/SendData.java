package com.ramabox.testing.testingapp1;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class SendData extends AppCompatActivity {
    // thread to read the data
    public handler_thread handlerThread;

    //interface variable
    public UARTClass uart;
    StringBuffer readSB = new StringBuffer();

    // buttons and textviews
    EditText readTxt, writeTxt;
    Button writeBtn, configBtn;

    // local variables
    byte[] writeBuffer;
    byte[] readBuffer;
    char[] readBufferToChar;
    int[] actualNumBytes;

    int numBytes;
    byte status;

    public boolean bConfiged = false;
    public SharedPreferences sharePrefSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_data);

        sharePrefSettings = getSharedPreferences("UARTLBPref", 0);
        /* create editable text objects */
        readTxt = (EditText) findViewById(R.id.readText);
        writeTxt = (EditText) findViewById(R.id.writeText);
        configBtn = (Button) findViewById(R.id.configBtn);
        writeBtn = (Button) findViewById(R.id.writeBtn);

        // alocate buffers
        writeBuffer = new byte[64];
        readBuffer = new byte[4096];
        readBufferToChar = new char[4096];
        actualNumBytes = new int[1];
        uart = new UARTClass(this, sharePrefSettings);
        handlerThread = new handler_thread(handler);
        handlerThread.start();
    }

    // the button actions
    public void writeClick(View v) {
        if (writeTxt.length() != 0x00){
            writeData(writeTxt.getText().toString());
        }
    }

    public void configClick(View v){
        if(!bConfiged){
            bConfiged = true;
            uart.setConfig();
            savePreference();
        } else if(bConfiged){
            configBtn.setText("Configured");
        }
    }

    public void ledOnClick(View v){
        writeData("a");
    }

    public void ledOffClick(View v){
        writeData("u");
    }

    public void deconfigClick(View v){
        bConfiged = false;
        configBtn.setText("Config");

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
            sharePrefSettings.edit().putInt("baudRate", 9600).apply();
            sharePrefSettings.edit().putInt("stopBit", 1).apply();
            sharePrefSettings.edit().putInt("dataBit", 8).apply();
            sharePrefSettings.edit().putInt("parity", 0).apply();
            sharePrefSettings.edit().putInt("flowControl", 0).apply();
        }
        else{
            sharePrefSettings.edit().putString("configed", "FALSE").apply();
        }
    }

    protected void restorePreference() {
        String key_name = sharePrefSettings.getString("configed", "");
        bConfiged = true == key_name.contains("TRUE");
        if(bConfiged){
            configBtn.setText("Configured");
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
        if( 2 == uart.resumeAccessory() )
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
        uart.destroyAccessory(bConfiged);
        super.onDestroy();
    }


    final Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            for(int i=0; i<actualNumBytes[0]; i++){
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

                status = uart.readData(4096, readBuffer, actualNumBytes);

                if (status == 0x00 && actualNumBytes[0] > 0) {
                    msg = mHandler.obtainMessage();
                    mHandler.sendMessage(msg);
                }

            }
        }
    }

    public void writeData(String srcStr) {

        String destStr = "";

                destStr = srcStr;

        numBytes = destStr.length();
        for (int i = 0; i < numBytes; i++) {
            writeBuffer[i] = (byte)destStr.charAt(i);
        }
        uart.sendData(numBytes, writeBuffer);

    }

    public void appendData(char[] data, int len){
        if(len >= 1)
            readSB.append(String.copyValueOf(data, 0, len));

        readTxt.setText(readSB);

    }

}
