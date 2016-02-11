package ru.delargo.btarduion;

import android.app.Service;
import android.content.Intent;
import android.os.*;
import android.os.Process;
import android.util.Log;

import java.util.Set;

/**
 * User: Sergey Vasilenko
 * Date: 02.06.13
 * Time: 21:06
 */
public abstract class LoopService extends Service {

	private static final String TAG = "whp.LoopService";

	protected static final boolean DEBUG = true;

	public static final int ACTION_INIT = 0;
	public static final int ACTION_START = 1;
	public static final int ACTION_STOP = 2;
	public static final int ACTION_LOOP = 3;

	private Looper mServiceLooper;
	private ServiceHandler mServiceHandler;
	private Messenger mMessenger;

	private final String mServiceName;

	private boolean isStopped = true;

	public LoopService(String serviceName) {
		mServiceName = serviceName;
	}

	private final class ServiceHandler extends Handler {
		public ServiceHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {
			int action = msg.what;
			switch (action) {
				case ACTION_INIT:
					if (DEBUG)
						Log.d(TAG, "init, startId = " + msg.arg1);
					return;
				case ACTION_START:
					if (DEBUG)
						Log.v(TAG, "start");
					if (!isStopped) {
						if (DEBUG)
							Log.d(TAG, "service have already started");
						return;
					}
					isStopped = false;
					break;
				case ACTION_STOP:
					if (DEBUG)
						Log.v(TAG, "stop: " + LoopService.this.getClass().getSimpleName() + ":" + LoopService.this.hashCode());
					isStopped = true;
					break;
				case ACTION_LOOP:
					//Log.d(TAG, "loop: " + LoopService.this.getClass().getSimpleName() + ":" + LoopService.this.hashCode());
					break;
				default:
					super.handleMessage(msg);
					return;
			}
			if (!isStopped) {
				//Log.v(TAG, "onLoop: " + LoopService.this.getClass().getSimpleName() + ":" + LoopService.this.hashCode());
				onLoop();
				if (isStopped) {
					return;
				}
				Message newMsg = obtainMessage(ACTION_LOOP);
				sendMessage(newMsg);
			}
		}
	}

	@Override
	public void onCreate() {
		if (DEBUG)
			Log.i(TAG, "LoopService onCreate: " + getClass().getSimpleName() + ":" + LoopService.this.hashCode());
		createServiceHandler();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (DEBUG) {
			Log.v(TAG, "onStartCommand: " + getClass().getSimpleName() + ":" + LoopService.this.hashCode()
					+ " flags: " + flags + " startId: " + startId + " intent: ");
			logExtras(intent.getExtras());
		}
		if (mServiceHandler == null) {
			createServiceHandler();
		}
		Message msg = mServiceHandler.obtainMessage();
		msg.arg1 = startId;
		msg.what = ACTION_INIT;
		mServiceHandler.sendMessage(msg);
		start();
		return START_REDELIVER_INTENT;
	}

	private void createServiceHandler() {
		HandlerThread thread = new HandlerThread(mServiceName, Process.THREAD_PRIORITY_BACKGROUND);
		thread.start();
		mServiceLooper = thread.getLooper();
		mServiceHandler = new ServiceHandler(mServiceLooper);
		mMessenger = new Messenger(mServiceHandler);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mMessenger.getBinder();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		stop();
	}

	protected abstract void onLoop();

	public synchronized void stop() {
		if (isStopped || mServiceHandler == null) {
			return;
		}
		if (DEBUG)
			Log.v(TAG, "stop: " + LoopService.this.getClass().getSimpleName() + ":" + LoopService.this.hashCode());
		isStopped = true;
		mServiceLooper.quit();
		mServiceLooper = null;
		mServiceHandler = null;
	}

	public synchronized void start() {
		if (mServiceHandler == null) {
			//if stop before start
			return;
		}
		if (DEBUG)
			Log.v(TAG, "start " + LoopService.this.getClass().getSimpleName() + ":" + LoopService.this.hashCode());
		Message msg = mServiceHandler.obtainMessage();
		msg.what = ACTION_START;
		mServiceHandler.removeMessages(ACTION_LOOP);
		mServiceHandler.sendMessage(msg);
	}

	protected boolean isStopped() {
		return isStopped;
	}

	public void logExtras(Bundle bundle) {
		if (bundle == null) {
			return;
		}
		Set<String> keys = bundle.keySet();
		if (keys != null) {
			for (String key : keys) {
				Log.d(TAG, getClass().getSimpleName() + ": " + key + " : " + bundle.get(key));
			}
		}
	}
}

