package com.bartonsoft.test;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Build.VERSION_CODES;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;

import com.bartonsoft.logger.Logger;

public class TestService extends Service {

	@RequiresApi(api = VERSION_CODES.O)
	public void onCreate() {
		super.onCreate();
		Logger.debug(getClass(), "onCreate: started = " + MainActivity.started);

		if (!MainActivity.started) {
			final Notification notif = new Notification.Builder(this, MainActivity.channelId)
				.setSmallIcon(R.mipmap.ic_launcher)
				.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher))
				.setContentTitle("Test")
				.setContentText("Testing...")
				.build();
			startForeground(1, notif);
		}
	}

	public void onDestroy() {
		super.onDestroy();
		Logger.debug(getClass(), "onDestroy");
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
}
