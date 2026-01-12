def is_glb_safe_var(buf: bytes) -> bool:
    return len(buf) >= 8 and (
        (buf[7] ^ 0xFF) & 0xFF == buf[3]
        and (buf[6] ^ 0xFF) & 0xFF == buf[2]
        and (buf[5] ^ 0xFF) & 0xFF == buf[1]
        and (buf[4] ^ 0xFF) & 0xFF == buf[0]
    )


def decode_glb_safe_var(buf: bytes) -> int:
    if not is_glb_safe_var(buf):
        raise ValueError("Invalid GLB_SAFE_VAR")
    return int.from_bytes(buf[0:4], "little")


def create_glb_safe_var(value: int) -> bytes:
    buf = bytearray(8)
    buf[0] = value & 0xFF
    buf[1] = (value >> 8) & 0xFF
    buf[2] = (value >> 16) & 0xFF
    buf[3] = (value >> 24) & 0xFF
    buf[4] = (~buf[0]) & 0xFF
    buf[5] = (~buf[1]) & 0xFF
    buf[6] = (~buf[2]) & 0xFF
    buf[7] = (~buf[3]) & 0xFF
    return bytes(buf)

