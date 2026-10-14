# Clipboard Access

Per-app clipboard read restrictions for Evolution X. Like Routines, this feature
compiles inside Settings instead of installing a separate APK.

The only menu entry is **Clipboard access** on **Privacy controls**, immediately
after **Location access**. The screen includes every installed application in the
current Android user, including system, disabled and non-launcher apps. Search
matches the application label or package name. English and Russian are included;
other languages fall back to English.

**Switch on means reading is blocked.** The introductory text explains that this
also prevents pasting through ClipboardManager. Writing to the clipboard remains
available. The screen uses the existing Settings toolbar and dynamic light/dark
theme, shows application icons, labels, package names and the current read state.

## Enforcement

`AppOpsManager.OP_READ_CLIPBOARD` is set per package and UID to `MODE_IGNORED`
when blocked, and `MODE_ALLOWED` when the restriction is removed. ClipboardService
already checks that operation for content, descriptions, presence checks and
change callbacks. No framework patch, root command or private denylist is needed.
AppOps persists the mode across process restarts and reboots. Normal Android
foreground and lock-screen rules still apply when the switch is off.

Applications are loaded and AppOps calls run off the main thread. Icons load only
for visible rows. Package broadcasts and activity resume refresh the list. Search
survives activity recreation. Writes verify that the package still has the same
UID, read back the actual mode and report an error if an overriding policy prevents
the change. The activity is not exported and takes no package or mode from intents.

This is a ClipboardManager restriction, not complete isolation of applications.
It cannot revoke data an app already read, block data supplied by a keyboard
through another channel, or isolate packages sharing a UID against impersonation.
Existing UID-wide restrictions take precedence over this package setting.

## Integration

Checkout this repository at `packages/features/ClipboardAccess` on branch `cnb`.
The manifest entry uses a `linkfile` to expose `settings/res` as
`packages/apps/Settings/clipboard-access-res`. Settings adds the filegroup
`:evolution-clipboard-access-settings-srcs`, that resource directory, the internal
activity and its menu preference. It uses Settings' existing system UID and
`MANAGE_APP_OPS_MODES` access; no new manifest permissions or privileged permission
allowlist changes are required.

Reproducible integration patches are included in `integration/`:

```sh
git -C packages/apps/Settings apply --check \
  ../../features/ClipboardAccess/integration/Settings.patch
git -C packages/apps/Settings apply \
  ../../features/ClipboardAccess/integration/Settings.patch
git -C .repo/manifests apply --check \
  ../../packages/features/ClipboardAccess/integration/manifest.patch
git -C .repo/manifests apply \
  ../../packages/features/ClipboardAccess/integration/manifest.patch
```

These patches target the integration revisions recorded in `integration/BASES.md`.
After syncing the manifest, build `SettingsGoogle` (or `Settings` for non-GMS
products). No extra product package is required.

## KernelSU test module

`ksu/build_module.py` packages the built, signed `SettingsGoogle.apk` and checks
that its signing certificates match a backup pulled from the test device:

```sh
out/host/linux-x86/bin/apksigner sign \
  --key release-keys/fairlady/platform.pk8 \
  --cert release-keys/fairlady/platform.x509.pem \
  --out artifacts/clipboard-access/SettingsGoogle.apk \
  out/target/product/fairlady/system_ext/priv-app/SettingsGoogle/SettingsGoogle.apk
python3 packages/features/ClipboardAccess/ksu/build_module.py \
  --apk artifacts/clipboard-access/SettingsGoogle.apk \
  --baseline-apk artifacts/clipboard-access/device-backup/SettingsGoogle.apk \
  --apksigner out/host/linux-x86/bin/apksigner \
  --aapt2 out/host/linux-x86/bin/aapt2 \
  --output artifacts/clipboard-access/evolution-clipboard-access-test.zip
```

The signing key must be the same platform key used by the installed firmware;
the packager rejects standard AOSP test-key builds when the device uses release
keys. Private keys and APKs are not included in this repository.
The packager also verifies the package name and rejects newly requested permissions
that are absent from the baseline manifest.

The module is specific to that baseline APK and Android API 37. It mounts just the
replacement APK in init's mount namespace during `post-fs-data`, before package
scanning, and does not require a metamodule. It does not install to `/data/app`,
edit framework files or change existing AppOps modes. If the firmware APK changes,
the module skips its mount rather than replacing a different version. Installation
and removal take effect after reboot. The mount result is recorded in
`/data/adb/modules/evolution_clipboard_access_test/mount.log`.

Because Android caches parsed manifests using the APK directory's timestamp,
the mount and uninstall scripts invalidate only the generated
`/data/system/package_cache/*/SettingsGoogle-*` entries. Application data is retained.

Remove `evolution_clipboard_access_test` in KernelSU and reboot to
restore the firmware Settings APK. Clipboard restrictions themselves remain in
AppOps; turn the switches off before removal if you also want to remove them.

## Device validation

1. Open Privacy controls. Confirm the entry appears once, directly after Location
   access, with the specified English or Russian title and summary.
2. Open the entry. Confirm system apps and apps without launcher activities appear;
   search by a label and then by a package name. Check dark/light mode and TalkBack.
3. Copy text in another app, enable the target app's switch, then focus the target
   app. Clipboard reads must return null, presence checks false and paste must fail.
   The target app must still be able to copy new text into the shared clipboard.
4. Disable the switch. Clipboard reads must work again while the target is focused.
5. Check `adb shell cmd appops get PACKAGE READ_CLIPBOARD`: on corresponds to
   `ignore`; off corresponds to `allow` or the default allowed mode.
6. Reboot, reopen the screen and verify the restriction remains enabled and enforced.
7. Rotate while searching, install/uninstall an app, and revisit the screen after
   changing AppOps externally. Verify the list and state refresh correctly.
8. Test a keyboard and a system application, plus a separate Android user. Verify
   package settings stay scoped to that user. An ordinary app must not be able to
   launch the non-exported activity.
