#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LIBS="$SCRIPT_DIR/../libs"
VMTEST="$SCRIPT_DIR/../vmtest"
OUT="$VMTEST/out"
GRADLE_CACHE="$HOME/.gradle/caches/modules-2/files-2.1"

# ── helpers ──────────────────────────────────────────────────────────────────
find_jar() {
    local group="$1" artifact="$2"
    find "$GRADLE_CACHE/$group/$artifact" -name "${artifact}-*.jar" \
        ! -name "*sources*" ! -name "*javadoc*" 2>/dev/null \
        | sort -V | tail -1
}

require_jar() {
    local path="$1" label="$2"
    if [[ -z "$path" || ! -f "$path" ]]; then
        echo "ERROR: cannot find $label jar (looked in gradle cache)" >&2
        exit 1
    fi
    echo "$path"
}

# ── locate deps ───────────────────────────────────────────────────────────────
SLF4J_API=$(find_jar org.slf4j slf4j-api)
LOG4J_API=$(find_jar org.apache.logging.log4j log4j-api)
LOG4J_CORE=$(find_jar org.apache.logging.log4j log4j-core)
LOG4J_SLF4J=$(find_jar org.apache.logging.log4j log4j-slf4j2-impl)
FASTUTIL=$(find_jar it.unimi.dsi fastutil)
COMMONS_LANG3=$(find_jar org.apache.commons commons-lang3)
COMMONS_IO=$(find_jar commons-io commons-io)
ASM=$(find_jar org.ow2.asm asm)
ASM_TREE=$(find_jar org.ow2.asm asm-tree)
ASM_COMMONS=$(find_jar org.ow2.asm asm-commons)
ASM_ANALYSIS=$(find_jar org.ow2.asm asm-analysis)
ASM_UTIL=$(find_jar org.ow2.asm asm-util)

require_jar "$SLF4J_API"   "slf4j-api"         > /dev/null
require_jar "$LOG4J_API"   "log4j-api"         > /dev/null
require_jar "$LOG4J_CORE"  "log4j-core"        > /dev/null
require_jar "$LOG4J_SLF4J" "log4j-slf4j2-impl" > /dev/null
require_jar "$FASTUTIL"      "fastutil"           > /dev/null
require_jar "$COMMONS_LANG3" "commons-lang3"      > /dev/null
require_jar "$COMMONS_IO"    "commons-io"         > /dev/null
require_jar "$ASM"         "asm"               > /dev/null
require_jar "$ASM_TREE"    "asm-tree"          > /dev/null
require_jar "$ASM_COMMONS" "asm-commons"       > /dev/null

CP="$LIBS/sedna-mc-1.0.70.jar"
CP="$CP:$SLF4J_API:$LOG4J_API:$LOG4J_CORE:$LOG4J_SLF4J"
CP="$CP:$FASTUTIL:$COMMONS_LANG3:$COMMONS_IO"
CP="$CP:$ASM:$ASM_TREE:$ASM_COMMONS"
[[ -n "$ASM_ANALYSIS" ]] && CP="$CP:$ASM_ANALYSIS"
[[ -n "$ASM_UTIL"     ]] && CP="$CP:$ASM_UTIL"
CP="$CP:$OUT"

# ── compile ──────────────────────────────────────────────────────────────────
mkdir -p "$OUT"
if [[ ! -f "$OUT/VMBootTest.class" || "$VMTEST/VMBootTest.java" -nt "$OUT/VMBootTest.class" ]]; then
    echo "[run_vm_test] Compiling VMBootTest.java..."
    javac -source 11 -target 11 -cp "$CP" -d "$OUT" "$VMTEST/VMBootTest.java"
fi

# ── run ───────────────────────────────────────────────────────────────────────
echo "[run_vm_test] Starting VM..."
exec java \
    --add-opens java.base/java.lang=ALL-UNNAMED \
    -Dlog4j2.configurationFile="$VMTEST/log4j2.xml" \
    -Dlog4j2.skipJansi=true \
    -cp "$CP" \
    VMBootTest
