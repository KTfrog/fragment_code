/*
  I am Client!
*/

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/queue.h>
#include <sys/time.h>
//socket
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <sys/stat.h>
#include <unistd.h>
#include <fcntl.h>
#include <netinet/ip.h>
#include <error.h>

#include "lsquic.h"
//#include "test_common.h"
//#include "prog.h"
#include "../src/liblsquic/lsquic_logger.h"


#if defined(IP_RECVORIGDSTADDR)
#   define DST_MSG_SZ sizeof(struct sockaddr_in)
#else
#   define DST_MSG_SZ sizeof(struct in_pktinfo)
#endif

#define ECN_SZ CMSG_SPACE(sizeof(int))

#define MAX(a, b) ((a) > (b) ? (a) : (b))
/* Amount of space required for incoming ancillary data */
#define CTL_SZ (CMSG_SPACE(DST_MSG_SZ) + ECN_SZ)


#define PRINT(fmt, ...) printf("%s   "fmt, get_cur_time(), ##__VA_ARGS__)
#define ERROR(fmt, ...) printf("%s   "fmt" :%s\n", get_cur_time(), ##__VA_ARGS__, strerror(errno))
char *get_cur_time()
{
    static char str_time[32] = {0};
    struct tm *tm_t;
    struct timeval time;
    
    gettimeofday(&time,NULL);
    tm_t = localtime(&time.tv_sec);
    if(NULL != tm_t) {
        sprintf(str_time,"%04d-%02d-%02d %02d:%02d:%02d.%06ld",
            tm_t->tm_year+1900,
            tm_t->tm_mon+1, 
            tm_t->tm_mday,
            tm_t->tm_hour, 
            tm_t->tm_min, 
            tm_t->tm_sec,
            time.tv_usec);
    }
    return str_time;
}

struct cl_client_ctx {
    struct lsquic_conn_ctx  *conn_h;
    struct lsquic_stream_ctx *stream_h;
    struct sockaddr_in *local_addr;
    lsquic_engine_t *engine;
    int is_client;
};

struct lsquic_conn_ctx {
    lsquic_conn_t       *conn;
    struct cl_client_ctx   *client_ctx;
};

struct lsquic_stream_ctx {
    lsquic_stream_t     *stream;
    struct cl_client_ctx   *client_ctx;
    struct event        *read_stdin_ev;
    char                 buf[8192];
    size_t               buf_used;   /* 已使用的buffer大小 */
};

lsquic_conn_ctx_t* g_lsquic_conn_ctx;
struct cl_client_ctx* g_cl_client_ctx;
static int s_is_lsq_hsk_ok = 0;
static int s_is_send_finished = 0;
void client_read_local_data();

static lsquic_conn_ctx_t *
cl_client_on_new_conn (void *stream_if_ctx, lsquic_conn_t *conn)
{
    PRINT(">>>> func:%s, line:%d\n", __func__, __LINE__);
    lsquic_conn_ctx_t *conn_h = malloc(sizeof(*conn_h));
    conn_h->conn = conn;

    g_lsquic_conn_ctx = conn_h;
    g_lsquic_conn_ctx->client_ctx = (struct cl_client_ctx *)stream_if_ctx;
    g_cl_client_ctx->conn_h = g_lsquic_conn_ctx;
    /* Need a stream to send request */
    if (g_cl_client_ctx->is_client) {
        PRINT("func:%s, line:%d. lsquic_conn_make_stream\n", __func__, __LINE__);
        lsquic_conn_make_stream(conn);
    }
    PRINT(">>>> func:%s, line:%d. end\n", __func__, __LINE__);
    return conn_h;
}

static void
cl_client_on_conn_closed (lsquic_conn_t *conn)
{
    PRINT(">>>> func:%s, line:%d\n", __func__, __LINE__);
    lsquic_conn_ctx_t *conn_h = lsquic_conn_get_ctx(conn);
    LSQ_NOTICE("Connection closed");
    lsquic_conn_set_ctx(conn, NULL);
    free(conn_h);
}

static lsquic_stream_ctx_t *
cl_client_on_new_stream (void *stream_if_ctx, lsquic_stream_t *stream)
{
    PRINT(">>>> func:%s, line:%d. On new stream\n", __func__, __LINE__);
    lsquic_stream_ctx_t *st_h = calloc(1, sizeof(*st_h));
    st_h->stream = stream;
    st_h->client_ctx = stream_if_ctx;
    st_h->buf_used = 0;
    
    g_cl_client_ctx->stream_h = st_h;
    /*
    if (g_cl_client_ctx->is_client) {
        PRINT(">>>> func:%s, line:%d. lsquic_stream_wantwrite(stream, 1)\n", __func__, __LINE__);
        lsquic_stream_wantwrite(stream, 1); // 设置为1后，后面再调用lsquic_engine_process_conns，就会触发 on_write
    }    */
    return st_h;
}


static void
cl_client_on_read (lsquic_stream_t *stream, lsquic_stream_ctx_t *st_h)
{
    PRINT(">>>> func:%s, line:%d\n", __func__, __LINE__);
    unsigned char buf[1500] = {0};
    size_t nr;

    nr = lsquic_stream_read(stream, buf, sizeof(buf));
    PRINT(">>>> func:%s, line:%d. nr:%ld\n", __func__, __LINE__, nr);
    if (0 == nr)
    {
        lsquic_stream_shutdown(stream, 2);
        //lsquic_conn_make_stream(tmpClientState->conn);
        return;
    }
    PRINT(">>>> func:%s, line:%d. Read frome server:[%s]\n", __func__, __LINE__, buf);
}

#define READ_LOCAL_FILE 1
static int totalBytes = 0;
static void
cl_client_on_write (lsquic_stream_t *stream, lsquic_stream_ctx_t *st_h)
{
    PRINT(">>>> func:%s, line:%d. stream:%p\n", __func__, __LINE__, stream);
    ssize_t nw = 0;
    //int i = 0;
    //for (i = 0; i < 2; i++)  // 在这for循环也只能触发一次packets_out
#if 0//test_common
    if (strlen(st_h->buf) == 0) {
        strcpy(st_h->buf, "chenlei\n");
        st_h->buf_used = strlen(st_h->buf);
    }
#endif
#if 0
    client_read_local_data();
#endif
#if READ_LOCAL_FILE
    // 打开文件这些初始化代码应该放在别的位置
    FILE *fp;
    fp = fopen("video.ts" , "r");
    if (fp == NULL) {
        perror("open video.ts failed!\n");
        return;
    }
    struct stat fileStat;
    if (fstat(fileno(fp), &fileStat) == -1) {  
        printf("获取文件状态失败\n");  
        return ;  
    }
    if (totalBytes == fileStat.st_size) {
        printf("文件已全部发送完毕！\n");  
        s_is_send_finished = 1;
    }

    int numBytes = 0;
    fseek(fp, totalBytes, SEEK_SET);
    while ((numBytes = fread(st_h->buf, sizeof(char), sizeof(st_h->buf), fp)) > 0) {
        st_h->buf_used = numBytes;
#endif
        PRINT("st_h->buf_used:%ld\n", st_h->buf_used);
        nw = lsquic_stream_write(stream, st_h->buf, st_h->buf_used);
        totalBytes += nw;
        PRINT("nw:%ld\n", nw);
        st_h->buf_used = 0;
        memset(st_h->buf, 0, sizeof(st_h->buf));
        /* Here we make an assumption that we can write the whole buffer.
        * Don't do it in a real program.
        */

        if (nw > 0) {
            lsquic_stream_flush(stream); // 多次flush也只能触发一次packets_out
            /*
            int diff;
            if (lsquic_engine_earliest_adv_tick(g_cl_client_ctx->engine, &diff))
            {
                PRINT("diff:%d\n", diff);
                lsquic_engine_process_conns(st_h->client_ctx->engine);
            }
            else
            {
                PRINT("FUN[%s]- adv_tick  return abnormal\n", __FUNCTION__);
            }
            */
            usleep(1000);
        }
        else {
            //client_read_local_data();
            PRINT("func:%s, line:%d. lsquic_stream_write. st_h->buf:[%s], len:%ld\n", __func__, __LINE__, st_h->buf, strlen(st_h->buf));
            //nw = lsquic_stream_write(stream, st_h->buf, strlen(st_h->buf));
            //PRINT("nw:%ld\n", nw);
            //st_h->buf_used = 0;
            //lsquic_engine_process_conns(st_h->client_ctx->engine);//会崩溃。。。
            //usleep(4000);
            break;
        }
#if READ_LOCAL_FILE
    }
#endif
    PRINT("totalBytes:%d\n", totalBytes);
    fclose(fp);

    lsquic_stream_wantwrite(stream, 0); // 多次调用也只能触发一次packets_out
    //ev_io_start(tmpClientState->loop, &tmpClientState->read_local_data_ev);// 异步启动监听标准输入
    lsquic_stream_wantread(stream, 1);
    // end 触发packets_out
    PRINT(">>>> func:%s, line:%d. end!!!!\n", __func__, __LINE__);
}

static void
cl_client_on_hsk_done(lsquic_conn_t *conn, enum lsquic_hsk_status status)
{
    PRINT(">>>> func:%s, line:%d. end\n", __func__, __LINE__);
    //lsquic_conn_ctx_t *conn_h = (lsquic_conn_ctx_t *) lsquic_conn_get_ctx(conn);
    switch (status)
    {
        case LSQ_HSK_OK:
        case LSQ_HSK_RESUMED_OK:
            PRINT("handshake successful, start stdin watcher\n");
            //client_read_local_data();
            s_is_lsq_hsk_ok = 1;
            break;
        default:
            PRINT("handshake failed\n");
            break;
    }
}


static void
cl_client_on_close (lsquic_stream_t *stream, lsquic_stream_ctx_t *st_h)
{
    PRINT(">>>> func:%s, line:%d\n", __func__, __LINE__);
    LSQ_NOTICE("%s called", __func__);
    // if (st_h->read_stdin_ev)
    // {
    //     event_del(st_h->read_stdin_ev);
    //     event_free(st_h->read_stdin_ev);
    // }
    free(st_h);
    lsquic_conn_close(lsquic_stream_conn(stream));
}

const struct lsquic_stream_if g_lsquic_stream_if = {
    .on_new_conn            = cl_client_on_new_conn,
    .on_conn_closed         = cl_client_on_conn_closed,
    .on_new_stream          = cl_client_on_new_stream,
    .on_read                = cl_client_on_read,
    .on_write               = cl_client_on_write,
    .on_hsk_done            = cl_client_on_hsk_done,
    .on_close               = cl_client_on_close,
};


struct ssl_ctx_st* cl_lookup_cert(void *lsquic_cert_lookup_ctx, const struct sockaddr *local, const char *sni)
{
    PRINT(">>>> func:%s, line:%d\n", __func__, __LINE__);
    return NULL;
}

int cl_packets_out(
    void                          *packets_out_ctx,
    const struct lsquic_out_spec  *out_spec,
    unsigned                       n_packets_out
) 
{
    PRINT(">>>> func:%s, line:%d. n_packets_out:%d\n", __func__, __LINE__, n_packets_out);
    struct msghdr msg;
    int sockfd;
    unsigned n;

    memset(&msg, 0, sizeof(msg));
    sockfd = (int) (uintptr_t) packets_out_ctx;

    for (n = 0; n < n_packets_out; ++n)
    {
        msg.msg_name       = (void *) out_spec[n].dest_sa;
        msg.msg_namelen    = sizeof(struct sockaddr_in);
        msg.msg_iov        = out_spec[n].iov;
        msg.msg_iovlen     = out_spec[n].iovlen;
        PRINT("msg_iov[%d].iov_len:%ld\n", n, msg.msg_iov[n].iov_len);
        if (sendmsg(sockfd, &msg, 0) < 0) {
            PRINT("func:%s, line:%d. sendmsg < 0\n", __func__, __LINE__);
            break;
        }
    }
    PRINT("func:%s, line:%d. n=%d\n\n\n", __func__, __LINE__, n);
    return (int) n;
}

void new_addr(struct sockaddr_in* addr, char *ip, unsigned int port)
{
    addr->sin_family = AF_INET;
    addr->sin_port = htons(port);
    addr->sin_addr.s_addr = inet_addr(ip);
}

int set_fd_nonblocking (int fd)
{
    int flags;

    flags = fcntl(fd, F_GETFL);
    if (-1 == flags)
        return -1;
    flags |= O_NONBLOCK;
    if (0 != fcntl(fd, F_SETFL, flags))
        return -1;

    return 0;
}

int set_fd_blocking (int fd)
{
    int flags;

    flags = fcntl(fd, F_GETFL);
    if (-1 == flags)
        return -1;
    flags &= ~O_NONBLOCK;
    if (0 != fcntl(fd, F_SETFL, flags))
        return -1;

    return 0;
}

void tut_proc_ancillary (struct msghdr *msg, 
                            struct sockaddr_storage *storage, int *ecn)
{
    PRINT("func:%s, line:%d. \n", __func__, __LINE__);
    //const struct in6_pktinfo *in6_pkt;
    struct cmsghdr *cmsg;

    for (cmsg = CMSG_FIRSTHDR(msg); cmsg; cmsg = CMSG_NXTHDR(msg, cmsg))
    {
        if (cmsg->cmsg_level == IPPROTO_IP &&
            cmsg->cmsg_type  ==
#if defined(IP_RECVORIGDSTADDR)
                                IP_ORIGDSTADDR
#else
                                IP_PKTINFO
#endif
                                              )
        {
#if defined(IP_RECVORIGDSTADDR)
            memcpy(storage, CMSG_DATA(cmsg), sizeof(struct sockaddr_in));
#else
            const struct in_pktinfo *in_pkt;
            in_pkt = (void *) CMSG_DATA(cmsg);
            ((struct sockaddr_in *) storage)->sin_addr = in_pkt->ipi_addr;
#endif
        }/*
        else if (cmsg->cmsg_level == IPPROTO_IPV6 &&
                 cmsg->cmsg_type  == IPV6_PKTINFO)
        {
            in6_pkt = (void *) CMSG_DATA(cmsg);
            ((struct sockaddr_in6 *) storage)->sin6_addr =
                                                    in6_pkt->ipi6_addr;
        }*/
        else if ((cmsg->cmsg_level == IPPROTO_IP && cmsg->cmsg_type == IP_TOS)
                 || (cmsg->cmsg_level == IPPROTO_IPV6
                                            && cmsg->cmsg_type == IPV6_TCLASS))
        {
            memcpy(ecn, CMSG_DATA(cmsg), sizeof(*ecn));
            *ecn &= IPTOS_ECN_MASK;
        }
    }
}

int client_read_net_data(int sockfd) 
{
    PRINT("func:%s, line:%d. \n", __func__, __LINE__);
    ssize_t nread;
    struct sockaddr_storage peer_sas;
    unsigned char buf[4096];
    unsigned char ctl_buf[CTL_SZ];
    struct iovec vec[1] = {{ buf, sizeof(buf) }};

    struct msghdr msg = {
        .msg_name       = &peer_sas,
        .msg_namelen    = sizeof(peer_sas),
        .msg_iovlen     = 1,
        .msg_iov        = vec,
        .msg_control    = ctl_buf,
        .msg_controllen = sizeof(ctl_buf),
    };
    nread = recvmsg(sockfd, &msg, 0);
    if (-1 == nread) {
        PRINT("-1 == nread\n");
        return -1;
    }
    PRINT("socket receive_size %ld\n", nread);
    // int i = 0;
    // for (i = 0; i < nread; i++) {
    //     PRINT("%2x ", buf[i]);
    //     if (i%32 == 7) {
    //         PRINT("\n");
    //     }
    // }
    // PRINT("\n");
    
    struct sockaddr_storage* local_sas = (struct sockaddr_storage*)g_cl_client_ctx->local_addr;
    // TODO handle ECN properly
    int ecn = 0;
    tut_proc_ancillary(&msg, local_sas, &ecn);
    PRINT("func:%s, line:%d. lsquic_engine_packet_in\n", __func__, __LINE__);
    lsquic_engine_packet_in(g_cl_client_ctx->engine, buf, nread,
                                (struct sockaddr *) local_sas,
                                (struct sockaddr *) &peer_sas,
                                NULL, ecn);
    //the fourth lsquic_engine_process_conns fire both on_new_stream and cl_packets_out
    printf("\n\n");
    PRINT("func:%s, line:%d. lsquic_engine_process_conns\n", __func__, __LINE__);
    lsquic_engine_process_conns(g_cl_client_ctx->engine);     

    int diff;
    if (lsquic_engine_earliest_adv_tick(g_cl_client_ctx->engine, &diff))
    {
        PRINT("diff:%d\n", diff);
    }
    else
    {
        PRINT("FUN[%s]- adv_tick  return abnormal\n", __FUNCTION__);
    }
    return nread;
}

void client_read_local_data() 
{
    PRINT("func:%s, line:%d. end\n", __func__, __LINE__);
    //TODO: chenlei
    //char data[1024];
    //fgets(data,  1024, stdin); //字符串地址  字符串长度   读入的文件
    lsquic_stream_ctx_t     *stream_h = g_cl_client_ctx->stream_h;
#if 1
    if(0 != set_fd_blocking(STDIN_FILENO) )
    {
        perror("STDIN_FILENO fcntl");
        return ;
    }
    memset(stream_h->buf, 0, sizeof(stream_h->buf));
    fgets(stream_h->buf, 1024, stdin);
    stream_h->buf_used = strlen(stream_h->buf);
#endif
    PRINT("func:%s, line:%d. stream_h->buf:[%s]\n", __func__, __LINE__, stream_h->buf);

    PRINT("func:%s, line:%d. lsquic_stream_wantwrite(stream_h->stream, 1)\n", __func__, __LINE__);
    lsquic_stream_wantwrite(stream_h->stream, 1);
    PRINT("func:%s, line:%d. lsquic_engine_process_conns\n", __func__, __LINE__);
    if (stream_h->buf_used <= 0) {
        lsquic_engine_process_conns(g_cl_client_ctx->engine); 
    }
}

int main(int argc, char** argv)
{
    PRINT("func:%s, line:%d\n", __func__, __LINE__);
    g_cl_client_ctx = malloc(sizeof(*g_cl_client_ctx));
    g_cl_client_ctx->is_client = 1;

    // {{ create and init socket
    int sockfd = socket(AF_INET, SOCK_DGRAM, 0);
    if (-1 == sockfd) {
        PRINT("create socket failed!");
        return -1;
    }

    if(0 != set_fd_nonblocking(sockfd) )
    {
        perror("fcntl");
        return -1;
    }

    int on = 1;
    // 允许应用程序接受数据包时获得数据包的服务类型(ToS)字段值
    if( 0 != setsockopt(sockfd, IPPROTO_IP, IP_RECVTOS, &on, sizeof(on) ) )
    {
        perror("fcntl");
        return -1;
    }
    
    struct sockaddr_in peer_addr;
    new_addr(&peer_addr, "127.0.0.1", 12345);// server addr
    struct sockaddr_in local_addr;
    local_addr.sin_family = AF_INET;
    local_addr.sin_addr.s_addr = INADDR_ANY; // will notice segmentfault without code of this line
    g_cl_client_ctx->local_addr = &local_addr;
    socklen_t socklen = sizeof(local_addr);
    if (0 != bind(sockfd, (struct sockaddr *) &local_addr, socklen))
    {
        perror("bind");
        return -1;
    }
    // }}

    //TODO: init ssl
    //init_ssl_ctx();

    // begin init quic
    struct lsquic_engine_settings   engine_settings;
    memset(&engine_settings, 0, sizeof(engine_settings));
    engine_settings.es_ecn      = LSQUIC_DF_ECN;
    lsquic_engine_init_settings(&engine_settings, 0);
    // 1
    if (0 != lsquic_global_init(LSQUIC_GLOBAL_CLIENT))
    {
        exit(EXIT_FAILURE);
    }

    struct lsquic_engine_api engine_api;
    memset(&engine_api, 0, sizeof(engine_api));
    // client must implement three functions of engine_api
    engine_api.ea_lookup_cert = cl_lookup_cert;
    engine_api.ea_stream_if     = &g_lsquic_stream_if;
    engine_api.ea_packets_out = cl_packets_out;
    // ea_packets_out_ctx对应ea_packets_out函数的第一个参数，就是你传的是什么句柄，到时候给你返回什么句柄，这个句柄你可以自定义类型
    engine_api.ea_packets_out_ctx = (void*)(uintptr_t)sockfd; // 64位系统typedef unsigned long int uintptr_t， 解决warnning, uintptr_t大小和指针大小才匹配
    // optional
    engine_api.ea_settings = &engine_settings;
    engine_api.ea_alpn = "echo";// 没发现有什么用

    PRINT("func:%s, line:%d\n", __func__, __LINE__);
    lsquic_engine_t *engine = lsquic_engine_new(0, &engine_api); // client mode
    g_cl_client_ctx->engine = engine;
    PRINT("func:%s, line:%d\n", __func__, __LINE__);
    // 2 , LSQVER_I001/LSQVER_I002
    lsquic_engine_connect(engine, LSQVER_I001, (struct sockaddr *) &local_addr, (struct sockaddr *) &peer_addr, 
                            (void *) &sockfd, NULL, NULL, 0, 
                            NULL, 0, NULL, 0);

    PRINT("func:%s, line:%d\n", __func__, __LINE__);
    // 3
    PRINT("func:%s, line:%d. lsquic_engine_process_conns \n", __func__, __LINE__);
    lsquic_engine_process_conns(engine);  // 4. will fire callback fucntion cl_packets_out
    int count = 30;
    int sleep_time = 1000; // 1ms
    do {
        usleep(sleep_time); // 1ms
        int ret = client_read_net_data(sockfd);
        if (ret < 0) {
            sleep_time = sleep_time*2;
        }
        else {
            sleep_time = 1000;
        }

        if (sleep_time >= 10*1000*1000) {
            break;
        }

        if (s_is_send_finished) {
            break;
        }

        if (s_is_lsq_hsk_ok) {
            usleep(1000); // 1ms
            PRINT("func:%s, line:%d. lsquic_stream_wantwrite 1\n", __func__, __LINE__);
            lsquic_stream_wantwrite(g_cl_client_ctx->stream_h->stream, 1); // 设置为1后，后面再调用lsquic_engine_process_conns，就会触发 on_write
            PRINT("func:%s, line:%d. lsquic_engine_process_conns\n", __func__, __LINE__);
            lsquic_engine_process_conns(g_cl_client_ctx->engine); // fire on_write
        }
        //count--;
    } while(count>0);

finish:
    PRINT("func:%s, line:%d. finish\n", __func__, __LINE__);
    lsquic_stream_shutdown(g_cl_client_ctx->stream_h->stream, 0);
    sleep(2);
    close(sockfd);
    lsquic_global_cleanup();
    return 0;
}