"use strict";
/* 색감 전송 섹션 — 렌더러(render_color_transfer.py)·main.js·index.html 세 곳이 같은 파일명을
   조립한다. 어느 하나만 고치면 이미지가 조용히 404 나고 결과 칸이 빈 채로 배포된다.
   그래서 "세 소스에서 뽑은 경로 집합"과 "디스크에 있는 파일"이 정확히 일치하는지 본다.
   실행: node --test website/tests/color-transfer.test.js */
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const WEB = path.join(__dirname, "..");
const COLOR_DIR = path.join(WEB, "assets", "color");
const html = fs.readFileSync(path.join(WEB, "index.html"), "utf8");
const js = fs.readFileSync(path.join(WEB, "assets", "js", "main.js"), "utf8");
const py = fs.readFileSync(path.join(WEB, "tools", "render_color_transfer.py"), "utf8");

/** main.js 의 STEPS 배열 — 0(원본)은 렌더 대상이 아니다. */
function jsSteps() {
  const m = js.match(/var STEPS = \[([^\]]+)\]/);
  assert.ok(m, "main.js 에서 STEPS 를 찾지 못했다 — initColorTransfer 가 바뀌었는지 확인");
  return m[1].split(",").map((s) => Number(s.trim()));
}

/** index.html 의 레퍼런스 스와치 slug */
function htmlRefs() {
  return [...html.matchAll(/class="ct-swatch[^"]*" data-ref="([a-z]+)"/g)].map((m) => m[1]);
}

/** 렌더러가 실제로 만들어 내는 강도 단계 */
function pySteps() {
  const m = py.match(/^INTENSITIES = \[([^\]]+)\]/m);
  assert.ok(m, "render_color_transfer.py 에서 INTENSITIES 를 찾지 못했다");
  return m[1].split(",").map((s) => Number(s.trim()));
}

/** 렌더러가 실제로 만들어 내는 레퍼런스 slug */
function pyRefs() {
  const block = py.match(/^REFERENCES = \[([\s\S]*?)^\]/m);
  assert.ok(block, "render_color_transfer.py 에서 REFERENCES 를 찾지 못했다");
  return [...block[1].matchAll(/\("([a-z]+)",\s*"[a-z]+"\)/g)].map((m) => m[1]);
}

test("main.js 의 강도 단계가 렌더러의 INTENSITIES 와 일치한다(0=원본이라 렌더 안 함)", () => {
  assert.deepEqual(jsSteps(), [0, ...pySteps()]);
});

test("index.html 의 레퍼런스 스와치가 렌더러의 REFERENCES 와 일치한다", () => {
  assert.deepEqual(htmlRefs(), pyRefs());
});

test("세 소스가 조립하는 모든 이미지 경로가 실제로 존재한다", () => {
  const wanted = new Set(["ct-target.webp"]);
  for (const ref of pyRefs()) {
    wanted.add(`ct-ref-${ref}.webp`);
    for (const step of pySteps()) wanted.add(`ct-after-${ref}-${step}.webp`);
  }
  for (const src of html.matchAll(/src="assets\/color\/([^"]+)"/g)) wanted.add(src[1]);

  const missing = [...wanted].filter((f) => !fs.existsSync(path.join(COLOR_DIR, f)));
  assert.deepEqual(missing, [], "렌더러를 다시 돌려야 한다: python3 website/tools/render_color_transfer.py");
});

test("assets/color 에 아무도 참조하지 않는 파일이 남아 있지 않다", () => {
  const wanted = new Set(["ct-target.webp", "palette.json"]);
  for (const ref of pyRefs()) {
    wanted.add(`ct-ref-${ref}.webp`);
    for (const step of pySteps()) wanted.add(`ct-after-${ref}-${step}.webp`);
  }
  const orphans = fs.readdirSync(COLOR_DIR).filter((f) => !wanted.has(f));
  assert.deepEqual(orphans, [], "레퍼런스·강도 구성을 줄였다면 옛 렌더 산출물을 지워야 한다");
});

test("palette.json 이 모든 레퍼런스를 hex 로 덮는다", () => {
  const pal = JSON.parse(fs.readFileSync(path.join(COLOR_DIR, "palette.json"), "utf8"));
  assert.deepEqual(Object.keys(pal).sort(), pyRefs().sort());
  for (const [slug, hexes] of Object.entries(pal)) {
    assert.ok(hexes.length >= 3, `${slug}: 대표색이 너무 적다`);
    for (const h of hexes) assert.match(h, /^#[0-9a-f]{6}$/, `${slug}: hex 형식 아님 ${h}`);
  }
});

test("사용자 노출 문구에 em-dash 가 없다 (9.G)", () => {
  const dir = path.join(WEB, "assets", "i18n");
  const hits = [];
  for (const f of fs.readdirSync(dir).filter((f) => f.endsWith(".json"))) {
    const d = JSON.parse(fs.readFileSync(path.join(dir, f), "utf8"));
    for (const [k, v] of Object.entries(d)) {
      if (typeof v === "string" && /[—–]/.test(v)) hits.push(`${f}:${k}`);
    }
  }
  assert.deepEqual(hits, [], "em-dash 는 하이픈이나 문장 분리로 바꿔야 한다");
});

test("기본 표시 이미지가 렌더된 조합 안에 있다(첫 화면이 404 나지 않는다)", () => {
  const result = html.match(/id="ctResult" src="assets\/color\/([^"]+)"/);
  const ref = html.match(/id="ctRef" src="assets\/color\/([^"]+)"/);
  assert.ok(result && ref, "ctResult·ctRef 의 기본 src 를 찾지 못했다");
  for (const f of [result[1], ref[1]]) {
    assert.ok(fs.existsSync(path.join(COLOR_DIR, f)), `기본 이미지 없음: ${f}`);
  }
});
