import hashlib

from bleak import BleakClient

from .constants import CHAR_AUTH_PASSWORD, CHAR_EXTENDED_READ


def compute_password(mac: str) -> bytes:
    salt = bytes([0x4F, 0xC2, 0x45, 0x4D, 0x9B, 0x81, 0x59, 0xA4, 0x93, 0xBB])
    mac_bytes = bytes.fromhex(mac.replace(":", ""))
    buf = mac_bytes + salt
    return hashlib.md5(buf).digest()


def chunk_payload(data: bytes):
    if not data:
        return [b"\x10"]
    total = max(1, (len(data) + 18) // 19)
    frames = []
    for idx in range(total):
        chunk = data[idx * 19 : idx * 19 + 19]
        header = ((idx + 1) << 4 & 0xF0) | (total & 0x0F)
        frames.append(bytes([header]) + chunk)
    return frames


async def read_extended_char(client: BleakClient, first_uuid: str, ext_uuid: str = CHAR_EXTENDED_READ):
    first = await client.read_gatt_char(first_uuid)
    if not first:
        return b""
    header = first[0]
    total_frames = header & 0x0F or 1
    frames = [first]
    for _ in range(total_frames - 1):
        frames.append(await client.read_gatt_char(ext_uuid))
    merged = bytearray()
    for frame in frames:
        merged.extend(frame[1:])
    return bytes(merged)


def maybe_decrypt(label: str, data: bytes, validator, cryptor, warnings: list):
    if validator(data):
        return data
    if not cryptor:
        return data
    try:
        plain = cryptor.decrypt(data)
        if validator(plain):
            return plain
        warnings.append(f"{label}: decrypted payload failed validation")
    except Exception as exc:
        warnings.append(f"{label}: decrypt error {exc}")
    return data

