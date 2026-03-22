#!/bin/bash
# disable-android-for-flatpak.sh
#
# Strips all Android-related configuration from the project so it can
# build inside the Flatpak sandbox where no Android SDK is available.
# This script modifies files IN-PLACE — only run during Flatpak builds.

set -euo pipefail

echo "=== Disabling Android targets for Flatpak build ==="

CONVENTION_DIR="build-logic/convention/src/main/kotlin"

# Note: Step 0 (offline repo) removed — using --share=network instead

# ─────────────────────────────────────────────────────────────────────
# 1. Root build.gradle.kts — comment out Android plugin declarations
# ─────────────────────────────────────────────────────────────────────
echo "[0/7] Removing Android & non-essential dependencies from build-logic/convention/build.gradle.kts"
sed -i \
    -e '/compileOnly(libs.android.gradlePlugin)/d' \
    -e '/compileOnly(libs.android.tools.common)/d' \
    -e '/alias(libs.plugins.flatpak.gradle.generator)/d' \
    -e '/flatpakGradleGenerator/,/^}/d' \
    build-logic/convention/build.gradle.kts

# Stub out Android utility files that reference Android SDK
cat > "$CONVENTION_DIR/zed/rainxch/githubstore/convention/AndroidCompose.kt" << 'KOTLIN'
package zed.rainxch.githubstore.convention

import org.gradle.api.Project

// Stubbed out for Flatpak build — no Android SDK available
internal fun Project.configureAndroidCompose() {}
KOTLIN

cat > "$CONVENTION_DIR/zed/rainxch/githubstore/convention/KotlinAndroid.kt" << 'KOTLIN'
package zed.rainxch.githubstore.convention

import org.gradle.api.Project

// Stubbed out for Flatpak build — no Android SDK available
internal fun Project.configureKotlinAndroid(project: Project) {}
KOTLIN

echo "[1/7] Patching root build.gradle.kts"
sed -i \
    -e 's|alias(libs.plugins.android.application)|// alias(libs.plugins.android.application)|' \
    -e 's|alias(libs.plugins.android.library)|// alias(libs.plugins.android.library)|' \
    -e 's|alias(libs.plugins.android.kotlin.multiplatform.library)|// alias(libs.plugins.android.kotlin.multiplatform.library)|' \
    -e '/alias(libs.plugins.flatpak.gradle.generator)/d' \
    -e '/flatpakGradleGenerator/,/^}/d' \
    -e '/alias(libs.plugins.compose.hot.reload)/d' \
    build.gradle.kts

# Also remove hot-reload from composeApp/build.gradle.kts
sed -i \
    -e '/alias(libs.plugins.compose.hot.reload)/d' \
    composeApp/build.gradle.kts

# ─────────────────────────────────────────────────────────────────────
# 2. Convention plugins — replace Android plugin applies with no-ops
# ─────────────────────────────────────────────────────────────────────
CONVENTION_DIR="build-logic/convention/src/main/kotlin"

echo "[2/7] Patching KmpLibraryConventionPlugin (remove Android library plugin + config)"
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

echo "[3/7] Patching CmpApplicationConventionPlugin (remove Android application)"
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

echo "[4/7] Patching CmpLibraryConventionPlugin & CmpFeatureConventionPlugin"

# CmpLibraryConventionPlugin — remove Android library dependency and debugImplementation
sed -i \
    -e 's|apply("com.android.library")|// apply("com.android.library")|' \
    -e '/debugImplementation/d' \
    "$CONVENTION_DIR/CmpLibraryConventionPlugin.kt" 2>/dev/null || true

sed -i \
    -e 's|apply("com.android.library")|// apply("com.android.library")|' \
    -e '/debugImplementation/d' \
    -e '/androidMainImplementation/d' \
    "$CONVENTION_DIR/CmpFeatureConventionPlugin.kt" 2>/dev/null || true

# Remove configureKotlinAndroid calls and Android extension blocks (only at call sites)
for f in "$CONVENTION_DIR"/*.kt "$CONVENTION_DIR"/zed/rainxch/githubstore/convention/*.kt; do
    [ -f "$f" ] || continue
    # Skip files that define these functions (declarations, not call sites)
    grep -q "fun Project.configureAndroidTarget" "$f" && continue
    grep -q "fun Project.configureKotlinAndroid" "$f" && continue
    sed -i \
        -e 's|configureAndroidTarget()|// configureAndroidTarget()|g' \
        -e 's|configureKotlinAndroid(this)|// configureKotlinAndroid(this)|g' \
        "$f"
done

# ─────────────────────────────────────────────────────────────────────
# 3. KotlinMultiplatform.kt — skip Android configuration
# ─────────────────────────────────────────────────────────────────────
echo "[5/7] Patching KotlinMultiplatform.kt"
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
echo "[6/7] Removing android {} blocks from module build.gradle.kts files"

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

# Remove androidMain.dependencies { ... } block
content = remove_block(content, 'androidMain.dependencies')

with open('composeApp/build.gradle.kts', 'w') as f:
    f.write(content)
"

# All modules — remove android {} and androidMain { ... } blocks
find . -name 'build.gradle.kts' -not -path './build-logic/*' -not -path './.flatpak-builder/*' | while read gradle_file; do
    # Skip if file has no android references
    grep -qE 'android\s*\{|androidMain\s*\{|androidMain\.dependencies\s*\{' "$gradle_file" || continue
    python3 -c "
import sys

with open('$gradle_file', 'r') as f:
    content = f.read()

def remove_block(text, keyword):
    result = []
    i = 0
    while i < len(text):
        line_start = text.rfind('\n', 0, i) + 1
        prefix = text[line_start:i].strip()
        if text[i:].startswith(keyword + ' {') or text[i:].startswith(keyword + '{'):
            if prefix == '' or prefix.endswith('\n'):
                brace_start = text.index('{', i)
                depth = 1
                j = brace_start + 1
                while j < len(text) and depth > 0:
                    if text[j] == '{': depth += 1
                    elif text[j] == '}': depth -= 1
                    j += 1
                if j < len(text) and text[j] == '\n':
                    j += 1
                i = j
                continue
        result.append(text[i])
        i += 1
    return ''.join(result)

content = remove_block(content, 'android')
content = remove_block(content, 'androidMain.dependencies')
content = remove_block(content, 'androidMain')

with open('$gradle_file', 'w') as f:
    f.write(content)
"
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

# RoomConventionPlugin — remove kspAndroid dependency (no Android target)
cat > "$CONVENTION_DIR/RoomConventionPlugin.kt" << 'KOTLIN'
import androidx.room.gradle.RoomExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import zed.rainxch.githubstore.convention.libs

class RoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.google.devtools.ksp")
                apply("androidx.room")
            }

            extensions.configure<RoomExtension> {
                schemaDirectory("$projectDir/schemas")
            }

            dependencies {
                "commonMainApi"(libs.findLibrary("androidx-room-runtime").get())
                "commonMainApi"(libs.findLibrary("sqlite-bundled").get())
                "kspJvm"(libs.findLibrary("androidx-room-compiler").get())
            }
        }
    }
}
KOTLIN

echo "=== Android targets disabled successfully ==="
