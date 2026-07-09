package com.jcbmarqz.ankidroidbridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class StopServerReceiver extends BroadcastReceiver {

    private static final String TAG = "StopServerReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "Stopping server via broadcast intent");
        Intent serviceIntent = new Intent(context, BridgeService.class);
        context.stopService(serviceIntent);
    }
}
