#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""CamCon 연결 안내 페이지 빌더 — 허브 + 제조사 4종의 한국어 정본 HTML 을 찍어 낸다.

입력: website/assets/i18n/ko.json (문구), website/assets/img/guide/ (사진 슬롯)
출력: website/{guide,guide-nikon,guide-canon,guide-sony,guide-fujifilm}.html

네 제조사 페이지는 골격이 같고 키만 다르다. 손으로 네 번 쓰면 한 곳만 고치는 사고가 나므로
같은 뼈대에서 생성한다. 산출된 HTML 이 다국어 빌더(build_i18n_pages.py)의 입력 템플릿이다.

사진 슬롯: assets/img/guide/<슬롯>.<확장자> 가 있으면 그 자리의 도해가 사진으로 바뀐다.
슬롯 이름과 주의사항은 그 디렉터리의 README.md 참조.

사용 순서:
  python3 website/tools/build_guide_pages.py      # 이 파일들
  python3 website/tools/build_i18n_pages.py       # 7개 언어판 + sitemap
"""
import json, os, sys

WEB = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
I18N = os.path.join(WEB, "assets", "i18n")
LANGS = ["ko", "en", "ja", "zh", "de", "es", "fr", "it"]
KO = json.load(open(os.path.join(I18N, "ko.json"), encoding="utf-8"))

def t(key):
    """한국어 정본 값. 생성기가 사전에 없는 키를 쓰면 여기서 바로 죽는다."""
    return KO[key]


NIKON_MODELS = [
    ("Z9", "Nikon Z9"), ("Z8", "Nikon Z8"), ("Z7_2", "Nikon Z7II"), ("Z7", "Nikon Z7"),
    ("Z6 III", "Nikon Z6III"), ("Z6_2", "Nikon Z6II"), ("Z6", "Nikon Z6"), ("Z5", "Nikon Z5"),
    ("Z50", "Nikon Z50"), ("Z30", "Nikon Z30"), ("Zfc", "Nikon Zfc"), ("Zf", "Nikon Zf"),
]

VENDORS = [
    dict(src="guide-nikon.html", p="guide", brand="Nikon", verified=True,
         s5title="guide.s5.title", s5note=True, models=True, gen=True, wizard=True, textra=["gn.t.recon", "gn.t.band"],
         s3="guide.s3", ogtitle="니콘 카메라 Wi-Fi 연결 방법", manual="https://onlinemanual.nikonimglib.com/",
         t12=["guide.t1", "guide.t2"]),
    dict(src="guide-canon.html", p="gc", brand="Canon", verified=False,
         s5title="g.s5.title", s5note=False, models=False, gen=False, wizard=False, textra=[],
         s3="gc.s3", ogtitle="캐논 카메라 Wi-Fi 연결 방법", manual="https://cam.start.canon/",
         t12=["gc.t1", "gc.t2"]),
    dict(src="guide-sony.html", p="gs", brand="Sony", verified=False,
         s5title="g.s5.title", s5note=False, models=False, gen=False, wizard=False, textra=[],
         s3="gs.s3", ogtitle="소니 카메라 Wi-Fi 연결 방법", manual="https://helpguide.sony.net/",
         t12=["gs.t1", "gs.t2"]),
    dict(src="guide-fujifilm.html", p="gf", brand="Fujifilm", verified=False,
         s5title="g.s5.title", s5note=False, models=False, gen=False, wizard=False, textra=[],
         s3="gf.s3", ogtitle="후지필름 카메라 Wi-Fi 연결 방법", manual="https://fujifilm-dsc.com/",
         t12=["gf.t1", "gf.t2"]),
]

SITE = "https://camcon.inik.kr"


def head(slug, tkey, dkey, ogtitle, ldtype, ldname):
    langs = ["ko", "en", "ja", "zh", "de", "es", "fr", "it"]
    alt = "\n".join(
        '  <link rel="alternate" hreflang="%s" href="%s/%s">' % (
            l, SITE, slug if l == "ko" else "%s/%s" % (l, slug))
        for l in langs)
    return f'''<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title data-i18n="{tkey}">{t(tkey)}</title>
  <meta name="description" data-i18n-attr="content" data-i18n="{dkey}" content="{t(dkey)}">
  <meta name="theme-color" content="#0A0A0C">

  <meta property="og:type" content="article">
  <meta property="og:locale" content="ko_KR">
  <meta property="og:site_name" content="CamCon">
  <meta property="og:url" content="{SITE}/{slug}">
  <meta property="og:title" content="{ogtitle}">
  <meta property="og:description" content="{t(dkey)}">
  <meta property="og:image" content="{SITE}/assets/img/og-card.png?v=5">
  <meta property="og:image:width" content="1200">
  <meta property="og:image:height" content="630">
  <meta property="og:image:type" content="image/png">
  <meta property="og:image:alt" content="CamCon Cinema Camera Controller">
  <meta name="twitter:card" content="summary_large_image">
  <meta name="twitter:title" content="{ogtitle}">
  <meta name="twitter:description" content="{t(dkey)}">
  <meta name="twitter:image" content="{SITE}/assets/img/og-card.png?v=5">

  <link rel="canonical" href="{SITE}/{slug}">
  <!-- 이 9줄은 8개 언어판 전부에 동일하게 들어가야 한다(상호 return-link 대칭).
       tools/build_i18n_pages.py 가 슬러그를 유지한 채 나머지 7개를 생성한다. -->
{alt}
  <link rel="alternate" hreflang="x-default" href="{SITE}/en/{slug}">
  <link rel="icon" type="image/svg+xml" href="favicon.svg">
  <link rel="apple-touch-icon" href="assets/img/apple-touch-icon.png">

  <link rel="preconnect" href="https://cdn.jsdelivr.net" crossorigin>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link rel="stylesheet" href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/variable/pretendardvariable-dynamic-subset.min.css">
  <link rel="stylesheet" href="assets/css/style.css?v=20260818b">
  <link rel="stylesheet" href="assets/css/guide.css?v=20260824d">
  <link rel="stylesheet" media="print" onload="this.media='all';this.onload=null" href="https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;500;600;700&display=swap">
  <noscript><link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;500;600;700&display=swap"></noscript>

  <script type="application/ld+json">
  {{
    "@context": "https://schema.org",
    "@type": "{ldtype}",
    "name": "{ldname}",
    "description": "{t(dkey)}",
    "url": "{SITE}/{slug}",
    "image": "{SITE}/assets/img/og-card.png?v=5",
    "totalTime": "PT5M"
  }}
  </script>
</head>
<body>
  <div class="grain" aria-hidden="true"></div>
  <a class="skip-link" href="#main" data-i18n="a11y.skip">{t("a11y.skip")}</a>
'''


NAV_ITEMS = [("features", "nav.features"), ("films", "nav.films"), ("cameras", "nav.cameras"),
             ("connect", "nav.connect"), ("pricing", "nav.pricing"), ("faq", "nav.faq")]


def header():
    items = "\n".join('        <a href="/#%s" data-i18n="%s">%s</a>' % (a, k, t(k)) for a, k in NAV_ITEMS)
    mitems = "\n".join('      <a href="/#%s" data-i18n="%s">%s</a>' % (a, k, t(k)) for a, k in NAV_ITEMS)
    opts = "\n".join('            <option value="%s">%s</option>' % (c, n) for c, n in [
        ("ko", "한국어"), ("en", "English"), ("ja", "日本語"), ("zh", "中文"),
        ("de", "Deutsch"), ("es", "Español"), ("fr", "Français"), ("it", "Italiano")])
    return f'''
  <header class="site-header" id="top">
    <div class="wrap header-inner">
      <a class="brand" href="/" aria-label="CamCon">
        <svg class="brand-mark" viewBox="0 0 48 48" fill="none" aria-hidden="true">
          <circle cx="24" cy="24" r="16.5" stroke="#EBA23C" stroke-width="2.4"/>
          <path class="iris" d="M35 24 L29.5 33.5 L18.5 33.5 L13 24 L18.5 14.5 L29.5 14.5 Z" stroke="#F7C266" stroke-width="1.4" stroke-linejoin="round" opacity="0.85"/>
          <circle cx="24" cy="24" r="3.4" fill="#EBA23C"/>
        </svg>
        <span class="brand-name">Cam<b>Con</b></span>
      </a>

      <!-- 홈의 섹션 앵커는 이 페이지에 없으므로 루트 기준으로 적는다.
           언어판에서는 build_i18n_pages.py 의 localize_links 가 /en/ 등을 앞에 붙인다. -->
      <nav class="nav" aria-label="{t("a11y.navMain")}" data-i18n-attr="aria-label" data-i18n="a11y.navMain">
{items}
      </nav>

      <div class="header-right">
        <div class="lang-select">
          <svg class="lang-globe" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3c2.5 2.5 2.5 15.5 0 18M12 3c-2.5 2.5-2.5 15.5 0 18"/></svg>
          <select id="lang" aria-label="{t("a11y.langSelect")}" data-i18n-attr="aria-label" data-i18n="a11y.langSelect">
{opts}
          </select>
        </div>
        <button class="nav-toggle" aria-expanded="false" aria-controls="mobile-nav" aria-label="{t("nav.menuOpen")}" data-i18n-attr="aria-label" data-i18n="nav.menuOpen">
          <span></span><span></span><span></span>
        </button>
      </div>
    </div>
    <nav class="mobile-nav" id="mobile-nav" aria-label="{t("a11y.navMobile")}" data-i18n-attr="aria-label" data-i18n="a11y.navMobile" hidden>
{mitems}
    </nav>
  </header>
'''


def footer(slug):
    fitems = "\n".join('          <a href="/#%s" data-i18n="%s">%s</a>' % (a, k, t(k)) for a, k in NAV_ITEMS)
    langlinks = "\n".join(
        '          <a href="/%s" lang="%s" hreflang="%s">%s</a>' % (
            slug if c == "ko" else "%s/%s" % (c, slug), c, c, n)
        for c, n in [("ko", "한국어"), ("en", "English"), ("ja", "日本語"), ("zh", "中文"),
                     ("de", "Deutsch"), ("es", "Español"), ("fr", "Français"), ("it", "Italiano")])
    return f'''
  <footer class="site-footer" id="contact">
    <div class="wrap">
      <div class="footer-top">
        <a class="brand footer-brand" href="/" aria-label="CamCon">
          <svg class="brand-mark" viewBox="0 0 48 48" fill="none" aria-hidden="true">
            <circle cx="24" cy="24" r="16.5" stroke="#EBA23C" stroke-width="2.4"/>
            <path d="M35 24 L29.5 33.5 L18.5 33.5 L13 24 L18.5 14.5 L29.5 14.5 Z" stroke="#F7C266" stroke-width="1.4" stroke-linejoin="round" opacity="0.85"/>
            <circle cx="24" cy="24" r="3.4" fill="#EBA23C"/>
          </svg>
          <span class="brand-name">Cam<b>Con</b></span>
        </a>
        <nav class="footer-nav" aria-label="{t("a11y.navFooter")}" data-i18n-attr="aria-label" data-i18n="a11y.navFooter">
{fitems}
          <a href="/guide.html" data-i18n="footer.guide">{t("footer.guide")}</a>
          <a href="/privacy.html" data-i18n="footer.privacy">{t("footer.privacy")}</a>
          <a href="/terms.html" data-i18n="footer.terms">{t("footer.terms")}</a>
          <a href="mailto:ppp5544@gmail.com" data-i18n="footer.contact">{t("footer.contact")}</a>
        </nav>
      </div>
      <div class="footer-bottom">
        <!-- 언어 링크는 같은 문서의 다른 언어판을 가리킨다(hreflang 이 있어 localize_links 대상에서 빠진다). -->
        <nav class="footer-nav" aria-label="{t("a11y.langSelect")}" data-i18n-attr="aria-label" data-i18n="a11y.langSelect" style="margin:0 0 14px -12px">
{langlinks}
        </nav>
        <p data-i18n="footer.rights">{t("footer.rights")}</p>
      </div>
    </div>
  </footer>

  <script src="assets/js/main.js?v=20260824c" defer></script>
</body>
</html>
'''


IMG_DIR = os.path.join(WEB, "assets", "img", "guide")
EXTS = (".webp", ".jpg", ".jpeg", ".png")


def img_size(rel):
    """webp 헤더에서 폭·높이를 읽는다. width/height 를 비워 두면 사진이 뜰 때 본문이 밀린다.
    Pillow 없이도 돌아야 하므로 VP8/VP8L/VP8X 세 형식을 직접 읽는다."""
    with open(os.path.join(WEB, rel), "rb") as f:
        b = f.read(40)
    if b[12:16] == b"VP8X":
        return (int.from_bytes(b[24:27], "little") + 1, int.from_bytes(b[27:30], "little") + 1)
    if b[12:16] == b"VP8L":
        n = int.from_bytes(b[21:25], "little")
        return ((n & 0x3FFF) + 1, ((n >> 14) & 0x3FFF) + 1)
    if b[12:16] == b"VP8 ":
        return (int.from_bytes(b[26:28], "little") & 0x3FFF,
                int.from_bytes(b[28:30], "little") & 0x3FFF)
    return (768, 512)


def slot_src(slot):
    """assets/img/guide/<slot>.<확장자> 가 있으면 그 경로를 준다. 없으면 None.

    빌드 시점에 파일 존재만 보므로, 사진을 넣고 이 생성기를 다시 돌리면 도해가 사진으로 바뀐다.
    사진을 빼면 도해로 되돌아간다. 어느 쪽이든 페이지가 비는 일은 없다."""
    if not slot:
        return None
    for ext in EXTS:
        if os.path.exists(os.path.join(IMG_DIR, slot + ext)):
            return "assets/img/guide/" + slot + ext
    return None


def photo(slot, cap_key, cap_extra=""):
    """사진 슬롯. alt 는 설명(figcaption)이 바로 아래 붙으므로 비워 장식으로 처리한다."""
    src = slot_src(slot)
    cap = '<span data-i18n="%s">%s</span>%s' % (cap_key, t(cap_key), cap_extra) if cap_extra else t(cap_key)
    capattr = "" if cap_extra else ' data-i18n="%s"' % cap_key
    w, h = img_size(src)
    return f'''                <figure class="shot shot-photo">
                  <img src="/{src}" alt="" width="{w}" height="{h}" loading="lazy" decoding="async">
                  <figcaption{capattr}>{cap}</figcaption>
                </figure>'''


def screen(bar_key, rows, cap_key, cap_extra="", slot=""):
    """기기 화면 도해. rows = [(키, on|off)]"""
    if slot_src(slot):
        return photo(slot, cap_key, cap_extra)
    body = "\n".join(
        '                      <div class="screen-row %s" data-i18n="%s">%s</div>' % (st, k, t(k))
        for k, st in rows)
    cap = '<span data-i18n="%s">%s</span>%s' % (cap_key, t(cap_key), cap_extra) if cap_extra \
        else '%s' % t(cap_key)
    capattr = "" if cap_extra else ' data-i18n="%s"' % cap_key
    return f'''                <figure class="shot">
                  <div class="screen">
                    <div class="screen-bar" data-i18n="{bar_key}">{t(bar_key)}</div>
                    <div class="screen-body">
{body}
                    </div>
                  </div>
                  <figcaption{capattr}>{cap}</figcaption>
                </figure>'''


def screen_text(bar_key, body_key, cap_key, slot=""):
    """본문이 여러 줄 텍스트인 도해(카메라 확인 화면). white-space:pre-line 이라 줄바꿈이 그대로 보인다."""
    if slot_src(slot):
        return photo(slot, cap_key)
    return f'''                <figure class="shot">
                  <div class="screen">
                    <div class="screen-bar" data-i18n="{bar_key}">{t(bar_key)}</div>
                    <div class="screen-body">
                      <p class="screen-tree" data-i18n="{body_key}">{t(body_key)}</p>
                    </div>
                  </div>
                  <figcaption data-i18n="{cap_key}">{t(cap_key)}</figcaption>
                </figure>'''


# 마법사 화면 순서와 각 화면의 설명 키. 사진이 없는 자리는 건너뛴다.
WIZ_SHOTS = [
    ("nikon-01-menu", "gn.shot.menu"),
    ("nikon-02-create", "gn.shot.create"),
    ("nikon-03-name", "gn.shot.name"),
    ("nikon-04-method", "gn.wiz.m1.cap"),
    ("nikon-05-network", "guide.s2.cap"),
    ("nikon-06-key", "gn.shot.key"),
    ("nikon-07-ip", "gn.wiz.m2.cap"),
]


def wizard_shots(manual_url):
    """마법사 화면 사진을 순서대로 늘어놓는다. 한 장도 없으면 빈 문자열을 준다(호출부가 도해로 되돌린다).

    사진은 도해와 달리 찍힌 언어 그대로 8개 언어판에 나온다. 그래서 화면 안의 글자에
    의존하지 않도록 설명(figcaption)이 무엇을 고르는 자리인지 문장으로 말해 준다."""
    figs = []
    for slot, cap_key in WIZ_SHOTS:
        src = slot_src(slot)
        if not src:
            continue
        extra = ""
        if slot == "nikon-05-network":
            extra = (' <a href="%s" target="_blank" rel="noopener" data-i18n="guide.s2.manual">%s</a>'
                     % (manual_url, t("guide.s2.manual")))
        cap = ('<span data-i18n="%s">%s</span>%s' % (cap_key, t(cap_key), extra)) if extra else t(cap_key)
        capattr = "" if extra else ' data-i18n="%s"' % cap_key
        w, h = img_size(src)
        figs.append('                  <figure class="shot shot-photo">\n'
                    '                    <img src="/%s" alt="" width="%d" height="%d" loading="lazy" decoding="async">\n'
                    '                    <figcaption%s>%s</figcaption>\n'
                    '                  </figure>' % (src, w, h, capattr, cap))
    if not figs:
        return ""
    return '                <div class="shots">\n' + "\n".join(figs) + '\n                </div>'


def wizard_block(manual_url):
    """마법사가 묻는 다섯 항목. 화면을 다섯 장 늘어놓는 것보다 표 하나가 훨씬 빨리 읽힌다."""
    rows = "\n".join(
        '                    <div class="wiz-k" data-i18n="gn.wiz.k%d">%s</div>\n'
        '                    <div class="wiz-v" data-i18n="gn.wiz.v%d">%s</div>' % (
            i, t("gn.wiz.k%d" % i), i, t("gn.wiz.v%d" % i))
        for i in range(1, 6))
    shots = wizard_shots(manual_url)
    if not shots:
        # 사진이 없으면 잘못 고르기 쉬운 두 화면만 도해로 보인다.
        shots = (screen("gn.wiz.m1.bar", [("gn.wiz.v2", "on"), ("gn.wiz.m1.off", "off")], "gn.wiz.m1.cap")
                 + "\n"
                 + screen("gn.wiz.m2.bar", [("gn.wiz.v5", "on"), ("gn.wiz.m2.off", "off")], "gn.wiz.m2.cap"))
    return f'''                <p class="gen-label" data-i18n="gn.wiz.title">{t("gn.wiz.title")}</p>
                <p data-i18n="gn.wiz.body">{t("gn.wiz.body")}</p>
                <div class="wiz">
{rows}
                </div>
{shots}'''


def vendor_page(v):
    p, s3 = v["p"], v["s3"]
    tkey = "guide.meta.title" if p == "guide" else p + ".meta.title"
    dkey = "guide.meta.desc" if p == "guide" else p + ".meta.desc"
    h1key = "guide.h1" if p == "guide" else p + ".h1"
    leadkey = "guide.lead" if p == "guide" else p + ".lead"
    s2body = "guide.s2.body" if p == "guide" else p + ".s2.body"
    s2path = "guide.s2.path" if p == "guide" else p + ".s2.path"
    s5body = "guide.s5.body" if p == "guide" else p + ".s5.body"

    pair_block = ""
    if v["wizard"]:
        pair_block = screen_text("gn.pair.bar", "gn.pair.screen", "gn.pair.cap", slot="nikon-08-pairing") + f'''
                <div class="note">
                  <b data-i18n="gn.pair.note.title">{t("gn.pair.note.title")}</b>
                  <p data-i18n="gn.pair.note.body">{t("gn.pair.note.body")}</p>
                </div>'''

    out = [head(v["src"], tkey, dkey, v["ogtitle"], "HowTo", v["ogtitle"]), header()]
    a = out.append

    a(f'''
  <main id="main">
    <div class="wrap">
      <div class="guide-hero">
        <p class="eyebrow"><a href="/guide.html" data-i18n="g.back">{t("g.back")}</a></p>
        <h1 data-i18n="{h1key}">{t(h1key)}</h1>
        <p class="lead" data-i18n="{leadkey}">{t(leadkey)}</p>
      </div>

      <div class="guide-layout">
        <nav class="guide-toc" aria-labelledby="toc-title">
          <h2 id="toc-title" data-i18n="guide.toc.title">{t("guide.toc.title")}</h2>
          <ol>
            <li><a href="#prep" data-i18n="guide.toc.prep">{t("guide.toc.prep")}</a></li>
            <li><a href="#steps" data-i18n="guide.toc.steps">{t("guide.toc.steps")}</a></li>''')
    if v["models"]:
        a(f'            <li><a href="#models" data-i18n="guide.toc.models">{t("guide.toc.models")}</a></li>')
    a(f'''            <li><a href="#trouble" data-i18n="guide.toc.trouble">{t("guide.toc.trouble")}</a></li>
          </ol>
        </nav>

        <div class="guide-body">

          <section class="guide-sec" id="prep">
            <h2 data-i18n="guide.prep.title">{t("guide.prep.title")}</h2>
            <p data-i18n="guide.prep.body">{t("guide.prep.body")}</p>
            <p data-i18n="guide.router">{t("guide.router")}</p>''')
    if not v["verified"]:
        a(f'''            <div class="note note-warn">
              <b data-i18n="g.status.untested.title">{t("g.status.untested.title")}</b>
              <p data-i18n="g.status.untested.body">{t("g.status.untested.body")}</p>
            </div>''')
    a(f'''          </section>

          <section class="guide-sec" id="steps">
            <h2 data-i18n="guide.steps.title">{t("guide.steps.title")}</h2>
            <p data-i18n="guide.steps.body">{t("guide.steps.body")}</p>

            <div class="step">
              <div class="step-no" aria-hidden="true">1</div>
              <h3 data-i18n="guide.s1.title">{t("guide.s1.title")}</h3>
              <div>
                <p data-i18n="guide.s1.body">{t("guide.s1.body")}</p>
                <p data-i18n="guide.s1.tip">{t("guide.s1.tip")}</p>
{screen("guide.s1.shot.bar", [("guide.s1.shot.a", "on"), ("guide.s1.shot.b", "off")], "guide.s1.cap", slot=v["p"] + "-hotspot")}
              </div>
            </div>

            <div class="step">
              <div class="step-no" aria-hidden="true">2</div>
              <h3 data-i18n="guide.s2.title">{t("guide.s2.title")}</h3>
              <div>
                <p data-i18n="{s2body}">{t(s2body)}</p>''')
    if v["gen"]:
        a(f'''                <p class="gen-label" data-i18n="gn.gen.new.title">{t("gn.gen.new.title")}</p>
                <span class="path" data-i18n="{s2path}">{t(s2path)}</span>
                <p class="gen-label" data-i18n="gn.gen.old.title">{t("gn.gen.old.title")}</p>
                <span class="path" data-i18n="gn.gen.old.path">{t("gn.gen.old.path")}</span>
                <p data-i18n="gn.gen.note">{t("gn.gen.note")}</p>
{wizard_block(v["manual"])}''')
    else:
        a(f'                <span class="path" data-i18n="{s2path}">{t(s2path)}</span>')
    s2shot = "" if (v["wizard"] and wizard_shots(v["manual"])) else screen(
        "guide.s2.shot.bar", [("guide.s2.shot.row1", "on"), ("guide.s2.shot.row2", "off")], "guide.s2.cap",
        cap_extra=' <a href="' + v["manual"] + '" target="_blank" rel="noopener" data-i18n="guide.s2.manual">'
                  + t("guide.s2.manual") + '</a>',
        slot=v["p"] + "-05-network")
    a(f'''{s2shot}
              </div>
            </div>

            <div class="step">
              <div class="step-no" aria-hidden="true">3</div>
              <h3 data-i18n="{s3}.title">{t(s3 + ".title")}</h3>
              <div>
                <p data-i18n="{s3}.body">{t(s3 + ".body")}</p>
                <div class="note">
                  <b data-i18n="{s3}.note.title">{t(s3 + ".note.title")}</b>
                  <p data-i18n="{s3}.note.body">{t(s3 + ".note.body")}</p>
                </div>''')
    if p == "guide":
        a(f'''                <div class="note">
                  <b data-i18n="gn.tip.auto.title">{t("gn.tip.auto.title")}</b>
                  <p data-i18n="gn.tip.auto.body">{t("gn.tip.auto.body")}</p>
                </div>''')
        a(screen("guide.s3.shot.bar", [("guide.s3.shot.on", "on"), ("guide.s3.shot.off", "off")], "guide.s3.cap", slot="nikon-01-type"))
    a(f'''              </div>
            </div>

            <div class="step">
              <div class="step-no" aria-hidden="true">4</div>
              <h3 data-i18n="guide.s4.title">{t("guide.s4.title")}</h3>
              <div>
                <p data-i18n="guide.s4.body">{t("guide.s4.body")}</p>
{pair_block if v["wizard"] else ""}
{screen("guide.s4.shot.bar", [("guide.s4.shot.row1", "on"), ("guide.s4.shot.row2", "off")], "guide.s4.cap", slot=v["p"] + "-app")}
              </div>
            </div>

            <div class="step">
              <div class="step-no" aria-hidden="true">5</div>
              <h3 data-i18n="{v["s5title"]}">{t(v["s5title"])}</h3>
              <div>
                <p data-i18n="{s5body}">{t(s5body)}</p>''')
    if v["s5note"]:
        a(f'''                <div class="note">
                  <b data-i18n="guide.s5.note.title">{t("guide.s5.note.title")}</b>
                  <p data-i18n="guide.s5.note.body">{t("guide.s5.note.body")}</p>
                </div>
                <div class="note">
                  <b data-i18n="gn.tip.code.title">{t("gn.tip.code.title")}</b>
                  <p data-i18n="gn.tip.code.body">{t("gn.tip.code.body")}</p>
                </div>
{screen_text("guide.s5.shot.bar", "guide.s5.shot.body", "guide.s5.cap", slot="nikon-09-allow")}''')
    a(f'''              </div>
            </div>

            <div class="note" style="margin-top:34px">
              <b data-i18n="guide.done.title">{t("guide.done.title")}</b>
              <p data-i18n="guide.done.body">{t("guide.done.body")}</p>
            </div>
          </section>''')

    if v["models"]:
        chips = "\n".join('              <li data-model="%s">%s</li>' % (m, n) for m, n in NIKON_MODELS)
        a(f'''
          <section class="guide-sec" id="models">
            <h2 data-i18n="guide.models.title">{t("guide.models.title")}</h2>
            <p data-i18n="guide.models.body">{t("guide.models.body")}</p>
            <!-- tools/generate_supported_cameras.py 의 WIFI_SUPPORTED 가 정본이다.
                 tests/guide.test.js 가 supported-cameras.json 과 대조해 어긋나면 실패시킨다. -->
            <ul class="models">
{chips}
            </ul>
            <p data-i18n="guide.models.note">{t("guide.models.note")}</p>
          </section>''')

    t1, t2 = v["t12"]
    a(f'''
          <section class="guide-sec" id="trouble">
            <h2 data-i18n="guide.trouble.title">{t("guide.trouble.title")}</h2>
            <p data-i18n="guide.trouble.body">{t("guide.trouble.body")}</p>
            <ul class="checks">
              <li>
                <b data-i18n="{t1}.title">{t(t1 + ".title")}</b>
                <p data-i18n="{t1}.body">{t(t1 + ".body")}</p>
              </li>
              <li>
                <b data-i18n="{t2}.title">{t(t2 + ".title")}</b>
                <p data-i18n="{t2}.body">{t(t2 + ".body")}</p>
              </li>''')
    for k in list(v["textra"]) + ["guide.t3", "guide.t4", "guide.t5", "guide.t6"]:
        a(f'''              <li>
                <b data-i18n="{k}.title">{t(k + ".title")}</b>
                <p data-i18n="{k}.body">{t(k + ".body")}</p>
              </li>''')
    a(f'''            </ul>
          </section>

          <div class="guide-end">
            <h2 data-i18n="guide.end.title">{t("guide.end.title")}</h2>
            <p data-i18n="guide.end.body">{t("guide.end.body")}</p>
            <a class="btn btn-primary" href="/guide-usb.html" data-i18n="gu.h1">{t("gu.h1")}</a>
          </div>

        </div>
      </div>
    </div>
  </main>
{footer(v["src"])}''')
    return "".join(out)

# 유선 제조사. usb = (키 접두, 개수) 로 USB 설정 표를 만든다.
WIRED = [
    dict(src="guide-usb-nikon.html", p="un", brand="Nikon", usb=("gu.nikon", 3),
         note="gu.nikon.note", wireless="guide-nikon.html", manual="https://onlinemanual.nikonimglib.com/",
         ogtitle="니콘 카메라 USB 유선 연결 방법"),
    dict(src="guide-usb-canon.html", p="uc", brand="Canon", usb=("uc.usb", 1),
         note="uc.note", wireless="guide-canon.html", manual="https://cam.start.canon/",
         ogtitle="캐논 카메라 USB 유선 연결 방법"),
    dict(src="guide-usb-sony.html", p="us", brand="Sony", usb=("us.usb", 4),
         note="us.note", wireless="guide-sony.html", manual="https://helpguide.sony.net/",
         ogtitle="소니 카메라 USB 유선 연결 방법",
         pwtbl=("us.pw", 4), pwslot="usb-sony-power",
         usbslot="usb-sony-usb", noteslot="usb-sony-mode",
         extra=dict(title="us.rs.title", body="us.rs.body", tbl=("us.rs", 3),
                    note="us.rs.note", slot="usb-sony-remote")),
    dict(src="guide-usb-fujifilm.html", p="uf", brand="Fujifilm", usb=("uf.usb", 2),
         note="uf.note", wireless="guide-fujifilm.html", manual="https://fujifilm-dsc.com/",
         ogtitle="후지필름 카메라 USB 유선 연결 방법"),
]


def kv_table(prefix, n, indent=20):
    """{prefix}.k1/v1 … 형태의 설정 표. 제조사마다 행 수가 달라 개수를 받는다."""
    pad = " " * indent
    return "\n".join(
        '%s<div class="wiz-k" data-i18n="%s.k%d">%s</div>\n'
        '%s<div class="wiz-v" data-i18n="%s.v%d">%s</div>' % (
            pad, prefix, i, t("%s.k%d" % (prefix, i)), pad, prefix, i, t("%s.v%d" % (prefix, i)))
        for i in range(1, n + 1))


def wired_vendor_page(v):
    """제조사별 유선 안내. 절전·카드·케이블·권한은 어느 제조사나 같고, USB 설정만 다르다."""
    p = v["p"]
    slug = v["src"]
    out = [head(slug, p + ".meta.title", p + ".meta.desc", v["ogtitle"], "HowTo", v["ogtitle"]), header()]
    a = out.append
    prefix, n = v["usb"]

    def shot(slot, cap_key):
        """사진이 있으면 사진, 없으면 아무것도 넣지 않는다(도해를 새로 그리지는 않는다)."""
        return photo(slot, cap_key) if slot and slot_src(slot) else ""

    # 소니만 원격 촬영 설정 단계가 하나 더 있어 뒤 단계 번호가 밀린다.
    has_extra = bool(v.get("extra"))
    n_cable = 4 if has_extra else 3
    n_perm = n_cable + 1

    pw_block = ""
    if v.get("pwtbl"):
        pw_block = '                <div class="wiz">\n%s\n                </div>' % kv_table(*v["pwtbl"])
    pw_block += ("\n" + shot(v.get("pwslot"), p + ".power")) if v.get("pwslot") else ""
    usb_shot = shot(v.get("usbslot"), prefix + ".k1")
    note_shot = shot(v.get("noteslot"), v["note"] + ".title")

    extra_block = ""
    if has_extra:
        e = v["extra"]
        extra_block = f'''
            <div class="step">
              <div class="step-no" aria-hidden="true">3</div>
              <h3 data-i18n="{e["title"]}">{t(e["title"])}</h3>
              <div>
                <p data-i18n="{e["body"]}">{t(e["body"])}</p>
                <div class="wiz">
{kv_table(*e["tbl"])}
                </div>
{shot(e["slot"], e["title"])}
                <div class="note">
                  <b data-i18n="{e["note"]}.title">{t(e["note"] + ".title")}</b>
                  <p data-i18n="{e["note"]}.body">{t(e["note"] + ".body")}</p>
                </div>
              </div>
            </div>
'''

    a(f'''
  <main id="main">
    <div class="wrap">
      <div class="guide-hero">
        <p class="eyebrow"><a href="/guide-usb.html" data-i18n="g.back">{t("g.back")}</a></p>
        <h1 data-i18n="{p}.h1">{t(p + ".h1")}</h1>
        <p class="lead" data-i18n="{p}.lead">{t(p + ".lead")}</p>
      </div>

      <div class="guide-layout">
        <nav class="guide-toc" aria-labelledby="toc-title">
          <h2 id="toc-title" data-i18n="guide.toc.title">{t("guide.toc.title")}</h2>
          <ol>
            <li><a href="#prep" data-i18n="guide.toc.prep">{t("guide.toc.prep")}</a></li>
            <li><a href="#steps" data-i18n="guide.toc.steps">{t("guide.toc.steps")}</a></li>
            <li><a href="#trouble" data-i18n="guide.toc.trouble">{t("guide.toc.trouble")}</a></li>
          </ol>
        </nav>

        <div class="guide-body">

          <section class="guide-sec" id="prep">
            <h2 data-i18n="gu.prep.title">{t("gu.prep.title")}</h2>
            <p data-i18n="gu.prep.body">{t("gu.prep.body")}</p>
            <div class="note">
              <b data-i18n="gu.prep.note.title">{t("gu.prep.note.title")}</b>
              <p data-i18n="gu.prep.note.body">{t("gu.prep.note.body")}</p>
            </div>
            <div class="note">
              <b data-i18n="gu.card.title">{t("gu.card.title")}</b>
              <p data-i18n="gu.card.body">{t("gu.card.body")}</p>
            </div>
          </section>

          <section class="guide-sec" id="steps">
            <h2 data-i18n="gu.steps.title">{t("gu.steps.title")}</h2>
            <p data-i18n="gu.steps.body">{t("gu.steps.body")}</p>

            <div class="step">
              <div class="step-no" aria-hidden="true">1</div>
              <h3 data-i18n="gu.power.title">{t("gu.power.title")}</h3>
              <div>
                <p data-i18n="gu.power.body">{t("gu.power.body")}</p>
                <p data-i18n="{p}.power">{t(p + ".power")}</p>
{pw_block}
              </div>
            </div>

            <div class="step">
              <div class="step-no" aria-hidden="true">2</div>
              <h3 data-i18n="gu.usbset.title">{t("gu.usbset.title")}</h3>
              <div>
                <div class="wiz">
{kv_table(prefix, n)}
                </div>
{usb_shot}
                <div class="note">
                  <b data-i18n="{v["note"]}.title">{t(v["note"] + ".title")}</b>
                  <p data-i18n="{v["note"]}.body">{t(v["note"] + ".body")}</p>
                </div>
{note_shot}
                <p><a href="{v["manual"]}" target="_blank" rel="noopener" data-i18n="guide.s2.manual">{t("guide.s2.manual")}</a></p>
              </div>
            </div>
{extra_block}
            <div class="step">
              <div class="step-no" aria-hidden="true">{n_cable}</div>
              <h3 data-i18n="gu.s2.title">{t("gu.s2.title")}</h3>
              <div>
                <p data-i18n="gu.s2.body">{t("gu.s2.body")}</p>
                <p data-i18n="gu.s2.tip">{t("gu.s2.tip")}</p>
              </div>
            </div>

            <div class="step">
              <div class="step-no" aria-hidden="true">{n_perm}</div>
              <h3 data-i18n="gu.s3.title">{t("gu.s3.title")}</h3>
              <div>
                <p data-i18n="gu.s3.body">{t("gu.s3.body")}</p>
{screen_text("gu.s3.shot.bar", "gu.s3.shot.body", "gu.s3.cap", slot="usb-03-permission")}
              </div>
            </div>

            <div class="note" style="margin-top:34px">
              <b data-i18n="guide.done.title">{t("guide.done.title")}</b>
              <p data-i18n="guide.done.body">{t("guide.done.body")}</p>
            </div>

            <div class="note" style="margin-top:14px">
              <b data-i18n="gu.mtp.title">{t("gu.mtp.title")}</b>
              <p data-i18n="gu.mtp.body">{t("gu.mtp.body")}</p>
            </div>
          </section>

          <section class="guide-sec" id="trouble">
            <h2 data-i18n="guide.trouble.title">{t("guide.trouble.title")}</h2>
            <p data-i18n="guide.trouble.body">{t("guide.trouble.body")}</p>
            <ul class="checks">''')

    for k in ("gu.t1", "gu.t2", "gu.t3", "gu.t4", "gu.t5", "gu.t6", "gu.t7"):
        a(f'''              <li>
                <b data-i18n="{k}.title">{t(k + ".title")}</b>
                <p data-i18n="{k}.body">{t(k + ".body")}</p>
              </li>''')

    a(f'''            </ul>
          </section>

          <div class="guide-end">
            <h2 data-i18n="gu.wifi.title">{t("gu.wifi.title")}</h2>
            <p data-i18n="gu.wifi.body">{t("gu.wifi.body")}</p>
            <a class="btn btn-primary" href="/{v["wireless"]}">{v["brand"]} Wi-Fi</a>
          </div>

        </div>
      </div>
    </div>
  </main>
{footer(slug)}''')
    return "".join(out)


def usb_page():
    """유선 허브. 제조사를 고르게 하고, 어느 제조사나 같은 두 조건(카드·케이블)을 먼저 못 박는다."""
    slug = "guide-usb.html"
    out = [head(slug, "gu.meta.title", "gu.meta.desc", "USB 케이블로 카메라 연결하는 방법",
                "WebPage", "USB 케이블로 카메라 연결하는 방법"), header()]
    a = out.append

    cards = "\n".join(
        '''            <a class="vcard" href="/%s">
              <span class="vcard-name">%s</span>
              <span class="vcard-desc" data-i18n="%s.lead">%s</span>
            </a>''' % (v["src"], v["brand"], v["p"], t(v["p"] + ".lead"))
        for v in WIRED)

    a(f'''
  <main id="main">
    <div class="wrap">
      <div class="guide-hero">
        <p class="eyebrow"><a href="/guide.html" data-i18n="g.back">{t("g.back")}</a></p>
        <h1 data-i18n="gu.h1">{t("gu.h1")}</h1>
        <p class="lead" data-i18n="gu.lead">{t("gu.lead")}</p>
      </div>

      <section class="guide-sec" id="prep" style="border-top:0;margin-top:22px">
        <h2 data-i18n="gu.prep.title">{t("gu.prep.title")}</h2>
        <p data-i18n="gu.prep.body">{t("gu.prep.body")}</p>
        <div class="note">
          <b data-i18n="gu.prep.note.title">{t("gu.prep.note.title")}</b>
          <p data-i18n="gu.prep.note.body">{t("gu.prep.note.body")}</p>
        </div>
        <div class="note">
          <b data-i18n="gu.card.title">{t("gu.card.title")}</b>
          <p data-i18n="gu.card.body">{t("gu.card.body")}</p>
        </div>
      </section>

      <section class="guide-sec" id="pick">
        <h2 data-i18n="ghub.pick.title">{t("ghub.pick.title")}</h2>
        <div class="vgrid">
{cards}
        </div>
      </section>

      <section class="guide-sec" id="other">
        <h2 data-i18n="guh.other.title">{t("guh.other.title")}</h2>
        <p data-i18n="guh.other.body">{t("guh.other.body")}</p>
        <div class="wiz">
          <div class="wiz-k">Panasonic</div>
          <div class="wiz-v" data-i18n="guh.pana">{t("guh.pana")}</div>
          <div class="wiz-k">OM System</div>
          <div class="wiz-v" data-i18n="guh.om">{t("guh.om")}</div>
        </div>
      </section>

      <section class="guide-sec" id="wifi">
        <h2 data-i18n="gu.wifi.title">{t("gu.wifi.title")}</h2>
        <p data-i18n="gu.wifi.body">{t("gu.wifi.body")}</p>
        <p style="margin-top:16px"><a class="btn btn-primary" href="/guide.html" data-i18n="g.back">{t("g.back")}</a></p>
      </section>
    </div>
  </main>
{footer(slug)}''')
    return "".join(out)


def hub_page():
    out = [head("guide.html", "ghub.meta.title", "ghub.meta.desc",
                "카메라 연결 방법", "WebPage", "카메라 연결 방법"), header()]
    a = out.append
    cards = []
    for v in VENDORS:
        badge = "ghub.badge.verified" if v["verified"] else "ghub.badge.untested"
        cls = "is-verified" if v["verified"] else ""
        dk = {"guide": "ghub.nikon.desc", "gc": "ghub.canon.desc",
              "gs": "ghub.sony.desc", "gf": "ghub.fuji.desc"}[v["p"]]
        cards.append(f'''            <a class="vcard {cls}" href="/{v["src"]}">
              <span class="vcard-name">{v["brand"]}</span>
              <span class="vcard-badge" data-i18n="{badge}">{t(badge)}</span>
              <span class="vcard-desc" data-i18n="{dk}">{t(dk)}</span>
            </a>''')
    a(f'''
  <main id="main">
    <div class="wrap">
      <div class="guide-hero">
        <h1 data-i18n="ghub.h1">{t("ghub.h1")}</h1>
        <p class="lead" data-i18n="ghub.lead">{t("ghub.lead")}</p>
      </div>

      <section class="guide-sec" id="pick" style="border-top:0;margin-top:18px">
        <h2 data-i18n="ghub.pick.title">{t("ghub.pick.title")}</h2>
        <div class="vgrid">
{chr(10).join(cards)}
        </div>
      </section>

      <section class="guide-sec" id="usb">
        <h2 data-i18n="ghub.usb.title">{t("ghub.usb.title")}</h2>
        <p data-i18n="ghub.usb.body">{t("ghub.usb.body")}</p>
        <p style="margin-top:16px">
          <a class="btn btn-primary" href="/guide-usb.html" data-i18n="gu.h1">{t("gu.h1")}</a>
          <a href="/#cameras" style="margin-left:14px" data-i18n="guide.end.cta">{t("guide.end.cta")}</a>
        </p>
      </section>
    </div>
  </main>
{footer("guide.html")}''')
    return "".join(out)


def main():
    check = "--check" in sys.argv
    pages = {"guide.html": hub_page(), "guide-usb.html": usb_page()}
    for w in WIRED:
        pages[w["src"]] = wired_vendor_page(w)
    for v in VENDORS:
        pages[v["src"]] = vendor_page(v)

    if check:
        # 커밋된 HTML 이 지금의 사전·사진 슬롯과 어긋나는지만 본다(쓰기 없음).
        # 사전만 고치고 이 생성기를 안 돌리면 한국어 페이지가 조용히 낡는다.
        stale = []
        for name, html in pages.items():
            try:
                with open(os.path.join(WEB, name), encoding="utf-8") as f:
                    if f.read() != html:
                        stale.append(name)
            except OSError:
                stale.append(name + "(없음)")
        if stale:
            print("  [실패] 안내 페이지가 최신이 아니다: %s" % ", ".join(stale))
            print("         python3 tools/build_guide_pages.py 실행 후 함께 커밋할 것.")
            sys.exit(1)
        print("검증: 안내 페이지 %d장 최신 OK" % len(pages))
        return

    for name, html in pages.items():
        with open(os.path.join(WEB, name), "w", encoding="utf-8") as f:
            f.write(html)
        print("작성: %s (%d bytes)" % (name, len(html)))


if __name__ == "__main__":
    main()
