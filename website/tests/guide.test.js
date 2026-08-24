"use strict";
/* 연결 안내 페이지(guide.html) — 원본·사전·생성물 세 곳이 어긋나는 것을 막는다.
   실행: node --test website/tests/

   이 페이지는 사람이 손으로 적은 기종 목록과, 생성기가 만든 7개 언어판을 함께 갖고 있어서
   한쪽만 고쳐도 조용히 어긋난다. 특히 기종 목록은 WIFI_SUPPORTED(생성기 상수)가 정본인데
   HTML 에 사본이 있으므로, 배지가 늘어난 뒤 안내 페이지만 옛 목록으로 남는 사고가 나기 쉽다. */
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const WEB = path.join(__dirname, "..");
const guide = fs.readFileSync(path.join(WEB, "guide-nikon.html"), "utf8");
/** 허브 + 제조사 4종. 언어판이 만들어지는 안내 페이지 전부. */
const GUIDE_PAGES = ["guide.html", "guide-usb.html",
                     "guide-usb-nikon.html", "guide-usb-canon.html",
                     "guide-usb-sony.html", "guide-usb-fujifilm.html",
                     "guide-nikon.html", "guide-canon.html",
                     "guide-sony.html", "guide-fujifilm.html"];
const catalog = JSON.parse(
  fs.readFileSync(path.join(WEB, "assets", "data", "supported-cameras.json"), "utf8")
);

const BUILD_LANGS = ["en", "ja", "zh", "de", "es", "fr", "it"];

/** guide.html 의 기종 칩에 박힌 data-model 값. */
function listedModels() {
  return [...guide.matchAll(/<li data-model="([^"]+)"/g)].map((m) => m[1]);
}

test("기종 목록이 supported-cameras.json 의 무선 지원 기종과 일치한다", () => {
  const fromJson = catalog.cameras
    .filter((c) => c.wifi === true)
    .map((c) => c.model)
    .sort();
  assert.deepEqual(
    listedModels().sort(),
    fromJson,
    "generate_supported_cameras.py 의 WIFI_SUPPORTED 를 바꿨다면 guide.html 의 기종 칩도 같이 고쳐야 한다"
  );
});

test("기종 칩에는 니콘만 있다", () => {
  // 무선은 니콘만 실기 확인됐다. 다른 제조사가 섞여 들어오면 안내 문구(니콘 전용)와 어긋난다.
  const nikon = new Set(
    catalog.cameras.filter((c) => c.wifi === true && c.vendor === "Nikon").map((c) => c.model)
  );
  for (const m of listedModels()) {
    assert.ok(nikon.has(m), `니콘 무선 기종이 아니다: ${m}`);
  }
});

test("안내 페이지 5장의 data-i18n 키가 8개 사전에 모두 있다", () => {
  const all = GUIDE_PAGES.map((f) => fs.readFileSync(path.join(WEB, f), "utf8")).join("\n");
  const keys = [...new Set([...all.matchAll(/data-i18n="([^"]+)"/g)].map((m) => m[1]))];
  assert.ok(keys.length > 50, `키가 너무 적다(${keys.length}개) — 추출 정규식을 확인할 것`);

  const dir = path.join(WEB, "assets", "i18n");
  for (const lang of ["ko", ...BUILD_LANGS]) {
    const dict = JSON.parse(fs.readFileSync(path.join(dir, `${lang}.json`), "utf8"));
    const missing = keys.filter((k) => !(k in dict));
    assert.deepEqual(missing, [], `${lang}.json 에 없는 키`);
  }
});

test("생성된 언어별 guide 에 한국어 원문이 남아 있지 않다", () => {
  /* "한글이 한 글자도 없어야 한다"로는 검사할 수 없다 — 언어 선택기의 항목 이름(한국어)과
     이중 언어 aria-label 은 어느 언어판에서도 한글이어야 맞다.
     그래서 "이 페이지의 키에 대한 한국어 값이 그대로 박혀 있는가"만 본다.
     이쪽이 실제로 막고 싶은 사고(사전에 키를 빠뜨려 원문이 노출되는 것)와 정확히 대응한다. */
  const dir = path.join(WEB, "assets", "i18n");
  const ko = JSON.parse(fs.readFileSync(path.join(dir, "ko.json"), "utf8"));
  const guideKeys = Object.keys(ko).filter((k) => k.startsWith("guide.") && /[가-힣]/.test(ko[k]));
  assert.ok(guideKeys.length > 50, `검사 대상 키가 너무 적다(${guideKeys.length}개)`);

  for (const lang of BUILD_LANGS) {
    const p = path.join(WEB, "dist-lang", lang, "guide-nikon.html");
    assert.ok(fs.existsSync(p), `생성물 없음: ${p} (build_i18n_pages.py 를 실행할 것)`);
    const html = fs.readFileSync(p, "utf8");
    const dict = JSON.parse(fs.readFileSync(path.join(dir, `${lang}.json`), "utf8"));
    const leaked = guideKeys.filter((k) => dict[k] !== ko[k] && html.includes(ko[k]));
    assert.deepEqual(leaked, [], `${lang}/guide.html 에 한국어 원문 잔류`);
  }
});

test("언어판 hreflang 이 홈이 아니라 guide 를 가리킨다", () => {
  // 슬러그를 빠뜨리면 8개 언어의 guide 가 전부 홈을 대체본으로 선언해
  // Google 이 hreflang 묶음 전체를 무시한다(가장 알아채기 어려운 실패다).
  for (const lang of BUILD_LANGS) {
    const html = fs.readFileSync(path.join(WEB, "dist-lang", lang, "guide-nikon.html"), "utf8");
    const hrefs = [...html.matchAll(/rel="alternate" hreflang="([^"]+)" href="([^"]+)"/g)];
    assert.equal(hrefs.length, 9, `${lang}: hreflang 9줄이 아니다`);
    for (const [, code, href] of hrefs) {
      assert.ok(href.endsWith("/guide-nikon.html"), `${lang}: ${code} 가 guide 를 가리키지 않는다 (${href})`);
    }
  }
});

test("언어판의 사이트 내부 링크가 같은 언어로 간다", () => {
  /* 절대경로 링크는 언어 접두가 빠져도 404 가 나지 않고 조용히 한국어 문서로 넘어간다.
     프랑스어 홈의 안내 링크가 한국어 안내로 가던 실제 사고를 막는다. */
  for (const lang of BUILD_LANGS) {
    const home = fs.readFileSync(path.join(WEB, "dist-lang", lang, "index.html"), "utf8");
    const toGuide = [...home.matchAll(/href="([^"]*)" data-i18n="footer.guide"/g)].map((m) => m[1]);
    assert.ok(toGuide.length > 0, `${lang}/index.html 에 안내 페이지 링크가 없다`);
    for (const href of toGuide) {
      assert.equal(href, `/${lang}/guide.html`, `${lang} 홈의 안내 링크가 다른 언어로 간다`);
    }

    // guide 쪽 헤더·푸터가 한국어 홈으로 튀지 않는지. 언어 선택 링크(hreflang 보유)는 제외한다.
    const html = fs.readFileSync(path.join(WEB, "dist-lang", lang, "guide-nikon.html"), "utf8");
    const stray = [...html.matchAll(/<a(?![^>]*hreflang)[^>]*href="(\/(?:#|guide[a-z-]*\.html|")[^"]*)"/g)]
      .map((m) => m[1]);
    assert.deepEqual(stray, [], `${lang}/guide.html 에 언어 접두가 빠진 내부 링크`);
  }
});

test("사용자 노출 문구에 em-dash 가 없다", () => {
  // 사전 쪽은 color-transfer.test.js 가 이미 본다. 여기서는 원본 HTML 에 직접 적힌 한국어를 본다.
  const body = guide.replace(/<!--[\s\S]*?-->/g, "");
  assert.equal(/[—–]/.test(body), false, "em-dash 는 콜론이나 접속사로 바꿔야 한다");
});

test("제조사 페이지에 다른 제조사 이름이 새지 않는다", () => {
  /* 공용 문구(준비물·설명서 링크·절전 안내 등)가 니콘 기준으로 쓰여 있어서 캐논·소니·후지
     페이지에 "니콘 Z 시리즈 카메라를 준비하세요"가 그대로 나오던 사고가 있었다.
     키를 공유하는 구조라 한 번 새면 세 페이지가 동시에 틀린다. */
  const VENDORS = {
    nikon: [/니콘/, /Nikon/],
    canon: [/캐논/, /Canon/],
    sony: [/소니/, /Sony/],
    fujifilm: [/후지/, /Fujifilm/],
  };
  // 페이지 → 그 페이지가 언급해도 되는 제조사
  const OWN = {
    "guide-nikon.html": "nikon", "guide-canon.html": "canon",
    "guide-sony.html": "sony", "guide-fujifilm.html": "fujifilm",
    "guide-usb-nikon.html": "nikon", "guide-usb-canon.html": "canon",
    "guide-usb-sony.html": "sony", "guide-usb-fujifilm.html": "fujifilm",
  };

  for (const [file, own] of Object.entries(OWN)) {
    const raw = fs.readFileSync(path.join(WEB, file), "utf8");
    // 개발 주석과 사진 파일 경로는 검사 대상이 아니다(경로에 제조사명이 들어간다).
    const body = raw
      .replace(/<!--[\s\S]*?-->/g, "")
      .replace(/<img[^>]*>/g, "")
      .replace(/href="[^"]*"/g, "");
    for (const [vendor, pats] of Object.entries(VENDORS)) {
      if (vendor === own) continue;
      for (const re of pats) {
        assert.equal(re.test(body), false,
          `${file} 에 다른 제조사(${vendor}) 이름이 있다: ${(body.match(re) || [])[0]}`);
      }
    }
  }
});
