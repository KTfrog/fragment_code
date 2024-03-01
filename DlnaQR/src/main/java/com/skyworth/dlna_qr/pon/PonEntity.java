package com.skyworth.dlna_qr.pon;

public class PonEntity {
	//wireless
	public boolean pon_wireless_multi_flag;	 //MULTI FLAG
	public int pon_wireless_ssid_number;//SSID COUNT
	public int pon_wireless_index;//SSID INDEX
	public String []pon_wireless_auth_mode = {"", "", "", "","","","",""};	 //AUTH MODE
	public String []pon_wireless_wps_conf_mode = {"", "", "", "","","","",""};//WPS MODE
	public String []pon_wireless_wpa_type = {"", "", "", "","","","",""};  //WPA AUTH MODE
	public String []pon_wireless_wep_type = {"", "", "", "","","","",""};	 //WEP AUTH MODE	
	public String []pon_wireless_ssid = {"", "", "", "","","","",""};;		 //SSID
	public String []pon_wireless_wpa_pwd = {"", "", "", "","","","",""};		 //WPA PWD
	public String []pon_wireless_wep_pwd = {"", "", "", "","","","",""};		 //WEP PWD1
	public boolean []pon_wireless_ssid_index_enable={true,true,true,true,true,true,true,true}; 
	public int []pon_wireless_ssid_index_position={0,1,2,3,4,5,6,7}; //for spinner chose

	//Loid&&DeviceRegister
	public String pon_loid_pon_type;		//PON TYPE: GPON/EPON
	public String pon_loid;					//LOGIC ID
	public String pon_loid_pwd;				//LOGIC ID PWD
	public String pon_loid_olt_auth;		//LOGIC ID OLT AUTH ENABLE
	
	//bandwidth
	public String []pon_bandwidth_wan_name= {"", "", "", "", "", "", "", ""};
	public String []pon_bandwidth_wan_index= {"", "", "", "", "", "", "", ""};
	public String []pon_bandwidth_wan_name_ext= {"", "", "", "", "", "", "", ""};
	public String []pon_bandwidth_wan_mode= {"", "", "", "", "", "", "", ""};
	public String []pon_bandwidth_wan_service= {"", "", "", "", "", "", "", ""};
	public String []pon_bandwidth_wan_vlan_mode= {"", "", "", "", "", "", "", ""};
	public String []pon_bandwidth_wan_vlan_id= {"", "", "", "", "", "", "", ""};
	public String []pon_bandwidth_wan_vlan_mtu= {"", "", "", "", "", "", "", ""};
	public String []pon_bandwidth_wan_8021P= {"0", "0", "0", "0", "0", "0", "0", "0"};
	public String []pon_bandwidth_wan_group_vlan_id= {"", "", "", "", "", "", "", ""};
	public String []pon_bandwidth_wan_lan1= {"", "", "", "", "", "", "", ""};
	public String []pon_bandwidth_wan_lan2= {"", "", "", "", "", "", "", ""};
	public String []pon_bandwidth_wan_lan3= {"", "", "", "", "", "", "", ""};
	public String []pon_bandwidth_wan_lan4= {"", "", "", "", "", "", "", ""};
	public String []pon_bandwidth_wan_ssid1= {"", "", "", "", "", "", "", ""};
	public String []pon_bandwidth_wan_ssid2= {"", "", "", "", "", "", "", ""};
	public String []pon_bandwidth_wan_ssid3= {"", "", "", "", "", "", "", ""};
	public String []pon_bandwidth_wan_ssid4= {"", "", "", "", "", "", "", ""};
	public String []pon_bandwidth_wan_ssid5= {"", "", "", "", "", "", "", ""};
	public String []pon_bandwidth_wan_ssid6= {"", "", "", "", "", "", "", ""};
	public String []pon_bandwidth_wan_ssid7= {"", "", "", "", "", "", "", ""};
	public String []pon_bandwidth_wan_ssid8= {"", "", "", "", "", "", "", ""};
	public int      []pon_bandwidth_wan_bind_flag       ={0,0,0,0,0,0,0,0};
	public String []pon_bandwidth_wan_pppuser = {"", "", "", "", "", "", "", ""};
	public String []pon_bandwidth_wan_ppppwd = {"", "", "", "", "", "", "", ""};
	public String []pon_bandwidth_wan_static_ip = {"", "", "", "", "", "", "", ""};
	public String []pon_bandwidth_wan_static_gateway= {"", "", "", "", "", "", "", ""};
	public String []pon_bandwidth_wan_static_mask = {"", "", "", "", "", "", "", ""};
	public String []pon_bandwidth_wan_static_dns = {"", "", "", "", "", "", "", ""};
	public String []pon_bandwidth_wan_link_mode={"", "", "", "", "", "", "", ""};
	public String []pon_bandwidth_wan_ip_version={"", "", "", "", "", "", "", ""};
	public String []pon_bandwidth_wan_dhcp_enable={"", "", "", "", "", "", "", ""};
	public int pon_bandwidth_wan_num;
	public int pon_bandwidth_wan_all_bind_flag;
	public int currentBandwidthIndex;
	public int preferenceFlag;
	public String pon_bandwidth_nogui_access_limit;
	public String pon_bandwidth_lan_flag;
	public String pon_bandwidth_bssid_flag;
	public boolean pon_bandwidth_bssid_dualband;

	
	public PonEntity() {
		pon_wireless_multi_flag = true;
		pon_wireless_ssid_number = 8;
		pon_wireless_index = 0;
		pon_loid = "";
		pon_loid_pwd = "";
		pon_loid_olt_auth = "";
		currentBandwidthIndex = 0;
		pon_bandwidth_bssid_flag="255";
		pon_bandwidth_lan_flag = "15";
		pon_bandwidth_nogui_access_limit = "0";
		pon_bandwidth_wan_all_bind_flag = 0;
		pon_bandwidth_wan_num = 8;
		pon_bandwidth_bssid_dualband = false;
	}
}
