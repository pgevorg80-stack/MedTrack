package com.example.gevorgpetrosyan;

import android.app.NotificationManager;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class ActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String medName = intent.getStringExtra("med_name");
        String status = intent.getStringExtra("status");
        String userId = intent.getStringExtra("user_id");
        int notificationId = intent.getIntExtra("notification_id", -1);

        // 1. Dismiss the reminder notification immediately
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationId != -1 && manager != null) {
            manager.cancel(notificationId);
        }

        if (userId == null) return;

        // 2. Update Database in Background
        AppDatabase db = AppDatabase.getInstance(context);
        Executors.newSingleThreadExecutor().execute(() -> {
            Medicine m = db.medicineDao().getByNameAndUserId(medName, userId);
            if (m != null) {
                String timeStamp = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(new Date());
                if (m.history == null) m.history = "";
                m.history += timeStamp + " - " + status + ",";

                if ("Taken".equals(status) && m.batches != null && !m.batches.isEmpty()) {
                    m.batches = subtractFromBatches(m.batches, m.dosage);

                    if (calculateTotalStock(m.batches) <= 0) {
                        sendOutOfStockNotification(context, m.name);
                    }
                }

                db.medicineDao().update(m);
                FirestoreHelper.uploadMedicine(m);
                
                // Trigger Widget Update after DB change
                updateWidget(context);
            }
        });

        // 3. Close notification drawer
        context.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
    }

    private void updateWidget(Context context) {
        Intent updateIntent = new Intent(context, MedWidget.class);
        updateIntent.setAction(MedWidget.ACTION_AUTO_UPDATE);
        context.sendBroadcast(updateIntent);
    }

    private String subtractFromBatches(String batchString, int pillsToTake) {
        String[] batches = batchString.split("\\|");
        StringBuilder newBatchBuilder = new StringBuilder();
        int remainingToSubtract = pillsToTake;

        for (String batch : batches) {
            if (batch.trim().isEmpty()) continue;
            if (remainingToSubtract > 0) {
                try {
                    int firstSpace = batch.indexOf(" ");
                    int currentBatchAmount = Integer.parseInt(batch.substring(0, firstSpace));
                    String batchDetails = batch.substring(firstSpace);

                    if (currentBatchAmount <= remainingToSubtract) {
                        remainingToSubtract -= currentBatchAmount;
                    } else {
                        int newAmount = currentBatchAmount - remainingToSubtract;
                        remainingToSubtract = 0;
                        if (newBatchBuilder.length() > 0) newBatchBuilder.append("|");
                        newBatchBuilder.append(newAmount).append(batchDetails);
                    }
                } catch (Exception e) {
                    if (newBatchBuilder.length() > 0) newBatchBuilder.append("|");
                    newBatchBuilder.append(batch);
                }
            } else {
                if (newBatchBuilder.length() > 0) newBatchBuilder.append("|");
                newBatchBuilder.append(batch);
            }
        }
        return newBatchBuilder.toString();
    }

    private int calculateTotalStock(String batches) {
        if (batches == null || batches.isEmpty()) return 0;
        int total = 0;
        for (String b : batches.split("\\|")) {
            try {
                total += Integer.parseInt(b.split(" ")[0]);
            } catch (Exception ignored) {}
        }
        return total;
    }

    private void sendOutOfStockNotification(Context context, String name) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(context, "med_reminders")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Out of Stock!")
                .setContentText("Warning: You have 0 pills left of " + name)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        nm.notify((int)System.currentTimeMillis(), b.build());
    }
}
