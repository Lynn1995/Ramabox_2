//User must modify the below package with their package name
package com.ramabox.testing.testingapp1;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;


/******************************FT311 GPIO interface class******************************************/
public class UARTClass extends Activity
{

	private static final String ACTION_USB_PERMISSION = "com.UARTLoopback.USB_PERMISSION";
	public UsbManager usbmanager;
	public UsbAccessory usbaccessory;
	public PendingIntent mPermissionIntent;
	public ParcelFileDescriptor filedescriptor = null;
	public FileInputStream inputstream = null;
	public FileOutputStream outputstream = null;
	public boolean mPermissionRequestPending = false;
	public read_thread readThread;

	private byte [] usbdata; 
	private byte []	writeusbdata;
	private byte  [] readBuffer; /*circular buffer*/
	private int totalBytes;
	private int writeIndex;
	private int readIndex;
	private byte status;
	final int  maxnumbytes = 65536;

	public boolean READ_ENABLE = false;
	public boolean accessory_attached = false;

	public Context global_context;

	public static String ManufacturerString = "mManufacturer=FTDI";
	public static String ModelString1 = "mModel=FTDIUARTDemo";
	public static String ModelString2 = "mModel=Android Accessory FT312D";
	public static String VersionString = "mVersion=1.0";

	public SharedPreferences intsharePrefSettings;

	/*constructor*/
	public UARTClass(Context context, SharedPreferences sharePrefSettings){
		super();
		global_context = context;
		intsharePrefSettings = sharePrefSettings;
		/*shall we start a thread here or what*/
		usbdata = new byte[1024]; 
		writeusbdata = new byte[256];
		/*128(make it 256, but looks like bytes should be enough)*/
		readBuffer = new byte [maxnumbytes];


		readIndex = 0;
		writeIndex = 0;
		/***********************USB handling******************************************/

		usbmanager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
		// Log.d("LED", "usbmanager" +usbmanager);
		mPermissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), 0);
		IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
		filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
		context.registerReceiver(mUsbReceiver, filter);

		inputstream = null;
		outputstream = null;
	}

	public void setConfig()
	{
		int baud = 9600;
		/*prepare the baud rate buffer*/
		writeusbdata[0] = (byte)baud;
		writeusbdata[1] = (byte)(baud >> 8);
		writeusbdata[2] = (byte)(baud >> 16);
		writeusbdata[3] = (byte)(baud >> 24);

		/*data bits*/
		writeusbdata[4] = 8;
		/*stop bits*/
		writeusbdata[5] = 1;
		/*parity*/
		writeusbdata[6] = 0;
		/*flow control*/
		writeusbdata[7] = 0;

		/*send the UART configuration packet*/
		sendPacket(8);
	}

	/*write data*/
	public byte sendData(int numBytes, byte[] buffer)
	{
		status = 0x00; /*success by default*/
		/*
		 * if num bytes are more than maximum limit
		 */
		if(numBytes < 1){
			/*return the status with the error in the command*/
			return status;
		}

		/*check for maximum limit*/
		if(numBytes > 256){
			numBytes = 256;
		}

		/*prepare the packet to be sent*/
		System.arraycopy(buffer, 0, writeusbdata, 0, numBytes);

		if(numBytes != 64)
		{
			sendPacket(numBytes);
		}
		else
		{
			byte temp = writeusbdata[63];
			sendPacket(63);
			writeusbdata[0] = temp;
			sendPacket(1);
		}

		return status;
	}

	/*read data*/
	public byte readData(int numBytes, byte[] buffer, int[] actualNumBytes)
	{
		status = 0x00; /*success by default*/

		/*should be at least one byte to read*/
		if((numBytes < 1) || (totalBytes == 0)){
			actualNumBytes[0] = 0;
			status = 0x01;
			return status;
		}

		/*check for max limit*/
		if(numBytes > totalBytes)
			numBytes = totalBytes;

		/*update the number of bytes available*/
		totalBytes -= numBytes;

		actualNumBytes[0] = numBytes;	

		/*copy to the user buffer*/	
		for(int count = 0; count<numBytes;count++)
		{
			buffer[count] = readBuffer[readIndex];
			readIndex++;
			/*shouldnt read more than what is there in the buffer,
			 * 	so no need to check the overflow
			 */
			readIndex %= maxnumbytes;
		}
		return status;
	}

	/*method to send on USB*/
	private void sendPacket(int numBytes)
	{	
		try {
			if(outputstream != null){
				outputstream.write(writeusbdata, 0,numBytes);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/*resume accessory*/
	public int resumeAccessory()
	{
		// Intent intent = getIntent();
		if (inputstream != null && outputstream != null) {
			return 1;
		}

		UsbAccessory[] accessories = usbmanager.getAccessoryList();
		if(accessories != null)
		{
			Toast.makeText(global_context, "Accessory Attached", Toast.LENGTH_SHORT).show();
		}
		else
		{
			accessory_attached = false;
			return 2;
		}

		// variable x = (expression) ? value if true : value if false
		UsbAccessory accessory = (accessories == null ? null : accessories[0]);
		if (accessory != null) {
			if(!accessory.toString().contains(ManufacturerString))
			{
				Toast.makeText(global_context, "Manufacturer is not matched!", Toast.LENGTH_SHORT).show();
				return 1;
			}

			if(!accessory.toString().contains(ModelString1) && !accessory.toString().contains(ModelString2))
			{
				Toast.makeText(global_context, "Model is not matched!", Toast.LENGTH_SHORT).show();
				return 1;
			}

			if(!accessory.toString().contains(VersionString))
			{
				Toast.makeText(global_context, "Version is not matched!", Toast.LENGTH_SHORT).show();
				return 1;
			}

			Toast.makeText(global_context, "Manufacturer, Model & Version are matched!", Toast.LENGTH_SHORT).show();
			accessory_attached = true;

			if (usbmanager.hasPermission(accessory)) {
				openAccessory(accessory);
			} 
			else
			{
				synchronized (mUsbReceiver) {
					if (!mPermissionRequestPending) {
						Toast.makeText(global_context, "Request USB Permission", Toast.LENGTH_SHORT).show();
						usbmanager.requestPermission(accessory,mPermissionIntent);
						mPermissionRequestPending = true;
					}
				}
			}
		}

		return 0;
	}

	/*destroy accessory*/
	public void destroyAccessory(boolean bConfiged){

		if(bConfiged){
			READ_ENABLE = false;  // set false condition for handler_thread to exit waiting data loop
			writeusbdata[0] = 0;  // send dummy data for instream.read going
			sendPacket(1);
		}
		else
		{
			setConfig();  // send default setting data for config
			try{Thread.sleep(10);}
			catch(Exception e){e.printStackTrace();}

			READ_ENABLE = false;  // set false condition for handler_thread to exit waiting data loop
			writeusbdata[0] = 0;  // send dummy data for instream.read going
			sendPacket(1);
			if(accessory_attached)
			{
				saveDefaultPreference();
			}
		}

		try{Thread.sleep(10);}
		catch(Exception e){e.printStackTrace();}
		closeAccessory();
	}

	/*********************helper routines*************************************************/		

	public void openAccessory(UsbAccessory accessory)
	{
		filedescriptor = usbmanager.openAccessory(accessory);
		if(filedescriptor != null){
			usbaccessory = accessory;

			FileDescriptor fd = filedescriptor.getFileDescriptor();

			inputstream = new FileInputStream(fd);
			outputstream = new FileOutputStream(fd);
			/*check if any of them are null*/
			if(inputstream == null || outputstream==null){
				return;
			}

			if(!READ_ENABLE){
				READ_ENABLE = true;
				readThread = new read_thread(inputstream);
				readThread.start();
			}
		}
	}

	private void closeAccessory()
	{
		try{
			if(filedescriptor != null)
				filedescriptor.close();

		}catch (IOException e){e.printStackTrace();}
		try {
			if(inputstream != null)
				inputstream.close();
		} catch(IOException e){e.printStackTrace();}

		try {
			if(outputstream != null)
				outputstream.close();

		}catch(IOException e){e.printStackTrace();}
		/*FIXME, add the notification also to close the application*/

		filedescriptor = null;
		inputstream = null;
		outputstream = null;

		System.exit(0);
	}

	protected void saveDetachPreference() {
		if(intsharePrefSettings != null)
		{
			intsharePrefSettings.edit()
			.putString("configed", "FALSE")
			.apply();
		}
	}

	protected void saveDefaultPreference() {
		if(intsharePrefSettings != null)
		{
			intsharePrefSettings.edit().putString("configed", "TRUE").apply();
			intsharePrefSettings.edit().putInt("baudRate", 9600).apply();
			intsharePrefSettings.edit().putInt("stopBit", 1).apply();
			intsharePrefSettings.edit().putInt("dataBit", 8).apply();
			intsharePrefSettings.edit().putInt("parity", 0).apply();
			intsharePrefSettings.edit().putInt("flowControl", 0).apply();
		}
	}

	/***********USB broadcast receiver*******************************************/
	private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() 
	{
		@Override
		public void onReceive(Context context, Intent intent) 
		{
			String action = intent.getAction();
			if (ACTION_USB_PERMISSION.equals(action)) 
			{
				synchronized (this)
				{
					UsbAccessory accessory = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
					if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
					{
						Toast.makeText(global_context, "Allow USB Permission", Toast.LENGTH_SHORT).show();
						openAccessory(accessory);
					} 
					else 
					{
						Toast.makeText(global_context, "Deny USB Permission", Toast.LENGTH_SHORT).show();
						Log.d("LED", "permission denied for accessory "+ accessory);

					}
					mPermissionRequestPending = false;
				}
			} 
			else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) 
			{
				saveDetachPreference();
				destroyAccessory(true);
				//closeAccessory();
			}else
			{
				Log.d("LED", "....");
			}
		}	
	};

	/*usb input data handler*/
	private class read_thread  extends Thread 
	{
		FileInputStream instream;

		read_thread(FileInputStream stream ){
			instream = stream;
			this.setPriority(Thread.MAX_PRIORITY);
		}

		public void run()
		{		
			while(READ_ENABLE)
			{
				while(totalBytes > (maxnumbytes - 1024))
				{
					try 
					{
						Thread.sleep(50);
					}
					catch (InterruptedException e) {e.printStackTrace();}
				}

				try
				{
					if(instream != null)
					{
						int readcount = instream.read(usbdata, 0, 1024);
						if(readcount > 0)
						{
							for(int count = 0;count< readcount;count++)
							{					    			
								readBuffer[writeIndex] = usbdata[count];
								writeIndex++;
								writeIndex %= maxnumbytes;
							}

							if(writeIndex >= readIndex)
								totalBytes = writeIndex-readIndex;
							else
								totalBytes = (maxnumbytes-readIndex)+writeIndex;

//					    		Log.e(">>@@","totalBytes:"+totalBytes);
						}
					}
				}
				catch (IOException e){e.printStackTrace();}
			}
		}
	}
}