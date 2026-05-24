"""Run this once after clone / proto changes to generate gRPC stubs."""
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

subprocess.check_call([
    sys.executable, "-m", "grpc_tools.protoc",
    f"-I{ROOT / 'proto'}",
    f"--python_out={ROOT / 'generated'}",
    f"--grpc_python_out={ROOT / 'generated'}",
    str(ROOT / "proto" / "ai_media.proto"),
])

# grpc_tools generates absolute imports (e.g. `import ai_media_pb2`).
# Fix to relative so generated/ package imports resolve correctly.
grpc_file = ROOT / "generated" / "ai_media_pb2_grpc.py"
content = grpc_file.read_text()
content = re.sub(r"^import (\w+_pb2)", r"from . import \1", content, flags=re.MULTILINE)
grpc_file.write_text(content)

print("Stubs generated in generated/")
