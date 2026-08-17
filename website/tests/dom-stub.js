"use strict";
/* main.js 를 Node 에서 require 하기 위한 최소 DOM 스텁.
   main.js 는 로드 시 window.matchMedia 와 document.addEventListener 만 부르고, 나머지 DOM 접근은
   DOMContentLoaded 이후이므로 실행되지 않는다. renderUnlisted/renderVerifiedCount 가 만지는 요소만 흉내낸다. */
function el(id) { return { id: id, hidden: true, innerHTML: "", textContent: "" }; }
const elements = {};
function reset() {
  for (const k of Object.keys(elements)) delete elements[k];
  ["camUnlisted", "camUnlistedList", "camVerified"].forEach((id) => { elements[id] = el(id); });
}
reset();
global.window = { matchMedia: () => ({ matches: false, addEventListener() {} }), location: { pathname: "/" } };
global.document = {
  addEventListener() {},
  getElementById: (id) => elements[id] || null,
  documentElement: { getAttribute: () => "ko" },
};
global.localStorage = { getItem: () => null, setItem() {}, removeItem() {} };
module.exports = { elements, reset };
