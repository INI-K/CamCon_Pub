# CamCon 공식 홈페이지

CamCon(안드로이드 DSLR/미러리스 무선 테더링 + 필름 시뮬레이션 앱) 공식 소개 사이트.
바닐라 HTML/CSS/JS(런타임 의존성 0)이며 nginx(Docker)로 서빙한다.
**배포 전 빌드 스텝이 하나 있다** — `tools/build_i18n_pages.py` 가 언어별 페이지(`dist-lang/`)와 `sitemap.xml` 을 생성한다.

## 구성

```
website/
├── index.html                      한국어 정본 단일 페이지 (섹션 앵커 네비, data-i18n) — 언어 페이지의 템플릿
├── privacy.html                    개인정보처리방침 (tools/build_legal_pages.py 산출물)
├── terms.html                      이용약관             (동상)
├── delete.html                     계정 삭제 안내       (동상)
├── favicon.svg                     4개 문서가 전부 참조 (Dockerfile 에 개별 COPY)
├── robots.txt
├── sitemap.xml                     build_i18n_pages.py 산출물 (8개 언어 + legal 3종)
├── assets/
│   ├── css/style.css               시네마 다크 스타일
│   ├── js/main.js                  i18n·탭·아코디언·리빌·스크롤스파이·카메라 탐색기·언어 제안 배너 (의존성 0)
│   ├── img/
│   │   ├── app-icon.png            앱 아이콘 사본 (192px) — 현재 어느 페이지도 참조하지 않는다
│   │   ├── apple-touch-icon.png    iOS 홈화면 아이콘 (180px 불투명 — iOS는 SVG 미지원)
│   │   ├── og-card.png             OG 공유 카드(한국어) — 루트(/) 전용, 정본 소스: tools/og-card.html
│   │   ├── og-card-en.png          OG 공유 카드(영문) — 생성 언어 페이지 7종이 공유
│   │   └── play/badge-*.png        8개 언어 Google Play 배지
│   ├── film/                       필름 예시·전후 비교 이미지 (render_film_examples.py 산출물)
│   ├── samples/{grid,hero,alt}.jpg 필름 예시 원본 사진
│   ├── data/supported-cameras.json 표준 PTP 카메라 드라이버 파싱 산출물 (지원 모델 목록)
│   └── i18n/{ko,en,ja,zh,de,es,fr,it}.json  8개 언어 번역 (루트는 런타임 로딩, 생성 페이지는 인라인)
├── dist-lang/{en,ja,zh,de,es,fr,it}/index.html   생성물이지만 **레포에 커밋한다** (Dockerfile 이 COPY)
├── snippets/security-headers.conf  nginx 가 location 마다 include 하는 공통 보안 헤더
├── tools/
│   ├── build_i18n_pages.py         언어별 페이지 + sitemap.xml 생성기 (배포 전 필수)
│   ├── build_legal_pages.py        legal_content.json → privacy/terms/delete.html
│   ├── legal_content.json          법적 문서 원문 (ko + en)
│   ├── generate_supported_cameras.py  supported-cameras.json 생성기
│   ├── render_film_examples.py     필름 예시·전후 비교 이미지 렌더러
│   ├── gen_playstore_graphics.py   Play Console 그래픽 렌더러
│   ├── og-card.html                og-card.png 렌더 템플릿 (헤드리스 크롬, 렌더법은 파일 상단 주석)
│   └── i18n_extract.py · i18n_backfill.py   ※ 사이트가 아니라 **앱** strings.xml 백필 도구
├── nginx.conf                      gzip · 정적 캐시 헤더 · 단순 서빙
├── Dockerfile                      nginx:alpine
└── docker-compose.yml              camcon-web, 8091:80
```

아이콘 출처는 셋으로 나뉜다 — 파비콘은 `favicon.svg`, 헤더 로고·히어로 마크는 `index.html` 안의 인라인 SVG(`.brand-mark`),
iOS 홈화면만 래스터 `apple-touch-icon.png`(180px 불투명, SVG 미지원)를 쓴다. 앱 아이콘이 바뀌면 `apple-touch-icon.png` 를 다시 뽑는다.

## 로컬에서 열기 (Docker 없이)

한국어 루트만 볼 때는 아무 파일 서버로도 된다.

```bash
cd website
python3 -m http.server 8091
# http://localhost:8091 접속
```

단 이 방식에서는 **`/en/` 같은 언어 페이지가 404**다 — 생성물이 `dist-lang/en/` 아래 있고,
배포 시에만 그 내용물이 사이트 루트로 올라가기 때문이다.
언어 페이지·언어 전환 링크까지 실제 경로 그대로 보려면 배포와 같은 트리를 만들어 서빙한다.

```bash
cd website
python3 tools/build_i18n_pages.py
rm -rf /tmp/camcon-web && mkdir -p /tmp/camcon-web
cp -R index.html favicon.svg robots.txt sitemap.xml privacy.html terms.html delete.html assets /tmp/camcon-web/
cp -R dist-lang/* /tmp/camcon-web/          # Dockerfile 의 COPY dist-lang/ 과 같은 배치
python3 -m http.server 8091 --directory /tmp/camcon-web
# http://localhost:8091/ (ko) · http://localhost:8091/en/ …
```

> `file://`로 직접 열어도 대부분 동작하지만, 절대 경로(`/assets/…`)·CDN 폰트는 HTTP 서버로 확인해야 정확하다.

## Docker로 실행

```bash
cd website
python3 tools/build_i18n_pages.py   # dist-lang/ 이 없으면 이미지 빌드가 COPY 에서 실패한다
docker compose up -d --build
# http://localhost:8091 접속

curl -I http://localhost:8091          # 응답 헤더 확인
docker compose logs -f camcon-web      # 로그
docker compose down                    # 중지
```

## NAS 배포 절차

1. **언어 페이지를 먼저 생성한다.** `Dockerfile` 이 `COPY dist-lang/` 로 하드 의존하므로 이 단계를 빼면 이미지 빌드가 실패한다.
   ```bash
   cd website
   python3 tools/build_i18n_pages.py           # dist-lang/{en,ja,zh,de,es,fr,it}/ + sitemap.xml
   python3 tools/build_i18n_pages.py --check    # (선택) 산출물이 최신인지만 확인, 미갱신이면 exit 1
   ```
2. `website/` 디렉터리 전체를 NAS로 복사한다(생성된 `dist-lang/` 포함).
3. 복사한 경로에서 컨테이너를 올린다.
   ```bash
   docker compose up -d --build
   ```
4. Nginx Proxy Manager에서 원하는 도메인 → **컨테이너 `camcon-web:80`(호스트 `8091`)** 로 프록시하고 SSL을 붙인다. (프록시/도메인 설정은 이 저장소 범위 밖)

## 지원 카메라 목록 재생성

`assets/data/supported-cameras.json`은 표준 PTP 카메라 드라이버 소스 트리의 camlib 모델 테이블을 파싱해 생성한다.
CamCon arm64-v8a 빌드에 실제 동봉된 19개 camlib(ax203 canon digigr8 dimagev directory jl2005a jl2005c
kodak_dc240 mars pentax ptp2 quicktake1x0 ricoh_g3 sierra sonix sq905 st2205 topfield tp6801)만 대상으로 한다.

```bash
cd website
python3 tools/generate_supported_cameras.py <카메라-드라이버-소스>/camlibs
# 정확한 소스 경로는 tools/generate_supported_cameras.py 헤더 주석 참조.
# → assets/data/supported-cameras.json 갱신 (파싱/제외/유지 개수 + 벤더별 개수 출력)
```

- 인자는 `.so`를 빌드한 표준 PTP 카메라 드라이버 소스의 `camlibs/` 디렉터리 경로.
- ptp2 USB 모델 테이블이 대부분을 차지하며, PTP/IP(Wi-Fi) 항목은 `connection: "wifi"`로 표기된다.
- **유명 카메라 제조사만 큐레이션**한다. 스크립트의 `VENDOR_WHITELIST`(Canon · Nikon · Sony · Fujifilm · Panasonic · Olympus · OM System · Pentax · Ricoh · Leica · Sigma · Hasselblad · Casio 등)에 없는 벤더는 제외된다. 유지할 브랜드를 바꾸려면 이 상수를 수정한다.
- 카메라 드라이버 라이브러리를 재빌드해 신규 기종이 추가되면 이 스크립트를 다시 돌려 JSON을 갱신한다.
- 목록은 USB 유선 기준이다. Wi-Fi 실기 검증 현황은 별개(페이지 상단 표).

## 다국어 (i18n) — URL이 정본

언어마다 URL이 따로 있다. 한 URL을 8개 언어가 공유하던 옛 방식(자동 감지 → 그 자리에서 문구 교체)은 폐기됐다.
hreflang·OG 카드·색인·"영어 페이지를 영어로 공유"가 전부 URL 단위로만 성립하기 때문이다.

| 언어 | URL | 실체 |
|------|-----|------|
| 한국어 | `/` | `index.html` (정본 템플릿). `/ko/` 는 만들지 않고 hreflang `ko` 를 `/` 로 건다 |
| en·ja·zh·de·es·fr·it | `/en/` … | `dist-lang/<lang>/index.html` — `build_i18n_pages.py` 생성물 |

- 생성 페이지는 사전을 `</head>` 직전에 인라인(`window.__I18N__`)한다 → i18n fetch 0, FOUC 0, **JS를 꺼도 완전한 번역본**.
  루트(`/`)만 `assets/i18n/ko.json` 을 런타임 로딩하며, 본문 인라인이 이미 한국어라 실패해도 보이는 변화가 없다.
- `x-default` 는 `/en/`. 8개 밖 언어권(pt/ru/id/…)은 영어 페이지로 안내한다.
- **자동 리다이렉트는 하지 않는다**(Google 명시 비권고). 대신 `main.js` 가 하단 제안 배너(`#langSuggest`)를 세션 1회 띄우고,
  이동은 사용자가 링크를 누를 때만 일어난다. 표시/숨김의 단일 소스는 `hidden` 속성이며 `display:flex` 는
  `style.css` 의 `#langSuggest:not([hidden])` 에만 있다(인라인 `display` 는 `[hidden]` 을 이겨서 배너가 항상 뜬다).
  - `camcon-lang`(localStorage) — 마지막으로 직접 고른 언어. **제안 소스**다("묻지 마라"가 아니다).
  - `camcon-lang-suggested`(localStorage) — 배너를 닫음. 노출을 막는 유일한 영구 키.
  - `camcon-lang-seen`(sessionStorage) — 이번 세션에 이미 1회 노출.
- 문자열을 추가/변경할 때는 **8개 언어 JSON 모두** 같은 키를 유지해야 한다(키 개수·이름 일치). 카메라 모델명은 번역하지 않는다.

### 재생성 규약 (필독)

**`index.html` 이나 `assets/i18n/*.json` 을 고치면 반드시 `python3 tools/build_i18n_pages.py` 를 재실행하고
생성된 `dist-lang/` 을 같은 커밋에 포함한다.** 빼먹으면 한국어 루트만 고쳐지고 나머지 7개 언어 페이지는 옛 내용으로 배포된다.
`dist-lang/` 은 생성물이지만 `.gitignore` 하지 않는다 — `Dockerfile` 이 `COPY dist-lang/` 로 하드 의존하고,
배포가 tar-over-ssh 로 레포 트리를 그대로 옮기기 때문이다.

```bash
cd website
python3 tools/build_i18n_pages.py            # 생성 + hreflang 대칭 자체 검증 (실패 시 exit 1)
python3 tools/build_i18n_pages.py --check    # 커밋된 dist-lang 이 최신인지만 판정 (stale 이면 exit 1)
```

`--check` 는 파일을 쓰지 않으므로 CI·커밋 전 확인용으로 쓴다.

## 유지보수 메모

- 문의 채널은 푸터의 `mailto:` 링크 하나다(`index.html`). 주소를 바꾸면 그 링크만 고치면 되고, 라벨은 8개 i18n JSON의 `footer.contact` 키에 있다.
- 필름 개수·지원 카메라 등 콘텐츠는 앱 사실과 동기화한다. 과장 표현을 넣지 않는다.
- PRO 가격은 미확정이다. `pricing.proPrice` 는 "준비 중"/"Coming soon"으로 유지하고 구체 금액을 넣지 않는다.
