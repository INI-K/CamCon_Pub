#!/usr/bin/env python3
"""
supported-cameras.json 생성기 — libgphoto2 소스 트리의 camlib 모델 테이블을 파싱한다.

CamCon의 arm64-v8a 빌드에 실제 동봉된 19개 camlib만 대상으로 한다:
  ax203 canon digigr8 dimagev directory jl2005a jl2005c kodak_dc240 mars
  pentax ptp2 quicktake1x0 ricoh_g3 sierra sonix sq905 st2205 topfield tp6801

사용법:
  python3 generate_supported_cameras.py <camlibs_dir> [-o out.json]

  <camlibs_dir> 예:
    /Users/ini-k/build_LibG/gphoto-build_16k/arm64-v8a/libgphoto2_src/camlibs

출력 구조:
  {
    "generated": "libgphoto2 camlibs parse",
    "total": <int>,
    "cameras": [ {"vendor": "Nikon", "model": "Z8", "connection": "usb"}, ... ]
  }

주의: 모델명(카메라 기종명)은 원문(영문)을 그대로 유지한다.
"""
import os
import re
import sys
import json
import glob
import argparse

# ── 파싱 대상 camlib과 소스 파일 위치(camlibs_dir 기준 상대 경로) ──
# directory 는 로컬 폴더 브라우징용 의사(pseudo) 카메라라 제외한다.
CAMLIBS = {
    "ptp2":         ["ptp2/library.c"],
    "canon":        ["canon/canon.c"],
    "sierra":       ["sierra/sierra.c"],
    "ricoh":        ["ricoh/library.c"],
    "pentax":       ["pentax/library.c"],
    "kodak_dc240":  ["kodak/dc240/dc240.c"],
    "digigr8":      ["digigr8/library.c"],
    "jl2005a":      ["jl2005a/library.c"],
    "jl2005c":      ["jl2005c/library.c"],
    "mars":         ["mars/library.c"],
    "sonix":        ["sonix/library.c"],
    "sq905":        ["sq905/library.c"],
    "ax203":        ["ax203/library.c"],
    "tp6801":       ["tp6801/library.c"],
    "st2205":       ["st2205/library.c"],
    "quicktake1x0": ["quicktake1x0/quicktake1x0.c"],
    "dimagev":      ["minolta/dimagev/dimagev.c"],
    "topfield":     ["topfield/puppy.c"],
}

KNOWN_VENDORS = {
    "Apple", "Nikon", "Canon", "Sony", "Fujifilm", "Fuji", "Panasonic",
    "Olympus", "Leica", "Pentax", "Ricoh", "Kodak", "Minolta", "Casio",
    "Sanyo", "Epson", "Sigma", "Samsung", "Agfa", "Toshiba", "Topfield",
}

VENDOR_ALIASES = {
    "Fuji": "Fujifilm",
    "Konica Minolta": "Minolta",
    "Sea & Sea": "Sea&Sea",
}

# ptpip 을 제외한 camlib 는 전부 USB 유선.
def read(path):
    with open(path, encoding="utf-8", errors="ignore") as fh:
        return fh.read()


def strip_mode_parens(name):
    """(PTP mode) (normal mode) 같은 접속모드 괄호 주석을 정리한다. (WLAN)은 유지."""
    prev = None
    while prev != name:
        prev = name
        name = re.sub(
            r"\s*\((?:[^()]*\b(?:PTP|MTP|normal|alternate|tethered|PC|mode|Storage|MSC)\b[^()]*)\)\s*$",
            "",
            name,
            flags=re.IGNORECASE,
        ).rstrip()
    return name


def add(bucket, vendor, model, connection):
    vendor = VENDOR_ALIASES.get(vendor, vendor).strip()
    model = model.strip()
    # 모델명이 제조사명으로 시작하면 중복 접두를 제거 (예: Fujifilm:"Fujifilm X-T5" → "X-T5")
    if vendor and model.lower().startswith(vendor.lower() + " "):
        model = model[len(vendor) + 1:].strip()
    if not model:
        return
    bucket.add((vendor, model, connection))


def parse_ptp2(txt, bucket):
    # USB 모델 테이블: {"Vendor:Model", 0xVID, 0xPID, flags}
    for m in re.finditer(r'\{\s*"([^"]+)"\s*,\s*0x[0-9a-fA-F]+\s*,\s*0x[0-9a-fA-F]+', txt):
        name = strip_mode_parens(m.group(1))
        if ":" in name:
            v, mo = name.split(":", 1)
        else:
            v, mo = "기타", name
        add(bucket, v, mo, "usb")
    # ptpip_models[]: Wi-Fi/PTP-IP 일반 항목 {"Name", FLAGS}
    block = re.search(r"ptpip_models\[\]\s*=\s*\{(.*?)\};", txt, re.DOTALL)
    if block:
        for m in re.finditer(r'\{\s*"([^"]+)"', block.group(1)):
            name = m.group(1).strip()
            first = name.split()[0]
            if first in ("Nikon", "Canon", "Fuji", "Ricoh", "Sony", "Fujifilm"):
                add(bucket, first, name[len(first):].strip(), "wifi")
            else:
                add(bucket, "PTP/IP", name, "wifi")


def parse_canon(txt, bucket):
    # {"Canon:PowerShot A5", CANON_CLASS_3, ...}
    for m in re.finditer(r'\{\s*"(Canon:[^"]+)"\s*,\s*CANON_CLASS', txt):
        name = strip_mode_parens(m.group(1))
        v, mo = name.split(":", 1)
        add(bucket, v, mo, "usb")


def parse_sierra(txt, bucket):
    # {"Vendor", "Model", SIERRA_MODEL_..., ...}
    for m in re.finditer(r'\{\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*SIERRA_MODEL', txt):
        add(bucket, m.group(1), m.group(2), "usb")


def parse_ricoh(txt, bucket):
    # {RICOH_MODEL_x, "Vendor:Model"}
    for m in re.finditer(r'\{\s*RICOH_[A-Z0-9_]+\s*,\s*"([^"]+)"', txt):
        name = m.group(1)
        if ":" in name:
            v, mo = name.split(":", 1)
        else:
            v, mo = "Ricoh", name
        add(bucket, v, mo, "usb")


def parse_pentax(txt, bucket):
    # strcpy(a.model, "Pentax:K1")
    for m in re.finditer(r'strcpy\s*\(\s*a\.model\s*,\s*"([^"]+)"', txt):
        name = m.group(1)
        if ":" in name:
            v, mo = name.split(":", 1)
        else:
            v, mo = "Pentax", name
        add(bucket, v, mo, "usb")


def parse_toy_table(txt, bucket, default_vendor):
    # {"Model Name", GP_DRIVER_STATUS_..., 0xVID, 0xPID}
    for m in re.finditer(r'\{\s*"([^"]+)"\s*,\s*GP_DRIVER_STATUS', txt):
        name = strip_mode_parens(m.group(1))
        v, mo = split_vendor(name, default_vendor)
        add(bucket, v, mo, "usb")


def split_vendor(name, fallback):
    """콜론형 → vendor:model, 아니면 첫 단어가 알려진 제조사면 분리, 그 외 fallback."""
    if ":" in name:
        return tuple(name.split(":", 1))
    first = name.split()[0] if name.split() else ""
    if first in KNOWN_VENDORS:
        rest = name[len(first):].strip()
        return (first, rest or name)
    return (fallback, name)


def parse_hex_table(txt, bucket, connection="usb"):
    # {"Vendor:Model", 0xVID, 0xPID, ...}  — ptp2 / kodak dc240 공용
    for m in re.finditer(r'\{\s*"([^"]+)"\s*,\s*0x[0-9a-fA-F]+\s*,\s*0x[0-9a-fA-F]+', txt):
        name = strip_mode_parens(m.group(1))
        v, mo = split_vendor(name, "기타")
        add(bucket, v, mo, connection)


def parse_amodel_literal(txt, bucket, fallback):
    # 단일/템플릿 a.model 문자열 (픽처프레임류)
    found = False
    for m in re.finditer(r'a\.model\s*,\s*(?:sizeof[^,]*,\s*)?"([^"%\\]+)"', txt):
        found = True
        name = strip_mode_parens(m.group(1).strip())
        v, mo = split_vendor(name, fallback)
        add(bucket, v, mo, "usb")
    if not found and fallback:
        add(bucket, fallback, fallback, "usb")


PARSERS = {
    "ptp2": parse_ptp2,
    "canon": parse_canon,
    "sierra": parse_sierra,
    "ricoh": parse_ricoh,
    "pentax": parse_pentax,
    "kodak_dc240": parse_hex_table,
    "digigr8": lambda t, b: parse_toy_table(t, b, "기타"),
    "jl2005a": lambda t, b: parse_toy_table(t, b, "기타"),
    "jl2005c": lambda t, b: parse_toy_table(t, b, "기타"),
    "mars": lambda t, b: parse_toy_table(t, b, "기타"),
    "sonix": lambda t, b: parse_toy_table(t, b, "기타"),
    "sq905": lambda t, b: parse_toy_table(t, b, "기타"),
    "ax203": lambda t, b: parse_amodel_literal(t, b, "AX203 USB picture frame"),
    "tp6801": lambda t, b: parse_amodel_literal(t, b, "TP6801 USB picture frame"),
    "st2205": lambda t, b: parse_amodel_literal(t, b, "ST2205 USB picture frame"),
    "quicktake1x0": lambda t, b: parse_amodel_literal(t, b, "Apple"),
    "dimagev": lambda t, b: add(b, "Minolta", "Dimage V", "usb"),
    "topfield": lambda t, b: parse_amodel_literal(t, b, "Topfield"),
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("camlibs_dir", help="libgphoto2 소스의 camlibs 디렉터리 경로")
    ap.add_argument("-o", "--out", default=None, help="출력 JSON 경로")
    args = ap.parse_args()

    root = args.camlibs_dir
    if not os.path.isdir(root):
        sys.exit(f"camlibs 디렉터리 없음: {root}")

    bucket = set()
    report = []
    for cam, rels in CAMLIBS.items():
        before = len(bucket)
        parser = PARSERS[cam]
        for rel in rels:
            path = os.path.join(root, rel)
            if not os.path.isfile(path):
                report.append(f"  ! {cam}: 소스 없음 {rel}")
                continue
            txt = read(path)
            parser(txt, bucket)
        report.append(f"  {cam:14} +{len(bucket) - before}")

    cameras = [
        {"vendor": v, "model": mo, "connection": c}
        for (v, mo, c) in sorted(bucket, key=lambda x: (x[0].lower(), x[1].lower()))
    ]
    out = {
        "generated": "libgphoto2 camlibs parse (CamCon arm64-v8a bundled 19 camlibs)",
        "total": len(cameras),
        "cameras": cameras,
    }

    out_path = args.out or os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
        "assets", "data", "supported-cameras.json",
    )
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as fh:
        json.dump(out, fh, ensure_ascii=False, indent=1)
        fh.write("\n")

    print("camlib별 신규 모델 수:")
    print("\n".join(report))
    print(f"\n총 고유 모델: {len(cameras)}")
    print(f"출력: {out_path}")


if __name__ == "__main__":
    main()
