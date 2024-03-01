package com.skyworth.dlna_qr;


import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.text.Spannable;
import android.text.SpannableString;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.json.simple.JSONArray;
import com.json.simple.JSONObject;
import com.json.simple.JSONValue;
import com.json.simple.parser.JSONParser;
import com.skyworth.dlna_qr.pon.OpticalManager;
import com.skyworth.dlna_qr.pon.PonEntity;
import com.skyworth.dlna_qr.utils.RandomPwd;
import com.skyworth.dlna_qr.utils.Utils;
import com.skyworth.dlna_qr.view.CenterImageSpan;
import com.skyworth.dlna_qr.view.QRCView;
import com.skyworth.sys.param.SkParam;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int BASE_INDEX = 0;
    // ssid4 == index 3
    private static final int SSID_INDEX = 3;
    private String TAG = "MainActivity_QR";
    private QRCView mQR;

    private MainActivity mContext;
    private OpticalManager mGetManager;
    private OpticalManager mSetManager;
    private OpticalManager mSwitchManager;
    private final static int HANDLER_GET_WIRELESS_INFO = 101;
    private final static int HANDLER_SET_WIRELESS_INFO = 102;
    private final static int HANDLER_PON_DLNA_ON = 103;
    private final static int HANDLER_PON_DLNA_OFF = 104;
    private final static int HANDLER_SET_DLNA_OK = 105;
    private static final int CONN_REVERT_SUCCESS = 109;

    private boolean threadFlag = false;
    private boolean getFlag = false;
    private boolean setFlag = false;
    private PonEntity mPonEntity;
    private static int mSsidCount = 8;
    private String mSSIDName = "";
    private String mSSIDPwd = "";
    private String mDlnaEnable = ""; // "0" or "1"
    private String mOriginalNetType = "ipoe";
    private boolean mIsNetRevert = false;
    private DlnaqrReceiver mItvReceiver = null;

    private static MainActivity mInstance = null;
    private String mSsidEnable = "";

    private static final int STATE_GW_SET_SSID_ENABLE = 1;
    private static final int STATE_GW_SET_SSID_ENABLE_OK = STATE_GW_SET_SSID_ENABLE << 1;
    private static final int STATE_GW_SET_DLNA_MODE = STATE_GW_SET_SSID_ENABLE << 2;
    private static final int STATE_GW_SET_DLNA_MODE_OK = STATE_GW_SET_SSID_ENABLE << 3;
    private static final int STATE_STB_SET_DHCP = STATE_GW_SET_SSID_ENABLE << 4;
    private static final int STATE_STB_SET_DHCP_OK = STATE_GW_SET_SSID_ENABLE << 5;

    private static final int STATE_GW_SET_SSID_DISABLE = STATE_STB_SET_DHCP_OK + 1;
    private static final int STATE_GW_SET_SSID_DISABLE_OK = STATE_GW_SET_SSID_DISABLE << 1;
    private static final int STATE_GW_SET_ITV_MODE = STATE_GW_SET_SSID_DISABLE << 2;
    private static final int STATE_GW_SET_ITV_MODE_OK = STATE_GW_SET_SSID_DISABLE << 3;
    private static final int STATE_STB_SET_IPOE = STATE_GW_SET_SSID_DISABLE << 4;
    private static final int STATE_STB_SET_IPOE_OK = STATE_GW_SET_SSID_DISABLE << 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);

        mInstance = this;
        init();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        mInstance = null;
        threadFlag = false;
        getFlag  = false;
        setFlag  = false;
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
            mHandler = null;
        }
        if (mItvReceiver != null) {
            unregisterReceiver(mItvReceiver);
            mItvReceiver = null;
        }

        if (!mIsNetRevert) {
            sendDlnaMsgToPon("0");
            mIsNetRevert = true;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 只响应返回键，首页键在framework里要处理一下
        Log.d(TAG, "event:" + event.getAction() + ", keyCode:" + keyCode + ", mDlnaEnable:" + mDlnaEnable + ", mSsidEnable:" + mSsidEnable);
        if (keyCode == 4) {
            if ("1".equals(mSsidEnable)) {
                // send msg to pon to off dlna
                Toast.makeText(mContext, getString(R.string.keyback_info), Toast.LENGTH_SHORT).show();
                doSetWirelessInfo("0");
            }
            return false;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void init() {
        createReceiver();

        mOriginalNetType = SkParam.getParam(SkParam.SK_PARAM_NET_CONNECTMODE);
        mContext = this;
        mQR = (QRCView) findViewById(R.id.qr);
        mGetManager = new OpticalManager(mContext);
        mSetManager = new OpticalManager(mContext);
        mSwitchManager = new OpticalManager(mContext);
        mPonEntity = new PonEntity();

        Log.d(TAG, "tvShowTips");
        TextView tvShowTips = (TextView)findViewById(R.id.tv_show_tips_s);
        Bitmap drawable = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_tv_tips);

        CenterImageSpan imgSpan = new CenterImageSpan(mContext, Utils.imageScale(drawable, Utils.dip2px(mContext, 24), Utils.dip2px(mContext, 24)));
        SpannableString spannableString = new SpannableString(getString(R.string.text_details_tips));
        try {
            spannableString.setSpan(imgSpan, 21, 22, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } catch (Exception e) {

        }
        tvShowTips.setText(spannableString);

        LinearLayout llaQrcShow = (LinearLayout)findViewById(R.id.lla_qrc_show);
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

        try {
            int width = wm.getDefaultDisplay().getWidth();
            int height = (int) (width * 1) / 3;
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) llaQrcShow.getLayoutParams();
            lp.height = height;
        } catch (Exception e) {

        }
        Map mapWlanStats = new LinkedHashMap();
        mapWlanStats.put("skponcmd","getWlanstatus.itvdoor");
        mapWlanStats.put("skponparams", "");
        mGetManager.interactStr = JSONValue.toJSONString(mapWlanStats);

        threadFlag = true;
        getFlag  = true;
        getThread.start();
    }

    private Handler mHandler = new Handler(){

        public void handleMessage(android.os.Message msg) {
            if (msg.what == HANDLER_GET_WIRELESS_INFO) {
                // 修改ssid4
                doUpdateWirelessInfo(mGetManager.reasultStr);
                if(!setThread.isAlive() ){
                    setThread.start();
                }
                doSetWirelessInfo("1");
            }
            else if(msg.what == HANDLER_SET_WIRELESS_INFO){
                if ("1".equals(mSsidEnable)) {
                    // show QR Code
                    Log.d(TAG, "set ssid4 ok, to show QR Code");
                    showQRCode();
                    sendDlnaMsgToPon("1");
                }
                else {
                    sendDlnaMsgToPon("0");
                }
            }
            else if (msg.what == HANDLER_SET_DLNA_OK) {
                if ("1".equals(mDlnaEnable)) {
                    dhcpNet();
                }
                else {
                    revertNet();

                    // 如果revertNet失败，则强行reboot
                    mHandler.postDelayed(mRebootRunnable, 20 * 1000);
                }
            }
            else if (msg.what == CONN_REVERT_SUCCESS) {
                mHandler.removeCallbacks(mRebootRunnable);
                Log.d(TAG, "reset network ok!, finish activity");
                finish();
            }
        };
    };

    Runnable mRebootRunnable = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "FORCE_REBOOT");
            PowerManager mPowerManager = (PowerManager)mContext.getSystemService(Context.POWER_SERVICE);
            mPowerManager.reboot("");
        }
    };

    private void createReceiver() {
        mItvReceiver = new DlnaqrReceiver();
        IntentFilter mIntentFilter = new IntentFilter();
        mIntentFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        mIntentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        //mIntentFilter.addAction("android.net.ethernet.IPV4_STATE_CHANGE");
        registerReceiver(mItvReceiver, mIntentFilter);
    }

    private void dhcpNet() {
        Log.d(TAG, "change to dhcpNet");
        SystemProperties.set("persist.sys.dlnaMode", "1");
        SystemProperties.set("persist.sys.dlnaDefaultNet", mOriginalNetType);
        connectByType(Utils.CONN_TYPE_DHCP);
    }

    private void revertNet() {
        Log.d(TAG, "revert net to " + mOriginalNetType);
        if (mOriginalNetType.equals("pppoe")) {
            connectByType(Utils.CONN_TYPE_PPPOE);
        }
        else {
            connectByType(Utils.CONN_TYPE_IPOE);
        }
        mIsNetRevert = true;
        SystemProperties.set("persist.sys.dlnaMode", "0");
        Toast.makeText(mContext, getString(R.string.itv_preparing), Toast.LENGTH_SHORT).show();
    }

    public void sendDlnaMsgToPon(String enable) {
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

    private void connectByType(final int type) {
        Log.d(TAG, "connectByType:" + type);
        Utils.connectByType(mContext, type);
    }

    private void showQRCode() {
        String wifiname = "CTC-SSID4";
        String wifipwd = "!@#$%12345";
        if (!"".equals(mSSIDName) || !"".equals(mSSIDPwd)) {
            wifiname = mSSIDName;
            wifipwd = mSSIDPwd;
        }
        String encrypt = "WPA";
        String msg = "WIFI:T:" + encrypt + ";S:" + wifiname + ";P:" + wifipwd + ";;";
        Log.e(TAG, "msg is:" + msg);
        mQR = (QRCView) findViewById(R.id.qr);
        mQR.createImageView(msg, 400, 400, null);

        TextView tv = (TextView) findViewById(R.id.ssidinfo);
        TextView ssidpwd = (TextView) findViewById(R.id.ssidpwd);
        tv.setText(mSSIDName);
        ssidpwd.setText(mSSIDPwd);
        //tv.setText("wifi:" + mSSIDName + "\t pwd:" + mSSIDPwd);
    }

    private void doUpdateWirelessInfo(String result) {
        try{
            String skPonResult   = null;
            String skPonParams = null;
            String bssidnum = null;
            String apon = null;


            JSONParser parserSkreasult = new JSONParser();
            JSONObject objSkreasult = (JSONObject)(parserSkreasult.parse(result));
            skPonResult = objSkreasult.get("skponresult").toString();
            if((skPonResult != null) && skPonResult.equalsIgnoreCase("ok")  ){
                JSONObject objPonParams = (JSONObject)objSkreasult.get("skponparams");
                //mPonEntity.pon_wireless_multi_flag = multiflag.equalsIgnoreCase("yes") ? true : false;
                bssidnum = objPonParams.get("ssidnums").toString();
                mPonEntity.pon_wireless_ssid_number = Integer.parseInt(bssidnum);
                apon = objPonParams.get("wlanenable").toString();

                JSONArray arrayWlanInfo=(JSONArray)objPonParams.get("wlanInfo");
                //容错处理，最大不能超过8条ssid链接
                if(mPonEntity.pon_wireless_ssid_number > mSsidCount){
                    mPonEntity.pon_wireless_ssid_number = mSsidCount;
                }
                if("0".equalsIgnoreCase(apon)){
                    mPonEntity.pon_wireless_ssid_number = 0;
                }
                int i = 0;
                int j = 0;
                for(i=0;i<mSsidCount;i++){
                    mPonEntity.pon_wireless_ssid_index_enable[i] = false;
                }
                JSONObject []objWlanInfo =new JSONObject[mPonEntity.pon_wireless_ssid_number+1];
                for(; j<mPonEntity.pon_wireless_ssid_number; j++){
                    objWlanInfo[j]= (JSONObject)arrayWlanInfo.get(j);
                    i = Integer.parseInt( objWlanInfo[j].get("ssididx").toString())-1;
                    Log.i(TAG,"ssidenable is:" + objWlanInfo[j].get("ssidenable").toString());
                    if(objWlanInfo[j].get("ssidenable").toString().equals("0")){
                        mPonEntity.pon_wireless_ssid_index_enable[i] = false;
                    }else {
                        mPonEntity.pon_wireless_ssid_index_enable[i] = true;
                    }
                    //updateUI(i, true);
                    mPonEntity.pon_wireless_ssid[i] = objWlanInfo[j].get("ssidname").toString();
                    mPonEntity.pon_wireless_auth_mode[i] = objWlanInfo[j].get("authmode").toString();
                    mPonEntity.pon_wireless_wps_conf_mode[i] =  objWlanInfo[j].get("wpsenable").toString();
                    if(mPonEntity.pon_wireless_auth_mode[i].equalsIgnoreCase("OPEN")){
                        //do noting
                    }
                    if(mPonEntity.pon_wireless_auth_mode[i].startsWith("WEP".substring(0,2))){
                        mPonEntity.pon_wireless_wep_type[i] =  objWlanInfo[j].get("encrypttype").toString();
                        mPonEntity.pon_wireless_wep_pwd[i]  = objWlanInfo[j].get("password").toString();
                    }
                    if(mPonEntity.pon_wireless_auth_mode[i].startsWith("WPA".substring(0,2))){
                        mPonEntity.pon_wireless_wpa_type[i] =  objWlanInfo[j].get("encrypttype").toString();
                        mPonEntity.pon_wireless_wpa_pwd[i]  = objWlanInfo[j].get("password").toString();
                    }

                }
                if("1".equalsIgnoreCase(apon)){
                    i = 0;
                    for(j=0; j<mPonEntity.pon_wireless_ssid_number; j++){
                        mPonEntity.pon_wireless_ssid_index_position[j] = i;
                        i++;
                    }
                }
            } else {
                return;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

    }


    /********
     http://192.168.1.1/setPONwlan.itvdoor?ssididx=1&wlssid=ChinaNet-ssid2&authmode=OPEN&wpsconfmode=1&wlwep=Both&key1str=33345&encryptype=TKIP&wlwpapsk=12345678901|apon=1
     wlssid:       	SSID
     authmode:     	加密类型
     wpsconfmode:   	wps使能
     wlwep: 			wep加密类型
     authkeylen: 	加密长度
     key1str: 		wep加密密码1
     wlwpapsk: 		wpa加密密码
     encryptype: 	wpa加密类型
     apon:			wireless使能
     ********/
    private void doSetWirelessInfo(String ssidEnable){
        mSsidEnable = ssidEnable;
        getFlag = false;

        PonEntity ponEntity = mPonEntity;
        int i = SSID_INDEX;
        String wlssid = ponEntity.pon_wireless_ssid[BASE_INDEX];
        // {{ for test
        String test_index = SystemProperties.get("persist.sys.dlna_index", "");
        if (!"".equals(test_index)) {
            i = Integer.parseInt(test_index);
            Log.d(TAG, "test_index:" + i);
            wlssid = "1a-JS-CTC-";
        }
        // }}
        int ssididx = i+1;
        // force open ssid4
        if ("1".equals(mSsidEnable)) {
            wlssid = buildSsid(wlssid);
            mSSIDName = wlssid;
            mSSIDPwd = buildPwd();
        }

        String authmode = ponEntity.pon_wireless_auth_mode[i];
        String wpsconfmode = ponEntity.pon_wireless_wps_conf_mode[i];
        String wlwpapsk = ponEntity.pon_wireless_wpa_pwd[i];
        String wlwep = ponEntity.pon_wireless_wep_type[i];
        String key1str = ponEntity.pon_wireless_wep_pwd[i];
        String encryptype = ponEntity.pon_wireless_wpa_type[i];

        Map mapWlanInfo = new LinkedHashMap();
        mapWlanInfo.put("ssididx", String.valueOf (ssididx));
        mapWlanInfo.put("ssidname", mSSIDName);
        mapWlanInfo.put("authmode",authmode);
        mapWlanInfo.put("ssidenable", mSsidEnable);

        if(authmode.startsWith("WEP".substring(0,2))){
            mapWlanInfo.put("encrypttype",ponEntity.pon_wireless_wep_type[i]);
            mapWlanInfo.put("password", mSSIDPwd);
        }
        if(authmode.startsWith("WPA".substring(0,2))){
            mapWlanInfo.put("encrypttype",ponEntity.pon_wireless_wpa_type[i]);
            mapWlanInfo.put("password", mSSIDPwd);
        }
        if(authmode.startsWith("OPEN".substring(0,2))){
            mapWlanInfo.put("encrypttype","");
            mapWlanInfo.put("password","");
            mapWlanInfo.put("wpsenable","0");
        }else{
            mapWlanInfo.put("wpsenable",wpsconfmode);
        }
        mapWlanInfo.put("channel","0");
        mapWlanInfo.put("visible","1");
        List listWlanInfo = new LinkedList();
        listWlanInfo.add(mapWlanInfo);
        Map mapSkPonParams = new LinkedHashMap();
        mapSkPonParams.put("wlanenable","1");
        mapSkPonParams.put("wlanInfo",listWlanInfo);
        Map mapSetPONwlan = new LinkedHashMap();
        mapSetPONwlan.put("skponcmd","setPONwlan.itvdoor");
        mapSetPONwlan.put("skponparams",mapSkPonParams);
        mSetManager.interactStr = JSONValue.toJSONString(mapSetPONwlan).replace("\\","");
        setFlag = true;
    }

    private String buildPwd() {
        String pwd = RandomPwd.getRandomPwd(8);
        Log.d(TAG, "random pwd:" + pwd);
        return pwd;
    }

    private String buildSsid(String ssid1) {
        Log.d(TAG, "ssid1:" + ssid1);
        StringBuilder sb = new StringBuilder();
        sb.append(ssid1);
        for (int i = 0; i < 4; i++) {
            sb.append(RandomPwd.getNumChar());
        }
        Log.d(TAG, "ssid4:" + sb.toString());
        return sb.toString();
    }


    //获取参数线程
    Thread getThread = new Thread(){
        public void run() {
            try {
                while (threadFlag) {
                    if(getFlag){
                        if(mGetManager.sendCommandToServer()){
                            Message msg = Message.obtain();
                            msg.what = HANDLER_GET_WIRELESS_INFO;
                            mHandler.sendMessage(msg);
                        }
                    }
                    Thread.sleep(4000);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        };
    };

    //设置参数线程
    Thread setThread = new Thread(){
        public void run() {
            try {
                int i = 0;
                while (threadFlag) {
                    if(setFlag){
                        if(mSetManager.sendCommandToServer()){
                            Message msg = Message.obtain();
                            msg.what = HANDLER_SET_WIRELESS_INFO;
                            mHandler.sendMessage(msg);
                            setFlag = false;
                        }else{
                            i++;
                        }
                        if(i > 3){
                            i = 0;
                            setFlag = false;
                        }
                        Thread.sleep(500);
                    }
                    Thread.sleep(500);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        };
    };

    //关闭打开DLNA模式的端口绑定
    Runnable mSwitchRunnable = new Runnable(){
        public void run() {
            try {
                if(mSwitchManager.sendCommandToServer()){
                    Message msg = Message.obtain();
                    msg.what = HANDLER_SET_DLNA_OK;
                    mHandler.sendMessageDelayed(msg, 1500);
                }
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        };
    };

    private class DlnaqrReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            Log.d("itvtest", "+++++++++++++ BroadcastReceiver!!!!!, action:" + action);
            if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                if (mIsNetRevert) {
                    handleConnectivityAction(intent);
                }
            }
        }
    }

    public boolean isNetworkConnected(){
        ConnectivityManager manager = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);

        if(manager == null){
            return false;
        }

        NetworkInfo info = manager.getActiveNetworkInfo();

        if(info != null){
            return info.isConnected();
        }else{
            NetworkInfo[] infos = manager.getAllNetworkInfo();

            if(infos == null){
                return false;
            }

            for (int i = 0; i < infos.length; i++) {
                Log.d(TAG, "all network info, i:" + i + ", type:" + infos[i].getType() + ", connected:" + infos[i].isConnected());
                if (infos[i].isConnected()) {
                    return true;
                }
            }
        }

        return false;
    }

    private void handleConnectivityAction(Intent intent) {
        NetworkInfo info = null;
        try{
            info = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
        }catch(Exception e){
            e.printStackTrace();
        }
        /*
        //wifi连接过程较慢会弹出错误提示框 这里延迟5s钟检测网络
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
         */

        boolean isConnected;
        if(info != null){
            isConnected = info.isConnected();
            Log.d(TAG, "+++++++++++++ info.isConnected():" + isConnected);
        }else{
            isConnected = isNetworkConnected();
        }

        if(!isConnected){
            Log.d(TAG, "+++++++++++++ network disconnect!!!!!");
        }else{
            Log.d(TAG, "++++ check is top activity!!! CONN_REVERT_SUCCESS");
            Message msg = Message.obtain();
            msg.what = CONN_REVERT_SUCCESS;
            mHandler.sendMessage(msg);
        }
    }

    public static MainActivity getInstance() {
        return mInstance;
    }

}
