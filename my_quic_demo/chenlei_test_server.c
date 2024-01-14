/*
  I am Server!
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
#include <unistd.h>
#include <fcntl.h>
#include <netinet/ip.h>
#include <error.h>


#include "lsquic.h"

#include <openssl/ssl.h>
#include <event2/event.h>
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

#define LISTEN_ADDR "192.168.20.60"

struct cl_client_ctx {
    struct lsquic_conn_ctx  *conn_h;
    struct lsquic_stream_ctx *stream_h;
    struct sockaddr_in *local_addr;
    lsquic_engine_t *engine;
    struct ssl_ctx_st   *ssl_ctx;
    int is_sender;
    int sockfd;
    struct event* ev_sock;
    struct event* prog_timer;
};

struct lsquic_conn_ctx {
    lsquic_conn_t       *conn;
    struct cl_client_ctx   *client_ctx;
};

struct lsquic_stream_ctx {
    lsquic_stream_t     *stream;
    struct cl_client_ctx   *client_ctx;
    struct event        *read_stdin_ev;
    char                 buf[4096];
    size_t               buf_used;   /* 已使用的buffer大小 */
};

typedef struct certs_map {
    char key[256];
    struct ssl_ctx_st* ce_ssl_ctx;
} certs_map_t;
certs_map_t* m_certs_map;

struct ssl_ctx_st   *g_ssl_ctx = NULL;
lsquic_conn_ctx_t* g_lsquic_conn_ctx;
struct cl_client_ctx* g_cl_client_ctx;
void client_read_local_data();
static char m_alpn[256] = {0};
static int totalBytes = 0;

static FILE* fp = NULL;
static void saveFile(char* buf, int size) 
{
    if (fp == NULL) {
        PRINT("open server_save_file failed!\n");
        return ;
    }
    fwrite(buf, sizeof(char), size, fp);
}

static lsquic_conn_ctx_t *
cl_server_on_new_conn (void *stream_if_ctx, lsquic_conn_t *conn)
{
    PRINT(">>>> func:%s, line:%d\n", __func__, __LINE__);
    lsquic_conn_ctx_t *conn_h = malloc(sizeof(*conn_h));
    conn_h->conn = conn;

    g_lsquic_conn_ctx = conn_h;
    g_lsquic_conn_ctx->client_ctx = (struct cl_client_ctx *)stream_if_ctx;
    g_cl_client_ctx->conn_h = g_lsquic_conn_ctx;
    /* Need a stream to send request */
    if (g_cl_client_ctx->is_sender) {
        PRINT("func:%s, line:%d. lsquic_conn_make_stream\n", __func__, __LINE__);
        lsquic_conn_make_stream(conn);
    }
    PRINT(">>>> func:%s, line:%d. end\n", __func__, __LINE__);
    return conn_h;
}

static void
cl_server_on_conn_closed (lsquic_conn_t *conn)
{
    PRINT(">>>> func:%s, line:%d\n", __func__, __LINE__);
    lsquic_conn_ctx_t *conn_h = lsquic_conn_get_ctx(conn);
    LSQ_NOTICE("Connection closed");
    lsquic_conn_set_ctx(conn, NULL);
    free(conn_h);
}

static lsquic_stream_ctx_t *
cl_server_on_new_stream (void *stream_if_ctx, lsquic_stream_t *stream)
{
    totalBytes = 0;
    fp = fopen("server_save_file.ts", "wb");
    PRINT(">>>> func:%s, line:%d. On new stream\n", __func__, __LINE__);
    lsquic_stream_ctx_t *st_h = calloc(1, sizeof(*st_h));
    st_h->stream = stream;
    st_h->client_ctx = stream_if_ctx;
    st_h->buf_used = 0;
    
    g_cl_client_ctx->stream_h = st_h;
    lsquic_stream_wantread(stream, 1);

    return st_h;
}

static void
cl_server_on_read (lsquic_stream_t *stream, lsquic_stream_ctx_t *st_h)
{
    PRINT(">>>> func:%s, line:%d\n", __func__, __LINE__);
    char buf[1500] = {0};
    size_t nr;

    nr = lsquic_stream_read(stream, buf, sizeof(buf));
    PRINT(">>>> func:%s, line:%d. Read from peer, nr:%ld\n", __func__, __LINE__, nr);
    if (0 == nr)
    {
        lsquic_stream_shutdown(stream, 2);
        //lsquic_conn_make_stream(tmpClientState->conn);
        return;
    }
    saveFile(buf, nr);
    totalBytes = totalBytes + nr;
    PRINT(">>>> func:%s, line:%d. totalBytes:%d\n", __func__, __LINE__, totalBytes);

    //lsquic_stream_wantread(stream, 0);
    //lsquic_stream_wantwrite(stream, 1);
    PRINT(">>>> func:%s, line:%d. cl_server_on_read end!\n", __func__, __LINE__);
}

static void
cl_server_on_write (lsquic_stream_t *stream, lsquic_stream_ctx_t *st_h)
{
    PRINT(">>>> func:%s, line:%d. stream:%p\n", __func__, __LINE__, stream);
#if 1//test_common
    if (strlen(st_h->buf) == 0) {
        strcpy(st_h->buf, "chenlei66666666666\n");
        st_h->buf_used = strlen(st_h->buf);
    }
#endif
    
    ssize_t nw = lsquic_stream_write(stream, st_h->buf, st_h->buf_used);
    PRINT("nw:%ld\n", nw);
    st_h->buf_used = 0;
    /* Here we make an assumption that we can write the whole buffer.
     * Don't do it in a real program.
     */

    lsquic_stream_flush(stream);// 触发packets_out
    lsquic_stream_wantwrite(stream, 0);
    //ev_io_start(tmpClientState->loop, &tmpClientState->read_local_data_ev);// 异步启动监听标准输入
    lsquic_stream_wantread(stream, 1);

    if (nw <= 0) {
        //client_read_local_data();
        PRINT("func:%s, line:%d. lsquic_stream_write. st_h->buf:[%s], len:%ld\n", __func__, __LINE__, st_h->buf, strlen(st_h->buf));
        //nw = lsquic_stream_write(stream, st_h->buf, strlen(st_h->buf));
        //PRINT("nw:%ld\n", nw);
        //st_h->buf_used = 0;
    }
}

static void
cl_server_on_hsk_done(lsquic_conn_t *conn, enum lsquic_hsk_status status)
{
    PRINT(">>>> func:%s, line:%d. end\n", __func__, __LINE__);
    //lsquic_conn_ctx_t *conn_h = (lsquic_conn_ctx_t *) lsquic_conn_get_ctx(conn);
    switch (status)
    {
        case LSQ_HSK_OK:
        case LSQ_HSK_RESUMED_OK:
            PRINT("handshake successful, start stdin watcher\n");
            //client_read_local_data();
            break;
        default:
            PRINT("handshake failed\n");
            break;
    }
}


static void
cl_server_on_close (lsquic_stream_t *stream, lsquic_stream_ctx_t *st_h)
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
    if (fp != NULL) {
        fclose(fp);
    }
}

const struct lsquic_stream_if g_lsquic_stream_if = {
    .on_new_conn            = cl_server_on_new_conn,
    .on_conn_closed         = cl_server_on_conn_closed,
    .on_new_stream          = cl_server_on_new_stream,
    .on_read                = cl_server_on_read,
    .on_write               = cl_server_on_write,
    .on_hsk_done            = cl_server_on_hsk_done,
    .on_close               = cl_server_on_close,
};


int select_alpn(SSL *ssl, const unsigned char **out, unsigned char *outlen,
                    const unsigned char *in, unsigned int inlen, void *arg)
{
    int r = 0;
    //quic_server_core* handle = (quic_server_core*)arg;

    PRINT("in [%s] inlen %d m_alpn [%s] m_alpn_size %ld\n", in, inlen, m_alpn, strlen(m_alpn));
    
    r = SSL_select_next_proto((unsigned char **) out, outlen, in, inlen,
                                    (unsigned char *)m_alpn, strlen(m_alpn));
    if (r == OPENSSL_NPN_NEGOTIATED)
        return SSL_TLSEXT_ERR_OK;
    else
    {
        PRINT("no supported protocol can be selected  %s\n", (char *) in);
        return SSL_TLSEXT_ERR_ALERT_FATAL;
    }
}

int init_ssl_ctx_map ()
{
    struct ssl_ctx_st* ce_ssl_ctx = SSL_CTX_new(TLS_method());
    if (!ce_ssl_ctx)
    {
        PRINT("init_ssl_ctx_map(). Cannot allocate SSL context\n");
        return -1;
    }

    int was = 0;
    char* key = m_alpn;
    SSL_CTX_set_min_proto_version(ce_ssl_ctx, TLS1_3_VERSION);
    SSL_CTX_set_max_proto_version(ce_ssl_ctx, TLS1_3_VERSION);
    SSL_CTX_set_alpn_select_cb(ce_ssl_ctx, select_alpn, NULL);
    SSL_CTX_set_default_verify_paths(ce_ssl_ctx);

    {
        const char *const s = getenv("LSQUIC_ENABLE_EARLY_DATA");
        if (!s || atoi(s))
            SSL_CTX_set_early_data_enabled(ce_ssl_ctx, 1);    /* XXX */
    }

   if ( 1 != SSL_CTX_use_certificate_chain_file(ce_ssl_ctx, "./ssh_key/cacert.pem") )
    {
        PRINT("SSL_CTX_use_certificate_chain_file failed\n");
        goto err_end;
    }

    if (1 != SSL_CTX_use_PrivateKey_file(ce_ssl_ctx, "./ssh_key/privkey.pem", SSL_FILETYPE_PEM))
    {
        PRINT("SSL_CTX_use_PrivateKey_file failed\n");
        goto err_end;
    }

    was = SSL_CTX_set_session_cache_mode(ce_ssl_ctx, 1);
    PRINT("set SSL session cache mode to 1 (was:%d)\n", was);

    strncpy(m_certs_map->key, key, strlen(key));
    m_certs_map->ce_ssl_ctx = ce_ssl_ctx;
    //memcpy(m_certs_map->ce_ssl_ctx, ce_ssl_ctx, sizeof(*ce_ssl_ctx));

    PRINT("init_ssl_ctx_map SUCCESS\n");
    
    return 0;

err_end:
    if (ce_ssl_ctx)
    {
        SSL_CTX_free(ce_ssl_ctx);
        ce_ssl_ctx = NULL;
    }
    return -1;
    
}

int init_ssl_ctx ()
{
    unsigned char ticket_keys[48] = {0};
    struct ssl_ctx_st* ce_ssl_ctx = SSL_CTX_new(TLS_method());
    if (!ce_ssl_ctx)
    {
        PRINT("init_ssl_ctx(), Cannot allocate SSL context\n");
        goto err_end;
    }
    g_ssl_ctx = ce_ssl_ctx;
    g_cl_client_ctx->ssl_ctx = g_ssl_ctx;

    SSL_CTX_set_min_proto_version(ce_ssl_ctx, TLS1_3_VERSION);
    SSL_CTX_set_max_proto_version(ce_ssl_ctx, TLS1_3_VERSION);
    SSL_CTX_set_default_verify_paths(ce_ssl_ctx);

    if (1 != SSL_CTX_set_tlsext_ticket_keys(ce_ssl_ctx, ticket_keys, sizeof(ticket_keys)))
    {
        PRINT("SSL_CTX_set_tlsext_ticket_keys failed\n");
        goto err_end;
    }

    PRINT("init_ssl_ctx SUCCESS\n");
    return 0;

err_end:
    if (ce_ssl_ctx)
    {
        SSL_CTX_free(ce_ssl_ctx);
        ce_ssl_ctx = NULL;
    }
    return -1;
}

// 必须返回一个可用的ssl_ctx，不然无法和client建立连接。sni参数对应lsquic_engine_connect的第七个参数
struct ssl_ctx_st* cl_lookup_cert(void *lsquic_cert_lookup_ctx, const struct sockaddr *local, const char *sni)
{
    PRINT(">>>> func:%s, line:%d. sni_str:%s\n", __func__, __LINE__, sni);
    //struct cl_client_ctx* handle = (struct cl_client_ctx*)lsquic_cert_lookup_ctx;

    struct ssl_ctx_st* ret = NULL;

    if(sni)
    {
        if (strcmp(m_certs_map->key, sni) == 0) {
            return m_certs_map->ce_ssl_ctx;
        }
        else
        {
            PRINT("1 Not found cert\n");
        }
    }
    else {
        return m_certs_map->ce_ssl_ctx;
    }
    PRINT("1 Not found cert 2\n");
    return ret;
}

struct ssl_ctx_st* cl_get_ssl_ctx(void *peer_ctx, const struct sockaddr *local)
{
    PRINT(">>>> func:%s, line:%d. peer_ctx:%p\n", __func__, __LINE__, peer_ctx);
    //struct cl_client_ctx* handle = (struct cl_client_ctx*)peer_ctx;
    //m_certs_map->ce_ssl_ctx;
    return g_cl_client_ctx->ssl_ctx;
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

void
cl_process_conns (struct cl_client_ctx *prog)
{
    
    PRINT("func:%s, line:%d. lsquic_engine_process_conns\n", __func__, __LINE__);
    int diff;
    struct timeval timeout;

    lsquic_engine_process_conns(prog->engine);

    if (lsquic_engine_earliest_adv_tick(prog->engine, &diff))
    {
        if (diff < 0
                /*|| (unsigned) diff < prog->prog_settings.es_clock_granularity*/)
        {
            timeout.tv_sec  = 0;
            timeout.tv_usec = 0;//prog->prog_settings.es_clock_granularity;
        }
        else
        {
            timeout.tv_sec = (unsigned) diff / 1000000;
            timeout.tv_usec = (unsigned) diff % 1000000;
        }
        PRINT("func:%s, line:%d. diff:%d\n", __func__, __LINE__, diff);
        //if (!prog_is_stopped())
            event_add(prog->prog_timer, &timeout);
    }
}

static void
process_conns_timer_handler (int fd, short what, void *arg)
{
    //if (!prog_is_stopped())
        cl_process_conns(arg);
}

static int process_conns_onley_once = 0;
int 
server_read_net_data(void* arg)
{
    PRINT("func:%s, line:%d. arg:%p\n", __func__, __LINE__, arg);
    struct cl_client_ctx* client_ctx = (struct cl_client_ctx*)arg;
    int sockfd = client_ctx->sockfd;
    
	int ret;
/*用libevent，这里就没用了
    fd_set rfds, efds;
	struct timeval  timeVal;
    FD_ZERO(&rfds);
	FD_SET(sockfd, &rfds);
	FD_ZERO(&efds);
	FD_SET(sockfd, &efds);

    long timeout = 2000;//ms
	timeVal.tv_sec = timeout / 1000;
	timeVal.tv_usec = (timeout % 1000) * 1000;
	if (timeout < 0)
	{
		ret = select(sockfd + 1, &rfds, NULL, &efds, NULL);
	}
	else
	{
		ret = select(sockfd + 1, &rfds, NULL, &efds, &timeVal);
	}
    if (ret < 0 || !FD_ISSET(sockfd, &rfds)) {
        PRINT("-1 == nread\n");
        return -1;
    }
*/
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
    
    struct sockaddr_storage* local_sas = (struct sockaddr_storage*)client_ctx->local_addr;
    // TODO handle ECN properly
    int ecn = 0;
    tut_proc_ancillary(&msg, local_sas, &ecn);
    PRINT("func:%s, line:%d. lsquic_engine_packet_in\n", __func__, __LINE__);
    ret = lsquic_engine_packet_in(client_ctx->engine, buf, nread,
                                (struct sockaddr *) local_sas,
                                (struct sockaddr *) &peer_sas,
                                NULL, ecn);
    //the fourth lsquic_engine_process_conns fire both on_new_stream and cl_packets_out
    if (ret != -1) {
        cl_process_conns(client_ctx);
    }
    return nread;
}

static void read_net_data(evutil_socket_t fd, short flags, void* arg) 
{
    server_read_net_data(arg);
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
#endif
    PRINT("func:%s, line:%d. stream_h->buf:[%s]\n", __func__, __LINE__, stream_h->buf);

    PRINT("func:%s, line:%d. lsquic_stream_wantwrite(stream_h->stream, 1)\n", __func__, __LINE__);
    lsquic_stream_wantwrite(stream_h->stream, 1);
    PRINT("func:%s, line:%d. lsquic_engine_process_conns\n", __func__, __LINE__);
    lsquic_engine_process_conns(g_cl_client_ctx->engine); 
}

int main(int argc, char** argv)
{
    PRINT("func:%s, line:%d\n", __func__, __LINE__);
    g_cl_client_ctx = malloc(sizeof(*g_cl_client_ctx));
    g_cl_client_ctx->is_sender = 0;
    m_certs_map = (certs_map_t*)malloc(sizeof(certs_map_t));
    //m_certs_map->ce_ssl_ctx = (struct ssl_ctx_t*)malloc(sizeof(struct ssl_ctx_t));

    // {{ create and init socket
    int sockfd = socket(AF_INET, SOCK_DGRAM, 0);
    if (-1 == sockfd) {
        PRINT("create socket failed!");
        return -1;
    }
    g_cl_client_ctx->sockfd = sockfd;

    if(0 != set_fd_nonblocking(sockfd) )
    // if(0 != set_fd_nonblocking(sockfd) )
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
    
    //struct sockaddr_in peer_addr;
    struct sockaddr_in local_addr;
    new_addr(&local_addr, LISTEN_ADDR, 12345);//
    g_cl_client_ctx->local_addr = &local_addr;
    socklen_t socklen = sizeof(local_addr);
    if (0 != bind(sockfd, (struct sockaddr *) &local_addr, socklen))
    {
        perror("bind");
        return -1;
    }
    // }}

#if 1
    //TODO: init ssl
    const char* alpn = "echo";
    size_t alpn_len = 0, all_len = 0;
    alpn_len  = strlen(alpn);
    m_alpn[all_len] = strlen(alpn);
    memcpy(&m_alpn[all_len + 1], alpn, strlen(alpn));
    m_alpn[all_len + 1 + alpn_len] = '\0';
    PRINT("m_alpn:%s\n", m_alpn);
    if (0 != init_ssl_ctx_map())
    {
        PRINT("init_ssl_ctx_map faile \n");
        return -1;
    }
    
    if (0 != init_ssl_ctx())
    {
        PRINT("init_ssl_ctx faile \n");
        return -1;
    }
#endif

    // begin init quic
    // init ssl
    struct lsquic_engine_settings   engine_settings;
    memset(&engine_settings, 0, sizeof(engine_settings));
    engine_settings.es_ecn      = LSQUIC_DF_ECN;
    lsquic_engine_init_settings(&engine_settings, LSQVER_I001);
    // 1
    if (0 != lsquic_global_init(LSQUIC_GLOBAL_SERVER | LSQUIC_GLOBAL_CLIENT))
    {
        exit(EXIT_FAILURE);
    }
    char err_buf[100] = {0};
    if (0 != lsquic_engine_check_settings(&engine_settings, LSQVER_I001, err_buf, sizeof(err_buf)))
    {
        PRINT("###### Error in settings: %s\n", err_buf);
        return -1;
    }

    struct lsquic_engine_api engine_api;
    memset(&engine_api, 0, sizeof(engine_api));
    // client must implement three functions of engine_api
    engine_api.ea_stream_if     = &g_lsquic_stream_if;
    engine_api.ea_packets_out = cl_packets_out;
    // ea_packets_out_ctx对应ea_packets_out函数的第一个参数，就是你传的是什么句柄，到时候给你返回什么句柄，这个句柄你可以自定义类型
    engine_api.ea_packets_out_ctx = (void*)(uintptr_t)sockfd; // 64位系统typedef unsigned long int uintptr_t， 解决warnning, uintptr_t大小和指针大小才匹配
    //server
    engine_api.ea_lookup_cert = cl_lookup_cert;
    engine_api.ea_get_ssl_ctx  = cl_get_ssl_ctx;
    engine_api.ea_cert_lu_ctx  = g_cl_client_ctx;
    // optional
    engine_api.ea_settings = &engine_settings;
    engine_api.ea_alpn = alpn;

    PRINT("func:%s, line:%d\n", __func__, __LINE__);
    lsquic_engine_t *engine = lsquic_engine_new(LSENG_SERVER, &engine_api); // client mode
    g_cl_client_ctx->engine = engine;

    PRINT("func:%s, line:%d. g_cl_client_ctx:%p\n", __func__, __LINE__, g_cl_client_ctx);
    // 1 创建base
    struct event_base* base = event_base_new();
    // 2 event_new
    struct event* ev_sock =    event_new(base, g_cl_client_ctx->sockfd, EV_READ|EV_PERSIST, read_net_data, g_cl_client_ctx);
    struct event* prog_timer = event_new(base, -1, 0, process_conns_timer_handler, g_cl_client_ctx);
    g_cl_client_ctx->prog_timer = prog_timer;
    g_cl_client_ctx->ev_sock = ev_sock;
    // 3 event_add
    event_add(ev_sock, NULL); //机顶盒可以设置2秒超时
    // 4, event_base_dispatch(base); //进入循环中
    PRINT("func:%s, line:%d. event_base_loop\n", __func__, __LINE__);
    event_base_loop(base, 0);
    // 5
    //event_base_loopbreak(); //强行退出


finish:
    PRINT("func:%s, line:%d. finish\n", __func__, __LINE__);
    lsquic_stream_shutdown(g_cl_client_ctx->stream_h->stream, 0);
    sleep(2);

    // 5 {{event_base_loopexit(); //最后一个时间回调函数执行完退出
    if (prog_timer)
    {
        event_del(prog_timer);
        event_free(prog_timer);
        prog_timer = NULL;
    }
    if (ev_sock)
    {
        event_del(ev_sock);
        event_free(ev_sock);
        ev_sock = NULL;
    }
    event_base_free(base);
    //}}

    close(sockfd);
    lsquic_global_cleanup();
    return 0;
}