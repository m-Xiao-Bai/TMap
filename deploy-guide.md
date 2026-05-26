# TransitMap 服务器部署指南

> 适用于全新 Linux 服务器（以 Ubuntu 22.04 / CentOS 7+ 为例），部署后端 + 管理端 + 用户端网页。

---

## 一、服务器环境要求

| 组件 | 版本要求 | 说明 |
|------|---------|------|
| JDK | 17+ | Spring Boot 3.2.0 要求 |
| MySQL | 8.0+ | 数据库 |
| Redis | 6.0+ | 缓存 + Token 存储 |
| Nginx | 1.18+ | 反向代理 + 前端静态资源 |
| Maven | 3.8+ | 本地打包用（可选，服务器上装也行） |

---

## 二、安装基础环境

### 2.1 JDK 17

```bash
# Ubuntu
sudo apt update
sudo apt install openjdk-17-jdk -y

# CentOS
sudo yum install java-17-openjdk java-17-openjdk-devel -y

# 验证
java -version
```

### 2.2 MySQL 8.0

```bash
# Ubuntu
sudo apt install mysql-server -y
sudo systemctl start mysql
sudo systemctl enable mysql

# CentOS
sudo yum install mysql-server -y
sudo systemctl start mysqld
sudo systemctl enable mysqld

# 获取初始密码（CentOS）
sudo grep 'temporary password' /var/log/mysqld.log
```

安全初始化并设置密码：

```bash
sudo mysql_secure_installation
```

登录 MySQL 创建数据库和用户：

```sql
mysql -u root -p

-- 创建数据库
CREATE DATABASE transit_map DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

-- 创建专用用户（替换 your_password 为你的密码）
CREATE USER 'tmap'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON transit_map.* TO 'tmap'@'localhost';
FLUSH PRIVILEGES;
```

**导入数据库表结构和数据：** 你需要从本地开发环境导出 SQL，然后在服务器上导入：

```bash
# 在本地导出（在你的 Windows 上执行）
mysqldump -u tmap -p transit_map > transit_map.sql

# 上传到服务器后导入
mysql -u tmap -p transit_map < transit_map.sql
```

### 2.3 Redis

```bash
# Ubuntu
sudo apt install redis-server -y
sudo systemctl start redis
sudo systemctl enable redis

# CentOS
sudo yum install redis -y
sudo systemctl start redis
sudo systemctl enable redis
```

设置 Redis 密码：

```bash
sudo vim /etc/redis/redis.conf
# 找到 # requirepass foobared，取消注释并改为：
requirepass your_redis_password

sudo systemctl restart redis
```

验证连接：

```bash
redis-cli -a your_redis_password ping
# 应返回 PONG
```

### 2.4 Nginx

```bash
# Ubuntu
sudo apt install nginx -y
sudo systemctl start nginx
sudo systemctl enable nginx

# CentOS
sudo yum install nginx -y
sudo systemctl start nginx
sudo systemctl enable nginx
```

---

## 三、部署后端

### 3.1 本地打包（在你的 Windows 上）

在项目根目录执行：

```bash
cd TransitMap-java
mvn clean package -DskipTests
```

打包完成后会生成两个 JAR：
- `TransitMap-admin-server/target/TransitMap-admin-server-0.0.1-SNAPSHOT.jar`
- `TransitMap-user-server/target/TransitMap-user-server-0.0.1-SNAPSHOT.jar`

### 3.2 上传到服务器

在服务器上创建目录结构：

```bash
sudo mkdir -p /opt/transitmap
sudo mkdir -p /opt/transitmap/admin
sudo mkdir -p /opt/transitmap/user
sudo mkdir -p /opt/transitmap/data
sudo mkdir -p /opt/transitmap/logs
```

上传 JAR 文件：

```bash
# 在本地执行（替换 SERVER_IP 为你的服务器 IP）
scp TransitMap-java/TransitMap-admin-server/target/TransitMap-admin-server-0.0.1-SNAPSHOT.jar root@SERVER_IP:/opt/transitmap/admin/
scp TransitMap-java/TransitMap-user-server/target/TransitMap-user-server-0.0.1-SNAPSHOT.jar root@SERVER_IP:/opt/transitmap/user/
```

### 3.3 配置环境变量

在服务器上创建环境变量文件：

```bash
sudo vim /opt/transitmap/env
```

写入以下内容（**替换为你自己的实际值**）：

```bash
# 数据库
DB_HOST=localhost
DB_PORT=3306
DB_NAME=transit_map
DB_USERNAME=tmap
DB_PASSWORD=your_mysql_password

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=your_redis_password

# 邮箱（QQ 邮箱授权码）
MAIL_HOST=smtp.qq.com
MAIL_PORT=587
MAIL_USERNAME=your_email@qq.com
MAIL_PASSWORD=your_email_authorization_code

# JWT（至少 32 位字符串，随便生成一个复杂的）
JWT_SECRET=your_jwt_secret_at_least_32_chars_long_random_string

# 高德地图 API Key
GAODE_API_KEY=your_gaode_api_key

# 数据存储目录
DATA_DIR=/opt/transitmap/data
```

### 3.4 创建 systemd 服务

**Admin Server（管理端后端，端口 8889）：**

```bash
sudo vim /etc/systemd/system/transitmap-admin.service
```

```ini
[Unit]
Description=TransitMap Admin Server
After=mysql.service redis.service
Requires=mysql.service redis.service

[Service]
Type=simple
User=root
WorkingDirectory=/opt/transitmap/admin
EnvironmentFile=/opt/transitmap/env
ExecStart=/usr/bin/java -jar /opt/transitmap/admin/TransitMap-admin-server-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

**User Server（用户端后端，端口 8888）：**

```bash
sudo vim /etc/systemd/system/transitmap-user.service
```

```ini
[Unit]
Description=TransitMap User Server
After=mysql.service redis.service
Requires=mysql.service redis.service

[Service]
Type=simple
User=root
WorkingDirectory=/opt/transitmap/user
EnvironmentFile=/opt/transitmap/env
ExecStart=/usr/bin/java -jar /opt/transitmap/user/TransitMap-user-server-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

启动服务：

```bash
sudo systemctl daemon-reload
sudo systemctl enable transitmap-admin transitmap-user
sudo systemctl start transitmap-admin transitmap-user
```

查看运行状态：

```bash
sudo systemctl status transitmap-admin
sudo systemctl status transitmap-user

# 查看日志
sudo journalctl -u transitmap-admin -f
sudo journalctl -u transitmap-user -f
```

---

## 四、部署前端

### 4.1 本地打包（在你的 Windows 上）

**管理端：**

```bash
cd TransitMap-vue-admin
npm install
npm run build
```

生成的文件在 `TransitMap-vue-admin/dist/` 目录。

**用户端：**

```bash
cd TransitMap-vue-user
npm install
npm run build
```

生成的文件在 `TransitMap-vue-user/dist/` 目录。

### 4.2 上传到服务器

在服务器上创建前端目录：

```bash
sudo mkdir -p /opt/transitmap/www/admin
sudo mkdir -p /opt/transitmap/www/user
```

上传打包产物：

```bash
# 管理端
scp -r TransitMap-vue-admin/dist/* root@SERVER_IP:/opt/transitmap/www/admin/

# 用户端
scp -r TransitMap-vue-user/dist/* root@SERVER_IP:/opt/transitmap/www/user/
```

### 4.3 配置 Nginx

```bash
sudo vim /etc/nginx/conf.d/transitmap.conf
```

写入以下配置：

```nginx
# 管理端
server {
    listen       80;
    server_name  admin.yourdomain.com;  # 替换为你的域名或 IP

    location / {
        root   /opt/transitmap/www/admin;
        index  index.html;
        try_files $uri $uri/ /index.html;   # Vue Router history 模式
    }

    # 代理管理端 API -> admin-server (8889)
    location /transitMap-admin/ {
        proxy_pass http://127.0.0.1:8889/transitMap-admin/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # 代理 /data 路径 -> admin-server (8889)
    location /data/ {
        proxy_pass http://127.0.0.1:8889/data/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}

# 用户端
server {
    listen       80;
    server_name  user.yourdomain.com;   # 替换为你的域名或 IP

    location / {
        root   /opt/transitmap/www/user;
        index  index.html;
        try_files $uri $uri/ /index.html;
    }

    # 代理用户端 API -> user-server (8888)
    location /transitMap/ {
        proxy_pass http://127.0.0.1:8888/transitMap/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # WebSocket 代理（AI 聊天功能需要）
    location /transitMap/ws/ {
        proxy_pass http://127.0.0.1:8888/transitMap/ws/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_read_timeout 3600s;
    }
}
```

> **如果没有域名**，把两个 server 合并到一个 `server` 块里，用不同端口或路径区分：
> - 管理端：`http://SERVER_IP:8080/` 代理到 admin 前端
> - 用户端：`http://SERVER_IP:8081/` 代理到 user 前端

测试并重载 Nginx：

```bash
sudo nginx -t
sudo systemctl reload nginx
```

---

## 五、开放防火墙端口

```bash
# Ubuntu (ufw)
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable

# CentOS (firewalld)
sudo firewall-cmd --permanent --add-service=http
sudo firewall-cmd --permanent --add-service=https
sudo firewall-cmd --reload

# 如果是云服务器（阿里云/腾讯云等），还需要在安全组中放行 80 和 443 端口
```

---

## 六、验证部署

打开浏览器访问：

| 地址 | 说明 |
|------|------|
| `http://SERVER_IP` | 用户端首页（取决于 Nginx server_name 配置） |
| `http://admin.yourdomain.com` | 管理端登录页 |

如果用 IP 直接访问且只配了一个 server 块，访问 `http://SERVER_IP` 即可。

### 常见检查命令

```bash
# 检查端口监听
ss -tlnp | grep -E '8888|8889|80'

# 检查后端是否启动
curl http://localhost:8888/transitMap/actuator/health
curl http://localhost:8889/transitMap-admin/actuator/health

# 查看后端日志
sudo journalctl -u transitmap-admin -n 50 --no-pager
sudo journalctl -u transitmap-user -n 50 --no-pager

# 查看 Nginx 错误日志
sudo tail -f /var/log/nginx/error.log
```

---

## 七、目录结构总览（服务器上）

```
/opt/transitmap/
├── admin/
│   └── TransitMap-admin-server-0.0.1-SNAPSHOT.jar
├── user/
│   └── TransitMap-user-server-0.0.1-SNAPSHOT.jar
├── data/                  # 上传文件、头像等数据存储
├── logs/                  # 日志（如果应用配置了文件输出）
├── env                    # 环境变量配置
└── www/
    ├── admin/             # 管理端前端 dist 文件
    │   ├── index.html
    │   └── assets/
    └── user/              # 用户端前端 dist 文件
        ├── index.html
        └── assets/
```

---

## 八、更新部署流程

后续更新只需重复以下步骤：

```bash
# 1. 本地重新打包
cd TransitMap-java && mvn clean package -DskipTests
cd TransitMap-vue-admin && npm run build
cd TransitMap-vue-user && npm run build

# 2. 上传并替换服务器文件
scp TransitMap-java/TransitMap-admin-server/target/*.jar root@SERVER_IP:/opt/transitmap/admin/
scp TransitMap-java/TransitMap-user-server/target/*.jar root@SERVER_IP:/opt/transitmap/user/
scp -r TransitMap-vue-admin/dist/* root@SERVER_IP:/opt/transitmap/www/admin/
scp -r TransitMap-vue-user/dist/* root@SERVER_IP:/opt/transitmap/www/user/

# 3. 重启后端服务
ssh root@SERVER_IP "systemctl restart transitmap-admin transitmap-user"
```

前端是纯静态文件，上传后 Nginx 直接生效，无需重启。

---

## 九、注意事项

1. **数据库必须先导出再导入** — 项目 `sql/` 目录为空，需要从你本地开发环境导出 `transit_map` 数据库的完整 SQL
2. **环境变量文件安全** — `/opt/transitmap/env` 包含数据库密码等敏感信息，不要放到 git 仓库里
3. **JWT_SECRET** — 生产环境务必使用一个随机的、至少 32 位的字符串，不要用开发时的默认值
4. **高德地图 API Key** — 确保你的 Key 在高德开放平台设置了服务器 IP 白名单
5. **Spring profiles** — 当前 `application.yml` 默认激活 `local`，需要确保服务器上有 `application-prod.yml` 或通过环境变量覆盖所有配置（当前的 `application.yml` 已通过 `${ENV_VAR}` 方式支持）
