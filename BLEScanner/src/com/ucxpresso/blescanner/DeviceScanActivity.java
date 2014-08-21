/*
 * 2014/8/20
 * BLE Scanner
 * Author: Jason
 * Copyright: Embeda International Inc. (www.ucxpresso.net)
 * License: GPL 3.0 
 */

package com.ucxpresso.blescanner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import com.ucxpresso.blescanner.bleDeviceT;
import com.ucxpresso.blescanner.R;

/**
 * Activity for scanning and displaying available BLE devices.
 */
public class DeviceScanActivity extends ListActivity {
	ArrayList<String> listItems = new ArrayList<String>();
	ArrayAdapter<String> adapter;

	private static final String TAG = DeviceScanActivity.class.getSimpleName();
	private BluetoothAdapter mBluetoothAdapter;
	private boolean mScanning;
	private Handler mHandler = new Handler();
	// private int scanCount;
	private static final int REQUEST_ENABLE_BT = 123456;

	// Stops scanning after 10 seconds.
	private static final long SCAN_PERIOD = 30000;
	private static final int MAX_SAMPLE = 9;
	private static final int MID_SAMPLE = 4;

	// ble devices array
	ArrayList<bleDeviceT> devices = new ArrayList<bleDeviceT>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		adapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_1, listItems);
		setListAdapter(adapter);

		checkBLE();
		init();
		boolean ret = enableBLE();
		if (ret) {
			startScan(false);
		} else {
			Log.d(TAG, getCtx() + " onCreate Waiting for on onActivityResult");
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	private void init() {
		// Initializes Bluetooth adapter.
		final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		mBluetoothAdapter = bluetoothManager.getAdapter();
	}

	private void startScan(boolean success) {
		if (mBluetoothAdapter == null) {
			init();
		}
		if (success) {
			mScanning = true;
			scanLeDevice(mScanning);
			return;
		}
		if (enableBLE()) {
			mScanning = true;
			scanLeDevice(mScanning);
		} else {
			Log.d(TAG, getCtx()
					+ " startScan Waiting for on onActivityResult success:"
					+ success);
		}
	}

	private void scanLeDevice(final boolean enable) {
		if (enable) {
			// Stops scanning after a pre-defined scan period.
			mHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					mScanning = false;
					Log.d(TAG, getCtx() + "run stopLeScan");
					mBluetoothAdapter.stopLeScan(mLeScanCallback);
				}
			}, SCAN_PERIOD);
			Log.d(TAG, getCtx() + " scanLeDevice startLeScan:" + enable);
			mBluetoothAdapter.startLeScan(mLeScanCallback);
		} else {
			Log.d(TAG, getCtx() + " scanLeDevice stopLeScan:" + enable);
			mBluetoothAdapter.stopLeScan(mLeScanCallback);
		}
	}

	private static String getCtx() {
		Date dt = new Date();
		return dt + " thread:" + Thread.currentThread().getName();
	}

	// Device scan callback.
	private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
		@Override
		public void onLeScan(final BluetoothDevice device, final int rssi,
				final byte[] scanRecord) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
//					try{
						
						// check devices list
						for (int i = 0; i < devices.size(); i++) {
							if (devices.get(i).device.equals(device)) {
								if ( devices.get(i).scancount<MAX_SAMPLE ) {
									devices.get(i).rssi[devices.get(i).scancount] = rssi;
									devices.get(i).scancount++;
									if ( devices.get(i).scancount==MAX_SAMPLE ) {
										String msg = getCtx();
										// filter RSSIs
										Arrays.sort(devices.get(i).rssi);	// sort RSSIs
										devices.get(i).distance = calculateAccuracy(devices.get(i).txPowerLevel, devices.get(i).rssi[MID_SAMPLE]);
										
										// show BLE item
										msg += "\nName = " + devices.get(i).device.getName();
										msg += "\nMF =" + devices.get(i).mfCode;
										msg += "\nAddr = " + devices.get(i).device.getAddress();
										msg += "\nTxPower = " + devices.get(i).txPowerLevel;
										msg += "\nRSSI = " + devices.get(i).rssi[MID_SAMPLE];
										msg += String.format("\nDistance = %1$,.2f\n", devices.get(i).distance); // */
										addItems(msg);
									}
								} 
								return;
							}
						}
						
						// new ble device
						bleDeviceT ble = new bleDeviceT();
						ble.device = device;
						
						ble.rssi = new int[MAX_SAMPLE];
						ble.rssi[0] = rssi;
						
						ble.scancount = 1;
						ble.txPowerLevel = -59;	// default
						
						//
						// phase the ble scanRecord (Advertising Package)
						//
						byte length;
						byte adType;
						try{
							for (int i = 0; i < scanRecord.length;) {
								length = scanRecord[i];
								adType = scanRecord[i + 1];
								if (length > 0) {
									switch (adType) {
									//
									// update TxPower Level
									//
									case 0x0A: 
										ble.txPowerLevel = scanRecord[i + 2];
										break;
									
									//
									// update Manufacture Code
									//
									case (byte) 0xff:	
										ble.mfCode = scanRecord[i+2];
										if ( length>2 ) {
											ble.mfCode = (ble.mfCode<<8) + scanRecord[i+3];
										}
										if ( length>3 ) {
											ble.mfCode = (ble.mfCode<<8) + scanRecord[i+4];
										}
										break;
										
									default:
										break;
									}
								}
								i += (length + 1);
							} // end of for-loop
						} catch(Exception ex) {
							Log.d(TAG, getCtx() + " Error:" + ex.getMessage());
						}
						// add new ble device into list
						devices.add(ble);
						
/*					} catch (Exception ex) {
						Log.d(TAG, getCtx() + " Error:" + ex.getMessage());
					} // */
				} // end of run()
			});
		}
	};

	private void addItems(String msg) {
		synchronized (listItems) {
			listItems.add(msg);
			adapter.notifyDataSetChanged();
		}
	}

	public void startScan(View v) {
		devices.clear();
		startScan(false);
	}

	public void stopScan(View v) {
		mScanning = false;
		scanLeDevice(mScanning);
	}

	public void clear(View v) {
		Log.d(TAG, getCtx() + " called clear");
		synchronized (listItems) {
			listItems.clear();
			adapter.notifyDataSetChanged();
		}
		devices.clear();
	}

	private void checkBLE() {
		// Use this check to determine whether BLE is supported on the device.
		// Then
		// you can selectively disable BLE-related features.
		if (!getPackageManager().hasSystemFeature(
				PackageManager.FEATURE_BLUETOOTH_LE)) {
			Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT)
					.show();
			finish();
		}
	}

	private boolean enableBLE() {
		boolean ret = true;
		// Ensures Bluetooth is available on the device and it is enabled. If
		// not,
		// displays a dialog requesting user permission to enable Bluetooth.
		if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
			Log.d(TAG,
					getCtx()
							+ " enableBLE either mBluetoothAdapter == null or disabled:"
							+ mBluetoothAdapter);
			Intent enableBtIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
			ret = false;
		}
		return ret;
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.d(TAG, getCtx() + " onActivityResult requestCode=" + requestCode
				+ ", resultCode=" + resultCode + ", Intent:" + data);
		if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
			startScan(true);
		}
	}

	protected static double calculateAccuracy(int txPower, double rssi) {
		if (rssi == 0) {
			return -1.0; // if we cannot determine accuracy, return -1.
		}
		
		if ( txPower == 0) {
			txPower = -65;	// default calibrate txPower @ 1 meter
		}

/*		double ratio = rssi * 1.0 / txPower;
		if (ratio < 1.0) {
			return Math.pow(ratio, 10);
		} else {
			double accuracy = (0.89976) * Math.pow(ratio, 7.7095) + 0.111;
			return accuracy;
		} // */
		
		double ratio_db = txPower - rssi;
		double ratio_linear = Math.pow(10, ratio_db/10);
		double accuracy = Math.sqrt(ratio_linear);
		return accuracy;
	}
}
