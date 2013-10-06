package com.watterso.position;

import java.util.Arrays;
import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.Queue;

import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class DataIntentService extends IntentService implements SensorEventListener {
	private static final int MAX_ACCEL = 500;
	private static final int MAX_WIFI = 100;
	private final IBinder mBinder = new DataBinder();
	private Intent mReceivedIntent;
	// Sensors
	private SensorManager mSensorManager;
	private Sensor mLinearAcceleration;
	private Sensor mAccelerometer;
	private Sensor mMagneticField;
	// Stored Sensor Vals
	private float[] mGravity;
	private float[] mGeoMagnetic;
	private float[] mLinearAcc;
	private HashMap<Long, Float[]> mAData;
	private HashMap<Long, HashMap<String, Integer>> mWData;
	private PriorityQueue<Long> mATimeStamps;
	private PriorityQueue<Long> mWTimeStamps;
	public DataIntentService(){
		super("WIFIVEL");

		mGravity = new float[3];
		mGeoMagnetic = new float[3];
		mLinearAcc = new float[3];
		
		mAData = new HashMap<Long, Float[]>();
		mWData = new HashMap<Long, HashMap<String,Integer>>();
		mATimeStamps = new PriorityQueue<Long>();
		mWTimeStamps = new PriorityQueue<Long>();
	}
	public void setupSensors(Context c){
		mSensorManager = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
		mLinearAcceleration = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
		mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		mMagneticField = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
		
		mSensorManager.registerListener(this, mLinearAcceleration, SensorManager.SENSOR_DELAY_NORMAL);
		mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
		mSensorManager.registerListener(this, mMagneticField, SensorManager.SENSOR_DELAY_NORMAL);
	}
	@Override
	public void onDestroy() {
		super.onDestroy();
		mSensorManager.unregisterListener(this);
	}

	@Override
	public IBinder onBind(Intent intent) {
		mReceivedIntent = intent;
		return mBinder;
	}

	public class DataBinder extends Binder {
		public DataIntentService getService() {
			return DataIntentService.this;
		}
	}

	// NAIVE ROTATION ASSUMES CORRECT USAGE OF 3D VECTOR
	public static Float[] rotateVector(float[] v, float[] r) {
		Float[] oot = new Float[3];
		for (int i = 0; i < oot.length; i++) {
			oot[i] = r[3 * i] * v[0] + r[3 * i + 1] * v[1] + r[3 * i + 2]
					* v[2];
		}
		return oot;
	}
	
	@Override
	public void onSensorChanged(SensorEvent event) {
		switch (event.sensor.getType()) {
		case Sensor.TYPE_ACCELEROMETER:
			mGravity = Arrays.copyOf(event.values, 3);
			break;
		case Sensor.TYPE_MAGNETIC_FIELD:
			mGeoMagnetic = Arrays.copyOf(event.values, 3);
			break;
		case Sensor.TYPE_LINEAR_ACCELERATION:
			mLinearAcc = Arrays.copyOf(event.values, 3);
			break;
		}
		float[] rotation = new float[9];
		if (SensorManager.getRotationMatrix(rotation, null, mGravity,
				mGeoMagnetic)) {
			// filter small changes
			if (Math.abs(mLinearAcc[0]) > .1 || Math.abs(mLinearAcc[1]) > .1
					|| Math.abs(mLinearAcc[2]) > .1) {
				Float[] rotatedAcc = new Float[3];
				rotatedAcc = rotateVector(mLinearAcc, rotation);
				mAData.put(event.timestamp, rotatedAcc);
				mATimeStamps.offer(event.timestamp);
				testAccellerometerSize();
				// Log.d("ROTATE", Arrays.toString(rotation));
				// Log.d("LINEAR", Arrays.toString(mLinearAcc));
				// Log.d("YOLO",
				// ""+rotatedAcc[2]);//Arrays.toString(rotatedAcc));
			}
		}
	}

	public HashMap<Long, Float[]> getAccelerometerData(){
		return mAData;
	}
	public HashMap<Long, HashMap<String, Integer>> getWifiData(){
		return mWData;
	}
	
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
	}
	
	@Override
	protected void onHandleIntent(Intent intent) {
		
	}
	public void pushWifiData(Long time,HashMap<String, Integer> snapshot) {
		mWData.put(time, snapshot);
		mWTimeStamps.offer(time);
		testWifiSize();
	}
	private void testWifiSize(){
		if(mWTimeStamps.size()>MAX_WIFI){
			Long pop = mWTimeStamps.poll();
			mWData.remove(pop);
		}
	}
	private void testAccellerometerSize(){
		if(mATimeStamps.size()>MAX_ACCEL){
			Long pop = mATimeStamps.poll();
			mAData.remove(pop);
		}
	}
}
