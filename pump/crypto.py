import os

from nacl.bindings import (
    crypto_aead_xchacha20poly1305_ietf_decrypt,
    crypto_aead_xchacha20poly1305_ietf_encrypt,
)

from .cache import save_cache


class PumpCryptor:
    def __init__(self, shared_key: bytes, data_root: dict):
        if len(shared_key) != 32:
            raise ValueError("Shared key must be 32 bytes")
        self.shared_key = shared_key
        self.data_root = data_root
        self.pump_section = data_root.setdefault("pump", {})
        self.read_counter = self.pump_section.get("read_counter", 0) or 0
        self.write_counter = self.pump_section.get("write_counter", 0) or 0
        self.reboot_counter = self.pump_section.get("reboot_counter", 0) or 0

    def _persist(self):
        self.pump_section["read_counter"] = self.read_counter
        self.pump_section["write_counter"] = self.write_counter
        self.pump_section["reboot_counter"] = self.reboot_counter
        save_cache(self.data_root)

    def decrypt(self, payload: bytes) -> bytes:
        if len(payload) < 24 + 16:
            raise ValueError("Encrypted payload too short")
        nonce = payload[-24:]
        ciphertext = payload[:-24]
        plaintext = crypto_aead_xchacha20poly1305_ietf_decrypt(ciphertext, b"", nonce, self.shared_key)
        data = plaintext[:-12]
        reboot_counter = int.from_bytes(plaintext[-12:-8], "little")
        numeric_counter = int.from_bytes(plaintext[-8:], "little")
        if reboot_counter != self.reboot_counter:
            self.reboot_counter = reboot_counter
            self.write_counter = 0
        self.read_counter = numeric_counter
        self._persist()
        return data

    def encrypt(self, payload: bytes) -> bytes:
        nonce = os.urandom(24)
        buffer = bytearray(payload)
        buffer.extend(self.reboot_counter.to_bytes(4, "little"))
        self.write_counter += 1
        buffer.extend(self.write_counter.to_bytes(8, "little"))
        ciphertext = crypto_aead_xchacha20poly1305_ietf_encrypt(bytes(buffer), b"", nonce, self.shared_key)
        self._persist()
        return ciphertext + nonce

