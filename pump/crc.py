CRC_POLY = 0x04C11DB7
CRC_TABLE = [0] * 256
for idx in range(256):
    val = idx << 24
    for _ in range(8):
        if val & 0x80000000:
            val = ((val << 1) & 0xFFFFFFFF) ^ CRC_POLY
        else:
            val = (val << 1) & 0xFFFFFFFF
    CRC_TABLE[idx] = val


def _bitstuff(data: bytes) -> bytes:
    if not data:
        return b""
    block_count = (len(data) + 3) // 4
    stuffed = bytearray(block_count * 4)
    for block in range(block_count):
        base = block * 4
        for idx in range(4):
            src = base + idx
            stuffed[base + 3 - idx] = data[src] if src < len(data) else 0
    return bytes(stuffed)


def crc16(payload: bytes) -> bytes:
    crc = 0xFFFFFFFF
    for byte in _bitstuff(payload):
        table_idx = ((crc >> 24) ^ byte) & 0xFF
        crc = ((crc << 8) & 0xFFFFFFFF) ^ CRC_TABLE[table_idx]
    return (crc & 0xFFFF).to_bytes(2, "little")


def crc16_valid(payload: bytes) -> bool:
    if len(payload) < 2:
        return False
    data, crc_bytes = payload[:-2], payload[-2:]
    calc = crc16(data)
    return calc == crc_bytes

