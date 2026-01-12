#!/usr/bin/env python3
import sys
import time
import base64
import subprocess
import frida
import threading
import os

if len(sys.argv) != 2:
    print("Usage: python play_integrity.py <nonce_hex>")
    sys.exit(1)

nonce_hex = sys.argv[1]
nonce_b64 = base64.b64encode(bytes.fromhex(nonce_hex)).decode()

PACKAGE = "net.sinovo.mylife.app"
PROCESS_NAME = "mylife App"
ACTIVITY = "net.sinovo.mylife.app/crc64e9c4225bc8bdbe22.SplashScreen"
CLOUD_PROJECT = 381590650735

token_event = threading.Event()
captured_token = None
script_error = None

def on_message(message, data):
    global captured_token, script_error
    if message['type'] == 'send':
        payload = message['payload']
        if payload.get('type') == 'token':
            print("[+] Token received", file=sys.stderr)
            captured_token = payload['value']
            token_event.set()
        elif payload.get('type') == 'error':
            print(f"[!] Script error: {payload['value']}", file=sys.stderr)
            script_error = payload['value']
            token_event.set()
    elif message['type'] == 'error':
        print(f"[!] Frida error: {message.get('description', str(message))}", file=sys.stderr)
        script_error = message.get('description', str(message))
        token_event.set()

try:
    print(f"[*] Stopping {PACKAGE}...", file=sys.stderr)
    subprocess.run(['adb', 'shell', 'am', 'force-stop', PACKAGE], capture_output=True)
    time.sleep(2)

    print(f"[*] Starting {ACTIVITY}...", file=sys.stderr)
    subprocess.run(['adb', 'shell', 'am', 'start', '-n', ACTIVITY], capture_output=True)
    time.sleep(5)

    print("[*] Connecting to device...", file=sys.stderr)
    device = frida.get_usb_device()

    print(f"[*] Waiting for process '{PROCESS_NAME}'...", file=sys.stderr)
    pid = None
    for i in range(20):
        for process in device.enumerate_processes():
            if process.name == PROCESS_NAME:
                pid = process.pid
                break
        if pid:
            break
        time.sleep(1)

    if not pid:
        raise RuntimeError(f"Process '{PROCESS_NAME}' not found after 20 seconds")

    print(f"[+] Process found (PID: {pid})", file=sys.stderr)
    print(f"[*] Attaching to process...", file=sys.stderr)
    session = device.attach(pid)

    print("[*] Preparing TypeScript script...", file=sys.stderr)
    script_path = os.path.join(os.path.dirname(__file__), 'scripts', 'integrity_token.ts')
    with open(script_path, 'r') as f:
        script_content = f.read()

    script_content = script_content.replace('NONCE_PLACEHOLDER', f'"{nonce_b64}"')
    script_content = script_content.replace('PROJECT_NUM_PLACEHOLDER', str(CLOUD_PROJECT))

    dist_dir = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'dist')
    os.makedirs(dist_dir, exist_ok=True)
    temp_script = os.path.join(dist_dir, '_temp_integrity.ts')

    with open(temp_script, 'w') as f:
        f.write(script_content)

    try:
        print("[*] Compiling script with Java bridge...", file=sys.stderr)
        compiler = frida.Compiler()
        project_root = os.path.dirname(os.path.dirname(__file__))
        bundle = compiler.build(temp_script, project_root=project_root)

        print("[*] Loading compiled script...", file=sys.stderr)
        script = session.create_script(bundle)
        script.on('message', on_message)
        script.load()

        print("[*] Waiting for integrity token (timeout: 30s)...", file=sys.stderr)
        if not token_event.wait(timeout=30):
            raise RuntimeError("Timeout: No response from script after 30 seconds")

        if script_error:
            raise RuntimeError(f"Script error: {script_error}")

        if captured_token:
            print(captured_token)
        else:
            raise RuntimeError("No token captured")

    finally:
        if os.path.exists(temp_script):
            os.unlink(temp_script)

except Exception as e:
    print(f"[!] Error: {e}", file=sys.stderr)
    sys.exit(1)
finally:
    print(f"[*] Cleaning up...", file=sys.stderr)
    subprocess.run(['adb', 'shell', 'am', 'force-stop', PACKAGE], capture_output=True)