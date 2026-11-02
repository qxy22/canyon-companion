# 部署到阿里云 ECS（CentOS Stream 9）

前端是纯静态文件，用 Nginx 托管并反向代理 `/api` 与 `/ws`；后端是一个 Spring Boot
fat jar，用 systemd 托管。数据库 MySQL 8，缓存 Redis，都装在同一台 ECS 上。

**前端代码一行都不用改**：`baseURL` 写的是相对路径 `/api`，WebSocket 地址是按
`location.host` 拼的，所以只要让它们同源（都由 Nginx 的 80 端口出来），就不需要配跨域、
也不需要改任何地址。

---

## 0. 先说一件必须知道的事

「录入战绩」这个功能是**后端直接连本机的英雄联盟客户端**（读 `127.0.0.1` 上的 LCU 接口）。
部署到 ECS 后，后端在云上、游戏在你本机，它读不到。

- 线上点「录入战绩」会提示：*未找到本地游戏客户端凭证，请确认游戏已启动并登录*。
  这是预期行为，不是故障，其它功能都不受影响。
- 答辩演示要用这个功能，就在笔记本上本地跑后端（`java -jar` + 本地 MySQL），
  或者在笔记本上跑全套、用内网穿透给评委访问。

另外，`/uploads/` 下的文件是**不校验登录就能直接访问**的（本地开发时就是这样，
URL 本身就是给 `<img src>` 用的）。别把敏感文件放到那个目录里。

---

## 1. 准备

**ECS 规格**：2 核 4G 起步。MySQL 8 空跑就要 700MB+，JVM 默认再吃 1G，4G 是底线。

**安全组**（阿里云控制台 → 安全组 → 入方向）：

| 端口 | 来源 | 说明 |
|---|---|---|
| 22 | 你的 IP | SSH |
| 80 | 0.0.0.0/0 | HTTP |
| 443 | 你的 IP 或 0.0.0.0/0 | 要上 HTTPS 才开 |

**8080 不要对外开放**——Nginx 在本机反向代理过去就行，端口开了等于把后端裸奔出去。

> 用域名访问需要备案，没备案就用公网 IP 直接访问。

---

## 2. 装软件

```bash
sudo dnf update -y

# JDK 17（项目编译目标就是 17，别装 8 或 21）
sudo dnf install -y java-17-openjdk-headless
java -version          # 应显示 17.x

# MySQL 8
sudo dnf install -y mysql-server
sudo systemctl enable --now mysqld

# Redis
sudo dnf install -y redis
sudo systemctl enable --now redis
redis-cli ping         # 应回 PONG

# Nginx
sudo dnf install -y nginx
sudo systemctl enable --now nginx
```

**防火墙**：

```bash
sudo firewall-cmd --permanent --add-service=http
sudo firewall-cmd --permanent --add-service=https
sudo firewall-cmd --reload
```

**SELinux（CentOS 上最常见的 502 / 403 元凶）**：

先确认它到底开没开。阿里云的一部分公共镜像是关掉的，那就不用折腾下面这些：

```bash
getenforce
```

- 输出 `Disabled` → 直接跳过本节，也不用管第 7 节的上下文设置。
- 输出 `Enforcing` 或 `Permissive` → 继续看。

开着的时候它会**禁止 Nginx 向外发起网络连接**，于是一切正常但访问 `/api` 就是 502 Bad Gateway：

```bash
sudo setsebool -P httpd_can_network_connect 1

# semanage 命令在最小化安装里没有，装了这个后面才能用
sudo dnf install -y policycoreutils-python-utils
```

---

## 3. 建数据库

JPA 只会自动建**表**，不会建**库**，所以这一步必须手工做：

```bash
sudo mysql -uroot
```

```sql
CREATE DATABASE lol DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE USER 'lol'@'localhost' IDENTIFIED BY '换成强密码';
GRANT ALL PRIVILEGES ON lol.* TO 'lol'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

表不用建。第一次启动时 `spring.jpa.hibernate.ddl-auto=update` 会把所有表建出来。

---

## 4. 本地打包

在本机（Windows）执行：

```bash
# 后端：产出 lol-server/target/lol-server-0.0.1-SNAPSHOT.jar
cd /d/outputs
mvn -o -DskipTests package

# 前端：产出 lol-web/dist/
cd /d/outputs/lol-web
npm run build
```

---

## 5. 上传到 ECS

先在服务器上把目录建好：

```bash
sudo mkdir -p /opt/lol/config /var/lib/lol/uploads /var/www/lol-web
sudo useradd -r -s /sbin/nologin lol
sudo chown -R lol:lol /opt/lol /var/lib/lol
```

然后从本机传：

```bash
# 后端 jar
scp /d/outputs/lol-server/target/lol-server-0.0.1-SNAPSHOT.jar root@你的IP:/opt/lol/lol-server.jar

# 前端 dist 里的所有文件
scp -r /d/outputs/lol-web/dist/* root@你的IP:/var/www/lol-web/

# 生产配置（改好密码和 jwt.secret 再传）
scp /d/outputs/deploy/application-prod.properties root@你的IP:/opt/lol/config/
scp /d/outputs/deploy/lol-server.service root@你的IP:/etc/systemd/system/
scp /d/outputs/deploy/nginx-lol.conf root@你的IP:/etc/nginx/conf.d/lol.conf
```

**桌面客户端安装包**（网页顶栏的「下载客户端」按钮要用）：

先在服务器上建目录：

```bash
sudo mkdir -p /var/www/lol-web/download
```

再从本机传（106MB，慢是正常的）：

```bash
scp "/d/outputs/lol-desktop/release/峡谷同行 Setup 1.0.1.exe" \
    root@你的IP:/var/www/lol-web/download/lol-desktop-setup.exe
```

> 服务器上固定叫 `lol-desktop-setup.exe`（纯 ASCII、不带版本号）是有意的：
> 页面里的链接写死成 `/download/lol-desktop-setup.exe`，以后发新版直接覆盖
> 同一个文件，**前端不用改、不用重新构建**。中文加空格的文件名在 scp 和 URL
> 里都要额外编码，没必要给自己找麻烦。

**重传 dist 时注意**：别用会删东西的同步方式。

```bash
# 可以：只覆盖，不删除（download/ 会留着）
scp -r /d/outputs/lol-web/dist/* root@你的IP:/var/www/lol-web/
# 也可以：rsync 加排除
rsync -av --exclude 'download/' /d/outputs/lol-web/dist/ root@你的IP:/var/www/lol-web/
# 别用：rsync --delete，它会把 download/ 整个删掉
```

**传之前先改**：`application-prod.properties` 里的数据库密码、`jwt.secret`
（`openssl rand -base64 48` 生成一个），以及 `nginx-lol.conf` 里的 `server_name`。

---

## 6. 启动后端

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now lol-server

# 看日志，第一次启动会建表，稍等 20~30 秒
sudo journalctl -u lol-server -f
```

确认表建出来了：

```bash
mysql -ulol -p -e "USE lol; SHOW TABLES;"
```

看到 `t_user` / `t_teamup` / `t_match_record` / `t_chat_message` 等表就说明成了。

---

## 7. 配置并重载 Nginx

```bash
sudo nginx -t                      # 语法检查，必须看到 successful
sudo systemctl reload nginx
```

**SELinux 文件上下文**（如果前面 `getenforce` 是 `Disabled`，整段跳过）：

标签不对的后果是 Nginx 读不到文件，页面直接 403。用 `semanage` + `restorecon` 这一对，
而不是 `chcon`：

```bash
# 前端静态文件：Nginx 只读
sudo semanage fcontext -a -t httpd_sys_content_t "/var/www/lol-web(/.*)?"
sudo restorecon -Rv /var/www/lol-web

# 上传目录：Nginx 要读，Java 还要往里写 —— 所以是 rw 类型
sudo semanage fcontext -a -t httpd_sys_rw_content_t "/var/lib/lol(/.*)?"
sudo restorecon -Rv /var/lib/lol
```

三个必须这么写的原因：

1. **`chcon` 不持久。** 它只改当前这批文件的标签，没写进 SELinux 策略库。
   一旦机器 relabel（重启触发的 `autorelabel`、运维手滑跑了一次 `restorecon`、
   从快照恢复），标签全部打回原形，页面又开始 403 —— 而且是过了一阵子才复现，
   很难联想到当初是这一步没做对。`semanage fcontext` 是往策略库里写规则，
   `restorecon` 是照着规则把标签刷上去，两者必须**先 semanage 再 restorecon**。

2. **上传目录不能用 `httpd_sys_content_t`。** 那个类型是给「Web 只读内容」的，
   后端 Java 进程往里写会被 AVC 拒绝 —— 表现出来就是换头像、发图片全部失败，
   但页面其它功能都正常。要 `httpd_sys_rw_content_t`。

3. **规则要盖到父目录。** 写 `"/var/lib/lol(/.*)?"` 而不是 `"/var/lib/lol/uploads(/.*)?"`：
   Nginx 要能 `search` 进 `/var/lib/lol` 这一层才读得到里面的文件，
   只给子目录设标签，父目录仍是 `var_lib_t`，照样进不去。
   前端那边同理写成 `"/var/www/lol-web(/.*)?"`。

确认生效：

```bash
ls -Z /var/www/lol-web/index.html          # 应含 httpd_sys_content_t
ls -Z /var/lib/lol/uploads                 # 应含 httpd_sys_rw_content_t
```

> **想省事的话有另一条路**：`sudo setenforce 0`，并把 `/etc/selinux/config` 里的
> `SELINUX=enforcing` 改成 `permissive`，重启。毕设演示环境这么干完全够用，
> 能一次性绕开上面全部坑。正式环境再按正规做法来。

---

## 8. 验证清单

浏览器打开 `http://你的IP/`，按顺序确认：

- [ ] 首页三栏正常（资料 / 开黑大厅 / 找队友）
- [ ] 注册 + 登录成功
- [ ] 发布一条组队，能在大厅看到
- [ ] 刷新 `/player/1` 这类子路由不 404（测 `try_files` 是否生效）
- [ ] 进入房间，发一条消息能收到（**测 WebSocket**，最容易漏）
- [ ] 传一张聊天图片能显示（测 `/uploads/` 与 `client_max_body_size`）
- [ ] 换头像成功
- [ ] 顶栏「下载客户端」能真的下到 exe（测 `/download/`）

命令行侧的快速检查：

```bash
curl -s http://127.0.0.1/api/online/list
# 未登录，应返回 {"code":401,...}。
# 注：本项目 HTTP 状态码恒为 200，业务码在 JSON body 里，
# 所以排查别看 HTTP 状态码，要看 code 字段。

ss -lntp | grep 8080    # 必须是 127.0.0.1:8080；显示 *:8080 说明在监听所有网卡，见下

# 客户端下载：看头不看 body，别真把 106MB 拉下来
curl -sI http://127.0.0.1/download/lol-desktop-setup.exe | head -8
# 期望：HTTP/1.1 200 + Content-Length ≈ 106000000 + Content-Type: application/octet-stream
# 出现 404 说明文件没放到位；出现 text/html 说明请求被 try_files 兜去 index.html 了，
# 检查 /download/ 那一段有没有真的生效
```

> `ss` 里出现 `*:8080` 表示后端在监听**所有网卡**。虽然安全组没开 8080 时外网仍旧连不进来，
> 但那是唯一的一层防线——安全组被误加一条规则后端就裸奔了。
> 在 `application-prod.properties` 里加 `server.address=127.0.0.1` 并重启即可，
> Nginx 从本机反代过去不受任何影响。

---

## 9. 出问题了怎么办

| 现象 | 原因 | 处理 |
|---|---|---|
| 页面能开，`/api` 全 502 | SELinux 拦了 Nginx 的外连 | `setsebool -P httpd_can_network_connect 1` |
| 502 且上面已做 | 后端没起来 | `journalctl -u lol-server -n 50` 看异常 |
| 聊天连不上，控制台报 400/403 | nginx 缺 Upgrade 头 | 检查 `/ws/` 那一段的 `proxy_http_version 1.1` 与两个 `proxy_set_header` |
| 刷新子路由 404 | 缺 `try_files` | 检查 `location /` 段 |
| 上传视频返回 413 | 请求体超限 | nginx `client_max_body_size` 与后端 25MB 对齐 |
| 后端起不来，连不上数据库 | MySQL 8 认证方式 | `ALTER USER 'lol'@'localhost' IDENTIFIED WITH mysql_native_password BY '密码';` |
| 首页打开就 403 | 静态文件 SELinux 标签不对 | `ls -Z /var/www/lol-web/index.html`，按第 7 节重设上下文 |
| 换头像 / 发图片失败，别的都正常 | 上传目录类型不是 rw，Nginx 或 Java 有一方被 AVC 拦了 | 确认是 `httpd_sys_rw_content_t`，且规则盖到了父目录 |
| 上面都对着还是写不进去 | 缺自定义策略 | `ausearch -m AVC -ts recent \| audit2allow -M lolupload` 然后 `semodule -i lolupload.pp` |
| 图片 404 | 上传目录与 alias 不一致 | 核对 `app.upload.dir` 与 nginx `alias`，两边必须是同一个绝对路径 |
| 发新版后页面还是旧的 | index.html 被缓存 | 确认 `location = /index.html` 的 no-cache 生效 |
| 点「下载客户端」下到的是个 HTML 文件 | 安装包不在 `download/` 里，请求被 `try_files` 兜到 index.html | `ls -l /var/www/lol-web/download/`；`/download/` 段的 `=404` 能立刻把这件事暴露成 404 |
| 下载中断 / 下到一半失败 | `send_timeout` 太短 | `/download/` 段里已设 300s，确认配置生效 |
| 客户端装上后打不开站点 | 内置地址不对 | 客户端内置的是 `http://47.110.138.40`；换服务器走菜单「文件 → 设置服务器地址…」 |

---

## 10. 可选：上 HTTPS

```bash
sudo dnf install -y certbot python3-certbot-nginx
sudo certbot --nginx -d 你的域名
```

证书会自动续期。前端 WebSocket 是跟着 `location.protocol` 走 `ws:`/`wss:` 的，
切到 HTTPS 后自动变成 `wss:`，不需要改代码。
