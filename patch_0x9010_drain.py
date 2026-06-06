#!/usr/bin/env python3
# library.c 0x9010 pull 루프에 same-handle 스톰 가드 적용 (탭 들여쓰기 보존).
import sys

F = "/Users/ini-k/CamCon/gphoto-build_16k/arm64-v8a/libgphoto2_src/camlibs/ptp2/library.c"
s = open(F).read()

if "camcon_at_last_handle" in s:
    print("이미 패치됨 — 중단")
    sys.exit(0)

# 1) 파일 스코프 static 변수 (camera_wait_for_event 정의 앞)
anchor = "static int\ncamera_wait_for_event (Camera *camera, int timeout,"
assert s.count(anchor) == 1, f"anchor count={s.count(anchor)}"
ins = ("/* CamCon: 0x9010 AdvancedTransfer same-handle storm guard state. */\n"
       "static uint32_t   camcon_at_last_handle = 0;\n"
       "static PTPParams *camcon_at_last_params = NULL;\n\n")
s = s.replace(anchor, ins + anchor, 1)

# 2) 0x9010 처리 블록 교체 (free(atdata) ~ path = NULL;)
dq = 'GP_LOG_D ("CamCon AdvancedTransfer: dequeued handle 0x%08x (atlen=%u)", handle, atlen);'
assert s.count(dq) == 1, f"dq count={s.count(dq)}"
i = s.index(dq)
ls = s.rfind('\n', 0, i) + 1
I = s[ls:i]                       # dequeued 라인의 들여쓰기(탭) — 원본에서 추출
free_ls = s.rfind('\n', 0, ls - 1) + 1   # 바로 앞 free(atdata) 라인 시작
endm = 'path = NULL;'
j = s.index(endm, i) + len(endm)
old_block = s[free_ls:j]
assert old_block.lstrip().startswith("free (atdata);"), repr(old_block[:40])

T = '\t'
new_block = (
    f"{I}free (atdata);\n"
    f"{I}atdata = NULL;\n"
    f"\n"
    f"{I}/* CamCon storm guard: 0x9010 only PEEKS the transfer queue head; the head is\n"
    f"{I} * consumed only when that handle is actually downloaded (gp_camera_file_get\n"
    f"{I} * -> 0x101b). Until then every poll returns the SAME handle, spinning this\n"
    f"{I} * loop at TCP-roundtrip speed (~33ms) and flooding logs. Suppress the repeat\n"
    f"{I} * by keeping the back-off growing and falling through to the timeout. A NEW\n"
    f"{I} * handle (burst a1->a2->a3) passes immediately so bursts stay lossless; the\n"
    f"{I} * params pointer scopes the guard to one PTP/IP session. */\n"
    f"{I}if (handle == camcon_at_last_handle && params == camcon_at_last_params) {{\n"
    f"{I}{T}GP_LOG_D (\"CamCon AdvancedTransfer: same handle 0x%08x still queued, backing off\", handle);\n"
    f"{I}}} else {{\n"
    f"{I}{T}GP_LOG_D (\"CamCon AdvancedTransfer: dequeued handle 0x%08x (atlen=%u)\", handle, atlen);\n"
    f"{I}{T}camcon_at_last_handle = handle;\n"
    f"{I}{T}camcon_at_last_params = params;\n"
    f"{I}{T}back_off_wait = 0;\n"
    f"{I}{T}C_MEM (path = calloc(1, sizeof(CameraFilePath)));\n"
    f"{I}{T}addret = add_object_to_fs_and_path (camera, handle, path, context);\n"
    f"{I}{T}if (addret >= GP_OK) {{\n"
    f"{I}{T}{T}*eventtype = GP_EVENT_FILE_ADDED;\n"
    f"{I}{T}{T}*eventdata = path;\n"
    f"{I}{T}{T}return GP_OK;\n"
    f"{I}{T}}}\n"
    f"{I}{T}GP_LOG_E (\"CamCon AdvancedTransfer: add_object 0x%08x failed (%d)\", handle, addret);\n"
    f"{I}{T}free (path);\n"
    f"{I}{T}path = NULL;\n"
    f"{I}}}"
)
assert s.count(old_block) == 1
s = s.replace(old_block, new_block, 1)

open(F, 'w').write(s)
print("패치 적용 완료")
print(f"  들여쓰기(탭 개수): {len(I)}")
print(f"  static 변수 + same-handle 가드 삽입")
