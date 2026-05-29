-- ============================================================
-- TMap v2: 爬虫 + 对话质量 评估表
-- 执行方式: 在 MySQL 中手动执行此文件
-- ============================================================

-- 1. 爬取任务记录
CREATE TABLE IF NOT EXISTS crawler_task (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id         VARCHAR(36) NOT NULL UNIQUE COMMENT 'UUID 任务ID',
    city_name       VARCHAR(50) NOT NULL COMMENT '城市名称',
    city_id         BIGINT COMMENT '关联城市ID（爬取完成后回填）',
    country_id      BIGINT NOT NULL DEFAULT 1 COMMENT '国家ID',
    status          VARCHAR(20) NOT NULL DEFAULT 'pending'
                    COMMENT 'pending/running/completed/failed/cancelled',
    sources         VARCHAR(100) NOT NULL DEFAULT 'wikipedia,baike,osm'
                    COMMENT '数据源，逗号分隔',
    trigger_user_id BIGINT COMMENT '触发用户ID',
    -- 统计结果
    lines_found     INT DEFAULT 0,
    stations_found  INT DEFAULT 0,
    lines_inserted  INT DEFAULT 0,
    lines_updated   INT DEFAULT 0,
    stations_inserted INT DEFAULT 0,
    stations_updated  INT DEFAULT 0,
    stations_skipped  INT DEFAULT 0,
    stations_pending_review INT DEFAULT 0,
    -- 进度
    progress_pct    INT DEFAULT 0 COMMENT '进度百分比 0-100',
    current_step    VARCHAR(50) COMMENT '当前步骤名称',
    error_message   TEXT,
    -- 时间
    started_at      DATETIME,
    completed_at    DATETIME,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_city (city_name),
    INDEX idx_status (status),
    INDEX idx_trigger_user (trigger_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='爬取任务记录';


-- 2. 站点审核队列
CREATE TABLE IF NOT EXISTS station_review (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id         VARCHAR(36) NOT NULL COMMENT '关联爬取任务',
    city_name       VARCHAR(50) NOT NULL,
    station_name    VARCHAR(100) NOT NULL,
    line_name       VARCHAR(200),
    -- 爬取原始数据
    scraped_address VARCHAR(500) COMMENT '爬取/LLM补全的地址',
    scraped_lat     DECIMAL(10,7),
    scraped_lng     DECIMAL(10,7),
    -- 审核状态
    review_status   VARCHAR(20) NOT NULL DEFAULT 'pending'
                    COMMENT 'pending/approved/rejected',
    confidence      VARCHAR(10) COMMENT 'high/medium/low',
    review_note     VARCHAR(500) COMMENT '审核备注',
    reviewer_id     BIGINT COMMENT '审核人ID',
    reviewed_at     DATETIME,
    -- 关联
    matched_station_id BIGINT COMMENT '审核通过后关联的站点ID',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_task (task_id),
    INDEX idx_status (review_status),
    INDEX idx_city (city_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='站点数据审核队列';


-- 3. 对话质量评估记录
CREATE TABLE IF NOT EXISTS chat_quality_log (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    message_id      BIGINT NOT NULL COMMENT '关联 chat_message.id',
    session_id      BIGINT NOT NULL,
    user_id         BIGINT,
    -- 评分
    quality_score   DECIMAL(3,2) COMMENT '0.00-1.00 自动评分',
    user_feedback   VARCHAR(20) COMMENT 'positive/negative/null',
    feedback_detail VARCHAR(500),
    -- 指标
    response_time_ms INT COMMENT '响应时间毫秒',
    token_count      INT,
    intent_type      VARCHAR(20) COMMENT 'route/chat/order',
    scenario         VARCHAR(30),
    -- 分析
    is_hallucination BOOLEAN DEFAULT FALSE COMMENT '是否疑似幻觉',
    is_off_topic     BOOLEAN DEFAULT FALSE COMMENT '是否偏离主题',
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session (session_id),
    INDEX idx_user (user_id),
    INDEX idx_score (quality_score),
    INDEX idx_feedback (user_feedback)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='对话质量评估记录';
