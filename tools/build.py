"""Build EdgeBar with the existing javac, D8 and Android Build Tools pipeline."""
from pathlib import Path
import argparse
import datetime
import json
import os
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile


ROOT = Path(__file__).resolve().parent.parent


def run(*args):
    process = subprocess.run(
        [str(arg) for arg in args], capture_output=True, text=True,
        encoding="utf-8", errors="replace",
    )
    for output in (process.stdout, process.stderr):
        if output.strip():
            print(output.strip(), flush=True)
    if process.returncode:
        raise RuntimeError(f"Build step failed: {Path(args[0]).name}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk-root", default=os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT"))
    parser.add_argument("--build-tools", default=os.environ.get("EDGEBAR_BUILD_TOOLS"))
    parser.add_argument("--android-jar", default=os.environ.get("EDGEBAR_ANDROID_JAR"))
    parser.add_argument("--java-home", default=os.environ.get("JAVA_HOME"))
    parser.add_argument("--keystore", default=str(ROOT / ".local" / "signing.jks"))
    parser.add_argument("--key-alias", default="edgebar")
    args = parser.parse_args()

    sdk = Path(args.sdk_root or Path.home() / "Android" / "Sdk")
    tools = Path(args.build_tools) if args.build_tools else sdk / "build-tools" / "36.0.0"
    android = Path(args.android_jar) if args.android_jar else sdk / "platforms" / "android-36" / "android.jar"
    jdk = Path(args.java_home) / "bin" if args.java_home else Path(shutil.which("javac")).resolve().parent
    suffix = ".exe" if os.name == "nt" else ""
    project = ROOT / "module"
    build = ROOT / "build" / datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    build.mkdir(parents=True)
    output_dir = ROOT / "dist"
    output_dir.mkdir(exist_ok=True)
    libraries = ROOT / "third_party" / "libxposed"
    api = libraries / "libxposed-102.jar"
    service = libraries / "libxposed-service-102.jar"
    ipc = libraries / "libxposed-interface-102.jar"

    run(tools / f"aapt2{suffix}", "compile", "--dir", project / "res", "-o", build / "res.zip")
    run(tools / f"aapt2{suffix}", "link", "-I", android, "--manifest", project / "AndroidManifest.xml",
        "--java", build / "generated", "-o", build / "base.apk", "--min-sdk-version", "29",
        "--target-sdk-version", "36", build / "res.zip")
    classes = build / "classes"
    classes.mkdir()
    sources = list((project / "src").rglob("*.java")) + list((build / "generated").rglob("*.java"))
    arguments = build / "javac.args"
    arguments.write_text("\n".join('"' + str(path).replace("\\", "/") + '"' for path in sources), encoding="utf-8")
    classpath = os.pathsep.join(str(path) for path in (android, api, service, ipc))
    run(jdk / f"javac{suffix}", "-J-Dfile.encoding=UTF-8", "-encoding", "UTF-8", "-source", "17", "-target", "17",
        "-cp", classpath, "-d", classes, "@" + str(arguments))
    with zipfile.ZipFile(build / "classes.jar", "w") as archive:
        for source in classes.rglob("*.class"):
            archive.write(source, source.relative_to(classes).as_posix())

    dex = build / "dex"
    dex.mkdir()
    run(jdk / f"java{suffix}", "-cp", tools / "lib" / "d8.jar", "com.android.tools.r8.D8", "--release",
        "--min-api", "29", "--lib", android, "--classpath", api, "--output", dex, build / "classes.jar", service, ipc)
    with zipfile.ZipFile(build / "base.apk", "a", zipfile.ZIP_DEFLATED) as archive:
        for source in dex.glob("*.dex"):
            archive.write(source, source.name)
        for source in (project / "metadata").rglob("*"):
            if source.is_file():
                archive.write(source, source.relative_to(project / "metadata").as_posix())
    run(tools / f"zipalign{suffix}", "-f", "4", build / "base.apk", build / "aligned.apk")

    keystore = Path(args.keystore).resolve()
    os.environ.setdefault("EDGEBAR_STORE_PASSWORD", "android")
    os.environ.setdefault("EDGEBAR_KEY_PASSWORD", os.environ["EDGEBAR_STORE_PASSWORD"])
    if not keystore.exists():
        keystore.parent.mkdir(parents=True, exist_ok=True)
        run(jdk / f"keytool{suffix}", "-genkeypair", "-keystore", keystore,
            "-storepass:env", "EDGEBAR_STORE_PASSWORD", "-keypass:env", "EDGEBAR_KEY_PASSWORD",
            "-alias", args.key_alias, "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
            "-dname", "CN=EdgeBar Local Development")
    version = ET.parse(project / "AndroidManifest.xml").getroot().get("{http://schemas.android.com/apk/res/android}versionName")
    output = output_dir / f"EdgeBar-v{version}.apk"
    run(jdk / f"java{suffix}", "-jar", tools / "lib" / "apksigner.jar", "sign", "--ks", keystore,
        "--ks-pass", "env:EDGEBAR_STORE_PASSWORD", "--key-pass", "env:EDGEBAR_KEY_PASSWORD",
        "--ks-key-alias", args.key_alias, "--out", output, build / "aligned.apk")
    run(jdk / f"java{suffix}", "-jar", tools / "lib" / "apksigner.jar", "verify", "--verbose", output)
    metadata = {"apk": str(output), "bytes": output.stat().st_size, "build": str(build)}
    (output_dir / "build.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    print(json.dumps(metadata), flush=True)


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
