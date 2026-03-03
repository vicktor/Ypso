# Ypso-CamAPS

Toolkit de ingeniería inversa para comunicarse con la bomba de insulina **YpsoPump** a través de Bluetooth Low Energy (BLE). Replica el protocolo utilizado por la app oficial **mylife** (`net.sinovo.mylife.app`) para emparejar, enviar comandos de bolus, leer estado/perfiles basales, extraer historial y recibir notificaciones en tiempo real.

El objetivo final es la integración con el sistema de páncreas artificial de lazo cerrado **CamAPS FX**.

> **Aviso:** Este proyecto es solo para investigación y uso personal. Manipular una bomba de insulina puede ser peligroso. Úsalo bajo tu propia responsabilidad.

## Requisitos previos

### Software

| Requisito | Versión mínima | Propósito |
|-----------|----------------|-----------|
| Python | 3.10+ | Runtime principal |
| ADB | - | Comunicación con dispositivo Android (solo pairing) |
| Frida | - | Instrumentación dinámica para obtener Play Integrity token (solo pairing) |
| Frida Server | Compatible con tu versión de Frida | Debe estar corriendo en el dispositivo Android (solo pairing) |

### Hardware

- **Ordenador** con Bluetooth Low Energy (Linux o macOS)
- **Bomba YpsoPump** encendida y en modo descubrible
- **Dispositivo Android** (solo para el pairing inicial):
  - App **mylife** (`net.sinovo.mylife.app`) instalada
  - Google Play Services con Play Integrity API
  - Depuración USB habilitada
  - Conectado por USB al ordenador

## Instalación

### 1. Clonar el repositorio

```bash
git clone https://github.com/vicktor/Ypso.git
cd Ypso
```

### 2. Instalar dependencias de Python

```bash
pip install -r requirements.txt
```

Dependencias principales:

| Paquete | Propósito |
|---------|-----------|
| `bleak >= 0.21.0` | Comunicación BLE multiplataforma (asyncio) |
| `PyNaCl >= 1.5.0` | Criptografía: X25519, XChaCha20-Poly1305, HChaCha20 |
| `grpcio >= 1.60.0` | Cliente gRPC para el servidor cloud de Ypsomed |
| `grpcio-tools >= 1.60.0` | Compilación de Protocol Buffers |

### 3. Instalar Frida (solo necesario para pairing)

```bash
pip install frida frida-tools
```

Además, debes instalar **frida-server** en tu dispositivo Android:

1. Descarga la versión correspondiente a la arquitectura de tu dispositivo desde [Frida releases](https://github.com/frida/frida/releases)
2. Cópiala al dispositivo:
   ```bash
   adb push frida-server /data/local/tmp/
   adb shell chmod 755 /data/local/tmp/frida-server
   ```
3. Ejecútala como root:
   ```bash
   adb shell su -c /data/local/tmp/frida-server &
   ```

## Uso

### Paso 1: Pairing (una sola vez)

El pairing establece una clave compartida entre tu ordenador y la bomba, válida durante **28 días**.

```bash
python pairing.py
```

Este proceso:
1. Escanea por BLE buscando dispositivos con nombre `YpsoPump_<SERIAL>`
2. Establece conexión BLE y se autentica con la bomba
3. Obtiene un nonce del servidor de Ypsomed vía gRPC (`connect.ml.pr.sec01.proregia.io:8090`)
4. Lanza la app mylife en el dispositivo Android mediante ADB
5. Usa Frida para interceptar el Play Integrity token
6. Envía el token al servidor de Ypsomed para completar el intercambio de claves
7. Calcula la clave compartida mediante X25519 ECDH + HChaCha20
8. Guarda toda la sesión en `ypsopump_keys.json`

**Requisitos durante el pairing:**
- La bomba debe estar cerca y encendida
- El dispositivo Android debe estar conectado por USB con ADB
- Frida Server debe estar corriendo en el dispositivo Android
- La app mylife debe estar instalada

### Paso 2: Operaciones con la bomba

Una vez completado el pairing, puedes usar `pumpcli.py` sin necesidad del dispositivo Android.

```bash
python pumpcli.py <comando> [opciones]
```

#### Comandos disponibles

**Iniciar un bolus:**
```bash
# Bolus rápido de 0.5 UI
python pumpcli.py start --total 0.5

# Bolus extendido de 2 UI durante 30 minutos
python pumpcli.py start --total 2.0 --duration 30

# Bolus combinado: 0.5 UI inmediato + 1.5 UI extendido en 60 minutos
python pumpcli.py start --total 2.0 --immediate 0.5 --duration 60
```

**Detener un bolus activo:**
```bash
python pumpcli.py stop --type fast
python pumpcli.py stop --type extended
python pumpcli.py stop --type combined
```

**Leer estado actual:**
```bash
# Estado del bolus y del sistema (insulina restante, batería, etc.)
python pumpcli.py status
```

**Leer perfil basal:**
```bash
# Muestra programas A y B con las 24 tasas horarias
python pumpcli.py basal
```

**Extraer historial:**
```bash
# Eventos
python pumpcli.py events --limit 50 --output events.json

# Alertas
python pumpcli.py alerts --limit 50 --output alerts.json

# Sistema
python pumpcli.py system --limit 50 --output system.json
```

**Extracción completa (versiones + fecha/hora + todos los historiales):**
```bash
python pumpcli.py extract --output snapshot.json
python pumpcli.py extract --limit 100 --output snapshot.json
```

**Escuchar notificaciones en tiempo real:**
```bash
# Escuchar indefinidamente
python pumpcli.py notify

# Escuchar durante 60 segundos
python pumpcli.py notify --timeout 60
```

#### Opciones globales

| Flag | Descripción |
|------|-------------|
| `--force` | Ignora la expiración de la clave compartida (usar con precaución) |
| `--no-crypto` | Desactiva la encriptación (lectura/escritura BLE en plano) |

#### Códigos de salida

| Código | Significado |
|--------|-------------|
| 0 | Éxito |
| 1 | Error de caché (archivo faltante o inválido) |
| 2 | Error BLE (comunicación con la bomba) |
| 3 | Otro error |

### Extracción standalone

`data_extract.py` es una alternativa simplificada a `pumpcli.py extract`:

```bash
python data_extract.py --output data.json
python data_extract.py --limit 100 --output data.json --reset-counters
```

## Configuración

### Perfil de dispositivo

El servidor de Ypsomed valida las métricas del dispositivo que realiza las peticiones. En `device_profiles.py` se definen perfiles de dispositivos Android.

Perfiles disponibles: `samsung_s21`, `pixel_6a`, `pixel_7`, `oneplus_9`, `xiaomi_mi11`

Para cambiar el perfil activo, modifica la variable `CURRENT_DEVICE`:

```python
CURRENT_DEVICE = "pixel_6a"  # Perfil activo por defecto
```

### Archivo de sesión (`ypsopump_keys.json`)

Este archivo se genera automáticamente durante el pairing y contiene:
- Clave privada y ID de dispositivo
- Serial, MAC y dirección BLE de la bomba
- Clave compartida y su fecha de expiración
- Contadores de lectura/escritura/reboot

**No edites este archivo manualmente** a menos que sepas lo que haces. Los contadores se actualizan automáticamente y están protegidos por un file lock (`ypsopump_keys.lock`).

## Arquitectura

```
pairing.py              ─── Flujo de pairing completo (BLE + gRPC + Frida)
pumpcli.py              ─── CLI principal para todas las operaciones
data_extract.py         ─── CLI simplificado para extracción de datos
play_integrity.py       ─── Extracción de Play Integrity token vía Frida
device_profiles.py      ─── Perfiles de dispositivos Android para spoofing

pump/                   ─── Librería core
├── sdk.py              ─── API de alto nivel: sesión, bolus, estado, basal, historial
├── crypto.py           ─── XChaCha20-Poly1305 con tracking de contadores
├── cache.py            ─── Persistencia de sesión (ypsopump_keys.json)
├── constants.py        ─── UUIDs de características BLE y mapeo de eventos
├── crc.py              ─── CRC16 personalizado con bitstuffing
├── entries.py          ─── Parser de entradas de historial
├── glb.py              ─── Codificación GLB_SAFE_VAR (formato redundante de 8 bytes)
├── lock.py             ─── Mutex basado en archivo (fcntl)
└── utils.py            ─── Framing BLE (chunks de 20 bytes) y cálculo de password

proto/                  ─── Definiciones Protobuf y stubs gRPC generados
├── encrypt_key.proto   ─── Servicio EncryptKey
├── nonce.proto         ─── Servicio NonceRequest
├── keyexchange_pb2.py  ─── Clases de mensajes protobuf
└── keyexchange_pb2_grpc.py ─── Stubs gRPC

scripts/
└── integrity_token.ts  ─── Script Frida (TypeScript) que hookea Play Integrity API
```

### Flujo de datos

```
┌─────────────┐     BLE      ┌──────────┐     gRPC     ┌─────────────────┐
│  Ordenador  │◄────────────►│ YpsoPump │              │ Servidor Ypsomed│
│  (este SW)  │              └──────────┘     ┌───────►│   (cloud)       │
│             │──────────────────────────────►│        └─────────────────┘
└──────┬──────┘                               │
       │ ADB + Frida (solo pairing)           │
       ▼                                      │
┌──────────────┐   Play Integrity token       │
│   Android    │──────────────────────────────┘
│  (mylife app)│
└──────────────┘
```

### Protocolo

- **Framing BLE:** Tramas de 20 bytes. Header = `(índice_trama << 4) | total_tramas`
- **Criptografía:** X25519 ECDH → HChaCha20 → XChaCha20-Poly1305 (AEAD)
- **Autenticación:** MD5(MAC_bytes + salt fijo de 10 bytes)
- **Formato GLB_SAFE_VAR:** 8 bytes: [valor LE (4)] + [NOT bitwise del valor (4)]
- **Firmware soportado:** Solo versión 05

## Troubleshooting

| Problema | Solución |
|----------|----------|
| `Cache file missing. Run pairing first.` | Ejecuta `python pairing.py` |
| `Cached shared key expired.` | Ejecuta `python pairing.py` de nuevo, o usa `--force` |
| La bomba no se encuentra por BLE | Asegúrate de que está encendida y cerca. Reinicia el Bluetooth del ordenador |
| Error de Frida durante pairing | Verifica que frida-server está corriendo en el Android (`adb shell ps \| grep frida`) |
| Play Integrity token timeout | La app mylife debe estar instalada y Google Play Services actualizado |
| `BleakError` durante operaciones | Acércate a la bomba. Comprueba que no hay otra app conectada por BLE |
