#!/usr/bin/env python3
"""CamCon 법적 페이지 빌더 — 워크플로 산출(개인정보처리방침·이용약관·계정삭제, ko+en)을
사이트 다크테마 정적 HTML(privacy.html / terms.html / delete.html)로 조립한다.

입력: website/tools/legal_content.json  (형식: {privacy:{koHtml,enHtml}, terms:{...}, deletion:{...}, effectiveDate})
출력: website/privacy.html, website/terms.html, website/delete.html
각 페이지는 ko 기본 + en 토글(의존성 0 인라인 JS). Play Console 개인정보처리방침/계정삭제 URL로 사용.
"""
import os, json

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
DATA = os.path.join(os.path.dirname(__file__), "legal_content.json")

STYLE = """
 :root{--bg:#0A0A0C;--panel:#111418;--ink:#F2F2F0;--mute:#9A9AA0;--amber:#EBA23C;--hair:rgba(255,255,255,.09)}
 *{box-sizing:border-box}
 body{margin:0;background:var(--bg);color:var(--ink);font-family:Pretendard,-apple-system,BlinkMacSystemFont,sans-serif;line-height:1.75;-webkit-font-smoothing:antialiased}
 a{color:var(--amber);text-decoration:none} a:hover{text-decoration:underline}
 .lhead{position:sticky;top:0;background:rgba(10,10,12,.82);backdrop-filter:blur(10px);-webkit-backdrop-filter:blur(10px);border-bottom:1px solid var(--hair);z-index:5}
 .lhead .in{max-width:860px;margin:0 auto;padding:15px 24px;display:flex;align-items:center;justify-content:space-between}
 .brand{display:flex;align-items:center;gap:9px;font-weight:700;font-size:18px;color:var(--ink)}
 .brand b{color:var(--amber)}
 .home{font-size:14px;color:var(--mute)}
 .wrap{max-width:860px;margin:0 auto;padding:44px 24px 72px}
 .lang{display:flex;gap:8px;margin:0 0 30px}
 .lang button{font:inherit;font-size:13px;font-weight:600;padding:7px 16px;border-radius:8px;border:1px solid var(--hair);background:transparent;color:var(--mute);cursor:pointer}
 .lang button.on{background:var(--amber);color:#1a1206;border-color:var(--amber)}
 h1{font-size:clamp(24px,5vw,31px);font-weight:700;margin:0 0 6px;letter-spacing:-.02em}
 .eff{color:var(--mute);font-size:14px;margin:0 0 30px}
 h2{font-size:20px;font-weight:700;margin:40px 0 10px;padding-top:22px;border-top:1px solid var(--hair);letter-spacing:-.01em}
 h3{font-size:16px;font-weight:600;margin:22px 0 8px;color:#e8e8ea}
 p{margin:10px 0;color:#d7d7da}
 ul,ol{margin:10px 0;padding-left:22px} li{margin:5px 0;color:#d7d7da}
 strong{color:var(--ink);font-weight:600}
 table{width:100%;border-collapse:collapse;margin:16px 0;font-size:13.5px}
 th,td{border:1px solid var(--hair);padding:9px 12px;text-align:left;vertical-align:top}
 th{background:var(--panel);color:var(--ink);font-weight:600}
 mark{background:rgba(235,162,60,.18);color:#f3c778;padding:0 4px;border-radius:3px}
 .foot{border-top:1px solid var(--hair);margin-top:44px;padding:28px 24px;text-align:center;color:var(--mute);font-size:13px}
 .foot a{margin:0 7px;color:var(--mute)} .foot a:hover{color:var(--amber)}
 [hidden]{display:none}
"""

BRAND_SVG = ('<svg width="26" height="26" viewBox="0 0 48 48" fill="none" aria-hidden="true">'
             '<circle cx="24" cy="24" r="16.5" stroke="#EBA23C" stroke-width="2.4"/>'
             '<path d="M35 24 L29.5 33.5 L18.5 33.5 L13 24 L18.5 14.5 L29.5 14.5 Z" stroke="#F7C266" stroke-width="1.4" stroke-linejoin="round" opacity="0.85"/>'
             '<circle cx="24" cy="24" r="3.4" fill="#EBA23C"/></svg>')

FOOT = ('<div><a href="/privacy.html">개인정보처리방침</a> · <a href="/terms.html">이용약관</a> · '
        '<a href="/delete.html">계정·데이터 삭제</a> · <a href="mailto:ppp5544@gmail.com">문의</a></div>'
        '<div style="margin-top:10px">© 2026 CamCon</div>')

TOGGLE_JS = ("var bs=document.querySelectorAll('.lang button'),"
             "ko=document.querySelector('[data-doc=ko]'),en=document.querySelector('[data-doc=en]');"
             "for(var i=0;i<bs.length;i++){bs[i].onclick=function(){"
             "for(var j=0;j<bs.length;j++)bs[j].classList.remove('on');this.classList.add('on');"
             "var e=this.getAttribute('data-l')==='en';en.hidden=!e;ko.hidden=e;"
             "document.documentElement.lang=this.getAttribute('data-l');"
             "window.scrollTo(0,0);};}")


def page(file, title_ko, title_en, ko_html, en_html, date):
    return f"""<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>{title_ko} — CamCon</title>
<meta name="robots" content="index,follow">
<meta name="theme-color" content="#0A0A0C">
<link rel="canonical" href="https://camcon.inik.kr/{file}">
<link rel="icon" type="image/svg+xml" href="favicon.svg">
<link rel="preconnect" href="https://cdn.jsdelivr.net" crossorigin>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/variable/pretendardvariable-dynamic-subset.min.css">
<style>{STYLE}</style>
</head>
<body>
<header class="lhead"><div class="in">
  <a class="brand" href="/">{BRAND_SVG}<span>Cam<b>Con</b></span></a>
  <a class="home" href="/">← 홈으로</a>
</div></header>
<main class="wrap">
  <div class="lang">
    <button class="on" data-l="ko">한국어</button>
    <button data-l="en">English</button>
  </div>
  <article data-doc="ko">
    <h1>{title_ko}</h1>
    <p class="eff">시행일: {date}</p>
    {ko_html}
  </article>
  <article data-doc="en" hidden>
    <h1>{title_en}</h1>
    <p class="eff">Effective date: {date}</p>
    {en_html}
  </article>
</main>
<footer class="foot">{FOOT}</footer>
<script>{TOGGLE_JS}</script>
</body>
</html>
"""


# 인벤토리 정합 보강: 계정 유지 부가필드(lastLoginAt/isActive/totalReferrals) 명시 + 기기정보 미저장 고지
KO_ANCHOR = "<p>이용자가 Google 계정으로 로그인할 때 인증 기능을 통해 수집됩니다.</p>"
KO_ADD = (KO_ANCHOR + "\n<p>또한 계정 유지·관리를 위해 마지막 로그인 시각, 계정 활성 상태, "
          "누적 추천 수가 함께 저장됩니다. 다만 이용자의 기기 모델·운영체제(OS) 버전·앱 버전 등 "
          "기기 상세 정보는 이용자 프로필에 저장하지 않습니다.</p>")
EN_ANCHOR = "<p>Collected through the authentication process when a user signs in with a Google account.</p>"
EN_ADD = (EN_ANCHOR + "\n<p>In addition, the last sign-in time, account active status, and total "
          "referral count are stored to maintain the account. However, device details such as your "
          "device model, OS version, and app version are not stored in your user profile.</p>")


def main():
    r = json.load(open(DATA, encoding="utf-8"))
    date = r.get("effectiveDate", "2026-07-08")

    pv_ko = r["privacy"]["koHtml"].replace(KO_ANCHOR, KO_ADD)
    pv_en = r["privacy"]["enHtml"].replace(EN_ANCHOR, EN_ADD)
    if KO_ADD not in pv_ko:
        print("[경고] ko privacy 앵커 미매치 — 부가필드 문구 미삽입")
    if EN_ADD not in pv_en:
        print("[경고] en privacy 앵커 미매치 — 부가필드 문구 미삽입")

    docs = [
        ("privacy.html", "개인정보처리방침", "Privacy Policy", pv_ko, pv_en),
        ("terms.html", "이용약관", "Terms of Service", r["terms"]["koHtml"], r["terms"]["enHtml"]),
        ("delete.html", "계정·데이터 삭제 요청", "Account & Data Deletion", r["deletion"]["koHtml"], r["deletion"]["enHtml"]),
    ]
    for file, tko, ten, ko, en in docs:
        out = os.path.join(ROOT, file)
        open(out, "w", encoding="utf-8").write(page(file, tko, ten, ko, en, date))
        print(f"작성: {out}  (ko {len(ko)}자 / en {len(en)}자)")


if __name__ == "__main__":
    main()
