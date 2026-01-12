# Android Device Profiles for Spoofing
# Choose one profile or customize

DEVICE_PROFILES = {
    "samsung_s21": {
        "platform": "Android",
        "model": "SM-G991B",
        "os_type": "Phone",
        "os_version": "14",
        "manufacturer": "samsung"
    },
    "pixel_7": {
        "platform": "Android",
        "model": "Pixel 7",
        "os_type": "Phone", 
        "os_version": "14",
        "manufacturer": "Google"
    },
    "oneplus_9": {
        "platform": "Android",
        "model": "LE2113",
        "os_type": "Phone",
        "os_version": "13",
        "manufacturer": "OnePlus"
    },
    "xiaomi_mi11": {
        "platform": "Android",
        "model": "M2011K2G",
        "os_type": "Phone",
        "os_version": "13",
        "manufacturer": "Xiaomi"
    }
}

# App metadata (from APK)
APP_INFO = {
    "application_name": "mylife app",
    "application_package": "net.sinovo.mylife.app",
    "library_version": "1.0.0.0",
    "xamarin": True
}

# Current device used (change this to switch profiles)
CURRENT_DEVICE = "xiaomi_mi11"
