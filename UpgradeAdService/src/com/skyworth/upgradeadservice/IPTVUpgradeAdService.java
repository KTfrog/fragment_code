package com.skyworth.upgradeadservice;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

import android.app.IntentService;
import android.app.MboxOutputModeManager;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.skyworth.sys.param.SkParam;
import android.content.Context;
import android.os.SystemProperties;

public class IPTVUpgradeAdService extends IntentService {

	private static final String TAG = "IPTVUpgradeAdService";
	
	private String mLogoPicture = "/data/local/bootPicHW.jpg";
	private String mAuthenPicture = "/data/local/launcher.jpg";
	private String mAnyPicture = "/data/local/bootanimation.zip";
	private String mFastPlay = "/data/local/bootvideo.mp4";
	private String mLogoPictureTmp = "/data/local/bootPicHW_tmp.jpg";
	private String mAuthenPictureTmp = "/data/local/launcher_tmp.jpg";
	private String mAnyPictureTmp = "/data/local/bootanimation_tmp.zip";
	private String mFastPlayTmp = "/data/local/bootvideo_tmp.mp4";

	public IPTVUpgradeAdService() {
		super("IPTVUpgradeAdService");
	}
	
	@Override
	protected void onHandleIntent(Intent intent) {
		if (intent != null) {
			Bundle bundle = intent.getExtras();
			if (bundle != null) {
				String type = bundle.getString("type");
				String url = bundle.getString("url");
				Log.d(TAG, "onHandleIntent get type => " + type + " get url => " + url);
				Thread mThread = new PictureUpgrade(type, url);
				mThread.start();
			} else {
				Log.d(TAG, "onHandleIntent get bundle is null!!!");
			}
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return super.onStartCommand(intent, flags, startId);
	}

	class PictureUpgrade extends Thread {

		private String mType = "";
		private String mUrl = "";

		public PictureUpgrade(String type, String url) {
			this.mType = type;
			this.mUrl = url;
		}

		public void run() {
			try {
				BufferedInputStream in = new BufferedInputStream(new URL(mUrl).openStream());
				String mFileName = null;
				if ("bootPic".equalsIgnoreCase(mType)) {
					mFileName = mLogoPictureTmp;
				} else if ("fastPlay".equalsIgnoreCase(mType)) {
					mFileName = mFastPlayTmp;
					SystemProperties.set("persist.service.bootvideo", "1");  // enable bootvideo
				} else if ("bootanimation".equalsIgnoreCase(mType)) {
					mFileName = mAnyPictureTmp;
					SystemProperties.set("persist.service.bootvideo", "0");  // disable bootvideo
				} else if ("authPic".equalsIgnoreCase(mType)) {
					mFileName = mAuthenPictureTmp;
				}
				Log.d(TAG, "run() ::: mFileName => " + mFileName);
				File downloadFile = new File(mFileName);
				BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(downloadFile));
	            byte[] buf = new byte[2048];
	            int lengths = in.read(buf);
				Log.i(TAG, "lengths ==> " + lengths);
	            while (lengths != -1) {
	                out.write(buf, 0, lengths);  
	                lengths = in.read(buf);
	            }  
	            in.close();  
	            out.close();
				Log.d(TAG, "run() ::: download success!!!");
				delPreFileAndRename(mType, downloadFile);			
			} catch (MalformedURLException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/* private String getFilename(String url) {
		String ret = "";
		try {
			Locale defloc = Locale.getDefault();
			int lengths = new String("FILENAME=").length();
			String filename;
			if(url.contains("&Action"))
			{
				filename = url.substring(url.indexOf("FILENAME=")+lengths, url.indexOf("&Action")).toLowerCase(defloc);
			}
			else
			{
				filename = url.substring(url.indexOf("FILENAME=")+lengths, url.indexOf("&MD5")).toLowerCase(defloc);
			}
			String type = (url.substring(url.indexOf("PicType=")+8, url.indexOf("&PicTypeID="))).toLowerCase(defloc);
			if(type.equalsIgnoreCase("bootpic")) {
				ret = mLogoPicture + "_" + filename;
			} else if(type.equalsIgnoreCase("authen")) {
				ret = mAuthenPicture + "_" + filename;
			}else if(type.equalsIgnoreCase("any")){
				ret= mAnyPicture + "_"  + filename;
			}else if(type.equalsIgnoreCase("fastplay"))
			{
				ret= mFastPlay + "_"  + filename;
			}
		} catch (Exception e) {  
            e.printStackTrace();  
        }
		return ret;
	}

	private String getExt(String filename) {
		String ret = "";
		try {
			Locale defloc = Locale.getDefault();
			ret = (filename.substring(filename.indexOf("."), filename.length())).toLowerCase(defloc);
		} catch (Exception e) {  
            e.printStackTrace();  
        }
		return ret;
	} */
	
	private void delPreFileAndRename(String type, File file) {
		try {
			if(type.equals("bootPic")) {
				File bootPic = new File(mLogoPicture);// /data/local/bootPicHW.jpg
				if(bootPic.exists()) {
					chmod(mLogoPicture);
					bootPic.delete();				
				} 
				file.renameTo(bootPic);
				chmod(mLogoPicture);
				upgradeBootLogo(mLogoPicture);
				Log.i(TAG, "==> BOOTLOGO update success");
			} else if(type.equals("fastPlay")) {
				File any = new File(mAnyPicture);// /data/local/bootanimation.zip
				File video = new File(mFastPlay);// /data/local/bootvideo.mp4
				if(any.exists()) {
					chmod(mAnyPicture);
					any.delete();
				} 
				if(video.exists()) {
					chmod(mFastPlay);
					video.delete();
				}
				file.renameTo(video);
				chmod(mFastPlay);
				SystemProperties.set("persist.sys.bootanim.enable", "false");
				Log.i(TAG, "==> BOOTVIDEO update success");
			} else if(type.equals("bootanimation")) {
				File any2 = new File(mAnyPicture);// /data/local/bootanimation.zip
				File video2 = new File(mFastPlay);// /data/local/bootvideo.mp4
				if(any2.exists()) {
					chmod(mAnyPicture);
					any2.delete();
				} 
				if(video2.exists()) {
					chmod(mFastPlay);
					video2.delete();
				}
				file.renameTo(any2);
				chmod(mAnyPicture);
				SystemProperties.set("persist.sys.bootanim.enable", "true");
				Log.i(TAG, "==> BOOTANIMATION update success");
			} else if(type.equals("authPic")) {
				File authPic = new File(mAuthenPicture);// /data/local/launcher.jpg
				if(authPic.exists()) {
					chmod(mAuthenPicture);
					authPic.delete();				
				} 
				file.renameTo(authPic);
				chmod(mAuthenPicture);
				Log.i(TAG, "==> AUTHENBG update success");
			} else {
				Log.i(TAG, "!!! unknown type !!!");
			}
		} catch (Exception e) {  
            e.printStackTrace();  
        }
	}

	private void chmod(String filename) {
		String command;
		Runtime runtime;
		try {
			command = "chmod 644 " + filename;
			runtime = Runtime.getRuntime();
			runtime.exec(command);
			Log.w(TAG, "chmod success!!!");
		} catch(Exception e) {
			Log.w(TAG, "chmod failed!!!");
			e.printStackTrace();
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
}
