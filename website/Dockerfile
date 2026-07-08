FROM nginx:alpine

# 정적 사이트 서빙용 nginx 설정 교체
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 사이트 정적 파일 복사 (파비콘/OG/카메라 JSON/번역은 assets/ 아래에 포함)
COPY index.html /usr/share/nginx/html/
COPY robots.txt sitemap.xml /usr/share/nginx/html/
COPY assets/ /usr/share/nginx/html/assets/

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s \
  CMD wget -qO- http://localhost/ >/dev/null 2>&1 || exit 1
