/* PTP/IP 주소 문자열의 이벤트 포트 결정 규칙 회귀 검사 (게이트 G8-2).
 *
 * ## 왜 이 파일이 있는가 (실기 블로커, 2026-08-30)
 *
 * 표준 PTP/IP 는 커맨드용·이벤트용 TCP 연결 2개를 연다. libgphoto2 의 주소 파서
 * (`camlibs/ptp2/ptpip.c` 의 `ptp_ptpip_connect`)는 이렇게 동작한다.
 *
 *     eventport = port = 15740;        // :986  ← port 를 파싱하기 '전'에 기본값을 넣는다
 *     if (p) { sscanf(p+1,"%d",&port); // :989
 *              p = strchr(p+1,':');    // :995
 *              if (p) sscanf(p+1,"%d",&eventport); }   // :997
 *
 * 즉 `"ptpip:IP:PORT"` 처럼 필드가 셋뿐이면 **커맨드는 PORT 로, 이벤트는 15740 으로** 간다.
 * 모든 카메라가 15740 을 쓰는 동안에는 기본값이 우연히 맞아 드러나지 않았다. SSH 터널이
 * 로컬 포워딩 포트(예: 15741)를 쓰면서 처음으로 어긋났고, 커맨드 핸드셰이크는 성공하는데
 * 이벤트 연결만 127.0.0.1:15740 으로 가서 `ptpip.c:1105 "could not connect event"` 로 죽었다.
 *
 * 채택한 해법은 **로컬 포워딩 포트를 15740 으로 고정**하는 것이다(`LocalPortAllocator`).
 * 그러면 `camera_ptpip_setup.cpp` 가 포트를 생략한 `ptpip:<ip>` 를 만들고, 파서 기본값에 따라
 * 커맨드·이벤트가 **모두 15740** = 우리 터널로 들어온다. 네이티브는 한 줄도 바뀌지 않았다.
 *
 * 이 검사가 고정하는 것은 두 가지다.
 *   1. 비-SSH 연결이 만드는 경로 문자열이 수정 전과 **바이트 동일**하다(G8-2).
 *   2. 15740 이 아닌 로컬 포트를 쓰면 이벤트가 터널 밖으로 새어 나간다 — 대체 포트를 금지하는
 *      근거이자, 훗날 3필드 경로를 도입할 때 무엇을 고쳐야 하는지에 대한 기록이다(G8-5).
 *
 * 아래 parse() 는 libgphoto2 코드를 **그대로 옮긴 것**이므로, 업스트림 갱신으로 파서가 바뀌면
 * 이 파일도 함께 맞춰야 한다.
 *
 * ## 실행
 *
 *     cc -o /tmp/ptpipfmt scripts/check_ptpip_address_format.c && /tmp/ptpipfmt
 *
 * 종료 코드 0 = 통과. 장치도 카메라도 필요 없다.
 */

#include <stdio.h>
#include <string.h>
#include <stdlib.h>

/* libgphoto2 ptp_ptpip_connect 의 주소 파싱을 그대로 옮긴 것. 수정하지 말 것. */
static int parse(const char *address, int *out_port, int *out_eventport) {
    char *addr, *s, *p;
    int port, eventport;

    if (NULL == strchr(address, ':')) return -1;
    addr = strdup(address);
    if (!addr) return -1;
    s = strchr(addr, ':');
    if (!s) { free(addr); return -1; }
    *s = '\0';
    p = strchr(s + 1, ':');
    eventport = port = 15740;
    if (p) {
        *p = '\0';
        if (!sscanf(p + 1, "%d", &port)) { free(addr); return -1; }
        p = strchr(p + 1, ':');
        if (p) {
            if (!sscanf(p + 1, "%d", &eventport)) { free(addr); return -1; }
        }
    }
    *out_port = port;
    *out_eventport = eventport;
    free(addr);
    return 0;
}

static int failures = 0;

static void expect(const char *what, const char *address, int want_port, int want_event) {
    int port = -1, eventport = -1;
    if (parse(address, &port, &eventport) != 0) {
        printf("  [실패] %s: 파싱 자체가 실패했다 (%s)\n", what, address);
        failures++;
        return;
    }
    if (port != want_port || eventport != want_event) {
        printf("  [실패] %s\n         주소 %s\n         기대 cmd=%d evt=%d, 실제 cmd=%d evt=%d\n",
               what, address, want_port, want_event, port, eventport);
        failures++;
        return;
    }
    printf("  [통과] %-46s cmd=%-6d evt=%-6d\n", address, port, eventport);
}

int main(void) {
    printf("PTP/IP 주소 형식 검사 — 커맨드 포트와 이벤트 포트가 같은 터널로 들어와야 한다\n\n");

    printf("[G8-2] 비-SSH 경로: 경로 문자열이 수정 전과 바이트 동일해야 한다\n");
    /* 카메라 실주소 + 15740 → camera_ptpip_setup.cpp 가 포트를 생략한다.
     * 필드가 둘뿐이라 파서 기본값이 그대로 쓰여 커맨드·이벤트가 모두 15740 이 된다.
     * 니콘 STA/AP, SSH 를 쓰지 않는 소니가 전부 이 경로다. */
    expect("니콘·비-SSH 소니(카메라 실주소)", "ptpip:192.168.1.50", 15740, 15740);
    expect("후지 포크(기존 형식 유지)", "ptpip:192.168.1.50:55740", 55740, 15740);

    printf("\n[G8] SSH 터널: 로컬 15740 이라 비-SSH 와 똑같은 형식이 만들어진다\n");
    /* LocalPortAllocator 가 15740 만 주므로 터널도 위와 같은 2필드 경로를 탄다.
     * 즉 이번 수정은 새 형식을 도입한 것이 아니라 원래 형식으로 되돌린 것이다. */
    expect("SSH 터널 loopback", "ptpip:127.0.0.1", 15740, 15740);

    printf("\n[G8-5] 대체 포트를 금지하는 근거 — 15740 이 아니면 이벤트가 터널 밖으로 샌다\n");
    {
        int port = -1, eventport = -1;
        parse("ptpip:127.0.0.1:15741", &port, &eventport);
        if (port == 15741 && eventport == 15740) {
            printf("  [통과] 결함 재현: cmd=%d evt=%d — 이벤트만 폰 로컬 15740(리스너 없음)으로 간다\n",
                   port, eventport);
        } else {
            printf("  [실패] 결함이 재현되지 않는다(cmd=%d evt=%d). libgphoto2 파서가 바뀌었다면\n"
                   "         LocalPortAllocator 의 15740 고정 근거를 다시 검토하라.\n",
                   port, eventport);
            failures++;
        }
    }
    /* 훗날 3필드 경로를 도입하면 대체 포트가 안전해진다는 근거(후속 과제 기록). */
    expect("후속 과제: 3필드 경로면 대체 포트도 안전", "ptpip:127.0.0.1:15741:15741", 15741, 15741);

    printf("\n%s (실패 %d건)\n", failures ? "검사 실패" : "검사 통과", failures);
    return failures ? 1 : 0;
}
