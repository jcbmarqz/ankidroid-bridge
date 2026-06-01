package com.marqjaco.ankidroidbridge;

import android.Manifest;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final String ANKI_PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE";
    private static StringBuilder logBuffer = new StringBuilder();

    private LinearLayout rootLayout;
    private TextView statusText, logText, logLabel, portLabel, clearButton;
    private ScrollView logScroll;
    private ImageButton toggleButton, themeButton;
    private EditText portInput;
    private boolean darkMode;
    private boolean receiverRegistered = false;

    private final BroadcastReceiver logReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String msg = intent.getStringExtra(BridgeService.EXTRA_MESSAGE);
            if (msg != null) appendLog(msg);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rootLayout = findViewById(R.id.rootLayout);

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            int bottom = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).bottom;
            int newPadding = 24 + bottom;
            if (v.getPaddingBottom() != newPadding) {
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), newPadding);
            }
            return insets;
        });

        statusText = findViewById(R.id.statusText);
        logText = findViewById(R.id.logText);
        logLabel = findViewById(R.id.logLabel);
        portLabel = findViewById(R.id.portLabel);
        logScroll = findViewById(R.id.logScroll);
        toggleButton = findViewById(R.id.toggleButton);
        themeButton = findViewById(R.id.themeButton);
        portInput = findViewById(R.id.portInput);
        clearButton = findViewById(R.id.clearButton);

        clearButton.setOnClickListener(v -> {
            logBuffer.setLength(0);
            logText.setText("");
        });

        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        portInput.setText(String.valueOf(prefs.getInt("port", 18765)));
        darkMode = prefs.getBoolean("darkMode", false);

        if (logBuffer.length() > 0) {
            logText.setText(logBuffer.toString());
        }

        registerReceiver(logReceiver, new IntentFilter(BridgeService.ACTION_LOG), Context.RECEIVER_NOT_EXPORTED);
        receiverRegistered = true;

        if (ContextCompat.checkSelfPermission(this, ANKI_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{ANKI_PERMISSION}, 1);
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2);
        }

        themeButton.setOnClickListener(v -> {
            darkMode = !darkMode;
            getPreferences(MODE_PRIVATE).edit().putBoolean("darkMode", darkMode).apply();
            applyTheme();
        });

        toggleButton.setOnClickListener(v -> {
            if (isServiceRunning()) {
                stopService(new Intent(this, BridgeService.class));
            } else {
                int port = getPort();
                getPreferences(MODE_PRIVATE).edit().putInt("port", port).apply();
                Intent intent = new Intent(this, BridgeService.class);
                intent.putExtra(BridgeService.EXTRA_PORT, port);
                startForegroundService(intent);
            }
            toggleButton.postDelayed(this::updateUI, 200);
        });

        // Auto-start
        if (!isServiceRunning()) {
            int port = getPort();
            Intent intent = new Intent(this, BridgeService.class);
            intent.putExtra(BridgeService.EXTRA_PORT, port);
            startForegroundService(intent);
        }

        applyTheme();
        updateUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (receiverRegistered) {
            unregisterReceiver(logReceiver);
            receiverRegistered = false;
        }
    }

    private int getPort() {
        try {
            return Integer.parseInt(portInput.getText().toString().trim());
        } catch (NumberFormatException e) {
            return 18765;
        }
    }

    private void applyTheme() {
        boolean running = isServiceRunning();
        if (darkMode) {
            rootLayout.setBackgroundColor(Color.parseColor("#0d1117"));
            statusText.setTextColor(running ? Color.parseColor("#39d353") : Color.parseColor("#8b949e"));
            statusText.setTypeface(android.graphics.Typeface.MONOSPACE);
            portLabel.setTextColor(Color.parseColor("#8b949e"));
            portLabel.setTypeface(android.graphics.Typeface.MONOSPACE);
            portLabel.setText("port:");
            portInput.setBackgroundResource(R.drawable.input_bg_dark);
            portInput.setTextColor(Color.parseColor("#c9d1d9"));
            portInput.setTypeface(android.graphics.Typeface.MONOSPACE);
            logLabel.setTextColor(Color.parseColor("#484f58"));
            logLabel.setText("STDOUT");
            logLabel.setTypeface(android.graphics.Typeface.MONOSPACE);
            logScroll.setBackgroundResource(R.drawable.log_bg_dark);
            logText.setTextColor(Color.parseColor("#39d353"));
            themeButton.setImageResource(R.drawable.ic_sun);
            themeButton.setColorFilter(null);
            toggleButton.setColorFilter(running ? Color.parseColor("#39d353") : Color.parseColor("#8b949e"));
            toggleButton.setBackgroundResource(running ? R.drawable.circle_button_dark_active : R.drawable.circle_button_dark);
            getWindow().setStatusBarColor(Color.parseColor("#0d1117"));
        } else {
            rootLayout.setBackgroundColor(Color.parseColor("#f0f4f8"));
            statusText.setTextColor(Color.parseColor("#555555"));
            statusText.setTypeface(android.graphics.Typeface.DEFAULT);
            portLabel.setTextColor(Color.parseColor("#666666"));
            portLabel.setTypeface(android.graphics.Typeface.DEFAULT);
            portLabel.setText("Port");
            portInput.setBackgroundResource(R.drawable.input_bg);
            portInput.setTextColor(Color.parseColor("#333333"));
            portInput.setTypeface(android.graphics.Typeface.DEFAULT);
            logLabel.setTextColor(Color.parseColor("#999999"));
            logLabel.setText("LOG");
            logLabel.setTypeface(android.graphics.Typeface.DEFAULT);
            logScroll.setBackgroundResource(R.drawable.log_bg);
            logText.setTextColor(Color.parseColor("#a0d0a0"));
            themeButton.setImageResource(R.drawable.ic_moon);
            themeButton.setColorFilter(null);
            toggleButton.setColorFilter(Color.WHITE);
            toggleButton.setBackgroundResource(running ? R.drawable.circle_button_active : R.drawable.circle_button);
            getWindow().setStatusBarColor(Color.parseColor("#f0f4f8"));
        }
    }

    private void updateUI() {
        boolean running = isServiceRunning();
        if (running) {
            statusText.setText(darkMode ? "● LISTENING :" + getPort() : "Running on :" + getPort());
            portInput.setEnabled(false);
        } else {
            statusText.setText(darkMode ? "○ STOPPED" : "Server stopped");
            portInput.setEnabled(true);
        }
        applyTheme();
    }

    private void appendLog(String msg) {
        logBuffer.append(msg).append("\n");
        logText.append(msg + "\n");
    }

    private boolean isServiceRunning() {
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo info : am.getRunningServices(Integer.MAX_VALUE)) {
            if (BridgeService.class.getName().equals(info.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
