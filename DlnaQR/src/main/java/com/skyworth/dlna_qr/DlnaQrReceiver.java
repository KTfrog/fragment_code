package com.skyworth.dlna_qr;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class DlnaQrReceiver extends BroadcastReceiver {
    public static final String TAG = "DlnaQrReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.i(TAG,"action:" + action);

        if (action.equals(Intent.ACTION_SCREEN_OFF) || action.equals(Intent.ACTION_SHUTDOWN) ) {

        }
        else if (action.equals("android.net.conn.CONNECTIVITY_CHANGE")) {
            // check network state
        }
        else if (action.equals("com.skyworth.ACTION_DLNA_ENABLE")) {
            intent.setClass(context, DlnaQrService.class);
            context.startService(intent);
        }
    }

}
