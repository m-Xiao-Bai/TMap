<template>
  <div class="agent-config-page">
    <div class="page-header">
      <h2><el-icon><ChatLineRound /></el-icon> Agent 路线助手配置</h2>
      <div class="header-actions">
        <el-tag v-if="userStore.roleCode === 3" type="warning" effect="plain">超级管理员</el-tag>
        <el-tag v-if="userStore.roleCode === 4" type="danger" effect="plain">最高级管理员</el-tag>
        <el-button :icon="Refresh" plain @click="fetchConfigs">刷新</el-button>
        <el-button type="primary" :icon="Check" :loading="saving" @click="handleSave">保存全部</el-button>
      </div>
    </div>

    <el-alert
      title="此处可调节 Agent 路线助手的运行参数。修改后会影响所有用户的对话体验。"
      type="info" show-icon :closable="false" style="margin-bottom: 16px"
    />

    <el-tabs v-model="activeTab" type="card" class="config-tabs" v-loading="loading">
      <!-- LLM 大模型 -->
      <el-tab-pane name="llm">
        <template #label>
          <span><el-icon><Cpu /></el-icon> 大模型</span>
        </template>
        <el-card shadow="never">
          <template #header>
            <div class="card-hd">
              <span>LLM 提供商与凭证</span>
              <el-button
                size="small"
                :loading="testingLlm"
                @click="onTestLlm"
              >
                <el-icon><Connection /></el-icon>
                测试连通性
              </el-button>
            </div>
          </template>

          <ConfigField :cfg="cfgMap['agent.llm.provider']" placeholder="openai-compatible">
            <template #hint>
              填写一个 provider，base_url / model 留空即自动套用默认：
              <code>openai</code> · <code>deepseek</code> · <code>qwen</code>(/dashscope) ·
              <code>kimi</code>(/moonshot) · <code>doubao</code>(/volcengine) ·
              <code>zhipu</code>(/glm) · <code>siliconflow</code>。均走 OpenAI 兼容协议，无需额外依赖。
            </template>
          </ConfigField>
          <ConfigField :cfg="cfgMap['agent.llm.model']" placeholder="留空走 provider 默认">
            <template #hint>模型名称。留空时根据 provider 自动选默认（如 deepseek-chat / qwen-plus 等）</template>
          </ConfigField>
          <ConfigField :cfg="cfgMap['agent.llm.base_url']" placeholder="留空走 provider 默认">
            <template #hint>OpenAI 兼容协议 base URL，结尾不带 /chat/completions。留空时根据 provider 自动选默认</template>
          </ConfigField>
          <ConfigField :cfg="cfgMap['agent.llm.api_key']">
            <template #hint>
              留空表示不修改。前端不显示明文，提交新值会自动 AES 加密后存储。
            </template>
          </ConfigField>

          <el-divider content-position="left">生成参数</el-divider>

          <ConfigField :cfg="cfgMap['agent.llm.temperature']" />
          <ConfigField :cfg="cfgMap['agent.llm.max_tokens']" />
          <ConfigField :cfg="cfgMap['agent.llm.timeout_ms']" />

          <el-divider content-position="left">计费</el-divider>

          <ConfigField :cfg="cfgMap['agent.llm.cost_per_1k_input_cents']">
            <template #hint>每 1000 输入 token 的成本（分）。用于用量大盘的成本估算。</template>
          </ConfigField>
          <ConfigField :cfg="cfgMap['agent.llm.cost_per_1k_output_cents']">
            <template #hint>每 1000 输出 token 的成本（分）</template>
          </ConfigField>
        </el-card>
      </el-tab-pane>

      <!-- 地图服务 -->
      <el-tab-pane name="map">
        <template #label>
          <span><el-icon><MapLocation /></el-icon> 地图</span>
        </template>
        <el-card shadow="never">
          <template #header>
            <div class="card-hd">
              <span>地图服务（地理编码 / IP 定位 / POI 搜索）</span>
              <el-button
                size="small"
                :loading="testingAmap"
                @click="onTestAmap"
              >
                <el-icon><Connection /></el-icon>
                测试连通性
              </el-button>
            </div>
          </template>
          <ConfigField :cfg="cfgMap['agent.map.provider']" placeholder="amap">
            <template #hint>当前仅支持高德（amap）</template>
          </ConfigField>
          <ConfigField :cfg="cfgMap['agent.map.api_key']">
            <template #hint>
              高德 Web 服务 Key。需要在 <a href="https://lbs.amap.com" target="_blank">高德开放平台</a>
              开启「地理编码 / 逆地理编码 / IP 定位 / 关键字搜索」服务。
            </template>
          </ConfigField>

          <el-divider content-position="left">附近地铁站候选（多对多组合规划）</el-divider>

          <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
            POI 不一定就是地铁站（如「万达广场」「人民广场」）。开启 TopN 候选后，
            Agent 会从用户位置附近找多个站做组合规划，选「接驳时间+地铁时间」总成本最小的方案，
            回复里也会智能给出步行/骑车/打车建议。
          </el-alert>

          <ConfigField :cfg="cfgMap['agent.nearby.max_candidates']">
            <template #hint>每个 POI 找几个最近站作为候选（推荐 3，复杂度 N×N 次 BFS）</template>
          </ConfigField>
          <ConfigField :cfg="cfgMap['agent.nearby.max_radius_km']" placeholder="5.0">
            <template #hint>候选半径上限（公里），超出范围的站不考虑</template>
          </ConfigField>

          <el-divider content-position="left">OpenStreetMap Overpass 兜底（实验性）</el-divider>

          <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 12px">
            灵感来自 <code>geospatial-mcp-server</code>。当本地 metro_station 完全找不到附近站时，
            调 OSM Overpass 公开 API（免费免 key）查询世界范围内的地铁站 POI。
            <strong>注意：OSM 找到的站没有本系统的 station_id，无法直接规划路径，仅用于
            「附近其实有 X 站，但本系统未录入」的提示。</strong>
          </el-alert>

          <ConfigField :cfg="cfgMap['agent.nearby.osm_enabled']">
            <template #hint>0=关闭；1=启用（每次失败匹配会调一次 OSM，延迟 1~5s）</template>
          </ConfigField>
          <ConfigField :cfg="cfgMap['agent.nearby.osm_endpoint']">
            <template #hint>
              Overpass API 端点。默认 <code>https://overpass-api.de/api/interpreter</code>，
              速度慢可换镜像如 <code>https://overpass.kumi.systems/api/interpreter</code>
            </template>
          </ConfigField>
          <ConfigField :cfg="cfgMap['agent.nearby.osm_radius_m']">
            <template #hint>OSM 查询半径（米）。默认 1500</template>
          </ConfigField>

          <el-divider content-position="left">LLM 位置消歧</el-divider>

          <el-alert type="success" :closable="false" show-icon style="margin-bottom: 12px">
            <strong>LLM-first 工作流（推荐）</strong>：每次都先问大模型「{地点} 附近最近的地铁站是？」，
            返回的站名在本地 metro_station 表验证存在后直接使用（防幻觉）。
            <br>命中即用，跳过传统的「站名匹配 + geo TopN + 模糊匹配」。
            <br>适合处理：地铁站名、知名 POI、地标、商圈、大学、俗称等。
            <br><strong>每次对话会增加 1~2s 延迟和少量 token 成本。</strong>
          </el-alert>
          <ConfigField :cfg="cfgMap['agent.location.llm_first_enabled']">
            <template #hint>0=禁用（走传统算法）；1=启用（默认，最高优先级，命中即用）</template>
          </ConfigField>

          <el-alert type="info" :closable="false" show-icon style="margin-top: 12px; margin-bottom: 12px">
            <strong>名字翻译兜底</strong>：当 LLM-first + 传统算法 全部失败时，
            再调一次 LLM 把模糊输入翻译为标准 POI 名（如「小蛮腰」→「广州塔」），用候选名重试 geocode。
          </el-alert>
          <ConfigField :cfg="cfgMap['agent.location.llm_resolve_enabled']">
            <template #hint>0=禁用；1=启用（默认）。禁用后只用确定性算法</template>
          </ConfigField>
        </el-card>
      </el-tab-pane>

      <!-- 会话行为 -->
      <el-tab-pane name="behavior">
        <template #label>
          <span><el-icon><DataLine /></el-icon> 会话行为</span>
        </template>
        <el-card shadow="never">
          <template #header>
            <span>开关与上下文</span>
          </template>
          <ConfigField :cfg="cfgMap['agent.enabled']">
            <template #hint>0 = 全局关闭 Agent（前端面板和浮按钮均隐藏）</template>
          </ConfigField>
          <ConfigField :cfg="cfgMap['agent.history.session_ttl_days']">
            <template #hint>登录用户会话保留多少天，过期由凌晨定时任务自动清理</template>
          </ConfigField>
          <ConfigField :cfg="cfgMap['agent.history.max_context_msgs']">
            <template #hint>每次调用 LLM 时携带的最近消息数</template>
          </ConfigField>
          <ConfigField :cfg="cfgMap['agent.history.summary_threshold']">
            <template #hint>会话消息超过该阈值后触发摘要（v2 待实现）</template>
          </ConfigField>

          <el-divider content-position="left">未登录用户限制</el-divider>

          <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
            未登录用户的对话受限。修改后即刻生效，已用配额按当天累计计算。
          </el-alert>

          <ConfigField :cfg="cfgMap['agent.anon.daily_message_limit']">
            <template #hint>
              未登录用户每天最多可发送的消息条数。设为 <code>0</code> = 不限制（不推荐）。
              计数按 anonToken + 自然日维度（00:00 自动重置）。
            </template>
          </ConfigField>
          <ConfigField :cfg="cfgMap['agent.anon.session_ttl_days']">
            <template #hint>
              未登录用户的会话保留天数。过期后会话与消息会被凌晨定时任务级联清理。
            </template>
          </ConfigField>

          <el-divider content-position="left">限流</el-divider>

          <ConfigField :cfg="cfgMap['agent.rate_limit.per_user_per_min']" />
          <ConfigField :cfg="cfgMap['agent.rate_limit.per_ip_per_min']" />
        </el-card>
      </el-tab-pane>

      <!-- UX 文案 -->
      <el-tab-pane name="ux">
        <template #label>
          <span><el-icon><EditPen /></el-icon> UX 文案</span>
        </template>
        <el-card shadow="never">
          <template #header>
            <span>欢迎语 / 快捷词 / Prompt</span>
          </template>
          <ConfigField :cfg="cfgMap['agent.welcome_text']" placeholder="你好，我是路线助手 🚇" />

          <div class="chip-edit-wrap">
            <div class="kv-label">
              <span class="kv-label-text">基础快捷词</span>
              <span class="kv-label-hint">agent.welcome_chips · 用户首次进入对话面板时显示</span>
            </div>
            <div class="chip-list">
              <el-tag
                v-for="(c, i) in welcomeChipsList"
                :key="i"
                closable
                @close="removeWelcomeChip(i)"
              >{{ c }}</el-tag>
              <el-input
                v-model="newChip"
                size="small"
                style="width:180px"
                placeholder="新增快捷词后回车"
                @keyup.enter="addWelcomeChip"
              />
              <el-button size="small" type="primary" plain :icon="Plus" @click="addWelcomeChip">添加</el-button>
            </div>
          </div>

          <ConfigField :cfg="cfgMap['agent.welcome_chips.use_personalized']">
            <template #hint>是否在基础词条之上叠加用户个性化高频词</template>
          </ConfigField>
          <ConfigField :cfg="cfgMap['agent.welcome_chips.personalized_count']" />

          <div class="chip-edit-wrap">
            <div class="kv-label">
              <span class="kv-label-text">输入框轮播提示</span>
              <span class="kv-label-hint">agent.input.placeholders</span>
            </div>
            <div class="chip-list">
              <el-tag
                v-for="(c, i) in placeholdersList"
                :key="i"
                closable
                type="info"
                @close="removePlaceholder(i)"
              >{{ c }}</el-tag>
              <el-input
                v-model="newPlaceholder"
                size="small"
                style="width:220px"
                placeholder="新增 placeholder 后回车"
                @keyup.enter="addPlaceholder"
              />
              <el-button size="small" type="primary" plain :icon="Plus" @click="addPlaceholder">添加</el-button>
            </div>
          </div>

          <ConfigField :cfg="cfgMap['agent.input.rotate_interval_ms']" />
          <ConfigField :cfg="cfgMap['agent.input.max_length']" />

          <el-divider content-position="left">Prompt 模板</el-divider>

          <ConfigField :cfg="cfgMap['agent.prompt.missing_destination']" multiline />
          <ConfigField :cfg="cfgMap['agent.prompt.cross_city']" multiline />
          <ConfigField :cfg="cfgMap['agent.prompt.system']" multiline :rows="10">
            <template #hint>
              全局系统 prompt，支持占位符
              <code v-pre>{{userLogged}}</code>、<code v-pre>{{selectedCity}}</code>、<code v-pre>{{routePlanSummary}}</code>
            </template>
          </ConfigField>
        </el-card>
      </el-tab-pane>

      <!-- 语音 -->
      <el-tab-pane name="voice">
        <template #label>
          <span><el-icon><Microphone /></el-icon> 语音</span>
        </template>
        <el-card shadow="never">
          <template #header>
            <span>语音输入</span>
          </template>
          <el-alert
            type="warning"
            :closable="false"
            show-icon
            style="margin-bottom: 12px"
          >
            前端默认使用 Web Speech API（Chrome / Edge 完美支持，Firefox 不支持）。
            后端 ASR 兜底未默认启用，启用前需自行接入讯飞 / 阿里 / Whisper SDK。
          </el-alert>

          <ConfigField :cfg="cfgMap['agent.voice.enabled']" />
          <ConfigField :cfg="cfgMap['agent.voice.mode']" placeholder="push_to_talk / toggle">
            <template #hint>push_to_talk = 按住说话；toggle = 点击切换</template>
          </ConfigField>
          <ConfigField :cfg="cfgMap['agent.voice.silence_ms']">
            <template #hint>静音多久后自动停止录音</template>
          </ConfigField>
          <ConfigField :cfg="cfgMap['agent.voice.max_duration_ms']" />
          <ConfigField :cfg="cfgMap['agent.voice.send_to_input']">
            <template #hint>1 = 转写文本填入输入框让用户编辑；0 = 转写完成直接发送</template>
          </ConfigField>

          <el-divider content-position="left">后端 ASR 兜底（高级）</el-divider>

          <ConfigField :cfg="cfgMap['agent.voice.server_asr_enabled']" />
          <ConfigField :cfg="cfgMap['agent.voice.server_asr_provider']" placeholder="xfyun / aliyun / whisper" />
        </el-card>
      </el-tab-pane>

      <!-- WebSocket -->
      <el-tab-pane name="ws">
        <template #label>
          <span><el-icon><Promotion /></el-icon> WebSocket</span>
        </template>
        <el-card shadow="never">
          <template #header>
            <span>WebSocket 连接参数</span>
          </template>
          <ConfigField :cfg="cfgMap['agent.ws.heartbeat_interval_ms']">
            <template #hint>客户端心跳间隔。生产环境 Nginx 反代超时需 ≥ 2 × 心跳值</template>
          </ConfigField>
          <ConfigField :cfg="cfgMap['agent.ws.idle_timeout_ms']" />
          <ConfigField :cfg="cfgMap['agent.ws.max_conn_per_user']">
            <template #hint>同一用户最多同时建立多少个 WebSocket 连接，超出后旧连接被拒（关闭码 4029）</template>
          </ConfigField>
        </el-card>
      </el-tab-pane>

      <!-- 安全 -->
      <el-tab-pane name="security">
        <template #label>
          <span><el-icon><Lock /></el-icon> 安全</span>
        </template>
        <el-card shadow="never">
          <template #header>
            <span>访问控制 / 内容过滤</span>
          </template>

          <ConfigField :cfg="cfgMap['agent.security.ws_allowed_origins']" placeholder="* 或 https://example.com,https://app.example.com">
            <template #hint>
              WebSocket 允许的 Origin 列表（逗号分隔）。<code>*</code> = 不限制（仅开发用）；
              生产环境改为具体域名，例如 <code>https://app.transitmap.cn</code>。修改后需要重启 user-server 生效。
            </template>
          </ConfigField>

          <div class="chip-edit-wrap">
            <div class="kv-label">
              <span class="kv-label-text">敏感词列表</span>
              <span class="kv-label-hint">agent.security.forbidden_words · 命中即拒绝，不调 LLM</span>
            </div>
            <div class="chip-list">
              <el-tag
                v-for="(w, i) in forbiddenWordsList"
                :key="i"
                closable
                type="danger"
                @close="removeForbiddenWord(i)"
              >{{ w }}</el-tag>
              <el-input
                v-model="newForbiddenWord"
                size="small"
                style="width:180px"
                placeholder="添加敏感词后回车"
                @keyup.enter="addForbiddenWord"
              />
              <el-button size="small" type="danger" plain :icon="Plus" @click="addForbiddenWord">添加</el-button>
            </div>
          </div>
        </el-card>
      </el-tab-pane>

      <!-- RAG -->
      <el-tab-pane name="rag">
        <template #label>
          <span><el-icon><Collection /></el-icon> RAG</span>
        </template>
        <el-card shadow="never">
          <template #header>
            <span>RAG 知识检索</span>
          </template>
          <el-alert
            type="info"
            :closable="false"
            show-icon
            style="margin-bottom: 12px"
          >
            知识条目在「RAG 知识库」页维护。启用后 Agent 会在每轮对话注入 TOP-K 条命中知识给 LLM 参考。
          </el-alert>

          <ConfigField :cfg="cfgMap['agent.rag.enabled']">
            <template #hint>0=关闭；1=开启（开启前请先在「RAG 知识库」添加至少 1 条条目）</template>
          </ConfigField>
          <ConfigField :cfg="cfgMap['agent.rag.top_k']">
            <template #hint>每次召回的知识条目数量上限（建议 3~5）</template>
          </ConfigField>
          <ConfigField :cfg="cfgMap['agent.rag.use_embedding']">
            <template #hint>
              0=关键词匹配（速度快、零成本，效果一般）；
              1=向量余弦相似度（效果好，需先在「RAG 知识库」点「重建向量」）
            </template>
          </ConfigField>
          <ConfigField :cfg="cfgMap['agent.rag.min_similarity']" placeholder="0.3">
            <template #hint>向量召回的最低相似度阈值（0~1），过滤掉无关条目</template>
          </ConfigField>

          <el-divider content-position="left">Embedding 向量化服务</el-divider>

          <el-alert
            type="warning"
            :closable="false"
            show-icon
            style="margin-bottom: 12px"
          >
            DeepSeek / Kimi 等聊天模型 <strong>不提供 embedding 接口</strong>，需单独配置：
            推荐 OpenAI（<code>text-embedding-3-small</code>，1536 维，最便宜）或自部署 BGE / m3e。
          </el-alert>

          <ConfigField :cfg="cfgMap['agent.embedding.base_url']" placeholder="https://api.openai.com/v1">
            <template #hint>留空则复用 agent.llm.base_url</template>
          </ConfigField>
          <ConfigField :cfg="cfgMap['agent.embedding.api_key']">
            <template #hint>留空则复用 agent.llm.api_key（多数情况两者不同）</template>
          </ConfigField>
          <ConfigField :cfg="cfgMap['agent.embedding.model']" placeholder="text-embedding-3-small">
            <template #hint>OpenAI 可选：text-embedding-3-small / text-embedding-3-large</template>
          </ConfigField>
        </el-card>
      </el-tab-pane>

      <!-- 引擎状态 -->
      <el-tab-pane name="engine">
        <template #label>
          <span><el-icon><Monitor /></el-icon> 引擎状态</span>
        </template>
        <el-card shadow="never">
          <template #header>
            <div class="card-hd">
              <span>Agent 引擎状态</span>
              <el-button size="small" :loading="checkingHealth" @click="onCheckHealth">
                <el-icon><Refresh /></el-icon> 刷新状态
              </el-button>
            </div>
          </template>

          <el-descriptions :column="2" border>
            <el-descriptions-item label="当前引擎">
              <el-tag :type="engineStatus.activeEngine?.includes('python') ? 'success' : 'info'" size="large">
                {{ engineStatus.activeEngine || '加载中...' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="配置值">
              <el-tag type="info">{{ engineStatus.configuredEngine || 'java' }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="Python 服务健康">
              <el-tag :type="engineStatus.pythonHealthy ? 'success' : 'danger'">
                {{ engineStatus.pythonHealthy ? '健康' : '不可用' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="最后检查时间">
              {{ engineStatus.pythonHealthDetails?.lastCheck || '-' }}
            </el-descriptions-item>
          </el-descriptions>

          <el-divider content-position="left">健康检查详情</el-divider>

          <div v-if="engineStatus.pythonHealthDetails?.checks">
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item
                v-for="(val, key) in engineStatus.pythonHealthDetails.checks"
                :key="key"
                :label="key"
              >
                <el-tag :type="val ? 'success' : 'danger'" size="small">
                  {{ val ? '正常' : '异常' }}
                </el-tag>
              </el-descriptions-item>
            </el-descriptions>
          </div>
          <el-empty v-else description="暂无健康检查数据" :image-size="60" />

          <el-divider content-position="left">切换引擎</el-divider>
          <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 12px">
            切换引擎后，所有新的对话将使用新引擎处理。正在中的对话不受影响。
          </el-alert>
          <ConfigField :cfg="cfgMap['agent.engine']" placeholder="java" />
          <ConfigField :cfg="cfgMap['agent.python.url']" placeholder="http://localhost:8000" />
          <ConfigField :cfg="cfgMap['agent.python.api_key']" />
          <ConfigField :cfg="cfgMap['agent.python.timeout_ms']" placeholder="60000" />
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 测试结果 dialog -->
    <el-dialog v-model="testDialogVisible" :title="testResult.title" width="720px" top="6vh">
      <el-result
        :icon="testResult.ok ? 'success' : 'error'"
        :title="testResult.ok ? '连通正常' : '连通失败'"
        :sub-title="testResult.subtitle"
      >
        <template #extra>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="耗时">{{ testResult.latencyMs }} ms</el-descriptions-item>
            <template v-if="testResult.ok && !testResult.detail?.probes">
              <el-descriptions-item v-if="testResult.detail?.model" label="模型">
                {{ testResult.detail.model }}
              </el-descriptions-item>
              <el-descriptions-item v-if="testResult.detail?.reply" label="回复">
                <code>{{ testResult.detail.reply }}</code>
              </el-descriptions-item>
              <el-descriptions-item v-if="testResult.detail?.tokensIn != null" label="Token">
                in {{ testResult.detail.tokensIn }} / out {{ testResult.detail.tokensOut }}
              </el-descriptions-item>
            </template>
            <template v-if="!testResult.ok && !testResult.detail?.probes">
              <el-descriptions-item label="错误">
                <span style="color:#F56C6C; word-break: break-all">{{ testResult.error }}</span>
              </el-descriptions-item>
            </template>
          </el-descriptions>

          <!-- 诊断信息 -->
          <div v-if="testResult.detail?.diag" class="diag-block">
            <div class="diag-title">诊断信息</div>
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="DB 中存储的前缀">
                <code>{{ testResult.detail.diag.storedPrefix }}</code>
                <el-tag v-if="testResult.detail.diag.isEncrypted" type="success" size="small" style="margin-left:8px">已加密</el-tag>
                <el-tag v-else type="warning" size="small" style="margin-left:8px">明文</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="解密后 Key">
                <code>{{ testResult.detail.diag.decryptedKeyMask }}</code>
                <span style="margin-left:8px; color:#909399">长度 {{ testResult.detail.diag.decryptedKeyLength }}</span>
              </el-descriptions-item>
              <el-descriptions-item v-if="testResult.detail.diag.diagError" label="诊断异常">
                <span style="color:#F56C6C">{{ testResult.detail.diag.diagError }}</span>
              </el-descriptions-item>
            </el-descriptions>
          </div>

          <!-- 探针逐项结果（高德测试） -->
          <div v-if="testResult.detail?.probes?.length" class="diag-block">
            <div class="diag-title">探针逐项结果</div>
            <div
              v-for="(p, i) in testResult.detail.probes"
              :key="i"
              class="probe-row"
              :class="{ ok: p.ok, fail: !p.ok }"
            >
              <div class="probe-header">
                <el-tag :type="p.ok ? 'success' : 'danger'" size="small">
                  {{ p.ok ? 'OK' : 'FAIL' }}
                </el-tag>
                <span class="probe-name">{{ p.name }}</span>
                <span class="probe-latency">{{ p.latencyMs }} ms</span>
              </div>
              <div v-if="p.ok" class="probe-body">
                <code>{{ JSON.stringify(p.data) }}</code>
              </div>
              <div v-else class="probe-body fail-body">
                <div v-if="p.infocode">
                  <strong>infocode:</strong> <code>{{ p.infocode }}</code>
                  <strong style="margin-left: 12px">info:</strong> <code>{{ p.info }}</code>
                </div>
                <div v-if="p.error">{{ p.error }}</div>
                <details v-if="p.rawResponse">
                  <summary>高德原始响应（截断 300 字符）</summary>
                  <pre>{{ p.rawResponse }}</pre>
                </details>
              </div>
            </div>
          </div>

          <!-- 诊断建议 -->
          <el-alert
            v-if="testResult.detail?.suggest"
            type="warning"
            show-icon
            :closable="false"
            style="margin-top: 12px; text-align: left"
          >
            <template #title>诊断建议</template>
            <div style="font-size: 13px; line-height: 1.6">{{ testResult.detail.suggest }}</div>
          </el-alert>
        </template>
      </el-result>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, h } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ChatLineRound, MapLocation, DataLine, EditPen, Microphone,
  Promotion, Collection, Cpu, Check, Refresh, Plus, Connection, Lock, Monitor
} from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { getAgentConfigs, updateAgentConfigs, testLlm, testAmap, getEngineStatus, checkPythonHealth } from '@/api/agentConfig'
import ConfigField from './components/AgentConfigField.vue'

const userStore = useUserStore()
const loading = ref(false)
const saving = ref(false)
const activeTab = ref('llm')

const allConfigs = ref([])
const cfgMap = computed(() => {
  const m = {}
  for (const c of allConfigs.value) m[c.configKey] = c
  return m
})

// 数组型 JSON 配置的本地编辑态
const welcomeChipsList = ref([])
const newChip = ref('')
const placeholdersList = ref([])
const newPlaceholder = ref('')
const forbiddenWordsList = ref([])
const newForbiddenWord = ref('')

// 测试结果
const testDialogVisible = ref(false)
const testingLlm = ref(false)
const testingAmap = ref(false)
const testResult = ref({ title: '', ok: false, subtitle: '', detail: null, error: '', latencyMs: 0 })

// 引擎状态
const checkingHealth = ref(false)
const engineStatus = ref({
  activeEngine: '',
  configuredEngine: 'java',
  pythonHealthy: false,
  pythonHealthDetails: {},
})

async function fetchConfigs() {
  loading.value = true
  try {
    const res = await getAgentConfigs()
    if (res.code === 200) {
      allConfigs.value = (res.data || []).map(c => ({ ...c }))
      welcomeChipsList.value = parseJsonArray(cfgMap.value['agent.welcome_chips']?.configValue)
      placeholdersList.value = parseJsonArray(cfgMap.value['agent.input.placeholders']?.configValue)
      forbiddenWordsList.value = parseJsonArray(cfgMap.value['agent.security.forbidden_words']?.configValue)
    }
  } finally {
    loading.value = false
  }
}

function parseJsonArray(s) {
  if (!s) return []
  try {
    const v = JSON.parse(s)
    return Array.isArray(v) ? v : []
  } catch { return [] }
}

function addWelcomeChip() {
  const t = newChip.value.trim()
  if (!t) return
  if (welcomeChipsList.value.includes(t)) {
    ElMessage.warning('已存在相同词条')
    return
  }
  welcomeChipsList.value.push(t)
  newChip.value = ''
}
function removeWelcomeChip(i) {
  welcomeChipsList.value.splice(i, 1)
}
function addPlaceholder() {
  const t = newPlaceholder.value.trim()
  if (!t) return
  if (placeholdersList.value.includes(t)) {
    ElMessage.warning('已存在相同 placeholder')
    return
  }
  placeholdersList.value.push(t)
  newPlaceholder.value = ''
}
function removePlaceholder(i) {
  placeholdersList.value.splice(i, 1)
}

function addForbiddenWord() {
  const t = newForbiddenWord.value.trim()
  if (!t) return
  if (forbiddenWordsList.value.includes(t)) {
    ElMessage.warning('已存在该敏感词')
    return
  }
  forbiddenWordsList.value.push(t)
  newForbiddenWord.value = ''
}
function removeForbiddenWord(i) {
  forbiddenWordsList.value.splice(i, 1)
}

async function handleSave() {
  saving.value = true
  try {
    // 同步数组到对应配置项的 JSON 字符串
    if (cfgMap.value['agent.welcome_chips']) {
      cfgMap.value['agent.welcome_chips'].configValue = JSON.stringify(welcomeChipsList.value)
    }
    if (cfgMap.value['agent.input.placeholders']) {
      cfgMap.value['agent.input.placeholders'].configValue = JSON.stringify(placeholdersList.value)
    }
    if (cfgMap.value['agent.security.forbidden_words']) {
      cfgMap.value['agent.security.forbidden_words'].configValue = JSON.stringify(forbiddenWordsList.value)
    }

    // secret 类型空值不提交（保留原密文）
    const payload = allConfigs.value.filter(c => {
      if (c.configType === 'secret') {
        const v = String(c.configValue || '').trim()
        // 后端返回的是 sk-****abcd 掩码或 (解密失败...) 占位
        // 这些情况都不应该被当作新值提交
        if (!v || v.includes('****') || v.includes('解密失败')) return false
      }
      return true
    }).map(c => ({
      configKey: c.configKey,
      configValue: String(c.configValue ?? ''),
      description: c.description
    }))

    await updateAgentConfigs(payload)
    ElMessage.success('保存成功')
    await fetchConfigs()
  } catch (e) {
    // request.js 已经弹了 ElMessage
  } finally {
    saving.value = false
  }
}

async function onTestLlm() {
  testingLlm.value = true
  try {
    const res = await testLlm()
    const d = res.data || {}
    testResult.value = {
      title: 'LLM 连通性测试',
      ok: !!d.ok,
      subtitle: d.ok ? '模型应答正常' : '调用失败，请检查 base_url / api_key / 网络',
      detail: d,
      error: d.error || '',
      latencyMs: d.latencyMs || 0
    }
    testDialogVisible.value = true
  } finally {
    testingLlm.value = false
  }
}

async function onTestAmap() {
  testingAmap.value = true
  try {
    const res = await testAmap()
    const d = res.data || {}
    testResult.value = {
      title: '高德地图连通性测试',
      ok: !!d.ok,
      subtitle: d.ok ? '地理编码正常' : '调用失败，请检查 api_key 或服务开通情况',
      detail: d,
      error: d.error || '',
      latencyMs: d.latencyMs || 0
    }
    testDialogVisible.value = true
  } finally {
    testingAmap.value = false
  }
}

async function fetchEngineStatus() {
  try {
    const res = await getEngineStatus()
    if (res.code === 200) {
      engineStatus.value = res.data || {}
    }
  } catch {}
}

async function onCheckHealth() {
  checkingHealth.value = true
  try {
    await checkPythonHealth()
    await fetchEngineStatus()
    ElMessage.success('健康检查完成')
  } catch {
    ElMessage.error('健康检查失败')
  } finally {
    checkingHealth.value = false
  }
}

onMounted(() => {
  fetchConfigs()
  fetchEngineStatus()
})
</script>

<style scoped>
.agent-config-page {
  padding: 4px 0;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.config-tabs {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.card-hd {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  font-size: 14px;
}

.chip-edit-wrap {
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;
}

.kv-label {
  margin-bottom: 8px;
}

.kv-label-text {
  font-size: 14px;
  color: #303133;
  font-weight: 600;
  margin-right: 8px;
}

.kv-label-hint {
  font-size: 11px;
  color: #bbb;
  font-family: monospace;
}

.chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

code {
  background: #f5f7fa;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 12px;
  color: #d63384;
}

.diag-block {
  margin-top: 16px;
  text-align: left;
}

.diag-title {
  font-size: 13px;
  font-weight: 600;
  color: #1a1a2e;
  margin-bottom: 8px;
  border-left: 3px solid #1a73e8;
  padding-left: 8px;
}

.probe-row {
  padding: 10px 12px;
  border-radius: 8px;
  margin-bottom: 8px;
  background: #fafbfc;
  border: 1px solid #f0f0f0;
}

.probe-row.fail {
  background: #fef0f0;
  border-color: #fbc4c4;
}

.probe-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.probe-name {
  font-size: 13px;
  font-weight: 600;
  flex: 1;
}

.probe-latency {
  font-size: 11px;
  color: #909399;
  font-family: monospace;
}

.probe-body {
  font-size: 12px;
  color: #606266;
  padding-left: 8px;
}

.probe-body.fail-body {
  color: #c5221f;
}

.probe-body details {
  margin-top: 6px;
}

.probe-body summary {
  cursor: pointer;
  color: #1a73e8;
}

.probe-body pre {
  margin-top: 6px;
  padding: 8px;
  background: #fff;
  border-radius: 4px;
  font-size: 11px;
  max-height: 160px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
