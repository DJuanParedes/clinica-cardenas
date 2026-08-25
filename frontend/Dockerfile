FROM node:22.16-alpine AS build
WORKDIR /app
ENV NG_CLI_ANALYTICS=false
ENV CI=true
ENV NODE_OPTIONS=--max-old-space-size=2048
COPY package*.json ./
RUN --mount=type=cache,target=/root/.npm npm config set fetch-retries 5 \
    && npm config set fetch-retry-factor 2 \
    && npm config set fetch-retry-mintimeout 10000 \
    && npm config set fetch-retry-maxtimeout 120000 \
    && if [ -f package-lock.json ]; then npm ci --no-audit --no-fund; else npm install --no-audit --no-fund; fi
COPY . .
RUN npm run build -- --configuration production \
    && test -f /app/dist/clinica-cardenas-web/browser/index.html

FROM nginx:1.30.4
ARG APP_VERSION=dev
LABEL org.opencontainers.image.version="${APP_VERSION}" pe.clinica.cardenas.module="frontend"
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/clinica-cardenas-web/browser /usr/share/nginx/html
COPY clinic-config.template.js /etc/clinic-config/clinic-config.template.js
COPY 40-clinic-config.sh /docker-entrypoint.d/40-clinic-config.sh
RUN chmod 755 /docker-entrypoint.d/40-clinic-config.sh
EXPOSE 80
