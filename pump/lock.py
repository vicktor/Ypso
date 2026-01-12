import contextlib
import fcntl
from pathlib import Path

LOCK_FILE = Path("ypsopump_keys.lock")


@contextlib.contextmanager
def lock_counters():
    LOCK_FILE.touch(exist_ok=True)
    with open(LOCK_FILE, "r+") as fh:
        fcntl.flock(fh, fcntl.LOCK_EX)
        try:
            yield
        finally:
            fcntl.flock(fh, fcntl.LOCK_UN)
