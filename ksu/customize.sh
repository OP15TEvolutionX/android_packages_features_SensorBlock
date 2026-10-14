#!/system/bin/sh
# Copyright (C) 2026 Evolution X; SPDX-License-Identifier: Apache-2.0

[ "$KSU" = "true" ] || abort "This test module requires KernelSU."
[ "$API" = "37" ] || abort "This test module requires Android API 37."

TARGET=/system_ext/priv-app/SettingsGoogle/SettingsGoogle.apk
[ -f "$TARGET" ] || abort "SettingsGoogle.apk was not found at the expected path."
ACTUAL=$(sha256sum "$TARGET" | cut -d ' ' -f 1)
EXPECTED=$(cat "$MODPATH/baseline.sha256")
PAYLOAD=$(sha256sum "$MODPATH/payload/SettingsGoogle.apk" | cut -d ' ' -f 1)
# Allow reinstalling the same payload when updating module scripts.
if [ "$ACTUAL" != "$EXPECTED" ] && [ "$ACTUAL" != "$PAYLOAD" ]; then
    abort "Settings differs from the firmware used to build this test module."
fi

ui_print "Installing clipboard access controls for Settings"
ui_print "Only SettingsGoogle.apk will be mounted; reboot to apply."
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$MODPATH/post-fs-data.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755
set_perm "$MODPATH/payload/SettingsGoogle.apk" 0 0 0644 u:object_r:system_file:s0
