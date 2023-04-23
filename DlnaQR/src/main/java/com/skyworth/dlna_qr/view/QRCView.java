package com.skyworth.dlna_qr.view;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import android.R.integer;
import android.R.string;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;


	
	/**
	 * @auther gjc
	 * @since 2016/12/27.
	 */
	@SuppressLint("AppCompatCustomView")
	public class QRCView extends ImageView{

		public QRCView(Context context) {
			super(context);
			// TODO Auto-generated constructor stub
		}

		public QRCView(Context context, AttributeSet attrs) {
			super(context, attrs);
			// TODO Auto-generated constructor stub
		}

		public QRCView(Context context, AttributeSet attrs, int defStyle) {
			super(context, attrs, defStyle);
			// TODO Auto-generated constructor stub
		}

		public void createImageView(String str, int width, int height, Bitmap logoBm) {

	        try {
	            Hashtable<EncodeHintType, Object> hints = new Hashtable<EncodeHintType, Object>();
	            hints.put(EncodeHintType.CHARACTER_SET, "utf-8");
	           // hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
//	            hints.put(EncodeHintType.MARGIN, 1);
	            BitMatrix matrix = new QRCodeWriter().encode(str, BarcodeFormat.QR_CODE, width, height,hints);
	            matrix = deleteWhite(matrix);//删除白边
	            width = matrix.getWidth();
	            height = matrix.getHeight();
	            int[] pixels = new int[width * height];
	            for (int y = 0; y < height; y++) {
	                for (int x = 0; x < width; x++) {
	                    if (matrix.get(x, y)) {
	                        pixels[y * width + x] = Color.BLACK;
	                    } else {
	                        pixels[y * width + x] = Color.WHITE;
	                    }
	                }
	            }
	            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
	            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
	            //bitmap = addLogo(bitmap, logoBm);
	            setImageBitmap(bitmap);
	           
	        } catch (Exception e) {
	        	e.printStackTrace();
	        }
	    }

	    /**
	     * 删除白边
	     * */
	    private static BitMatrix deleteWhite(BitMatrix matrix) {
	        int[] rec = matrix.getEnclosingRectangle();
	        int resWidth = rec[2] + 1;
	        int resHeight = rec[3] + 1;

	        BitMatrix resMatrix = new BitMatrix(resWidth, resHeight);
	        resMatrix.clear();
	        for (int i = 0; i < resWidth; i++) {
	            for (int j = 0; j < resHeight; j++) {
	                if (matrix.get(i + rec[0], j + rec[1]))
	                    resMatrix.set(i, j);
	            }
	        }
	        return resMatrix;
	    }

	    /**
	     * 在二维码中间添加Logo图案
	     */
	    private static Bitmap addLogo(Bitmap src, Bitmap logo) {
	        if (src == null) {
	            return null;
	        }

	        if (logo == null) {
	            return src;
	        }

	        //获取图片的宽高
	        int srcWidth = src.getWidth();
	        int srcHeight = src.getHeight();
	        int logoWidth = logo.getWidth();
	        int logoHeight = logo.getHeight();

	        if (srcWidth == 0 || srcHeight == 0) {
	            return null;
	        }

	        if (logoWidth == 0 || logoHeight == 0) {
	            return src;
	        }

	        //logo大小为二维码整体大小的1/5
	        float scaleFactor = srcWidth * 1.0f / 5 / logoWidth;
	        Bitmap bitmap = Bitmap.createBitmap(srcWidth, srcHeight, Bitmap.Config.ARGB_8888);
	        try {
	            Canvas canvas = new Canvas(bitmap);
	            canvas.drawBitmap(src, 0, 0, null);
	            canvas.scale(scaleFactor, scaleFactor, srcWidth / 2, srcHeight / 2);
	            canvas.drawBitmap(logo, (srcWidth - logoWidth) / 2, (srcHeight - logoHeight) / 2, null);

	            canvas.save();
				//canvas.save(Canvas.ALL_SAVE_FLAG);
				canvas.restore();
	        } catch (Exception e) {
	            bitmap = null;
	            e.getStackTrace();
	        }

	        return bitmap;
	    }

	}

