"""
TransitMap Python Service — 统一异常处理
"""


class TmapException(Exception):
    """基础异常"""
    def __init__(self, message: str, code: str = "INTERNAL_ERROR", status_code: int = 500):
        self.message = message
        self.code = code
        self.status_code = status_code
        super().__init__(message)


class LlmGatewayError(TmapException):
    """LLM 网关错误"""
    def __init__(self, message: str, provider: str = ""):
        self.provider = provider
        super().__init__(message, code="LLM_ERROR", status_code=502)


class LlmRateLimitError(LlmGatewayError):
    """LLM 限流"""
    def __init__(self, provider: str = ""):
        super().__init__(f"LLM 供应商 {provider} 限流", provider=provider)
        self.code = "LLM_RATE_LIMIT"
        self.status_code = 429


class JavaApiError(TmapException):
    """Java 内部 API 错误"""
    def __init__(self, message: str, endpoint: str = ""):
        self.endpoint = endpoint
        super().__init__(message, code="JAVA_API_ERROR", status_code=502)


class CrawlerError(TmapException):
    """爬虫错误"""
    def __init__(self, message: str):
        super().__init__(message, code="CRAWLER_ERROR", status_code=500)


class CrawlerSourceError(CrawlerError):
    """数据源爬取失败"""
    def __init__(self, source: str, message: str):
        self.source = source
        super().__init__(f"[{source}] {message}")


class AuthenticationError(TmapException):
    """认证失败"""
    def __init__(self, message: str = "认证失败"):
        super().__init__(message, code="AUTH_ERROR", status_code=401)


class ForbiddenError(TmapException):
    """权限不足"""
    def __init__(self, message: str = "权限不足"):
        super().__init__(message, code="FORBIDDEN", status_code=403)
