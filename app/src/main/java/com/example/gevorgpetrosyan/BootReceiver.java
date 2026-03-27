package com.example.gevorgpetrosyan;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Executors;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Re-schedule all alarms when the device reboots
            Executors.newSingleThreadExecutor().execute(() -> {
                AppDatabase db = AppDatabase.getInstance(context);
                List<Medicine> meds = db.medicineDao().getAll();
                for (Medicine m : meds) {
                    if (m.times != null && !m.times.isEmpty()) {
                        setupAlarms(context, m.name, m.times);
                    }
                }
            });
        }
    }

    private void setupAlarms(Context context, String name, String times) {
        for (String t : times.split(",")) {
            try {
                String[] p = t.trim().split(":");
                setAlarm(context, name, Integer.parseInt(p[0]), Integer.parseInt(p[1]), t.trim());
            } catch (Exception ignored) {}
        }
    }

    private void setAlarm(Context context, String name, int h, int m, String timeStr) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra("med_name", name);
        intent.putExtra("med_time", timeStr);
        
        PendingIntent pi = PendingIntent.getBroadcast(context, (name + timeStr).hashCode(), intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, h);
        cal.set(Calendar.MINUTE, m);
        cal.set(Calendar.SECOND, 0);
        
        if (cal.before(Calendar.getInstance())) cal.add(Calendar.DAY_OF_MONTH, 1);
        
        if (am != null) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
        }
    }
}
