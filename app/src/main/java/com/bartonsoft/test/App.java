package com.bartonsoft.test;

import android.app.Application;

public class App extends Application {

	public void onCreate() {
		super.onCreate();

//		new Thread() {
//			public void run() {
////				try { sleep(5000); } catch (Exception e) {}
//				Logger.debug(getClass(), "run");
//				final Intent intent = new Intent(App.this, TestService.class);
//				try {
//					startService(intent);
//				}
//				catch (IllegalStateException e) {
//					Logger.error(getClass(), e);
//					if (VERSION.SDK_INT >= VERSION_CODES.O) {
//						startForegroundService(intent);
//					}
//				}
//			}
//		}.start();
//
//		for (int i = 20; i > 0; --i) {
//			Logger.debug(getClass(), "onCreate: " + i);
//			try { Thread.sleep(100); } catch (Exception e) {}
//		}
//		try {
//			Thread.sleep(3000);
//		}
//		catch (Exception e) {
//		}
	}
}
