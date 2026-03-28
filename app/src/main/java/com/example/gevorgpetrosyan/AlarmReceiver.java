package com.example.gevorgpetrosyan;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AlarmReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "med_reminders";

    @Override
    public void onReceive(Context context, Intent intent) {
        String medName = intent.getStringExtra("med_name");
        String medTime = intent.getStringExtra("med_time");
        String userId = intent.getStringExtra("user_id");

        // Fallback to current user if userId was not passed (e.g., from old alarms)
        if (userId == null) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) userId = user.getUid();
        }

        if (userId == null) return;

        AppDatabase db = AppDatabase.getInstance(context);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // 1. Create Notification Channel (for Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Medicine Reminders", NotificationManager.IMPORTANCE_HIGH);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        // 2. Fetch the specific medicine to check Stock and Expiry
        Medicine m = db.medicineDao().getByNameAndUserId(medName, userId);
        if (m == null) return;

        int totalStock = calculateTotalStock(m.batches);
        String warningPrefix = "";

        // 3. Logic: Check for 0 Pills or Expired Batches
        if (totalStock <= 0) {
            warningPrefix = "[OUT OF STOCK] ";
        } else if (isMedicineExpired(m.batches)) {
            warningPrefix = "[EXPIRED!] ";
        }

        // 4. Create a unique notification ID using Name and Time
        int notificationId = (userId + medName + medTime).hashCode();

        // 5. Setup Action Buttons (Taken / Missed)
        Intent takenIntent = new Intent(context, ActionReceiver.class);
        takenIntent.putExtra("med_name", medName);
        takenIntent.putExtra("med_time", medTime);
        takenIntent.putExtra("user_id", userId);
        takenIntent.putExtra("status", "Taken");
        takenIntent.putExtra("notification_id", notificationId);
        PendingIntent takenPI = PendingIntent.getBroadcast(context, notificationId, takenIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent missedIntent = new Intent(context, ActionReceiver.class);
        missedIntent.putExtra("med_name", medName);
        missedIntent.putExtra("med_time", medTime);
        missedIntent.putExtra("user_id", userId);
        missedIntent.putExtra("status", "Missed");
        missedIntent.putExtra("notification_id", notificationId);
        PendingIntent missedPI = PendingIntent.getBroadcast(context, notificationId + 1, missedIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // 6. Build the Notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(warningPrefix + "Medicine Reminder")
                .setContentText("Time to take " + medName + ". Remaining: " + totalStock)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .addAction(android.R.drawable.ic_menu_edit, "I TOOK IT", takenPI)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "MISSED", missedPI);

        if (manager != null) {
            manager.notify(notificationId, builder.build());
        }
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

    private boolean isMedicineExpired(String batchString) {
        if (batchString == null || batchString.isEmpty()) return false;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("d/M/yyyy", Locale.getDefault());
            Date today = new Date();
            String[] batches = batchString.split("\\|");
            for (String batch : batches) {
                if (batch.contains("(Exp: ")) {
                    String dateStr = batch.substring(batch.indexOf("(Exp: ") + 6, batch.length() - 1);
                    Date expiryDate = sdf.parse(dateStr);
                    if (expiryDate != null && expiryDate.before(today)) return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }
}
