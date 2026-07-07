/* CamCon website — no dependencies */
(function () {
  "use strict";

  var SUPPORTED = ["ko", "en", "ja", "zh", "de", "es", "fr", "it"];
  var OTHER_VENDOR = "기타"; // supported-cameras.json 안의 벤더 없는 모델 버킷
  var I18N = {};
  var camData = null; // {total, cameras:[{vendor,model,connection}]}

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

  /* ══════════════ header scrolled state ══════════════ */
  function initHeader() {
    var header = document.querySelector(".site-header");
    if (!header) return;
    var onScroll = function () {
      header.classList.toggle("scrolled", window.scrollY > 8);
    };
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
  }

  /* ══════════════ i18n ══════════════ */
  function t(key) {
    return I18N && Object.prototype.hasOwnProperty.call(I18N, key) ? I18N[key] : null;
  }

  function detectLang() {
    var saved = localStorage.getItem("camcon-lang");
    if (saved && SUPPORTED.indexOf(saved) >= 0) return saved;
    var navs = navigator.languages || [navigator.language || ""];
    for (var i = 0; i < navs.length; i++) {
      var code = (navs[i] || "").slice(0, 2).toLowerCase();
      if (SUPPORTED.indexOf(code) >= 0) return code;
    }
    return "ko"; // 브랜드 기본 + 인라인 폴백과 일치
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
    fetch("assets/i18n/" + lang + ".json", { cache: "no-cache" })
      .then(function (r) { if (!r.ok) throw new Error("http " + r.status); return r.json(); })
      .then(function (dict) {
        I18N = dict;
        document.documentElement.lang = lang;
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
    var lang = detectLang();
    if (sel) sel.value = lang;
    loadLang(lang);
    if (sel) {
      sel.addEventListener("change", function () {
        var next = sel.value;
        localStorage.setItem("camcon-lang", next);
        loadLang(next);
      });
    }
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
      var panel = trigger.nextElementSibling;
      trigger.addEventListener("click", function () {
        var open = trigger.getAttribute("aria-expanded") === "true";
        trigger.setAttribute("aria-expanded", open ? "false" : "true");
        panel.style.maxHeight = open ? null : panel.scrollHeight + "px";
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

    fetch("assets/data/supported-cameras.json", { cache: "no-cache" })
      .then(function (r) { if (!r.ok) throw new Error("http " + r.status); return r.json(); })
      .then(function (data) {
        camData = data;
        var totalEl = document.getElementById("camTotal");
        if (totalEl) totalEl.textContent = (data.total || data.cameras.length).toLocaleString();
        buildVendorChips();
        renderCameras();
      })
      .catch(function () {
        var totalEl = document.getElementById("camTotal");
        if (totalEl) totalEl.textContent = "—";
      });
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
      return '<li><span class="cam-vendor">' + esc(vendorLabel(c.vendor)) + "</span>" +
        '<span class="cam-model">' + esc(c.model) + "</span></li>";
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
    if (!camData) return;
    buildVendorChips();
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
    var reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (reduce || !("IntersectionObserver" in window)) {
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
})();
