# Stage 1: Build
FROM node:22-alpine@sha256:9385cd9f3001dfc3431e8ead12c43e9e1f87cc1b9b5c6cfd0f73865d405b27c4 AS builder

# Install pnpm via Node.js corepack (version pinned from packageManager)
RUN corepack enable && corepack prepare pnpm@11.0.9 --activate

WORKDIR /app

# Copy package manifests for layer caching
COPY package.json pnpm-lock.yaml ./

# --ignore-scripts: pnpm v10+ requires explicit build approval per package; skip scripts for speed
RUN pnpm install --frozen-lockfile --ignore-scripts

# Copy source and configs
COPY vite.config.ts tsconfig*.json index.html ./
COPY src/ ./src/
# COPY public/ ./public/

# Build (includes vue-tsc type-check + vite build)
RUN pnpm build

# Stage 2: Runtime
FROM nginx:1.27-alpine@sha256:8b1e78743a03dbb2c95171cc58639fef29abc8816598e27fb910ed2e621e589a

# Copy built assets from builder stage
COPY --from=builder /app/dist/ /usr/share/nginx/html/

# Copy nginx config
COPY nginx.conf /etc/nginx/conf.d/default.conf

# SSL certificate directory (mount certificates at runtime via volume or secret)
# For dev: generate self-signed cert with openssl and mount as volume
# For prod: mount CA-signed certs (e.g., Let's Encrypt) at the same paths
#   /etc/nginx/ssl/server.crt — certificate file
#   /etc/nginx/ssl/server.key — private key file
RUN mkdir -p /etc/nginx/ssl

EXPOSE 80
EXPOSE 443

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:80/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
