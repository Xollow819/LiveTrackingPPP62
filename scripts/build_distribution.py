#!/usr/bin/env python3
"""Build a signed public APK using a persistent private key outside the repository."""
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import zipfile


def verify_apk(root):
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        for line in (root / "local.properties").read_text().splitlines():
            if line.startswith("sdk.dir="):
                sdk = line.split("=", 1)[1].replace("\\:", ":").replace("\\\\", "\\")
                break
    if not sdk:
        raise SystemExit("Set ANDROID_HOME to verify the distribution APK.")
    candidates = [p for p in (Path(sdk) / "build-tools").iterdir()
                  if (p / "apksigner").is_file() and (p / "aapt").is_file()]
    tools = max(candidates, key=lambda p: tuple(int(n) for n in re.findall(r"\d+", p.name)))
    apk = root / "app/build/outputs/apk/release/app-release.apk"
    subprocess.run([str(tools / "apksigner"), "verify", "--min-sdk-version", "26", str(apk)], check=True)
    manifest = subprocess.check_output([
        str(tools / "aapt"), "dump", "xmltree", str(apk), "AndroidManifest.xml",
    ], text=True)
    if 'package="com.ppp62.livetracking"' not in manifest:
        raise SystemExit("Distribution APK has an unexpected package ID.")
    for attribute in ("debuggable", "testOnly"):
        if re.search(r"android:" + attribute + r"[^\n]*=\(type 0x12\)0xffffffff", manifest):
            raise SystemExit("Distribution APK must not be " + attribute)
    if not re.search(r"android:extractNativeLibs[^\n]*=\(type 0x12\)0xffffffff", manifest):
        raise SystemExit("Distribution APK must extract native libraries during installation.")
    with zipfile.ZipFile(apk) as archive:
        libraries = [info for info in archive.infolist() if info.filename.startswith("lib/") and info.filename.endswith(".so")]
        for abi in ("arm64-v8a", "armeabi-v7a"):
            if not any(info.filename.startswith("lib/" + abi + "/") for info in libraries):
                raise SystemExit("Distribution APK is missing " + abi + " libraries.")
        if any(info.compress_type != zipfile.ZIP_DEFLATED for info in libraries):
            raise SystemExit("Distribution native libraries must be compressed.")
    print("Verified APK signature, production package, installer flags, and both ARM architectures.")


def main():
    root = Path(__file__).resolve().parents[1]
    private = Path.home() / ".android" / "pppvenza-release"
    private.mkdir(mode=0o700, parents=True, exist_ok=True)
    private.chmod(0o700)
    store = private / "signing.p12"
    credentials = private / "credentials.json"
    if not store.exists() and not credentials.exists():
        keytool = shutil.which("keytool")
        if not keytool:
            raise SystemExit("Install JDK 17 and make keytool available before building.")
        secret = secrets.token_urlsafe(32)
        environment = dict(os.environ, PPP62_KEYTOOL_PASSWORD=secret)
        subprocess.run([
            keytool, "-genkeypair", "-keystore", str(store), "-storetype", "PKCS12",
            "-storepass:env", "PPP62_KEYTOOL_PASSWORD", "-keypass:env", "PPP62_KEYTOOL_PASSWORD",
            "-alias", "pppvenza", "-keyalg", "RSA", "-keysize", "4096", "-validity", "10000",
            "-dname", "CN=PPPVenza, OU=Android, O=PPP62, C=ID",
        ], env=environment, check=True)
        store.chmod(0o600)
        descriptor = os.open(credentials, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(descriptor, "w") as file:
            json.dump({"password": secret, "alias": "pppvenza"}, file)
    if not store.is_file() or not credentials.is_file():
        raise SystemExit("Signing files are incomplete. Restore the existing key and credentials; do not replace a published key.")
    store.chmod(0o600)
    credentials.chmod(0o600)
    config = json.loads(credentials.read_text())
    environment = dict(os.environ,
        PPP62_RELEASE_STORE_FILE=str(store),
        PPP62_RELEASE_STORE_PASSWORD=config["password"],
        PPP62_RELEASE_KEY_ALIAS=config["alias"],
        PPP62_RELEASE_KEY_PASSWORD=config["password"],
    )
    subprocess.run([
        str(root / "gradlew"), ":app:assembleRelease", ":app:testDebugUnitTest",
        ":app:lintRelease", "--console=plain",
    ], cwd=root, env=environment, check=True)
    verify_apk(root)
    print("Signed APK: " + str(root / "app/build/outputs/apk/release/app-release.apk"))
    print("Keep a secure backup of " + str(private) + " for all future app updates.")


if __name__ == "__main__":
    main()
