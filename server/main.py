import subprocess
import sys
from pathlib import Path

from fastapi import FastAPI, Depends, HTTPException
from pydantic import BaseModel, Field

from .auth import require_api_key
from .config import settings

app = FastAPI(
    title="YpsoPump API",
    version="0.1.0",
    description="Play Integrity token service for YpsoPump pairing",
)

PLAY_INTEGRITY_SCRIPT = Path(__file__).resolve().parent.parent / "play_integrity.py"


class IntegrityTokenRequest(BaseModel):
    nonce: str = Field(..., description="Hex-encoded nonce from Ypsomed server")


class IntegrityTokenResponse(BaseModel):
    token: str


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post(
    "/api/v1/integrity-token",
    response_model=IntegrityTokenResponse,
    dependencies=[Depends(require_api_key)],
)
def get_integrity_token(req: IntegrityTokenRequest):
    nonce_clean = req.nonce.replace(" ", "")
    try:
        bytes.fromhex(nonce_clean)
    except ValueError:
        raise HTTPException(status_code=400, detail="nonce must be a valid hex string")

    result = subprocess.run(
        [sys.executable, str(PLAY_INTEGRITY_SCRIPT), nonce_clean],
        capture_output=True,
        text=True,
        timeout=90,
    )

    if result.returncode != 0:
        raise HTTPException(
            status_code=502,
            detail=f"Play Integrity failed: {result.stderr.strip()}",
        )

    token = result.stdout.strip()
    if not token:
        raise HTTPException(
            status_code=502,
            detail="Play Integrity returned no token",
        )

    return IntegrityTokenResponse(token=token)


def run():
    import uvicorn

    uvicorn.run(
        "server.main:app",
        host=settings.host,
        port=settings.port,
        log_level=settings.log_level,
    )


if __name__ == "__main__":
    run()
