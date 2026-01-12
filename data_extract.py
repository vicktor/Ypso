import argparse
import asyncio
import json
import sys
from pathlib import Path

from bleak.exc import BleakError

from pump.cache import PumpCacheError
from pump.lock import lock_counters
from pump.sdk import extract_snapshot, load_session


async def run_extract(args):
    data, pump, cryptor = load_session(
        allow_expired=args.force,
        allow_plain=args.no_crypto,
        reset_counters=args.reset_counters,
        set_write_counter=args.set_write_counter,
    )
    result = await extract_snapshot(data, pump, cryptor, limit=args.limit)
    rendered = json.dumps(result, indent=2)
    if args.output:
        Path(args.output).write_text(rendered)
        print(f"Saved data to {args.output}")
    else:
        print(rendered)


def main():
    parser = argparse.ArgumentParser(description="Extract YpsoPump history using cached pairing data.")
    parser.add_argument("--limit", type=int, help="Maximum entries per category")
    parser.add_argument("--output", help="Write JSON output to this file")
    parser.add_argument("--force", action="store_true", help="Ignore cached key expiry (use with caution)")
    parser.add_argument("--reset-counters", action="store_true", help="Reset cached read/write/reboot counters before connecting")
    parser.add_argument("--set-write-counter", type=int, help="Force cached write counter before connecting")
    parser.add_argument("--no-crypto", action="store_true", help="Ignore cached shared key (force plain reads/writes)")
    args = parser.parse_args()
    try:
        with lock_counters():
            asyncio.run(run_extract(args))
    except PumpCacheError as exc:
        print(f"Cache error: {exc}")
        sys.exit(1)
    except BleakError as exc:
        print(f"BLE error: {exc}")
        sys.exit(2)
    except Exception as exc:
        print(f"Error: {exc}")
        sys.exit(3)


if __name__ == "__main__":
    main()
