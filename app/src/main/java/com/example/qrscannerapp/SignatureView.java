package com.example.qrscannerapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class SignatureView extends View {

    private Path path = new Path();
    // Длина текущего штриха: касание без движения — не подпись (раньше оно ставило hasSignature)
    private float strokeLength = 0f;
    private float lastX, lastY;
    private Paint paint = new Paint();
    private Bitmap bitmap;
    private Canvas canvas;
    private boolean hasSignature = false;
    // Подпись, пришедшая до того, как у вида появился размер
    private Bitmap pendingSignature;

    public SignatureView(Context context) {
        super(context);
        init();
    }

    public SignatureView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint.setAntiAlias(true);
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(6f);
        setClickable(true);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);
            if (pendingSignature != null) {
                Bitmap signature = pendingSignature;
                pendingSignature = null;
                setSignature(signature);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Не даём ScrollView перехватывать жест, пока рисуется подпись
                getParent().requestDisallowInterceptTouchEvent(true);
                path.moveTo(x, y);
                strokeLength = 0f;
                lastX = x;
                lastY = y;
                return true;
            case MotionEvent.ACTION_MOVE:
                path.lineTo(x, y);
                strokeLength += (float) Math.hypot(x - lastX, y - lastY);
                lastX = x;
                lastY = y;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                path.lineTo(x, y);
                strokeLength += (float) Math.hypot(x - lastX, y - lastY);
                // Случайное касание (меньше ~4 dp) не рисуем и подписью не считаем
                if (strokeLength >= 4 * getResources().getDisplayMetrics().density) {
                    if (canvas != null) {
                        canvas.drawPath(path, paint);
                    }
                    hasSignature = true;
                }
                path.reset();
                break;
            default:
                return false;
        }

        invalidate();
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap != null) {
            canvas.drawBitmap(bitmap, 0, 0, null);
        }
        canvas.drawPath(path, paint);
    }

    public void clear() {
        path.reset();
        pendingSignature = null;
        hasSignature = false;
        if (canvas != null) {
            canvas.drawColor(Color.WHITE);
        }
        invalidate();
    }

    /**
     * Вписать готовую подпись (например, нарисованную на весь экран) по центру, без искажений
     */
    public void setSignature(Bitmap signature) {
        if (signature == null) {
            clear();
            return;
        }
        if (canvas == null) {
            pendingSignature = signature;
            hasSignature = true;
            return;
        }

        path.reset();
        canvas.drawColor(Color.WHITE);
        float scale = Math.min((float) bitmap.getWidth() / signature.getWidth(),
                (float) bitmap.getHeight() / signature.getHeight());
        float width = signature.getWidth() * scale;
        float height = signature.getHeight() * scale;
        float left = (bitmap.getWidth() - width) / 2f;
        float top = (bitmap.getHeight() - height) / 2f;
        canvas.drawBitmap(signature, null, new android.graphics.RectF(left, top, left + width, top + height),
                new Paint(Paint.FILTER_BITMAP_FLAG));
        hasSignature = true;
        invalidate();
    }

    public boolean hasSignature() {
        return hasSignature;
    }

    public Bitmap getSignatureBitmap() {
        if (!hasSignature) return null;
        return pendingSignature != null ? pendingSignature : bitmap;
    }
}
