#!/bin/bash

# Target directory
TARGET_DIR="./decompiled"
GRADLE_CACHE="$HOME/.gradle/caches"
NEOFORM_INTERMEDIATE="$HOME/.gradle/caches/neoformruntime/intermediate_results"

mkdir -p "$TARGET_DIR"

# Find all source jars in the Gradle cache
find "$GRADLE_CACHE/modules-2" -name "*-sources.jar" | while read -r jar_path; do
    jar_name=$(basename "$jar_path")
    out_path="$TARGET_DIR/$jar_name"
    mkdir -p "$out_path"
    echo "Extracting $jar_name..."
    unzip -o -q "$jar_path" -d "$out_path"
done

# Extract Minecraft + NeoForge patched sources from neoformruntime cache
# These are the decompiled+patched Minecraft sources produced by NeoForge MDG
echo "Extracting Minecraft+NeoForge patched sources..."
for patches_zip in "$NEOFORM_INTERMEDIATE"/applyNeoforgePatches_*_output.zip; do
    [ -f "$patches_zip" ] || continue
    hash_txt="${patches_zip/_output.zip/.txt}"
    # Determine NeoForge version from the .txt metadata file
    nf_version=$(grep -oP '(?<=neoforge/)[^/]+(?=/)' "$hash_txt" 2>/dev/null | head -1)
    if [ -n "$nf_version" ]; then
        out_dir="$TARGET_DIR/minecraft-neoforge-${nf_version}"
        echo "  -> minecraft-neoforge-${nf_version}"
        mkdir -p "$out_dir"
        unzip -o -q "$patches_zip" -d "$out_dir"
    fi
done

echo "Extraction complete."
