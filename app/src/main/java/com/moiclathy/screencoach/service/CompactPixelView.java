package com.moiclathy.screencoach.service;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import com.moiclathy.screencoach.util.PixelFont;

/**
 * Draws the tiny pixel answer plate: question+answer and a colored status square.
 * The plate is white; glyphs are dark. At scale 2 an ASCII glyph is 12x16 px.
 * The status indicator is a single 1x1 px colored rect.
 * No L label, explanation or progress text is drawn.
 */
public final class CompactPixelView extends View {
    private static final int SCALE = 2;
    private static final int PADDING = 4;
    private static final int GAP = 4;
    private static final int COLOR_PLATE = 0xFFFFFFFF;
    private static final int COLOR_TEXT = 0xFF1F2937;

    private final Paint glyphPaint = new Paint();
    private final Paint fallbackPaint = new Paint();
    private final Paint squarePaint = new Paint();
    private final Paint bgPaint = new Paint();
    private final RectF bgRect = new RectF();
    private final float density;
    private String rawText = "1B";
    private boolean collapsed = false;
    private int statusColor = 0xFF22C55E;

    public CompactPixelView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        glyphPaint.setAntiAlias(false);
        glyphPaint.setColor(COLOR_TEXT);
        fallbackPaint.setAntiAlias(false);
        fallbackPaint.setColor(COLOR_TEXT);
        fallbackPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
        fallbackPaint.setTextSize(14f);
        squarePaint.setAntiAlias(false);
        bgPaint.setAntiAlias(false);
        bgPaint.setColor(COLOR_PLATE);
    }

    Paint fallbackPaint() {
        return fallbackPaint;
    }

    void setText(String text, boolean collapsed) {
        rawText = text == null ? "1?" : text;
        this.collapsed = collapsed;
        invalidate();
    }

    void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
        invalidate();
    }

    void setStatusColor(int color) {
        statusColor = color;
        invalidate();
    }

    static int desiredWidth(String text, Paint fallbackPaint) {
        return PADDING * 2 + Math.round(PixelFont.measure(text, SCALE, fallbackPaint))
                + GAP + CompactGeometry.SQUARE_SIZE;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        float radius = Math.max(3f, density * 2f);
        bgRect.set(0, 0, w, h);
        canvas.drawRoundRect(bgRect, radius, radius, bgPaint);

        int textMax = Math.max(8, w - PADDING * 2 - GAP - CompactGeometry.SQUARE_SIZE);
        String text = fitText(rawText, textMax);
        float textWidth = PixelFont.measure(text, SCALE, fallbackPaint);
        PixelFont.draw(canvas, text, PADDING, PADDING, SCALE, glyphPaint, fallbackPaint);

        int squareLeft = Math.round(PADDING + textWidth + GAP);
        int squareTop = (h - CompactGeometry.SQUARE_SIZE) / 2;
        squarePaint.setColor(statusColor);
        canvas.drawRect(squareLeft, squareTop,
                squareLeft + CompactGeometry.SQUARE_SIZE,
                squareTop + CompactGeometry.SQUARE_SIZE, squarePaint);
    }

    private String fitText(String text, int available) {
        if (PixelFont.measure(text, SCALE, fallbackPaint) <= available) return text;
        String suffix = "..";
        for (int end = text.length() - 1; end > 0; end--) {
            String candidate = text.substring(0, end) + suffix;
            if (PixelFont.measure(candidate, SCALE, fallbackPaint) <= available) {
                return candidate;
            }
        }
        return "?";
    }
}
