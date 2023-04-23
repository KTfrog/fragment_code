#!/usr/bin/python
#coding:utf-8

import os
import smtplib
import sys
from email.mime.text import  MIMEText
from email.header import Header

g_from_addr='Chenlei01@skyworth.com'
g_passwd = 'kuli.3.20'
g_android_version = 'AML9.0'

g_eng_cn = {
    'BJ' : '北京',
    'HE' : '河北',
    'LN' : '辽宁',
    'JN' : '吉林',
    'TJ' : '天津',
    'SD' : '山东',
    'SX' : '山西',
    'JS' : '江苏',
    'GZ' : '贵州',
    'NMG' : '内蒙古',
    'SN' : '陕西',
    'FJ' : '福建',
    'XJ' : '新疆',
    'YN' : '云南',
    'SH' : '上海',
    'NX' : '宁夏',
    'HI' : '海南',
    'HN' : '湖南',
    'ZJ' : '浙江',
    'SC' : '四川',
    'GD' : '广东',
    'QH' : '青海',
    'XZ' : '西藏',
    'GX' : '广西',
    'CQ' : '重庆',
    'HA' : '河南',
    'GS' : '甘肃',
    'HB' : '湖北',
    'QS' : '青海',
    'HI' : '海南',
    'AH' : '安徽',
    'JX' : '江西',
}

g_all_customers = {
    'BEIJING' : [
                    'BJ_CTC', 'BJ_CU', 'BJ_CM', 
                    'HE_CTC', 'HE_CU', 'HE_CM', 
                    'LN_CTC', 'LN_CU', 'LN_CM', 
                    'JL_CTC', 'JL_CU', 'JL_CM',
                    'JL_CTC', 'JL_CU', 'JL_CM',
                    'HLJ_CTC', 'HLJ_CU', 'HLJ_CM',
                    'SD_CTC', 'SD_CU', 'SD_CM',
                    'JS_CTC', 'JS_CU',
                    'NMG_CTC', 'NMG_CU',
                              'SN_CU', 'SN_CM',
                    'HA_CTC', 'SX_CU', 'GZ_CM',
                    'VIDEO_CENTER_CU'
                ],
    'WUHAN' :   [
                    'GD_CTC', 'GD_CU', 'GD_CM',
                    'CQ_CTC', 'CQ_CU', 'CQ_CM',
                    'GS_CTC', 'GS_CU', 'GS_CM',
                    'JX_CTC', 'JX_CU', 'JX_CM',
                                        'SX_CM', 
                                        'JS_CM',
                                         'AH_CM',
                    'GZ_CTC'
                                        'NMG_CM',
                                        'NX_CM',
                                        'GX_CM',
                                        'HA_CM',
                ],
    'SHENZHEN' :[
                    'FJ_CTC', 'FJ_CU', 'FJ_CM',
                    'XJ_CTC', 'XJ_CU', 'XJ_CM',
                    'YN_CTC', 'YN_CU', 'YN_CM',
                    'SH_CTC', 'SH_CU', 'SH_CM',
                    'HN_CTC', 'HN_CU', 'HN_CM',
                    'ZJ_CTC', 'ZJ_CU', 'ZJ_CM',
                    'SC_CTC', 'SC_CU', 'SC_CM',
                    'QS_CTC', 'QS_CU', 'QS_CM',
                    'HB_CTC', 'HB_CU', 'HB_CM',
                    'HI_CTC', 'HI_CU', 'HI_CM',
                    'AH_CTC', 'AH_CU',
                    'GX_CTC', 'GX_CU',
                    'SX_CTC',
                              'GZ_CU',
                    'XZ_CTC',
                              'HA_CU',
                    'NX_CTC',
                ]
}

g_beijing_customers = g_all_customers['BEIJING']
g_wuhan_customers = g_all_customers['WUHAN']
g_shenzhen_customers = g_all_customers['SHENZHEN']


# g_to_all_addrs = ['1457065732@qq.com', 'Chenlei01@skyworth.com']
g_to_all_addrs = ['lvdongdong@skyworth.com', 'xuqingbiao@skyworth.com', 'gaotiangang@skyworth.com', 'huangguangcun@skyworth.com', 'liguanliang@skyworth.com', 'liuxiao@skyworth.com', 'pengyongjun@skyworth.com', 'Chenlei01@skyworth.com',
'luoqingsong@skyworth.com', 'LongWen@skyworth.com', 'xujia@skyworth.com', 'ChengShaoKang@skyworth.com', 'chenzhuoxuan@skyworth.com', 'dingde@skyworth.com', 'dongweigang@skyworth.com', 'fanjin@skyworth.com', 'FanZhanHao@skyworth.com',
'hemingjun@skyworth.com', 'huangguangcun@skyworth.com', 'lijingchao@skyworth.com', 'liyuntian@skyworth.com', 'luolusheng@skyworth.com', 'luoyongping@skyworth.com', 'lvhang@skyworth.com', 'ningze@skyworth.com', 'qiyaozu@skyworth.com', 
'WuJiaXi@skyworth.com', 'tianhuaxin@skyworth.com', 'wujine@skyworth.com', 'WuWei01@skyworth.com', 'yangpeng05@skyworth.com', 'yangshuang@skyworth.com', 'yangyu@skyworth.com']


code_root_dir='./sk_patch/'

#先保存更新platform前的svn号
def get_svn_key_value(source_svn_info, key):
    list = source_svn_info.split('\n')
    value = ''
    for line in list:
        if (line.find(key) == 0):
            print(line)
            value = line
            break
    key_value = value.split(': ')
    value = key_value[1]
    return value

#更新前
r = os.popen("svn info {}/platform".format(code_root_dir))
svn_info = r.read()
svn_version_num_before = get_svn_key_value(svn_info, 'Last Changed Rev')
svn_url = get_svn_key_value(svn_info, 'URL')

# 1、 更新platform
r = os.popen("svn up {}/platform".format(code_root_dir))
update_log = r.read()
print("原始svn更新信息：{}".format(update_log))
# update_log = '''
# A    platform/system/bt/stack/include/btm_ble_api_types.h
# U    platform/frameworks/base/services/net/java/android/net/dhcp/DhcpConfiguration.java
# D    platform/packages/apps/CTCTestSettings/src/ctc/android/smart/terminal/settings/SubActivity.java
# '''

#更新后
r = os.popen("svn info {}/platform".format(code_root_dir))
svn_info = r.read()
svn_version_num_after = get_svn_key_value(svn_info, 'Last Changed Rev')

def remove_empty_elements(list):
    while "" in list:
        list.remove("")
    return list

svnfile_list = update_log.split('\n')
svnfile_list = remove_empty_elements(svnfile_list)
# 去掉开始一行:正在升级 'platform':
svnfile_list.remove(svnfile_list[0])
# 去掉最后一行：版本 230389。
svnfile_list.remove(svnfile_list[len(svnfile_list) - 1])
#print('svnfile_list:{}'.format(len(svnfile_list)))
if len(svnfile_list) == 0:
    # final ，更新其他目录
    r = os.popen("svn up")
    r.close()
    sys.exit(0)


def get_file_name(path):
    sub_strings = path.split('/')
    count = len(sub_strings)
    name = sub_strings[count-1]
    return name

def array_to_str(arr):
    str = ''
    for item in arr:
        str = str + item + '\n'
    return str

def build_mail_msg(dir, full_paths):
    print('---------------build_mail_msg------------------')
    msg = '\n'
    msg += '''
{}客户化目录下的代码和platform最近更新的代码文件可能有如下重合，请注意同步更新！
{}'''.format(dir, full_paths)
    msg += '\n'
    print('---------------build_mail_msg[{}]'.format(msg))
    return msg

def send_mail2(from_addr, to_addrs, msg):
    print('---------------sned mail------------------')
    smtpObj = smtplib.SMTP()
    smtpObj.connect("maila.skyworth.com", "25")
    smtpObj.login(from_addr, g_passwd)
    mail = MIMEText(msg, 'plain', 'utf-8')
    mail['From'] = Header('svn robot')
    mail['To'] = Header('{}'.format(to_addrs), 'utf-8')
    mail['Subject'] = Header('{}平台代码更新通知'.format(g_android_version), 'utf-8')
    print(to_addrs)
    smtpObj.sendmail(from_addr, to_addrs, mail.as_string())
    print('---------------sned mail------end------------')

# 列出客户化目录，或者是白名单的形式g_dict_mail_addr
all_custom_dirs_list = []
r = os.popen('ls {}'.format(code_root_dir))
all_custom_dirs_list = r.read().split('\n')
all_custom_dirs_list = remove_empty_elements(all_custom_dirs_list)
platform_index = all_custom_dirs_list.index('platform')
all_custom_dirs_list.pop(platform_index)
print("all_custom_dirs_list:{}".format(all_custom_dirs_list))

svnfile_name_list = []
for f in svnfile_list:
    name = get_file_name(f)
    svnfile_name_list.append(name)
#去掉list里重复的文件名（路径不同） 
svnfile_name_list = list(set(svnfile_name_list))

g_gather_msg = ''
effect_custom_dirs = []
for custom_dir in all_custom_dirs_list:
    # 4、找出客户化和platform重合的文件
    print('======================={}  start================='.format(custom_dir))
    effect_file_paths = []
    for path in svnfile_list:
        name = get_file_name(path)
        cmd = 'find ' + code_root_dir + custom_dir + ' -name ' + name + ' -type f'
        r = os.popen(cmd)
        shell_ret_str = r.read()
        #print('shell_ret_str:\n' + shell_ret_str)
        if (len(shell_ret_str) > 0):
            #去掉sk_patch/platform/
            sub_strings = path.split('sk_patch/platform/')
            sub_path = sub_strings[1]
            #print('sub_path:' + sub_path)
            if (shell_ret_str.find(sub_path) >= 0):
                effect_file_paths.append(path)
            elif (sub_path.find('sk_hwconfig') >= 0 and shell_ret_str.find('sk_branches/') >= 0):
                #去掉sk_hwconfig/xxx/两级目录
                sub_strings = sub_path.split('sk_hwconfig/')
                sub_path = sub_strings[1]
                pos = sub_path.find('/')
                sub_path = sub_path[pos+1:]
                print('sub_path:' + sub_path)
                if (shell_ret_str.find(sub_path) >= 0):
                    print('find')
                    effect_file_paths.append(path)
    
    # 4、向客户化研发发送提醒邮件
    if (len(effect_file_paths) > 0):
        effect_custom_dirs.append(custom_dir)
        effect_file_paths_str = array_to_str(effect_file_paths)
        g_gather_msg += build_mail_msg(custom_dir, effect_file_paths_str)
    print('======================={}  end================='.format(custom_dir))

#对effect_custom_dirs进行分类，分成北京，武汉，深圳
effect_beijing_dirs = []
effect_wuhan_dirs = []
effect_shenzhen_dirs = []
effect_unknown_dirs = []
for custom_dir in effect_custom_dirs:
    custom_dir = custom_dir.replace('-', '_')
    is_find = False
    print(' custom_dir:' + custom_dir)
    for dir in g_beijing_customers:
        if (custom_dir.find(dir) == 0):
            effect_beijing_dirs.append(custom_dir)
            is_find = True
            print('effect_beijing_dirs add:' + custom_dir)
            break
    print('1 is_find:{}'.format(is_find))
    if (is_find):
        continue
    for dir in g_wuhan_customers:
        if (custom_dir.find(dir) == 0):
            effect_wuhan_dirs.append(custom_dir)
            is_find = True
            break
    print('2 is_find:{}'.format(is_find))
    if (is_find):
        continue
    for dir in g_shenzhen_customers:
        #print('shenzhen dir:' + dir)
        if (custom_dir.find(dir) == 0):
            effect_shenzhen_dirs.append(custom_dir)
            is_find = True
            break
    print('3 is_find:{}'.format(is_find))
    if (is_find):
        continue
    effect_unknown_dirs.append(custom_dir)

#先显示对哪些客户化目录有影响
effect_custom_dirs_str = '''(邮件无法直接回复，如需退订邮件通知或有其他建议请联系Chenlei01@skyworth.com)
最近platform更新可能和如下客户化项目代码有重合，请注意同步：
北京：{}
武汉：{}
深圳：{}
未知：{}\n
'''.format(effect_beijing_dirs, effect_wuhan_dirs, effect_shenzhen_dirs, effect_unknown_dirs)
# 增加版本信息 
#svn log -r 240795:242678 sk_patch/platform/
svn_log_cmd = 'svn log -r {}:{} {}/platform'.format(int(svn_version_num_before)+1, svn_version_num_after, code_root_dir)
print('svn_log_cmd:' + svn_log_cmd)
svn_log = ''
r = os.popen(svn_log_cmd)
svn_log = r.read()
svn_log = '{}\n更新日志：\n{}'.format(svn_url, svn_log)
update_log = '本次platform更新的全部文件如下：\n' + update_log
total_msg = effect_custom_dirs_str + svn_log + update_log
if (len(g_gather_msg) > 0):
    total_msg = total_msg + '\n=============================以下为各个客户化目录和platform更新文件重合的详细情况================================' + g_gather_msg
send_mail2(g_from_addr, g_to_all_addrs, total_msg)
#print(total_msg)

print(svnfile_name_list)

# final ，更新其他目录
r = os.popen("svn up")

r.close()

print('======================exec finish=========================')