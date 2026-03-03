# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Reverse-engineering toolkit for communicating with the **YpsoPump** insulin pump over Bluetooth Low Energy (BLE). Replicates the protocol used by the official **mylife app** (`net.sinovo.mylife.app`) to pair, send bolus commands, read status/basal profiles, extract history, and watch real-time notifications. The goal is integration with the **CamAPS FX** closed-loop artificial pancreas system.

## Commands

```bash
# Install dependencies
pip install -r requirements.txt

# One-time pairing (requires Android device with mylife app via USB + adb)
python pairing.py

# Main CLI - all pump operations
python pumpcli.py <command> [args]
# Commands: start, stop, status, basal, events, alerts, system, notify, extract
# Global flags: --force (ignore key expiry), --no-crypto (skip encryption)

# Standalone data extraction (subset of pumpcli extract)
python data_extract.py

# Play Integrity token extraction (called by pairing.py, rarely run standalone)
python play_integrity.py
```

No test suite, linter, or CI/CD pipeline exists.

## Architecture

### Data Flow

1. **Pairing** (`pairing.py`): BLE scan → discover pump → gRPC to Ypsomed cloud (nonce + key exchange) → X25519 ECDH → derive shared key → persist to `ypsopump_keys.json` (valid 28 days)
2. **Operations** (`pumpcli.py` → `pump/sdk.py`): Load cached session → BLE connect → authenticate with MD5 password → encrypt/decrypt commands via XChaCha20-Poly1305

### Core Library (`pump/`)

| Module | Role |
|--------|------|
| `sdk.py` | High-level async BLE API: session management, bolus, status, basal, history, notifications |
| `crypto.py` | XChaCha20-Poly1305 encryption/decryption with counter tracking |
| `cache.py` | Session persistence to/from `ypsopump_keys.json` |
| `constants.py` | BLE characteristic UUIDs and event name mappings |
| `crc.py` | Custom CRC16 (polynomial 0x04C11DB7) with bitstuffing |
| `entries.py` | History entry binary parser |
| `glb.py` | GLB_SAFE_VAR encoding (8-byte redundant format for pump variable storage) |
| `lock.py` | File-based mutex (`fcntl.flock`) for counter safety |
| `utils.py` | BLE framing helpers (20-byte chunking) and password computation |

### Protocol (`proto/`)

gRPC stubs generated from `encrypt_key.proto` and `nonce.proto`, consolidated into `keyexchange_pb2.py` / `keyexchange_pb2_grpc.py`. The gRPC stub imports `keyexchange_pb2` without package prefix — must be importable from the proto directory context.

### Key Protocol Details

- **BLE framing**: 20-byte frames; header byte = `(frame_index << 4) | total_frames`. Multi-frame reads use `CHAR_EXTENDED_READ`.
- **Crypto**: X25519 ECDH → HChaCha20 key derivation → XChaCha20-Poly1305 (AEAD). Nonce: 24 bytes random, with 12-byte plaintext suffix `[reboot_counter(4) || write_counter(8)]`.
- **Authentication**: MD5(MAC_bytes + fixed 10-byte salt)
- **GLB_SAFE_VAR**: bytes 0-3 = LE value, bytes 4-7 = bitwise NOT of 0-3

### Device Spoofing (`device_profiles.py`)

The Ypsomed server requires device metrics matching the official Android app. Contains profiles for Samsung, Pixel, OnePlus, Xiaomi devices. Active profile set by `CURRENT_DEVICE` variable.

### Session State (`ypsopump_keys.json`)

Stores private key, device UUID, pump serial/MAC/BLE address, shared key + expiry, counters (read/write/reboot). Protected by `ypsopump_keys.lock`.

## Runtime Requirements

- Python 3.10+
- `bleak`, `PyNaCl`, `grpcio` (in requirements.txt)
- For pairing: `frida` Python package, `adb` in PATH, Android device with mylife app connected via USB
- Frida script: `scripts/integrity_token.ts` (hooks Play Integrity API)
