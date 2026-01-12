import json
import time
from pathlib import Path

KEY_FILE = Path("ypsopump_keys.json")


class PumpCacheError(RuntimeError):
    pass


def load_cache(allow_expired: bool = False):
    if not KEY_FILE.exists():
        raise PumpCacheError("Cache file missing. Run pairing first.")
    data = json.loads(KEY_FILE.read_text())
    pump = data.get("pump") or {}
    required = ["serial", "mac", "bt_address", "ble_address", "shared_key", "shared_key_expires_at"]
    missing = [key for key in required if not pump.get(key)]
    if missing:
        raise PumpCacheError(f"Cache missing fields: {', '.join(missing)}")
    if pump["shared_key_expires_at"] <= time.time() and not allow_expired:
        raise PumpCacheError("Cached shared key expired. Run pairing again or pass --force.")
    return data, pump


def save_cache(data):
    KEY_FILE.write_text(json.dumps(data))


def reset_cached_counters(data):
    pump = data.setdefault("pump", {})
    pump["read_counter"] = 0
    pump["write_counter"] = 0
    pump["reboot_counter"] = 0
    save_cache(data)


def set_cached_write_counter(data, counter: int):
    pump = data.setdefault("pump", {})
    pump["write_counter"] = counter
    save_cache(data)
