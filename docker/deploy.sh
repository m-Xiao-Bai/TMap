#!/bin/bash
# ============================================================
# TMap 部署脚本
# 用法: ./deploy.sh [all|backend|frontend|python]
# ============================================================

set -e

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DOCKER_DIR="$PROJECT_DIR/docker"

# 颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# 检查 Docker
check_docker() {
    if ! command -v docker &> /dev/null; then
        log_error "Docker 未安装"
        exit 1
    fi
    if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
        log_error "Docker Compose 未安装"
        exit 1
    fi
}

# 检查 .env 文件
check_env() {
    if [ ! -f "$DOCKER_DIR/.env" ]; then
        log_error ".env 文件不存在，请先配置"
        exit 1
    fi
    if [ ! -f "$PROJECT_DIR/TransitMap-python/.env" ]; then
        log_warn "Python .env 文件不存在，将使用 .env.example"
        cp "$PROJECT_DIR/TransitMap-python/.env.example" "$PROJECT_DIR/TransitMap-python/.env"
    fi
}

# 执行 SQL 迁移
run_migrations() {
    log_info "检查数据库迁移..."
    if [ -f "$DOCKER_DIR/mysql/v2_crawler_tables.sql" ]; then
        log_info "执行 v2 迁移脚本..."
        docker exec -i tmap-mysql mysql -u root -p$(grep MYSQL_ROOT_PASSWORD "$DOCKER_DIR/.env" | cut -d= -f2) transit_map < "$DOCKER_DIR/mysql/v2_crawler_tables.sql" 2>/dev/null || true
    fi
}

# 构建并启动所有服务
deploy_all() {
    log_info "构建并启动所有服务..."
    cd "$DOCKER_DIR"
    docker compose up -d --build
    log_info "所有服务已启动"
}

# 仅更新后端
deploy_backend() {
    log_info "更新后端服务..."
    cd "$DOCKER_DIR"
    docker compose up -d --build admin-server user-server
    docker compose restart nginx
    log_info "后端服务已更新"
}

# 仅更新前端
deploy_frontend() {
    log_info "更新前端..."
    cd "$PROJECT_DIR/TransitMap-vue-user" && npm run build
    cd "$PROJECT_DIR/TransitMap-vue-admin" && npm run build
    cd "$DOCKER_DIR"
    docker compose restart nginx
    log_info "前端已更新"
}

# 仅更新 Python 服务
deploy_python() {
    log_info "更新 Python 服务..."
    cd "$DOCKER_DIR"
    docker compose up -d --build python-service
    log_info "Python 服务已更新"
}

# 查看日志
show_logs() {
    cd "$DOCKER_DIR"
    docker compose logs -f --tail=100
}

# 健康检查
health_check() {
    log_info "执行健康检查..."

    # 检查 Java 后端
    if curl -sf http://localhost:8888/transitMap/actuator/health > /dev/null 2>&1; then
        log_info "✅ Java User Server: 正常"
    else
        log_warn "❌ Java User Server: 不可达"
    fi

    if curl -sf http://localhost:8889/transitMap-admin/actuator/health > /dev/null 2>&1; then
        log_info "✅ Java Admin Server: 正常"
    else
        log_warn "❌ Java Admin Server: 不可达"
    fi

    # 检查 Python 服务
    if curl -sf http://localhost:8000/health > /dev/null 2>&1; then
        log_info "✅ Python Service: 正常"
    else
        log_warn "❌ Python Service: 不可达"
    fi

    # 检查 Nginx
    if curl -sf http://localhost:80 > /dev/null 2>&1; then
        log_info "✅ Nginx: 正常"
    else
        log_warn "❌ Nginx: 不可达"
    fi
}

# 主入口
main() {
    check_docker
    check_env

    case "${1:-all}" in
        all)
            deploy_all
            run_migrations
            sleep 5
            health_check
            ;;
        backend)
            deploy_backend
            ;;
        frontend)
            deploy_frontend
            ;;
        python)
            deploy_python
            ;;
        logs)
            show_logs
            ;;
        health)
            health_check
            ;;
        *)
            echo "用法: $0 [all|backend|frontend|python|logs|health]"
            exit 1
            ;;
    esac
}

main "$@"
