from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    api_key: str
    host: str = "0.0.0.0"
    port: int = 8000
    log_level: str = "info"

    model_config = {"env_file": ".env", "env_prefix": "YPSO_"}


settings = Settings()
