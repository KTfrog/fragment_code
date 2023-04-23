package com.skyworth.upgradeadservice.util;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferencesService {
	private Context mContext;
	//PreferencesService instance = null;

	public PreferencesService(Context ctx) {
		// TODO Auto-generated constructor stub
		mContext = ctx;
	}

    public void saveADUrl(String url) {
     	SharedPreferences saveADUrl = mContext.getSharedPreferences("saveADUrl", mContext.MODE_PRIVATE);
        SharedPreferences.Editor editor =saveADUrl.edit();
        editor.putString("saveADUrl", url);
        editor.commit();	
    }
     
    public String getADUrl()
    {
    	SharedPreferences saveADUrl = mContext.getSharedPreferences("saveADUrl", mContext.MODE_PRIVATE);
     	String mADPlatformUrl = saveADUrl.getString("saveADUrl", ""); 
     	return mADPlatformUrl;
    }

    public void setReportShowState(String adType, boolean status /* need report? */) {
        // TODO Auto-generated method stub
        SharedPreferences saveADUrl = mContext.getSharedPreferences("saveADUrl", mContext.MODE_PRIVATE);
        SharedPreferences.Editor editor = saveADUrl.edit();
        editor.putBoolean(adType + "_reportState", status);
        editor.commit();
    };
    
    public boolean getReportState(String adType)
    {
        SharedPreferences saveADUrl = mContext.getSharedPreferences("saveADUrl", mContext.MODE_PRIVATE);
        boolean status = saveADUrl.getBoolean(adType + "_reportState", true);
        return status;
    }
}
