"""
TransitMap Python Service — 配置管理

所有配置通过环境变量或 .env 文件加载。
LLM 供应商配置支持多个，通过 LLM_PROVIDER_N_* 格式定义。
"""

import os
from dataclasses import dataclass, field
from dotenv import load_dotenv

load_dotenv()


@dataclass
class LlmProviderConfig:
    """单个 LLM 供应商配置"""
    name: str
    base_url: str
    api_key: str
    model: str
    is_default: bool = False
    max_tokens: int = 1024
    temperature: float = 0.3
    timeout_ms: int = 30000


@dataclass
class Settings:
    """全局配置"""

    # ── 服务 ──
    SERVICE_PORT: int = int(os.getenv("PYTHON_SERVICE_PORT", "8000"))
    SERVICE_HOST: str = os.getenv("PYTHON_SERVICE_HOST", "0.0.0.0")
    SERVICE_API_KEY: str = os.getenv("PYTHON_SERVICE_API_KEY", "")
    DEBUG: bool = os.getenv("DEBUG", "false").lower() == "true"

    # ── 数据库（与 Java 共享 MySQL） ──
    DATABASE_URL: str = os.getenv(
        "DATABASE_URL",
        "mysql+pymysql://root:tmap123@localhost:3306/transit_map"
    )
    DB_POOL_SIZE: int = int(os.getenv("DB_POOL_SIZE", "10"))
    DB_MAX_OVERFLOW: int = int(os.getenv("DB_MAX_OVERFLOW", "20"))
    DB_POOL_RECYCLE: int = int(os.getenv("DB_POOL_RECYCLE", "3600"))

    # ── Redis ──
    REDIS_URL: str = os.getenv("REDIS_URL", "redis://localhost:6379/0")

    # ── Java 内部 API ──
    JAVA_INTERNAL_API_URL: str = os.getenv(
        "JAVA_INTERNAL_API_URL",
        "http://localhost:8888/transitMap/api/internal"
    )
    JAVA_INTERNAL_API_KEY: str = os.getenv("JAVA_INTERNAL_API_KEY", "")
    JAVA_INTERNAL_TIMEOUT: int = int(os.getenv("JAVA_INTERNAL_TIMEOUT", "10"))

    # ── 高德 API ──
    AMAP_API_KEY: str = os.getenv("AMAP_API_KEY", "")
    AMAP_BASE_URL: str = os.getenv("AMAP_BASE_URL", "https://restapi.amap.com")

    # ── 爬虫 ──
    CRAWLER_MAX_CONCURRENT: int = int(os.getenv("CRAWLER_MAX_CONCURRENT", "1"))
    CRAWLER_DEFAULT_SOURCES: str = os.getenv("CRAWLER_DEFAULT_SOURCES", "wikipedia,baike")
    CRAWLER_GEOCODE_BATCH_SIZE: int = int(os.getenv("CRAWLER_GEOCODE_BATCH_SIZE", "10"))
    CRAWLER_GEOCODE_DELAY_MS: int = int(os.getenv("CRAWLER_GEOCODE_DELAY_MS", "100"))

    # ── LLM 供应商列表（动态加载） ──
    llm_providers: list[LlmProviderConfig] = field(default_factory=list)

    def load_llm_providers(self):
        """从环境变量加载 LLM 供应商配置。格式: LLM_PROVIDER_N_*"""
        providers = []
        seen_indices = set()

        for key in os.environ:
            if key.startswith("LLM_PROVIDER_") and key.endswith("_NAME"):
                idx = key.split("_")[2]
                seen_indices.add(idx)

        for idx in sorted(seen_indices, key=int):
            prefix = f"LLM_PROVIDER_{idx}_"
            name = os.getenv(f"{prefix}NAME", "")
            base_url = os.getenv(f"{prefix}BASE_URL", "")
            api_key = os.getenv(f"{prefix}API_KEY", "")
            model = os.getenv(f"{prefix}MODEL", "")
            if not all([name, base_url, api_key, model]):
                continue
            providers.append(LlmProviderConfig(
                name=name,
                base_url=base_url,
                api_key=api_key,
                model=model,
                is_default=os.getenv(f"{prefix}IS_DEFAULT", "false").lower() == "true",
                max_tokens=int(os.getenv(f"{prefix}MAX_TOKENS", "1024")),
                temperature=float(os.getenv(f"{prefix}TEMPERATURE", "0.3")),
                timeout_ms=int(os.getenv(f"{prefix}TIMEOUT_MS", "30000")),
            ))

        self.llm_providers = providers
        return providers

    def get_default_llm_provider(self) -> LlmProviderConfig | None:
        """获取默认 LLM 供应商"""
        for p in self.llm_providers:
            if p.is_default:
                return p
        return self.llm_providers[0] if self.llm_providers else None

    def get_llm_provider(self, name: str) -> LlmProviderConfig | None:
        """按名称获取 LLM 供应商"""
        for p in self.llm_providers:
            if p.name == name:
                return p
        return None


# 全局单例
settings = Settings()
