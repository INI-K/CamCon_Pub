#!/usr/bin/env python3
"""CamCon i18n 백필 1단계: 기본(en 버킷 = values/)에 한국어 원문으로 박힌 <string> 키를
결정론적으로 추출한다. Android는 values*/의 모든 xml을 병합하므로 전 파일을 스캔한다.

산출: /tmp/i18n_problem.json  { count, keys:[{name, ko, file, present:{loc:bool}}] }
- name: string 리소스 이름
- ko:   현재 기본 파일에 박힌 한국어 원문(inner raw, 포맷인자/이스케이프 보존)
- file: 그 키가 정의된 values/ 하위 파일명(기본값 in-place 교체용)
- present: 각 로케일에 이미 그 키가 존재하는지(중복 추가 방지)
"""
import os, re, json, glob

RES = os.path.join(os.path.dirname(__file__), "..", "..", "app/src/main/res")
RES = os.path.abspath(RES)
LOCALES = ["ko", "ja", "zh", "de", "es", "fr", "it"]

STRING_RE = re.compile(r'<string\s+name="([^"]+)"([^>]*)>(.*?)</string>', re.DOTALL)
HANGUL = re.compile(r'[가-힣]')


def parse_dir(d):
    """values dir 하위 모든 xml에서 name->(inner, filebasename) (translatable=false 제외)"""
    out = {}
    for f in sorted(glob.glob(os.path.join(d, "*.xml"))):
        try:
            txt = open(f, encoding="utf-8").read()
        except Exception:
            continue
        for m in STRING_RE.finditer(txt):
            name, attrs, inner = m.group(1), m.group(2), m.group(3)
            if "translatable=\"false\"" in attrs:
                continue
            out[name] = (inner, os.path.basename(f))
    return out


def main():
    base = parse_dir(os.path.join(RES, "values"))
    locs = {loc: parse_dir(os.path.join(RES, f"values-{loc}")) for loc in LOCALES}

    problem = []
    for name, (inner, fname) in base.items():
        if HANGUL.search(inner):  # 기본값이 한국어 = 문제 키
            problem.append({
                "name": name,
                "ko": inner,
                "file": fname,
                "present": {loc: (name in locs[loc]) for loc in LOCALES},
            })

    problem.sort(key=lambda x: x["name"])
    out = {"count": len(problem), "keys": problem}
    json.dump(out, open("/tmp/i18n_problem.json", "w", encoding="utf-8"),
              ensure_ascii=False, indent=1)

    print(f"기본(values/) 총 키: {len(base)}")
    print(f"한국어 원문 문제 키: {len(problem)}")
    # 로케일별 이미 존재하는 문제키 수(=중복이라 새로 안 넣어도 됨)
    for loc in LOCALES:
        already = sum(1 for k in problem if k["present"][loc])
        print(f"  values-{loc}: 문제키 중 이미 존재 {already} / 신규 추가 필요 {len(problem)-already}")
    # 기본 파일 분포
    from collections import Counter
    c = Counter(k["file"] for k in problem)
    print("문제 키가 든 기본 파일 분포:")
    for f, n in c.most_common():
        print(f"  {f}: {n}")
    # 포맷 인자 포함 키 수(번역 시 보존 필요)
    fmt = sum(1 for k in problem if re.search(r'%(\d+\$)?[sd]', k["ko"]))
    print(f"포맷 인자(%s/%d) 포함 문제 키: {fmt}")


if __name__ == "__main__":
    main()
