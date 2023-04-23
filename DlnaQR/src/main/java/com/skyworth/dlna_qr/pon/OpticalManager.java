package com.skyworth.dlna_qr.pon;


import java.io.BufferedReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import android.net.ethernet.EthernetManager;
import android.net.IpInfo;
import java.lang.String;

import android.content.Context;
import android.util.Log;
import android.os.SystemProperties;


import java.util.Timer;
import java.util.TimerTask; 

public class OpticalManager {
	public final static String TAG = "OpticalManager_QR";
	public static final String OPT_PROP_FLAG = "persist.sys.opt_flag";
	public static final String OPT_PROP_GATEWAY = "persist.sys.opt_gateway";
	
	public  String defaultGateway = "192.168.1.1";
	public  int defaultPort = 8011; 
	public  String realGateway;
	public  String interactStr;
	public  String reasultStr;
	public  Context mContext;
	public  boolean tcpThreadFlag = false;
	public  Socket  tcpSocket = null; 
	public  OutputStream  outputStream = null;
	public  InputStream inputStream = null;
	public  boolean  interactSuccess = false;
	public boolean timeoutFlag = false;
	
	public OpticalManager(Context context) {
		mContext = context;
		init();
	}
	
	private void init(){
		realGateway = getEthernetGateway();
	}


	
	//获取网关，如果失败，采取默认网关
	private String getEthernetGateway(){
		String gateway = defaultGateway;
		if(SystemProperties.get(OPT_PROP_FLAG, "0").equals("1")){
			gateway = SystemProperties.get(OPT_PROP_GATEWAY, defaultGateway);
		}else{
			EthernetManager ethernetManager = (EthernetManager)mContext.getSystemService(Context.ETHERNET_SERVICE);
			if(ethernetManager != null){
				IpInfo mIpInfo = ethernetManager.getIPv4Info();
				if (mIpInfo != null) {
					gateway = mIpInfo.gateway.getHostAddress();
				}
			}
		}
		Log.i(TAG, "gateway = " + gateway);
		return gateway;
	}
	
	
	//tcp 子线程
	public class TcpThread extends Thread{
		public void stopThread(){
			try {
				if(tcpSocket != null ){
						tcpSocket.close();
						tcpSocket = null;
				}
			}
			catch (Exception  e) {
				e.printStackTrace();
			}
		}
		
		public void run() {
			try {
				String str = stitchURL();
				if(str == null){
					interactSuccess = false;
					return ;
				}
				if(tcpSocket != null ){
					tcpSocket.close();
					tcpSocket = null;
				}
				
				tcpSocket = new Socket(realGateway,defaultPort);
				outputStream = tcpSocket.getOutputStream();
				inputStream = tcpSocket.getInputStream();
				
				outputStream.write(str.getBytes("UTF-8"));
				outputStream.flush();
				
				BufferedReader bf = new BufferedReader(new InputStreamReader(inputStream));				
				StringBuilder reasultBuilder = new StringBuilder();
				String line = bf.readLine();
				String recvStr = null;
				if(line.contains("<skmsg>") && line.contains("</skmsg>") ){
					String []StrArr = line.split("<skmsg>");
					recvStr = StrArr[1];
					StrArr = recvStr.split("</skmsg>");
					reasultStr = StrArr[0];
					interactSuccess = true;
				}
				Log.i(TAG, "reasultStr --->" + reasultStr);
				tcpSocket.close();
				tcpSocket = null;
				inputStream = null;
				outputStream =null;
				
			} catch (Exception  e) {
				interactSuccess = false;
				Log.e(TAG, "Socket exception :"+ e);
				e.printStackTrace();
			}
		};
	}
	
	
	//http请求
	public  boolean sendCommandToServer(){
		try{
				interactSuccess = false;
				TcpThread tcpThread = new TcpThread();
                		tcpThread.start();
				
				// 初始化定时器
				Timer timer = new Timer();
				timeoutFlag = false;
				long delay = 6000;
				timer.schedule(new TimerTask() {
					public void run() {
					timeoutFlag = true;
					}
				}, delay);
				while((!timeoutFlag) && tcpThread.isAlive()){
					Thread.sleep(200);
				}
				if(timeoutFlag){
					tcpThread.interrupt();
					Log.e(TAG,"timeout");
					return false;
				}
				// 停止定时器
				if(timer != null){
					timer.cancel();
					timer = null;
				}		
				
				tcpThread.join(); 
				return interactSuccess;
			
		}catch(Exception e){
			Log.e(TAG, "exception :"+ e);
			e.printStackTrace();
		}
		return false;
	}
	
	//拼接URL
	private String stitchURL(){
		if(realGateway!=null && interactStr!=null){
			Log.i(TAG, "send json :"+ interactStr);
			return ("<skmsg>"+ interactStr + "</skmsg>" );
		}
		return null;
	}
	
}
