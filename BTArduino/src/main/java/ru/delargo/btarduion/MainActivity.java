package ru.delargo.btarduion;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

/**
 * Created by Sergey Vasilenko on 21.03.14.
 */
public class MainActivity extends Activity {

	private static final String TAG = "MainActivity";

	private static final int REQUEST_ENABLE_BT = 1;

	public static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

	private static final int REQUEST_LED_ON = 1;
	private static final int REQUEST_LED_OFF = 0;
	private static final int REQUEST_TEMP_AND_HUMIDITY = 2;

	private TextView tvState;
	private TextView tvTemperature;
	private TextView tvHumidity;
	private BluetoothAdapter mBluetoothAdapter;
	private BluetoothSocket mBtSocket;

	private BTSendTask mSendTask;

	private ResponseReceiver mResponseReceiver;
	private Handler mHandler;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mHandler = new Handler();
		setContentView(R.layout.main);
		findViewById(R.id.bLedOn).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setLedState(true);
			}
		});
		findViewById(R.id.bLedOff).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setLedState(false);
			}
		});
		findViewById(R.id.bGetTH).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				getTempAndHumidity();
			}
		});
		tvState = (TextView) findViewById(R.id.tvState);
		tvTemperature = (TextView) findViewById(R.id.tvTemperature);
		tvHumidity = (TextView) findViewById(R.id.tvHumidity);
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mBluetoothAdapter.isEnabled()) {
			followConnect();
		} else {
			Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(intent, REQUEST_ENABLE_BT);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (mBluetoothAdapter.isEnabled() && mBtSocket == null) {
			followConnect();
		}
		IntentFilter intentFilter = new IntentFilter(BTReaderService.ACTION_BT_RECEIVED);
		mResponseReceiver = new ResponseReceiver();
		LocalBroadcastManager.getInstance(this).registerReceiver(mResponseReceiver, intentFilter);

		Intent btServiceIntent = new Intent(this, BTReaderService.class);
		startService(btServiceIntent);
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (mBtSocket != null) {
			try {
				mBtSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				mBtSocket = null;
			}
		}
		LocalBroadcastManager.getInstance(this).unregisterReceiver(mResponseReceiver);
		mResponseReceiver = null;

		Intent btServiceIntent = new Intent(this, BTReaderService.class);
		stopService(btServiceIntent);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQUEST_ENABLE_BT) {
			followConnect();
		}
	}

	private void setLedState(boolean switchOn) {
		if (mSendTask != null) {
			return;
		}
		mSendTask = new BTSendTask();
		mSendTask.execute(switchOn ? REQUEST_LED_ON : REQUEST_LED_OFF);
	}

	private void getTempAndHumidity() {
		if (mSendTask != null) {
			mHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					getTempAndHumidity();
				}
			}, 1000);
			return;
		}
		mSendTask = new BTSendTask();
		mSendTask.execute(REQUEST_TEMP_AND_HUMIDITY);
	}

	private void followConnect() {
		Set<BluetoothDevice> devices = mBluetoothAdapter.getBondedDevices();
		BluetoothDevice device = null;
		for (BluetoothDevice d : devices) {
			if (d.getName().equalsIgnoreCase("HC-06")) {
				device = d;
				break;
			}
		}

		if (device == null) {
			Log.e(TAG, "Error, can not find device");
			return;
		}
		try {
			mBtSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
			mBluetoothAdapter.cancelDiscovery();
			mBtSocket.connect();
			BTApplication.getInstance().setBluetoothSocket(mBtSocket);
		} catch (IOException e) {
			e.printStackTrace();
			if (mBtSocket != null) {
				try {
					mBtSocket.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
		}
	}

	private void onReceive(Intent intent) {
		String responseType = intent.getStringExtra(BTReaderService.EXTRA_RESPONSE_TYPE);
		switch (responseType) {
			case BTReaderService.RESPONSE_LED:
				boolean isLedOn = intent.getBooleanExtra(BTReaderService.EXTRA_LED_STATE, false);
				onLedStateChanged(isLedOn);
				break;
			case BTReaderService.RESPONSE_ERROR:
				String errorReason = intent.getStringExtra(BTReaderService.EXTRA_ERROR_REASON);
				onError(errorReason);
				break;
			case BTReaderService.RESPONSE_HUMIDITY:
				float humidity = intent.getFloatExtra(BTReaderService.EXTRA_HUMIDITY, Float.NaN);
				onHumidityReceived(humidity);
				break;
			case BTReaderService.RESPONSE_TEMPERATURE:
				float temperature = intent.getFloatExtra(BTReaderService.EXTRA_TEMPERATURE, Float.NaN);
				onTemperatureReceived(temperature);
				break;
		}
	}

	private void onLedStateChanged(boolean isOn) {
		Log.v(TAG, "onLedStateChanged: " + (isOn ? "On" : "Off"));
		tvState.setText(isOn ? "On" : "Off");
	}

	private void onError(String errorReason) {
		Log.v(TAG, "onError: " + errorReason);
		Toast.makeText(this, "Error! Reason: " + errorReason, Toast.LENGTH_SHORT).show();
	}

	private void onHumidityReceived(float humidity) {
		Log.v(TAG, "onHumidityReceived: " + humidity);
		tvHumidity.setText(String.valueOf(humidity));
	}

	private void onTemperatureReceived(float temperature) {
		Log.v(TAG, "onTemperatureReceived: " + temperature);
		tvTemperature.setText(String.valueOf(temperature));
	}


	private class BTSendTask extends AsyncTask<Integer, Void, Void> {

		@Override
		protected Void doInBackground(Integer... params) {
			int requestType = params[0];
			String message = String.valueOf(requestType);
			byte[] msgBuffer = message.getBytes();
			try {
				OutputStream os = mBtSocket.getOutputStream();
				os.write(msgBuffer);
			} catch (IOException e) {
				e.printStackTrace();
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void aVoid) {
			mSendTask = null;
		}
	}

	private final class ResponseReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, final Intent intent) {
			if (Looper.getMainLooper() != Looper.myLooper()) {
				mHandler.post(new Runnable() {
					@Override
					public void run() {
						MainActivity.this.onReceive(intent);
					}
				});
			} else {
				MainActivity.this.onReceive(intent);
			}
		}
	}
}
