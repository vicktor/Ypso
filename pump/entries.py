from .constants import EVENT_NAMES


def parse_entry(payload: bytes) -> dict:
    from datetime import datetime, timedelta

    timestamp = datetime(2000, 1, 1) + timedelta(seconds=get_char_value(payload, 0, 4))
    entry_type = payload[4]
    value1 = get_char_value(payload, 5, 2)
    value2 = get_char_value(payload, 7, 2)
    value3 = get_char_value(payload, 9, 2)
    sequence = get_char_value(payload, 11, 4)
    index = get_char_value(payload, 15, 2)
    return {
        "timestamp": timestamp.isoformat(),
        "type_id": entry_type,
        "value1": value1,
        "value2": value2,
        "value3": value3,
        "sequence": sequence,
        "index": index,
    }


def get_char_value(buf: bytes, start: int, length: int) -> int:
    value = 0
    for i in range(length - 1, -1, -1):
        value = (value << 8) | buf[start + i]
    return value


def type_name(type_id: int) -> str:
    for name, value in EVENT_NAMES.items():
        if value == type_id:
            return name
    return f"TYPE_{type_id}"

