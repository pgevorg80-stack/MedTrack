package com.example.gevorgpetrosyan;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;

public class MedicineInfoHelper {
    // Note: If GitHub blocks push, use the 'Unblock Secret' link in the error log.
    private static final String API_KEY = "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";
    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL = "llama-3.3-70b-versatile"; 

    public static void showMedicineInfo(Context context, String medicineName) {
        if (medicineName == null || medicineName.isEmpty()) return;

        boolean isRu = isRussian(context);
        boolean isDark = (context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int textColor = isDark ? Color.WHITE : Color.BLACK;
        float density = context.getResources().getDisplayMetrics().density;
        
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        
        // Root Layout mimicking Voice Log's structure
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * density);
        root.setPadding(padding, padding, padding, padding);
        
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(32 * density);
        if (isDark) {
            bg.setColor(Color.parseColor("#1C1C1E")); // Dark gray common in iOS/Modern Android
            bg.setStroke((int) (1 * density), Color.parseColor("#38383A")); // Subtle border
        } else {
            bg.setColor(Color.WHITE);
        }
        root.setBackground(bg);
        
        // Title (20sp, Bold, Centered, matching tv_voice_status)
        TextView titleTv = new TextView(context);
        titleTv.setText(medicineName);
        titleTv.setTextSize(20);
        titleTv.setTypeface(null, Typeface.BOLD);
        titleTv.setTextColor(Color.parseColor("#2196F3")); // Blue as requested
        titleTv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.setMargins(0, 0, 0, (int) (32 * density));
        titleTv.setLayoutParams(titleLp);
        root.addView(titleTv);

        // Circular Loader (Old design)
        ProgressBar loader = new ProgressBar(context);
        LinearLayout.LayoutParams loaderLp = new LinearLayout.LayoutParams(-2, -2);
        loaderLp.gravity = Gravity.CENTER;
        loaderLp.setMargins(0, 0, 0, (int) (24 * density));
        loader.setLayoutParams(loaderLp);
        root.addView(loader);

        // Scrollable content area
        ScrollView scrollView = new ScrollView(context);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, 0, 1.0f);
        scrollLp.setMargins(0, 0, 0, (int) (24 * density));
        scrollView.setLayoutParams(scrollLp);
        
        TextView infoTv = new TextView(context);
        infoTv.setText(isRu ? "AI готовит инструкцию..." : "AI is generating instructions...");
        infoTv.setTextSize(16);
        infoTv.setLineSpacing(1.2f, 1.2f);
        infoTv.setTextColor(isDark ? Color.WHITE : Color.GRAY);
        scrollView.addView(infoTv);
        root.addView(scrollView);

        // Big Rounded Button (matching btn_voice_ok)
        MaterialButton closeBtn = new MaterialButton(context);
        closeBtn.setText(isRu ? "Закрыть" : "Close");
        closeBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#2196F3")));
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setCornerRadius((int) (28 * density));
        closeBtn.setAllCaps(false);
        closeBtn.setElevation(4 * density);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-1, (int) (56 * density));
        closeBtn.setLayoutParams(btnLp);
        root.addView(closeBtn);

        builder.setView(root);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        
        closeBtn.setOnClickListener(v -> dialog.dismiss());

        dialog.show();

        fetchAIInfo(medicineName, isRu, new AICallback() {
            @Override
            public void onSuccess(String result) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    root.removeView(loader);
                    String cleanResult = result.trim().toLowerCase();
                    if (cleanResult.contains("это не лекарство") || cleanResult.contains("not a medicine")) {
                        infoTv.setText(isRu ? "Не найдено" : "Not found");
                        infoTv.setTextColor(isDark ? Color.WHITE : Color.BLACK);
                    } else {
                        infoTv.setTextColor(textColor);
                        infoTv.setText(result);
                    }
                });
            }

            @Override
            public void onError(String error) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    root.removeView(loader);
                    infoTv.setTextColor(Color.RED);
                    infoTv.setText(isRu ? "Ошибка: Информация не найдена" : "Error: Info not found");
                });
            }
        });
    }

    private static void fetchAIInfo(String medicineName, boolean isRu, AICallback callback) {
        OkHttpClient client = new OkHttpClient();

        // Updated prompt to handle non-medicine inputs and provide simple instructions
        String prompt = isRu ?
            "Если '" + medicineName + "' не является лекарством, ответь только: 'Это не лекарство'. " +
            "Если это лекарство, напиши простую и понятную инструкцию: как принимать, способ применения и дозировку. " +
            "Ответь строго на русском языке, объем текста 4-5 строк." :
            "If '" + medicineName + "' is not a medicine, reply only with: 'This is not a medicine.' " +
            "If it is a medicine, provide simple and clear instructions on how to use it, including usage and dosage. " +
            "Answer strictly in 4-5 lines.";

        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("model", MODEL);
            JSONArray messages = new JSONArray();
            JSONObject msg = new JSONObject();
            msg.put("role", "user");
            msg.put("content", prompt);
            messages.put(msg);
            jsonBody.put("messages", messages);
            jsonBody.put("temperature", 0.5); // Lower temperature for more factual instructions
        } catch (Exception e) {
            callback.onError(e.getMessage());
            return;
        }

        RequestBody body = RequestBody.create(jsonBody.toString(), MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(API_URL).addHeader("Authorization", "Bearer " + API_KEY).post(body).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onError("API Error: " + response.code());
                    return;
                }
                try {
                    String jsonData = response.body().string();
                    JSONObject Jobject = new JSONObject(jsonData);
                    String content = Jobject.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
                    callback.onSuccess(content.trim());
                } catch (Exception e) {
                    callback.onError(e.getMessage());
                }
            }
        });
    }

    interface AICallback {
        void onSuccess(String result);
        void onError(String error);
    }

    private static boolean isRussian(Context context) {
        return context.getSharedPreferences("LangPrefs", Context.MODE_PRIVATE).getBoolean("IsRussian", false);
    }
}
