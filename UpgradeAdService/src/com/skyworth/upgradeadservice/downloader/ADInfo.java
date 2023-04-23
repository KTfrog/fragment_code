package com.skyworth.upgradeadservice.downloader;

public class ADInfo {
    private String mADType;
    private String mLocalpath;
    private String mUrl;
    private String mMD5;
    private int mShowtime;
    private String mLocalTmpPath;
    
    public ADInfo(String adType, String localpath, String url, String MD5, int showtime) {
        mADType = adType;
        mUrl = url;
        mLocalpath = localpath;
        mMD5 = MD5;
        mShowtime =showtime;
        setlocalTmpPath(localpath + ".tmp");
    }
    
    public String getADType() {
        return mADType;
    }
    
    public String getLocalpath() {
        return mLocalpath;
    }
    
    public String getUrl() {
        return mUrl;
    }
    public void setUrl(String mUrl) {
        this.mUrl = mUrl;
    }

    public String getMD5() {
        return mMD5;
    }

    public void setMD5(String mMD5) {
        this.mMD5 = mMD5;
    }

    public int getShowtime() {
        return mShowtime;
    }

    public void setShowtime(int mShowtime) {
        this.mShowtime = mShowtime;
    }

    public String getlocalTmpPath() {
        return mLocalTmpPath;
    }

    public void setlocalTmpPath(String localTmpPath) {
        this.mLocalTmpPath = localTmpPath;
    }

}
