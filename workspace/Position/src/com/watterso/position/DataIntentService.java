package com.watterso.position;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.PriorityQueue;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.IBinder;

public class DataIntentService extends IntentService implements SensorEventListener {
	private static final int MAX_ACCEL = 50;
	private static final int MAX_WIFI = 500;
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
	private PriorityQueue<Long> mWTimeStamps;
	public DataIntentService(){
		super("WIFIVEL");

		mGravity = new float[3];
		mGeoMagnetic = new float[3];
		mLinearAcc = new float[3];
		
		mAData = new HashMap<Long, Float[]>();
		mWData = new HashMap<Long, HashMap<String,Integer>>();
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
				testAccellerometerSize(event.timestamp, rotatedAcc);
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
	private void testAccellerometerSize(long timestamp, Float[] accData){
		if(mAData.size()>=MAX_ACCEL){
			calcVelocity();
			mAData.clear();
		}
		mAData.put(timestamp, accData);
	}
	
	private void calcVelocity() {
		Long[] times = new Long[1];
		times = mAData.keySet().toArray(times);
		Arrays.sort(times);
		Long t0 = times[0];
		Long tf = times[times.length-1];
		int cnt = 0;
		HashMap<String,Integer> avgStrength = new HashMap<String, Integer>();
		for(Long timeStamp : mWData.keySet()){
			if(timeStamp>t0 && timeStamp<tf){
				HashMap<String,Integer> lTmp  = mWData.get(timeStamp);
				for(String bssid : lTmp.keySet()){
					avgStrength.put(bssid, avgStrength.get(bssid)+lTmp.get(bssid));
				}
				cnt++;
			}
		}
		for(String bssid : avgStrength.keySet()){
			avgStrength.put(bssid, (int)(avgStrength.get(bssid)/cnt+.5));	//avg rounded
		}
		
		float x = 0;
		float y = 0;
		ArrayList<Float []> tmp = (ArrayList<Float []>)mAData.values();
		for(int i =0; i< tmp.size()-1; i++){
			Float[] arr = tmp.get(i);
			Float[] arr1 = tmp.get(i+1);
			x+=adaptive(arr[0],arr1[0]);
			y+=adaptive(arr[1],arr1[1]);
		}
	}

	//TODO: Stop being lazy and do integration on your own
	//Adapted from:
	//http://introcs.cs.princeton.edu/java/93integration/AdaptiveQuadrature.java.html
	private final static double EPSILON = 1E-6;
	// adaptive quadrature
    public static float adaptive(float a, float b) {
        float h = b - a;
        float c = (float) ((a + b) / 2.0);
        float d = (float) ((a + c) / 2.0);
        float e = (float) ((b + c) / 2.0);
        float Q1 = h/6  * (f(a) + 4*f(c) + f(b));
        float Q2 = h/12 * (f(a) + 4*f(d) + 2*f(c) + 4*f(e) + f(b));
        if (Math.abs(Q2 - Q1) <= EPSILON)
            return Q2 + (Q2 - Q1) / 15;
        else
            return adaptive(a, c) + adaptive(c, b);
    }
    /**********************************************************************
     * Standard normal distribution density function.
     * Replace with any sufficiently smooth function.
     **********************************************************************/
     static float f(float x) {
         return (float) (Math.exp(- x * x / 2) / Math.sqrt(2 * Math.PI));
     }
}
