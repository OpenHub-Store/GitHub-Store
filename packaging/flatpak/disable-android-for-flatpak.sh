#!/bin/bash
# disable-android-for-flatpak.sh
#
# Strips all Android-related configuration from the project so it can
# build inside the Flatpak sandbox where no Android SDK is available.
# This script modifies files IN-PLACE — only run during Flatpak builds.

set -euo pipefail

echo "=== Disabling Android targets for Flatpak build ==="

# ─────────────────────────────────────────────────────────────────────
# 1. Root build.gradle.kts — comment out Android plugin declarations
# ─────────────────────────────────────────────────────────────────────
echo "[1/6] Patching root build.gradle.kts"
sed -i \
    -e 's|alias(libs.plugins.android.application)|// alias(libs.plugins.android.application)|' \
    -e 's|alias(libs.plugins.android.library)|// alias(libs.plugins.android.library)|' \
    -e 's|alias(libs.plugins.android.kotlin.multiplatform.library)|// alias(libs.plugins.android.kotlin.multiplatform.library)|' \
    build.gradle.kts

# ─────────────────────────────────────────────────────────────────────
# 2. Convention plugins — replace Android plugin applies with no-ops
# ─────────────────────────────────────────────────────────────────────
CONVENTION_DIR="build-logic/convention/src/main/kotlin"

echo "[2/6] Patching KmpLibraryConventionPlugin (remove Android library plugin + config)"
cat > "$CONVENTION_DIR/KmpLibraryConventionPlugin.kt" << 'KOTLIN'
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import zed.rainxch.githubstore.convention.configureJvmTarget
import zed.rainxch.githubstore.convention.libs
import zed.rainxch.githubstore.convention.pathToResourcePrefix
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.gradle.kotlin.dsl.configure

class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
                apply("org.jetbrains.kotlin.plugin.serialization")
            }

            configureJvmTarget()

            extensions.configure<KotlinMultiplatformExtension> {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                    freeCompilerArgs.add("-Xmulti-dollar-interpolation")
                    freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
                    freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
                }
            }

            dependencies {
                "commonMainImplementation"(libs.findLibrary("kotlinx-serialization-json").get())
            }
        }
    }
}
KOTLIN

echo "[3/6] Patching CmpApplicationConventionPlugin (remove Android application)"
cat > "$CONVENTION_DIR/CmpApplicationConventionPlugin.kt" << 'KOTLIN'
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import zed.rainxch.githubstore.convention.configureJvmTarget
import zed.rainxch.githubstore.convention.libs

class CmpApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
                apply("org.jetbrains.compose")
                apply("org.jetbrains.kotlin.plugin.compose")
            }

            configureJvmTarget()
        }
    }
}
KOTLIN

echo "[4/6] Patching CmpLibraryConventionPlugin & CmpFeatureConventionPlugin"

# CmpLibraryConventionPlugin — remove Android library dependency
sed -i \
    -e 's|apply("com.android.library")|// apply("com.android.library")|' \
    "$CONVENTION_DIR/CmpLibraryConventionPlugin.kt" 2>/dev/null || true

sed -i \
    -e 's|apply("com.android.library")|// apply("com.android.library")|' \
    "$CONVENTION_DIR/CmpFeatureConventionPlugin.kt" 2>/dev/null || true

# Remove configureKotlinAndroid calls and Android extension blocks
for f in "$CONVENTION_DIR"/*.kt "$CONVENTION_DIR"/zed/rainxch/githubstore/convention/*.kt; do
    [ -f "$f" ] || continue
    sed -i \
        -e 's|configureAndroidTarget()|// configureAndroidTarget()|g' \
        -e 's|configureKotlinAndroid(this)|// configureKotlinAndroid(this)|g' \
        "$f"
done

# ─────────────────────────────────────────────────────────────────────
# 3. KotlinMultiplatform.kt — skip Android configuration
# ─────────────────────────────────────────────────────────────────────
echo "[5/6] Patching KotlinMultiplatform.kt"
cat > "$CONVENTION_DIR/zed/rainxch/githubstore/convention/KotlinMultiplatform.kt" << 'KOTLIN'
package zed.rainxch.githubstore.convention

import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

internal fun Project.configureKotlinMultiplatform() {
    // Android target disabled for Flatpak build
    configureJvmTarget()

    extensions.configure<KotlinMultiplatformExtension> {
        compilerOptions {
            freeCompilerArgs.add("-Xexpect-actual-classes")
            freeCompilerArgs.add("-Xmulti-dollar-interpolation")
            freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
            freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
        }
    }
}
KOTLIN

# ─────────────────────────────────────────────────────────────────────
# 4. Module build.gradle.kts files — remove android {} blocks
# ─────────────────────────────────────────────────────────────────────
echo "[6/6] Removing android {} blocks from module build.gradle.kts files"

# composeApp — remove android {} block and its contents
python3 -c "
import re, sys

with open('composeApp/build.gradle.kts', 'r') as f:
    content = f.read()

# Remove top-level android { ... } blocks (handles nested braces)
def remove_block(text, keyword):
    result = []
    i = 0
    while i < len(text):
        # Look for 'android {' at line start (possibly with whitespace)
        line_start = text.rfind('\n', 0, i) + 1
        prefix = text[line_start:i].strip()
        if text[i:].startswith(keyword + ' {') or text[i:].startswith(keyword + '{'):
            if prefix == '' or prefix.endswith('\n'):
                # Find matching closing brace
                brace_start = text.index('{', i)
                depth = 1
                j = brace_start + 1
                while j < len(text) and depth > 0:
                    if text[j] == '{': depth += 1
                    elif text[j] == '}': depth -= 1
                    j += 1
                # Skip past the block and any trailing newline
                if j < len(text) and text[j] == '\n':
                    j += 1
                i = j
                continue
        result.append(text[i])
        i += 1
    return ''.join(result)

content = remove_block(content, 'android')
with open('composeApp/build.gradle.kts', 'w') as f:
    f.write(content)
"

# core/data — remove android {} block
for gradle_file in \
    core/data/build.gradle.kts \
    core/domain/build.gradle.kts \
    core/presentation/build.gradle.kts; do
    if [ -f "$gradle_file" ]; then
        python3 -c "
import sys
with open('$gradle_file', 'r') as f:
    lines = f.readlines()
result = []
skip_depth = 0
i = 0
while i < len(lines):
    stripped = lines[i].strip()
    if stripped.startswith('android {') or stripped == 'android{':
        skip_depth = 1
        i += 1
        while i < len(lines) and skip_depth > 0:
            for ch in lines[i]:
                if ch == '{': skip_depth += 1
                elif ch == '}': skip_depth -= 1
            i += 1
        continue
    result.append(lines[i])
    i += 1
with open('$gradle_file', 'w') as f:
    f.writelines(result)
"
    fi
done

# Remove AndroidApplicationComposeConventionPlugin registration attempt
# (it won't compile without AGP)
cat > "$CONVENTION_DIR/AndroidApplicationComposeConventionPlugin.kt" << 'KOTLIN'
import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidApplicationComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        // No-op: Android disabled for Flatpak build
    }
}
KOTLIN

cat > "$CONVENTION_DIR/AndroidApplicationConventionPlugin.kt" << 'KOTLIN'
import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        // No-op: Android disabled for Flatpak build
    }
}
KOTLIN

echo "=== Android targets disabled successfully ==="
