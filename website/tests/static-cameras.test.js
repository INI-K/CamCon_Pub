"use strict";
/* 지원 기종 정적 노출 정합성 — index.html 에 구운 목록·숫자·큐레이션이 supported-cameras.json 과
   어긋나면 홈페이지가 조용히 거짓말을 하게 되므로 여기서 강제한다.
   실행: node --test website/tests/ */
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const ROOT = path.join(__dirname, "..");
const catalog = JSON.parse(fs.readFileSync(path.join(ROOT, "assets", "data", "supported-cameras.json"), "utf8"));
const indexHtml = fs.readFileSync(path.join(ROOT, "index.html"), "utf8");
const LANGS = ["ko", "en", "ja", "zh", "de", "es", "fr", "it"];
const dicts = Object.fromEntries(LANGS.map((l) => [
  l, JSON.parse(fs.readFileSync(path.join(ROOT, "assets", "i18n", l + ".json"), "utf8")),
]));

function staticBlock() {
  const m = indexHtml.match(/<!-- CAM-STATIC:BEGIN[^>]*-->([\s\S]*?)<!-- CAM-STATIC:END -->/);
  assert.ok(m, "index.html 에 CAM-STATIC 마커가 있어야 한다");
  return m[1];
}

test("정적 목록 <li> 수가 카탈로그 total 과 일치한다 (inject_camera_list.py 재실행 누락 감지)", () => {
  const rows = staticBlock().match(/<li>/g) || [];
  assert.equal(rows.length, catalog.total);
  assert.equal(catalog.cameras.length, catalog.total);
});

test("정적 목록의 Wi-Fi 배지 수가 카탈로그의 wifi 플래그 수와 일치한다", () => {
  const wifiRows = staticBlock().match(/<span class="cam-wifi">/g) || [];
  const wifiCatalog = catalog.cameras.filter((c) => c.wifi);
  assert.equal(wifiRows.length, wifiCatalog.length);
});

test("탐색기 헤더 숫자(camTotal)가 정적으로 주입되어 total 과 일치한다", () => {
  const m = indexHtml.match(/<span class="num" id="camTotal">([^<]*)<\/span>/);
  assert.ok(m, "camTotal 요소가 있어야 한다");
  assert.equal(m[1], String(catalog.total));
});

test("대표 기종 큐레이션(data-cam)은 전부 카탈로그에 실존한다", () => {
  const cams = [...indexHtml.matchAll(/<li data-cam="([^"]+)">([\s\S]*?)<\/li>/g)];
  assert.ok(cams.length >= 8, "큐레이션 카드는 8개 이상이어야 한다");
  const known = new Map(catalog.cameras.map((c) => [c.vendor + " " + c.model, c]));
  for (const [, key] of cams) {
    assert.ok(known.has(key), `카탈로그에 없는 큐레이션 기종: ${key}`);
  }
});

/* 큐레이션 카드의 Wi-Fi 배지 근거 목록 — 카드에 무선을 표기하려면 여기에 근거와 함께 추가해야 한다.
   근거 = 개발 실기 검증 또는 getVerifiedCameras 실사용 보고(wifi:true). 추측·드라이버 등재만으로는 불가. */
const WIFI_VERIFIED = new Set([
  "Nikon Z8",                    // 개발 실기 검증 (USB·Wi-Fi) + 실사용 보고 nikon:z8
  "Nikon Z6",                    // 개발 실기 검증 (USB·Wi-Fi) + 실사용 보고 nikon:z6
  "Sony ILCE-7C (Control)",      // 실사용 보고 sony:ilce7c (usb+wifi, 2026-08-29 실측)
  "Sony Alpha-A7 IV",            // 실사용 보고 sony:alphaa7iv (wifi, 2026-08-29 실측)
]);

test("큐레이션 Wi-Fi 배지는 근거 목록(WIFI_VERIFIED)에 있어야 한다 (비검증 무선 표기 금지)", () => {
  const known = new Map(catalog.cameras.map((c) => [c.vendor + " " + c.model, c]));
  for (const [, key, body] of indexHtml.matchAll(/<li data-cam="([^"]+)">([\s\S]*?)<\/li>/g)) {
    const claimsWifi = /Wi-Fi/.test(body);
    const allowed = WIFI_VERIFIED.has(key) || !!(known.get(key) || {}).wifi;
    if (claimsWifi) assert.ok(allowed, `${key}: 근거 없는 Wi-Fi 배지`);
    // 카탈로그 wifi 큐레이션(Nikon Z)에 든 카드는 무선을 감추지 않는다.
    if ((known.get(key) || {}).wifi) assert.ok(claimsWifi, `${key}: 카탈로그 wifi 기종인데 배지 누락`);
  }
});

test("hero.brands 는 8개 언어 전부에 있고 total 숫자를 담는다", () => {
  for (const lang of LANGS) {
    const v = dicts[lang]["hero.brands"];
    assert.ok(v, `${lang}: hero.brands 키 누락`);
    assert.ok(v.includes(String(catalog.total)), `${lang}: hero.brands 에 ${catalog.total} 없음`);
  }
});

test("meta.desc 는 8개 언어 전부에서 total 숫자를 담고, cameras.topTitle 키가 있다", () => {
  for (const lang of LANGS) {
    assert.ok(dicts[lang]["meta.desc"].includes(String(catalog.total)), `${lang}: meta.desc 에 total 없음`);
    assert.ok(dicts[lang]["cameras.topTitle"], `${lang}: cameras.topTitle 키 누락`);
  }
});

test("8개 언어 사전의 키 집합이 동일하다 (재생성 규약)", () => {
  const base = Object.keys(dicts.ko).sort();
  for (const lang of LANGS.slice(1)) {
    assert.deepEqual(Object.keys(dicts[lang]).sort(), base, `${lang}: 키 집합이 ko 와 다르다`);
  }
});

test("index.html 인라인 한국어(hero.brands·meta.desc)가 ko 사전과 일치한다", () => {
  // 런타임에 ko.json 이 덮어써도 화면이 변하지 않아야 한다(FOUC·SEO 원문 불일치 방지).
  const hero = indexHtml.match(/data-i18n="hero.brands">([\s\S]*?)<\/a>/);
  assert.ok(hero, "hero.brands 요소가 있어야 한다");
  assert.equal(hero[1].trim(), dicts.ko["hero.brands"]);
  const desc = indexHtml.match(/data-i18n="meta.desc" content="([^"]+)"/);
  assert.equal(desc[1], dicts.ko["meta.desc"]);
});
