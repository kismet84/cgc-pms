# Local/CI runtime image, not a registry publication. The Oracle digest contains
# MySQL 8.4.12. Remove administration-only Shell/Python; patch SQLite because RPM
# and libdnf require it. Keep RPM dependency checks; no --nodeps or mutable yum update.
FROM container-registry.oracle.com/mysql/community-server:8.4@sha256:7dcc4add9183664de3a214daf85a50c3ba6cccfd7534f700b6561bf5b41885be
# Oracle ELSA-2026-58936 publishes this exact SHA256 (linux/amd64).
ADD --checksum=sha256:fbf3fab2833d74866a02db82e4efb8e93d80e80757d83c1142357724026db6c5 https://yum.oracle.com/repo/OracleLinux/OL9/baseos/latest/x86_64/getPackage/sqlite-libs-3.34.1-11.el9_8.x86_64.rpm /tmp/sqlite-libs.rpm
RUN rpm -e mysql-shell \
    && rpm -U /tmp/sqlite-libs.rpm \
    && rm /tmp/sqlite-libs.rpm \
    && ! rpm -q mysql-shell \
    && test "$(rpm -q sqlite-libs)" = sqlite-libs-3.34.1-11.el9_8.x86_64 \
    && test "$(id -u mysql):$(id -g mysql)" = 27:27 \
    && mysqld --version | grep -F '8.4.12' \
    && mysql --version && mysqldump --version
