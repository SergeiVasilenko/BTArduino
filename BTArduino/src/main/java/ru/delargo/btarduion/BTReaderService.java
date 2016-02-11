package ru.delargo.btarduion;

import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by Sergey Vasilenko on 23.03.14.
 */
public class BTReaderService extends LoopService {

	private static final String TAG = "BTReaderService";
	private static final int BUFFER_SIZE = 1024;

	public static final String ACTION_BT_RECEIVED = "ACTION_BT_RECEIVED";

	public static final String EXTRA_RESPONSE_TYPE = "EXTRA_RESPONSE_TYPE";
	public static final String EXTRA_LED_STATE = "EXTRA_LED_STATE";
	public static final String EXTRA_ERROR_REASON = "EXTRA_ERROR_REASON";
	public static final String EXTRA_TEMPERATURE = "EXTRA_TEMPERATURE";
	public static final String EXTRA_HUMIDITY = "EXTRA_HUMIDITY";

	public static final String RESPONSE_LED = "LED";
	public static final String RESPONSE_ERROR = "Error";
	public static final String RESPONSE_TEMPERATURE = "Temperature";
	public static final String RESPONSE_HUMIDITY = "Humidity";

	private BluetoothSocket mBtSocket;
	private byte[] buffer;

	private boolean mIsConnected = false;
	private StringBuilder mBuilder;

	public BTReaderService() {
		super("BTReaderService");
	}

	@Override
	public void onCreate() {
		super.onCreate();
		buffer = new byte[BUFFER_SIZE];
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		buffer = null;
		mBtSocket = null;
	}

	@Override
	protected void onLoop() {
		if (mBtSocket == null) {
			mBtSocket = BTApplication.getInstance().getBluetoothSocket();
			if (mBtSocket == null) {
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				return;
			}
		}
		try {
			InputStream is = mBtSocket.getInputStream();
			boolean isInReadingProcess = false;
			do {
				if (is.available() > 0) {
					if (mBuilder == null) {
						mBuilder = new StringBuilder();
					}
					isInReadingProcess = true;
					int bytes = is.read(buffer);
					String responsePart = new String(buffer, 0, bytes, "UTF-8");
					mBuilder.append(responsePart);
					Log.v(TAG, "onLoop: responsePart " + responsePart);
					if (responsePart.charAt(responsePart.length() - 1) == '\n'
							|| responsePart.charAt(responsePart.length() - 1) == '\r') {
						isInReadingProcess = false;
						String response = mBuilder.toString();
						response = response.trim();
						// response = response.replaceAll("[\\n\\r]+", "");
						Log.v(TAG, "onLoop: response " + response);
						if (response.startsWith(RESPONSE_ERROR)) {
							onErrorResponse(response);
						} else if (response.startsWith(RESPONSE_LED)) {
							onLedResponse(response);
						} else if (response.startsWith(RESPONSE_HUMIDITY)) {
							onHumidityResponse(response);
						} else if (response.startsWith(RESPONSE_TEMPERATURE)) {
							onTemperatureResponse(response);
						} else {
							Log.w(TAG, "Unknown response: " + response);
						}
					}
				}
			} while (isInReadingProcess);
		} catch (IOException e) {
			e.printStackTrace();
		}
		mBuilder = null;
	}

	private void onLedResponse(String response) {
		String state = response.substring(RESPONSE_LED.length() + 1, response.length());
		boolean isLedOn = state.equalsIgnoreCase("ON");
		Intent intent = new Intent(ACTION_BT_RECEIVED);
		intent.putExtra(EXTRA_RESPONSE_TYPE, RESPONSE_LED);
		intent.putExtra(EXTRA_LED_STATE, isLedOn);
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}

	private void onErrorResponse(String response) {
		String reason = "";
		if (response.length() > RESPONSE_ERROR.length()) {
			reason = response.substring(RESPONSE_ERROR.length() + 1, response.length());
		}
		Intent intent = new Intent(ACTION_BT_RECEIVED);
		intent.putExtra(EXTRA_RESPONSE_TYPE, RESPONSE_ERROR);
		intent.putExtra(EXTRA_ERROR_REASON, reason);
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}

	private void onHumidityResponse(String response) {
		String humidityText = response.substring(RESPONSE_HUMIDITY.length() + 1, response.length());
		float humidity = Float.valueOf(humidityText);
		Intent intent = new Intent(ACTION_BT_RECEIVED);
		intent.putExtra(EXTRA_RESPONSE_TYPE, RESPONSE_HUMIDITY);
		intent.putExtra(EXTRA_HUMIDITY, humidity);
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}

	private void onTemperatureResponse(String response) {
		String temperatureText = response.substring(RESPONSE_TEMPERATURE.length() + 1, response.length());
		float temperature = Float.valueOf(temperatureText);
		Intent intent = new Intent(ACTION_BT_RECEIVED);
		intent.putExtra(EXTRA_RESPONSE_TYPE, RESPONSE_TEMPERATURE);
		intent.putExtra(EXTRA_TEMPERATURE, temperature);
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}
}
