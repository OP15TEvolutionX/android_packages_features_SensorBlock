# Validation — 2026-10-14

Built `SettingsGoogle` for `evolution_fairlady-user` and tested on the matching
Evolution X Android 17 / API 37 firmware with KernelSU Next 3.4.2.

Verified on device:

- The sole menu entry is on Privacy controls, directly after Location access and
  before the injected Activity controls entry. Its Russian title and summary
  exactly match the requested text, and tapping it opens the new activity.
- Russian and English screen resources render correctly with the existing Settings
  toolbar and dark theme. The temporary English app locale was reset afterwards.
- The list includes system applications, overlays without launcher activities and
  disabled applications. Package-name search finds disabled `org.omnirom.omnijaws`.
- Enabling the test application's switch sets `READ_CLIPBOARD` to `ignore`.
- A focused, unprivileged test activity can write to the clipboard while blocked,
  but receives no clip, no description, `hasPrimaryClip() == false` and zero change
  callbacks.
- The blocked mode and checked switch survive a device reboot.
- Disabling the switch sets the mode back to `allow`; the test activity then reads
  the exact test text written while blocked, with description and presence restored.
- The KernelSU mount survives reboot, the mounted APK hash matches the packaged
  payload, `sys.boot_completed` is `1`, and the crash log is empty after the final
  installation and tests. The temporary probe application was removed.
- APK signing certificate verification, package identity verification, rejection
  of added manifest permissions, shell syntax checks, ZIP CRC verification and
  reverse application checks of both integration patches pass.

The signed payload SHA-256 is
`283b4c05d2ed02faa6ae00c1553dd93ef03505f6930e3a1e2db7b885129dd1c0`.
The baseline Settings APK SHA-256 is
`16964273d88445d107c5a94b9781b7250ba959c341c404e1881320f9f1faa472`.
The test module ZIP SHA-256 is
`3afc27d03d564977d1ff6f76569ac088a805f920f8654bb7ae654dd5f8809c98`.

Secondary Android users, administrator restrictions, TalkBack, rotation and light
theme remain manual validation items; this run does not claim device coverage for
them. Normal Android foreground restrictions remain in place when reading is
unblocked. No restrictions were applied to the user's existing applications.
