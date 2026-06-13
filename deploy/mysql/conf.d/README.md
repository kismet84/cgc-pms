# MySQL Custom Configuration

Place custom MySQL `.cnf` files here. They will be mounted into the container at `/etc/mysql/conf.d/`.

Example `custom.cnf`:
```ini
[mysqld]
binlog_format = ROW
binlog_expire_logs_days = 7
max_allowed_packet = 64M
```
