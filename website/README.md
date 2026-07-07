# CamCon 공식 홈페이지

CamCon(안드로이드 DSLR/미러리스 무선 테더링 + 필름 시뮬레이션 앱) 공식 소개 사이트.
빌드 스텝이 없는 **정적 사이트**(바닐라 HTML/CSS/JS)이며, nginx(Docker)로 서빙한다.

## 구성

```
website/
├── index.html                      단일 페이지 (섹션 앵커 네비, data-i18n)
├── assets/
│   ├── css/style.css               시네마 다크 스타일
│   ├── js/main.js                  i18n·탭·아코디언·리빌·스크롤스파이·카메라 탐색기 (의존성 0)
│   ├── img/
│   │   ├── app-icon.png            실제 앱 아이콘 (파비콘·헤더·히어로 공용)
│   │   └── og-card.svg             OG 공유 카드
│   ├── data/supported-cameras.json libgphoto2 파싱 산출물 (지원 모델 목록)
│   └── i18n/{ko,en,ja,zh,de,es,fr,it}.json  8개 언어 번역 (런타임 로딩)
├── tools/
│   └── generate_supported_cameras.py  supported-cameras.json 생성기
├── nginx.conf                      gzip · 정적 캐시 헤더 · 단순 서빙
├── Dockerfile                      nginx:alpine
└── docker-compose.yml              camcon-web, 8091:80
```

파비콘·헤더 로고·히어로 뱃지는 모두 실제 앱 아이콘(`app-icon.png`)을 사용한다. 아이콘은 `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png`에서 복사한 것이며, 앱 아이콘이 바뀌면 이 파일을 교체한다.

## 로컬에서 열기 (Docker 없이)

정적 파일이므로 아무 파일 서버로도 확인 가능하다.

```bash
cd website
python3 -m http.server 8091
# http://localhost:8091 접속
```

> `file://`로 직접 열어도 대부분 동작하지만, 상대 경로·CDN 폰트는 HTTP 서버로 확인하는 편이 정확하다.

## Docker로 실행

```bash
cd website
docker compose up -d --build
# http://localhost:8091 접속

curl -I http://localhost:8091          # 응답 헤더 확인
docker compose logs -f camcon-web      # 로그
docker compose down                    # 중지
```

## NAS 배포 절차

1. `website/` 디렉터리 전체를 NAS로 복사한다.
2. 복사한 경로에서 컨테이너를 올린다.
   ```bash
   docker compose up -d --build
   ```
3. Nginx Proxy Manager에서 원하는 도메인 → **컨테이너 `camcon-web:80`(호스트 `8091`)** 로 프록시하고 SSL을 붙인다. (프록시/도메인 설정은 이 저장소 범위 밖)

## 지원 카메라 목록 재생성

`assets/data/supported-cameras.json`은 libgphoto2 소스 트리의 camlib 모델 테이블을 파싱해 생성한다.
CamCon arm64-v8a 빌드에 실제 동봉된 19개 camlib(ax203 canon digigr8 dimagev directory jl2005a jl2005c
kodak_dc240 mars pentax ptp2 quicktake1x0 ricoh_g3 sierra sonix sq905 st2205 topfield tp6801)만 대상으로 한다.

```bash
cd website
python3 tools/generate_supported_cameras.py \
  /Users/ini-k/build_LibG/gphoto-build_16k/arm64-v8a/libgphoto2_src/camlibs
# → assets/data/supported-cameras.json 갱신 (camlib별 신규 모델 수 + 총계 출력)
```

- 인자는 `.so`를 빌드한 libgphoto2 소스의 `camlibs/` 디렉터리 경로.
- ptp2 USB 모델 테이블이 대부분을 차지하며, ptpip 6종은 `connection: "wifi"`로 표기된다.
- libgphoto2를 재빌드해 신규 기종이 추가되면 이 스크립트를 다시 돌려 JSON을 갱신한다.
- 목록은 USB 유선 기준이다. Wi-Fi 실기 검증 현황은 별개(페이지 상단 표).

## 다국어 (i18n)

- 빌드 스텝 없이 `data-i18n` 속성 + `assets/i18n/{lang}.json` 런타임 로딩.
- `navigator.language` 자동 감지 → `localStorage`(`camcon-lang`) 저장 → 헤더 드롭다운으로 전환, `<html lang>` 동기화.
- 문자열을 추가/변경할 때는 **8개 언어 JSON 모두** 같은 키를 유지해야 한다(키 개수·이름 일치). 카메라 모델명은 번역하지 않는다.
- JS 미동작(예: `file://` 직접 열람) 시 HTML 인라인 한국어가 폴백으로 남는다.

## 유지보수 메모

- 문의 채널이 확정되면 `index.html`의 `<!-- TODO: 문의 이메일/채널 확정 후 교체 -->` 주석 위치(카메라 섹션 안내문, 푸터 링크)를 실제 링크로 교체한다. 문의 라벨/링크 텍스트는 8개 i18n JSON의 `footer.contact` 키에도 반영한다.
- 필름 개수·지원 카메라 등 콘텐츠는 앱 사실과 동기화한다. 과장 표현을 넣지 않는다.
- 가격은 "출시 시 공개"로 유지한다(구체 금액 미표기).
