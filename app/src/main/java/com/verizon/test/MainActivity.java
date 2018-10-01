package com.verizon.test;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.Notification.Builder;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import com.google.android.gms.common.api.GoogleApiClient;
import com.strumsoft.android.commons.logger.Logger;

//public class MainActivity extends AppCompatActivity implements ConnectionCallbacks, OnConnectionFailedListener {
public class MainActivity extends AppCompatActivity {
	static String channelId;
	private GoogleApiClient driveClient;
	private static TextView text;
	public static boolean started;

	private static final int REQUEST_CODE_RESOLVE_CONNECT = 1;
	private static final int REQUEST_CODE_CREATOR = 2;

	public static void notify(Context context, Intent intent) {
		setText("received " + intent + ": " + intent.getStringExtra("key"));
	}

	private static void setText(String msg) {
		text.setText(text.getText() + "\n" + msg);
	}

	private void startService(Class<?> cls) {
		final Intent intent = new Intent(MainActivity.this, cls);
		try {
			startService(intent);
		}
		catch (IllegalStateException e) {
			Logger.error(getClass(), e);
			if (VERSION.SDK_INT >= VERSION_CODES.O) {
				startForegroundService(intent);
			}
		}
	}

	@TargetApi(VERSION_CODES.O)
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Logger.debug(getClass(), "onCreate");

		setContentView(R.layout.activity_main);
		text = (TextView)findViewById(R.id.text);

		channelId = "Test-" + Long.toString(System.currentTimeMillis());
		getNotificationChannel(this, channelId);

		new Thread() {
			public void run() {
				for (int i = 7; i > 0; --i) {
					Logger.debug(getClass(), "sleeping");
					try { sleep(10000); } catch (Exception e) {}
				}
				startService(TestService1.class);
				try {
					sleep(5000);
				}
				catch (Exception e) {
				}
				startService(TestService2.class);
				try {
					sleep(60000);
				}
				catch (Exception e) {
				}
				Logger.debug(getClass(), "exiting");
			}
		}.start();

		try {
			Thread.sleep(3000);
		}
		catch (Exception e) {
		}
		finish();


//		new Thread() {
//			@TargetApi(VERSION_CODES.M)
//			public void run() {
//				if (ActivityCompat.checkSelfPermission(MainActivity.this, permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
//					requestPermissions(new String[] { permission.READ_CALL_LOG }, 0);
//				}
//				else {
//					final String[] cols = {
//						Calls.NUMBER,
//						Calls.DATE,
//						Calls.DURATION
//					};
//					final Uri uri = Calls.CONTENT_URI.buildUpon()
//						.appendQueryParameter(Calls.OFFSET_PARAM_KEY, "10")
//						.appendQueryParameter(Calls.LIMIT_PARAM_KEY, "5").build();
//
//					final Cursor cursor = getContentResolver().query(uri, cols, null, null, null);
//					if (cursor != null) {
//						try {
//							while (cursor.moveToNext()) {
//								Log.d("VzmCallLog", "number = " + cursor.getString(0) +
//									", date = " + new Date(cursor.getLong(1)) +
//									", duration = " + cursor.getInt(2));
//							}
//						}
//						finally {
//							cursor.close();
//						}
//					}
//
////					runOnUiThread(new Runnable() {
////						@Override
////						public void run() {
////							setText("canceled");
////						}
////					});
//				}
//			}
//		}.start();
//		finish();

//		final AlarmManager mgr = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
//		Intent intent = new Intent(this, AlarmReceiver.class);
//		intent.putExtra("key", "value1");
//		intent.setData(Uri.parse("ABC1"));
//		PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
//		final long time = System.currentTimeMillis() + 20000;
//		mgr.set(AlarmManager.RTC_WAKEUP, time, pendingIntent);
//
//		intent = new Intent(this, AlarmReceiver.class);
//		intent.putExtra("key", "value2");
//		intent.setData(Uri.parse("ABC2"));
//		pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
//		mgr.set(AlarmManager.RTC_WAKEUP, time, pendingIntent);
//
//		text.setText("scheduled at " + new Date(time));
//
//		new Thread() {
//			public void run() {
//				try {
//					Thread.sleep(10000);
//				}
//				catch (Exception e) {
//				}
//
//				final AlarmManager mgr = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
//				final Intent intent = new Intent(MainActivity.this, AlarmReceiver.class);
//				final PendingIntent pendingIntent = PendingIntent.getBroadcast(MainActivity.this, 0, intent, 0);
//				mgr.cancel(pendingIntent);
//
//				runOnUiThread(new Runnable() {
//					@Override
//					public void run() {
//						setText("canceled");
//					}
//				});
//			}
//		}.start();

//		final ListFormatter formatter = ListFormatter.getInstance();
//		long millis = (123 * 3600 + 45 * 60 + 56) * 1000 + 789;
//		long secs = millis / 1000L;
//		long hours = secs / 3600;
//		long mins = (secs % 3600) / 60;
//		String str = String.format("%02d:%02d:%02d:%d", hours, mins, secs % 60, millis % 1000L);
//
//		((TextView)findViewById(R.id.text)).setText(str);

//		try {
//			String str = AESEncryption.decrypt("63438fa901db4040".getBytes(), "pzLvm5ru/vnr3LZqkoZYO91kyqYb3DaaCSqzXs/B1/w=\n");
//			Logger.debug(str);
//			str = "9257887290:" + str;
//			str = Base64.encodeToString(str.getBytes(), Base64.NO_WRAP);
//			Logger.debug(str);
//			((TextView)findViewById(R.id.text)).setText(str);
//		}
//		catch (Exception e) {
//			Logger.error(e);
//		}


//		new Message().createTable(null, true);

//		Intent i = new Intent(Intent.ACTION_SEND);
//		i.setType("application/zip");
//		i.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File("/sdcard/message.db")));
//		i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
//		try {
//			startActivityForResult(Intent.createChooser(i, "Send report..."), 3);
//		}
//		catch (android.content.ActivityNotFoundException ex) {
//			Toast.makeText(this, "There are no email clients installed.", Toast.LENGTH_SHORT).show();
//		}
//		driveClient = new GoogleApiClient.Builder(this)
//			.addApi(Drive.API)
//			.addScope(Drive.SCOPE_FILE)
//			.addConnectionCallbacks(this)
//			.addOnConnectionFailedListener(this)
//			.build();
//		driveClient.connect();
	}

//	@Override
//	public void onConnected(Bundle bundle) {
//		Drive.DriveApi.newDriveContents(driveClient).setResultCallback(new ResultCallback<DriveContentsResult>() {
//			@Override
//			public void onResult(@NonNull DriveContentsResult result) {
//				if (!result.getStatus().isSuccess()) {
//					// Handle error
//					return;
//				}
//
//				MetadataChangeSet metadataChangeSet = new MetadataChangeSet.Builder()
//					.setMimeType("text/html")
//					.build();
//				IntentSender intentSender = Drive.DriveApi
//					.newCreateFileActivityBuilder()
//					.setInitialMetadata(metadataChangeSet)
//					.setInitialDriveContents(result.getDriveContents())
//					.build(driveClient);
//				try {
//					startIntentSenderForResult(intentSender, 1, null, 0, 0, 0);
//				}
//				catch (SendIntentException e) {
//					// Handle the exception
//				}
//			}
//		});
//	}
//
//	@Override
//	public void onConnectionSuspended(int i) {
//
//	}
//
//	@Override
//	public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
//		if (connectionResult.hasResolution()) {
//			try {
//				connectionResult.startResolutionForResult(this, REQUEST_CODE_RESOLVE_CONNECT);
//			}
//			catch (IntentSender.SendIntentException e) {
//				// Unable to resolve, message user appropriately
//			}
//		}
//		else {
//			GooglePlayServicesUtil.getErrorDialog(connectionResult.getErrorCode(), this, 0).show();
//		}
//	}

//	@Override
//	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
//		Log.d("TEST", "onActivityResult: requestCode = " + requestCode + ", resultCode = " + resultCode +
//			", data = " + dumpIntent(data, "  "));
//		switch (requestCode) {
//			case REQUEST_CODE_RESOLVE_CONNECT:
//				if (resultCode == RESULT_OK) {
//					driveClient.connect();
//				}
//				break;
//
//			case REQUEST_CODE_CREATOR:
//				if (resultCode == RESULT_OK) {
//					DriveId driveId = (DriveId)data.getParcelableExtra(OpenFileActivityBuilder.EXTRA_RESPONSE_DRIVE_ID);
//					((TextView)findViewById(R.id.text)).setText("DriveId = " + driveId);
//				}
//				finish();
//				break;
//
//			case 3:
//				final Uri uri = data == null ? null : data.getData();
//				if (uri != null) {
//					final Cursor cursor = getContentResolver().query(uri, null, null, null, null);
//					Log.d("TEST", "count = " + cursor.getCount());
//					if (cursor.moveToFirst()) {
//						Log.d("TEST", "row = " + dumpRow(cursor));
//					}
//				}
//				break;
//
//			default:
//				super.onActivityResult(requestCode, resultCode, data);
//				break;
//		}
//	}

	@TargetApi(VERSION_CODES.O)
	public void onStart() {
		super.onStart();
		started = true;
		Logger.debug(getClass(), "onStart");
//		notify(this, chan);
	}

	public void onStop() {
		super.onStop();
		started = false;
		Logger.debug(getClass(), "onStop");
	}

	public void onDestroy() {
		super.onDestroy();
		Logger.debug(getClass(), "onDestroy");
	}

	@RequiresApi(api = VERSION_CODES.O)
	public void notify(Context context, String chan) {
		final Notification notif = new Builder(context, chan)
			.setSmallIcon(R.mipmap.ic_launcher)
			.setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher))
			.setContentTitle(chan + "-title")
			.setContentText(chan + "-text")
			.setOnlyAlertOnce(true)
			.build();
		final NotificationManager nm = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
		nm.notify(1, notif);
	}

	@RequiresApi(api = VERSION_CODES.O)
	public void getNotificationChannel(Context context, String chan) {
		final NotificationChannel channel = new NotificationChannel(chan, chan + "-name", NotificationManager.IMPORTANCE_LOW);
		channel.setDescription(chan + "-desc");
		channel.enableLights(false);
		channel.enableVibration(false);
		channel.setShowBadge(false);
		channel.setSound(null, null);
		final NotificationManager nm = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
		nm.createNotificationChannel(channel);
	}
}
