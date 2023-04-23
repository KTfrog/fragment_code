package com.skyworth.upgradeadservice;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import com.skyworth.sys.param.SkParam;
import com.skyworth.upgradeadservice.downloader.ADInfo;
import com.skyworth.upgradeadservice.downloader.Downloader;
import com.skyworth.upgradeadservice.downloader.Reporter;
import com.skyworth.upgradeadservice.util.Dao;
import com.skyworth.upgradeadservice.util.PreferencesService;

import android.annotation.SuppressLint;
import android.app.MboxOutputModeManager;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemProperties;
import android.util.Log;

public class UpgradeAdService extends Service{
	private static PreferencesService mService;
	private static Dao mDao;

    private static final String TAG = "UpgradeAdService";

    // 开机启动时候或者平台地址有更新置为true
    private static boolean isNeedReq = false;

    //"http://192.168.20.152/php/302.html";
    private String mADPlatformUrl = "http://10.0.6.201:8888/mad_interface/adsserver/web/adreq";
    // keyword for request url
    private final String StartKey     = "StartPIC2";
    private final String AppLaunchKey = "AppLaunchPIC2";
    private final String AuthenKey    = "AuthenPIC2";
    // localpath for each keyword
    private final String StartPICPath     = "/data/local/boot.jpg";
    private final String AppLaunchPICPat1 = "/data/local/bootanimation.jpg";
    private final String AppLaunchPICPat2 = "/data/local/bootanimation.zip";
    private final String AppLaunchPICPath = "/data/local/bootvideo.mp4";
    private final String AuthenPICPath    = "/data/local/launcher.jpg";

    private List<ADInfo> infos = new ArrayList<ADInfo>();
    
    public static final int EVENT_DOWNLOAD_COMPLETE = 100;
    public static final int EVENT_REPORT_DOWNLOAD_SUCCESS = 101;
    public static final int EVENT_REPORT_SHOW_SUCCESS = 102;

    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case EVENT_DOWNLOAD_COMPLETE:
                ADInfo info = (ADInfo)msg.obj;
                if (info != null) {
                    // copy
                    if (info.getADType().equals(AppLaunchKey)) {
                        Log.d(TAG, "aaaa, msg.arg1:" + msg.arg1);
                        if (msg.arg1 == 1) { // jpg
                            moveFile(info.getlocalTmpPath(), AppLaunchPICPat1);
                            // delete other type file
                            File file = new File(AppLaunchPICPat2);
                            if (file.exists()) {
                                file.delete();
                            }
                            file = new File(AppLaunchPICPath);
                            if (file.exists()) {
                                file.delete();
                            }
							SystemProperties.set("persist.service.bootvideo", "0");  // disable bootvideo
							//SystemProperties.set("persist.service.bootadv", "0");  // disable bootvideo
                        } else if (msg.arg1 == 2) { // zip
                            moveFile(info.getlocalTmpPath(), AppLaunchPICPat2);
                            // delete other type file
                            File file = new File(AppLaunchPICPat1);
                            if (file.exists()) {
                                file.delete();
                            }
                            file = new File(AppLaunchPICPath);
                            if (file.exists()) {
                                file.delete();
                            }
							SystemProperties.set("persist.service.bootvideo", "0");  // disable bootvideo
							//SystemProperties.set("persist.service.bootadv", "0");  // disable bootvideo
                        } else if (msg.arg1 == 3) { // mp4, ts
                            moveFile(info.getlocalTmpPath(), AppLaunchPICPath);
                            // delete other type file
                            File file = new File(AppLaunchPICPat1);
                            if (file.exists()) {
                                file.delete();
                            }
                            file = new File(AppLaunchPICPat2);
                            if (file.exists()) {
                                file.delete();
                            }
                            SystemProperties.set("persist.service.bootvideo", "1");  // enable bootvideo
							//SystemProperties.set("persist.service.bootadv", "1");  // enable bootvideo
                        }
                    } else {
                        moveFile(info.getlocalTmpPath(), info.getLocalpath());
                    }
                    mDao.updataInfos(info.getADType(), info.getMD5(), info.getShowtime());
                    // if need report
                    Log.d(TAG, "aaaa, set display report state true!");
                    mService.setReportShowState(info.getADType(), true);
                    
                    if (info.getADType().equals(StartKey)) {
                        if (!upgradeBootLogo(StartPICPath)) {
                            Log.d(TAG, "aaaa, upgradeBootLogo fail!");
                        }
                    }
                }
                break;
            case EVENT_REPORT_DOWNLOAD_SUCCESS:
                break;
            case EVENT_REPORT_SHOW_SUCCESS:
                String adType = (String)msg.obj;
                if (adType != null) {
                    mService.setReportShowState(adType, false);
                }
                break;
            default:
                break;
            }
        };
    };
    
    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void onCreate() {
        // TODO Auto-generated method stub
        super.onCreate();
		Log.i(TAG, " onCreate!");

        mService = new PreferencesService(this);
        mDao = new Dao(this);
        isNeedReq = true;
        
        initData();
        reportAdIsShow();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // TODO Auto-generated method stub
        Log.i(TAG, " onStartCommand!");
        // 读取之前保存的url
        mADPlatformUrl = mService.getADUrl();
        //mADPlatformUrl = "http://10.0.6.201:8888/mad_interface/adsserver/web/adreq";
        Log.i(TAG, " onStartCommand, mADPlatformUrl:" + mADPlatformUrl);
        if (isNeedReq && mADPlatformUrl != null && !mADPlatformUrl.equals("")) {
            for (ADInfo info : infos) {
                String completeUrl = getCompleteURL(mADPlatformUrl, info.getADType());
                info.setUrl(completeUrl);
                new Downloader(info, mHandler, mDao).download();
            }
            
            isNeedReq = false;
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void reportAdIsShow() {
        // TODO Auto-generated method stub
        for (ADInfo info : infos) {
            boolean state = mService.getReportState(info.getADType());
            Log.d(TAG, "aaaa, report state:" + state + ", ad type:" + info.getADType());
            if (state) {
                new Reporter(info.getADType(), mService.getADUrl(), mHandler).reportShowAdSuccessMsg();
            }
        }
    }
    
    public boolean upgradeBootLogo(String path) {
        MboxOutputModeManager mbox = (MboxOutputModeManager)this.getSystemService(this.MBOX_OUTPUTMODE_SERVICE);
        if (mbox != null) {
            if (mbox.updateLogo(path) != -1) {
                Log.d(TAG, "aaaa, MboxOutputModeManager update logo ok");
            }
        }
        return false;
    }
    
    // 每次下发广告平台地址都会调用此函数，但url不一定是更新了
    public static void setADUrl(String url) {
        if (mService != null && url != null && !url.equals("")) {
            String tmpUrl = mService.getADUrl();
            // 不一样才保存
            if (!url.equals(tmpUrl)) {
                mService.saveADUrl(url);
                // 触发下载
                isNeedReq = true;
            }
        }
    }
    
    private String getCompleteURL(String url, String type) {
        String userID = SkParam.getParam(SkParam.SK_PARAM_ITV_USERNAME);
        String terminaltype = SkParam.getParam(SkParam.SK_PARAM_SYS_PRODUCT_TYPE);
        return url + "?slotid=" + type + "&userid=" + userID + "&terminaltype=" + terminaltype;
    }
    
    private void initData() {
        ADInfo info;
        info= new ADInfo(StartKey, StartPICPath, "url", "md5", 8);
        infos.add(info);
        info = new ADInfo(AppLaunchKey, AppLaunchPICPath, "url", "md5", 8);
        infos.add(info);
        info = new ADInfo(AuthenKey, AuthenPICPath, "url", "md5", 8);
        infos.add(info);
        
        mDao.init(infos);
    }
    
    public void moveFile(String srcPath, String dstPath) {
        Log.d(TAG, "move file: " + srcPath + ", to: " + dstPath);
        File srcFile = new File(srcPath);
        File dstFile = new File(dstPath);
        if (!srcFile.exists())
            return;
        
        if (dstFile.exists()) {
            dstFile.delete();
        }
        srcFile.renameTo(dstFile);
        // other apk like Skiptv need to read this file
       
        Process p;
        try {
            p = Runtime.getRuntime().exec("chmod 644 " + dstFile);
            if (p.waitFor() == 0) {
                Log.i(TAG, "chmod  ok!");
            } else {
                Log.i(TAG, "chmod  fail!");
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        Log.d(TAG, "move file ok");
    }
    
    @Override
    public void onDestroy() {
        // TODO Auto-generated method stub
        Log.i(TAG, " onDestroy!");
        if (mDao != null) 
            mDao.closeDb();
        
        super.onDestroy();
    }
}
