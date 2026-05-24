from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    http_port: int = Field(default=8765, validation_alias="AI_WORKER_HTTP_PORT")
    grpc_port: int = Field(default=50051, validation_alias="AI_WORKER_GRPC_PORT")
    edge_tts_rate: str = Field(default="+0%", validation_alias="EDGE_TTS_RATE")
    edge_tts_pitch: str = Field(default="+0Hz", validation_alias="EDGE_TTS_PITCH")
    spleeter_model: str = Field(default="spleeter:2stems", validation_alias="SPLEETER_MODEL")
    tmp_dir: str = Field(default="/tmp/ai-media-worker", validation_alias="AI_WORKER_TMP_DIR")

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")


settings = Settings()
