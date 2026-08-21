FROM nginx:alpine

# 정적 사이트 서빙용 nginx 설정 교체
COPY nginx.conf /etc/nginx/conf.d/default.conf
# nginx.conf가 location마다 include 하는 공통 보안 헤더
COPY snippets/ /etc/nginx/snippets/

# 사이트 정적 파일 복사 (OG 카드/카메라 JSON/번역은 assets/ 아래에 포함)
# favicon.svg는 index.html·guide·privacy·terms·delete 5개 문서가 전부 참조한다. 빠지면 전 페이지 404.
# guide.html은 한국어 원본이라 루트에서 서빙된다(다른 7개 언어판은 아래 dist-lang/ 에 들어 있다).
COPY index.html guide.html favicon.svg /usr/share/nginx/html/
COPY robots.txt sitemap.xml privacy.html terms.html delete.html /usr/share/nginx/html/
COPY assets/ /usr/share/nginx/html/assets/

# 언어별 정적 페이지 (tools/build_i18n_pages.py 산출물: dist-lang/{en,ja,zh,de,es,fr,it}/index.html)
# 디렉터리는 "내용물"이 복사되므로 언어를 추가해도 이 줄은 그대로 둔다.
# 전제: 이미지 빌드 전에 반드시 build_i18n_pages.py를 실행해 dist-lang/을 만들어야 한다.
#      (없으면 빌드가 여기서 멈춘다 — 언어 페이지 없는 채로 조용히 배포되는 것보다 낫다.)
COPY dist-lang/ /usr/share/nginx/html/

# COPY 는 빌드 컨텍스트의 퍼미션을 그대로 이미지에 박는다. 배포 트리를 tar-over-ssh 로
# 옮기면 원본(macOS) 퍼미션이 따라와 0600/0700 이 되는 일이 있는데, 그러면 nginx 워커가
# 비-root 라 전 페이지가 403 이 된다(실제로 한 번 라이브가 이렇게 죽었다).
# 빌드 시점에 읽기 권한을 강제해 배포 트리 퍼미션과 무관하게 만든다.
RUN chmod -R a+rX /usr/share/nginx/html /etc/nginx/snippets /etc/nginx/conf.d

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s \
  CMD wget -qO- http://localhost/ >/dev/null 2>&1 || exit 1
