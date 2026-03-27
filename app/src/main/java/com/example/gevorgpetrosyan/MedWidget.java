package com.example.gevorgpetrosyan;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class MedWidget extends AppWidgetProvider {

    public static final String ACTION_AUTO_UPDATE = "com.example.gevorgpetrosyan.WIDGET_UPDATE";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_AUTO_UPDATE.equals(intent.getAction()) || 
            Intent.ACTION_TIME_TICK.equals(intent.getAction()) || 
            Intent.ACTION_TIME_CHANGED.equals(intent.getAction()) ||
            Intent.ACTION_TIMEZONE_CHANGED.equals(intent.getAction())) {
            
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, MedWidget.class));
            for (int appWidgetId : appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId);
            }
        }
    }

    public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.med_widget);

        // Update Time
        String currentTime = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        views.setTextViewText(R.id.widget_time, currentTime);

        // Update Week Grid
        boolean isRussian = context.getSharedPreferences("LangPrefs", Context.MODE_PRIVATE).getBoolean("IsRussian", false);
        String[] dayLettersEn = {"S", "M", "T", "W", "T", "F", "S"};
        String[] dayLettersRu = {"В", "П", "В", "С", "Ч", "П", "С"};
        String[] dayLetters = isRussian ? dayLettersRu : dayLettersEn;
        int currentDay = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);

        int[] tvIds = {R.id.tv_day_1, R.id.tv_day_2, R.id.tv_day_3, R.id.tv_day_4, R.id.tv_day_5, R.id.tv_day_6, R.id.tv_day_7};
        int[] dotIds = {R.id.dot_day_1, R.id.dot_day_2, R.id.dot_day_3, R.id.dot_day_4, R.id.dot_day_5, R.id.dot_day_6, R.id.dot_day_7};

        for (int i = 1; i <= 7; i++) {
            String letter = dayLetters[i - 1];
            if (i == currentDay) {
                SpannableString ss = new SpannableString(letter);
                ss.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, letter.length(), 0);
                views.setTextViewText(tvIds[i - 1], ss);
                views.setTextColor(tvIds[i - 1], Color.WHITE);
                views.setInt(dotIds[i - 1], "setColorFilter", Color.WHITE);
                views.setInt(dotIds[i - 1], "setImageAlpha", 255);
            } else {
                views.setTextViewText(tvIds[i - 1], letter);
                views.setTextColor(tvIds[i - 1], Color.parseColor("#A0FFFFFF"));
                views.setInt(dotIds[i - 1], "setColorFilter", Color.WHITE);
                views.setInt(dotIds[i - 1], "setImageAlpha", 64);
            }
        }

        views.setInt(R.id.widget_mic_icon, "setColorFilter", Color.WHITE);

        // Setup Mic Click
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra("trigger_voice", true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_mic_btn, pendingIntent);

        // Fetch Data and Update Next Dose
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(context);
                List<Medicine> meds = db.medicineDao().getAll();
                String nextDose = getNextUpcomingDose(meds);
                
                RemoteViews updateViews = new RemoteViews(context.getPackageName(), R.layout.med_widget);
                updateViews.setTextViewText(R.id.widget_next_dose, nextDose);
                appWidgetManager.partiallyUpdateAppWidget(appWidgetId, updateViews);
            } catch (Exception ignored) {}
        });

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static String getNextUpcomingDose(List<Medicine> meds) {
        Calendar now = Calendar.getInstance();
        int nowTotal = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        String closest = "--:--";
        int minDiff = Integer.MAX_VALUE;
        for (Medicine m : meds) {
            if (m.times == null || m.times.isEmpty()) continue;
            for (String t : m.times.split(",")) {
                try {
                    String[] p = t.trim().split(":");
                    int total = Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
                    int diff = total - nowTotal;
                    if (diff < 0) diff += 1440;
                    if (diff < minDiff) { minDiff = diff; closest = t.trim(); }
                } catch (Exception ignored) {}
            }
        }
        return closest;
    }
}
