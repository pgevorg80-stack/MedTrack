package com.example.gevorgpetrosyan;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;

public class MedicineInfoHelper {
    private static final String API_KEY = "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";
    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL = "llama-3.3-70b-versatile"; // Stable fast Groq model

    public static void showMedicineInfo(Context context, String medicineName) {
        if (medicineName == null || medicineName.isEmpty()) return;

        boolean isRu = isRussian(context);
        
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(60, 40, 60, 40);
        
        TextView titleTv = new TextView(context);
        titleTv.setText(medicineName);
        titleTv.setTextSize(22);
        titleTv.setTypeface(null, Typeface.BOLD);
        titleTv.setTextColor(Color.parseColor("#2196F3"));
        titleTv.setPadding(0, 0, 0, 20);
        root.addView(titleTv);

        ProgressBar loader = new ProgressBar(context, null, android.R.attr.progressBarStyleSmall);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.gravity = Gravity.CENTER;
        lp.setMargins(0, 40, 0, 40);
        loader.setLayoutParams(lp);
        root.addView(loader);

        TextView infoTv = new TextView(context);
        infoTv.setText(isRu ? "AI ищет информацию..." : "AI is searching for info...");
        infoTv.setTextSize(15);
        infoTv.setTextColor(Color.GRAY);
        root.addView(infoTv);

        builder.setView(root);
        builder.setPositiveButton(isRu ? "Закры?ть" : "Close", null);
        AlertDialog dialog = builder.create();
        dialog.show();

        fetchAIInfo(context, medicineName, isRu, new AICallback() {
            @Override
            public void onSuccess(String result) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    root.removeView(loader);
                    infoTv.setTextColor(Color.BLACK);
                    infoTv.setText(result);
                });
            }

            @Override
            public void onError(String error) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    root.removeView(loader);
                    infoTv.setTextColor(Color.RED);
                    infoTv.setText(error);
                });
            }
        });
    }

    private static void fetchAIInfo(Context context, String medicineName, boolean isRu, AICallback callback) {
        OkHttpClient client = new OkHttpClient();

        String prompt = isRu ? 
            "Кратко опиши лекарство " + medicineName + ". Что это, для чего применяется и основные побочные эффекты. Ответь строго на русском языке, максимум 5 строк." :
            "Briefly explain " + medicineName + ". What it is, primary uses, and main side effects. Max 5 lines.";

        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("model", MODEL);
            JSONArray messages = new JSONArray();
            JSONObject msg = new JSONObject();
            msg.put("role", "user");
            msg.put("content", prompt);
            messages.put(msg);
            jsonBody.put("messages", messages);
        } catch (Exception e) {
            callback.onError("Error: " + e.getMessage());
            return;
        }

        RequestBody body = RequestBody.create(
            jsonBody.toString(),
            MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
            .url(API_URL)
            .addHeader("Authorization", "Bearer " + API_KEY)
            .post(body)
            .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Network error: " + e.getMessage());
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
                    JSONArray choices = Jobject.getJSONArray("choices");
                    String content = choices.getJSONObject(0).getJSONObject("message").getString("content");
                    callback.onSuccess(content.trim());
                } catch (Exception e) {
                    callback.onError("Parse error: " + e.getMessage());
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
