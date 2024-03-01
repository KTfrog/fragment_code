/*
  I am Client!
*/

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/queue.h>
#include <sys/time.h>
#include <pthread.h>
#include <assert.h>
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

#define LOG_PRINT_TIMES   200
static int log_times = LOG_PRINT_TIMES;
#define PRINT(fmt, ...) if (log_times>0) {log_times--;printf("%s   " fmt, get_cur_time(), ##__VA_ARGS__);}
#define PRINTD(fmt, ...) printf("%s   " fmt, get_cur_time(), ##__VA_ARGS__)
#define LOGI(fmt, ...) printf("%s   " fmt, get_cur_time(), ##__VA_ARGS__)
#define LOGE(fmt, ...) printf("%s   "fmt" :%s\n", get_cur_time(), ##__VA_ARGS__, strerror(errno))
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
#define RING_BUFFER_NODE_NUM 10000
#define MTU_MAX 2000
#define RTP_SIZE 1316

struct cl_client_ctx {
    struct lsquic_conn_ctx  *conn_h;
    struct lsquic_stream_ctx *stream_h;
    struct sockaddr_in *local_addr;
    lsquic_engine_t *engine;
    int is_sender;
    int sockfd;
    struct event_base* base;
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
    char                 buf[8192];
    size_t               buf_used;   /* 已使用的buffer大小 */
};

// 定义链表节点结构体
typedef struct Node {
    char buf[MTU_MAX];
    size_t buffer_size; /* write_buf_used 已使用的buffer大小 */
    struct Node *next;
} Node;

// 环形缓冲区结构体定义（使用Node作为单位）
typedef struct RingBuffer {
    Node *head;      // 头结点
    Node *tail;      // 尾结点
    size_t capacity; // 总节点数（即总缓冲区大小除以MTU_MAX）
} RingBuffer;

lsquic_conn_ctx_t* g_lsquic_conn_ctx;
struct cl_client_ctx* g_cl_client_ctx;
static int s_is_lsq_hsk_ok = 0;
//static int s_is_send_finished = 0;
static FILE *fp = NULL;
void client_read_local_data();
void client_prog_stop();

static Node s_all_nodes[RING_BUFFER_NODE_NUM];
static RingBuffer* g_rb = NULL;

// 初始化环形缓冲区
void ring_buffer_init(RingBuffer* rb, Node* nodes, size_t num_nodes) {
    if (num_nodes == 0 || nodes == NULL) return;
    
    rb->capacity = num_nodes;
    rb->head = rb->tail = nodes;

    PRINT("1 ring_buffer_init(), rb->head->buffer_size:%lu\n", rb->head->buffer_size);
    //PRINT(">>ring_buffer_init(), i=%lu, &nodes[i]:%p, nodes+i:%p\n", i, &nodes[i], nodes+i);
    for (size_t i = 0; i < num_nodes - 1; i++) {
        nodes[i].next = &nodes[i + 1];
        nodes[i].buffer_size = 0;
    }
    nodes[num_nodes - 1].next = &nodes[0]; // 链成一个循环链表
}

// 判断环形缓冲区是否为空
int ring_buffer_is_empty(const RingBuffer* rb) {
    //return rb->head->buffer_size == 0 && rb->head == rb->tail;
    return rb->head->buffer_size == 0 || rb->head == rb->tail;
}

// 判断环形缓冲区是否已满
int ring_buffer_is_full(const RingBuffer* rb) {
    return !ring_buffer_is_empty(rb) && rb->tail->next == rb->head;
}

// 写入数据到环形缓冲区
int ring_buffer_write(RingBuffer* rb, const void* data, size_t data_size) {
    //if (data_size > MTU_MAX || ring_buffer_is_full(rb)) return false;

    //PRINT("ring_buffer_write(), data_size:%lu\n", data_size);
    //TODO:如果写满了怎么办,先加个断言
    if (ring_buffer_is_full(rb)) {
        PRINTD("ring_buffer_is_full !!");
    }
    assert(!ring_buffer_is_full(rb));
    Node *current = rb->tail;// tail = write point
    if (data_size > 0) {
        size_t copy_size = MTU_MAX - current->buffer_size;
        if (copy_size > data_size) copy_size = data_size;
        
        memcpy(current->buf, data, copy_size);
        current->buffer_size = copy_size;
        rb->tail = current->next;
    }

    return 1;
}

// 从环形缓冲区读取数据
size_t ring_buffer_read(RingBuffer* rb, void* dest, size_t dest_size) {
    if (rb == NULL || dest_size > MTU_MAX || ring_buffer_is_empty(rb)) return 0;

    Node *current = rb->head; // head = read point
    if (current->buffer_size == 0) {
        return 0;
    }

    size_t copy_size = current->buffer_size;
    if (dest_size > 0) {
        if (copy_size > dest_size) {
            copy_size = dest_size;
        }

        memcpy(dest, current->buf, copy_size);

        current->buffer_size = 0;
        rb->head = current->next;
    }

    return copy_size;
}


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
    if (g_cl_client_ctx->is_sender) {
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
    
    if (g_cl_client_ctx->is_sender) {
        PRINT(">>>> func:%s, line:%d. lsquic_stream_wantwrite(stream, 1)\n", __func__, __LINE__);
        lsquic_stream_wantwrite(stream, 1); // 设置为1后，后面再调用lsquic_engine_process_conns，就会触发 on_write
    }
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
    //PRINT(">>>> func:%s, line:%d. stream:%p\n", __func__, __LINE__, stream);
    static char static_buf[MTU_MAX] = {0};
    static size_t num_bytes_sent_so_far = 0;
    static size_t num_bytes_remaining_to_send = 0;
    char* buf = static_buf;
    size_t buf_size = 0;

    // 上一个RTP buf没发完
    if (num_bytes_remaining_to_send > 0) {
        buf = &static_buf[num_bytes_sent_so_far];
        buf_size = num_bytes_remaining_to_send;
    }
    else {
        buf_size = ring_buffer_read(g_rb, buf, MTU_MAX);
    }

    PRINT(">>>> func:%s, line:%d. buf_size:%ld\n", __func__, __LINE__, buf_size);
    //assert (buf_size == 0 || buf_size == RTP_SIZE);//最后一个包既不是0也不是1316
    if (buf_size > 0) {
        ssize_t nw = lsquic_stream_write(stream, buf, buf_size);
        totalBytes = totalBytes + nw;
        PRINT(">>>> func:%s, line:%d. nw:%zd, totalBytes:%d\n", __func__, __LINE__, nw, totalBytes);
        //nw为0的情况，整个RTP包丢了是可以接受的，不会导致整个流乱掉
        //nw>0但是又不等于buf_size情况，要把剩余的buf重新发一下
        //只有程序开始的时候有这种情况buf_size != nw，后面nw只有0
        if (nw <= 0) {
            PRINT(">>>> func:%s, line:%d. buf_size:%ld, nw:%zd\n", __func__, __LINE__, buf_size, nw);
            return ;//break;
        }
        else if (nw < buf_size) {
            PRINTD(">>>> func:%s, line:%d. buf_size:%ld, nw:%zd\n", __func__, __LINE__, buf_size, nw);
            num_bytes_sent_so_far = num_bytes_sent_so_far + nw;
            num_bytes_remaining_to_send = buf_size - nw;
        }
        else {
            // flush就是表示可以发送了，但是并不是前面write的每个buf就会被打包成一个个packet
            // 感觉这些buf最终都还是存在engine统一的缓存里，发送的时候并不会按一个个的buf，一个packet这样发送
            // 而是全部的buf总体，再切割成一个个的UDP发送
            num_bytes_sent_so_far = 0;
            num_bytes_remaining_to_send = 0;
            int ret = lsquic_stream_flush(stream);
            assert(ret == 0);
        }
    }
    else {
        return;//break;
    }
    //PRINT("totalBytes:%d\n", totalBytes);

    //lsquic_stream_wantwrite(stream, 0); // 多次调用也只能触发一次packets_out
    //lsquic_stream_wantread(stream, 1);
    // end 触发packets_out
    //PRINT(">>>> func:%s, line:%d. end!!!!\n", __func__, __LINE__);
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
    //PRINT("func:%s, line:%d. n=%d\n", __func__, __LINE__, n);
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
    //PRINT("func:%s, line:%d. \n", __func__, __LINE__);
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
prog_process_conns (struct cl_client_ctx *prog)
{
    
    PRINT("func:%s, line:%d. lsquic_engine_process_conns\n", __func__, __LINE__);
    int diff;
    struct timeval timeout;

    lsquic_engine_process_conns(prog->engine);

    // diff = 当前时间 - tick触发时间
    // diff<=0则说明可以立即执行; diff > 0则说明还未到触发时机
    if (lsquic_engine_earliest_adv_tick(prog->engine, &diff))
    {
        if (diff < 0
                || (unsigned) diff < LSQUIC_DF_CLOCK_GRANULARITY)
        {
            timeout.tv_sec  = 0;
            timeout.tv_usec = LSQUIC_DF_CLOCK_GRANULARITY;//prog->prog_settings.es_clock_granularity;
        }
        else
        {
            timeout.tv_sec = (unsigned) diff / 1000000;
            timeout.tv_usec = (unsigned) diff % 1000000;
        }
        PRINT("func:%s, line:%d. diff:%d\n\n", __func__, __LINE__, diff);
        if (prog->prog_timer)
            event_add(prog->prog_timer, &timeout);//之前的timout会被覆盖，不用担心会有多个timer
    }
}

static void
process_conns_timer_handler (int fd, short what, void *arg)
{
    //if (!prog_is_stopped())
        prog_process_conns(arg);
}

void client_prog_stop() {
#if 1
    event_base_loopbreak(g_cl_client_ctx->base);
#else
    if (g_cl_client_ctx->ev_sock) {
        event_del(g_cl_client_ctx->ev_sock);
        event_free(g_cl_client_ctx->ev_sock);
        g_cl_client_ctx->ev_sock = NULL;
    }
    if (g_cl_client_ctx->prog_timer) {
        event_del(g_cl_client_ctx->prog_timer);
        event_free(g_cl_client_ctx->prog_timer);
        g_cl_client_ctx->prog_timer = NULL;
    }
#endif
}

//int client_read_net_data(int sockfd, long timeout_us) 
int 
client_read_net_data(void* arg)
{
    PRINT("func:%s, line:%d. arg:%p\n", __func__, __LINE__, arg);
    struct cl_client_ctx* client_ctx = (struct cl_client_ctx*)arg;
    int sockfd = client_ctx->sockfd;

/*用libevent，这里就没用了
    if (timeout_us > 0) {
        fd_set rfds, efds;
        int ret;
        struct timeval  timeVal;
        FD_ZERO(&rfds);
        FD_SET(sockfd, &rfds);
        FD_ZERO(&efds);
        FD_SET(sockfd, &efds);

        long timeout = timeout_us;//us
        timeVal.tv_sec = timeout / (1000*1000);
        timeVal.tv_usec = timeout % (1000*1000);
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
        client_prog_stop();
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
    
    struct sockaddr_storage* local_sas = (struct sockaddr_storage*)client_ctx->local_addr;
    // TODO handle ECN properly
    int ecn = 0;
    tut_proc_ancillary(&msg, local_sas, &ecn);
    PRINT("func:%s, line:%d. lsquic_engine_packet_in\n", __func__, __LINE__);
    lsquic_engine_packet_in(client_ctx->engine, buf, nread,
                                (struct sockaddr *) local_sas,
                                (struct sockaddr *) &peer_sas,
                                NULL, ecn);

    //first to third lsquic_engine_process_conns  fire cl_packets_out
    //the fourth lsquic_engine_process_conns fire both on_new_stream and hsk_done
	
    prog_process_conns(client_ctx);     


    return nread;
}

static void read_net_data(evutil_socket_t fd, short flags, void* arg) 
{
    client_read_net_data(arg);
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

void* thread_function(void* arg)
{
    char buf[MTU_MAX];
    int numBytes = 0;
    while ((numBytes = fread(buf, sizeof(char), RTP_SIZE, fp)) > 0) {
        if (numBytes != RTP_SIZE) {
            PRINTD("write to ringbuffer:%d\n", numBytes);
        }
        //nw = lsquic_stream_write(stream, st_h->buf, st_h->buf_used);
        ring_buffer_write(g_rb, buf, numBytes);
        usleep(1300);
    }
    PRINTD("文件已全部发送完毕！\n");  
#if 0
    struct stat fileStat;
    if (fstat(fileno(fp), &fileStat) == -1) {  
        LOGI("获取文件状态失败\n");  
        return ;  
    }
    if (totalBytes == fileStat.st_size) {
        //LOGI("文件已全部发送完毕！totalBytes:%d\n", totalBytes);  
        s_is_send_finished = 1;
        //client_prog_stop();
        return;
    }
    fseek(fp, totalBytes, SEEK_SET);
#endif
    return NULL;
}
void read_file_thread()
{
    // 创建线程属性
    pthread_attr_t attr;
    if (pthread_attr_init(&attr) != 0) {
        perror("Error initializing thread attributes");
        exit(EXIT_FAILURE);
    }

    // 创建并启动一个新线程
    pthread_t thread_id;
    if (pthread_create(&thread_id, &attr, thread_function, NULL) != 0) {
        perror("Error creating thread");
        exit(EXIT_FAILURE);
    }
    PRINT("func:%s, line:%d. create thread ok \n", __func__, __LINE__);
}

int log_buf(void *logger_ctx, const char *buf, size_t len)
{
    struct cl_client_ctx* cl_client_ctx = (struct cl_client_ctx *)logger_ctx;
    printf("logbuf:%s\n", buf);
    return 0;
}

int main(int argc, char** argv)
{
    PRINT("func:%s, line:%d\n", __func__, __LINE__);
    g_cl_client_ctx = malloc(sizeof(*g_cl_client_ctx));
    g_cl_client_ctx->is_sender = 1;

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
    new_addr(&peer_addr, LISTEN_ADDR, 12345);// server addr
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
    g_cl_client_ctx->sockfd = sockfd;

#if 0
    // init log level
    static struct lsquic_logger_if m_logger_if; // 没有static，程序会崩溃
    m_logger_if.log_buf = log_buf;
    lsquic_logger_init(&m_logger_if, (void*)g_cl_client_ctx, LLTS_YYYYMMDD_HHMMSSUS);//LLTS_YYYYMMDD_HHMMSSUS,LLTS_CHROMELIKE
#endif
    // begin init quic
    struct lsquic_engine_settings   engine_settings;
    memset(&engine_settings, 0, sizeof(engine_settings));
    lsquic_engine_init_settings(&engine_settings, LSQVER_I001);
    engine_settings.es_ecn      = LSQUIC_DF_ECN;
    engine_settings.es_cc_algo  = 1; //1:  Cubic   2:  BBRv1
    // 1
    if (0 != lsquic_global_init(LSQUIC_GLOBAL_CLIENT))
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
    // optional
    engine_api.ea_settings = &engine_settings;
    engine_api.ea_alpn = "echo";// 没发现有什么用,因为server端写死了

    PRINT("func:%s, line:%d\n", __func__, __LINE__);
    lsquic_engine_t *engine = lsquic_engine_new(0, &engine_api); // client mode
    g_cl_client_ctx->engine = engine;

#if READ_LOCAL_FILE
    // 打开文件这些初始化代码应该放在别的位置
    fp = fopen("iptv_hd_mini.ts" , "r");
    if (fp == NULL) {
        perror("fopen failed!");
        return -1;
    }
#endif

    RingBuffer rb;
    g_rb = &rb;
    ring_buffer_init(g_rb, s_all_nodes, RING_BUFFER_NODE_NUM);
    read_file_thread();

    
    LOGI("func:%s, line:%d. begin\n", __func__, __LINE__);
    // 2 , LSQVER_I001/LSQVER_I002
    lsquic_engine_connect(engine, LSQVER_I001, (struct sockaddr *) &local_addr, (struct sockaddr *) &peer_addr, 
                            (void *) &sockfd, NULL, NULL, 0, 
                            NULL, 0, NULL, 0);
    PRINT("func:%s, line:%d\n", __func__, __LINE__);
    // 3
    prog_process_conns(g_cl_client_ctx);  // 4. will fire callback fucntion cl_packets_out

    PRINT("func:%s, line:%d. g_cl_client_ctx:%p\n", __func__, __LINE__, g_cl_client_ctx);
    // 1 创建base
    struct event_base* base = event_base_new();
    g_cl_client_ctx->base = base;
    // 2 event_new
    struct event* ev_sock =    event_new(base, g_cl_client_ctx->sockfd, EV_READ|EV_PERSIST, read_net_data, g_cl_client_ctx);
    struct event* prog_timer = event_new(base, -1, 0, process_conns_timer_handler, g_cl_client_ctx);
    g_cl_client_ctx->prog_timer = prog_timer;
    g_cl_client_ctx->ev_sock = ev_sock;
    // 3 event_add
    struct timeval timeout;
    timeout.tv_sec = 10; // 秒为单位
    timeout.tv_usec = 0; // 微秒为单位
    event_add(ev_sock, &timeout); //机顶盒可以设置2秒超时

    // 4, event_base_dispatch(base); //进入循环中
    PRINT("func:%s, line:%d. event_base_loop\n", __func__, __LINE__);
    event_base_loop(base, 0);

finish:
    PRINTD("func:%s, line:%d. finish. totalBytes:%d\n", __func__, __LINE__, totalBytes);
    if (fp) {
        fclose(fp);
    }
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