package com.example.automotivecarpropapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class SpeedGraphView extends View {

    private static final int MAX_POINTS = 60;   // ~60 de mostre pe ecran

    private final List<Float> speeds = new ArrayList<>();
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final Path fillPath = new Path();

    private float maxSpeed = 100f;   // scala minimă a axei Y (crește automat)

    public SpeedGraphView(Context context, AttributeSet attrs) {
        super(context, attrs);

        linePaint.setColor(0xFFFF5722);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(5f);

        fillPaint.setColor(0x33FF5722);
        fillPaint.setStyle(Paint.Style.FILL);

        gridPaint.setColor(0x33FFFFFF);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(2f);
    }

    public void addSpeed(float speedKmh) {
        speeds.add(speedKmh);
        if (speeds.size() > MAX_POINTS) {
            speeds.remove(0);
        }
        // scala crește dacă viteza depășește maximul curent
        if (speedKmh > maxSpeed) {
            maxSpeed = speedKmh * 1.1f;
        }
        invalidate();   // cere redesenare
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();

        // linii orizontale de ghidaj (25%, 50%, 75%)
        for (int i = 1; i <= 3; i++) {
            float y = h * i / 4f;
            canvas.drawLine(0, y, w, y, gridPaint);
        }

        if (speeds.size() < 2) {
            return;
        }

        linePath.reset();
        fillPath.reset();

        float stepX = w / (MAX_POINTS - 1);

        for (int i = 0; i < speeds.size(); i++) {
            float x = i * stepX;
            float y = h - (speeds.get(i) / maxSpeed) * h;
            if (i == 0) {
                linePath.moveTo(x, y);
                fillPath.moveTo(x, h);
                fillPath.lineTo(x, y);
            } else {
                linePath.lineTo(x, y);
                fillPath.lineTo(x, y);
            }
        }

        // închidem forma de umplere până jos
        float lastX = (speeds.size() - 1) * stepX;
        fillPath.lineTo(lastX, h);
        fillPath.close();

        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(linePath, linePaint);
    }
}