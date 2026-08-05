FROM alpine@sha256:48b0309ca019d89d40f670aa1bc06e426dc0931948452e8491e3d65087abc07d

RUN apk add --no-cache libreoffice=7.6.7.2-r2 python3=3.12.13-r0 font-dejavu=2.37-r5 \
    && addgroup -S preview \
    && adduser -S -D -H -G preview -u 10001 preview

COPY --chown=preview:preview server.py /app/server.py

USER 10001:10001
WORKDIR /app
EXPOSE 8080
ENTRYPOINT ["python3", "/app/server.py"]
