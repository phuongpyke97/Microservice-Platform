"""
Pre-download spleeter model during Docker build.

Applies the httpx redirect fix inline, then uses spleeter's own
ModelProvider to download + verify + extract the model.
This avoids runtime download issues with GitHub's 302 redirects.
"""
import httpx

# --- Monkeypatch httpx to follow redirects ---
_orig_client_init = httpx.Client.__init__

def _patched_client_init(self, *args, **kwargs):
    kwargs.setdefault("follow_redirects", True)
    _orig_client_init(self, *args, **kwargs)

httpx.Client.__init__ = _patched_client_init

_orig_httpx_get = httpx.get

def _patched_httpx_get(*args, **kwargs):
    kwargs.setdefault("follow_redirects", True)
    return _orig_httpx_get(*args, **kwargs)

httpx.get = _patched_httpx_get

# --- Download model using spleeter's own logic ---
from spleeter.model.provider import ModelProvider  # noqa: E402

provider = ModelProvider.default()
model_dir = provider.get("2stems")
print(f"Spleeter 2stems model pre-downloaded to: {model_dir}")
