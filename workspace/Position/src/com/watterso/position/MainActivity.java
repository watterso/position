package com.watterso.position;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
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
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		Intent intent = new Intent(this, DataIntentService.class);
		if(bindService(intent,mServiceConnection,Context.BIND_AUTO_CREATE));{
			Log.d("YOLO", "Bound Success!");
		}
		Button post = (Button)findViewById(R.id.postButton);
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
	private void postData(){
		HashMap<Long, Float[]> tmp= new HashMap<Long, Float[]>(mDataIntentService.getAData());
		new PostDataTask().execute(tmp);
	}
	
	private ServiceConnection mServiceConnection = new ServiceConnection() {
		
		@Override
		public void onServiceDisconnected(ComponentName name) {
			// TODO Auto-generated method stub
			
		}
		
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			DataBinder binder = (DataBinder)service;
			if(mDataIntentService == null){
				mDataIntentService = binder.getService();
				mDataIntentService.setupSensors(getApplicationContext());
			}
			Log.d("YOLO", "IS NULL? "+(mDataIntentService == null));
		}
	};
	
	public static JSONObject getJsonFromMap(HashMap<Long,Float[]> in){
		JSONObject oot = new JSONObject();
		for(Long l : in.keySet()){
			try {
				Float[] arr1 = in.get(l);
				JSONArray arr = new JSONArray();
				arr.put(arr1[0]);
				arr.put(arr1[1]);
				arr.put(arr1[2]);
				oot.put(""+l, arr);
			} catch (JSONException e) {
				Log.d("YOLOJSON", e.getMessage());
			}
		}
		return oot;
	}
	private class PostDataTask extends AsyncTask<HashMap<Long,Float []>,Void,Void>{

		@Override
		protected Void doInBackground(HashMap<Long,Float []>... params) {
			JSONObject json = getJsonFromMap(params[0]);
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
}
