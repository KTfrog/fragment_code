package com.skyworth.dlna_qr;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemProperties;
import android.util.Log;

import com.json.simple.JSONValue;
import com.skyworth.dlna_qr.pon.OpticalManager;
import com.skyworth.dlna_qr.utils.Utils;

import java.util.LinkedHashMap;
import java.util.Map;

public class DlnaQrService extends Service {
    public static final String TAG = "DlanQrService";
    private String mDlnaEnable = "";
    private OpticalManager mSwitchManager;

    private final static int HANDLER_SET_DLNA_OK = 105;
    private final static int HANDLER_SET_DLNA_RETRY = 110;
    private Context mContext = null;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        mContext = this;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        String action = intent.getAction();
        Log.d(TAG, "onStartCommand action:" + action);
        if ("com.skyworth.ACTION_DLNA_ENABLE".equals(action)) {
            if (mSwitchManager == null) {
                mSwitchManager = new OpticalManager(this);
            }
            String dlnaEnable = (String) intent.getStringExtra("dlnaEnable");
            sendDlnaMsgToPon(dlnaEnable);
        }

        return super.onStartCommand(intent, flags, startId);
    }

    private Handler mHandler = new Handler() {
        public void handleMessage(android.os.Message msg) {
            if (msg.what == HANDLER_SET_DLNA_OK) {
                if ("0".equals(mDlnaEnable)) {
                    Log.d(TAG, "HANDLER_SET_DLNA_OK 0");
                    SystemProperties.set("persist.sys.dlnaMode", "0");
                    Log.d(TAG, "connectByType:" + Utils.CONN_TYPE_IPOE);
                    Utils.connectByType(mContext, Utils.CONN_TYPE_IPOE);
                }
            }
            else if (msg.what == HANDLER_SET_DLNA_RETRY) {
                sendDlnaMsgToPon(mDlnaEnable);
            }
        }
    };

    private void sendDlnaMsgToPon(String enable) {
        Log.d(TAG, "send msg to pon, enable:" + enable);
        mDlnaEnable = enable;
        Map mapWlanStats = new LinkedHashMap();
        mapWlanStats.put("skponcmd","setDlnaOn.itvdoor");
        Map mapSkPonParams = new LinkedHashMap();
        mapSkPonParams.put("enable",mDlnaEnable);
        mapWlanStats.put("skponparams", mapSkPonParams);
        mSwitchManager.interactStr = JSONValue.toJSONString(mapWlanStats);
        new Thread(mSwitchRunnable).start();
    }

    //关闭打开DLNA模式的端口绑定
    Runnable mSwitchRunnable = new Runnable(){
        public void run() {
            if(mSwitchManager.sendCommandToServer()){
                Log.d(TAG, "mSwitchRunnable send ok");
                Message msg = Message.obtain();
                msg.what = HANDLER_SET_DLNA_OK;
                mHandler.sendMessageDelayed(msg, 1500);
            }
            else {
                Message msg = Message.obtain();
                msg.what = HANDLER_SET_DLNA_RETRY;
                mHandler.sendMessageDelayed(msg, 3000);
            }
        };
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

//    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}
