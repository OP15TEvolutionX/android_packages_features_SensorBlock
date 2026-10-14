#!/system/bin/sh
# Copyright (C) 2026 Evolution X; SPDX-License-Identifier: Apache-2.0
# Restore parsing of the firmware manifest when the test APK is removed.
for cache in /data/system/package_cache/*/SettingsGoogle-*; do
    [ -f "$cache" ] && rm -f "$cache"
done
