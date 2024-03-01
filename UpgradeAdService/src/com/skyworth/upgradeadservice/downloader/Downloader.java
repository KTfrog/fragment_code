package com.skyworth.upgradeadservice.downloader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.skyworth.sys.param.SkParam;
import com.skyworth.upgradeadservice.UpgradeAdService;
import com.skyworth.upgradeadservice.util.Dao;
import com.skyworth.upgradeadservice.util.URLParser;

public class Downloader {
    public static final String TAG = "Downloader";
    
    // 下载地址
    private String mUrl;
    private String mLocalpath;
    private String mType;
    private Handler mHandler;
    private Dao mDao;
    private ADInfo mADInfo;
    
    private String mMD5 = "";  
    private int mShowTime = 0;
    private int retryTimes = 0;
    private Timer timer = new Timer();
    
    private final int BUFFER_SIZE = 8096;
    public static final int EVENT_RELOAD = 200;
    
    public Downloader(ADInfo info, Handler handler, Dao dao) {
        mADInfo = info;
        mUrl = info.getUrl();
        // use tmp path, move to localpath when down complete
        mLocalpath = info.getlocalTmpPath();
        mType = info.getADType();
        mHandler = handler;
        mDao = dao;
    }
    

    class MyTask extends TimerTask {
        @Override
        public void run() {
            // TODO Auto-generated method stub
            Log.i(TAG, " MyTask run!");
            
            if (downloadAD(mUrl, mLocalpath)) {
                new Reporter(mType, mUrl, mHandler).reportDownloadSuccessMsg();
            }
            
            // 最多尝试连接3次
            if (++retryTimes == 3 && timer != null) {
                timer.cancel();
                retryTimes = 0;
                return;
            }
        }
    }
    
    @SuppressLint("HandlerLeak")
    private Handler internalHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case EVENT_RELOAD:
                download();
                break;
            default:
                break;
            }
        };
    };
    

    
    /*
     * @return , true means download new ADfile that will have new md5
     */
    private boolean downloadAD(String url, String localpath) {
        try {
            Log.d(TAG, "aaaa, downloadAD url:"+url);
            URL serverUrl = new URL(url);
            HttpURLConnection conn = (HttpURLConnection)serverUrl.openConnection();
            conn.setRequestMethod("GET");
            // 忽略中间的302跳转，直接到最后的200
            conn.setInstanceFollowRedirects(true);
            // 规范里5秒超时
            conn.setConnectTimeout(5000);
            conn.setUseCaches(false);
            
            InputStream is = conn.getInputStream();
            
            Log.d(TAG, "aaaa, ResponseCode:"+conn.getResponseCode());
            
            if (conn.getResponseCode() == 200) {
                if (timer != null) {
                    timer.cancel();
                }
                
                Log.d(TAG, "aaaa, type:" + conn.getContentType() + ", len:" + conn.getContentLength());
                if (conn.getContentLength() == 0) {
                    return false;
                }
                
                if (conn.getContentType().contains("text")) { // redirect url
                    InputStreamReader isr = new InputStreamReader(is);  
                    BufferedReader bufferReader = new BufferedReader(isr);
                    String resultData  = bufferReader.readLine();
                    Log.d(TAG, "aaaa, resultData:" + resultData);
                    if (resultData != null && resultData.startsWith("http://")) {
                        mUrl = resultData;
                        mMD5 = URLParser.getParamValue(resultData, "MD5CheckSum");
                        String showTime = URLParser.getParamValue(resultData, "ShowTime");
                        mShowTime = Integer.parseInt(showTime);
                        Log.d(TAG, "aaaa, md5:" + mMD5 + ", showTime:" + mShowTime);
                        mADInfo.setMD5(mMD5);
                        mADInfo.setShowtime(mShowTime);
                        boolean isAlreadyDownload = mDao.isExistInfor(mADInfo.getADType(), mADInfo.getMD5());
                        if (isAlreadyDownload) {
                            // do nothing
                            Log.d(TAG, "aaaa, isAlreadyDownload, download thread finish");
                        } else {
                            Message msg = Message.obtain();
                            msg.what = EVENT_RELOAD;
                            internalHandler.sendMessage(msg);
                        }
                    }
                    return false;
                } else {
                    return writeResToDisk(is, localpath, conn.getURL().toString());
                }
            } else {
                Log.d(TAG, "aaaa, ResponseCode wrong, do not save to local path!");
            }
        } catch (MalformedURLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return false;
    }

    @SuppressLint("WorldReadableFiles")
    private boolean writeResToDisk(InputStream fis, String dest_file, String url)  throws Exception
    {
        try{
            Log.i(TAG, "writeResToDisk start, dest_file:"+dest_file + ", url:" + url);
            int type = 0;
            if (url.contains(".jpg")) {
                type = 1;
            } else if (url.contains(".zip")) {
                type = 2;
            } else {
                type = 3;
            }
            
            File file = new File(dest_file);
            if (file.exists())
                file.delete();
            
            byte buffer[] = new byte[BUFFER_SIZE];
            int size = -1;
            
            FileOutputStream fos = new FileOutputStream(file);
            while ((size = fis.read(buffer)) != -1) {
                //Log.i(TAG, "fis.read(buffer), size:" + size);
                fos.write(buffer, 0, size); 
            }
            
            fis.close();
            fos.close();
            
            Log.i(TAG, "writeResToDisk end!");

            Message msg = Message.obtain();
            msg.what = UpgradeAdService.EVENT_DOWNLOAD_COMPLETE;
            msg.obj = mADInfo;
            msg.arg1 = type;
            mHandler.sendMessage(msg);
            
            return true;
        }catch (Exception e){
            Log.i(TAG, "writeResToDisk error,file=" + dest_file);
            e.printStackTrace();
            // TODO: handle exception
        }
        return false;
    }
    
    public void download() {
        if (timer != null) {
            timer.cancel();
        }
        retryTimes = 0;
        timer = new Timer();
        timer.schedule(new MyTask(), 2*1000, 60 * 60 *1000 /* one hour */);
    }
}
