package com.skyworth.dlna_qr.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.AsyncTask;

import com.skyworth.sys.param.SkParam;
import com.skyworthdigital.SkNetwork;

public class Utils {

    public static final int CONN_TYPE_DHCP = 106;
    public static final int CONN_TYPE_IPOE = 107;
    public static final int CONN_TYPE_PPPOE = 108;

    /**
     * 调整图片大小
     *
     * @param bitmap 源
     * @param dst_w  输出宽度
     * @param dst_h  输出高度
     * @return
     */
    public static Bitmap imageScale(Bitmap bitmap, int dst_w, int dst_h) {
        int src_w = bitmap.getWidth();
        int src_h = bitmap.getHeight();
        float scale_w = ((float) dst_w) / src_w;
        float scale_h = ((float) dst_h) / src_h;
        Matrix matrix = new Matrix();
        matrix.postScale(scale_w, scale_h);
        Bitmap dstbmp = Bitmap.createBitmap(bitmap, 0, 0, src_w, src_h, matrix,
                true);
        return dstbmp;
    }

    public static int dip2px(Context mContext, float dipValue) {
        float scale = mContext.getResources().getDisplayMetrics().density;
        return (int) (dipValue * scale + 0.5f);
    }

    public static void connectByType(Context context, final int type) {
        SkNetwork mSkyNetwork = new SkNetwork(context);
        AsyncTask<String, Void, String> task = new AsyncTask<String, Void, String>() {
            @Override
            protected String doInBackground(String... arg0) {
                switch(type) {
                    case CONN_TYPE_DHCP:
                        mSkyNetwork.setEthernetDhcpMode();
                        break;
                    case CONN_TYPE_IPOE:
                        String usr = SkParam.getParam(SkParam.SK_PARAM_NET_DHCPUSR);
                        String pwd = SkParam.getParam(SkParam.SK_PARAM_NET_DHCPPWD);
                        mSkyNetwork.setEthernetIpoeMode(usr, pwd);
                        break;
                    case CONN_TYPE_PPPOE:
                        String mPppoeUsr = SkParam.getParam(SkParam.SK_PARAM_NET_PPPOEUSR);
                        String mPppoePwd = SkParam.getParam(SkParam.SK_PARAM_NET_PPPOEPWD);
                        mSkyNetwork.setEthernetPppoeMode(mPppoeUsr, mPppoePwd);
                        break;
                    default:
                        break;
                }
                return null;
            }
        };
        task.execute();
    }
}
