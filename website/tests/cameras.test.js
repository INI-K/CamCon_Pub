"use strict";
/* 홈페이지 카메라 탐색기 — "목록 외 실사용 확인" 로직 검증.
   실행: node --test website/tests/
   실제 supported-cameras.json(945종)과 실서버 응답 형태({key, model, usb, wifi})를 그대로 쓴다. */
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const dom = require("./dom-stub");
const site = require("../assets/js/main.js");
const catalog = JSON.parse(fs.readFileSync(path.join(__dirname, "..", "assets", "data", "supported-cameras.json"), "utf8"));

// CF 의 toDisplayModel 을 그대로 가져와 서버 정제 → 클라 표시까지 한 경로로 검증한다.
const cfSrc = fs.readFileSync(path.join(__dirname, "..", "..", "functions", "index.js"), "utf8");
const i = cfSrc.indexOf("function toDisplayModel");
const toDisplayModel = new Function(cfSrc.slice(i, cfSrc.indexOf("\n}\n", i) + 3) + "; return toDisplayModel;")();

function report(entries) {
  // entries: [rawModel, {usb, wifi}] — 서버가 저장·정제해 내려주는 형태로 변환
  const map = {};
  for (const [raw, flags] of entries) {
    const key = site.normalizeCameraKey(raw);
    map[key] = { usb: !!flags.usb, wifi: !!flags.wifi, model: toDisplayModel(raw) };
  }
  return map;
}

test.beforeEach(() => {
  dom.reset();
  site.setCamData(catalog);
  site.setI18n({ "cameras.verified": "사용 확인됨", "cameras.verifiedTpl": "실사용 확인 {n}종" });
  site.setVerified({});
});

test("KEEP-IN-SYNC 블록이 functions/index.js 와 바이트 동일하다", () => {
  const web = fs.readFileSync(path.join(__dirname, "..", "assets", "js", "main.js"), "utf8");
  const cut = (s) => { const a = s.indexOf("/* KEEP IN SYNC"); const b = s.indexOf("return KEY_ALIASES[key] || key;\n}", a); return s.slice(a, b); };
  assert.equal(cut(web), cut(cfSrc));
});

test("실서버 3종(카탈로그 등재)은 목록 외로 분류되지 않고 N종에만 집계된다", () => {
  site.setVerified(report([
    ["Canon EOS R100", { usb: true }],
    ["Nikon DSC D3300", { usb: true }],
    ["Sony ILCE-6400 (PC Control)", { usb: true }],
  ]));
  assert.deepEqual(site.unlistedVerified(), []);
  assert.equal(site.countVerifiedMatches(), 3);
  site.renderUnlisted();
  assert.equal(dom.elements.camUnlisted.hidden, true);
  assert.equal(dom.elements.camUnlistedList.innerHTML, "");
});

test("카탈로그에 없는 기종은 목록 외 블록에 벤더/모델/방식으로 렌더된다", () => {
  site.setVerified(report([
    ["Canon EOS R100", { usb: true }],
    ["Nikon Corporation ZZZ 9000", { usb: true, wifi: true }],
  ]));
  const items = site.unlistedVerified();
  assert.equal(items.length, 1);
  assert.equal(items[0].key, "nikon:zzz9000");
  site.renderUnlisted();
  assert.equal(dom.elements.camUnlisted.hidden, false);
  const html = dom.elements.camUnlistedList.innerHTML;
  assert.match(html, /<span class="cam-vendor">Nikon<\/span>/);
  assert.match(html, /<span class="cam-model">ZZZ 9000<\/span>/);
  assert.match(html, /사용 확인됨 · USB · Wi-Fi/);
});

test("목록 외 항목은 헤드라인 N종에 합산되지 않는다(신뢰 지표는 카탈로그 매칭분만)", () => {
  site.setVerified(report([
    ["Canon EOS R100", { usb: true }],
    ["Nikon Corporation ZZZ 9000", { usb: true }],
    ["Nikon Corporation ZZZ 9001", { usb: true }],
  ]));
  assert.equal(site.countVerifiedMatches(), 1);
});

test("제조사 문자열 폴백(RICOH IMAGING…)도 브랜드 기준으로 카탈로그에 매칭되거나 올바르게 라벨링된다", () => {
  site.setVerified(report([
    ["RICOH IMAGING COMPANY, LTD. RICOH GR III", { wifi: true }],       // 카탈로그 Ricoh GR III 와 매칭돼야
    ["RICOH IMAGING COMPANY, LTD. PENTAX K-3 Mark III", { usb: true }], // 미등재 → Pentax | K-3 Mark III
    ["CASIO COMPUTER CO.,LTD. EX-ZR3600", { usb: true }],               // 콤마 동의어 → Casio | EX-ZR3600
  ]));
  assert.equal(site.normalizeCameraKey("RICOH IMAGING COMPANY, LTD. RICOH GR III"), "ricoh:griii");
  const keys = site.unlistedVerified().map((x) => x.key);
  assert.deepEqual(keys, ["casio:exzr3600", "pentax:k3markiii"]);
  site.renderUnlisted();
  const html = dom.elements.camUnlistedList.innerHTML;
  assert.match(html, /Pentax<\/span><span class="cam-model">K-3 Mark III</);
  assert.match(html, /Casio<\/span><span class="cam-model">EX-ZR3600</);
  assert.doesNotMatch(html, /COMPANY/);
});

test("표시명이 비어 있는 문서(구 CF 응답)는 정규화 키를 이름으로 내보내지 않고 건너뛴다", () => {
  site.setVerified({ "canon:eosr1": { usb: true, wifi: false, model: "" } });
  assert.deepEqual(site.unlistedVerified(), []);
  site.renderUnlisted();
  assert.equal(dom.elements.camUnlisted.hidden, true);
});

test("표시명에 담긴 마크업은 서버 정제 + 클라 이스케이프로 무해화된다", () => {
  site.setVerified(report([["Fujifilm X-T99 <img src=x onerror=alert(1)>", { wifi: true }]]));
  site.renderUnlisted();
  const html = dom.elements.camUnlistedList.innerHTML;
  // 태그·속성 형태(<img, onerror=)는 살아남으면 안 되고, 남는 건 공백으로 분해된 무해한 텍스트뿐이어야 한다.
  assert.doesNotMatch(html, /<img|onerror=|<script/i);
  assert.match(html, /<span class="cam-model">X-T99 img src x onerror alert\(1\)<\/span>/);
  // 클라 이스케이프 자체 확인 — 서버 정제를 우회한 값이 와도 태그로 해석되지 않는다.
  site.setVerified({ "nikon:zzz1": { usb: true, wifi: false, model: 'Nikon <b onclick="x">Z</b>' } });
  site.renderUnlisted();
  assert.match(dom.elements.camUnlistedList.innerHTML, /<span class="cam-model">&lt;b onclick=&quot;x&quot;&gt;Z&lt;\/b&gt;<\/span>/);
});

test("stripVendorPrefix 는 마지막 매치 벤더를 반환하고 종료한다", () => {
  assert.deepEqual(site.stripVendorPrefix("Nikon Corporation Z 8"), { vendor: "nikon", model: "Z 8" });
  assert.deepEqual(site.stripVendorPrefix("Panasonic Lumix DMC-GH5"), { vendor: "panasonic", model: "DMC-GH5" });
  assert.deepEqual(site.stripVendorPrefix("Canon Canon"), { vendor: "canon", model: "" });
  assert.deepEqual(site.stripVendorPrefix(""), { vendor: "", model: "" });
});

test("헤드라인 카운트는 VERIFIED_MIN 미만이면 숨긴다", () => {
  site.setVerified(report([["Canon EOS R100", { usb: true }]]));
  site.renderVerifiedCount();
  assert.equal(dom.elements.camVerified.hidden, true);
  const many = {};
  catalog.cameras.slice(0, site.VERIFIED_MIN).forEach((c) => { many[site.camKey(c)] = { usb: true, wifi: false, model: c.vendor + " " + c.model }; });
  site.setVerified(many);
  site.renderVerifiedCount();
  assert.equal(dom.elements.camVerified.hidden, false);
  assert.match(dom.elements.camVerified.textContent, /실사용 확인 \d+종/);
});
