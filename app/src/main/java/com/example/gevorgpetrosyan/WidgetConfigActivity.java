package com.example.gevorgpetrosyan;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.SeekBar;
import androidx.appcompat.app.AppCompatActivity;

public class WidgetConfigActivity extends AppCompatActivity {

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private View widgetPreview;
    private SeekBar sbBgHue, sbTextHue;
    private CheckBox cbTextWhite;
    
    private int currentBgColor = Color.parseColor("#2196F3");
    private int currentTextColor = Color.WHITE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        setContentView(R.layout.activity_widget_config);

        widgetPreview = findViewById(R.id.widget_banner); // Inside the included layout
        sbBgHue = findViewById(R.id.sb_bg_hue);
        sbTextHue = findViewById(R.id.sb_text_hue);
        cbTextWhite = findViewById(R.id.cb_text_white);

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

        findViewById(R.id.btn_confirm).setOnClickListener(v -> saveAndFinish());
        
        updatePreview();
    }

    private void updatePreview() {
        float[] bgHsv = {sbBgHue.getProgress(), 0.8f, 0.9f};
        currentBgColor = Color.HSVToColor(bgHsv);
        
        if (cbTextWhite.isChecked()) {
            currentTextColor = Color.WHITE;
            sbTextHue.setEnabled(false);
        } else {
            sbTextHue.setEnabled(true);
            float[] textHsv = {sbTextHue.getProgress(), 1f, 0.2f}; // Darker colors for text if not white
            currentTextColor = Color.HSVToColor(textHsv);
        }

        // Apply to preview (Simplified application for preview)
        if (widgetPreview != null) {
            widgetPreview.setBackgroundColor(currentBgColor);
            // In a real app we'd find all textviews in preview and color them
            // For now, this gives a visual hint.
        }
    }

    private void saveAndFinish() {
        SharedPreferences prefs = getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("bg_color_" + appWidgetId, currentBgColor);
        editor.putInt("text_color_" + appWidgetId, currentTextColor);
        editor.apply();

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        MedWidget.updateAppWidget(this, appWidgetManager, appWidgetId);

        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, resultValue);
        finish();
    }
}
