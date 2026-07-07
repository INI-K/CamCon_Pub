# CamCon 공식 홈페이지

CamCon(안드로이드 DSLR/미러리스 무선 테더링 + 필름 시뮬레이션 앱) 공식 소개 사이트.
빌드 스텝이 없는 **정적 사이트**(바닐라 HTML/CSS/JS)이며, nginx(Docker)로 서빙한다.

## 구성

```
website/
├── index.html              단일 페이지 (섹션 앵커 네비)
├── favicon.svg             리티클+조리개 파비콘
├── assets/
│   ├── css/style.css       시네마 다크 스타일
│   ├── js/main.js          탭·아코디언·리빌·스크롤스파이 (의존성 0)
│   └── img/og-card.svg     OG 공유 카드
├── nginx.conf              gzip · 정적 캐시 헤더 · 단순 서빙
├── Dockerfile              nginx:alpine
└── docker-compose.yml      camcon-web, 8091:80
```

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

## 유지보수 메모

- 문의 채널이 확정되면 `index.html`의 `<!-- TODO: 문의 이메일/채널 확정 후 교체 -->` 주석 위치(카메라 섹션 안내문, 푸터 링크)를 실제 링크로 교체한다.
- 필름 개수·지원 카메라 등 콘텐츠는 앱 사실과 동기화한다. 과장 표현을 넣지 않는다.
- 가격은 "출시 시 공개"로 유지한다(구체 금액 미표기).
