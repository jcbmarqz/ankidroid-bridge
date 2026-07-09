package com.jcbmarqz.ankidroidbridge;

import android.app.ActivityManager;
import android.app.ForegroundServiceStartNotAllowedException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class StartServerReceiver extends BroadcastReceiver {

    private static final String TAG = "StartServerReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (isServiceRunning(context)) {
            Log.i(TAG, "Server already running, ignoring START_SERVER intent");
            return;
        }

        int port = intent.getIntExtra(BridgeService.EXTRA_PORT, 18765);
        Log.i(TAG, "Starting server on port " + port + " via broadcast intent");

        Intent serviceIntent = new Intent(context, BridgeService.class);
        serviceIntent.putExtra(BridgeService.EXTRA_PORT, port);
        try {
            context.startForegroundService(serviceIntent);
        } catch (ForegroundServiceStartNotAllowedException e) {
            Log.e(TAG, "Cannot start foreground service from background. Grant 'Display over other apps' permission.", e);
        }
    }

    private boolean isServiceRunning(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo info : am.getRunningServices(Integer.MAX_VALUE)) {
            if (BridgeService.class.getName().equals(info.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
