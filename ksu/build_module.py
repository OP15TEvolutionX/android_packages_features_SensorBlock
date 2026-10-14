#!/usr/bin/env python3
"""Package a signed Settings APK as a firmware-specific KernelSU test module."""
# Copyright (C) 2026 Evolution X; SPDX-License-Identifier: Apache-2.0
import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo


def sha256(path):
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def certificates(apksigner, apk):
    output = subprocess.check_output(
        [str(apksigner), "verify", "--print-certs", str(apk)], text=True
    )
    digests = re.findall(r"certificate SHA-256 digest: ([0-9a-f]+)", output)
    if not digests:
        raise RuntimeError(f"No APK signing certificates found: {apk}")
    return sorted(set(digests))


def permissions(aapt2, apk):
    output = subprocess.check_output([str(aapt2), "dump", "permissions", str(apk)], text=True)
    if output.splitlines()[0] != "package: com.android.settings":
        raise RuntimeError(f"Not a Settings APK: {apk}")
    return set(re.findall(r"uses-permission(?:-sdk-\d+)?: name='([^']+)'", output))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--baseline-apk", type=Path, required=True)
    parser.add_argument("--apksigner", type=Path, required=True)
    parser.add_argument("--aapt2", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    certs = certificates(args.apksigner, args.apk)
    if certs != certificates(args.apksigner, args.baseline_apk):
        raise RuntimeError("New and installed Settings APK signing certificates differ")
    added_permissions = permissions(args.aapt2, args.apk) - permissions(args.aapt2, args.baseline_apk)
    if added_permissions:
        raise RuntimeError("Test APK adds unreviewed permissions: " + ", ".join(sorted(added_permissions)))
    metadata = {
        "module_id": "evolution_clipboard_access_test",
        "sdk": 37,
        "target": "/system_ext/priv-app/SettingsGoogle/SettingsGoogle.apk",
        "apk_sha256": sha256(args.apk),
        "baseline_sha256": sha256(args.baseline_apk),
        "signing_certificates_sha256": certs,
    }
    source_dir = Path(__file__).resolve().parent
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with ZipFile(args.output, "w", compression=ZIP_DEFLATED) as archive:
        for name in ("module.prop", "customize.sh", "post-fs-data.sh", "uninstall.sh"):
            entry = ZipInfo(name)
            entry.compress_type = ZIP_DEFLATED
            entry.create_system = 3
            entry.external_attr = (0o100755 if name.endswith(".sh") else 0o100644) << 16
            archive.writestr(entry, (source_dir / name).read_bytes())
        archive.writestr("skip_mount", "")
        archive.writestr("baseline.sha256", metadata["baseline_sha256"] + "\n")
        archive.writestr("build.json", json.dumps(metadata, indent=2) + "\n")
        archive.write(args.apk, "payload/SettingsGoogle.apk")
    print(json.dumps({"zip": str(args.output.resolve()), **metadata}, indent=2))


if __name__ == "__main__":
    main()
