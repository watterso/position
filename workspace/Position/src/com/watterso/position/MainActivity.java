package com.watterso.position;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import com.watterso.position.DataIntentService.DataBinder;
import com.watterso.position.DataIntentService.DeltaSnapShot;

public class MainActivity extends Activity implements SensorEventListener {
	private DataIntentService mDataIntentService;
	private boolean ScanAsFastAsPossible = true;
	public static String GNEX = "Galaxy Nexus";
	public static String N7 = "Nexus 7";

	private boolean mFlag = false;
	private HashMap<String, Integer> mOppositeData;
	private String mOpposite;
	private float mX = 0;
	private float mY = 0;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (GNEX.equals(Build.MODEL)) {
			mOpposite = N7;
		} else {
			mOpposite = GNEX;
		}
		mOppositeData = new HashMap<String, Integer>();
		setContentView(R.layout.activity_main);
		Intent intent = new Intent(this, DataIntentService.class);
		if (bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE)) {
			Log.d("YOLO", "Bound Success!");
		}
		IntentFilter i = new IntentFilter();
		i.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
		registerReceiver(new BroadcastReceiver() {
			public void onReceive(Context c, Intent i) {
				if (!WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(i
						.getAction()))
					return;
				WifiManager w = (WifiManager) c.getSystemService(Context.WIFI_SERVICE);
				Long time = System.currentTimeMillis();
				HashMap<String, Integer> snapshot = new HashMap<String, Integer>();
				for (ScanResult scanRes : w.getScanResults()) {
					snapshot.put(scanRes.BSSID, scanRes.level);
				}
				mDataIntentService.pushWifiData(time, snapshot);
				if (ScanAsFastAsPossible)
					w.startScan();
				else {

				}
			}
		}, i);
		Button post = (Button) findViewById(R.id.postButton);
		post.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				postData();
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

	@Override
	protected void onPause() {
		super.onPause();

	}

	@Override
	protected void onStop() {
		super.onStop();

	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onSensorChanged(SensorEvent event) {

	}

	private void postData() {
		// new
		// PostDataTask().execute(getJsonFromVectorMap(mDataIntentService.getAccelerometerData()));
		// new
		// PostDataTask().execute(getJsonFromWifiMap(mDataIntentService.getWifiData()));
		// new
		// PostDataTask().execute(getJsonFromWifiAverageMap(mDataIntentService.getWifiAverageData()));
	}

	public static JSONObject getJsonFromWifiAverageMap(
			HashMap<String, Integer> wifiAverageData) {
		JSONObject info = new JSONObject();
		for (String bssid : wifiAverageData.keySet()) {
			try {
				info.put(bssid, wifiAverageData.get(bssid));
			} catch (JSONException e) {
				Log.d("YOLOJSON", e.getMessage());
			}
		}
		return info;
	}
	public void updateCompass(){
		new GetDataTask().execute();HashMap<String, DeltaSnapShot[]> tmp = mDataIntentService
				.getSnaps();
		HashMap<String, Integer> avg = mDataIntentService
				.getWifiAverageData();
		float x = 0;
		float y = 0;
		Long time = System.currentTimeMillis();
		for (String key : mOppositeData.keySet()) {
			Integer me = avg.get(key);
			Integer them = mOppositeData.get(key);
			if (tmp.get(key) == null || me == null || them == null)
				continue;
			DeltaSnapShot dSS0 = tmp.get(key)[0];
			DeltaSnapShot dSS1 = tmp.get(key)[1];
			if (me > them) {
				// use [1]
				if (dSS1 != null && (time - dSS1.time) < 1000000) {
					x += dSS1.x;
					y += dSS1.y;
				} /*else if (dSS0 != null
						&& (time - dSS0.time) < 1000000) {
					y -= dSS0.x;
					x -= dSS0.y;
				}*/
			} else {
				// use [0]
				if (dSS0 != null
						&& (System.currentTimeMillis() - dSS0.time) < 1000000) {
					x += dSS0.x;
					y += dSS0.y;
				} /*else if (dSS1 != null
						&& (time - dSS1.time) < 1000000) {
					x -= dSS1.x;
					y -= dSS1.y;
				}*/
			}
		}
		mX = x;
		mY = y;
		
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				float x = mX;
				float y = mY;
				float mag = (x * x) + (y * y);
				float normX = x / mag;
				float normY = y / mag;
				TextView leText = (TextView) findViewById(R.id.label);
				leText.setText("" + x+","+y+" | "+normX+","+normY);

			}
		});
	}
	public void putData(JSONObject json) {
		new PutDataTask().execute(json);
	}

	private ServiceConnection mServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceDisconnected(ComponentName name) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			DataBinder binder = (DataBinder) service;
			if (mDataIntentService == null) {
				mDataIntentService = binder.getService();
				mDataIntentService.setupSensors(getApplicationContext());
				mDataIntentService.setMainActivity(MainActivity.this);
			}
			Log.d("YOLO", "IS NULL? " + (mDataIntentService == null));
		}
	};

	public static JSONObject getJsonFromWifiMap(
			HashMap<Long, HashMap<String, Integer>> in) {
		JSONObject oot = new JSONObject();
		for (Long l : in.keySet()) {
			try {
				HashMap<String, Integer> tmp = in.get(l);
				JSONObject snap = new JSONObject();
				for (String s : tmp.keySet()) {
					snap.put(s, tmp.get(s));
				}
				oot.put("" + l, snap);
			} catch (JSONException e) {
				Log.d("YOLOJSON", e.getMessage());
			}
		}
		return oot;
	}

	public static JSONObject getJsonFromVectorMap(HashMap<Long, float[]> in) {
		JSONObject oot = new JSONObject();
		for (Long l : in.keySet()) {
			try {
				float[] arr1 = in.get(l);
				JSONArray arr = new JSONArray();
				arr.put(arr1[0]);
				arr.put(arr1[1]);
				arr.put(arr1[2]);
				oot.put("" + l, arr);
			} catch (JSONException e) {
				Log.d("YOLOJSON", e.getMessage());
			}
		}
		return oot;
	}

	private class GetDataTask extends AsyncTask<Void, Void, Void> {
		float mX = 0;
		float mY = 0;

		@Override
		protected Void doInBackground(Void... params) {
			try {
				String url = DataIntentService.MONGO_URL
						+ "&q="
						+ URLEncoder.encode("{\"device\":\"" + mOpposite
								+ "\"}", "UTF-8");
				DefaultHttpClient httpClient = new DefaultHttpClient();
				HttpGet httpGet = new HttpGet(url);
				HttpResponse httpResponse = httpClient.execute(httpGet);
				HttpEntity httpEntity = httpResponse.getEntity();
				String json = EntityUtils.toString(httpEntity);
				try {
					JSONArray arr = new JSONArray(json);
					JSONObject obj = arr.getJSONObject(0);
					JSONObject data = obj.getJSONObject("data");
					HashMap<String, Integer> oot = new HashMap<String, Integer>();
					Iterator it = data.keys();
					while (it.hasNext()) {
						String key = (String) it.next();
						oot.put(key, data.getInt(key));
					}
					mOppositeData.clear();
					mOppositeData.putAll(oot);
					
				} catch (JSONException e) {
					Log.d("YOLOJSON", e.getMessage());
				}
			} catch (UnsupportedEncodingException e) {
				Log.d("YOLOENC", e.getMessage());
			} catch (ClientProtocolException e) {
				Log.d("YOLOPROTO", e.getMessage());
			} catch (IOException e) {
				Log.d("YOLOIO", e.getMessage());
			}

			return null;
		}

	}

	private class PostDataTask extends AsyncTask<JSONObject, Void, Void> {

		@Override
		protected Void doInBackground(JSONObject... params) {
			JSONObject json = params[0];
			String path = "http://www.posttestserver.com/post.php?dir=james";
			HttpPost lePost = new HttpPost(path);
			try {
				lePost.setEntity(new StringEntity(json.toString()));
			} catch (UnsupportedEncodingException e) {
				Log.d("YOLOENC", e.getMessage());
			}
			lePost.setHeader("Accept", "application/json");
			lePost.setHeader("Content-type", "application/json");
			DefaultHttpClient httpclient = new DefaultHttpClient();
			ResponseHandler responseHandler = new BasicResponseHandler();
			try {
				httpclient.execute(lePost, responseHandler);
			} catch (ClientProtocolException e) {
				Log.d("YOLOPROTO", e.getMessage());
			} catch (IOException e) {
				Log.d("YOLOIO", e.getMessage());
			}
			Log.d("INFO", "POSTED");
			return null;
		}
	}

	public class PutDataTask extends AsyncTask<JSONObject, Void, Void> {

		@Override
		protected Void doInBackground(JSONObject... params) {
			JSONObject json = params[0];
			try {
				String url = DataIntentService.MONGO_URL
						+ "&q="
						+ URLEncoder.encode("{\"device\":\""
								+ android.os.Build.MODEL + "\"}", "UTF-8")
						+ "&u=true";
				HttpPut httpPut = new HttpPut(url);
				httpPut.setHeader("Content-type", "application/json");
				httpPut.setEntity(new StringEntity(json.toString()));
				DefaultHttpClient httpClient = new DefaultHttpClient();
				HttpResponse response = httpClient.execute(httpPut);
			} catch (ClientProtocolException e) {
				Log.d("YOLOPROTO", e.getMessage());
			} catch (IOException e) {
				Log.d("YOLOIO", e.getMessage());
			}
			Log.d("INFO", "POSTED");
			return null;
		}
	}
}
