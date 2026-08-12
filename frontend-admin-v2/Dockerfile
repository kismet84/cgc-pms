FROM node:22-alpine@sha256:c610fcdfb1d5b4740dd70c284ed3cb16bb857e0f7166196e36a5501df7a3aa32 AS build

WORKDIR /workspace
RUN corepack enable && corepack prepare pnpm@11.0.9 --activate
RUN pnpm config set registry https://registry.npmmirror.com

COPY packages/frontend-contracts ./packages/frontend-contracts
COPY frontend-admin-v2/package.json frontend-admin-v2/pnpm-lock.yaml frontend-admin-v2/pnpm-workspace.yaml ./frontend-admin-v2/
COPY frontend-admin-v2/patches ./frontend-admin-v2/patches
WORKDIR /workspace/frontend-admin-v2
RUN --mount=type=cache,id=cgc-pms-v2-pnpm,target=/root/.local/share/pnpm/store \
    pnpm install --ignore-scripts --frozen-lockfile

COPY frontend-admin-v2 ./
ARG VITE_BASE_PATH=/
ARG VITE_FEATURE_PWA=false
ARG VITE_FEATURE_OFFLINE_DRAFT=false
ARG VITE_FEATURE_OFFLINE_SYNC=false
ARG VITE_FEATURE_FIELD_DAILY_LOG=false
ARG VITE_FEATURE_FIELD_QUALITY_SAFETY=false
ARG VITE_FEATURE_NOTIFICATION_MULTI_CLIENT=false
ENV VITE_BASE_PATH=$VITE_BASE_PATH
ENV VITE_FEATURE_PWA=$VITE_FEATURE_PWA \
    VITE_FEATURE_OFFLINE_DRAFT=$VITE_FEATURE_OFFLINE_DRAFT \
    VITE_FEATURE_OFFLINE_SYNC=$VITE_FEATURE_OFFLINE_SYNC \
    VITE_FEATURE_FIELD_DAILY_LOG=$VITE_FEATURE_FIELD_DAILY_LOG \
    VITE_FEATURE_FIELD_QUALITY_SAFETY=$VITE_FEATURE_FIELD_QUALITY_SAFETY \
    VITE_FEATURE_NOTIFICATION_MULTI_CLIENT=$VITE_FEATURE_NOTIFICATION_MULTI_CLIENT
RUN pnpm build

FROM nginx:1.30-alpine@sha256:97d490c12ba55b4946b01546d1c3ed324e8d41ab1c9fcb2a616aa470620e5b46
COPY frontend-admin-v2/nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /workspace/frontend-admin-v2/dist /usr/share/nginx/html
RUN apk add --no-cache --virtual .setcap-deps libcap-setcap \
    && setcap 'cap_net_bind_service=+ep' /usr/sbin/nginx \
    && apk del .setcap-deps \
    && mkdir -p /etc/nginx/ssl \
    && touch /run/nginx.pid \
    && chown -R nginx:nginx /etc/nginx/conf.d /etc/nginx/templates /etc/nginx/ssl /var/cache/nginx /run/nginx.pid
USER nginx
EXPOSE 80 443
HEALTHCHECK --interval=15s --timeout=3s --retries=3 CMD wget -qO- http://127.0.0.1/healthz || exit 1
CMD ["nginx", "-g", "daemon off;"]
