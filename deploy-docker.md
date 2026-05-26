# TransitMap Docker 部署教程（新手完整版）

> 本文档面向第一次部署的新手，每一步都会解释"是什么"和"为什么"。
> 目标：把后端（Java）和前端网页（Vue）部署到一台全新的 Linux 服务器上。

---

## 你需要准备什么

| 准备项 | 说明 |
|--------|------|
| 一台 Linux 服务器 | 阿里云/腾讯云/华为云均可，推荐 Ubuntu 22.04，最低配置 2 核 4G 内存 |
| 服务器的公网 IP | 购买后云平台会给你，例如 `47.96.xxx.xxx` |
| 服务器的 root 密码 或 SSH 密钥 | 用来远程登录服务器操作 |
| 你本地电脑上能正常运行的项目 | MySQL 数据库里有数据，前后端 `npm run dev` 能跑通 |

---

## 整体流程概览

```
第 1 步：本地导出数据库         （在你的 Windows 电脑上操作）
第 2 步：本地打包前端           （在你的 Windows 电脑上操作）
第 3 步：修改管理端前端配置      （在你的 Windows 电脑上操作）
第 4 步：把项目上传到服务器      （在你的 Windows 电脑上操作）
第 5 步：在服务器上安装 Docker   （在服务器上操作）
第 6 步：在服务器上启动所有服务   （在服务器上操作）
第 7 步：验证是否部署成功        （在浏览器里操作）
```

> **什么是 Docker？**
> Docker 可以理解为"轻量级虚拟机"。它把 MySQL、Redis、Java 后端、Nginx 这些软件各自打包成一个独立的"容器"，互不干扰，一键启动。你不需要在服务器上手动安装 MySQL 和 Redis，Docker 会自动帮你搞定。

---

## 第 1 步：在本地导出数据库

你的数据库 `transit_map` 里有表结构和数据，需要导出成一个 `.sql` 文件，然后在服务器上导入。

打开 **cmd**（命令提示符），执行：

```bash
cd D:\Yu_Yan\DaiMa\TMap-mina
```

然后执行导出命令：

```bash
mysqldump -u tmap -p --default-character-set=utf8mb4 --hex-blob --routines --triggers --result-file=D:\Yu_Yan\DaiMa\TMap-mina\docker\mysql\init.sql transit_map
```

执行后会提示你输入密码，输入你的 MySQL 密码（就是 `tmap`），回车。

**验证是否成功：** 用记事本打开 `docker\mysql\init.sql`，如果能看到 `CREATE TABLE`、`INSERT INTO` 这样的 SQL 语句，而且中文不是乱码，就说明成功了。

> **为什么要导出？** 服务器是全新的，上面没有你的数据库。这个 `.sql` 文件就是你数据库的"备份"，Docker 启动 MySQL 时会自动执行它来还原你的数据库。

---

## 第 2 步：在本地打包前端

前端代码（Vue 项目）需要打包成纯 HTML/CSS/JS 文件，服务器上不需要安装 Node.js。

打开一个 **新的 cmd 窗口**，依次执行：

**打包管理端：**
```bash
cd D:\Yu_Yan\DaiMa\TMap-mina\TransitMap-vue-admin
npm install
npm run build
```

成功后会在 `TransitMap-vue-admin` 目录下生成一个 `dist` 文件夹，里面就是打包好的文件。

**打包用户端：**
```bash
cd D:\Yu_Yan\DaiMa\TMap-mina\TransitMap-vue-user
npm install
npm run build
```

同样会生成 `dist` 文件夹。

> **为什么要打包？** 你平时开发用的 `npm run dev` 是开发模式，启动了一个本地服务器。但服务器上不需要这个，只需要打包后的静态文件，由 Nginx（一个 Web 服务器软件）来提供访问。

---

## 第 3 步：修改管理端前端配置

管理端部署后访问路径是 `/admin/`（比如 `http://你的IP/admin/`），需要告诉前端这个路径前缀。

**修改 1：** 打开 `TransitMap-vue-admin\vite.config.js`，添加 `base: '/admin/'`：

```js
export default defineConfig({
  base: '/admin/',   // ← 添加这一行
  plugins: [
    // ... 后面不变
```

**修改 2：** 打开 `TransitMap-vue-admin\src\router\index.js`，找到 `createWebHistory()`，改成：

```js
history: createWebHistory('/admin/'),   // ← 括号里加上 '/admin/'
```

**修改完后重新打包管理端：**
```bash
cd D:\Yu_Yan\DaiMa\TMap-mina\TransitMap-vue-admin
npm run build
```

> **为什么要改？** 用户端部署在根路径 `/`，管理端部署在 `/admin/` 子路径。如果不告诉 Vue Router 这个路径前缀，管理端刷新页面时会 404。

---

## 第 4 步：把项目上传到服务器

你需要把整个项目文件夹传到服务器上。有两种方式：

### 方式 A：用 scp 命令传输（推荐新手用这个）

打开 cmd，执行：

```bash
scp -r D:\Yu_Yan\DaiMa\TMap-mina root@8.166.118.132:/opt/TMap-mina
```

- `scp` 是安全复制命令，`-r` 表示复制整个文件夹
- 它会提示你输入服务器 root 密码
- 传输时间取决于你的网速和项目大小（含 node_modules 会很大，建议先看方式 B）

### 方式 B：只传必要文件（推荐，传输量小）

先排除不需要的文件，只传关键内容。在 cmd 中执行：

```bash
# 先在服务器上创建目录
ssh root@你的服务器IP "mkdir -p /opt/TMap-mina"

# 传 docker 配置目录
scp -r D:\Yu_Yan\DaiMa\TMap-mina\docker root@你的服务器IP:/opt/TMap-mina/

# 传后端代码（排除 target 和 data 目录）
scp -r D:\Yu_Yan\DaiMa\TMap-mina\TransitMap-java root@你的服务器IP:/opt/TMap-mina/

# 传前端打包产物（只需要 dist 文件夹）
scp -r D:\Yu_Yan\DaiMa\TMap-mina\TransitMap-vue-admin\dist root@你的服务器IP:/opt/TMap-mina/TransitMap-vue-admin/
scp -r D:\Yu_Yan\DaiMa\TMap-mina\TransitMap-vue-user\dist root@你的服务器IP:/opt/TMap-mina/TransitMap-vue-user/
```

### 方式 C：用 SFTP 工具（图形界面，最直观）

下载 [FileZilla](https://filezilla-project.org/) 或用 [WinSCP](https://winscp.net/)：
1. 打开软件，输入服务器 IP、root 用户名、密码，端口填 22
2. 连接后，左边是你的本地文件，右边是服务器文件
3. 把 `TMap-mina` 文件夹拖到右边的 `/opt/` 目录下

> **注意：** `node_modules` 文件夹不需要上传，它非常大且服务器上用不到（Docker 构建时会自己下载依赖）。

---

## 第 5 步：在服务器上安装 Docker

你需要通过 SSH 登录到服务器。打开 cmd 执行：

```bash
ssh root@你的服务器IP
```

输入密码后就进入了服务器的命令行。然后依次执行以下命令：

### 5.1 更新系统软件包

```bash
apt update
```

> 这会更新软件包列表，确保能下载到最新版本的软件。

### 5.2 安装 Docker

```bash
apt install -y docker.io docker-compose-plugin
```

> `docker.io` 是 Docker 引擎，`docker-compose-plugin` 是 Docker Compose（用来同时管理多个容器的工具）。

### 5.3 启动 Docker 并设置开机自启

```bash
systemctl start docker
systemctl enable docker
```

> `systemctl start docker` 是启动 Docker 服务。
> `systemctl enable docker` 是设置开机自动启动，这样服务器重启后 Docker 会自动运行。

### 5.4 验证安装成功

```bash
docker --version
docker compose version
```

如果两个命令都输出了版本号（例如 `Docker version 24.x.x`），说明安装成功。

---

## 第 6 步：编辑环境变量文件

在服务器上编辑 `docker/.env` 文件，填入你的真实配置：

```bash
cd /opt/Tmap/docker
nano .env
```

> `nano` 是服务器上的文本编辑器。打开后你可以直接编辑内容。

把文件里的占位符替换成你的真实值：

```bash
# ========== 数据库 ==========
MYSQL_ROOT_PASSWORD=你给MySQL设置的root密码（随便起一个复杂的）
DB_USERNAME=tmap
DB_PASSWORD=你给MySQL设置的用户密码（随便起一个复杂的）

# ========== Redis ==========
REDIS_PASSWORD=你给Redis设置的密码（随便起一个复杂的）

# ========== JWT（用于用户登录验证，需要一个长字符串） ==========
JWT_SECRET=随便打一串超过32位的字母数字混合字符串比如TransitMapJwtSecret2026AbCdEfGh

# ========== 高德地图（你项目里用到的 API Key） ==========
GAODE_API_KEY=你的高德APIKey

# ========== 邮箱（用于发送验证码，用你的 QQ 邮箱） ==========
MAIL_USERNAME=你的QQ邮箱@qq.com
MAIL_PASSWORD=你的QQ邮箱授权码（不是QQ密码，是在QQ邮箱设置里生成的）

# ========== 微信小程序（不用就留空） ==========
WECHAT_APPID=
WECHAT_SECRET=
```

编辑完后，按 `Ctrl + O` 保存，按 `Enter` 确认，然后按 `Ctrl + X` 退出。

> **什么是 .env 文件？** 它是一个存放密码和配置的文件。Docker Compose 启动时会自动读取它，把里面的值传给各个容器。这样密码就不会写死在代码里。

---

## 第 7 步：启动所有服务

```bash
cd /opt/Tmap/docker
docker compose up -d --build
```

**这条命令做了什么？**
- `cd /opt/TMap-mina/docker` — 进入 docker 配置目录
- `docker compose up` — 根据 `docker-compose.yml` 文件启动所有服务
- `-d` — 后台运行（不占用你的终端）
- `--build` — 第一次运行需要加这个，它会编译你的 Java 后端代码

**这个过程会花较长时间（首次约 5-15 分钟）**，因为需要：
1. 下载 MySQL、Redis、Nginx 的 Docker 镜像
2. 下载 Maven 依赖并编译 Java 后端代码
3. 等待 MySQL 和 Redis 健康检查通过

### 查看构建进度

如果想看实时进度，打开一个新的 SSH 窗口，执行：

```bash
cd /opt/Tmap/docker
docker compose logs -f
```

按 `Ctrl + C` 可以退出日志查看（不会停止服务）。

### 查看所有容器状态

```bash
docker compose ps
```

正常情况下你会看到 5 个容器，状态都是 `running`：

```
NAME              STATUS
tmap-mysql        running (healthy)
tmap-redis        running (healthy)
tmap-admin-server running
tmap-user-server  running
tmap-nginx        running
```

如果某个容器状态不是 `running`，查看它的日志排查问题：

```bash
docker compose logs admin-server    # 查看管理端后端日志
docker compose logs user-server     # 查看用户端后端日志
docker compose logs nginx           # 查看 Nginx 日志
docker compose logs mysql           # 查看 MySQL 日志
```

---

## 第 8 步：验证部署是否成功

### 8.1 在服务器上测试

```bash
# 测试用户端后端是否响应
curl http://localhost:8888/transitMap/

# 测试管理端后端是否响应
curl http://localhost:8889/transitMap-admin/

# 测试 Nginx 是否正常
curl http://localhost/
```

如果返回了 HTML 或 JSON 内容（而不是"连接被拒绝"），说明服务正常。

### 8.2 在浏览器里测试

打开浏览器，访问：

| 地址 | 说明 |
|------|------|
| `http://你的服务器IP` | 用户端首页 |
| `http://你的服务器IP/admin/` | 管理端登录页 |

如果能看到页面，说明部署成功！

### 8.3 如果浏览器打不开

**检查防火墙：** 服务器可能阻止了 80 端口的外部访问。

```bash
# Ubuntu 系统
ufw allow 80/tcp
ufw allow 443/tcp
```

**如果是云服务器（阿里云/腾讯云/华为云）：** 还需要去云平台控制台 → 安全组 → 添加规则，放行 80 端口（TCP）。

---

## 附录 A：常用运维命令速查

```bash
# ===== 服务管理 =====
cd /opt/Tmap/docker

docker compose up -d                    # 启动所有服务（后台运行）
docker compose down                     # 停止并删除所有容器
docker compose restart                  # 重启所有服务
docker compose restart admin-server     # 只重启管理端后端
docker compose restart user-server      # 只重启用户端后端
docker compose restart nginx            # 只重启 Nginx

# ===== 查看日志 =====
docker compose logs -f                  # 实时查看所有服务日志
docker compose logs -f admin-server     # 实时查看管理端后端日志
docker compose logs -f user-server      # 实时查看用户端后端日志
docker compose logs --tail=50 nginx     # 查看 Nginx 最近 50 行日志

# ===== 进入容器内部 =====
docker exec -it tmap-mysql bash                     # 进入 MySQL 容器
docker exec -it tmap-mysql mysql -u tmap -p         # 直接进入 MySQL 命令行
docker exec -it tmap-redis redis-cli -a 你的Redis密码  # 进入 Redis 命令行

# ===== 查看资源占用 =====
docker stats                            # 实时查看各容器 CPU/内存占用

# ===== 数据库备份 =====
docker exec tmap-mysql mysqldump -u tmap -p'你的密码' transit_map > /opt/backup_$(date +%Y%m%d).sql
```

---

## 附录 B：后续更新部署

当你修改了代码，需要重新部署时：

### 只改了前端代码

```bash
# 1. 在本地重新打包
cd D:\Yu_Yan\DaiMa\TMap-mina\TransitMap-vue-admin
npm run build
cd D:\Yu_Yan\DaiMa\TMap-mina\TransitMap-vue-user
npm run build

# 2. 上传新的 dist 到服务器（用 scp 或 FileZilla）
scp -r D:\Yu_Yan\DaiMa\TMap-mina\TransitMap-vue-admin\dist root@你的IP:/opt/TMap-mina/TransitMap-vue-admin/
scp -r D:\Yu_Yan\DaiMa\TMap-mina\TransitMap-vue-user\dist root@你的IP:/opt/TMap-mina/TransitMap-vue-user/

# 3. 在服务器上重启 Nginx
ssh root@你的IP "cd /opt/TMap-mina/docker && docker compose restart nginx"
```

### 只改了后端代码

```bash
# 1. 上传修改过的 Java 源代码到服务器（用 scp 或 FileZilla）

# 2. 在服务器上重新构建并重启后端
ssh root@你的IP "cd /opt/TMap-mina/docker && docker compose up -d --build admin-server user-server"
```

### 前端和后端都改了

```bash
# 1. 本地打包前端 + 上传 dist + 上传 Java 代码
# 2. 在服务器上重建后端 + 重启 Nginx
ssh root@你的IP "cd /opt/TMap-mina/docker && docker compose up -d --build && docker compose restart nginx"
```

---

## 附录 C：Docker 文件说明

项目中与 Docker 相关的文件：

```
TMap-mina/
├── docker/
│   ├── docker-compose.yml    ← 核心文件：定义了 5 个服务（MySQL、Redis、admin后端、user后端、Nginx）
│   │                            以及它们之间的关系、端口映射、数据卷等
│   ├── .env                  ← 密码和配置：存放所有敏感信息，不要提交到 git
│   ├── Dockerfile.backend    ← 后端构建模板：告诉 Docker 如何编译和运行你的 Java 项目
│   ├── nginx/
│   │   └── nginx.conf        ← Nginx 配置：定义了前端页面怎么访问、API 请求怎么转发到后端
│   └── mysql/
│       └── init.sql          ← 数据库初始化：你从本地导出的 SQL，首次启动 MySQL 时自动执行
```

**docker-compose.yml 中的 5 个服务：**

| 服务 | 作用 | 端口 |
|------|------|------|
| `mysql` | 数据库，存储所有业务数据 | 3306 |
| `redis` | 缓存，存储登录 Token 等 | 6379 |
| `admin-server` | 管理端后端 API | 8889 |
| `user-server` | 用户端后端 API | 8888 |
| `nginx` | Web 服务器，提供前端页面访问 + 把 API 请求转发给后端 | 80 |

**请求流向：**

```
用户浏览器 → Nginx(:80)
                ├── 访问 /           → 返回用户端 HTML
                ├── 访问 /admin/     → 返回管理端 HTML
                ├── /transitMap/*    → 转发给 user-server(:8888)
                └── /transitMap-admin/* → 转发给 admin-server(:8889)
```

---

## 附录 D：常见问题

### Q: `docker compose up` 报错 "port is already allocated"

说明服务器上已经有程序占用了 3306/6379/80 等端口。

```bash
# 查看哪个程序占用了端口
ss -tlnp | grep -E '3306|6379|80|8888|8889'

# 如果是服务器上已有的 MySQL/Redis，停掉它
systemctl stop mysql
systemctl stop redis
```

### Q: 后端容器启动失败，日志报 "Connection refused"

说明后端连不上 MySQL 或 Redis。检查：

```bash
# 确认 MySQL 和 Redis 容器是否在运行
docker compose ps

# 确认 .env 里的密码是否正确
cat /opt/TMap-mina/docker/.env
```

### Q: Nginx 返回 502 Bad Gateway

说明 Nginx 连不上后端。检查后端容器是否启动成功：

```bash
docker compose logs admin-server
docker compose logs user-server
```

### Q: 页面能打开但 API 请求报错

打开浏览器 F12 → Network 面板，看请求的 URL 是否正确。请求应该是：
- 用户端：`http://你的IP/transitMap/...`
- 管理端：`http://你的IP/transitMap-admin/...`

如果请求的 URL 是 `http://localhost:...`，说明前端打包时没有正确配置。

### Q: 中文显示为乱码

确认 MySQL 容器使用了 utf8mb4 编码（docker-compose.yml 中已配置 `--character-set-server=utf8mb4`）。如果导入 init.sql 时已经有乱码，需要重新导出（第 1 步）。
