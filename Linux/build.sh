#!/usr/bin/env bash
# Build a minimal RV32 Linux Image for the OmniTech LogicMachine VM.
#
# What this builds:
#   - Cross-toolchain (rv32imafdc / ilp32d, via Buildroot)
#   - Linux kernel 6.6 with CLINT + ns16550a UART drivers
#   - Busybox static initramfs (embedded in the kernel Image)
#
# Prerequisites:
#   sudo apt-get install -y \
#       build-essential git wget cpio unzip rsync bc \
#       flex bison libssl-dev libelf-dev python3 file
#
# Usage:
#   cd Linux/
#   bash build.sh            # full build (~30-90 min first time)
#   bash build.sh clean      # wipe build and start over
#
# Output:
#   Linux/Image   -- load with: /loadbin Linux/Image

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="${SCRIPT_DIR}/build"
BR_VERSION="2024.02.9"
BR_DIR="${BUILD_DIR}/buildroot-${BR_VERSION}"
NCPUS=$(nproc)

echo "=== OmniTech Linux Builder ==="
echo "    Buildroot  : ${BR_VERSION}"
echo "    Jobs       : ${NCPUS}"
echo ""

# ── Clean ─────────────────────────────────────────────────────────────────────
if [ "${1:-}" = "clean" ]; then
    echo "==> Removing ${BUILD_DIR} ..."
    rm -rf "${BUILD_DIR}"
    echo "Done."
    exit 0
fi

# ── Prerequisite check ────────────────────────────────────────────────────────
MISSING=()
for cmd in wget tar make gcc g++ flex bison bc cpio python3 file; do
    command -v "$cmd" &>/dev/null || MISSING+=("$cmd")
done
if [ ${#MISSING[@]} -gt 0 ]; then
    echo "ERROR: missing commands: ${MISSING[*]}"
    echo "Install with:"
    echo "  sudo apt-get install -y build-essential git wget cpio unzip rsync bc \\"
    echo "      flex bison libssl-dev libelf-dev python3 file"
    exit 1
fi

mkdir -p "${BUILD_DIR}"

# ── Download Buildroot ────────────────────────────────────────────────────────
BR_TAR="${BUILD_DIR}/buildroot-${BR_VERSION}.tar.gz"
if [ ! -f "${BR_TAR}" ]; then
    echo "==> Downloading Buildroot ${BR_VERSION} ..."
    wget -q --show-progress \
        "https://buildroot.org/downloads/buildroot-${BR_VERSION}.tar.gz" \
        -O "${BR_TAR}"
fi
if [ ! -d "${BR_DIR}" ]; then
    echo "==> Extracting Buildroot ..."
    tar xzf "${BR_TAR}" -C "${BUILD_DIR}"
fi

# ── Configure Buildroot ───────────────────────────────────────────────────────
cd "${BR_DIR}"

if [ ! -f ".config" ]; then
    echo "==> Applying qemu_riscv32_virt_defconfig ..."
    make qemu_riscv32_virt_defconfig

    echo "==> Overlaying OmniTech settings ..."
    # Switch rootfs: ext2 → cpio (needed for linux-rebuild-with-initramfs)
    sed -i 's/^BR2_TARGET_ROOTFS_EXT2=y/# BR2_TARGET_ROOTFS_EXT2 is not set/' .config
    sed -i 's/^BR2_TARGET_ROOTFS_EXT2_2=y/# BR2_TARGET_ROOTFS_EXT2_2 is not set/' .config
    grep -q 'BR2_TARGET_ROOTFS_CPIO=y'      .config || echo 'BR2_TARGET_ROOTFS_CPIO=y'      >> .config
    grep -q 'BR2_TARGET_ROOTFS_CPIO_GZIP=y' .config || echo 'BR2_TARGET_ROOTFS_CPIO_GZIP=y' >> .config
    grep -q 'BR2_PACKAGE_BUSYBOX=y'         .config || echo 'BR2_PACKAGE_BUSYBOX=y'         >> .config
    # Write the kernel fragment path (use a temp var to avoid quoting issues)
    FRAG_ESCAPED="${BUILD_DIR}/omnitech_linux.config"
    sed -i '/BR2_LINUX_KERNEL_CONFIG_FRAGMENT_FILES/d' .config
    echo "BR2_LINUX_KERNEL_CONFIG_FRAGMENT_FILES=\"${FRAG_ESCAPED}\"" >> .config

    make olddefconfig
fi

# ── Write Linux kernel config fragment ───────────────────────────────────────
cat > "${BUILD_DIR}/omnitech_linux.config" << 'LINUXCFG'
# OmniTech LogicMachine — kernel config overlay
# Applied on top of rv32_defconfig via Buildroot fragment mechanism.

# CLINT timer driver (0x02000000 in DTB)
CONFIG_CLINT_TIMER=y

# ns16550a UART (0x10001000 in DTB, no PLIC needed — polling TX via LSR=0x60)
CONFIG_SERIAL_8250=y
CONFIG_SERIAL_8250_CONSOLE=y
CONFIG_SERIAL_OF_PLATFORM=y
CONFIG_SERIAL_8250_NR_UARTS=4
CONFIG_SERIAL_8250_RUNTIME_UARTS=1

# SBI v0.1 legacy + v0.2 extensions (set_timer / console_putchar)
CONFIG_RISCV_SBI=y
CONFIG_RISCV_SBI_V01=y

# Initramfs (embedded cpio.gz, injected by linux-rebuild-with-initramfs)
CONFIG_BLK_DEV_INITRD=y
CONFIG_INITRAMFS_COMPRESSION_GZIP=y
CONFIG_RD_GZIP=y

# Minimal virtual filesystem
CONFIG_DEVTMPFS=y
CONFIG_DEVTMPFS_MOUNT=y
CONFIG_TMPFS=y
CONFIG_PROC_FS=y
CONFIG_SYSFS=y
CONFIG_PRINTK=y

# Size optimisation
CONFIG_CC_OPTIMIZE_FOR_SIZE=y
LINUXCFG

# ── Build ─────────────────────────────────────────────────────────────────────
echo "==> Building (first time: 30-90 min) ..."
make -j"${NCPUS}" 2>&1 | tee "${BUILD_DIR}/build.log" || {
    echo ""
    echo "ERROR: Build failed. Last 30 lines of build.log:"
    tail -30 "${BUILD_DIR}/build.log"
    exit 1
}

# ── Embed initramfs in kernel ─────────────────────────────────────────────────
echo "==> Embedding initramfs in kernel ..."
make linux-rebuild-with-initramfs 2>&1 | tee -a "${BUILD_DIR}/build.log"

# ── Copy output ───────────────────────────────────────────────────────────────
OUT="${BR_DIR}/output/images/Image"
if [ ! -f "${OUT}" ]; then
    echo "ERROR: ${OUT} not found. Check ${BUILD_DIR}/build.log"
    exit 1
fi

cp "${OUT}" "${SCRIPT_DIR}/Image"
SIZE=$(du -h "${SCRIPT_DIR}/Image" | cut -f1)

echo ""
echo "=== Done ==="
echo "    Image : Linux/Image (${SIZE})"
echo ""
echo "In-game steps:"
echo "  1. Insert a Microcontroller into the Logic Machine"
echo "  2. Connect Expansion Slots with RAM cards (>= 32 MB total)"
echo "  3. Run: /loadbin Linux/Image"
echo "  4. Press RUN in the Logic Machine GUI"
echo "  5. Open the CONSOLE tab — Linux boot log should appear"
