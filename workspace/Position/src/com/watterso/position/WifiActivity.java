package com.watterso.position;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class WifiActivity extends Activity {
	private static boolean ScanAsFastAsPossible = true;
	private boolean firstTime = true;
	private ArrayList<String> mWifis;
	private String mWatch = "";
	private HashMap<Long,HashMap<String,Integer>> mData;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		IntentFilter i = new IntentFilter();
		i.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
		registerReceiver(new BroadcastReceiver() {
			public void onReceive(Context c, Intent i) {
				if (!WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(i
						.getAction()))
					return;
				WifiManager w = (WifiManager) c.getSystemService(Context.WIFI_SERVICE);
				Long time = System.nanoTime();
				if (firstTime) {
					initializeList(time, w.getScanResults());
					firstTime = false;
				} else {
					refreshList(time, w.getScanResults());
				}
				if (ScanAsFastAsPossible)
					w.startScan();
				else {

				}
			}
		}, i);
		mWifis = new ArrayList<String>();
		mData = new HashMap<Long, HashMap<String,Integer>>();

	}

	@Override
	protected void onResume() {
		WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		boolean a = wm.startScan();
		super.onResume();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	private void initializeList(Long time, List<ScanResult> results) {
		ListView wifiList = (ListView) findViewById(R.id.WifiList);
		String[] arr = new String[1];
		HashMap<String, Integer> snapshot = new HashMap<String, Integer>();
		for (ScanResult res : results) {
			String wifiInfo = res.SSID + " % " + res.BSSID + " % " + res.level;
			mWifis.add(wifiInfo);
			snapshot.put(res.BSSID, res.level);
		}
		mData.put(time, snapshot);
		Collections.sort(mWifis, new Comparator<String>() {
			@Override
			public int compare(String lhs, String rhs) {
				String[] aLhs = lhs.split("%");
				String[] aRhs = rhs.split("%");
				int lhsDbm = -1
						* Integer.parseInt(aLhs[aLhs.length - 1].trim());
				int rhsDbm = -1
						* Integer.parseInt(aRhs[aRhs.length - 1].trim());
				// Log.d("AAAAAAAA",
				// ""+Arrays.toString(aLhs)+" | "+Arrays.toString(aRhs));
				// Log.d("AAAAAAAA",
				// ""+aLhs[aLhs.length-1]+" | "+aRhs[aRhs.length-1]);
				// Log.d("IIIIIIII", ""+lhsDbm+" | "+rhsDbm);
				return lhsDbm - rhsDbm;
			}
		});
		String tmp = mWifis.get(0);
		mWatch = mWifis.get(0).split("%")[1].trim();
		Log.d("YOLO", mWatch);
		mWifis.clear();
		mWifis.add(tmp);
		wifiList.setAdapter(new ArrayAdapter<String>(this, R.layout.list_entry,
				mWifis));
	}

	private void refreshList(Long time, List<ScanResult> results) {
		ListView wifiList = (ListView) findViewById(R.id.WifiList);
		HashMap<String, Integer> snapshot = new HashMap<String, Integer>();
		for (ScanResult res : results) {
			if (res.BSSID.equals(mWatch)) {
				String wifiInfo = res.SSID + " % " + res.BSSID + " % "
						+ res.level;
				mWifis.clear();
				mWifis.add(wifiInfo);
			}
			snapshot.put(res.BSSID, res.level);
		}
		mData.put(time,snapshot);
		/*Collections.sort(mWifis, new Comparator<String>() {
			@Override
			public int compare(String lhs, String rhs) {
				String[] aLhs = lhs.split("%");
				String[] aRhs = rhs.split("%");
				int lhsDbm = -1
						* Integer.parseInt(aLhs[aLhs.length - 1].trim());
				int rhsDbm = -1
						* Integer.parseInt(aRhs[aRhs.length - 1].trim());
				// Log.d("AAAAAAAA",
				// ""+Arrays.toString(aLhs)+" | "+Arrays.toString(aRhs));
				// Log.d("AAAAAAAA",
				// ""+aLhs[aLhs.length-1]+" | "+aRhs[aRhs.length-1]);
				// Log.d("IIIIIIII", ""+lhsDbm+" | "+rhsDbm);
				return lhsDbm - rhsDbm;
			}
		});*/
		((ArrayAdapter<String>) wifiList.getAdapter()).notifyDataSetChanged();
	}

}