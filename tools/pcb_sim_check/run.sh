#!/usr/bin/env bash
# Standalone check of the PCB circuit simulator and test benches (no Minecraft needed).
#   Harness — reference circuits that must pass / plausible mistakes that must fail
#   Geo        — a drawn board run through footprints, netlist extraction and the benches
#   Blueprints — every ready-made blueprint passes its own bench with its reference parts
# Run from the repository root: tools/pcb_sim_check/run.sh
set -euo pipefail
GSON=$(find ~/.gradle/caches -name "gson-2.*.jar" ! -name "*sources*" | head -1)
OUT=$(mktemp -d)
SRC=src/main/java/com/dev1lroot/mcmods/omnitech/pcb
javac -d "$OUT" -cp "$GSON" $SRC/*.java $SRC/sim/*.java tools/pcb_sim_check/*.java
java -cp "$OUT:$GSON" Harness
java -cp "$OUT:$GSON" Geo
java -cp "$OUT:$GSON" Blueprints
rm -rf "$OUT"
