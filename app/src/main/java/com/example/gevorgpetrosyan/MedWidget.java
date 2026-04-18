package com.example.gevorgpetrosyan;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MedWidget extends AppWidgetProvider {

    public static final String ACTION_AUTO_UPDATE = "com.example.gevorgpetrosyan.WIDGET_UPDATE";
    public static final String ACTION_WIDGET_PINNED = "com.example.gevorgpetrosyan.WIDGET_PINNED";
    private static final ExecutorService executor = Executors.newFixedThreadPool(2);

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        super.onDeleted(context, appWidgetIds);
        SharedPreferences prefs = context.getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        for (int appWidgetId : appWidgetIds) {
            editor.remove("bg_color_" + appWidgetId);
            editor.remove("text_color_" + appWidgetId);
            editor.remove("bg_opacity_" + appWidgetId);
        }
        editor.apply();
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        // Start ticking when first widget is added
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        
        if (ACTION_WIDGET_PINNED.equals(intent.getAction())) {
            int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                int bgColor = intent.getIntExtra("pending_bg", Color.parseColor("#2196F3"));
                int textColor = intent.getIntExtra("pending_text", Color.WHITE);
                int opacity = intent.getIntExtra("pending_opacity", 255);
                
                SharedPreferences prefs = context.getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE);
                prefs.edit()
                    .putInt("bg_color_" + appWidgetId, bgColor)
                    .putInt("text_color_" + appWidgetId, textColor)
                    .putInt("bg_opacity_" + appWidgetId, opacity)
                    .apply();
                    
                updateAppWidget(context, appWidgetManager, appWidgetId);
            }
        } else if (ACTION_AUTO_UPDATE.equals(intent.getAction()) || 
            Intent.ACTION_TIME_TICK.equals(intent.getAction()) || 
            Intent.ACTION_TIME_CHANGED.equals(intent.getAction()) ||
            Intent.ACTION_TIMEZONE_CHANGED.equals(intent.getAction()) ||
            Intent.ACTION_DATE_CHANGED.equals(intent.getAction())) {
            
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, MedWidget.class));
            for (int appWidgetId : appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId);
            }
        }
    }

    public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.med_widget);

        // Load Configured Colors
        SharedPreferences prefs = context.getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE);
        int bgColor = prefs.getInt("bg_color_" + appWidgetId, Color.parseColor("#2196F3"));
        int textColor = prefs.getInt("text_color_" + appWidgetId, Color.WHITE);
        int opacity = prefs.getInt("bg_opacity_" + appWidgetId, 255);
        int subTextColor = Color.argb(160, Color.red(textColor), Color.green(textColor), Color.blue(textColor));

        // Apply background tint and opacity
        views.setInt(R.id.widget_background_img, "setColorFilter", bgColor);
        views.setInt(R.id.widget_background_img, "setImageAlpha", opacity);

        // Update Time
        views.setTextColor(R.id.widget_time, textColor);

        // Update Week Grid
        boolean isRussian = context.getSharedPreferences("LangPrefs", Context.MODE_PRIVATE).getBoolean("IsRussian", false);
        String[] dayLettersEn = {"S", "M", "T", "W", "T", "F", "S"};
        String[] dayLettersRu = {"В", "П", "В", "С", "Ч", "П", "С"};
        String[] dayLetters = isRussian ? dayLettersRu : dayLettersEn;
        int currentDay = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);

        Locale widgetLocale = isRussian ? new Locale("ru") : Locale.US;
        views.setTextViewText(R.id.widget_date, new SimpleDateFormat("d MMMM", widgetLocale).format(new Date()));
        views.setTextColor(R.id.widget_date, subTextColor);

        int[] tvIds = {R.id.tv_day_1, R.id.tv_day_2, R.id.tv_day_3, R.id.tv_day_4, R.id.tv_day_5, R.id.tv_day_6, R.id.tv_day_7};
        int[] dotIds = {R.id.dot_day_1, R.id.dot_day_2, R.id.dot_day_3, R.id.dot_day_4, R.id.dot_day_5, R.id.dot_day_6, R.id.dot_day_7};

        for (int i = 1; i <= 7; i++) {
            String letter = dayLetters[i - 1];
            if (i == currentDay) {
                SpannableString ss = new SpannableString(letter);
                ss.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, letter.length(), 0);
                views.setTextViewText(tvIds[i - 1], ss);
                views.setTextColor(tvIds[i - 1], textColor);
                views.setInt(dotIds[i - 1], "setColorFilter", textColor);
                views.setInt(dotIds[i - 1], "setImageAlpha", 255);
            } else {
                views.setTextViewText(tvIds[i - 1], letter);
                views.setTextColor(tvIds[i - 1], subTextColor);
                views.setInt(dotIds[i - 1], "setColorFilter", textColor);
                views.setInt(dotIds[i - 1], "setImageAlpha", 64);
            }
        }

        views.setInt(R.id.widget_mic_icon, "setColorFilter", textColor);
        views.setTextColor(R.id.widget_next_dose, textColor);
        
        // Apply colors to new elements (Labels and pill backgrounds)
        views.setTextViewText(R.id.widget_next_dose_label, isRussian ? "СЛЕД. ДОЗА" : "NEXT DOSE");
        views.setTextColor(R.id.widget_next_dose_label, Color.argb(204, Color.red(textColor), Color.green(textColor), Color.blue(textColor)));
        views.setInt(R.id.widget_next_dose_bg, "setColorFilter", textColor);
        views.setInt(R.id.widget_next_dose_bg, "setImageAlpha", 64);
        views.setInt(R.id.widget_mic_bg, "setColorFilter", textColor);
        views.setInt(R.id.widget_mic_bg, "setImageAlpha", 64);

        // Setup Mic Click
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra("trigger_voice", true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_mic_btn, pendingIntent);

        // Fetch Data and Update Next Dose
        executor.execute(() -> {
            try {
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if (user == null) {
                    RemoteViews updateViews = new RemoteViews(context.getPackageName(), R.layout.med_widget);
                    updateViews.setTextViewText(R.id.widget_next_dose, "--:--");
                    appWidgetManager.partiallyUpdateAppWidget(appWidgetId, updateViews);
                    return;
                }
                
                AppDatabase db = AppDatabase.getInstance(context);
                List<Medicine> meds = db.medicineDao().getAllByUserId(user.getUid());
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
        String todayStr = new SimpleDateFormat("MMM dd", Locale.getDefault()).format(new Date());

        String closest = "--:--";
        int minDiff = Integer.MAX_VALUE;
        for (Medicine m : meds) {
            if (m.times == null || m.times.isEmpty()) continue;
            for (String t : m.times.split(",")) {
                try {
                    String time = t.trim();
                    String[] p = time.split(":");
                    int total = Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);

                    // Skip if taken today at this specific time
                    if (m.history != null && m.history.contains(todayStr) && m.history.contains(time)) {
                        continue;
                    }

                    int diff = total - nowTotal;
                    if (diff < 0) diff += 1440;
                    if (diff < minDiff) {
                        minDiff = diff;
                        closest = time;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return closest;
    }
}
