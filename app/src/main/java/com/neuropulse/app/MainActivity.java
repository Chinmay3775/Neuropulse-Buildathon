package com.neuropulse.app;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private TextView tvTotalTime, tvMostUsed, tvAddictionRisk, tvStatus;
    private Button btnStart, btnStop, btnReqUsage, btnReqNotif;
    private RecyclerView rvSessions;

    private SessionAdapter sessionAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Toolbar
        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);

        // Views
        tvTotalTime = findViewById(R.id.tvTotalTime);
        tvMostUsed = findViewById(R.id.tvMostUsed);
        tvAddictionRisk = findViewById(R.id.tvAddictionRisk);
        tvStatus = findViewById(R.id.tvStatus);

        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        btnReqUsage = findViewById(R.id.btnReqUsage);
        btnReqNotif = findViewById(R.id.btnReqNotif);

        rvSessions = findViewById(R.id.rvSessions);
        rvSessions.setLayoutManager(new LinearLayoutManager(this));
        sessionAdapter = new SessionAdapter();
        rvSessions.setAdapter(sessionAdapter);

        loadRecentSessions();

        // Start Monitoring
        btnStart.setOnClickListener(v -> {
            if (!hasUsageStatsPermission()) {
                Toast.makeText(this, "Please grant Usage Access first", Toast.LENGTH_SHORT).show();
                return;
            }
            startMonitoring();
        });

        // Stop Monitoring
        btnStop.setOnClickListener(v -> stopMonitoring());

        // Request Usage Access
        btnReqUsage.setOnClickListener(v -> requestUsageAccess());

        // Request Notification Access
        btnReqNotif.setOnClickListener(v -> requestNotificationAccess());
    }

    private void loadRecentSessions() {
        new Thread(() -> {
            List<SessionEntity> sessions = AppDatabase.getInstance(getApplicationContext())
                    .sessionDao()
                    .getAllSessions();
            runOnUiThread(() -> sessionAdapter.setSessions(sessions));
        }).start();
    }

    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = AppOpsManager.MODE_DEFAULT;
        if (appOps != null) {
            mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), getPackageName());
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private void requestUsageAccess() {
        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        startActivity(intent);
        Toast.makeText(this, "Grant Usage Access to Neuropulse", Toast.LENGTH_LONG).show();
    }

    private void requestNotificationAccess() {
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        startActivity(intent);
        Toast.makeText(this, "Grant Notification Access to Neuropulse", Toast.LENGTH_LONG).show();
    }

    private void startMonitoring() {
        Intent serviceIntent = new Intent(this, UsageMonitorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        tvStatus.setText("Monitoring: ON");
        Toast.makeText(this, "Monitoring started", Toast.LENGTH_SHORT).show();
    }

    private void stopMonitoring() {
        Intent serviceIntent = new Intent(this, UsageMonitorService.class);
        stopService(serviceIntent);
        tvStatus.setText("Monitoring: OFF");
        Toast.makeText(this, "Monitoring stopped", Toast.LENGTH_SHORT).show();
    }
}
