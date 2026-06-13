# Stage 1: Build
FROM node:20-alpine AS builder

# Install pnpm (version pinned from packageManager)
RUN npm install -g pnpm@11.0.9

WORKDIR /app

# Copy package manifests for layer caching
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

# Copy source and configs
COPY vite.config.ts tsconfig*.json index.html ./
COPY src/ ./src/
# COPY public/ ./public/

# Build (includes vue-tsc type-check + vite build)
RUN pnpm build

# Stage 2: Runtime
FROM nginx:1.27-alpine

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
