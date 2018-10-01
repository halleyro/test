package com.verizon.test;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build.VERSION_CODES;

import com.strumsoft.android.commons.logger.Logger;

@TargetApi(VERSION_CODES.O)
public class TestService1 extends TestService {

	public int onStartCommand(Intent intent, int flags, final int startId) {
		new Thread() {
			public void run() {
				try {
					sleep(10000);
				}
				catch (Exception e) {
				}
				Logger.debug(getClass(), "stopping");
				stopSelf(startId);
				try {
					sleep(30000);
				}
				catch (Exception e) {
				}
				Logger.debug(getClass(), "exiting");
			}
		}.start();

		return START_NOT_STICKY;
	}
}
