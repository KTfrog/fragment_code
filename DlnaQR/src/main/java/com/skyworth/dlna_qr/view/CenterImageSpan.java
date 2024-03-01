package com.skyworth.dlna_qr.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.style.ImageSpan;

public class CenterImageSpan extends ImageSpan {


    public CenterImageSpan(Context context, Bitmap bitmap) {
        super(context, bitmap);
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
        Drawable b = getDrawable();
        canvas.save();


        int transY = bottom - b.getBounds().bottom;

        if (mVerticalAlignment == ALIGN_BOTTOM) {

        }
        if (mVerticalAlignment == ALIGN_BASELINE) {
            transY -= paint.getFontMetricsInt().descent;
        } else {
            Paint.FontMetricsInt fm = paint.getFontMetricsInt();
            transY = (y + fm.descent + y + fm.ascent) / 2 - b.getBounds().bottom / 2;
        }

        canvas.translate(x, transY);
        b.draw(canvas);
        canvas.restore();
    }
}