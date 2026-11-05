#!/usr/bin/env bash
set -e

# Cari binary adb (dari PATH atau Android SDK lokal)
ADB_BIN="adb"
if ! command -v adb &>/dev/null; then
    if [ -x "$HOME/Android/Sdk/platform-tools/adb" ]; then
        ADB_BIN="$HOME/Android/Sdk/platform-tools/adb"
        export PATH="$HOME/Android/Sdk/platform-tools:$PATH"
    elif [ -n "$ANDROID_HOME" ] && [ -x "$ANDROID_HOME/platform-tools/adb" ]; then
        ADB_BIN="$ANDROID_HOME/platform-tools/adb"
        export PATH="$ANDROID_HOME/platform-tools:$PATH"
    else
        echo "❌ Error: 'adb' tidak ditemukan di sistem!"
        echo "💡 Install adb pakai pacman: sudo pacman -S android-tools"
        exit 1
    fi
fi

# Cek apakah ada HP yang terdeteksi
DEVICES_OUTPUT=$("$ADB_BIN" devices | grep -v "List of devices" | grep -v "^$" || true)

if [ -z "$DEVICES_OUTPUT" ]; then
    echo "❌ HP belum terdeteksi sama sekali via ADB!"
    echo ""
    echo "👉 Kalau pakai Kabel USB:"
    echo "   1. Colok kabel USB ke laptop."
    echo "   2. Di notifikasi HP: ubah mode dari 'Hanya Mengisi Daya' ke 'Transfer Berkas (MTP)'."
    echo "   3. Cek layar HP: kalau muncul pop-up 'Izinkan debugging USB?', centang 'Selalu izinkan' lalu klik Izinkan / OK."
    echo ""
    echo "👉 Kalau pakai Wi-Fi (Wireless Debugging):"
    echo "   1. Pastikan HP & Laptop di Wi-Fi yang sama."
    echo "   2. Di HP: Developer Options -> Wireless debugging (klik teksnya)."
    echo "   3. Hubungkan dengan cara:"
    echo "      adb pair <IP_HP>:<PORT_PAIRING>"
    echo "      adb connect <IP_HP>:<PORT_CONNECT>"
    echo ""
    echo "Setelah itu jalankan lagi: ./dev.sh"
    exit 1
fi

# Cek apakah statusnya unauthorized
if echo "$DEVICES_OUTPUT" | grep -q "unauthorized"; then
    echo "⚠️  HP terdeteksi tapi statusnya 'unauthorized'!"
    echo "👉 Buka layar HP lu sekarang, ada pop-up konfirmasi 'Izinkan debugging USB?'."
    echo "   Centang 'Selalu izinkan dari komputer ini' dan klik OK."
    exit 1
fi

echo "📱 Perangkat terdeteksi:"
echo "$DEVICES_OUTPUT"
echo ""

echo "⚡ [1/2] Compiling incremental & installing to device..."
./gradlew :app:installArm64FossDebug

echo "🚀 [2/2] Launching Oto (Debug) on screen..."
"$ADB_BIN" shell am start -n co.oto.music.debug/com.mochyy.mochymusic.MainActivity

echo "✅ Beres! Oto (Debug) sudah jalan di HP."
