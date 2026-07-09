package com.jcbmarqz.ankidroidbridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;

public class BridgeService extends Service {

    private static final String CHANNEL_ID = "bridge_service";
    public static final String EXTRA_PORT = "port";
    public static final String ACTION_LOG = "com.jcbmarqz.ankidroidbridge.LOG";
    public static final String EXTRA_MESSAGE = "message";

    private BridgeServer server;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    public static final String ACTION_STOP = "com.jcbmarqz.ankidroidbridge.STOP";
    public static final String ACTION_STOP_SERVER = "com.jcbmarqz.ankidroidbridge.STOP_SERVER";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action) || ACTION_STOP_SERVER.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        int port = intent != null ? intent.getIntExtra(EXTRA_PORT, 18765) : 18765;

        // PendingIntent to open the app
        android.app.PendingIntent openIntent = android.app.PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), android.app.PendingIntent.FLAG_IMMUTABLE);

        // PendingIntent to stop the server
        Intent stopIntent = new Intent(this, BridgeService.class);
        stopIntent.setAction(ACTION_STOP);
        android.app.PendingIntent stopPendingIntent = android.app.PendingIntent.getService(this, 1,
                stopIntent, android.app.PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("AnkiConnect Bridge")
                .setContentText("Running on port " + port)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(openIntent)
                .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
                .build();

        startForeground(1, notification);

        if (server != null && server.isAlive()) {
            return START_STICKY;
        }

        server = new BridgeServer(this, port);
        try {
            server.start();
            log("Server started on port " + port);
        } catch (IOException e) {
            log("ERROR: " + e.getMessage());
            stopSelf();
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (server != null) {
            server.stop();
            log("Server stopped");
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void log(String message) {
        Log.i("BridgeService", message);
        String timestamp = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                .format(new java.util.Date());
        Intent intent = new Intent(ACTION_LOG);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_MESSAGE, "[" + timestamp + "] " + message);
        sendBroadcast(intent);
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Bridge Service", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
}
