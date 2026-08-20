#!/usr/bin/env python3
"""
CamCon 홈페이지 — 색감 전송(MKL Color Transfer) 예시 사진 렌더러.

앱이 실제로 쓰는 MKL(Monge-Kantorovich Linear) 색감 전송을 그대로 옮겨,
"레퍼런스 사진 한 장의 색을 내 사진에 입힌다"를 눈으로 보여주는 예시를 만든다.

정본 소스(이 스크립트가 따라가야 하는 원본):
  - app/.../data/processor/GPUImageMKLColorTransferFilter.kt  (프래그먼트 셰이더 = 픽셀 변환)
  - app/.../data/processor/ColorTransferProcessor.kt          (통계 계산·샘플링 규칙)
둘 중 하나가 바뀌면 이 파일도 같이 고쳐야 예시가 앱 출력과 어긋나지 않는다.

앱과 의도적으로 다른 점:
  - 앱은 float32/GPU, 여기는 float64/CPU. 눈에 보이는 차이는 없지만 비트 단위로 같지는 않다.
  - 다운스케일 필터가 다르다(Android createScaledBitmap vs PIL LANCZOS). 통계용 800px 축소에만
    영향을 주며, 평균·표준편차는 소수점 아래에서만 흔들린다.

- 원본/레퍼런스: website/assets/samples/{hero,grid,alt}.jpg  (교체하려면 이 파일만 바꾸고 재실행)
- 출력: website/assets/color/*.webp

재실행: python3 website/tools/render_color_transfer.py
"""
import json
import os

import numpy as np
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SAMPLES = os.path.join(ROOT, "website/assets/samples")
OUT = os.path.join(ROOT, "website/assets/color")
os.makedirs(OUT, exist_ok=True)

# 원본(색을 입힐 사진) — 설산. 차갑고 채도가 낮아 레퍼런스 색이 들어오는 게 가장 잘 보인다.
TARGET = "hero"
# 레퍼런스(색을 가져올 사진) — 원본과 색 성격이 뚜렷이 다른 두 장.
REFERENCES = [
    ("fjord", "grid"),  # 피오르 — 시원한 블루
    ("forest", "alt"),  # 숲 폭포 — 짙은 그린
]
# 강도 단계. 0 은 원본과 같으므로 렌더하지 않고 페이지가 원본 이미지를 그대로 쓴다.
INTENSITIES = [35, 70, 100]

STATS_MAX_SIZE = 800  # ColorTransferProcessor.STATS_MAX_SIZE
OUT_W, OUT_H = 1280, 854  # 원본·결과 — 전/후 비교 프레임용(필름 섹션 ba-* 와 같은 규격)
THUMB_W, THUMB_H = 640, 426  # 레퍼런스 썸네일
PALETTE_N = 5  # 레퍼런스에서 뽑는 대표색 개수


def crop_resize(img, w, h):
    """render_film_examples.py 와 동일한 중앙 크롭 → 리사이즈."""
    img = img.convert("RGB")
    iw, ih = img.size
    tr = w / h
    if iw / ih > tr:
        nw = int(ih * tr)
        x = (iw - nw) // 2
        img = img.crop((x, 0, x + nw, ih))
    else:
        nh = int(iw / tr)
        y = (ih - nh) // 2
        img = img.crop((0, y, iw, y + nh))
    return img.resize((w, h), Image.LANCZOS)


def load_sample(name):
    for ext in (".jpg", ".jpeg", ".png"):
        p = os.path.join(SAMPLES, name + ext)
        if os.path.exists(p):
            return Image.open(p).convert("RGB")
    raise SystemExit(f"[에러] 원본 없음: {SAMPLES}/{name}.jpg — samples/ 에 사진을 넣으세요")


def rgb_to_lab(rgb):
    """sRGB(0..1, shape (...,3)) → CIE Lab. 셰이더 rgbToLab 과 같은 상수·분기."""
    m = rgb > 0.04045
    lin = np.where(m, np.power((rgb + 0.055) / 1.055, 2.4), rgb / 12.92)
    r, g, b = lin[..., 0], lin[..., 1], lin[..., 2]

    x = r * 0.4124564 + g * 0.3575761 + b * 0.1804375
    y = r * 0.2126729 + g * 0.7151522 + b * 0.0721750
    z = r * 0.0193339 + g * 0.1191920 + b * 0.9503041

    xn, yn, zn = x / 0.95047, y / 1.00000, z / 1.08883

    def f(t):
        return np.where(t > 0.008856, np.cbrt(np.maximum(t, 0.0)), 7.787 * t + 16.0 / 116.0)

    fx, fy, fz = f(xn), f(yn), f(zn)
    return np.stack([116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz)], axis=-1)


def lab_to_rgb(lab):
    """CIE Lab → sRGB(0..1). 셰이더 labToRgb 을 그대로 옮겼다.

    역변환 분기는 표준 Lab(fx > 6/29)이 아니라 셰이더가 쓰는 `pow(fx,3) > 0.008856` 이다.
    수치상 거의 같지만, 앱과 어긋나지 않도록 일부러 셰이더 쪽을 따른다.
    """
    fy = (lab[..., 0] + 16.0) / 116.0
    fx = lab[..., 1] / 500.0 + fy
    fz = fy - lab[..., 2] / 200.0

    def finv(t):
        c = np.power(t, 3.0)
        return np.where(c > 0.008856, c, (t - 16.0 / 116.0) / 7.787)

    x = finv(fx) * 0.95047
    y = finv(fy) * 1.00000
    z = finv(fz) * 1.08883

    r = x * 3.2404542 + y * -1.5371385 + z * -0.4985314
    g = x * -0.9692660 + y * 1.8760108 + z * 0.0415560
    b = x * 0.0556434 + y * -0.2040259 + z * 1.0572252
    rgb = np.stack([r, g, b], axis=-1)

    m = rgb > 0.0031308
    return np.where(m, 1.055 * np.power(np.maximum(rgb, 0.0), 1.0 / 2.4) - 0.055, 12.92 * rgb)


def sample_step(total_pixels):
    """ColorTransferProcessor.bitmapToLabPixelsOptimized 의 샘플링 규칙."""
    if total_pixels > 100000:
        return max(1, total_pixels // 20000)
    if total_pixels > 50000:
        return max(1, total_pixels // 25000)
    return 1


def lab_stats(img):
    """통계용 800px 축소 → 샘플링 → Lab 채널별 (평균, 표준편차).

    표준편차는 앱과 같이 E[x²] - E[x]² (모집단 분산)으로 계산한다.
    """
    w, h = img.size
    if w > STATS_MAX_SIZE or h > STATS_MAX_SIZE:
        s = min(STATS_MAX_SIZE / w, STATS_MAX_SIZE / h)
        img = img.resize((int(w * s), int(h * s)), Image.LANCZOS)

    arr = np.asarray(img, dtype=np.float64) / 255.0
    flat = arr.reshape(-1, 3)
    flat = flat[:: sample_step(flat.shape[0])]

    lab = rgb_to_lab(flat)
    mean = lab.mean(axis=0)
    var = np.maximum((lab * lab).mean(axis=0) - mean * mean, 0.0)
    return mean, np.sqrt(var)


def color_transfer(target_img, in_stats, ref_stats, intensity):
    """셰이더 main() 과 같은 순서: Lab 변환 → 통계 정합 → 강도 보간 → RGB 복원 → clamp."""
    in_mean, in_std = in_stats
    ref_mean, ref_std = ref_stats

    arr = np.asarray(target_img, dtype=np.float64) / 255.0
    lab = rgb_to_lab(arr)

    # inputStdDev 가 0 에 가까우면 셰이더는 referenceMean 을 그대로 쓴다(0 나눗셈 회피).
    transferred = np.empty_like(lab)
    for c in range(3):
        if in_std[c] > 0.001:
            transferred[..., c] = (lab[..., c] - in_mean[c]) / in_std[c] * ref_std[c] + ref_mean[c]
        else:
            transferred[..., c] = ref_mean[c]

    final_lab = lab + (transferred - lab) * intensity  # mix()
    rgb = np.clip(lab_to_rgb(final_lab), 0.0, 1.0)
    return Image.fromarray(np.rint(rgb * 255.0).astype(np.uint8), "RGB")


def dominant_palette(img, n=PALETTE_N):
    """레퍼런스의 대표색 n개를 hex 로 뽑는다.

    '어떤 색이 옮겨오는가'를 페이지에서 눈으로 보여주기 위한 값이라, 실제 사진에서 차지하는
    면적 순(median cut 양자화)으로 고르고 밝기순으로 정렬한다. 임의로 채도를 부풀리지 않는다
    (사진에 없는 색을 팔레트로 내보이면 그건 장식이지 정보가 아니다).
    """
    small = img.convert("RGB").resize((200, 133), Image.LANCZOS)
    q = small.quantize(colors=n, method=Image.MEDIANCUT, dither=Image.NONE)
    pal = q.getpalette()[: n * 3]
    counts = dict(q.getcolors() or [])
    entries = []
    for i in range(n):
        r, g, b = pal[i * 3 : i * 3 + 3]
        lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
        entries.append((counts.get(i, 0), lum, f"#{r:02x}{g:02x}{b:02x}"))
    entries.sort(key=lambda e: e[1])  # 어두운 색 → 밝은 색
    return [hex_ for _, _, hex_ in entries]


def main():
    target_src = load_sample(TARGET)
    target = crop_resize(target_src, OUT_W, OUT_H)
    in_stats = lab_stats(target)

    made = []

    fp = os.path.join(OUT, "ct-target.webp")
    target.save(fp, "WEBP", quality=84, method=6)
    made.append(("ct-target", os.path.getsize(fp)))

    palettes = {}
    for slug, scene in REFERENCES:
        ref_src = load_sample(scene)
        ref_stats = lab_stats(ref_src)
        palettes[slug] = dominant_palette(ref_src)

        fp = os.path.join(OUT, f"ct-ref-{slug}.webp")
        crop_resize(ref_src, THUMB_W, THUMB_H).save(fp, "WEBP", quality=82, method=6)
        made.append((f"ct-ref-{slug}", os.path.getsize(fp)))

        for i in INTENSITIES:
            out = color_transfer(target, in_stats, ref_stats, i / 100.0)
            fp = os.path.join(OUT, f"ct-after-{slug}-{i}.webp")
            out.save(fp, "WEBP", quality=84, method=6)
            made.append((f"ct-after-{slug}-{i}", os.path.getsize(fp)))

    # main.js 가 레퍼런스 선택 시 대표색 점을 칠하는 데 쓴다. 없으면 점만 안 그려지고 레이아웃은 멀쩡하다.
    fp = os.path.join(OUT, "palette.json")
    with open(fp, "w", encoding="utf-8") as f:
        json.dump(palettes, f, ensure_ascii=False, indent=2)
        f.write("\n")
    made.append(("palette.json", os.path.getsize(fp)))

    total = sum(sz for _, sz in made)
    for name, sz in made:
        print(f"  {name:<26} {sz / 1024:7.1f} KB")
    print(f"\n{len(made)}개 파일, 합계 {total / 1024:.1f} KB → {OUT}")


if __name__ == "__main__":
    main()
