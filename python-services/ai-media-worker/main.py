import asyncio
import uvicorn
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.config import settings
from app.grpc_server import serve_grpc
from app.routers.ai_media import router as ai_media_router


class GrpcServerManager:
    def __init__(self):
        self.server = None
        self.task = None

    async def start(self):
        self.server = await serve_grpc(settings.grpc_port)
        self.task = asyncio.create_task(self.server.wait_for_termination())

    async def stop(self):
        if self.server:
            await self.server.stop(grace=5)
        if self.task:
            self.task.cancel()
            try:
                await self.task
            except asyncio.CancelledError:
                pass

grpc_manager = GrpcServerManager()


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup
    print(f"Starting gRPC server on port {settings.grpc_port}...")
    await grpc_manager.start()
    print("gRPC server started.")
    yield
    # Shutdown
    print("Shutting down gRPC server...")
    await grpc_manager.stop()
    print("gRPC server shut down.")


app = FastAPI(lifespan=lifespan)
app.include_router(ai_media_router)


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=settings.http_port)
