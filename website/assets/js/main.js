/* CamCon website — no dependencies */
(function () {
  "use strict";

  var SUPPORTED = ["ko", "en", "ja", "zh", "de", "es", "fr", "it"];
  var OTHER_VENDOR = "기타"; // supported-cameras.json 안의 벤더 없는 모델 버킷
  var I18N = {};
  var camData = null; // {total, cameras:[{vendor,model,connection}]}

  // 정적 자산 캐시 버스터. index.html 의 css/js ?v= 와 같은 값을 유지한다
  // (main.js 자체가 ?v= 로 받아지므로 stale main.js 가 stale ASSET_V 를 들고 있을 수 없다).
  var ASSET_V = "20260817a";

  var VERIFIED_URL = "https://asia-northeast3-camcon-67ad7.cloudfunctions.net/getVerifiedCameras";
  var verifiedByKey = {};
  var verifiedCount = 0;

  // CSS 의 scroll-behavior:auto 는 앵커 이동만 커버한다. JS 가 명시한 behavior:"smooth" 는 그 위를 덮으므로
  // 모듈 스코프에 한 번만 두고 스크롤·리빌 양쪽에서 공유한다.
  var REDUCE_MOTION = window.matchMedia("(prefers-reduced-motion: reduce)");

/* KEEP IN SYNC — 이 블록은 functions/index.js 와 website/assets/js/main.js 에 바이트 동일하게 존재해야 배지 매칭이 성립한다. 한쪽 수정 시 반드시 다른 쪽도 동일 반영. */
var VENDOR_SYNONYMS = [
  ["nikon",     ["nikon corporation", "nikon"]],
  ["canon",     ["canon inc.", "canon inc", "canon"]],
  ["sony",      ["sony corporation", "sony"]],
  ["fujifilm",  ["fujifilm corporation", "fuji photo film", "fujifilm", "fuji"]],
  ["panasonic", ["panasonic corporation", "panasonic", "lumix"]],
  ["olympus",   ["om digital solutions", "om system", "olympus imaging corp.",
                 "olympus imaging corp", "olympus corporation", "olympus"]],
  ["leica",     ["leica camera ag", "leica"]],
  ["pentax",    ["ricoh imaging company, ltd.", "ricoh imaging", "pentax"]],
  ["ricoh",     ["ricoh"]],
  ["sigma",     ["sigma"]],
  ["hasselblad",["hasselblad"]],
  ["casio",     ["casio computer co.,ltd.", "casio"]]
];
var KEY_ALIASES = {
  "sony:ilce7":    "sony:alphaa7",     "sony:ilce7r":   "sony:alphaa7r",
  "sony:ilce7m3":  "sony:alphaa7iii",  "sony:ilce7m4":  "sony:alphaa7iv",
  "sony:ilce7rm2": "sony:alphaa7rii",  "sony:ilce7rm3": "sony:alphaa7riii",
  "sony:ilce7rm4": "sony:dsca7riv",    "sony:ilce7s":   "sony:alphaa7s",
  "sony:ilce7sm2": "sony:alphaa7sii",  "sony:ilce7sm3": "sony:dsca7siii",
  "sony:ilce3000": "sony:alphaa3000",  "sony:ilce5000": "sony:alphaa5000",
  "sony:ilce5100": "sony:alphaa5100",  "sony:ilce6000": "sony:alphaa6000",
  "sony:ilce6100": "sony:alphaa6100",  "sony:ilce6700": "sony:a6700"
};
function normalizeCameraKey(input) {
  if (!input) return "";
  var s = String(input).toLowerCase();
  s = s.replace(/\([^)]*\)/g, " ");
  s = s.replace(/[_/]/g, " ").replace(/\s+/g, " ").trim();
  var vendor = "", changed = true;
  while (changed) {
    changed = false;
    for (var i = 0; i < VENDOR_SYNONYMS.length; i++) {
      var canon = VENDOR_SYNONYMS[i][0], syns = VENDOR_SYNONYMS[i][1];
      for (var j = 0; j < syns.length; j++) {
        var syn = syns[j];
        if (s === syn || s.indexOf(syn + " ") === 0) {
          vendor = canon;
          s = s.slice(syn.length).trim(); changed = true; break;
        }
      }
      if (changed) break;
    }
  }
  if (s.indexOf("dsc ") === 0) s = s.slice(4);
  var model = s.replace(/[^a-z0-9]/g, "");
  var key = vendor + ":" + model;
  return KEY_ALIASES[key] || key;
}

  document.addEventListener("DOMContentLoaded", function () {
    initI18n();
    initMobileNav();
    initTabs();
    initAccordion();
    initCamExplorer();
    initReveal();
    initScrollSpy();
    initHeader();
    initBeforeAfter();
    initFilmPicker();
  });

  /* ══════════════ before/after slider ══════════════ */
  function initBeforeAfter() {
    document.querySelectorAll(".ba").forEach(function (ba) {
      var range = ba.querySelector(".ba-range");
      var before = ba.querySelector(".ba-before-wrap");
      var divider = ba.querySelector(".ba-divider");
      if (!range || !before || !divider) return;
      function set(v) {
        var clip = "inset(0 " + (100 - v) + "% 0 0)";
        before.style.clipPath = clip;
        before.style.webkitClipPath = clip;
        divider.style.left = v + "%";
      }
      range.addEventListener("input", function () { set(+range.value); });
      set(+range.value);
    });
  }

  /* ══════════════ film picker → drives before/after ══════════════ */
  function initFilmPicker() {
    var after = document.getElementById("baAfter");
    var nameTag = document.getElementById("baName");
    if (!after || !nameTag) return;
    var before = document.querySelector(".ba .ba-before");
    var swatches = document.querySelectorAll(".swatch[data-after]");
    if (!swatches.length) return;
    var ba = document.querySelector(".ba");
    var preloaded = {};

    /* 스와치 14개를 커서로 훑기만 해도 원본 3.7MB 가 무조건 전송되던 구간.
       데이터세이버·저속 회선을 존중하고 총량에 상한을 둔다. */
    var MAX_PRELOAD = 4;
    var preloadCount = 0;

    function canPreload() {
      var c = navigator.connection;
      if (c && (c.saveData || /(^|-)2g$/.test(c.effectiveType || ""))) return false;
      return preloadCount < MAX_PRELOAD;
    }
    function preload(src) {
      if (!src || preloaded[src] || !canPreload()) return;
      preloaded[src] = true;
      preloadCount++;
      var img = new Image();
      img.fetchPriority = "low";
      img.decoding = "async";
      img.src = src;
    }

    function select(sw) {
      var src = sw.getAttribute("data-after");
      if (!src) return;
      for (var i = 0; i < swatches.length; i++) {
        var on = swatches[i] === sw;
        swatches[i].classList.toggle("is-active", on);
        swatches[i].setAttribute("aria-pressed", on ? "true" : "false");
      }
      after.src = src;
      // 원본도 스와치의 장면과 짝으로 교체 (data-before, 장면 매핑은 render_film_examples.py가 보장)
      var bsrc = sw.getAttribute("data-before");
      if (before && bsrc) before.src = bsrc;
      nameTag.textContent = sw.getAttribute("data-name") || "";
      // 스와치를 눌러도 슬라이더가 화면 밖이면 아무 피드백이 없다. 좁은 화면 전용이던 보정을
      // 실제 가시성 기준으로 넓힌다 — 이미 충분히 보이면 스크롤하지 않는다.
      if (ba) {
        var r = ba.getBoundingClientRect();
        var vh = window.innerHeight || document.documentElement.clientHeight;
        var visible = Math.min(r.bottom, vh) - Math.max(r.top, 0);
        if (visible < Math.min(r.height, vh) * 0.5) {
          ba.scrollIntoView({ behavior: REDUCE_MOTION.matches ? "auto" : "smooth", block: "center" });
        }
      }
    }

    for (var i = 0; i < swatches.length; i++) {
      (function (sw) {
        sw.setAttribute("aria-pressed", sw.classList.contains("is-active") ? "true" : "false");
        // 스와치는 <button> 이므로 Enter·Space 는 브라우저가 click 으로 넘긴다(수동 keydown 불요).
        sw.addEventListener("click", function () { select(sw); });
        // 스쳐 지나가는 커서는 프리로드하지 않는다 — 220ms 이상 머문 경우에만.
        var hoverTimer = null;
        sw.addEventListener("pointerenter", function () {
          clearTimeout(hoverTimer);
          hoverTimer = setTimeout(function () {
            preload(sw.getAttribute("data-after"));
            preload(sw.getAttribute("data-before"));
          }, 220);
        });
        sw.addEventListener("pointerleave", function () { clearTimeout(hoverTimer); });
      })(swatches[i]);
    }
  }

  /* ══════════════ header scrolled state ══════════════ */
  /* window scroll 리스너는 매 스크롤 프레임마다 JS 콜백을 태운다 → 이 파일이 이미 두 번 쓰는
     IntersectionObserver 로 합류시킨다. 센티널 높이 9px 이 기존 scrollY > 8 임계값을 그대로 재현한다. */
  function initHeader() {
    var header = document.querySelector(".site-header");
    if (!header) return;
    if (!("IntersectionObserver" in window)) { header.classList.add("scrolled"); return; }

    var sentinel = document.createElement("div");
    sentinel.setAttribute("aria-hidden", "true");
    sentinel.style.cssText = "position:absolute;top:0;left:0;width:1px;height:9px;pointer-events:none";
    document.body.prepend(sentinel);

    new IntersectionObserver(function (entries) {
      header.classList.toggle("scrolled", !entries[0].isIntersecting);
    }, { threshold: 0 }).observe(sentinel);
  }

  /* ══════════════ i18n ══════════════ */
  /* 언어는 URL 이 정본이다 — `/` 는 한국어, `/en/`·`/ja/`… 는 tools/build_i18n_pages.py 가 만든 정적 페이지다.
     따라서 이 파일은 페이지 언어를 바꾸지 않고, 그 언어의 사전만 확보해 동적 문자열(카메라 탐색기 등)에 쓴다.
     언어 전환은 in-place 교체가 아니라 해당 URL 로의 이동이다. */
  function t(key) {
    return I18N && Object.prototype.hasOwnProperty.call(I18N, key) ? I18N[key] : null;
  }

  function pageLang() {
    var l = (document.documentElement.getAttribute("lang") || "").toLowerCase();
    return SUPPORTED.indexOf(l) >= 0 ? l : "ko";
  }

  function langHref(lang) {
    return lang === "ko" ? "/" : "/" + lang + "/";
  }

  // zh 는 사전이 간체 1종뿐이라 번체 태그도 zh 로 보낸다. 번체 사전이 생기면 이 표만 고치면 된다.
  var SCRIPT_MAP = {
    "zh-hant": "zh", "zh-tw": "zh", "zh-hk": "zh", "zh-mo": "zh",
    "zh-hans": "zh", "zh-cn": "zh", "zh-sg": "zh"
  };

  // 브라우저가 원하는 언어. 자동 이동에는 쓰지 않고 제안 배너 판정에만 쓴다.
  function suggestLang() {
    var navs = navigator.languages || [navigator.language || ""];
    for (var i = 0; i < navs.length; i++) {
      var tag = (navs[i] || "").toLowerCase();
      if (SCRIPT_MAP[tag]) return SCRIPT_MAP[tag]; // slice(0,2) 로 subtag 를 버리기 전에 먼저 본다
      var code = tag.slice(0, 2);
      if (SUPPORTED.indexOf(code) >= 0) return code;
    }
    return "en"; // 8개 밖 언어권(pt/ru/id/vi/th/nl/tr…)은 한국어가 아니라 영어 — hreflang x-default 와 동일
  }

  function applyI18n() {
    document.querySelectorAll("[data-i18n]").forEach(function (el) {
      var val = t(el.getAttribute("data-i18n"));
      if (val == null) return; // 번역 없으면 인라인(한국어) 유지
      var attr = el.getAttribute("data-i18n-attr");
      if (attr) el.setAttribute(attr, val);
      else el.innerHTML = val;
    });
    var title = t("meta.title");
    if (title) document.title = title;
    refreshCamText(); // 언어 변경 시 동적 텍스트도 갱신
  }

  function loadLang(lang, done) {
    // 정적 생성 페이지는 사전이 </head> 직전에 인라인돼 있다 → fetch 도 FOUC 도 없다.
    if (window.__I18N__) {
      I18N = window.__I18N__;
      applyI18n();
      if (done) done();
      return;
    }
    // 루트(/)만 여기로 온다. 본문 인라인이 이미 한국어라 교체돼도 보이는 변화는 없다.
    fetch("/assets/i18n/" + lang + ".json?v=" + ASSET_V)
      .then(function (r) { if (!r.ok) throw new Error("http " + r.status); return r.json(); })
      .then(function (dict) {
        I18N = dict;
        applyI18n();
        if (done) done();
      })
      .catch(function () {
        // 로딩 실패(예: file:// 로 열람) → 인라인 한국어 폴백 유지
        if (done) done();
      });
  }

  function initI18n() {
    var sel = document.getElementById("lang");
    var lang = pageLang();
    if (sel) {
      sel.value = lang;
      sel.addEventListener("change", function () {
        var next = sel.value;
        if (SUPPORTED.indexOf(next) < 0 || next === lang) return;
        remember("local", CHOSEN_KEY, next); // 명시 선택 — 이후 제안 배너의 기본 후보가 된다
        location.href = langHref(next) + location.hash;
      });
    }
    loadLang(lang);
    initLangSuggest(lang);
  }

  /* ══════════════ 언어 제안 배너 ══════════════ */
  /* 자동 리다이렉트는 하지 않는다(Google 명시 비권고). 이동은 사용자가 링크를 누를 때만 일어난다.
     문구는 "제안 대상 언어"로 보여야 뜻이 통하는데 인라인 사전은 현재 페이지 언어 1종뿐이므로,
     이 8줄만 예외적으로 JS 에 둔다(사전 조회 대상이 아니다). */
  var LANG_CTA = {
    ko: "한국어로 보기",
    en: "View in English",
    ja: "日本語で見る",
    zh: "查看中文版",
    de: "Auf Deutsch ansehen",
    es: "Ver en español",
    fr: "Voir en français",
    it: "Guarda in italiano"
  };

  /* 저장 키 3종은 역할이 다르다 — 섞으면 "예전에 영어를 골랐던 사람이 한국어 루트에서 안내를 못 받는" 회귀가 난다.
     camcon-lang           마지막으로 직접 고른 언어. "묻지 마라"가 아니라 "이 사람은 X 를 원한다"는 제안 소스다.
     camcon-lang-suggested 배너를 명시적으로 닫음. 노출을 막는 유일한 영구 키.
     camcon-lang-seen      이번 세션에 이미 1회 노출(sessionStorage). */
  var CHOSEN_KEY = "camcon-lang";
  var DISMISS_KEY = "camcon-lang-suggested";
  var SEEN_KEY = "camcon-lang-seen";

  // 쿠키 차단·사생활 모드에서는 storage 접근 자체가 던진다 → 배너만 조용히 포기하고 나머지 init 은 말려들지 않게 한다.
  function suggestState() {
    try {
      return {
        chosen: localStorage.getItem(CHOSEN_KEY),
        dismissed: localStorage.getItem(DISMISS_KEY),
        seen: sessionStorage.getItem(SEEN_KEY)
      };
    } catch (e) {
      return null;
    }
  }

  function remember(area, key, val) {
    try { (area === "session" ? sessionStorage : localStorage).setItem(key, val); } catch (e) { /* 저장 실패는 무시 */ }
  }

  function initLangSuggest(current) {
    var box = document.getElementById("langSuggest");
    if (!box) return;
    var state = suggestState();
    if (!state || state.dismissed || state.seen) return; // 닫았거나 이번 세션에 이미 봤으면 끝

    // 직접 고른 언어가 있으면 그게 제안 대상이다(브라우저 언어보다 우선). 없으면 브라우저 언어.
    var want = state.chosen && SUPPORTED.indexOf(state.chosen) >= 0 ? state.chosen : suggestLang();
    if (want === current) return;

    var link = document.getElementById("langSuggestLink");
    var close = document.getElementById("langSuggestClose");
    if (!link || !close) return;

    link.href = langHref(want) + location.hash;
    link.setAttribute("lang", want);
    link.setAttribute("hreflang", want);
    link.textContent = LANG_CTA[want] || want;
    link.addEventListener("click", function () {
      remember("local", CHOSEN_KEY, want);
    });
    close.addEventListener("click", function () {
      remember("local", DISMISS_KEY, "1");
      box.hidden = true; // display 는 style.css 의 #langSuggest:not([hidden]) 가 잡는다
    });
    remember("session", SEEN_KEY, "1");
    box.hidden = false;
  }

  /* ══════════════ mobile nav ══════════════ */
  function initMobileNav() {
    var toggle = document.querySelector(".nav-toggle");
    var menu = document.getElementById("mobile-nav");
    if (!toggle || !menu) return;

    function close() {
      toggle.setAttribute("aria-expanded", "false");
      toggle.setAttribute("aria-label", t("nav.menuOpen") || "메뉴 열기");
      menu.hidden = true;
    }
    toggle.addEventListener("click", function () {
      if (toggle.getAttribute("aria-expanded") === "true") {
        close();
      } else {
        toggle.setAttribute("aria-expanded", "true");
        toggle.setAttribute("aria-label", t("nav.menuClose") || "메뉴 닫기");
        menu.hidden = false;
      }
    });
    menu.querySelectorAll("a").forEach(function (link) {
      link.addEventListener("click", close);
    });
    document.addEventListener("keydown", function (e) {
      if (e.key === "Escape") close();
    });
  }

  /* ══════════════ connection tabs ══════════════ */
  function initTabs() {
    var tabs = Array.prototype.slice.call(document.querySelectorAll(".tab"));
    if (!tabs.length) return;

    function activate(tab) {
      tabs.forEach(function (other) {
        var on = other === tab;
        other.classList.toggle("is-active", on);
        other.setAttribute("aria-selected", on ? "true" : "false");
        other.tabIndex = on ? 0 : -1;
        var panel = document.getElementById(other.getAttribute("aria-controls"));
        if (panel) { panel.classList.toggle("is-active", on); panel.hidden = !on; }
      });
    }
    tabs.forEach(function (tab, i) {
      tab.addEventListener("click", function () { activate(tab); });
      tab.addEventListener("keydown", function (e) {
        var next = null;
        if (e.key === "ArrowRight") next = tabs[(i + 1) % tabs.length];
        else if (e.key === "ArrowLeft") next = tabs[(i - 1 + tabs.length) % tabs.length];
        else if (e.key === "Home") next = tabs[0];
        else if (e.key === "End") next = tabs[tabs.length - 1];
        if (next) { e.preventDefault(); activate(next); next.focus(); }
      });
    });
  }

  /* ══════════════ FAQ accordion ══════════════ */
  function initAccordion() {
    document.querySelectorAll(".acc-trigger").forEach(function (trigger) {
      trigger.addEventListener("click", function () {
        var open = trigger.getAttribute("aria-expanded") === "true";
        trigger.setAttribute("aria-expanded", open ? "false" : "true");
      });
    });
  }

  /* ══════════════ camera explorer ══════════════ */
  var CAP = 250; // 렌더 상한 (검색으로 좁히도록 유도)
  var activeVendor = "*";
  var searchTerm = "";

  function initCamExplorer() {
    var toggle = document.getElementById("camToggle");
    var body = document.getElementById("camExplorerBody");
    var search = document.getElementById("camSearch");
    if (toggle && body) {
      toggle.addEventListener("click", function () {
        var open = toggle.getAttribute("aria-expanded") === "true";
        toggle.setAttribute("aria-expanded", open ? "false" : "true");
        body.hidden = open;
      });
    }
    if (search) {
      var deb;
      search.addEventListener("input", function () {
        clearTimeout(deb);
        deb = setTimeout(function () {
          searchTerm = search.value.trim().toLowerCase();
          renderCameras();
        }, 110);
      });
    }

    // 로딩 구간에 검색창만 있고 결과가 없는 공백을 남기지 않는다.
    var listEl = document.getElementById("camList");
    if (listEl) {
      var skel = "";
      for (var s = 0; s < 8; s++) skel += '<li class="cam-skel" aria-hidden="true"></li>';
      listEl.innerHTML = skel;
    }

    fetch("/assets/data/supported-cameras.json?v=" + ASSET_V)
      .then(function (r) { if (!r.ok) throw new Error("http " + r.status); return r.json(); })
      .then(function (data) {
        camData = data;
        var totalEl = document.getElementById("camTotal");
        if (totalEl) totalEl.textContent = (data.total || data.cameras.length).toLocaleString();
        buildVendorChips();
        renderCameras();
        // 목록 렌더 뒤 "사용 확인됨" 배지 데이터 로드 (실패해도 목록은 정상)
        loadVerified(function (v) {
          verifiedByKey = {};
          (v.cameras || []).forEach(function (c) {
            if (!c || typeof c.key !== "string") return; // 손상 캐시 한 줄이 목록 전체를 지우지 않게
            verifiedByKey[c.key] = { usb: c.usb, wifi: c.wifi, model: c.model || "" };
          });
          verifiedCount = countVerifiedMatches();
          renderVerifiedCount();
          renderUnlisted();
          renderCameras();
        });
      })
      .catch(function () {
        // 실패를 침묵하면 검색창만 있고 결과가 영원히 없는 패널이 남는다 → 스켈레톤을 걷고 사유를 노출한다.
        var totalEl = document.getElementById("camTotal");
        if (totalEl) totalEl.textContent = "-";
        if (listEl) listEl.innerHTML = "";
        var emptyEl = document.getElementById("camEmpty");
        if (emptyEl) {
          emptyEl.hidden = false;
          emptyEl.textContent = t("cameras.loadError") || "목록을 불러오지 못했습니다. 잠시 후 새로고침해 주세요.";
        }
      });
  }

  /* 이 응답은 nginx 캐시 밖(Cloud Functions)이라 ?v= 로 제어되지 않고, 호출 1회가 Firestore read 과금이다.
     배지는 마케팅 표시용이라 몇 시간 지연이 무해하므로 클라이언트에서 TTL 캐시한다. */
  var VERIFIED_TTL = 6 * 60 * 60 * 1000; // 6h
  // v2: 응답에 표시명(model)이 추가됐다 — 구 캐시는 그 필드가 없어 목록 외 블록이 빈 이름으로 뜨므로 키를 갈아 강제 재요청한다.
  var VERIFIED_CACHE_KEY = "camcon-verified-v2";

  function loadVerified(done) {
    var raw = null;
    try { localStorage.removeItem("camcon-verified"); } catch (e) { /* v1 키 정리 — 실패 무시 */ }
    try { raw = localStorage.getItem(VERIFIED_CACHE_KEY); } catch (e) { raw = null; }
    if (raw) {
      try {
        var c = JSON.parse(raw);
        if (c && c.data && (Date.now() - c.at) < VERIFIED_TTL) { done(c.data); return; }
      } catch (e) { /* 손상 캐시는 무시하고 재요청 */ }
    }
    fetch(VERIFIED_URL)
      .then(function (r) { return r.ok ? r.json() : { cameras: [] }; })
      .then(function (v) {
        try { localStorage.setItem(VERIFIED_CACHE_KEY, JSON.stringify({ at: Date.now(), data: v })); } catch (e) { /* 저장 실패는 무시 */ }
        done(v);
      })
      .catch(function () { /* 배지 없이 목록만 */ });
  }

  /* 실사용 확인 수의 최소 표시 임계값. 집계가 한 자릿수인 동안 "드라이버 등재 945 · 실사용 확인 1종"으로
     나란히 놓이면 대비가 오히려 신뢰를 깎는다 → 두 자릿수부터 '사례 모음'으로 읽힌다고 보고 10 으로 둔다.
     임계 미만이면 뒷단(실사용 확인)만 감추고 앞단(드라이버 등재 945)은 그대로 노출한다.
     집계가 쌓이면 이 상수만 낮추거나 지우면 된다. */
  var VERIFIED_MIN = 10;

  // 배지 매칭 키. 카운트와 목록 렌더가 이 함수 하나만 보게 해서 둘이 갈라지지 않도록 한다.
  function camKey(c) {
    return normalizeCameraKey(c.vendor + " " + c.model);
  }

  /* 헤더 숫자는 CF 응답 원본 개수가 아니라 카탈로그에 "실제로 매칭되는" 기종 수여야 한다.
     normalizeCameraKey 가 CF 쪽과 어긋나거나 드라이버 목록에 없는 기종이 제보되면 매칭 실패분이
     생기는데, 원본 개수를 그대로 쓰면 존재하지 않는 기종까지 세어 과장된다.
     주의: 이 숫자는 화면의 배지 개수와 반드시 같지 않다 — 여기서는 키 기준으로 중복을 제거하고
     ("N종") 배지는 카탈로그 행마다 붙는데, 표기가 다른 같은 기종이 서로 다른 행으로 존재해
     같은 키로 접히는 그룹이 있다(예: "Canon MVX 3i" / "Canon MVX3i"). "종" 표기에는 중복 제거가
     맞으므로 의도된 차이다. */
  function countVerifiedMatches() {
    if (!camData) return 0;
    var seen = {}, n = 0;
    camData.cameras.forEach(function (c) {
      var k = camKey(c);
      if (verifiedByKey[k] && !seen[k]) { seen[k] = 1; n++; }
    });
    // 목록 외(카탈로그 미매칭) 기종은 이 숫자에 넣지 않는다 — 클라이언트가 보낸 문자열로 만들어지는 항목이라
    // 헤드라인 신뢰 지표까지 부풀릴 수 있다. 목록 외 블록 자체에서만 보여 준다.
    return n;
  }

  // 카탈로그 키 집합 — camData 가 바뀔 때만 다시 계산(945행 × 정규화 4패스를 렌더마다 반복하지 않게).
  var catalogKeyCache = { src: null, keys: null };
  function catalogKeys() {
    if (!camData) return {};
    if (catalogKeyCache.src !== camData) {
      var keys = {};
      camData.cameras.forEach(function (c) { keys[camKey(c)] = 1; });
      catalogKeyCache = { src: camData, keys: keys };
    }
    return catalogKeyCache.keys;
  }

  /* 카탈로그(드라이버 등재 945종)에 매칭되지 않는 실사용 확인 기종.
     원래는 CF 응답이 key 만 줘서 이름을 붙일 수 없어 홈페이지 어디에도 안 보였다 — 이제 CF 가 정제된
     표시명(model)을 함께 주므로 별도 블록으로 노출한다. 카탈로그 매칭분은 카탈로그 표기를 쓰므로 여기서 제외.
     표시명이 비어 있으면(구 CF 응답·이상 문서) 정규화 키를 이름 대신 내보내지 않고 행을 건너뛴다. */
  function unlistedVerified() {
    if (!camData) return [];
    var inCatalog = catalogKeys();
    return Object.keys(verifiedByKey)
      .filter(function (k) { return !inCatalog[k] && k.indexOf(":") > 0 && verifiedByKey[k].model; })
      .sort()
      .map(function (k) { return { key: k, v: verifiedByKey[k] }; });
  }

  // 키 앞부분(정규화 벤더) → 표시용 벤더명. VENDOR_SYNONYMS 의 canon 값과 1:1.
  var VENDOR_LABELS = {
    nikon: "Nikon", canon: "Canon", sony: "Sony", fujifilm: "Fujifilm", panasonic: "Panasonic",
    olympus: "OM System / Olympus", leica: "Leica", pentax: "Pentax", ricoh: "Ricoh",
    sigma: "Sigma", hasselblad: "Hasselblad", casio: "Casio"
  };

  /* 표시명에서 선행 벤더 동의어("Nikon Corporation Z 8" → "Z 8")를 떼어 카탈로그 행과 같은 모양으로 맞춘다.
     벤더는 마지막으로 떼어낸 동의어 기준(normalizeCameraKey 와 같은 규칙) — "RICOH IMAGING COMPANY, LTD. RICOH GR III" 는
     제조사(Ricoh Imaging=Pentax 버킷)가 아니라 브랜드(Ricoh)로 라벨링돼야 한다. */
  function stripVendorPrefix(model) {
    var s = String(model || "").trim(), low = s.toLowerCase(), vendor = "", changed = true;
    while (changed) {
      changed = false;
      for (var i = 0; i < VENDOR_SYNONYMS.length && !changed; i++) {
        var canon = VENDOR_SYNONYMS[i][0], syns = VENDOR_SYNONYMS[i][1];
        for (var j = 0; j < syns.length; j++) {
          var syn = syns[j];
          if (low === syn || low.indexOf(syn + " ") === 0) {
            vendor = canon; s = s.slice(syn.length).trim(); low = s.toLowerCase(); changed = true; break;
          }
        }
      }
    }
    return { vendor: vendor, model: s };
  }

  function renderUnlisted() {
    var wrap = document.getElementById("camUnlisted");
    var list = document.getElementById("camUnlistedList");
    if (!wrap || !list) return;
    var items = unlistedVerified().slice(0, CAP); // 카탈로그 목록과 같은 렌더 상한
    if (!items.length) { wrap.hidden = true; list.innerHTML = ""; return; }
    list.innerHTML = items.map(function (it) {
      var parts = stripVendorPrefix(it.v.model);
      var vendorKey = parts.vendor || it.key.split(":")[0];
      var vendor = VENDOR_LABELS[vendorKey] || (vendorKey.charAt(0).toUpperCase() + vendorKey.slice(1));
      var model = parts.model || it.v.model;
      var methods = [];
      if (it.v.usb) methods.push("USB");
      if (it.v.wifi) methods.push("Wi-Fi");
      var label = (t("cameras.verified") || "사용 확인됨") + (methods.length ? " · " + methods.join(" · ") : "");
      return '<li><span class="cam-vendor">' + esc(vendor) + "</span>" +
        '<span class="cam-model">' + esc(model) + "</span>" +
        '<span class="cam-verify">' + esc(label) + "</span></li>";
    }).join("");
    wrap.hidden = false;
  }

  // 드라이버 등재 수(945)와 실사용 확인 수는 성격이 다른 값이라 한 줄로 합치지 않고 2단으로 표기한다.
  function renderVerifiedCount() {
    var el = document.getElementById("camVerified");
    if (!el) return;
    var tpl = t("cameras.verifiedTpl");
    if (!tpl || verifiedCount < VERIFIED_MIN) { el.hidden = true; return; }
    el.textContent = tpl.replace("{n}", verifiedCount.toLocaleString());
    el.hidden = false;
  }

  function vendorLabel(v) {
    if (v === OTHER_VENDOR) return t("cameras.otherVendor") || "기타";
    return v;
  }

  function buildVendorChips() {
    var wrap = document.getElementById("camFilters");
    if (!wrap || !camData) return;
    var counts = {};
    camData.cameras.forEach(function (c) { counts[c.vendor] = (counts[c.vendor] || 0) + 1; });
    // 8개 이상 보유 벤더만 칩으로, 개수 내림차순
    var vendors = Object.keys(counts)
      .filter(function (v) { return counts[v] >= 8; })
      .sort(function (a, b) { return counts[b] - counts[a]; });

    wrap.innerHTML = "";
    var all = document.createElement("button");
    all.className = "cam-chip";
    all.dataset.vendor = "*";
    all.innerHTML = (t("cameras.allVendors") || "전체") +
      '<span class="cam-chip-n">' + camData.cameras.length.toLocaleString() + "</span>";
    wrap.appendChild(all);

    vendors.forEach(function (v) {
      var b = document.createElement("button");
      b.className = "cam-chip";
      b.dataset.vendor = v;
      b.innerHTML = esc(vendorLabel(v)) + '<span class="cam-chip-n">' + counts[v] + "</span>";
      wrap.appendChild(b);
    });

    // 활성 상태 반영
    wrap.querySelectorAll(".cam-chip").forEach(function (c) {
      c.classList.toggle("is-active", c.dataset.vendor === activeVendor);
    });

    if (!wrap.dataset.bound) {
      wrap.dataset.bound = "1";
      wrap.addEventListener("click", function (e) {
        var chip = e.target.closest(".cam-chip");
        if (!chip) return;
        activeVendor = chip.dataset.vendor;
        wrap.querySelectorAll(".cam-chip").forEach(function (c) {
          c.classList.toggle("is-active", c === chip);
        });
        renderCameras();
      });
    }
  }

  function renderCameras() {
    var list = document.getElementById("camList");
    var empty = document.getElementById("camEmpty");
    var count = document.getElementById("camCount");
    if (!list || !camData) return;

    var filtered = camData.cameras.filter(function (c) {
      if (activeVendor !== "*" && c.vendor !== activeVendor) return false;
      if (searchTerm) {
        var hay = (c.vendor + " " + c.model).toLowerCase();
        if (hay.indexOf(searchTerm) === -1) return false;
      }
      return true;
    });

    var shown = filtered.slice(0, CAP);
    list.innerHTML = shown.map(function (c) {
      var vh = verifiedByKey[camKey(c)];
      var badge = "";
      if (vh) {
        var methods = [];
        if (vh.usb) methods.push("USB");
        if (vh.wifi) methods.push("Wi-Fi");
        var label = (t("cameras.verified") || "사용 확인됨") + (methods.length ? " · " + methods.join(" · ") : "");
        badge = '<span class="cam-verify">' + esc(label) + '</span>';
      }
      if (c.wifi) {
        badge = '<span class="cam-wifi">' + esc(t("cameras.wifiBadge") || "Wi-Fi") + "</span>" + badge;
      }
      return '<li><span class="cam-vendor">' + esc(vendorLabel(c.vendor)) + "</span>" +
        '<span class="cam-model">' + esc(c.model) + "</span>" + badge + "</li>";
    }).join("");

    if (empty) empty.hidden = filtered.length !== 0;
    if (count) {
      if (!filtered.length) {
        count.textContent = "";
      } else {
        var tpl = t("cameras.countTpl") || "{total}개 모델 중 {shown}개 표시";
        count.textContent = tpl
          .replace("{total}", filtered.length.toLocaleString())
          .replace("{shown}", shown.length.toLocaleString());
      }
    }
  }

  function refreshCamText() {
    renderVerifiedCount();
    if (!camData) return;
    buildVendorChips();
    renderUnlisted();
    renderCameras();
  }

  function esc(s) {
    return String(s).replace(/[&<>"]/g, function (ch) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[ch];
    });
  }

  /* ══════════════ scroll reveal ══════════════ */
  function initReveal() {
    var items = document.querySelectorAll(".reveal");
    if (!items.length) return;
    if (REDUCE_MOTION.matches || !("IntersectionObserver" in window)) {
      items.forEach(function (el) { el.classList.add("is-visible"); });
      return;
    }
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          entry.target.classList.add("is-visible");
          io.unobserve(entry.target);
        }
      });
    }, { threshold: 0.12, rootMargin: "0px 0px -8% 0px" });
    items.forEach(function (el) { io.observe(el); });
  }

  /* ══════════════ scrollspy ══════════════ */
  function initScrollSpy() {
    var links = Array.prototype.slice.call(document.querySelectorAll(".nav a"));
    if (!links.length || !("IntersectionObserver" in window)) return;
    var map = {}, sections = [];
    links.forEach(function (link) {
      var id = link.getAttribute("href").slice(1);
      var section = document.getElementById(id);
      if (section) { map[id] = link; sections.push(section); }
    });
    var spy = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          links.forEach(function (l) { l.classList.remove("is-current"); });
          if (map[entry.target.id]) map[entry.target.id].classList.add("is-current");
        }
      });
    }, { rootMargin: "-45% 0px -50% 0px" });
    sections.forEach(function (s) { spy.observe(s); });
  }

  /* 테스트 훅 — 브라우저에는 module 이 없어 통째로 건너뛴다. Node(website/tests/) 에서만 내부 함수를 꺼내
     카메라 탐색기 로직(키 정규화·목록 외 판정·표시명·카운트·이스케이프)을 실제 카탈로그로 검증한다.
     상태(camData·verifiedByKey·I18N)는 클로저 변수라 setter 로 주입한다. */
  if (typeof module === "object" && module && module.exports) {
    module.exports = {
      normalizeCameraKey: normalizeCameraKey,
      camKey: camKey,
      stripVendorPrefix: stripVendorPrefix,
      unlistedVerified: unlistedVerified,
      countVerifiedMatches: countVerifiedMatches,
      renderUnlisted: renderUnlisted,
      renderVerifiedCount: renderVerifiedCount,
      esc: esc,
      VERIFIED_MIN: VERIFIED_MIN,
      setCamData: function (d) { camData = d; },
      setVerified: function (m) { verifiedByKey = m; verifiedCount = countVerifiedMatches(); },
      setI18n: function (d) { I18N = d; }
    };
  }
})();
