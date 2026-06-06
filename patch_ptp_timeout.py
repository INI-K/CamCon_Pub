#!/usr/bin/env python3
# library.c:6952 camera_wait_for_event Nikon 경로의
#   C_PTP_REP (ptp_check_event (params));
# 를 STA WTU 모드(PTP/IP+Nikon AdvancedTransfer+비라이브뷰)에서 PTP_ERROR_TIMEOUT을
# 비치명적으로 처리하도록 교체한다. (전송할 게 없을 때 GetEvent Timeout이 -10으로
# camera_wait_for_event를 죽여 앱이 '연결 끊김'으로 오인하던 문제 해결)
import sys

F = "/Users/ini-k/CamCon/gphoto-build_16k/arm64-v8a/libgphoto2_src/camlibs/ptp2/library.c"
lines = open(F).read().split('\n')

if any('camcon_check_event_timeout' in l or 'cevt == PTP_ERROR_TIMEOUT' in l for l in lines):
    print("이미 패치됨 — 중단")
    sys.exit(0)

# camera_wait_for_event(6948~) do-while(6951) 맨 위의 6952 라인을 라인번호로 특정.
# (동일 텍스트가 다른 함수에도 있으므로 위치 6951(0-index)±몇 줄에서 탐색)
target = None
for i in range(6945, 6960):
    if i < len(lines) and 'C_PTP_REP (ptp_check_event (params));' in lines[i]:
        target = i
        break
assert target is not None, "C_PTP_REP (ptp_check_event (params)) 6952 부근에서 못 찾음"

line = lines[target]
indent = line[:len(line) - len(line.lstrip('\t'))]
T = '\t'
new = [
    indent + "{",
    indent + T + "/* camcon_check_event_timeout: STA(PC connection) WTU 모드에선 전송할 게 없을 때",
    indent + T + " * GetEvent가 PTP_ERROR_TIMEOUT(0x02fa)을 내지만 이는 연결 끊김이 아니라 '이벤트",
    indent + T + " * 없음'이다. 기존 C_PTP_REP는 이를 치명적으로 보고 -10을 return해 아래 0x9010",
    indent + T + " * AdvancedTransfer 전송큐 pull에 도달하지 못하고 앱이 '연결 끊김'으로 오인했다.",
    indent + T + " * PTP/IP + Nikon AdvancedTransfer + 비라이브뷰일 때만 Timeout을 무시하고 0x9010",
    indent + T + " * pull로 진행한다. 그 외(USB/타벤더/라이브뷰/Timeout이 아닌 실제 에러)는 기존대로 전파. */",
    indent + T + "uint16_t cevt = ptp_check_event (params);",
    indent + T + "if (cevt != PTP_RC_OK) {",
    indent + T + T + "if ((camera->port->type == GP_PORT_PTPIP) &&",
    indent + T + T + "    !params->inliveview &&",
    indent + T + T + "    ptp_operation_issupported(params, PTP_OC_NIKON_AdvancedTransfer) &&",
    indent + T + T + "    cevt == PTP_ERROR_TIMEOUT) {",
    indent + T + T + T + "GP_LOG_D (\"CamCon: GetEvent timeout (no event) on STA WTU - continue to 0x9010 pull\");",
    indent + T + T + "} else {",
    indent + T + T + T + "C_PTP_REP (cevt);",
    indent + T + T + "}",
    indent + T + "}",
    indent + "}",
]
lines[target] = '\n'.join(new)
open(F, 'w').write('\n'.join(lines))
print(f"패치 적용 완료 (line {target+1}, 들여쓰기 {len(indent)}탭)")
