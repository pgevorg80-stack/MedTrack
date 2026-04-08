package com.example.gevorgpetrosyan;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class VoiceVisualizerView extends View {
    private final Paint paint = new Paint();
    private final List<Float> heights = new ArrayList<>();
    private final int MAX_BARS = 40;
    private float currentRms = 0f;
    private final Random random = new Random();
    private float animationOffset = 0f;

    public VoiceVisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setColor(Color.parseColor("#2196F3"));
        paint.setStrokeWidth(12f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setAntiAlias(true);
        for (int i = 0; i < MAX_BARS; i++) heights.add(0.1f);
    }

    public void updateRms(float rms) {
        // Smoothly transition the RMS value
        this.currentRms = (this.currentRms * 0.4f) + (Math.max(0.1f, rms) * 0.6f);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float centerY = height / 2;
        float spacing = width / MAX_BARS;
        
        animationOffset += 0.15f;

        // Shift heights and add new one
        heights.remove(0);
        float normalizedRms = (currentRms + 2f) / 10f; 
        if (normalizedRms < 0.15f) {
            // Idle "breathing" animation
            normalizedRms = 0.15f + (float) Math.sin(animationOffset) * 0.05f;
        }
        heights.add(normalizedRms);

        for (int i = 0; i < MAX_BARS; i++) {
            // Add a wave effect to the bars
            float wave = (float) Math.sin(animationOffset + (i * 0.3f)) * 0.1f;
            float barHeight = (heights.get(i) + wave) * height * 0.7f;
            
            // Ensure minimum visible bar
            barHeight = Math.max(15f, barHeight);
            
            float x = i * spacing + spacing / 2;
            
            // Gradient effect: middle bars are taller and brighter
            int alpha = (int) (255 * (0.4f + 0.6f * (1f - Math.abs(i - MAX_BARS/2f) / (MAX_BARS/2f))));
            paint.setAlpha(alpha);
            
            canvas.drawLine(x, centerY - barHeight / 2, x, centerY + barHeight / 2, paint);
        }
        
        postInvalidateOnAnimation();
    }
}
