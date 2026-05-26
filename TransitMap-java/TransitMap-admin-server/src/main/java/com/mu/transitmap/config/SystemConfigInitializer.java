package com.mu.transitmap.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mu.transitmap.entity.SystemConfig;
import com.mu.transitmap.service.impl.SystemConfigServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class SystemConfigInitializer implements CommandLineRunner {

    @Autowired
    private SystemConfigServiceImpl systemConfigService;

    @Override
    public void run(String... args) {
        initConfig("station.status_map", "json",
                "{\"0\":\"未开通\",\"1\":\"运营中\",\"2\":\"建设中\",\"3\":\"规划中\",\"4\":\"已停运\"}",
                "station", "站点/线路状态码与中文映射", 1);

        initConfig("station.type_map", "json",
                "{\"0\":\"地下\",\"1\":\"地面\",\"2\":\"高架\"}",
                "station", "站点类型码与中文映射", 1);

        initConfig("pagination.default_size", "number",
                "10", "pagination", "默认每页显示条数", 1);

        initConfig("pagination.size_options", "json",
                "[10,20,50,100]", "pagination", "每页条数可选值", 1);

        initConfig("auth.captcha_image_expiry", "number",
                "300", "auth", "图片验证码过期时间（秒）", 1);

        initConfig("auth.captcha_email_expiry", "number",
                "300", "auth", "邮箱验证码过期时间（秒）", 1);

        initConfig("auth.token_expiry", "number",
                "86400000", "auth", "登录凭证过期时间（毫秒）", 1);

        initConfig("cache.ttl.metroLine", "number",
                "86400", "cache", "地铁线路缓存时间（秒，默认86400=1天）", 1);
        initConfig("cache.ttl.metroStation", "number",
                "86400", "cache", "地铁站缓存时间（秒，默认86400=1天）", 1);
        initConfig("cache.ttl.city", "number",
                "86400", "cache", "城市列表缓存时间（秒，默认86400=1天）", 1);
        initConfig("cache.ttl.country", "number",
                "86400", "cache", "国家列表缓存时间（秒，默认86400=1天）", 1);

        // 清理已废弃的 homepage 配置
        removeConfig("homepage.hero_title");
        removeConfig("homepage.hero_subtitle");
        removeConfig("homepage.search_placeholder");
        removeConfig("homepage.feature_title");
        removeConfig("homepage.features");
        removeConfig("homepage.show_stats");
        removeConfig("homepage.show_lines");

        // 地图路线样式配置
        initConfig("map.route_style", "json",
                "{\"routeColor\":\"#ff6b35\",\"glowWeight\":14,\"glowOpacity\":0.25,\"lineWeight\":6,\"lineOpacity\":0.95,\"dashArray\":\"12 6\",\"endpointRadius\":10,\"endpointWeight\":4,\"midpointRadius\":7,\"midpointWeight\":3,\"dimLineGlowOpacity\":0.04,\"dimLineOpacity\":0.12,\"dimStationOpacity\":0.12,\"dimStationRadius\":3}",
                "map", "路线高亮样式（颜色、线宽、透明度等）", 1);

        // 地图标签缩放配置
        initConfig("map.label_config", "json",
                "{\"baseFontSize\":11,\"minFontSize\":6,\"shrinkStartZoom\":13,\"hideZoom\":11,\"fontWeight\":500,\"color\":\"#3a3a4a\"}",
                "map", "站点标签缩放行为（字号、缩放阈值等）", 1);

        // 票价阶梯配置
        initConfig("ticket.price_tiers", "json",
                "[{\"maxStops\":3,\"price\":2},{\"maxStops\":6,\"price\":3},{\"maxStops\":9,\"price\":4},{\"maxStops\":12,\"price\":5},{\"maxStops\":18,\"price\":6},{\"maxStops\":999,\"price\":7}]",
                "ticket", "票价阶梯（按站数区间定价）", 1);

        // 行程估算参数
        initConfig("ticket.estimate_params", "json",
                "{\"minutesPerStop\":3,\"minMinutes\":2,\"kmPerStop\":1.8}",
                "ticket", "行程参数：每站耗时(分钟)、最低耗时、每站距离(无实际距离时的回退值，公里)", 1);

        // 支付超时时间
        initConfig("ticket.payment_timeout_hours", "number",
                "24", "ticket", "待支付订单超时时间（小时），超时后自动标记为已过期", 1);

        // 二维码有效时长
        initConfig("ticket.qr_validity_hours", "number",
                "24", "ticket", "支付成功后二维码有效时长（小时），过期后自动标记为已过期", 1);

        // 清理已废弃的 line.colors 配置
        removeConfig("line.colors");

        // ===== Agent 路线助手配置 =====

        // 基础开关
        initConfig("agent.enabled", "number",
                "1", "agent", "Agent 全局开关（1=开启 0=关闭）", 0);

        // 欢迎语与快捷词
        initConfig("agent.welcome_text", "string",
                "你好，我是路线助手 🚇 告诉我你想去哪儿、从哪儿出发，我帮你规划地铁路线。",
                "agent", "Agent 欢迎语文案", 1);
        initConfig("agent.welcome_chips", "json",
                "[\"我要去...\",\"附近地铁\",\"换乘建议\",\"怎么买票\",\"常去地点\"]",
                "agent", "基础快捷词条（管理端配置）", 1);
        initConfig("agent.welcome_chips.use_personalized", "number",
                "1", "agent", "是否叠加个性化快捷词（1=是 0=否）", 0);
        initConfig("agent.welcome_chips.personalized_count", "number",
                "3", "agent", "个性化快捷词最多几条", 0);

        // 输入框
        initConfig("agent.input.placeholders", "json",
                "[\"试试：我在国贸去北京西站\",\"试试：从首尔站去明洞\",\"告诉我地名我帮你规划 🚇\"]",
                "agent", "输入框轮播 placeholder", 1);
        initConfig("agent.input.rotate_interval_ms", "number",
                "3500", "agent", "placeholder 轮播间隔（毫秒）", 1);
        initConfig("agent.input.max_length", "number",
                "500", "agent", "单次最大输入字符数", 1);

        // Prompt 配置
        initConfig("agent.prompt.missing_destination", "string",
                "你想去哪儿呢？告诉我目的地，我帮你规划路线 🚇",
                "agent", "缺少目的地时的提示文案", 0);
        initConfig("agent.prompt.cross_city", "string",
                "你这是跨城旅程，本系统只支持单城内地铁规划。想看哪一段？",
                "agent", "跨城旅程提示文案", 0);
        initConfig("agent.prompt.system", "string",
                "你是 TransitMap 的城内地铁路线助手。\n\n【你能做什么】\n- 用户告诉你\"我在 A 想去 B\"，你帮 ta 规划地铁路线\n- 解释换乘怎么走、需要多久、多少钱\n- 引导用户下单（让 ta 点页面上的\"下单\"按钮，不要你直接操作）\n\n【你不能做什么】\n- 不规划跨城旅程（直接拒绝，让用户选其中一座城市）\n- 不预测列车时刻\n- 不回答与出行无关的问题（婉拒）\n- 【严重禁令】绝对不要凭印象说出\"系统只有 X 号线\"\"暂未接入 X 站\"\"找不到 X 站\"等数据相关的话！\n  你完全不知道系统里有哪些线路和站点，所有这类信息都由系统注入的 routePlan 字段决定。\n  如果 routePlan 数据存在，请基于其中的站名、线路名、票价照实回答，不要做任何关于「系统是否有数据」的判断。\n\n【当前会话上下文】\n- 用户已登录：{{userLogged}}\n- 已选城市：{{selectedCity}}\n- 系统已查到的路线（如有）：{{routePlanSummary}}\n\n【回复风格】\n- 友好简洁、用 🚇 emoji 增加趣味（每条消息最多 1 个）\n- 关键信息用列表呈现\n- 不要编造站名/票价，所有数据以系统注入的 routePlan 为准\n- 回复末尾必须返回 3 个跟随快捷词（JSON 数组），让用户更易继续对话",
                "agent", "Agent 全局 System Prompt", 0);
        initConfig("agent.prompt.general", "string",
                "", "agent", "Agent 通用对话系统提示词（空则用内置默认）", 0);

        // 历史上下文
        initConfig("agent.history.session_ttl_days", "number",
                "30", "agent", "登录用户会话保留天数", 0);
        initConfig("agent.history.max_context_msgs", "number",
                "10", "agent", "LLM 上下文最大消息数", 0);
        initConfig("agent.history.summary_threshold", "number",
                "20", "agent", "触发上下文摘要的消息数阈值", 0);

        // 匿名用户限制
        initConfig("agent.anon.daily_message_limit", "number",
                "10", "agent", "未登录用户每天最多可发送的消息条数（0=不限）", 0);
        initConfig("agent.anon.session_ttl_days", "number",
                "3", "agent", "未登录用户会话保留天数", 0);

        // LLM 配置
        initConfig("agent.llm.provider", "string",
                "openai-compatible", "agent", "LLM 提供商类型", 0);
        initConfig("agent.llm.model", "string",
                "deepseek-chat", "agent", "LLM 模型名称", 0);
        initConfig("agent.llm.base_url", "string",
                "https://api.deepseek.com/v1", "agent", "LLM API 基础 URL", 0);
        initConfig("agent.llm.api_key", "secret",
                "", "agent", "LLM API Key（AES 加密存储）", 0);
        initConfig("agent.llm.temperature", "number",
                "0.3", "agent", "LLM 温度参数", 0);
        initConfig("agent.llm.max_tokens", "number",
                "1024", "agent", "LLM 最大输出 token 数", 0);
        initConfig("agent.llm.timeout_ms", "number",
                "30000", "agent", "LLM 请求超时（毫秒）", 0);
        initConfig("agent.llm.cost_per_1k_input_cents", "number",
                "1", "agent", "每 1000 输入 token 成本（分）", 0);
        initConfig("agent.llm.cost_per_1k_output_cents", "number",
                "2", "agent", "每 1000 输出 token 成本（分）", 0);

        // 地图/高德配置
        initConfig("agent.map.provider", "string",
                "amap", "agent", "地图服务提供商", 0);
        initConfig("agent.map.api_key", "secret",
                "", "agent", "高德地图 API Key（AES 加密存储）", 0);

        // 限流
        initConfig("agent.rate_limit.per_user_per_min", "number",
                "30", "agent", "每登录用户每分钟最大对话条数", 0);
        initConfig("agent.rate_limit.per_ip_per_min", "number",
                "60", "agent", "每 IP 每分钟最大对话条数（防爬/防 DDoS）", 0);

        // 安全
        initConfig("agent.security.forbidden_words", "json",
                "[\"政治\",\"色情\",\"赌博\",\"毒品\"]",
                "agent", "敏感词列表（命中则直接拒绝，不调 LLM）", 0);
        initConfig("agent.security.ws_allowed_origins", "string",
                "*", "agent", "WebSocket 允许的 Origin 白名单（逗号分隔，* 表示不限制；生产环境应改为具体域名）", 0);

        // WebSocket
        initConfig("agent.ws.heartbeat_interval_ms", "number",
                "25000", "agent", "WebSocket 心跳间隔（毫秒）", 0);
        initConfig("agent.ws.idle_timeout_ms", "number",
                "300000", "agent", "WebSocket 空闲超时（毫秒）", 0);
        initConfig("agent.ws.max_conn_per_user", "number",
                "3", "agent", "同用户最大并发 WS 连接数", 0);

        // 语音输入（这些 5 项需要被前端读取，isPublic=1）
        initConfig("agent.voice.enabled", "number",
                "1", "agent", "语音输入开关（1=开启 0=关闭）", 1);
        initConfig("agent.voice.mode", "string",
                "push_to_talk", "agent", "语音模式：push_to_talk / toggle", 1);
        initConfig("agent.voice.silence_ms", "number",
                "1500", "agent", "静音自动停止时长（毫秒）", 1);
        initConfig("agent.voice.max_duration_ms", "number",
                "60000", "agent", "单次录音最长时长（毫秒）", 1);
        initConfig("agent.voice.send_to_input", "number",
                "1", "agent", "转写完成后填入输入框（1）或直接发送（0）", 1);
        initConfig("agent.voice.server_asr_enabled", "number",
                "0", "agent", "是否启用后端 ASR 兜底（1=是 0=否）", 0);
        initConfig("agent.voice.server_asr_provider", "string",
                "", "agent", "后端 ASR 提供商：xfyun/aliyun/whisper", 0);

        // RAG（v1 关闭）
        initConfig("agent.rag.enabled", "number",
                "0", "agent", "RAG 知识检索开关（1=开启 0=关闭）", 0);
        initConfig("agent.rag.top_k", "number",
                "3", "agent", "RAG 召回的知识条目数量上限", 0);
        initConfig("agent.rag.use_embedding", "number",
                "0", "agent", "RAG 检索策略：1=向量余弦相似度（需先配 embedding 并重建向量）0=关键词匹配", 0);
        initConfig("agent.rag.min_similarity", "string",
                "0.3", "agent", "向量召回最低相似度阈值（0~1，越大越严格）", 0);

        // 邻近站候选 + OSM 兜底
        initConfig("agent.nearby.max_candidates", "number",
                "3", "agent", "POI 找最近地铁站的候选数量上限（用于多对多组合规划）", 0);
        initConfig("agent.nearby.max_radius_km", "string",
                "5.0", "agent", "候选搜索半径上限（公里），超出此距离的站点不参与候选", 0);
        initConfig("agent.nearby.osm_enabled", "number",
                "0", "agent", "是否启用 OpenStreetMap Overpass API 兜底（本地 metro_station 没找到合适站时调用，1=启用）", 0);
        initConfig("agent.nearby.osm_endpoint", "string",
                "https://overpass-api.de/api/interpreter", "agent", "OSM Overpass API 端点（公共服务，可换镜像）", 0);
        initConfig("agent.nearby.osm_radius_m", "number",
                "1500", "agent", "OSM Overpass 查询半径（米），仅在 osm_enabled=1 时生效", 0);

        // LLM 位置消歧（应对方言/俗称/模糊描述，如「小蛮腰」「市中心」）
        initConfig("agent.location.llm_resolve_enabled", "number",
                "1", "agent", "常规匹配全失败时，是否调 LLM 把模糊地名翻译为标准 POI 名（1=启用 0=禁用）", 0);
        initConfig("agent.location.llm_first_enabled", "number",
                "1", "agent", "（最高优先级）每次都先问 LLM「最近的地铁站」，命中即用；DB 验证防幻觉。1=启用 0=禁用", 0);

        // Embedding 配置（独立于聊天 LLM；DeepSeek 等不提供 embedding）
        initConfig("agent.embedding.base_url", "string",
                "https://api.openai.com/v1", "agent", "Embedding API base_url（建议 OpenAI 或本地兼容服务）", 0);
        initConfig("agent.embedding.api_key", "secret",
                "", "agent", "Embedding API Key（AES 加密存储；可与 LLM key 不同）", 0);
        initConfig("agent.embedding.model", "string",
                "text-embedding-3-small", "agent", "Embedding 模型名称（OpenAI 推荐 text-embedding-3-small，1536 维）", 0);

        // 迁移：将历史 DB 中以下 key 的 isPublic 修正为 1（首次升级后生效）
        ensurePublic("agent.welcome_text");
        ensurePublic("agent.voice.enabled");
        ensurePublic("agent.voice.mode");
        ensurePublic("agent.voice.silence_ms");
        ensurePublic("agent.voice.max_duration_ms");
        ensurePublic("agent.voice.send_to_input");

        systemConfigService.refreshCache();
    }

    /** 把已存在 key 的 isPublic 升级为 1（首次安装/老库升级用） */
    private void ensurePublic(String key) {
        try {
            SystemConfig cfg = systemConfigService.getOne(new LambdaQueryWrapper<SystemConfig>()
                    .eq(SystemConfig::getConfigKey, key));
            if (cfg != null && (cfg.getIsPublic() == null || cfg.getIsPublic() != 1)) {
                cfg.setIsPublic(1);
                systemConfigService.updateById(cfg);
            }
        } catch (Exception e) {
            // 忽略，不阻断启动
        }
    }

    private void removeConfig(String key) {
        systemConfigService.remove(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, key));
    }

    private void initConfig(String key, String type, String value, String group, String desc, int isPublic) {
        long count = systemConfigService.count(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, key));
        if (count > 0) return;

        SystemConfig config = new SystemConfig();
        config.setConfigKey(key);
        config.setConfigType(type);
        config.setConfigValue(value);
        config.setConfigGroup(group);
        config.setDescription(desc);
        config.setIsPublic(isPublic);
        systemConfigService.save(config);
    }
}
