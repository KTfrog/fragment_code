package com.skyworth.upgradeadservice.downloader;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.skyworth.sys.param.SkParam;
import com.skyworth.upgradeadservice.UpgradeAdService;


public class Reporter {
    public static final String TAG = "UpgradeAdService-Reporter";
    private String mAdType;
    private String mServerUrl;
    private Handler mHandler;

    public Reporter(String adType, String url, Handler handler) {
        mAdType = adType;
        mServerUrl = url;
        mHandler = handler;
    }
    
    public void reportShowAdSuccessMsg() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                // TODO Auto-generated method stub
                reportDisplay();
            }
        }).start();
    }
    
    public void reportDownloadSuccessMsg() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                // TODO Auto-generated method stub
                reportDownloadMsg();
            }
        }).start();
    }
    
    private void reportDisplay() {
        URL serverUrl;
        try {
            serverUrl = new URL(mServerUrl);
            String host = serverUrl.getHost();
            int port = serverUrl.getPort();
            // http://{AD_IP}:{AD_PORT}/mad_interface/rest/startuptv/showsuccess/submit  -- show
            // mad_interface/rest/startuptv/downloadsuccess/submit -- download
            String submitUrl = "http://" + host + ":" + port + "/mad_interface/rest/startuptv/showsuccess/submit";
            String userid = SkParam.getParam(SkParam.SK_PARAM_ITV_USERNAME);
            String terminaltype = SkParam.getParam(SkParam.SK_PARAM_SYS_PRODUCT_TYPE);
            String terminalversion = SkParam.getParam(SkParam.SK_PARAM_SYS_SOFTWARE_VERSION);
            String slotid = mAdType;
            String downloadurl = mServerUrl;
            submitUrl += "?userid=" + userid + "&terminaltype=" + terminaltype + "&terminalversion=" + terminalversion + "&slotid=" + slotid;
            Log.d(TAG, "aaaa, report submitUrl: " + submitUrl);
            serverUrl = new URL(submitUrl);
            HttpURLConnection conn = (HttpURLConnection)serverUrl.openConnection();
            conn.setRequestMethod("POST");
            // api自动处理302重定向
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(5000);
            conn.setUseCaches(false);
            PrintWriter printWriter = new PrintWriter(conn.getOutputStream());
            String post = "{\"downloadurl\" : \"" + downloadurl + "\"}";
            printWriter.write(post);
            printWriter.flush();
            
            InputStream is = conn.getInputStream();
            Log.d(TAG, "aaaa, ResponseCode:"+conn.getResponseCode());
            if (conn.getResponseCode() == 200) {
                Log.d(TAG, "aaaa, report display success");
                Message msg = Message.obtain();
                msg.what = UpgradeAdService.EVENT_REPORT_SHOW_SUCCESS;
                msg.obj = mAdType;
                mHandler.dispatchMessage(msg);
            }
        } catch (MalformedURLException e) {
            // TODO Auto-generated catch block
            Log.d(TAG, "aaaa, report Download failed");
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            Log.d(TAG, "aaaa, report Download failed");
            e.printStackTrace();
        }
        
    }
    
    
    private void reportDownloadMsg() {
        URL serverUrl;
        try {
            serverUrl = new URL(mServerUrl);
            String host = serverUrl.getHost();
            int port = serverUrl.getPort();
            // http://{AD_IP}:{AD_PORT}/mad_interface/rest/startuptv/showsuccess/submit  -- show
            // mad_interface/rest/startuptv/downloadsuccess/submit -- download
            String submitUrl = "http://" + host + ":" + port + "/mad_interface/rest/startuptv/downloadsuccess/submit";
            String userid = SkParam.getParam(SkParam.SK_PARAM_ITV_USERNAME);
            String terminaltype = SkParam.getParam(SkParam.SK_PARAM_SYS_PRODUCT_TYPE);
            String terminalversion = SkParam.getParam(SkParam.SK_PARAM_SYS_SOFTWARE_VERSION);
            String slotid = mAdType;
            String downloadurl = mServerUrl;
            submitUrl += "?userid=" + userid + "&terminaltype=" + terminaltype + "&terminalversion=" + terminalversion + "&slotid=" + slotid;
            Log.d(TAG, "aaaa, report submitUrl: " + submitUrl);
            serverUrl = new URL(submitUrl);
            HttpURLConnection conn = (HttpURLConnection)serverUrl.openConnection();
            conn.setRequestMethod("POST");
            // api自动处理302重定向
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(5000);
            conn.setUseCaches(false);
            PrintWriter printWriter = new PrintWriter(conn.getOutputStream());
            String post = "{\"downloadurl\" : \"" + downloadurl + "\"}";
            printWriter.write(post);
            printWriter.flush();
            InputStream is = conn.getInputStream();
            Log.d(TAG, "aaaa, ResponseCode:"+conn.getResponseCode());
            if (conn.getResponseCode() == 200) {
                Log.d(TAG, "aaaa, report Download success");
            }
        } catch (MalformedURLException e) {
            // TODO Auto-generated catch block
            Log.d(TAG, "aaaa, report Download failed");
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            Log.d(TAG, "aaaa, report Download failed");
            e.printStackTrace();
        }
        
    }
}
