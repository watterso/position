package com.watterso.position;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
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
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import com.watterso.position.DataIntentService.DataBinder;

public class MainActivity extends Activity implements SensorEventListener {
	private DataIntentService mDataIntentService;
	private boolean ScanAsFastAsPossible = true;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
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
				Long time = System.currentTimeMillis()*1000000;
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
		new PostDataTask().execute(getJsonFromWifiAverageMap(mDataIntentService
				.getWifiAverageData()));
	}

	public static JSONObject getJsonFromWifiAverageMap(
			HashMap<String, Integer> wifiAverageData) {
		JSONObject info = new JSONObject();
		for(String bssid : wifiAverageData.keySet()){
			try {
				info.put(bssid, wifiAverageData.get(bssid));
			} catch (JSONException e) {
				Log.d("YOLOJSON", e.getMessage());
			}
		}
		return info;
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
