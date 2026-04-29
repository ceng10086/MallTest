# Ubuntu 24.04 部署 mall 项目完整指南

本指南记录在全新安装的 Ubuntu 24.04 上，从零部署 [mall](https://github.com/macrozheng/mall) 全套服务的完整过程。

---

## 项目架构概览

```
┌────────────────────────────────────────────────────────────┐
│  mall-admin-web (Vue3)  :5173                              │
│  ├── mall-admin  (Spring Boot) :8080   ← 后台管理 API       │
│  ├── mall-search (Spring Boot) :8081   ← 商品搜索服务       │
│  ├── mall-portal (Spring Boot) :8085   ← 前台商城 API       │
│  └── mall-demo   (Spring Boot) :8082   ← 演示/测试模块      │
├────────────────────────────────────────────────────────────┤
│  中间件 (Docker)                                           │
│  ├── MySQL 5.7          :3306                              │
│  ├── Redis 7            :6379                              │
│  ├── Elasticsearch 7.17 :9200/9300                         │
│  ├── MongoDB 4          :27017                             │
│  ├── RabbitMQ 3.9       :5672/15672                        │
│  ├── MinIO              :9090/9001                         │
│  ├── Nginx 1.22         :80                                │
│  ├── Logstash 7.17      :4560-4563                         │
│  └── Kibana 7.17        :5601                              │
└────────────────────────────────────────────────────────────┘
```

**技术栈：**
- 后端：Java 8 + Spring Boot 2.7.5 + MyBatis + JWT
- 前端：Vue 3 + TypeScript + Vite + Element Plus
- 构建：Maven 3.8+ / npm

---

## 第一步：系统基础准备

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y curl wget git zip unzip ufw net-tools
```

### 安装 Docker

```bash
# 移除旧版本（如有）
for pkg in docker.io docker-compose docker-compose-v2 docker-doc podman-docker containerd runc; do
  sudo apt remove -y $pkg 2>/dev/null
done

# 添加 Docker 官方 GPG key
sudo apt update
sudo apt install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

# 添加 Docker APT 源
sudo tee /etc/apt/sources.list.d/docker.sources <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF

sudo apt update

# 安装 Docker 及相关组件
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# 免 sudo 使用 Docker
sudo groupadd docker 2>/dev/null
sudo usermod -aG docker $USER

# 重要：重新登录（或重启）使 docker 组生效
# 当前会话可用 newgrp docker 临时激活
newgrp docker
```

验证：
```bash
docker --version        # Docker version 29.x
docker compose version  # Docker Compose version v5.x
```

---

## 第二步：安装 Java（SDKMAN 管理多版本）

项目 `pom.xml` 指定 `java.version=1.8`，使用 SDKMAN 方便未来切换到 JDK 17。

```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 8.0.442-tem
```

验证：
```bash
java -version
# openjdk version "1.8.0_442"
```

---

## 第三步：安装 Maven

```bash
sudo apt install -y maven
```

验证：
```bash
mvn -version
# Apache Maven 3.8.7
```

---

## 第四步：安装 Node.js（NVM 管理多版本）

`mall-admin-web` 的 `package.json` 要求 Node.js `^20.19.0 || >=22.12.0`。

```bash
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.3/install.sh | bash
source ~/.bashrc
nvm install 22
nvm use 22
```

验证：
```bash
node -v
# v22.x.x
```

---

## 第五步：克隆项目

```bash
cd ~
git clone https://github.com/ceng10086/MallTest.git
cd MallTest
```

目录结构：
```
MallTest/
├── mall/              # Spring Boot 后端（多模块）
└── mall-admin-web/    # Vue3 前端
```

---

## 第六步：创建中间件所需的宿主机目录

Docker 编排文件将数据持久化到宿主机 `/mydata/` 下，需提前创建目录结构。

```bash
sudo mkdir -p /mydata/mysql/data /mydata/mysql/conf/conf.d /mydata/mysql/conf/mysql.conf.d /mydata/mysql/log
sudo mkdir -p /mydata/redis/data
sudo mkdir -p /mydata/nginx/conf /mydata/nginx/html /mydata/nginx/logs
sudo mkdir -p /mydata/rabbitmq/data
sudo mkdir -p /mydata/elasticsearch/plugins /mydata/elasticsearch/data
sudo mkdir -p /mydata/mongo/db
sudo mkdir -p /mydata/minio/data
sudo mkdir -p /mydata/logstash
sudo chown -R $USER:$USER /mydata
```

> **说明：** MySQL 5.7 容器要求 `/etc/mysql/conf.d/` 和 `/etc/mysql/mysql.conf.d/` 目录存在，须在挂载的配置目录下预创建这两个子目录。

---

## 第七步：创建 Nginx 和 Logstash 配置文件

### Nginx 最小配置

```bash
cat > /mydata/nginx/conf/nginx.conf <<'EOF'
user  nginx;
worker_processes  auto;

error_log  /var/log/nginx/error.log notice;
pid        /var/run/nginx.pid;

events {
    worker_connections  1024;
}

http {
    include       /etc/nginx/mime.types;
    default_type  application/octet-stream;

    log_format  main  '$remote_addr - $remote_user [$time_local] "$request" '
                      '$status $body_bytes_sent "$http_referer" '
                      '"$http_user_agent" "$http_x_forwarded_for"';

    access_log  /var/log/nginx/access.log  main;

    sendfile        on;
    keepalive_timeout  65;

    server {
        listen       80;
        server_name  localhost;

        location / {
            root   /usr/share/nginx/html;
            index  index.html index.htm;
        }

        error_page   500 502 503 504  /50x.html;
        location = /50x.html {
            root   /usr/share/nginx/html;
        }
    }
}
EOF
```

### 从 Nginx 镜像提取 mime.types 和默认配置

挂载 `/mydata/nginx/conf` 到 `/etc/nginx` 会覆盖容器内的默认文件，需要把 `mime.types` 提前放入宿主机：

```bash
docker create --name nginx-temp nginx:1.22
docker cp nginx-temp:/etc/nginx/mime.types /mydata/nginx/conf/mime.types
docker cp nginx-temp:/etc/nginx/conf.d/default.conf /mydata/nginx/conf/conf.d/default.conf 2>/dev/null
docker rm nginx-temp
```

### Logstash 配置

```bash
cat > /mydata/logstash/logstash.conf <<'EOF'
input {
  tcp {
    mode => "server"
    host => "0.0.0.0"
    port => 4560
    codec => json_lines
    type => "debug"
  }
  tcp {
    mode => "server"
    host => "0.0.0.0"
    port => 4561
    codec => json_lines
    type => "error"
  }
  tcp {
    mode => "server"
    host => "0.0.0.0"
    port => 4562
    codec => json_lines
    type => "business"
  }
  tcp {
    mode => "server"
    host => "0.0.0.0"
    port => 4563
    codec => json_lines
    type => "record"
  }
}
filter{
  if [type] == "record" {
    mutate {
      remove_field => "port"
      remove_field => "host"
      remove_field => "@version"
    }
    json {
      source => "message"
      remove_field => ["message"]
    }
  }
}
output {
  elasticsearch {
    hosts => "es:9200"
    index => "mall-%{type}-%{+YYYY.MM.dd}"
  }
}
EOF
```

---

## 第八步：启动中间件（Docker Compose）

```bash
cd ~/MallTest/mall/document/docker
docker compose -f docker-compose-env.yml up -d
```

等全部 9 个容器启动后验证：
```bash
docker compose -f docker-compose-env.yml ps
```

期望输出 9 个容器全部 `Up`：

| 容器 | 端口 |
|------|------|
| mysql | 3306 |
| redis | 6379 |
| nginx | 80 |
| rabbitmq | 5672, 15672 |
| elasticsearch | 9200, 9300 |
| logstash | 4560-4563 |
| kibana | 5601 |
| mongo | 27017 |
| minio | 9090, 9001 |

---

## 第九步：导入数据库

```bash
# 等待 MySQL 就绪
until docker exec mysql mysqladmin ping -h localhost -u root -proot --silent 2>/dev/null; do
  echo "Waiting for MySQL..."
  sleep 2
done

# 创建数据库
docker exec -i mysql mysql -u root -proot -e \
  "CREATE DATABASE IF NOT EXISTS mall DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 导入数据表及种子数据（78 张表，含测试账号）
docker exec -i mysql mysql -u root -proot mall \
  < ~/MallTest/mall/document/sql/mall.sql
```

验证：
```bash
docker exec -i mysql mysql -u root -proot mall -e "SELECT username, email FROM ums_admin;"
```

预期输出管理员账号：`admin`, `test`, `macro`, `productAdmin`, `orderAdmin` 等。

---

## 第十步：修复 MySQL 数据目录权限

导入 SQL 后若出现 `Permission denied` 读取触发器文件，需修复所有权：

```bash
sudo chown -R 999:999 /mydata/mysql/data
docker restart mysql
```

---

## 第十一步：配置 Elasticsearch IK 分词器

mall-search 依赖 `analysis-ik` 插件进行中文分词。

```bash
docker exec elasticsearch bin/elasticsearch-plugin install -b \
  https://get.infini.cloud/elasticsearch/analysis-ik/7.17.3

docker restart elasticsearch

# 等待 ES 就绪
until curl -s http://localhost:9200/_cluster/health | grep -q '"status"'; do
  sleep 1
done
```

---

## 第十二步：配置 RabbitMQ

mall-portal 需要 `mall` 用户和 `/mall` 虚拟主机。

```bash
docker exec rabbitmq rabbitmqctl add_user mall mall
docker exec rabbitmq bash -c \
  "chown -R rabbitmq:rabbitmq /var/lib/rabbitmq && rabbitmqctl add_vhost /mall && rabbitmqctl set_permissions -p /mall mall '.*' '.*' '.*'"
```

> **注意：** 如果 `/mall` vhost 状态为 down（通常由数据目录权限问题引起），先 `delete_vhost` 再重建。

---

## 第十三步：构建 Java 项目

```bash
cd ~/MallTest/mall

# 构建全部 8 个模块（跳过 Docker 镜像构建，因开发环境不需要）
mvn clean package -DskipTests -Ddocker.skip=true

# 安装到本地 Maven 仓库，供 spring-boot:run 使用
mvn install -DskipTests -Ddocker.skip=true
```

各模块构建产物：
```
mall-common/target/mall-common-1.0-SNAPSHOT.jar
mall-mbg/target/mall-mbg-1.0-SNAPSHOT.jar
mall-security/target/mall-security-1.0-SNAPSHOT.jar
mall-admin/target/mall-admin-1.0-SNAPSHOT.jar
mall-search/target/mall-search-1.0-SNAPSHOT.jar
mall-portal/target/mall-portal-1.0-SNAPSHOT.jar
mall-demo/target/mall-demo-1.0-SNAPSHOT.jar
```

---

## 第十四步：启动后端服务

每个服务单独开一个终端，或使用后台运行：

```bash
# mall-admin (管理后台 API, 端口 8080)
java -jar ~/MallTest/mall/mall-admin/target/mall-admin-1.0-SNAPSHOT.jar \
  --spring.profiles.active=dev &

# mall-search (商品搜索, 端口 8081)
java -jar ~/MallTest/mall/mall-search/target/mall-search-1.0-SNAPSHOT.jar \
  --spring.profiles.active=dev &

# mall-portal (前台商城 API, 端口 8085)
java -jar ~/MallTest/mall/mall-portal/target/mall-portal-1.0-SNAPSHOT.jar \
  --spring.profiles.active=dev &

# mall-demo (演示模块, 端口 8082)
java -jar ~/MallTest/mall/mall-demo/target/mall-demo-1.0-SNAPSHOT.jar \
  --spring.profiles.active=dev &
```

---

## 第十五步：启动前端

```bash
cd ~/MallTest/mall-admin-web
npm install
npm run dev &
```

---

## 第十六步：验证全部服务

### 健康检查

```bash
# 后端
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator  # mall-admin
curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/actuator  # mall-search
curl -s -o /dev/null -w "%{http_code}" http://localhost:8082/actuator  # mall-demo
curl -s -o /dev/null -w "%{http_code}" http://localhost:8085/actuator  # mall-portal

# 前端
curl -s -o /dev/null -w "%{http_code}" http://localhost:5173/
```

### API 功能验证

```bash
# 登录接口（默认管理员账号 admin / macro123）
curl -s -X POST http://localhost:8080/admin/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"macro123"}'

# 商品搜索（需要 IK 分词器）
curl -s "http://localhost:8081/esProduct/search/simple?keyword=手机&pageNum=1&pageSize=3"

# 前台首页内容（需要 MongoDB + RabbitMQ）
curl -s "http://localhost:8085/home/content"

# 品牌列表（需要 Spring Security 认证）
curl -s "http://localhost:8082/brand/listAll"
```

---

## 常见问题排查

### 1. MySQL 容器不断重启

**症状：** `mysqld: Can't read dir of '/etc/mysql/conf.d/'`
**解决：** 确保宿主机存在 `mkdir -p /mydata/mysql/conf/conf.d /mydata/mysql/conf/mysql.conf.d`

### 2. Elasticsearch IK 插件安装失败

**症状：** `Unknown plugin analysis-ik`
**解决：** 旧版 URL 已失效，使用新版 `https://get.infini.cloud/elasticsearch/analysis-ik/7.17.3`，并加 `-b` 参数跳过交互确认。

### 3. RabbitMQ vhost 拒绝连接

**症状：** `vhost '/mall' is down`
**解决：** 删除 vhost 后重建，并确保 `/var/lib/rabbitmq` 目录属主为 `rabbitmq:rabbitmq`。

### 4. mall-demo 构建失败

**症状：** `Error reading archive file: error in opening zip file`
**解决：** 本地 Maven 缓存损坏，`mvn clean package -DskipTests -pl mall-demo -am` 重新构建。

### 5. 数据库表访问 Permission denied

**症状：** `Can't get stat of './mall/xxx.TRG' (Errcode: 13)`
**解决：** `sudo chown -R 999:999 /mydata/mysql/data && docker restart mysql`

### 6. Nginx 容器退出

**症状：** `open() "/etc/nginx/nginx.conf" failed`
**解决：** 挂载 `/mydata/nginx/conf` 覆盖了默认配置文件，需手动创建 `nginx.conf` 并从镜像中提取 `mime.types`。

---

## 服务端口总览

| 服务 | 端口 | 类型 |
|------|------|------|
| mall-admin-web | 5173 | 前端 (Vite dev server) |
| mall-admin | 8080 | 后端 (管理 API) |
| mall-search | 8081 | 后端 (搜索服务) |
| mall-demo | 8082 | 后端 (演示模块) |
| mall-portal | 8085 | 后端 (前台 API) |
| MySQL | 3306 | 中间件 |
| Redis | 6379 | 中间件 |
| Elasticsearch | 9200 | 中间件 |
| MongoDB | 27017 | 中间件 |
| RabbitMQ | 5672 | 中间件 |
| RabbitMQ 管理 | 15672 | 中间件 |
| MinIO API | 9090 | 中间件 |
| MinIO Console | 9001 | 中间件 |
| Nginx | 80 | 中间件 |
| Logstash | 4560-4563 | 中间件 |
| Kibana | 5601 | 中间件 |

---

## 默认账号

| 服务 | 用户名 | 密码 |
|------|--------|------|
| mall-admin 登录 | admin | macro123 |
| MySQL | root | root |
| RabbitMQ | mall | mall |
| MinIO | minioadmin | minioadmin |
| Druid 监控 | druid | druid |
