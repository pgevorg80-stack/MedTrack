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
    private final int MAX_BARS = 30;
    private float currentRms = 0f;
    private final Random random = new Random();

    public VoiceVisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setColor(Color.parseColor("#2196F3"));
        paint.setStrokeWidth(8f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < MAX_BARS; i++) heights.add(0.1f);
    }

    public void updateRms(float rms) {
        this.currentRms = Math.max(0.1f, rms);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float centerY = height / 2;
        float spacing = width / MAX_BARS;

        // Shift heights and add new one
        heights.remove(0);
        float normalizedRms = (currentRms + 2f) / 10f; // Simple normalization
        if (normalizedRms < 0.1f) normalizedRms = 0.1f + random.nextFloat() * 0.1f;
        heights.add(normalizedRms);

        for (int i = 0; i < MAX_BARS; i++) {
            float barHeight = heights.get(i) * height * 0.8f;
            float x = i * spacing + spacing / 2;
            canvas.drawLine(x, centerY - barHeight / 2, x, centerY + barHeight / 2, paint);
        }
        
        // Auto-animate a bit if it's static
        postInvalidateDelayed(50);
    }
}
