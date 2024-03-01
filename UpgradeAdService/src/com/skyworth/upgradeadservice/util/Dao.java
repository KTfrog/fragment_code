package com.skyworth.upgradeadservice.util;

import java.util.ArrayList;
import java.util.List;

import com.skyworth.upgradeadservice.downloader.ADInfo;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/**
 * 
 *
 */
public class Dao {
    private DBHelper dbHelper;

    public Dao(Context context) {
        dbHelper = new DBHelper(context);
    }
    
    /**
     * init database
     */
    public void init(List<ADInfo> infos) {
        Log.d("Dao", "aaaa, init()");
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        for (ADInfo info : infos) {
            String sql = "SELECT * FROM download_info WHERE adType=?";
            Cursor cursor = database.rawQuery(sql, new String[] { info.getADType() });
            Log.d("Dao", "aaaa, count:" + cursor.getCount());
            if (cursor.getCount() == 0) {  // not find, then init
                String insert = "insert into download_info(adType, localPath, md5, showTime) values (?,?,?,?)";
                Object[] bindArgs = { info.getADType(), info.getLocalpath(),
                        info.getMD5(), info.getShowtime()};
                database.execSQL(insert, bindArgs);
            }
        }
    }

    /**
     *
     */
    public boolean isExistInfor(String adType, String md5) {
        Log.d("Dao", "isExistInfor, adType:" + adType + ", md5:" + md5);
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        String sql = "SELECT *  FROM download_info WHERE adType=? AND md5=?";
        Cursor cursor = database.rawQuery(sql, new String[] {adType,  md5});
        Log.d("Dao", "aaaa, getCount:" + cursor.getCount());
        // not exist
        if (cursor.getCount() != 0)
            return true;
        
        return false;
    }

    /**
     *
     */
    public void updataInfos(String adType, String md5, int showTime) {
        Log.d("Dao", "updataInfos, adType:" + adType + ", md5:" + md5 + ", showTime" + showTime);
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        String sql = "UPDATE download_info SET md5 = ? , showTime = ? WHERE adType=?";
        Object[] bindArgs = { md5, showTime, adType };
        database.execSQL(sql, bindArgs);
    }
    
    /**
     *
     */
    public void saveInfo(List<ADInfo> infos) {
        SQLiteDatabase database = dbHelper.getWritableDatabase();
        for (ADInfo info : infos) {
            String sql = "insert into download_info(adType, localPath, md5, showTime) values (?,?,?,?)";
            Object[] bindArgs = { info.getADType(), info.getLocalpath(),
                    info.getMD5(), info.getShowtime()};
            database.execSQL(sql, bindArgs);
        }
    }

    /**
     *
     */
    public List<ADInfo> getInfos(String MD5) {
        List<ADInfo> list = new ArrayList<ADInfo>();
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        String sql = "select thread_id, start_pos, end_pos,compelete_size,url, md5 from download_info where md5=?";
        Cursor cursor = database.rawQuery(sql, new String[] { MD5 });
        while (cursor.moveToNext()) {
            ADInfo info = new ADInfo(cursor.getString(0),
                    cursor.getString(1), cursor.getString(2), cursor.getString(2), cursor.getInt(3));
            list.add(info);
        }
        cursor.close();
        return list;
    }

    /**
     *
     */
    public void closeDb() {
        dbHelper.close();
    }

    /**
     *
     */
    public void delete(String md5) {
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        database.delete("download_info", "md5=?", new String[] { md5 });
        database.close();
    }
}
