package com.example.gevorgpetrosyan;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.util.Calendar;

public class WidgetConfigActivity extends AppCompatActivity {

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private ImageView widgetBackgroundPreview;
    private SeekBar sbBgHue, sbTextHue;
    private CheckBox cbTextWhite;
    private boolean isRussian = false;
    
    private int currentBgColor = Color.parseColor("#2196F3");
    private int currentTextColor = Color.WHITE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);

        // Load language preference
        isRussian = getSharedPreferences("LangPrefs", MODE_PRIVATE).getBoolean("IsRussian", false);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        setContentView(R.layout.activity_widget_config);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        widgetBackgroundPreview = findViewById(R.id.widget_background_img);
        sbBgHue = findViewById(R.id.sb_bg_hue);
        sbTextHue = findViewById(R.id.sb_text_hue);
        cbTextWhite = findViewById(R.id.cb_text_white);

        // Apply Translations
        updateUIStrings();

        // Initial state for animation
        findViewById(R.id.tv_config_header).setAlpha(0);
        findViewById(R.id.tv_live_preview_label).setAlpha(0);
        findViewById(R.id.preview_card).setAlpha(0);
        findViewById(R.id.controls_card).setAlpha(0);

        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updatePreview();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };

        sbBgHue.setOnSeekBarChangeListener(listener);
        sbTextHue.setOnSeekBarChangeListener(listener);
        cbTextWhite.setOnCheckedChangeListener((v, checked) -> updatePreview());

        findViewById(R.id.btn_confirm).setOnClickListener(v -> handleConfirm());
        
        updatePreview();
        startEntranceAnimations();
    }

    private void startEntranceAnimations() {
        animateViewIn(findViewById(R.id.tv_config_header), 100);
        animateViewIn(findViewById(R.id.tv_live_preview_label), 200);
        animateViewIn(findViewById(R.id.preview_card), 300);
        animateViewIn(findViewById(R.id.controls_card), 400);
    }

    private void animateViewIn(View view, int delay) {
        if (view == null) return;
        view.setAlpha(0f);
        view.setTranslationY(50f);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(600)
                .setStartDelay(delay)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f))
                .start();
    }

    private String tr(String en, String ru) {
        return isRussian ? ru : en;
    }

    private void updateUIStrings() {
        ((TextView) findViewById(R.id.tv_config_header)).setText(tr("Customize Your Widget", "Настройка виджета"));
        ((TextView) findViewById(R.id.tv_live_preview_label)).setText(tr("Live Preview", "Предпросмотр"));
        ((TextView) findViewById(R.id.tv_bg_color_label)).setText(tr("Background Color", "Цвет фона"));
        ((TextView) findViewById(R.id.tv_text_color_label)).setText(tr("Text and Icons Color", "Цвет текста и иконок"));
        cbTextWhite.setText(tr("Keep text white (Better contrast)", "Белый текст (лучший контраст)"));
        ((MaterialButton) findViewById(R.id.btn_confirm)).setText(tr("ADD TO HOME SCREEN", "ДОБАВИТЬ НА ЭКРАН"));
    }

    private void updatePreview() {
        float[] bgHsv = {sbBgHue.getProgress(), 0.8f, 0.9f};
        currentBgColor = Color.HSVToColor(bgHsv);
        
        if (cbTextWhite.isChecked()) {
            currentTextColor = Color.WHITE;
            sbTextHue.setEnabled(false);
        } else {
            sbTextHue.setEnabled(true);
            float[] textHsv = {sbTextHue.getProgress(), 0.8f, 0.4f}; 
            currentTextColor = Color.HSVToColor(textHsv);
        }

        if (widgetBackgroundPreview != null) {
            widgetBackgroundPreview.setColorFilter(currentBgColor);
            
            ((TextView)findViewById(R.id.widget_time)).setTextColor(currentTextColor);
            ((TextView)findViewById(R.id.widget_next_dose)).setTextColor(currentTextColor);
            ((ImageView)findViewById(R.id.widget_mic_icon)).setColorFilter(currentTextColor);
            
            int currentDay = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
            int[] tvIds = {R.id.tv_day_1, R.id.tv_day_2, R.id.tv_day_3, R.id.tv_day_4, R.id.tv_day_5, R.id.tv_day_6, R.id.tv_day_7};
            int[] dotIds = {R.id.dot_day_1, R.id.dot_day_2, R.id.dot_day_3, R.id.dot_day_4, R.id.dot_day_5, R.id.dot_day_6, R.id.dot_day_7};
            
            int subTextColor = Color.argb(160, Color.red(currentTextColor), Color.green(currentTextColor), Color.blue(currentTextColor));

            for (int i = 1; i <= 7; i++) {
                TextView tvDay = findViewById(tvIds[i-1]);
                ImageView dotDay = findViewById(dotIds[i-1]);
                
                if (tvDay != null) {
                    if (i == currentDay) {
                        tvDay.setTextColor(currentTextColor);
                        tvDay.setTypeface(null, Typeface.BOLD);
                    } else {
                        tvDay.setTextColor(subTextColor);
                        tvDay.setTypeface(null, Typeface.NORMAL);
                    }
                }
                
                if (dotDay != null) {
                    dotDay.setColorFilter(currentTextColor);
                    dotDay.setImageAlpha(i == currentDay ? 255 : 64);
                }
            }
        }
    }

    private void handleConfirm() {
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            saveColors(appWidgetId);
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
            MedWidget.updateAppWidget(this, appWidgetManager, appWidgetId);

            Intent resultValue = new Intent();
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            setResult(RESULT_OK, resultValue);
            finish();
        } else {
            pinWidgetToHomeScreen();
        }
    }

    private void saveColors(int id) {
        SharedPreferences prefs = getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE);
        prefs.edit()
            .putInt("bg_color_" + id, currentBgColor)
            .putInt("text_color_" + id, currentTextColor)
            .apply();
    }

    private void pinWidgetToHomeScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AppWidgetManager appWidgetManager = getSystemService(AppWidgetManager.class);
            ComponentName myProvider = new ComponentName(this, MedWidget.class);

            if (appWidgetManager.isRequestPinAppWidgetSupported()) {
                Intent callbackIntent = new Intent(this, MedWidget.class);
                callbackIntent.setAction("com.example.gevorgpetrosyan.WIDGET_PINNED");
                callbackIntent.putExtra("pending_bg", currentBgColor);
                callbackIntent.putExtra("pending_text", currentTextColor);
                
                PendingIntent successCallback = PendingIntent.getBroadcast(this, 0, 
                        callbackIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

                appWidgetManager.requestPinAppWidget(myProvider, null, successCallback);
                String msg = tr("Please confirm placement on your home screen", "Пожалуйста, подтвердите размещение на главном экране");
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                finish();
            } else {
                String err = tr("Pinned widgets not supported", "Закрепление виджетов не поддерживается");
                Toast.makeText(this, err, Toast.LENGTH_SHORT).show();
            }
        } else {
            String err = tr("Add widget from home screen manually", "Добавьте виджет вручную с главного экрана");
            Toast.makeText(this, err, Toast.LENGTH_SHORT).show();
        }
    }
}
