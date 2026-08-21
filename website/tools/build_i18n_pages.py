#!/usr/bin/env python3
"""CamCon 다국어 페이지 빌더 — 한국어 정본 HTML 을 템플릿으로 언어별 정적 페이지를 만든다.

입력: website/index.html·guide.html, website/assets/i18n/<lang>.json (8개)
출력: website/dist-lang/<lang>/{index,guide}.html (en·ja·zh·de·es·fr·it 7개), website/sitemap.xml

대상 페이지는 PAGES 로 관리한다. 페이지를 늘릴 때는 여기에 한 줄 추가하고
Dockerfile 의 COPY 목록에 한국어 원본을 같이 넣으면 된다(원본은 루트에서 서빙된다).

왜 정적 생성인가 — 지금은 8개 언어가 URL 하나를 공유해서 hreflang 을 붙일 대상이 없고,
"영어 페이지를 영어로 공유"할 방법이 아예 없다. 언어마다 URL 을 주면 OG 카드·색인·공유가 전부 풀린다.
루트(/)는 한국어 그대로 둔다(Play Console 등록 URL·기존 피인용 보존). 그래서 /ko/ 는 만들지 않고
hreflang ko 를 / 로 건다. x-default 는 /en/ 이다.

사전은 </head> 직전에 인라인하므로 생성 페이지는 i18n fetch 가 없다(= FOUC 0, JS 꺼도 완전한 번역본).
자동 리다이렉트는 하지 않는다 — Google 이 명시적으로 비권고하고, 전환은 main.js 의 제안 배너가 맡는다.

의존성: lxml. 정규식 치환은 쓰지 않는다 — hero.title 처럼 중첩 <span> 을 품은 값이 있어 반드시 DOM 조작이어야 한다.

dist-lang/ 은 생성물이지만 레포에 커밋한다 — Dockerfile 이 `COPY dist-lang/` 로 하드 의존하고 배포가
레포 트리를 그대로 옮기기 때문이다. 그래서 index.html·사전을 고치고 재실행을 빠뜨리면 7개 언어가 조용히 낡는다.
`--check` 는 그 stale 여부만 판정한다(파일 쓰기 없음, 낡았으면 exit 1).

사용법:  python3 website/tools/build_i18n_pages.py [--check]
"""
import datetime
import json
import os
import re
import shutil
import subprocess
import sys

import lxml.html
from lxml import etree

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
I18N_DIR = os.path.join(ROOT, "assets", "i18n")
OUT_DIR = os.path.join(ROOT, "dist-lang")
SITEMAP = os.path.join(ROOT, "sitemap.xml")

SITE = "https://camcon.inik.kr"
ROOT_LANG = "ko"                                        # 루트(/)의 언어
BUILD_LANGS = ["en", "ja", "zh", "de", "es", "fr", "it"]  # 하위 디렉터리로 생성할 언어
ALL_LANGS = [ROOT_LANG] + BUILD_LANGS                   # hreflang · sitemap 대상
X_DEFAULT = "en"                                        # 8개 밖 언어권이 받을 페이지

# 언어권별 OG 로케일. 페이지 언어와 1:1 이므로 표를 늘릴 때 BUILD_LANGS 와 같이 늘린다.
OG_LOCALE = {
    "ko": "ko_KR", "en": "en_US", "ja": "ja_JP", "zh": "zh_CN",
    "de": "de_DE", "es": "es_ES", "fr": "fr_FR", "it": "it_IT",
}

# 생성 페이지의 소셜 카드. 한글이 픽셀로 구워진 og-card.png 대신 영문 카드를 쓴다
# (ja/zh/de/… 전용 카드는 아직 없어 초기엔 영문 카드를 공유한다).
OG_IMAGE = SITE + "/assets/img/og-card-en.png"

# 절대경로화 대상 속성. "assets/ 접두"로 좁히면 favicon.svg 같은 루트 상대경로를 놓친다.
# data-after/data-before 는 main.js 가 그대로 img.src 에 넣으므로 src 와 같은 취급이어야 한다.
URL_ATTRS = ("src", "href", "poster", "data-after", "data-before")
ABSOLUTE_RE = re.compile(r"^(?:[a-z][a-z0-9+.\-]*:|//|/|#)", re.I)

# lxml 의 HTML 직렬화가 속성명을 소문자로 내리는데 SVG 는 대소문자를 구분한다.
# 브라우저는 viewbox→viewBox 를 보정해 주지만 원문 그대로 돌려놓는 편이 안전하다.
SVG_CASE_FIX = {" viewbox=": " viewBox="}

# 생성 대상 페이지.
#   src   : 한국어 정본 파일명(= 언어 디렉터리 안에서도 같은 이름으로 생성된다)
#   slug  : URL 에서 언어 접두 뒤에 붙는 부분. 홈은 빈 문자열이라 "/" · "/en/" 이 된다.
#   title/desc: 이 페이지의 <title>·description 을 담은 사전 키(둘 다 data-i18n 이 없는 자리에 쓰인다)
#   prio  : sitemap 우선순위(루트 언어는 여기에 +0.1 한 값을 쓴다)
PAGES = [
    {"src": "index.html", "slug": "", "title": "meta.title", "desc": "meta.desc", "prio": 0.9},
    {"src": "guide.html", "slug": "guide.html", "title": "guide.meta.title", "desc": "guide.meta.desc", "prio": 0.7},
]


def template_path(page):
    return os.path.join(ROOT, page["src"])


def page_path(lang, slug):
    base = "/" if lang == ROOT_LANG else "/%s/" % lang
    return base + slug


def page_url(lang, slug):
    return SITE + page_path(lang, slug)


def load_dict(lang):
    with open(os.path.join(I18N_DIR, lang + ".json"), encoding="utf-8") as f:
        return json.load(f)


def plain(value):
    """og:title 등 content 속성에 넣을 순수 텍스트. 사전 값에 태그가 섞여 있어도 벗겨낸다."""
    if "<" not in value:
        return value
    return lxml.html.fragment_fromstring(value, create_parent="span").text_content()


def set_inner_html(el, html):
    """main.js:applyI18n 과 같은 시맨틱 — data-i18n-attr 이 없으면 innerHTML 교체."""
    tail = el.tail
    for child in list(el):
        el.remove(child)
    holder = lxml.html.fragment_fromstring(html, create_parent="span")
    el.text = holder.text
    for child in list(holder):
        el.append(child)
    el.tail = tail


def apply_dict(tree, dct):
    """(a) [data-i18n] 순회. 번역 없는 키는 인라인 한국어를 남기지 않고 실패로 본다."""
    missing = []
    for el in tree.xpath("//*[@data-i18n]"):
        key = el.get("data-i18n")
        if key not in dct:
            missing.append(key)
            continue
        val = dct[key]
        attr = el.get("data-i18n-attr")
        if attr:
            el.set(attr, val)  # main.js 와 동일하게 값 그대로 — 속성 대상 키는 전부 평문이다
        else:
            set_inner_html(el, val)
    return missing


def set_meta(tree, selector, value):
    nodes = tree.xpath(selector)
    for n in nodes:
        n.set("content", value)
    return len(nodes)


def rebuild_hreflang(tree, slug):
    """(f) 자기참조 + x-default 포함 9줄. 한 페이지의 8개 언어판이 같은 9줄을 갖도록 통째로 다시 깐다.
    hreflang 은 같은 문서의 다른 언어판만 가리켜야 하므로 슬러그가 반드시 유지되어야 한다
    (guide 의 en 대체본은 /en/ 이 아니라 /en/guide.html 이다)."""
    head = tree.find("head")
    canonical = tree.xpath('//link[@rel="canonical"]')
    anchor = canonical[0] if canonical else head[-1]
    for old in tree.xpath('//link[@rel="alternate"][@hreflang]'):
        old.getparent().remove(old)
    at = list(head).index(anchor) + 1
    for i, code in enumerate(ALL_LANGS + ["x-default"]):
        link = etree.Element("link")
        link.set("rel", "alternate")
        link.set("hreflang", code)
        link.set("href", page_url(X_DEFAULT if code == "x-default" else code, slug))
        link.tail = "\n  "
        head.insert(at + i, link)


def absolutize(tree):
    """(g) 스킴·슬래시·해시로 시작하지 않는 모든 참조를 루트 절대경로로. /en/ 하위에서 404 나는 것을 막는다."""
    n = 0
    for el in tree.iter():
        if not isinstance(el.tag, str):
            continue
        if el.get("srcset"):
            print("  [경고] srcset 은 절대경로화 대상이 아니다 — %s" % el.get("srcset")[:60])
        for attr in URL_ATTRS:
            val = el.get(attr)
            if val and not ABSOLUTE_RE.match(val):
                el.set(attr, "/" + val)
                n += 1
    return n


def patch_jsonld(tree, lang, dct, page):
    """(e) JSON-LD 의 name·description·url·image 를 해당 언어로.
    name 은 HowTo 처럼 제목을 갖는 타입에만 있으므로 있을 때만 갈아 끼운다."""
    nodes = tree.xpath('//script[@type="application/ld+json"]')
    for node in nodes:
        data = json.loads(node.text)
        if "name" in data and data.get("@type") != "SoftwareApplication":
            data["name"] = plain(dct[page["title"]]).split(" | ")[0]
        data["description"] = plain(dct[page["desc"]])
        data["url"] = page_url(lang, page["slug"])
        data["image"] = OG_IMAGE
        node.text = "\n  " + json.dumps(data, ensure_ascii=False, indent=2) + "\n  "
    return len(nodes)


def inline_dict(tree, dct):
    """(h) 사전을 </head> 직전에 인라인 → 생성 페이지는 i18n fetch 가 사라진다(FOUC 0)."""
    payload = json.dumps(dct, ensure_ascii=False, separators=(",", ":"))
    payload = payload.replace("</", "<\\/")  # </script> 탈출 방지 (JSON 에서 \/ 는 합법)
    script = etree.Element("script")
    script.text = "window.__I18N__=" + payload + ";"
    script.tail = "\n"
    tree.find("head").append(script)


def mark_current_lang(tree, lang):
    """헤더 select 와 푸터 정적 링크가 현재 언어를 가리키게 한다(JS 없이도 맞아야 한다)."""
    for opt in tree.xpath('//select[@id="lang"]/option'):
        if opt.get("value") == lang:
            opt.set("selected", "selected")
        elif opt.get("selected") is not None:
            del opt.attrib["selected"]
    for a in tree.xpath('//a[@hreflang]'):
        if a.get("hreflang") == lang:
            a.set("aria-current", "page")
        elif a.get("aria-current") is not None:
            del a.attrib["aria-current"]


def strip_comments(tree):
    """생성물은 빌드 산출물이므로 한국어 개발 주석을 남기지 않는다(바이트 절감 + 잔류 한글 검사 노이즈 제거)."""
    for c in tree.xpath("//comment()"):
        c.getparent().remove(c)


def serialize(tree):
    html = lxml.html.tostring(tree, encoding="unicode", doctype="<!DOCTYPE html>")
    for bad, good in SVG_CASE_FIX.items():
        html = html.replace(bad, good)
    return html + "\n"


def render_page(lang, template_src, page):
    """언어 페이지 HTML 을 문자열로 만든다(파일 쓰기 없음) — 생성과 --check 가 같은 경로를 타야 판정이 성립한다."""
    tree = lxml.html.document_fromstring(template_src)
    dct = load_dict(lang)

    missing = apply_dict(tree, dct)                                  # (a)
    tree.set("lang", lang)                                           # (b)

    # (c) 이 7개 태그에는 data-i18n 이 없어 별도 매핑이 필요하다.
    title = plain(dct[page["title"]])
    desc = plain(dct[page["desc"]])
    hit = 0
    hit += set_meta(tree, '//meta[@property="og:locale"]', OG_LOCALE[lang])
    hit += set_meta(tree, '//meta[@property="og:title"]', title)
    hit += set_meta(tree, '//meta[@property="og:description"]', desc)
    hit += set_meta(tree, '//meta[@name="twitter:title"]', title)
    hit += set_meta(tree, '//meta[@name="twitter:description"]', desc)
    hit += set_meta(tree, '//meta[@property="og:image"]', OG_IMAGE)
    hit += set_meta(tree, '//meta[@name="twitter:image"]', OG_IMAGE)

    # (d) 언어별 URL
    set_meta(tree, '//meta[@property="og:url"]', page_url(lang, page["slug"]))
    for c in tree.xpath('//link[@rel="canonical"]'):
        c.set("href", page_url(lang, page["slug"]))

    ld = patch_jsonld(tree, lang, dct, page)                         # (e)
    rebuild_hreflang(tree, page["slug"])                             # (f)
    n_abs = absolutize(tree)                                         # (g)
    inline_dict(tree, dct)                                           # (h)
    mark_current_lang(tree, lang)
    strip_comments(tree)

    if missing:
        print("  [경고] %s 사전에 없는 키 %d개: %s" % (lang, len(missing), missing[:5]))
    if hit != 7 or ld != 1:
        print("  [경고] %s 메타 매핑 %d/7, JSON-LD %d/1" % (lang, hit, ld))
    return serialize(tree), n_abs, len(dct)


def out_path(lang, page):
    return os.path.join(OUT_DIR, lang, page["src"])


def build_page(lang, template_src, page):
    html, n_abs, n_keys = render_page(lang, template_src, page)
    out = out_path(lang, page)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        f.write(html)
    print("작성: %s  (절대경로화 %d건, 사전 %d키)" % (out, n_abs, n_keys))


# ── sitemap ────────────────────────────────────────────────────────────────
def git_date(rel):
    try:
        p = subprocess.run(["git", "log", "-1", "--format=%cs", "--", rel],
                           cwd=ROOT, capture_output=True, text=True, timeout=15)
        d = p.stdout.strip()
        if p.returncode != 0 or not d:
            return None
        q = subprocess.run(["git", "status", "--porcelain", "--", rel],
                           cwd=ROOT, capture_output=True, text=True, timeout=15)
        if q.stdout.strip():  # 미커밋 수정본이 배포되므로 오늘로 본다
            return datetime.date.today().isoformat()
        return d
    except (OSError, subprocess.SubprocessError):
        return None


def last_modified(*rel_paths):
    """git 커밋 날짜 우선, git 이 없으면 mtime. 여러 입력이면 가장 최신."""
    dates = []
    for rel in rel_paths:
        d = git_date(rel)
        if d is None:
            ts = os.path.getmtime(os.path.join(ROOT, rel))
            d = datetime.date.fromtimestamp(ts).isoformat()
        dates.append(d)
    return max(dates)


def write_sitemap():
    lines = ['<?xml version="1.0" encoding="UTF-8"?>',
             '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"',
             '        xmlns:xhtml="http://www.w3.org/1999/xhtml">']

    n_url = 0
    for page in PAGES:
        alternates = []
        for code in ALL_LANGS + ["x-default"]:
            href = page_url(X_DEFAULT if code == "x-default" else code, page["slug"])
            alternates.append('    <xhtml:link rel="alternate" hreflang="%s" href="%s"/>' % (code, href))

        for lang in ALL_LANGS:
            mod = last_modified(page["src"], "assets/i18n/%s.json" % lang)
            prio = page["prio"] + (0.1 if lang == ROOT_LANG else 0.0)
            lines += ["  <url>",
                      "    <loc>%s</loc>" % page_url(lang, page["slug"]),
                      "    <lastmod>%s</lastmod>" % mod,
                      "    <changefreq>weekly</changefreq>",
                      "    <priority>%.1f</priority>" % prio]
            lines += alternates
            lines.append("  </url>")
            n_url += 1

    # 법적 페이지 3종은 지금까지 sitemap 에 아예 없었다.
    for legal in ("privacy.html", "terms.html", "delete.html"):
        lines += ["  <url>",
                  "    <loc>%s/%s</loc>" % (SITE, legal),
                  "    <lastmod>%s</lastmod>" % last_modified(legal),
                  "    <changefreq>yearly</changefreq>",
                  "    <priority>0.3</priority>",
                  "  </url>"]
        n_url += 1

    lines.append("</urlset>")
    with open(SITEMAP, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print("작성: %s  (URL %d건)" % (SITEMAP, n_url))


# ── 자체 검증 ──────────────────────────────────────────────────────────────
def verify():
    """hreflang return-link 대칭성 — 한쪽이라도 깨지면 Google 이 hreflang 전체를 무시한다.
    페이지마다 독립된 8개 언어 묶음이므로 묶음별로 따로 본다(홈의 대체본이 guide 를 가리키면 안 된다)."""
    ok = True
    n_checked = 0
    for page in PAGES:
        slug = page["slug"]
        paths = {ROOT_LANG: template_path(page)}
        for lang in BUILD_LANGS:
            paths[lang] = out_path(lang, page)

        declared = {}
        for lang, path in paths.items():
            tree = lxml.html.parse(path).getroot()
            links = {l.get("hreflang"): l.get("href")
                     for l in tree.xpath('//link[@rel="alternate"][@hreflang]')}
            declared[lang] = links
            canon = tree.xpath('//link[@rel="canonical"]/@href')
            if canon[:1] != [page_url(lang, slug)]:
                print("  [실패] %s/%s canonical=%s" % (page["src"], lang, canon)); ok = False
            if links.get(lang) != page_url(lang, slug):
                print("  [실패] %s/%s 자기참조 hreflang 없음" % (page["src"], lang)); ok = False
            if links.get("x-default") != page_url(X_DEFAULT, slug):
                print("  [실패] %s/%s x-default 불일치" % (page["src"], lang)); ok = False

        expected = {c: page_url(X_DEFAULT if c == "x-default" else c, slug)
                    for c in ALL_LANGS + ["x-default"]}
        for lang, links in declared.items():
            if links != expected:
                print("  [실패] %s/%s hreflang 집합 불일치" % (page["src"], lang)); ok = False
        n_checked += len(paths)
    print("검증: hreflang %d페이지 × 9줄 대칭 %s" % (n_checked, "OK" if ok else "FAIL"))
    return ok


def check_pages(sources):
    """커밋된 dist-lang 이 지금의 원본·사전과 일치하는지만 본다(쓰기 없음).
    sitemap 은 대상에서 뺀다 — lastmod 가 git 상태에 따라 '오늘' 로 흔들려 stale 오탐이 난다."""
    stale = []
    n = 0
    for page in PAGES:
        for lang in BUILD_LANGS:
            n += 1
            html = render_page(lang, sources[page["src"]], page)[0]
            try:
                with open(out_path(lang, page), encoding="utf-8") as f:
                    current = f.read()
            except OSError:
                stale.append("%s/%s(없음)" % (lang, page["src"]))
                continue
            if current != html:
                stale.append("%s/%s" % (lang, page["src"]))
    if stale:
        print("  [실패] dist-lang 이 최신이 아니다: %s" % ", ".join(stale))
        print("         python3 tools/build_i18n_pages.py 재실행 후 dist-lang 을 함께 커밋할 것.")
        return False
    print("검증: dist-lang %d페이지 최신 OK" % n)
    return True


def main():
    args = sys.argv[1:]
    if args and args != ["--check"]:
        print("사용법: python3 build_i18n_pages.py [--check]")
        sys.exit(2)

    sources = {}
    for page in PAGES:
        with open(template_path(page), encoding="utf-8") as f:
            sources[page["src"]] = f.read()

    if args == ["--check"]:
        if not check_pages(sources) or not verify():
            sys.exit(1)
        return

    if os.path.isdir(OUT_DIR) and os.path.basename(OUT_DIR) == "dist-lang":
        shutil.rmtree(OUT_DIR)  # 언어·페이지를 뺐을 때 옛 산출물이 남지 않게

    for page in PAGES:
        for lang in BUILD_LANGS:
            build_page(lang, sources[page["src"]], page)
    write_sitemap()
    if not verify():
        sys.exit(1)


if __name__ == "__main__":
    main()
