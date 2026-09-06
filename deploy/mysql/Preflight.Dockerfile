# TLS tooling only. MySQL stays in the independently verified Oracle runtime.
FROM alpine:3.24@sha256:28bd5fe8b56d1bd048e5babf5b10710ebe0bae67db86916198a6eec434943f8b
RUN apk add --no-cache openssl=3.5.8-r0 libssl3=3.5.8-r0 libcrypto3=3.5.8-r0 su-exec=0.3-r0 \
    && openssl version \
    && su-exec 27:27 id
