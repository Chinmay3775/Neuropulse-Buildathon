// FileName: MultipleFiles/UsageMonitorService.java
package com.neuropulse.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler; // Import Handler
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class UsageMonitorService extends Service {

    private static final String TAG = "UsageMonitorService";
    private static final String CHANNEL_ID = "NeuropulseMonitor";
    private static final long MONITOR_INTERVAL_MS = TimeUnit.MINUTES.toMillis(1); // Monitor every 1 minute

    private Handler handler;
    private Runnable monitorRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, getNotification("Monitoring usage..."));
        Log.d(TAG, "Service created");

        handler = new Handler();
        monitorRunnable = new Runnable() {
            @Override
            public void run() {
                monitorUsage();
                handler.postDelayed(this, MONITOR_INTERVAL_MS); // Schedule next run
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start the monitoring loop
        handler.post(monitorRunnable);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(monitorRunnable); // Stop the monitoring loop
        Log.d(TAG, "Service destroyed");
    }

    private void monitorUsage() {
        try {
            UsageStatsManager usageStatsManager = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
            if (usageStatsManager == null) return;

            long endTime = System.currentTimeMillis();
            long startTime = endTime - TimeUnit.DAYS.toMillis(1); // last 24 hours

            List<UsageStats> stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY, startTime, endTime);

            if (stats == null || stats.isEmpty()) return;

            Collections.sort(stats, (a, b) -> Long.compare(b.getTotalTimeInForeground(), a.getTotalTimeInForeground()));
            UsageStats mostUsed = stats.get(0);

            String mostUsedPackageName = mostUsed.getPackageName();
            long totalScreenTimeSeconds = 0;

            for (UsageStats usage : stats) {
                totalScreenTimeSeconds += usage.getTotalTimeInForeground() / 1000;
            }

            long totalScreenTimeMinutes = TimeUnit.SECONDS.toMinutes(totalScreenTimeSeconds);
            String addictionRisk = "Low";
            if (totalScreenTimeMinutes > 180) addictionRisk = "High";
            else if (totalScreenTimeMinutes > 120) addictionRisk = "Medium";

            SessionEntity session = new SessionEntity();
            session.appPackage = mostUsedPackageName;
            session.sessionDurationSec = totalScreenTimeSeconds;
            session.addictionRisk = addictionRisk;
            session.timestamp = System.currentTimeMillis();

            // Populate other fields if needed, e.g., from UnlockReceiver and NLService
            session.unlocksLastHour = UnlockReceiver.getUnlockCount(getApplicationContext());
            session.notifCountLast30Min = NLService.getNotifCountLast30Min();
            session.nightFlag = Utils.isNight(session.timestamp) ? 1 : 0;
            // For appCategory, you might want to use Utils.pkgToCategory(mostUsedPackageName)
            session.appCategory = Utils.pkgToCategory(mostUsedPackageName);


            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            db.sessionDao().insert(session);

            Log.d(TAG, "Most used: " + mostUsedPackageName + " | Total time: " + totalScreenTimeMinutes
                    + "m | Risk: " + addictionRisk + " | Unlocks: " + session.unlocksLastHour
                    + " | Notifs: " + session.notifCountLast30Min);

        } catch (Exception e) {
            Log.e(TAG, "Error monitoring usage: ", e);
        }
        // Removed stopSelf() for continuous monitoring
    }

    private Notification getNotification(String content) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Neuropulse")
                .setContentText(content)
                .setSmallIcon(R.mipmap.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Neuropulse Monitoring";
            String description = "Tracks screen usage";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
