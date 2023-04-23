package com.skyworth.upgradeadservice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class UpgradeAdReceiver extends BroadcastReceiver{

    @Override
    public void onReceive(Context context, Intent intent) {
        // TODO Auto-generated method stub
        // Toast.makeText(context, "message:"+value, Toast.LENGTH_LONG).show();
        String action = intent.getAction();
		Log.d("UpgradeAdReceiver", "onReceive action : " + action);
        if (action.equalsIgnoreCase("com.skyworth.upgradeadservice.UpgradeAdReceiver")) {
            String value = intent.getStringExtra("ADPlatformUrl");
            Log.d("UpgradeAdReceiver", "ProgressReceiver message:"+value);
            UpgradeAdService.setADUrl(value);
			context.startService(new Intent(context, UpgradeAdService.class));
        } else if(action.equalsIgnoreCase("SKY_IPTV_UPGRADE_BOOT_AND_AUTH_PICTURE")) {
			Log.d("UpgradeAdReceiver", "action from IPTV, prepare to ungrade Ads...");
			intent.setClass(context, IPTVUpgradeAdService.class);
			context.startService(intent);
		}
        /*
        else if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            context.startService(new Intent(context, UpgradeAdService.class));
        }
        */       
    }

}
