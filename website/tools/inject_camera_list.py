#!/usr/bin/env python3
"""지원 기종 정적 목록 주입기 — supported-cameras.json 을 index.html 에 HTML 로 미리 굽는다.

왜 필요한가 — 기종 목록을 main.js 가 런타임 fetch 로 그리면 HTML 원문에 기종명이 없어
"Nikon Z8 안드로이드 테더링" 같은 기종명 검색(SEO)에 잡히지 않는다. 이 스크립트가
목록 955행을 <li> 로 미리 넣어 두면, JS 는 로드 후 같은 형태로 다시 그리므로(검색·필터 포함)
화면 동작은 그대로이고 검색엔진과 JS 실패 상황만 좋아진다.

동작:
  1) <ul id="camList"> 안의 CAM-STATIC 마커 사이를 <li> 목록으로 교체 (JS 렌더와 같은 마크업)
  2) <span id="camTotal"> 의 자리표시자를 실제 총계 숫자로 교체

재실행해도 같은 결과가 나오는 멱등 스크립트다. JSON 을 재생성했다면 이 스크립트와
build_i18n_pages.py 를 순서대로 재실행해 index.html·dist-lang 을 함께 갱신해야 한다.

사용법:  python3 website/tools/inject_camera_list.py [--check]
  --check : 파일을 쓰지 않고 index.html 이 최신인지만 판정(낡았으면 exit 1)
"""
import html
import json
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
JSON_PATH = os.path.join(ROOT, "assets", "data", "supported-cameras.json")
INDEX_PATH = os.path.join(ROOT, "index.html")

BEGIN = "<!-- CAM-STATIC:BEGIN inject_camera_list.py 생성 구간 — 손으로 고치지 말 것 -->"
END = "<!-- CAM-STATIC:END -->"


def li_row(cam):
    """main.js renderCameras 의 행 마크업과 동일한 형태 — JS 가 덮어써도 화면이 변하지 않아야 한다."""
    badge = '<span class="cam-wifi">Wi-Fi</span>' if cam.get("wifi") else ""
    return (
        '<li><span class="cam-vendor">%s</span>'
        '<span class="cam-model">%s</span>%s</li>'
        % (html.escape(cam["vendor"]), html.escape(cam["model"]), badge)
    )


def main():
    check = sys.argv[1:] == ["--check"]
    if sys.argv[1:] and not check:
        print("사용법: python3 inject_camera_list.py [--check]")
        sys.exit(2)

    with open(JSON_PATH, encoding="utf-8") as f:
        data = json.load(f)
    total = data["total"]
    rows = "\n              ".join(li_row(c) for c in data["cameras"])

    with open(INDEX_PATH, encoding="utf-8") as f:
        src = f.read()

    # (1) 마커 사이 목록 교체
    pattern = re.compile(re.escape(BEGIN) + r".*?" + re.escape(END), re.DOTALL)
    if not pattern.search(src):
        sys.exit("index.html 에 CAM-STATIC 마커가 없다 — camList 마크업을 확인할 것")
    out = pattern.sub(BEGIN + "\n              " + rows + "\n              " + END, src)

    # (2) 총계 숫자 정적화 (JS 로드 전에도 '…' 가 아니라 실제 값이 보인다)
    out, n = re.subn(
        r'(<span class="num" id="camTotal">)[^<]*(</span>)',
        r"\g<1>%d\g<2>" % total, out)
    if n != 1:
        sys.exit("camTotal 자리를 찾지 못했다(%d건) — index.html 마크업을 확인할 것" % n)

    if check:
        if out != src:
            print("[실패] index.html 정적 목록이 supported-cameras.json 과 다르다.")
            print("       python3 tools/inject_camera_list.py 재실행 후 build_i18n_pages.py 도 재실행할 것.")
            sys.exit(1)
        print("검증: index.html 정적 목록 최신 OK (%d종)" % total)
        return

    with open(INDEX_PATH, "w", encoding="utf-8") as f:
        f.write(out)
    print("주입: index.html 정적 목록 %d종 + 총계 %d" % (len(data["cameras"]), total))


if __name__ == "__main__":
    main()
