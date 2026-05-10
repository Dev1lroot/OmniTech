#!/bin/bash
# Cruel Angel's Thesis (Neon Genesis Evangelion OP) — OmniTech Speaker MMIO test
#
# Runs under Linux inside the LogicMachine VM.
# Requires root (for /dev/mem access) — Buildroot default user is root.
#
# OmniTech bus base:  0x40000000
# Speaker region:     base + 0x200 (4 bytes per speaker, speaker 0 used here)
#   bits  7:0  = volume  (0–255)
#   bits 23:8  = freq Hz (0 = stop)
#   Example word write: (freq << 8) | volume
#
# Note names and frequencies (Hz):
#   Bb4=466  C5=523  B4=494  D5=587  Eb5=622  F5=698  G4=392  G5=784

SPEAKER_ADDR=$(( 0x40000000 + 0x200 ))   # speaker 0
VOLUME=200                                 # 0-255

# Write a 32-bit word to a physical address via /dev/mem
write_word() {
    local addr=$1 val=$2
    local b0=$(( val        & 0xFF ))
    local b1=$(( (val >> 8) & 0xFF ))
    local b2=$(( (val >> 16)& 0xFF ))
    local b3=$(( (val >> 24)& 0xFF ))
    printf "$(printf '\\x%02x\\x%02x\\x%02x\\x%02x' $b0 $b1 $b2 $b3)" \
        | dd of=/dev/mem bs=4 count=1 seek=$(( addr / 4 )) conv=notrunc 2>/dev/null
}

# Play one note: set tone, hold for dur seconds, then brief silence
play_note() {
    local freq=$1 dur=$2
    write_word $SPEAKER_ADDR $(( (freq << 8) | VOLUME ))
    sleep "$dur"
    write_word $SPEAKER_ADDR 0
    sleep 0.05
}

echo "Playing Cruel Angel's Thesis..."

# Phrase 1  (eighth notes @ ~120 BPM = 0.25 s each, last note held as quarter)
play_note  392 0.25   # G4
play_note  523 0.25   # C5
play_note  494 0.25   # B4
play_note  523 0.25   # C5
play_note  587 0.25   # D5
play_note  622 0.25   # Eb5
play_note  587 0.25   # D5
play_note  523 0.50   # C5  (quarter)

# Phrase 2
play_note  466 0.25   # Bb4
play_note  466 0.25   # Bb4
play_note  523 0.25   # C5
play_note  587 0.25   # D5
play_note  622 0.25   # Eb5
play_note  587 0.25   # D5
play_note  523 0.25   # C5
play_note  587 0.25   # D5
play_note  698 0.25   # F5
play_note  784 1.00   # G5  (half note — hold)

# Ensure speaker is silenced when done
write_word $SPEAKER_ADDR 0
echo "Done."
