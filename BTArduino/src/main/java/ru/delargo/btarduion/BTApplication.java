package ru.delargo.btarduion;

import android.app.Application;
import android.bluetooth.BluetoothSocket;

/**
 * Created by Sergey Vasilenko on 23.03.14.
 */
public class BTApplication extends Application {

	private static BTApplication mApplication;
	private BluetoothSocket mBluetoothSocket;

	@Override
	public void onCreate() {
		super.onCreate();
		mApplication = this;
	}

	public static BTApplication getInstance() {
		return mApplication;
	}

	public BluetoothSocket getBluetoothSocket() {
		return mBluetoothSocket;
	}

	public void setBluetoothSocket(BluetoothSocket bluetoothSocket) {
		mBluetoothSocket = bluetoothSocket;
	}
}
