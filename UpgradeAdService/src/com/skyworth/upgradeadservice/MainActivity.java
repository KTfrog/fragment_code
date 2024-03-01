package com.skyworth.upgradeadservice;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.MboxOutputModeManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class MainActivity extends Activity {
    public static final String TAG = "UpgradeAdMainActivity";
    Button btn1, btn2, btn3, btn4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        btn1 = (Button)findViewById(R.id.button1);
        btn2 = (Button)findViewById(R.id.button2);
        btn3 = (Button)findViewById(R.id.button3);
        btn4 = (Button)findViewById(R.id.button4);
        
        
        btn1.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                Intent intent = new Intent();
                intent.setAction("com.skyworth.upgradeadservice.UpgradeAdService");
                startService(intent);
            }
        });
        
        btn2.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                Intent intent = new Intent();
                intent.setAction("com.skyworth.upgradeadservice.UpgradeAdReceiver");
                //intent.setAction(Intent.ACTION_BOOT_COMPLETED);
                String value = "http://192.168.20.152/php/302.php";
                intent.putExtra("ADPlatformUrl", value);
                sendBroadcast(intent);
            }
        });
        
        btn3.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                Intent intent = new Intent();
                intent.setAction("com.skyworth.upgradeadservice.UpgradeAdService");
                stopService(intent);
            }
        });
        
        btn4.setOnClickListener(new OnClickListener() {
            
            @Override
            public void onClick(View arg0) {
                // TODO Auto-generated method stub
                upgradeBootLogo("/data/local/boot.jpg");//ZTE
				//upgradeBootLogo("/data/local/bootPicHW.jpg");//HW
            }
        });
    }
    
    public boolean upgradeBootLogo(String path) {
        MboxOutputModeManager mbox = (MboxOutputModeManager)this.getSystemService(this.MBOX_OUTPUTMODE_SERVICE);
        if (mbox != null) {
            int ret = mbox.updateLogo(path);
            Log.d(TAG, "aaaa, update logo ret:" + ret);
            if (ret != -1) {
                Log.d("UpgradeAdMainActivity", "aaaa, MboxOutputModeManager update logo ok");
            }
        }
        return false;
    }
    
}
