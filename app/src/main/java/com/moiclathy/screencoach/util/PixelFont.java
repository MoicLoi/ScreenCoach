package com.moiclathy.screencoach.util;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * A generated 6x8 bitmap font for printable ASCII (uppercase, lowercase,
 * digits and common punctuation). At scale 2 each glyph is at most 12x16 px,
 * matching the compact overlay's <=16 px per-character limit. Unsupported
 * characters fall back to a 14 px monospace system glyph.
 */
public final class PixelFont {
    public static final int GLYPH_WIDTH = 6;
    public static final int GLYPH_HEIGHT = 8;
    private static final int FIRST = 32;
    private static final int COUNT = 95;
    private static final int[][] GLYPHS = {
        {0,0,0,0,0,0,0,0}, // space
        {0,8,8,8,8,0,8,0}, // !
        {0,20,20,0,0,0,0,0}, // \"
        {12,4,62,20,60,24,8,0}, // #
        {0,0,28,24,28,12,28,0}, // $
        {0,48,40,60,28,10,12,0}, // %
        {0,28,16,16,42,36,30,0}, // &
        {0,8,8,0,0,0,0,0}, // '
        {8,8,8,8,8,8,8,0}, // (
        {0,8,8,8,8,8,0,0}, // )
        {0,12,24,24,12,0,0,0}, // *
        {0,0,8,8,62,8,8,0}, // +
        {0,0,0,0,0,0,8,8}, // ,
        {0,0,0,0,28,0,0,0}, // -
        {0,0,0,0,0,0,8,0}, // .
        {0,4,4,8,8,16,16,0}, // /
        {0,28,20,44,36,20,28,0}, // 0
        {0,24,8,8,8,8,28,0}, // 1
        {0,28,4,4,8,16,60,0}, // 2
        {0,28,4,4,28,4,60,0}, // 3
        {0,12,12,20,4,62,4,0}, // 4
        {0,28,16,28,4,4,60,0}, // 5
        {0,28,16,48,60,20,28,0}, // 6
        {0,60,4,4,8,8,16,0}, // 7
        {0,28,20,20,28,52,28,0}, // 8
        {0,28,52,28,4,4,24,0}, // 9
        {0,0,0,8,0,0,8,0}, // :
        {0,0,0,8,0,0,8,8}, // \;
        {0,0,0,4,24,24,4,0}, // <
        {0,0,0,62,0,62,0,0}, // =
        {0,0,0,48,12,12,48,0}, // >
        {0,28,4,8,8,0,8,0}, // ?
        {0,0,28,50,46,46,48,28}, // @
        {0,8,24,20,20,60,34,0}, // A
        {0,28,20,20,28,20,28,0}, // B
        {0,28,16,16,48,16,28,0}, // C
        {0,56,36,36,36,36,56,0}, // D
        {0,28,16,16,28,16,28,0}, // E
        {0,28,16,16,28,16,16,0}, // F
        {0,28,16,32,36,20,28,0}, // G
        {0,36,36,36,60,36,36,0}, // H
        {0,28,8,8,8,8,28,0}, // I
        {0,28,4,4,4,4,56,0}, // J
        {0,36,40,56,56,36,38,0}, // K
        {0,16,16,16,16,16,30,0}, // L
        {0,54,54,62,42,34,34,0}, // M
        {0,52,52,60,44,36,36,0}, // N
        {0,28,20,36,36,20,28,0}, // O
        {0,28,22,28,16,16,16,0}, // P
        {0,28,20,36,36,20,28,4}, // Q
        {0,60,36,60,36,36,34,0}, // R
        {0,28,48,24,12,4,28,0}, // S
        {0,62,8,8,8,8,8,0}, // T
        {0,36,36,36,36,20,28,0}, // U
        {0,34,20,20,20,24,8,0}, // V
        {0,34,34,42,60,20,20,0}, // W
        {0,20,20,8,8,20,34,0}, // X
        {0,36,20,24,8,8,8,0}, // Y
        {0,30,4,8,8,16,30,0}, // Z
        {12,8,8,8,8,8,12,0}, // [
        {0,0,16,16,8,8,4,4}, // \\
        {24,8,8,8,8,8,24,0}, // ]
        {0,8,20,0,0,0,0,0}, // ^
        {0,0,0,0,0,0,0,0}, // _
        {0,0,0,0,0,0,0,0}, // `
        {0,0,0,28,28,52,28,0}, // a
        {16,16,16,28,20,20,28,0}, // b
        {0,0,0,28,16,16,28,0}, // c
        {4,4,4,28,52,52,28,0}, // d
        {0,0,0,28,60,48,28,0}, // e
        {12,8,8,28,8,8,8,0}, // f
        {0,0,0,28,52,52,28,4}, // g
        {16,16,16,28,20,20,20,0}, // h
        {8,0,0,24,8,8,28,0}, // i
        {8,0,0,24,8,8,8,8}, // j
        {16,16,16,20,24,20,20,0}, // k
        {24,8,8,8,8,8,12,0}, // l
        {0,0,0,60,42,42,42,0}, // m
        {0,0,0,28,20,20,20,0}, // n
        {0,0,0,28,20,52,28,0}, // o
        {0,0,0,28,20,20,28,16}, // p
        {0,0,0,28,52,52,28,4}, // q
        {0,0,0,30,24,16,16,0}, // r
        {0,0,0,28,16,4,28,0}, // s
        {0,0,8,60,8,8,12,0}, // t
        {0,0,0,20,20,20,28,0}, // u
        {0,0,0,36,20,20,8,0}, // v
        {0,0,0,34,42,28,20,0}, // w
        {0,0,0,20,8,24,20,0}, // x
        {0,0,0,4,20,28,8,8}, // y
        {0,0,0,28,0,0,28,0}, // z
        {12,8,8,24,8,8,12,0}, // {
        {8,8,8,8,8,8,8,8}, // |
        {24,8,8,12,8,8,24,0}, // }
        {0,0,0,0,62,0,0,0}, // ~
    };

    private PixelFont() { }

    public static float measure(String text, int scale, Paint fallback) {
        if (text == null) return 0f;
        float width = 0f;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= FIRST && c < FIRST + COUNT) {
                width += GLYPH_WIDTH * scale;
            } else {
                width += fallback.measureText(String.valueOf(c));
            }
        }
        return width;
    }

    public static void draw(Canvas canvas, String text, float x, float y, int scale,
                            Paint glyphPaint, Paint fallbackPaint) {
        if (text == null) return;
        float cursor = x;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < FIRST || c >= FIRST + COUNT) {
                canvas.drawText(String.valueOf(c), cursor, y + 14f, fallbackPaint);
                cursor += fallbackPaint.measureText(String.valueOf(c));
                continue;
            }
            int[] rows = GLYPHS[c - FIRST];
            for (int row = 0; row < GLYPH_HEIGHT; row++) {
                int bits = rows[row];
                if (bits == 0) continue;
                for (int col = 0; col < GLYPH_WIDTH; col++) {
                    if ((bits & (1 << (GLYPH_WIDTH - 1 - col))) == 0) continue;
                    float left = cursor + col * scale;
                    float top = y + row * scale;
                    canvas.drawRect(left, top, left + scale, top + scale, glyphPaint);
                }
            }
            cursor += GLYPH_WIDTH * scale;
        }
    }
}
