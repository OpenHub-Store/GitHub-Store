#!/usr/bin/env python3
"""
Generate comprehensive flatpak-sources.json by scanning Gradle cache.

FAST approach:
1. Scan ~/.gradle/caches/modules-2/files-2.1/ for all cached artifacts
2. Compute SHA512 from local files (no network needed)
3. Determine Maven repo URL based on group name heuristics
4. For POMs not in local cache, download from repos (tries multiple)
5. Skip files with non-standard names (AAR/klib with internal cache names)
6. Output flatpak-sources.json

Files that Gradle stores under internal names (e.g., "animation.aar" instead of
"animation-android-1.10.0.aar") are SKIPPED — they are Android/iOS platform
artifacts that are not needed for the Desktop/Flatpak build.
"""

import hashlib
import json
import os
import sys
import urllib.request
import ssl
from pathlib import Path
from collections import defaultdict

GRADLE_CACHE = Path(os.path.expanduser("~")) / ".gradle" / "caches" / "modules-2" / "files-2.1"
OUTPUT_DIR = Path(__file__).parent
SSL_CTX = ssl.create_default_context()

# All repos
ALL_REPOS = [
    "https://repo1.maven.org/maven2",
    "https://dl.google.com/dl/android/maven2",
    "https://maven.pkg.jetbrains.space/public/p/compose/dev",
    "https://plugins.gradle.org/m2",
]


def get_repos_for_group(group):
    """Get ordered list of repos to try for a given group."""
    g = group.lower()
    if any(g.startswith(p) for p in ["androidx.", "com.android.", "com.google.android.",
            "com.google.firebase", "com.google.gms", "com.google.testing."]):
        return ["https://dl.google.com/dl/android/maven2", "https://repo1.maven.org/maven2",
                "https://maven.pkg.jetbrains.space/public/p/compose/dev"]
    if g.startswith("org.jetbrains.compose"):
        return ["https://maven.pkg.jetbrains.space/public/p/compose/dev",
                "https://repo1.maven.org/maven2", "https://plugins.gradle.org/m2"]
    if g.startswith("org.gradle.") or g.startswith("gradle.plugin."):
        return ["https://plugins.gradle.org/m2", "https://repo1.maven.org/maven2"]
    return ["https://repo1.maven.org/maven2", "https://dl.google.com/dl/android/maven2",
            "https://maven.pkg.jetbrains.space/public/p/compose/dev", "https://plugins.gradle.org/m2"]


def sha512_file(filepath):
    h = hashlib.sha512()
    with open(filepath, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def download_and_hash(url):
    """Download file, return SHA512 or None."""
    try:
        req = urllib.request.Request(url)
        req.add_header("User-Agent", "flatpak-gen/1.0")
        with urllib.request.urlopen(req, timeout=20, context=SSL_CTX) as resp:
            if resp.status == 200:
                data = resp.read()
                if len(data) < 500 and b'<html' in data[:100].lower():
                    return None
                return hashlib.sha512(data).hexdigest()
    except:
        pass
    return None


def is_standard_filename(artifact, version, filename):
    """Check if filename follows Maven naming convention (artifact-version.ext)."""
    base = f"{artifact}-{version}"
    # Standard: artifact-version.ext or artifact-version-classifier.ext
    if filename.startswith(base):
        return True
    return False


def scan_gradle_cache():
    artifacts = defaultdict(dict)
    if not GRADLE_CACHE.exists():
        print(f"ERROR: Gradle cache not found at {GRADLE_CACHE}", file=sys.stderr)
        sys.exit(1)
    for group_dir in GRADLE_CACHE.iterdir():
        if not group_dir.is_dir(): continue
        for artifact_dir in group_dir.iterdir():
            if not artifact_dir.is_dir(): continue
            for version_dir in artifact_dir.iterdir():
                if not version_dir.is_dir(): continue
                for hash_dir in version_dir.iterdir():
                    if not hash_dir.is_dir(): continue
                    for f in hash_dir.iterdir():
                        if f.is_file():
                            artifacts[(group_dir.name, artifact_dir.name, version_dir.name)][f.name] = str(f)
    return artifacts


def main():
    print("=" * 60)
    print("Flatpak Sources Generator (fast, skip non-standard names)")
    print("=" * 60)

    print("\nScanning Gradle cache...")
    artifacts = scan_gradle_cache()
    print(f"Found {len(artifacts)} unique artifacts")

    all_entries = []
    seen = set()
    stats = {"local": 0, "downloaded": 0, "skipped": 0, "failed_pom": 0}
    total = len(artifacts)

    for idx, ((group, artifact, version), files) in enumerate(sorted(artifacts.items())):
        if (idx + 1) % 200 == 0:
            print(f"  [{idx+1}/{total}] {stats}")

        group_path = group.replace(".", "/")
        base_name = f"{artifact}-{version}"
        repos = get_repos_for_group(group)

        needed = set()
        has_jar_or_aar = False

        for fname in files:
            if fname.endswith("-sources.jar") or fname.endswith("-javadoc.jar"):
                continue
            if not fname.endswith((".jar", ".pom", ".module", ".klib", ".aar")):
                continue
            # SKIP files with non-standard names (Gradle internal cache names)
            if not is_standard_filename(artifact, version, fname):
                stats["skipped"] += 1
                continue
            needed.add(fname)
            if fname.endswith((".jar", ".aar")):
                has_jar_or_aar = True

        # Ensure POM if we have JAR/AAR
        pom_name = f"{base_name}.pom"
        if has_jar_or_aar and pom_name not in needed:
            needed.add(pom_name)

        for fname in sorted(needed):
            key = f"{group_path}/{artifact}/{version}/{fname}"
            if key in seen: continue
            seen.add(key)

            local_path = files.get(fname)
            if local_path and os.path.exists(local_path):
                sha = sha512_file(local_path)
                url = f"{repos[0]}/{group_path}/{artifact}/{version}/{fname}"
                all_entries.append({
                    "type": "file", "url": url, "sha512": sha,
                    "dest": f"offline-repository/{group_path}/{artifact}/{version}",
                    "dest-filename": fname
                })
                stats["local"] += 1
            else:
                # Download (missing POMs mostly)
                found = False
                for repo in repos:
                    url = f"{repo}/{group_path}/{artifact}/{version}/{fname}"
                    sha = download_and_hash(url)
                    if sha:
                        all_entries.append({
                            "type": "file", "url": url, "sha512": sha,
                            "dest": f"offline-repository/{group_path}/{artifact}/{version}",
                            "dest-filename": fname
                        })
                        stats["downloaded"] += 1
                        found = True
                        break
                if not found:
                    stats["failed_pom"] += 1

    # Plugin markers
    print("\nAdding plugin marker POMs...")
    mc = add_plugin_markers(artifacts, all_entries, seen)

    all_entries.sort(key=lambda e: (e["dest"], e["dest-filename"]))

    output = OUTPUT_DIR / "flatpak-sources.json"
    with open(output, "w") as f:
        json.dump(all_entries, f, indent=4)

    print(f"\n{'='*60}")
    print(f"  Local:      {stats['local']}")
    print(f"  Downloaded: {stats['downloaded']}")
    print(f"  Markers:    {mc}")
    print(f"  Skipped:    {stats['skipped']} (non-standard filenames)")
    print(f"  Failed:     {stats['failed_pom']}")
    print(f"  TOTAL:      {len(all_entries)}")
    print(f"\nWritten to {output}")


def add_plugin_markers(artifacts, entries, seen):
    count = 0
    pv = {}
    for (g, a, v), _ in artifacts.items():
        if g == "org.jetbrains.kotlin" and a == "kotlin-gradle-plugin": pv["kotlin"] = v
        if g == "org.jetbrains.compose" and a == "compose-gradle-plugin": pv["compose"] = v
        if g == "com.google.devtools.ksp" and a == "symbol-processing-gradle-plugin": pv["ksp"] = v
        if g == "com.android.tools.build" and a == "gradle": pv["agp"] = v
        if g == "org.jlleitschuh.gradle" and a == "ktlint-gradle": pv["ktlint"] = v
        if g == "com.codingfeline.buildkonfig" and a == "buildkonfig-gradle-plugin": pv["buildkonfig"] = v
        if g == "androidx.room" and a == "room-gradle-plugin": pv["room"] = v
        if g == "org.gradle.kotlin" and a == "gradle-kotlin-dsl-plugins": pv["kotlin-dsl"] = v

    print(f"  Versions: {pv}")

    markers = []
    if "kotlin" in pv:
        for pid in ["org.jetbrains.kotlin.multiplatform", "org.jetbrains.kotlin.plugin.serialization",
                     "org.jetbrains.kotlin.plugin.compose", "org.jetbrains.kotlin.jvm", "org.jetbrains.kotlin.android"]:
            markers.append((pid, pv["kotlin"]))
    if "compose" in pv:
        markers.append(("org.jetbrains.compose", pv["compose"]))
    if "ksp" in pv:
        markers.append(("com.google.devtools.ksp", pv["ksp"]))
    if "agp" in pv:
        for pid in ["com.android.application", "com.android.library", "com.android.kotlin.multiplatform.library"]:
            markers.append((pid, pv["agp"]))
    if "ktlint" in pv:
        markers.append(("org.jlleitschuh.gradle.ktlint", pv["ktlint"]))
    if "buildkonfig" in pv:
        markers.append(("com.codingfeline.buildkonfig", pv["buildkonfig"]))
    if "room" in pv:
        markers.append(("androidx.room", pv["room"]))
    if "kotlin-dsl" in pv:
        markers.append(("org.gradle.kotlin.kotlin-dsl", pv["kotlin-dsl"]))
    markers.append(("io.github.jwharm.flatpak-gradle-generator", "1.7.0"))

    repos = ["https://plugins.gradle.org/m2", "https://dl.google.com/dl/android/maven2",
             "https://maven.pkg.jetbrains.space/public/p/compose/dev", "https://repo1.maven.org/maven2"]

    for pid, ver in markers:
        gp = pid.replace(".", "/")
        art = f"{pid}.gradle.plugin"
        fn = f"{art}-{ver}.pom"
        key = f"{gp}/{art}/{ver}/{fn}"
        if key in seen: continue
        seen.add(key)
        for repo in repos:
            url = f"{repo}/{gp}/{art}/{ver}/{fn}"
            sha = download_and_hash(url)
            if sha:
                entries.append({"type": "file", "url": url, "sha512": sha,
                    "dest": f"offline-repository/{gp}/{art}/{ver}", "dest-filename": fn})
                count += 1
                print(f"    + {pid}:{ver}")
                break
    return count


if __name__ == "__main__":
    main()
