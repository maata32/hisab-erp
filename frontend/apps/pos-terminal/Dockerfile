# syntax=docker/dockerfile:1.7

FROM node:20-alpine AS deps
WORKDIR /workspace
COPY package.json package-lock.json ./
COPY nx.json tsconfig.base.json .eslintrc.json .prettierrc tailwind.config.js postcss.config.js ./
# npm ci + committed lockfile = reproducible builds that match CI (npm install would re-resolve).
RUN npm ci --no-audit --fund=false

FROM deps AS build
WORKDIR /workspace
COPY apps apps
COPY libs libs
RUN npx nx build pos-terminal --configuration=production

FROM nginx:1.27-alpine AS runtime
RUN apk add --no-cache curl && \
    addgroup -S app && adduser -S -G app app
COPY --from=build /workspace/dist/apps/pos-terminal/ /usr/share/nginx/html/
COPY apps/pos-terminal/nginx.conf /etc/nginx/conf.d/default.conf
COPY apps/erp-admin/nginx-proxy.conf /etc/nginx/conf.d/proxy.conf
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD curl -fsS http://localhost/ || exit 1
