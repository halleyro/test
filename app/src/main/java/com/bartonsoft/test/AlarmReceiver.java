package com.bartonsoft.test;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.bartonsoft.logger.Logger;

public class AlarmReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		Logger.debug(getClass(), "received " + intent);
		MainActivity.notify(context, intent);
	}
}
