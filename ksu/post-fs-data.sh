#!/system/bin/sh
# Copyright (C) 2026 Evolution X; SPDX-License-Identifier: Apache-2.0
# Mount before PackageManager scans APKs. No additional metamodule is required.

MODDIR=${0%/*}
TARGET=/system_ext/priv-app/SettingsGoogle/SettingsGoogle.apk
BUSYBOX=/data/adb/ksu/bin/busybox
exec > "$MODDIR/mount.log" 2>&1

[ "$(getprop ro.build.version.sdk)" = "37" ] || exit 0
[ -f "$TARGET" ] && [ -f "$MODDIR/payload/SettingsGoogle.apk" ] || exit 0
ACTUAL=$(sha256sum "$TARGET" | cut -d ' ' -f 1)
EXPECTED=$(cat "$MODDIR/baseline.sha256")
if [ "$ACTUAL" != "$EXPECTED" ]; then
    echo "Firmware Settings changed; skipping the test module."
    exit 0
fi

chcon u:object_r:system_file:s0 "$MODDIR/payload/SettingsGoogle.apk" || exit 0
"$BUSYBOX" nsenter -t 1 -m /system/bin/mount -o bind \
    "$MODDIR/payload/SettingsGoogle.apk" "$TARGET" || exit 0

# PackageCacher stats the APK directory, whose firmware mtime does not change
# when mounting a single file. Invalidate only this package's generated cache.
for cache in /data/system/package_cache/*/SettingsGoogle-*; do
    [ -f "$cache" ] && rm -f "$cache"
done
echo "Clipboard access test APK mounted successfully."
