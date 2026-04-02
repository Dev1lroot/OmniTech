#!/bin/bash

# Target directory
TARGET_DIR="./decompiled"
GRADLE_CACHE="$HOME/.gradle/caches"

mkdir -p "$TARGET_DIR"

# Find all source jars in the Gradle cache
find "$GRADLE_CACHE" -name "*-sources.jar" | while read -r jar_path; do
    # Get the filename (e.g., neoforge-21.0.1-sources.jar)
    jar_name=$(basename "$jar_path")
    
    # Create a specific directory for this jar
    out_path="$TARGET_DIR/$jar_name"
    mkdir -p "$out_path"
    
    echo "Extracting $jar_name..."
    
    # Extract to the specific folder
    unzip -o -q "$jar_path" -d "$out_path"
done

echo "Extraction complete." 
