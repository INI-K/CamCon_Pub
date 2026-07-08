#!/usr/bin/env python3
"""CamCon i18n 백필 2·3단계.

  split : /tmp/i18n_problem.json(283 문제키)을 N개 청크(/tmp/i18n_in_XX.json)로 분할.
          각 청크는 번역 에이전트가 Read로 읽어 /tmp/i18n_out_XX.json 에 결과를 쓴다.
  apply : 에이전트 산출(/tmp/i18n_out_*.json) + /tmp/i18n_problem.json 을 합쳐
          - 기본(values/) 283키의 한국어 값을 영어로 in-place 교체
          - values-ko 에 없는 196키를 원문(한국어) 그대로 백필
          - values-ja/zh/de/es/fr/it 에 없는 196키를 번역으로 백필
          로케일별 신규 키는 strings_zzz_i18n_backfill.xml 새 파일에 모아 추가(중복/기존파일 오염 방지).

번역 산출 스키마(/tmp/i18n_out_XX.json): {"translations":[{"name","en","ja","zh","de","es","fr","it"}]}
"""
import os, re, json, sys, glob

RES = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "app/src/main/res"))
LOCALES = ["ko", "ja", "zh", "de", "es", "fr", "it"]
TRANS_LOCALES = ["ja", "zh", "de", "es", "fr", "it"]  # ko는 원문 복사
N_CHUNKS = 16
PROBLEM = "/tmp/i18n_problem.json"


def esc(s):
    """평문 번역 텍스트 → Android XML <string> inner. %s/%1$s/%d, \\n은 그대로."""
    s = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    s = s.replace('"', '\\"').replace("'", "\\'")
    s = s.replace("\r\n", "\\n").replace("\r", "\\n").replace("\n", "\\n")
    return s


def load_problem():
    return json.load(open(PROBLEM, encoding="utf-8"))["keys"]


def cmd_split():
    keys = load_problem()
    # 번역 입력은 name+ko만
    chunks = [[] for _ in range(N_CHUNKS)]
    for i, k in enumerate(keys):
        chunks[i % N_CHUNKS].append({"name": k["name"], "ko": k["ko"]})
    for i, c in enumerate(chunks):
        p = f"/tmp/i18n_in_{i:02d}.json"
        json.dump({"chunk": i, "keys": c}, open(p, "w", encoding="utf-8"),
                  ensure_ascii=False, indent=1)
    print(f"{len(keys)}키를 {N_CHUNKS}청크로 분할: /tmp/i18n_in_00.json ~ /tmp/i18n_in_{N_CHUNKS-1:02d}.json")
    print(f"청크당 약 {len(keys)//N_CHUNKS}~{len(keys)//N_CHUNKS+1}키")


def load_translations():
    tr = {}
    for p in sorted(glob.glob("/tmp/i18n_out_*.json")):
        d = json.load(open(p, encoding="utf-8"))
        for t in d.get("translations", []):
            tr[t["name"]] = t
    return tr


def replace_base_value(fpath, name, new_inner):
    txt = open(fpath, encoding="utf-8").read()
    pat = re.compile(r'(<string\s+name="' + re.escape(name) + r'"[^>]*>)(.*?)(</string>)', re.DOTALL)
    new_txt, n = pat.subn(lambda m: m.group(1) + new_inner + m.group(3), txt, count=1)
    if n != 1:
        return False
    open(fpath, "w", encoding="utf-8").write(new_txt)
    return True


def write_backfill(locale, entries):
    """entries: list[(name, inner_already_escaped)] → values-{locale}/strings_zzz_i18n_backfill.xml"""
    d = os.path.join(RES, f"values-{locale}")
    os.makedirs(d, exist_ok=True)
    p = os.path.join(d, "strings_zzz_i18n_backfill.xml")
    lines = ['<?xml version="1.0" encoding="utf-8"?>',
             '<!-- 2.0 릴리스 i18n 백필: 기본(en)에 한국어로 박혀있던 키의 로케일 번역 -->',
             "<resources>"]
    for name, inner in entries:
        lines.append(f'    <string name="{name}">{inner}</string>')
    lines.append("</resources>\n")
    open(p, "w", encoding="utf-8").write("\n".join(lines))
    return p, len(entries)


def cmd_apply():
    keys = load_problem()
    tr = load_translations()
    missing = [k["name"] for k in keys if k["name"] not in tr]
    if missing:
        print(f"[중단] 번역 누락 {len(missing)}키 (예: {missing[:8]}) — 번역 워크플로 재확인 필요")
        sys.exit(1)
    # 각 언어 필드 완비 검증
    incomplete = []
    for k in keys:
        t = tr[k["name"]]
        for lang in ["en"] + TRANS_LOCALES:
            if not t.get(lang) or not str(t[lang]).strip():
                incomplete.append((k["name"], lang))
    if incomplete:
        print(f"[중단] 번역 필드 비어있음 {len(incomplete)}건 (예: {incomplete[:8]})")
        sys.exit(1)

    # 1) 기본(values/) 283키: 한국어 → 영어 in-place
    by_file = {}
    for k in keys:
        by_file.setdefault(k["file"], []).append(k)
    base_ok = 0
    base_fail = []
    for fname, ks in by_file.items():
        fp = os.path.join(RES, "values", fname)
        for k in ks:
            if replace_base_value(fp, k["name"], esc(tr[k["name"]]["en"])):
                base_ok += 1
            else:
                base_fail.append(k["name"])
    print(f"기본(en) in-place 교체: 성공 {base_ok} / 실패 {len(base_fail)}")
    if base_fail:
        print(f"  실패 키(예): {base_fail[:8]}")
        sys.exit(1)

    # 2) values-ko: 없는 키를 원문(한국어) 그대로 (raw inner는 이미 XML 이스케이프됨)
    ko_entries = [(k["name"], k["ko"]) for k in keys if not k["present"]["ko"]]
    p, n = write_backfill("ko", ko_entries)
    print(f"values-ko 백필: {n}키 → {p}")

    # 3) ja/zh/de/es/fr/it: 없는 키를 번역으로
    for loc in TRANS_LOCALES:
        entries = [(k["name"], esc(tr[k["name"]][loc])) for k in keys if not k["present"][loc]]
        p, n = write_backfill(loc, entries)
        print(f"values-{loc} 백필: {n}키 → {p}")

    print("적용 완료. 이제 assembleRelease + lint(MissingTranslation)로 검증하세요.")


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    if cmd == "split":
        cmd_split()
    elif cmd == "apply":
        cmd_apply()
    else:
        print("usage: i18n_backfill.py [split|apply]")
        sys.exit(2)
