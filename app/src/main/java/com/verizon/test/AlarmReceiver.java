package com.verizon.test;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.strumsoft.android.commons.logger.Logger;

public class AlarmReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		Logger.debug(getClass(), "received " + intent);
		MainActivity.notify(context, intent);
	}
}
