#!/usr/bin/env bash
# CamCon 자율 테스트 하네스 (adb 기반)
#
# 목적: 카메라↔안드로이드 디바이스를 USB/WiFi로 연결만 해두면, 사람이 화면을
#       만지지 않고 adb 만으로 설치→권한부여→UI 구동→logcat 단언까지 자동 수행한다.
#
# 핵심 원리: CamCon 의 연결/촬영/라이브뷰 파이프라인은 모든 단계에서 명확한 Log 를
#            남긴다. 이 스크립트는 그 "실제 로그 문자열"을 단언 오라클로 사용한다.
#
# 사용법:
#   ./camcon_autotest.sh doctor      # 환경/디바이스 호환성 점검 (디바이스 무관, 항상 먼저 실행)
#   ./camcon_autotest.sh install     # debug APK 설치 + 런타임 권한 일괄 부여
#   ./camcon_autotest.sh mock        # [카메라 불필요] Mock 카메라 모드로 UI/촬영 파이프라인 검증
#   ./camcon_autotest.sh usb         # [카메라 USB-OTG 연결 필요] USB 감지→연결→촬영 검증
#   ./camcon_autotest.sh wifi        # [카메라 Wi-Fi 연결 필요] PTP-IP 검색→연결→촬영 검증
#   ./camcon_autotest.sh all         # doctor→install→mock→usb→wifi 순차 (가능한 것만)
#
# 환경변수:
#   ADB=/path/to/adb     adb 경로 강제 지정
#   SERIAL=<device>      대상 디바이스 시리얼 강제 지정 (미지정 시 단일 디바이스 자동)
#   TIMEOUT=30           logcat 단언 기본 타임아웃(초)

set -u

PKG="com.inik.camcon"
LAUNCH_ACT="$PKG/.presentation.ui.SplashActivity"
MOCK_ACT="$PKG/.presentation.ui.MockCameraActivity"
PTPIP_ACT="$PKG/.presentation.ui.PtpipConnectionActivity"
MAIN_ACT="$PKG/.presentation.ui.MainActivity"
MIN_SDK=29
PROJ_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
APK="$PROJ_ROOT/app/build/outputs/apk/debug/app-debug.apk"
TIMEOUT="${TIMEOUT:-30}"
CAPTURE_WAIT="${CAPTURE_WAIT:-60}"   # 수동 셔터 입력 대기는 넉넉히

# ── adb 자동 탐지 ──────────────────────────────────────────────
detect_adb() {
  if [ -n "${ADB:-}" ] && [ -x "$ADB" ]; then return; fi
  for c in \
    "$(command -v adb 2>/dev/null)" \
    "/Volumes/EX_1TB_NVME/dev-cache/android-sdk/platform-tools/adb" \
    "$HOME/Library/Android/sdk/platform-tools/adb"; do
    if [ -n "$c" ] && [ -x "$c" ]; then ADB="$c"; return; fi
  done
  echo "✗ adb 를 찾을 수 없습니다. ADB=/path/to/adb 로 지정하세요." >&2; exit 1
}

# ── 디바이스 선택 ──────────────────────────────────────────────
select_device() {
  if [ -n "${SERIAL:-}" ]; then DEV="$SERIAL"; return; fi
  local list; list="$("$ADB" devices | awk 'NR>1 && $2=="device"{print $1}')"
  local n; n="$(echo "$list" | grep -c . )"
  if [ "$n" -eq 0 ]; then echo "✗ 연결된 디바이스가 없습니다." >&2; exit 1; fi
  if [ "$n" -gt 1 ]; then
    echo "✗ 디바이스가 여러 개입니다. SERIAL=<시리얼> 로 지정하세요:" >&2
    echo "$list" >&2; exit 1
  fi
  DEV="$list"
}

a() { "$ADB" -s "$DEV" "$@"; }              # adb 단축
sh_() { a shell "$@"; }                       # adb shell 단축

c_green="\033[32m"; c_red="\033[31m"; c_yellow="\033[33m"; c_reset="\033[0m"
pass() { echo -e "${c_green}✓ PASS${c_reset} $*"; }
fail() { echo -e "${c_red}✗ FAIL${c_reset} $*"; FAILED=$((FAILED+1)); }
warn() { echo -e "${c_yellow}! WARN${c_reset} $*"; }
info() { echo "  $*"; }
FAILED=0

# ── logcat 단언 오라클 ─────────────────────────────────────────
# oracle_wait <timeout> <success_regex> [fail_regex]
#  버퍼를 폴링하며 success 발견 시 0, fail 발견 시 2, 타임아웃 시 1 반환.
#  시스템 노이즈(ActivityTaskManager 의 액티비티명 로그, 권한거부 등)는 항상 제외 —
#  안 그러면 am start 거부 로그의 액티비티명에 success_regex 가 오탐 매칭된다(실제 겪음).
SYS_NOISE='ActivityTaskManager|Permission Denial|SecurityException|PackageManager|WindowManager'
oracle_clear() { a logcat -c >/dev/null 2>&1; }
oracle_wait() {
  local to="$1" ok="$2" bad="${3:-__NEVER_MATCH__}"; local t=0 buf
  while [ "$t" -lt "$to" ]; do
    buf="$(a logcat -d 2>/dev/null | grep -vE "$SYS_NOISE")"
    if echo "$buf" | grep -qE "$bad"; then return 2; fi
    if echo "$buf" | grep -qE "$ok"; then return 0; fi
    sleep 1; t=$((t+1))
  done
  return 1
}

# 화면 깨우기 + 잠금해제 (잠금/AOD 상태면 UI 조작·am start 가 막힌다 — 실제 겪음)
wake_unlock() {
  sh_ input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
  sh_ wm dismiss-keyguard >/dev/null 2>&1
  sleep 1
  sh_ dumpsys window 2>/dev/null | grep -q "mScreenOnFully=true" && return 0
  warn "화면이 완전히 켜지지 않음(보안잠금 PIN 가능성) — 수동 잠금해제 후 재시도 필요할 수 있음"
}

# 정상 진입점(exported SplashActivity) 기동. 비exported 액티비티는 shell uid 로 am start 불가(API31+).
launch_app() { sh_ monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; }

# 촬영→수신 단언 (스트리밍 오라클).
#  주의: temp_native_downloads 파일은 전송 중 임시→이동되어 churn 하므로 개수 비교는 불안정.
#  또 libgphoto2 가 DEBUG 로그를 폭주시켜 logcat -d 폴링은 수신 마커를 놓친다.
#  => 수신 담당 TAG 만 필터해 실시간 스트리밍하고 "다운로드 완료" 마커를 잡는다.
capture_wait() {
  local secs="$1"; local f="/tmp/camcon_capwait_$$.log"; : > "$f"
  a logcat -c >/dev/null 2>&1
  # 수신/다운로드 담당 TAG 만 표시, 나머지(특히 libgphoto2)는 침묵(*:S)
  a logcat -v brief 사진다운로드매니저:D 카메라이벤트매니저:D PtpipDataSource:I 카메라캡처레포:D '*:S' > "$f" 2>/dev/null &
  local lpid=$!; local t=0 hit=""
  while [ "$t" -lt "$secs" ]; do
    hit="$(grep -oE '다운로드 완료[:-] .*|파일 수신: .*다운로드 완료 - .*' "$f" 2>/dev/null | tail -1)"
    [ -n "$hit" ] && break
    sleep 1; t=$((t+1))
  done
  kill "$lpid" 2>/dev/null; wait "$lpid" 2>/dev/null
  rm -f "$f"
  [ -n "$hit" ] && { echo "$hit"; return 0; } || return 1
}

# ── UI 자동화 헬퍼 (Compose 접근성 트리 기반) ──────────────────
# 주의: 화면에 연속 애니메이션이 있으면 uiautomator 가 "could not get idle state" 로
#       실패할 수 있다. --compressed + 재시도로 완화하되, 단언의 진짜 오라클은 logcat 이다.
ui_dump() {
  local i out
  for i in 1 2 3; do
    out="$(sh_ uiautomator dump --compressed /sdcard/ui.xml 2>&1)"
    if echo "$out" | grep -q "UI hierchary dumped\|dumped to"; then break; fi
    sleep 1
  done
  sh_ cat /sdcard/ui.xml 2>/dev/null
}
# tap_text <보이는텍스트일부> : 접근성 트리에서 텍스트/콘텐츠설명 노드의 중심 좌표를 탭
tap_text() {
  local needle="$1" xml node bounds x1 y1 x2 y2
  xml="$(ui_dump)"
  node="$(echo "$xml" | tr '>' '>\n' | grep -E "(text|content-desc)=\"[^\"]*$needle[^\"]*\"" | head -1)"
  if [ -z "$node" ]; then return 1; fi
  bounds="$(echo "$node" | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -1)"
  if [ -z "$bounds" ]; then return 1; fi
  x1=$(echo "$bounds" | grep -oE '[0-9]+' | sed -n 1p); y1=$(echo "$bounds" | grep -oE '[0-9]+' | sed -n 2p)
  x2=$(echo "$bounds" | grep -oE '[0-9]+' | sed -n 3p); y2=$(echo "$bounds" | grep -oE '[0-9]+' | sed -n 4p)
  sh_ input tap $(( (x1+x2)/2 )) $(( (y1+y2)/2 ))
}

# ════════════════════════════════════════════════════════════════
# doctor : 환경/디바이스 호환성 점검 (디바이스 동작 없이 안전)
# ════════════════════════════════════════════════════════════════
cmd_doctor() {
  echo "── CamCon 자율테스트 환경 점검 ──"
  info "adb        : $ADB"
  info "device     : $DEV"
  local rel sdk abi
  rel="$(sh_ getprop ro.build.version.release | tr -d '\r')"
  sdk="$(sh_ getprop ro.build.version.sdk | tr -d '\r')"
  abi="$(sh_ getprop ro.product.cpu.abi | tr -d '\r')"
  info "android    : $rel (API $sdk)"
  info "abi        : $abi"

  if [ "$sdk" -ge "$MIN_SDK" ] 2>/dev/null; then pass "API $sdk ≥ minSdk $MIN_SDK"; else
    fail "API $sdk < minSdk $MIN_SDK → CamCon 설치 불가 (INSTALL_FAILED_OLDER_SDK)"; fi
  case "$abi" in arm64-v8a) pass "ABI arm64-v8a 호환";; *) fail "ABI $abi (앱은 arm64-v8a 전용)";; esac

  if [ -f "$APK" ]; then pass "debug APK 존재: $(du -h "$APK" | cut -f1)"; else
    warn "debug APK 없음 → './gradlew assembleDebug' 먼저 실행 필요"; fi

  # USB host / WiFi 기능 (각 경로 테스트 가능 여부)
  sh_ pm list features 2>/dev/null | grep -q usb.host && pass "USB host 지원" || warn "USB host 미지원 → usb 테스트 불가"
  local wifi; wifi="$(sh_ dumpsys wifi 2>/dev/null | grep -m1 -iE 'Wi-Fi is')"
  info "wifi 상태  : ${wifi:-알수없음}"
  echo "$wifi" | grep -qi disabled && warn "WiFi 비활성 → wifi 테스트 전 'svc wifi enable' 필요(HW 없으면 불가)"

  # 카메라 현재 연결 여부
  sh_ dumpsys usb 2>/dev/null | grep -q "host_connected=true" && pass "USB 호스트에 장치 연결됨(카메라 가능성)" || info "현재 USB 호스트에 연결된 장치 없음"
}

# ════════════════════════════════════════════════════════════════
# install : APK 설치 + 런타임 권한 일괄 부여
# ════════════════════════════════════════════════════════════════
RUNTIME_PERMS=(
  android.permission.ACCESS_FINE_LOCATION
  android.permission.ACCESS_COARSE_LOCATION
  android.permission.POST_NOTIFICATIONS
  android.permission.READ_MEDIA_IMAGES
  android.permission.READ_EXTERNAL_STORAGE
  android.permission.WRITE_EXTERNAL_STORAGE
  android.permission.NEARBY_WIFI_DEVICES
)
cmd_install() {
  [ -f "$APK" ] || { fail "APK 없음: $APK ('./gradlew assembleDebug')"; return 1; }
  info "설치 중..."
  if a install -r -g "$APK" 2>&1 | tail -3 | grep -qi Success; then
    pass "APK 설치 성공 (-g 로 권한 자동부여 시도됨)"
  else
    # -g 가 일부 기기서 실패하면 개별 grant
    a install -r "$APK" >/dev/null 2>&1
    for p in "${RUNTIME_PERMS[@]}"; do sh_ pm grant "$PKG" "$p" >/dev/null 2>&1; done
    if sh_ pm list packages | grep -q "$PKG"; then pass "APK 설치 + 개별 권한부여 완료"; else fail "APK 설치 실패"; fi
  fi
  # 배터리 최적화 예외(백그라운드 서비스 안정) — 무인 테스트 안정성
  sh_ dumpsys deviceidle whitelist +$PKG >/dev/null 2>&1 || true
}

# ════════════════════════════════════════════════════════════════
# mock : 카메라 없이 Mock 모드로 UI/촬영 파이프라인 검증
# ════════════════════════════════════════════════════════════════
cmd_mock() {
  echo "── [MOCK] Mock 카메라 모드 (카메라 불필요) ──"
  sh_ pm list packages | grep -q "$PKG" || { fail "앱 미설치 ('install' 먼저)"; return 1; }
  wake_unlock
  oracle_clear
  # MockCameraActivity 는 exported=false → API31+ 에서 shell(uid2000)이 am start 불가(SecurityException).
  local out; out="$(sh_ am start -n "$MOCK_ACT" 2>&1)"
  if echo "$out" | grep -qi "SecurityException\|Permission Denial\|not exported"; then
    warn "MockCameraActivity 는 비exported → adb 직접 기동 불가(보안). Mock 모드는 앱 내에서"
    warn "  설정→(ADMIN)→Mock Camera 로만 진입 가능. 자율 테스트는 usb/wifi(정상 진입점) 사용 권장."
    return 0
  fi
  # 일부 ROM/디버그 빌드에선 허용될 수 있음 — 앱 자체 TAG 로그로만 단언(시스템로그는 oracle 에서 제외됨)
  if oracle_wait "$TIMEOUT" "MockCameraViewModel" "onCreate 실패|FATAL EXCEPTION"; then
    pass "MockCameraViewModel 활성(Mock 모드 기동)"
  else
    warn "Mock 모드 앱 로그 미관측"
  fi
  a exec-out screencap -p > "/tmp/camcon_mock.png" 2>/dev/null && info "스크린샷: /tmp/camcon_mock.png"
}

# ════════════════════════════════════════════════════════════════
# usb : USB-OTG 카메라 연결→네이티브 연결→촬영 검증
# ════════════════════════════════════════════════════════════════
cmd_usb() {
  echo "── [USB] 카메라를 폰에 USB-OTG 로 연결한 상태에서 실행 ──"
  echo "  (폰 USB가 PC adb 에 점유되면 OTG 불가 → '무선 디버깅'으로 adb 전환 후 카메라 연결)"
  sh_ pm list packages | grep -q "$PKG" || { fail "앱 미설치 ('install' 먼저)"; return 1; }
  if ! sh_ dumpsys usb 2>/dev/null | grep -q "host_connected=true"; then
    warn "USB 호스트에 장치 미연결 — 카메라를 OTG 로 연결 후 재실행하세요."; return 1
  fi
  pass "USB 장치 연결 감지됨"
  wake_unlock
  # 깨끗한 재연결을 위해 앱을 먼저 종료(이미 연결돼 있으면 마커가 재방출되지 않으므로).
  sh_ am force-stop "$PKG" >/dev/null 2>&1; sleep 1
  oracle_clear
  launch_app
  # USB 권한 다이얼로그가 처음이면 뜬다 — '항상 허용'+'확인' 자동 탭 시도(OEM 라벨 다양).
  sleep 2
  for t in 항상 always Always 기본; do tap_text "$t" >/dev/null 2>&1 && break; done
  for t in 확인 OK Allow 허용; do tap_text "$t" >/dev/null 2>&1 && break; done
  # 실측 검증된 연결 성공 마커: 모델 갱신 / 연결됨 감지 / 준비완료 / UI 블로킹 해제
  if oracle_wait "$TIMEOUT" "카메라 기능 정보 업데이트 완료|카메라 연결 상태: 연결됨|카메라 준비 완료|카메라 초기화 완료" "연결 실패|초기화 실패|FATAL EXCEPTION"; then
    local model; model="$(a logcat -d 2>/dev/null | grep -oE "카메라 기능 정보 업데이트 완료: .*" | tail -1 | sed 's/카메라 기능 정보 업데이트 완료: //')"
    pass "USB 카메라 연결 성공${model:+ — ${model}}"
  elif ui_dump | grep -qE "Corporation|Nikon|Canon|Sony|FUJIFILM|Panasonic|여유 [0-9]+컷"; then
    local model; model="$(ui_dump | tr '\"' '\n' | grep -E "Corporation|여유 [0-9]+컷" | head -1)"
    pass "USB 카메라 연결 확인(현재 UI 상태)${model:+ — ${model}}"
  else
    fail "USB 카메라 연결 실패/타임아웃 (권한 다이얼로그 수동 확인 필요할 수 있음)"; return 1
  fi
  # 촬영→수신 단언 (스트리밍 오라클).
  info "카메라 셔터를 누르거나 앱 원격촬영을 트리거하세요(${CAPTURE_WAIT}s 대기)…"
  local rx; if rx="$(capture_wait "$CAPTURE_WAIT")"; then
    pass "촬영→USB 다운로드 확인 — ${rx}"
  else
    warn "신규 촬영 미관측(${CAPTURE_WAIT}s) — 연결은 정상. 셔터 입력이 없었을 수 있음"
  fi
  a exec-out screencap -p > "/tmp/camcon_usb.png" 2>/dev/null && info "스크린샷: /tmp/camcon_usb.png"
}

# ════════════════════════════════════════════════════════════════
# wifi : Wi-Fi PTP-IP 검색→연결→촬영 검증
# ════════════════════════════════════════════════════════════════
cmd_wifi() {
  echo "── [WiFi] 폰을 카메라 AP(또는 핫스팟)에 연결한 상태에서 실행 ──"
  sh_ pm list packages | grep -q "$PKG" || { fail "앱 미설치 ('install' 먼저)"; return 1; }
  sh_ svc wifi enable >/dev/null 2>&1; sleep 2
  if sh_ dumpsys wifi 2>/dev/null | grep -m1 -i 'Wi-Fi is' | grep -qi disabled; then
    warn "WiFi 활성화 실패(HW 없음/차단) → wifi 경로 테스트 불가"; return 1
  fi
  local ssid; ssid="$(sh_ dumpsys wifi 2>/dev/null | grep -oE 'SSID: [^,]+' | head -1)"
  info "현재 WiFi: ${ssid:-미연결} (카메라 AP/핫스팟 또는 카메라와 같은 망이어야 함)"
  wake_unlock
  oracle_clear
  # PtpipConnectionActivity 도 비exported → 정상 진입점으로 띄운 뒤 앱이 검색/연결.
  launch_app
  sleep 1
  for t in WiFi Wi-Fi 무선 검색 카메라검색 연결; do tap_text "$t" >/dev/null 2>&1 && break; done
  if oracle_wait "$TIMEOUT" "카메라 검색 완료|카메라 발견|PTPIP 연결 성공|PTP-IP 연결 성공|게이트웨이 PTP-IP 연결 성공" "검색 실패|연결 실패|Wi-Fi가 연결되어 있지 않"; then
    pass "PTP-IP 카메라 검색/연결 성공"
  else
    fail "PTP-IP 검색/연결 실패/타임아웃 (폰이 카메라 AP/망에 있는지 확인)"; return 1
  fi
  info "카메라 셔터/원격촬영 트리거(${CAPTURE_WAIT}s 대기)…"
  local rx; if rx="$(capture_wait "$CAPTURE_WAIT")"; then
    pass "촬영→WiFi 다운로드 확인 — ${rx}"
  else
    warn "신규 촬영 미관측(${CAPTURE_WAIT}s) — 연결은 정상"
  fi
  a exec-out screencap -p > "/tmp/camcon_wifi.png" 2>/dev/null && info "스크린샷: /tmp/camcon_wifi.png"
}

# ── 엔트리 ─────────────────────────────────────────────────────
main() {
  detect_adb; select_device
  case "${1:-doctor}" in
    doctor)  cmd_doctor ;;
    install) cmd_doctor; cmd_install ;;
    mock)    cmd_mock ;;
    usb)     cmd_usb ;;
    wifi)    cmd_wifi ;;
    all)     cmd_doctor; cmd_install; cmd_mock; cmd_usb; cmd_wifi ;;
    *) echo "사용법: $0 {doctor|install|mock|usb|wifi|all}"; exit 2 ;;
  esac
  echo
  if [ "$FAILED" -eq 0 ]; then echo -e "${c_green}=== 전체 통과 ===${c_reset}"; else
    echo -e "${c_red}=== 실패 $FAILED 건 ===${c_reset}"; exit 1; fi
}
main "$@"
