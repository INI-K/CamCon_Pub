#!/usr/bin/env python3
# build_libgphoto2_android.sh 의 패치 적용 섹션을
# 외부 PATCH_SRC(파일 복사, ptp.c 누락) → repo 내 단일 .patch(git apply)로 교체.
import sys

F = "/Users/ini-k/CamCon/build_libgphoto2_android.sh"
s = open(F).read()

if "patches/camcon-libgphoto2.patch" in s:
    print("이미 개선됨 — 중단")
    sys.exit(0)

start = "# CamCon 커스텀 패치 적용 (로컬 수정 사항 반영)"
end = 'echo ">> 패치 적용 및 검증 완료"\nfi'
i = s.index(start)
j = s.index(end, i) + len(end)
old = s[i:j]

new = '''# CamCon 커스텀 패치 적용 — repo 내 단일 .patch (외부 PATCH_SRC 의존 제거)
# patches/camcon-libgphoto2.patch:
#   - library.c:  0x9010 AdvancedTransfer pull + same-handle 드레인 가드,
#                 find_child PTP 캐시 우선, capture_preview ChangeCameraMode 건너뜀
#   - ptp.c/ptp.h: ptp_nikon_advancedtransfer(0x9010) 구현/선언  ← 기존 cp 방식이 누락하던 핵심
#   - ptpip.c:    TCP 소켓 최적화
#   - gphoto2-filesys.c: lazy creation + 캐시 우선 + dirty 열거 스킵
# 어느 머신에서든 동일 재현: git clone master -> git apply 이 patch -> build.
PATCH_FILE="$BASE_DIR/patches/camcon-libgphoto2.patch"
if [ -f "$PATCH_FILE" ]; then
    echo ">> CamCon 패치 적용: $PATCH_FILE"
    if ! git -C "$BUILD_DIR/libgphoto2" apply --check "$PATCH_FILE" 2>/dev/null; then
        echo ""
        echo "ERROR: 패치가 현재 libgphoto2 master에 깨끗이 적용되지 않습니다."
        echo "  (master 업데이트로 충돌 시 patches/camcon-libgphoto2.patch 를 재생성하세요:"
        echo "   빌드트리 libgphoto2_src 에서 git diff > patches/camcon-libgphoto2.patch)"
        exit 1
    fi
    git -C "$BUILD_DIR/libgphoto2" apply "$PATCH_FILE"

    # 핵심 패치 반영 검증 — 누락 시 빌드 중단
    echo ">> 패치 검증 중..."
    patch_ok=true
    grep -q 'camcon_at_last_handle' "$BUILD_DIR/libgphoto2/camlibs/ptp2/library.c" \\
        && echo "  ✓ 0x9010 same-handle 드레인 가드" \\
        || { echo "  ✗ library.c 드레인 가드 누락!"; patch_ok=false; }
    grep -q 'ptp_nikon_advancedtransfer' "$BUILD_DIR/libgphoto2/camlibs/ptp2/ptp.c" \\
        && echo "  ✓ ptp_nikon_advancedtransfer(0x9010) 구현" \\
        || { echo "  ✗ ptp.c 구현 누락!"; patch_ok=false; }
    grep -q 'skipping ChangeCameraMode' "$BUILD_DIR/libgphoto2/camlibs/ptp2/library.c" \\
        && echo "  ✓ capture_preview ChangeCameraMode 건너뜀" \\
        || { echo "  ✗ ChangeCameraMode 패치 누락!"; patch_ok=false; }
    grep -q 'Skip full enumeration' "$BUILD_DIR/libgphoto2/libgphoto2/gphoto2-filesys.c" \\
        && echo "  ✓ gp_filesystem_append dirty 열거 스킵" \\
        || { echo "  ✗ gphoto2-filesys 패치 누락!"; patch_ok=false; }

    if [ "$patch_ok" = false ]; then
        echo ""
        echo "ERROR: 패치 검증 실패."
        exit 1
    fi
    echo ">> 패치 적용 및 검증 완료"
else
    echo "WARNING: $PATCH_FILE 없음 — 순정 master 빌드 (CamCon 패치 미적용)"
fi'''

assert s.count(old) == 1
s = s.replace(old, new, 1)
open(F, 'w').write(s)
print("빌드 스크립트 개선 완료: PATCH_SRC 파일복사 → patches/camcon-libgphoto2.patch git apply")
