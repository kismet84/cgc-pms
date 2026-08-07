# Stage 1: Build
FROM node:22-alpine@sha256:9385cd9f3001dfc3431e8ead12c43e9e1f87cc1b9b5c6cfd0f73865d405b27c4 AS builder

# Install pnpm via Node.js corepack (version pinned from packageManager)
RUN corepack enable && corepack prepare pnpm@11.0.9 --activate
RUN pnpm config set fetch-timeout 600000

WORKDIR /app

# Copy package manifests and pnpm workspace config for layer caching
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY patches/ ./patches/

# --ignore-scripts: pnpm v10+ requires explicit build approval per package; skip scripts for speed
RUN --mount=type=cache,id=cgc-pms-legacy-pnpm,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile --ignore-scripts \
    && test -x node_modules/.pnpm/@esbuild+linux-x64@0.25.0/node_modules/@esbuild/linux-x64/bin/esbuild

# Copy source and configs
COPY vite.config.ts tsconfig*.json index.html ./
COPY src/ ./src/
COPY public/ ./public/

# Build (includes vue-tsc type-check + vite build)
RUN pnpm build

# Stage 2: Runtime
FROM nginx:1.30-alpine@sha256:97d490c12ba55b4946b01546d1c3ed324e8d41ab1c9fcb2a616aa470620e5b46

# Copy built assets from builder stage
COPY --from=builder /app/dist/ /usr/share/nginx/html/

# Copy nginx config as an official nginx template so BACKEND_URL is substituted at startup.
COPY nginx.conf /etc/nginx/templates/default.conf.template

# SSL certificate directory (mount certificates at runtime via volume or secret)
# For dev: generate self-signed cert with openssl and mount as volume
# For prod: mount CA-signed certs (e.g., Let's Encrypt) at the same paths
#   /etc/nginx/ssl/server.crt — certificate file
#   /etc/nginx/ssl/server.key — private key file
RUN mkdir -p /etc/nginx/ssl \
    && apk add --no-cache --virtual .setcap-deps libcap-setcap \
    && setcap 'cap_net_bind_service=+ep' /usr/sbin/nginx \
    && apk del .setcap-deps \
    && touch /run/nginx.pid \
    && chown -R nginx:nginx /etc/nginx/conf.d /etc/nginx/templates /etc/nginx/ssl /var/cache/nginx /run/nginx.pid

USER nginx

EXPOSE 80
EXPOSE 443

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -q -O /dev/null http://localhost:80/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
