#!/usr/bin/env python3
"""CamCon Play Console 그래픽 생성 — 현재 앱 아이콘(벡터 드로어블)에서 렌더.

  1) 앱 아이콘 512x512  (ic_launcher_background.xml + ic_launcher_foreground.xml 합성)
  2) 그래픽 이미지 1024x500 (다크 HUD 배경 + 리티클 전경 + CamCon 워드마크)

VectorDrawable(XML) → SVG 변환 후 macOS qlmanage로 래스터화, PIL로 후처리/합성.
출력: 리포 루트 playstore-app-icon-512.png, playstore-feature-1024x500.png
"""
import os, re, subprocess, xml.etree.ElementTree as ET
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DRW = os.path.join(ROOT, "app/src/main/res/drawable")
FONT_BOLD = os.path.join(ROOT, "app/src/main/res/font/pretendard_bold.otf")
FONT_MED = os.path.join(ROOT, "app/src/main/res/font/pretendard_medium.otf")
AND = "{http://schemas.android.com/apk/res/android}"
AAPT = "{http://schemas.android.com/aapt}"
TMP = "/tmp/camcon_gfx"; os.makedirs(TMP, exist_ok=True)


def av(el, n): return el.get(AND + n)


def color_split(c):
    c = c.lstrip("#")
    if len(c) == 8:
        return "#" + c[2:].upper(), int(c[0:2], 16) / 255.0
    return "#" + c.upper(), 1.0


def grad_svg(g, gid):
    stops = "".join(
        f'<stop offset="{av(it,"offset")}" stop-color="{color_split(av(it,"color"))[0]}"'
        f' stop-opacity="{color_split(av(it,"color"))[1]}"/>'
        for it in g.findall("item"))
    if av(g, "type") == "radial":
        return (f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" '
                f'cx="{av(g,"centerX")}" cy="{av(g,"centerY")}" r="{av(g,"gradientRadius")}">{stops}</radialGradient>')
    return (f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" '
            f'x1="{av(g,"startX")}" y1="{av(g,"startY")}" x2="{av(g,"endX")}" y2="{av(g,"endY")}">{stops}</linearGradient>')


def convert(xml_path, pfx):
    root = ET.parse(xml_path).getroot()
    defs, body, n = [], [], 0
    for p in root.findall("path"):
        d = av(p, "pathData")
        fill, fillop, stroke, strokeop = "none", 1.0, "none", 1.0
        for at in p.findall(AAPT + "attr"):
            g = at.find("gradient")
            if g is None:
                continue
            gid = f"{pfx}{n}"; n += 1
            defs.append(grad_svg(g, gid))
            if at.get("name") == "android:fillColor":
                fill = f"url(#{gid})"
            elif at.get("name") == "android:strokeColor":
                stroke = f"url(#{gid})"
        if av(p, "fillColor") and fill == "none":
            fill, fillop = color_split(av(p, "fillColor"))
        if av(p, "strokeColor") and stroke == "none":
            stroke, strokeop = color_split(av(p, "strokeColor"))
        parts = [f'd="{d}"', f'fill="{fill}"']
        if fill != "none" and fillop < 1:
            parts.append(f'fill-opacity="{fillop}"')
        if stroke != "none":
            parts.append(f'stroke="{stroke}"')
            if strokeop < 1: parts.append(f'stroke-opacity="{strokeop}"')
            if av(p, "strokeWidth"): parts.append(f'stroke-width="{av(p,"strokeWidth")}"')
            if av(p, "strokeLineCap"): parts.append(f'stroke-linecap="{av(p,"strokeLineCap")}"')
            if av(p, "strokeLineJoin"): parts.append(f'stroke-linejoin="{av(p,"strokeLineJoin")}"')
        body.append("<path " + " ".join(parts) + "/>")
    return "".join(defs), "".join(body)


def render_svg(svg, size, name):
    p = os.path.join(TMP, name + ".svg")
    open(p, "w").write(svg)
    subprocess.run(["qlmanage", "-t", "-s", str(size), "-o", TMP, p],
                   capture_output=True)
    out = p + ".png"
    return Image.open(out).convert("RGBA") if os.path.exists(out) else None


def main():
    bgd, bgb = convert(os.path.join(DRW, "ic_launcher_background.xml"), "b")
    fgd, fgb = convert(os.path.join(DRW, "ic_launcher_foreground.xml"), "f")

    # 1) 앱 아이콘 512 (배경+전경 풀스퀘어; Play가 라운딩 마스크 적용)
    icon_svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" '
                f'viewBox="0 0 108 108"><defs>{bgd}{fgd}</defs>{bgb}{fgb}</svg>')
    img = render_svg(icon_svg, 512, "icon")
    img = img.resize((512, 512), Image.LANCZOS).convert("RGB")
    icon_out = os.path.join(ROOT, "playstore-app-icon-512.png")
    img.save(icon_out, "PNG")
    print("앱 아이콘:", icon_out, img.size)

    # 2) 그래픽 이미지 1024x500 — 다크 HUD 배경 + 라운드 앱아이콘 + 워드마크
    import math
    from PIL import ImageFilter
    W, H = 1024, 500
    banner = Image.new("RGB", (W, H)); px = banner.load()
    cx, cy, R = W * 0.30, H * 0.5, W * 0.72
    c0 = (0x1B, 0x22, 0x2C); c1 = (0x0A, 0x0B, 0x0D)  # 중심 쿨블랙 → 가장자리
    for y in range(H):
        for x in range(W):
            t = min(1.0, math.hypot(x - cx, y - cy) / R)
            px[x, y] = tuple(int(c0[i] + (c1[i] - c0[i]) * t) for i in range(3))
    # 앱 아이콘(이미 다크 배경 — 투명/흰박스 문제 없음)을 둥근 모서리로 마스킹
    rs = 340
    ic = img.resize((rs, rs), Image.LANCZOS).convert("RGBA")
    mask = Image.new("L", (rs, rs), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, rs - 1, rs - 1], radius=int(rs * 0.22), fill=255)
    ic.putalpha(mask)
    ix, iy = 82, (H - rs) // 2
    # 앰버 소프트 글로우로 배경과 분리
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse([ix - 26, iy - 26, ix + rs + 26, iy + rs + 26],
                                 fill=(0xE8, 0xA2, 0x45, 45))
    glow = glow.filter(ImageFilter.GaussianBlur(42))
    banner = Image.alpha_composite(banner.convert("RGBA"), glow).convert("RGB")
    banner.paste(ic, (ix, iy), ic)
    # 워드마크(Pretendard)
    dr = ImageDraw.Draw(banner)
    dr.text((476, 172), "CamCon", font=ImageFont.truetype(FONT_BOLD, 120), fill=(245, 246, 248))
    dr.text((480, 312), "Cinema Camera Controller",
            font=ImageFont.truetype(FONT_MED, 40), fill=(0xE8, 0xA2, 0x45))
    feat_out = os.path.join(ROOT, "playstore-feature-1024x500.png")
    banner.save(feat_out, "PNG")
    print("그래픽 이미지:", feat_out, banner.size)


if __name__ == "__main__":
    main()
