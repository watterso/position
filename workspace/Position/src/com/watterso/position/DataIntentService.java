package com.watterso.position;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.PriorityQueue;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class DataIntentService extends IntentService implements
		SensorEventListener {
	public class DeltaSnapShot {
		public float x;
		public float y;
		public long time;

		public DeltaSnapShot(float lx, float ly, long t) {
			x = lx;
			y = ly;
			time = t;
		}
	}

	public static final String MONGO_API = "jIHkv3sxi0kID-9TRpmg_5Yckd2dKF7M";
	public static final String MONGO_URL = "https://api.mongolab.com/api/1/databases/positioning/collections/wifi?apiKey="
			+ MONGO_API;
	private static final int MAX_ACCEL = 50;
	private static final int MAX_WIFI = 500;
	private final IBinder mBinder = new DataBinder();
	private Intent mReceivedIntent;
	private MainActivity mMainActivity;
	// Sensors
	private SensorManager mSensorManager;
	private Sensor mLinearAcceleration;
	private Sensor mAccelerometer;
	private Sensor mMagneticField;
	// Stored Sensor Vals
	private float[] mGravity;
	private float[] mGeoMagnetic;
	private float[] mLinearAcc;
	private HashMap<String, DeltaSnapShot[]> mSnaps; // 0: increase, 1: decrease
	private HashMap<Long, float[]> mAData;
	private HashMap<Long, HashMap<String, Integer>> mWData;
	private HashMap<String, Integer> mRecentWifiAverages;
	private PriorityQueue<Long> mWTimeStamps;
	private PriorityQueue<Long> mATimeStamps;

	public DataIntentService() {
		super("WIFIVEL");

		mGravity = new float[3];
		mGeoMagnetic = new float[3];
		mLinearAcc = new float[3];

		mAData = new HashMap<Long, float[]>();
		mWData = new HashMap<Long, HashMap<String, Integer>>();
		mSnaps = new HashMap<String, DataIntentService.DeltaSnapShot[]>();
		mRecentWifiAverages = new HashMap<String, Integer>();
		mWTimeStamps = new PriorityQueue<Long>();
		mATimeStamps = new PriorityQueue<Long>();
	}

	public void setupSensors(Context c) {
		mSensorManager = (SensorManager) c
				.getSystemService(Context.SENSOR_SERVICE);
		mLinearAcceleration = mSensorManager
				.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
		mAccelerometer = mSensorManager
				.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		mMagneticField = mSensorManager
				.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

		mSensorManager.registerListener(this, mLinearAcceleration,
				SensorManager.SENSOR_DELAY_NORMAL);
		mSensorManager.registerListener(this, mAccelerometer,
				SensorManager.SENSOR_DELAY_NORMAL);
		mSensorManager.registerListener(this, mMagneticField,
				SensorManager.SENSOR_DELAY_NORMAL);
	}

	public void setMainActivity(MainActivity m) {
		mMainActivity = m;
	}
	
	public HashMap<String, DeltaSnapShot []> getSnaps(){
		return mSnaps;
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
		long temp = System.currentTimeMillis();
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
		//Log.d("LE", Arrays.toString(mLinearAcc));
		// filter small changes
		if (Math.abs(mLinearAcc[0]) > .1 || Math.abs(mLinearAcc[1]) > .1
				|| Math.abs(mLinearAcc[2]) > .1) {
			// Float[] rotatedAcc = new Float[3];
			// rotatedAcc = rotateVector(mLinearAcc, rotation);
			testAccellerometerSize(temp, mLinearAcc);
			// Log.d("ROTATE", Arrays.toString(rotation));
			// Log.d("LINEAR", Arrays.toString(mLinearAcc));
			// Log.d("YOLO",
			// ""+rotatedAcc[2]);//Arrays.toString(rotatedAcc));

		}
	}

	public HashMap<Long, float[]> getAccelerometerData() {
		return mAData;
	}

	public HashMap<Long, HashMap<String, Integer>> getWifiData() {
		return mWData;
	}

	HashMap<String, Integer> getWifiAverageData() {
		return mRecentWifiAverages;
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
	}

	@Override
	protected void onHandleIntent(Intent intent) {

	}

	public void pushWifiData(Long time, HashMap<String, Integer> snapshot) {
		mWData.put(time, snapshot);
		mWTimeStamps.offer(time);
		testWifiSize();
	}

	private void testWifiSize() {
		if (mWTimeStamps.size() > MAX_WIFI) {
			Long pop = mWTimeStamps.poll();
			mWData.remove(pop);
		}
	}

	private void testAccellerometerSize(long timestamp, float[] accData) {
		if (mAData.size() >= MAX_ACCEL) {
			calcVelocity();
			mAData.clear();
			mATimeStamps.clear();
		}
		mAData.put(timestamp, accData);
		mATimeStamps.offer(timestamp);
	}

	private void calcVelocity() {
		float x = 0;
		float y = 0;
		for (int i = 0; i < mAData.size() - 1; i++) {
			Long pop = mATimeStamps.poll();
			Long next = mATimeStamps.peek();
			float[] arr = mAData.get(pop);
			float[] arr1 = mAData.get(next);
			x += adaptive(arr[0], arr1[0]);
			y += adaptive(arr[1], arr1[1]);
		}

		Long[] times = new Long[1];
		times = mAData.keySet().toArray(times);
		Arrays.sort(times);
		Long t0 = times[0];
		Long tf = times[times.length - 1];
		//Log.d("TIME", t0+","+tf);
		HashMap<String, Integer> avgStrength = new HashMap<String, Integer>();
		HashMap<String, ArrayList<Integer>> tot = new HashMap<String, ArrayList<Integer>>();
		for (Long timeStamp : mWData.keySet()) {
			if (timeStamp >= t0 && timeStamp <= tf) {
				//Log.d("TIME", timeStamp+"");
				HashMap<String, Integer> lTmp = mWData.get(timeStamp);
				for (String bssid : lTmp.keySet()) {
					if (tot.get(bssid) != null) {
						tot.get(bssid).add(lTmp.get(bssid));
					} else {
						ArrayList<Integer> tmp = new ArrayList<Integer>();
						tmp.add(lTmp.get(bssid));
						tot.put(bssid, tmp);
					}
				}
			}
		}
		for (String bssid : tot.keySet()) {
			ArrayList<Integer> strengths = tot.get(bssid);
			Integer level = 0;
			for (Integer i : strengths) {
				level += i;
			}
			if (level == 0 || strengths.size() == 0)
				return;
			Collections.sort(strengths);
			Integer mean = (int) (level / strengths.size() + .5);
			//Log.d("SIZE&MEAN", "SIZE: " + strengths.size() + " MEAN: " + mean);
			Integer median = strengths.get(strengths.size() / 2);
			if (mSnaps.get(bssid) == null) {
				mSnaps.put(bssid, new DeltaSnapShot[2]);
			}
			if (median > mean) {
				// growing, index 0
				mSnaps.get(bssid)[0] = new DeltaSnapShot(x, y, tf);
			} else {
				// shrinking, index 1
				mSnaps.get(bssid)[1] = new DeltaSnapShot(x, y, tf);
			}
			avgStrength.put(bssid, mean); // avg rounded
		}
		mRecentWifiAverages = avgStrength;
		mMainActivity.updateCompass();
		if (mRecentWifiAverages.size() > 0){
			updateMongo();
		}
	}

	private void updateMongo() {
		JSONObject oot = new JSONObject();
		try {
			oot.put("device", android.os.Build.MODEL);
			JSONObject info = new JSONObject();
			for (String bssid : mRecentWifiAverages.keySet()) {
				info.put(bssid, mRecentWifiAverages.get(bssid));
			}
			oot.put("data", info);
			mMainActivity.new PutDataTask().execute(oot);
		} catch (JSONException e) {
			Log.d("YOLOJSON", e.getMessage());
		}
	}

	// DAT ALGOS
	public static Integer select(ArrayList<Integer> list, int k) {
		if (list.size() == 1) {
			return list.get(0);
		}
		Integer med = medianOfMedians(list);
		list.remove(med);

		ArrayList<Integer> greater = new ArrayList<Integer>();
		ArrayList<Integer> less = new ArrayList<Integer>();
		for (Integer i : list) {
			if (i <= med) {
				less.add(i);
			} else {
				greater.add(i);
			}
		}
		if (less.size() + 1 == k) {
			return med;
		} else if (less.size() + 1 < k) {
			return select(less, k);
		} else {
			return select(greater, k - (less.size() + 1));
		}
	}

	public static Integer medianOfMedians(ArrayList<Integer> list) {
		ArrayList<Integer> medians = new ArrayList<Integer>();
		if (list.size() == 0)
			return 0;
		if (list.size() < 5) {
			return list.get((int) (list.size() / 2));
		}
		for (int i = 0; ((i + 1) * 5 - 1) < list.size(); i++) {
			Integer[] subArr = new Integer[1];
			Log.d("YOLO", ((i + 1) * 5 - 1) + " ? " + list.size());
			subArr = list.subList(i, (i + 1) * 5 - 1).toArray(subArr);
			Arrays.sort(subArr);
			medians.add(subArr[2]);
		}
		Integer[] subArr = new Integer[1];
		subArr = medians.toArray(subArr);
		Arrays.sort(subArr);
		return subArr[subArr.length / 2];

	}

	// TODO: Stop being lazy and do integration on your own
	// Adapted from:
	// http://introcs.cs.princeton.edu/java/93integration/AdaptiveQuadrature.java.html
	private final static double EPSILON = 1E-6;

	// adaptive quadrature
	public static float adaptive(float a, float b) {
		float h = b - a;
		float c = (float) ((a + b) / 2.0);
		float d = (float) ((a + c) / 2.0);
		float e = (float) ((b + c) / 2.0);
		float Q1 = h / 6 * (f(a) + 4 * f(c) + f(b));
		float Q2 = h / 12 * (f(a) + 4 * f(d) + 2 * f(c) + 4 * f(e) + f(b));
		if (Math.abs(Q2 - Q1) <= EPSILON)
			return Q2 + (Q2 - Q1) / 15;
		else
			return adaptive(a, c) + adaptive(c, b);
	}

	/**********************************************************************
	 * Standard normal distribution density function. Replace with any
	 * sufficiently smooth function.
	 **********************************************************************/
	static float f(float x) {
		return (float) (Math.exp(-x * x / 2) / Math.sqrt(2 * Math.PI));
	}
}
