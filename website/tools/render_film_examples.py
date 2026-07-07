#!/usr/bin/env python3
"""
CamCon 홈페이지 — 필름 시뮬레이션 예시 사진 렌더러.

앱에 실제로 탑재된 3D LUT(.cube)를 원본 사진에 적용해 '진짜 필름 룩' 예시를 만든다.
- 원본: website/assets/samples/{grid,hero}.jpg  (교체하려면 이 파일만 바꾸고 재실행)
  · grid.jpg = 카테고리/무료 스와치 공용 장면(같은 장면·다른 필름 = 비교 시연)
  · hero.jpg = 전/후 슬라이더용
- 출력: website/assets/film/*.webp
- LUT 원천: app/src/main/assets/luts/  (앱과 동일)

재실행: python3 website/tools/render_film_examples.py
"""
import os
import numpy as np
from PIL import Image, ImageOps

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
LUT_DIR = os.path.join(ROOT, "app/src/main/assets/luts")
SAMPLES = os.path.join(ROOT, "website/assets/samples")
OUT = os.path.join(ROOT, "website/assets/film")
os.makedirs(OUT, exist_ok=True)

# 무료 시그니처 5종
FREE = [
    ("free-portra400",     "negative_new/kodak_portra_400.cube"),
    ("free-velvia50",      "colorslide/fuji_velvia_50.cube"),
    ("free-trix400",       "bw/kodak_tri-x_400.cube"),
    ("free-classicchrome", "fujixtransiii/fuji_xtrans_iii_classic_chrome.cube"),
    ("free-px680",         "instant_consumer/polaroid_px-680.cube"),
]
# 9개 계열 대표(무료와 중복 회피)
CATS = [
    ("cat-bw",       "bw/ilford_delta_100.cube"),
    ("cat-slide",    "colorslide/fuji_provia_100_generic.cube"),
    ("cat-xtrans",   "fujixtransiii/fuji_xtrans_iii_astia.cube"),
    ("cat-instc",    "instant_consumer/polaroid_px-70.cube"),
    ("cat-instp",    "instant_pro/fuji_fp-100c.cube"),
    ("cat-colorneg", "negative_color/fuji_superia_200.cube"),
    ("cat-negnew",   "negative_new/fuji_400h.cube"),
    ("cat-negold",   "negative_old/kodak_portra_160_nc.cube"),
    ("cat-print",    "print/kodak_2383_constlclip.cube"),
]
HERO_LUT = "negative_new/kodak_portra_400.cube"  # 전/후 슬라이더 '후'


def load_cube(path):
    size = None
    data = []
    for ln in open(path):
        s = ln.strip()
        if not s or s[0] == "#":
            continue
        if s.startswith("LUT_3D_SIZE"):
            size = int(s.split()[-1]); continue
        if s[0].isalpha():        # TITLE / DOMAIN_* / LUT_1D_* 등
            continue
        p = s.split()
        if len(p) == 3:
            data.append((float(p[0]), float(p[1]), float(p[2])))
    arr = np.array(data, dtype=np.float32).reshape((size, size, size, 3))  # [b,g,r]
    return arr, size


def apply_lut(img, lut, size):
    a = np.asarray(img.convert("RGB"), dtype=np.float32) / 255.0
    idx = a * (size - 1)
    lo = np.floor(idx).astype(int)
    hi = np.minimum(lo + 1, size - 1)
    fr = idx - lo
    r0, g0, b0 = lo[..., 0], lo[..., 1], lo[..., 2]
    r1, g1, b1 = hi[..., 0], hi[..., 1], hi[..., 2]
    fr_r, fr_g, fr_b = fr[..., 0:1], fr[..., 1:2], fr[..., 2:3]

    def s(ri, gi, bi):
        return lut[bi, gi, ri]

    c00 = s(r0, g0, b0) * (1 - fr_r) + s(r1, g0, b0) * fr_r
    c10 = s(r0, g1, b0) * (1 - fr_r) + s(r1, g1, b0) * fr_r
    c01 = s(r0, g0, b1) * (1 - fr_r) + s(r1, g0, b1) * fr_r
    c11 = s(r0, g1, b1) * (1 - fr_r) + s(r1, g1, b1) * fr_r
    c0 = c00 * (1 - fr_g) + c10 * fr_g
    c1 = c01 * (1 - fr_g) + c11 * fr_g
    out = c0 * (1 - fr_b) + c1 * fr_b
    return Image.fromarray(np.clip(out * 255, 0, 255).astype("uint8"))


def crop_resize(img, w, h):
    img = ImageOps.exif_transpose(img).convert("RGB")
    tr = w / h
    iw, ih = img.size
    ir = iw / ih
    if ir > tr:                       # 너무 넓음 → 좌우 크롭
        nw = int(ih * tr); x = (iw - nw) // 2
        img = img.crop((x, 0, x + nw, ih))
    else:                             # 너무 높음 → 상하 크롭
        nh = int(iw / tr); y = (ih - nh) // 2
        img = img.crop((0, y, iw, y + nh))
    return img.resize((w, h), Image.LANCZOS)


def load_base(name, w, h):
    for ext in (".jpg", ".jpeg", ".png"):
        p = os.path.join(SAMPLES, name + ext)
        if os.path.exists(p):
            return crop_resize(Image.open(p), w, h)
    raise SystemExit(f"[에러] 원본 없음: {SAMPLES}/{name}.jpg — samples/ 에 사진을 넣으세요")


def main():
    # 스와치용 공용 장면 (5:4)
    grid = load_base("grid", 760, 608)
    made = []
    for slug, lp in FREE + CATS:
        lut, size = load_cube(os.path.join(LUT_DIR, lp))
        out = apply_lut(grid, lut, size)
        fp = os.path.join(OUT, slug + ".webp")
        out.save(fp, "WEBP", quality=82, method=6)
        made.append((slug, os.path.getsize(fp)))
    # 전/후 슬라이더 (3:2)
    hero = load_base("hero", 1280, 854)
    hero.save(os.path.join(OUT, "hero-before.webp"), "WEBP", quality=84, method=6)
    lut, size = load_cube(os.path.join(LUT_DIR, HERO_LUT))
    apply_lut(hero, lut, size).save(os.path.join(OUT, "hero-after.webp"), "WEBP", quality=84, method=6)
    made.append(("hero-before", os.path.getsize(os.path.join(OUT, "hero-before.webp"))))
    made.append(("hero-after", os.path.getsize(os.path.join(OUT, "hero-after.webp"))))

    total = sum(sz for _, sz in made)
    print(f"렌더 완료: {len(made)}장, 총 {total/1024:.0f} KB")
    for slug, sz in made:
        print(f"  {slug:22s} {sz/1024:6.1f} KB")


if __name__ == "__main__":
    main()
