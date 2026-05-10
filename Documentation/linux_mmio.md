# Linux MMIO Programming — OmniTech Bus Device

The **Logic Machine** runs Buildroot Linux inside a RISC-V Sedna emulator.
When Linux is booted, all peripherals on the cable network (GPIO ports, pixel
displays with portId > 0) are accessible from userspace via a custom MMIO
device mapped at physical address **`0x40000000`**.

No kernel module is required. The device is enumerated in the DTB under
`/proc/device-tree/soc/omnitech-bus@40000000/` and accessible through
`/dev/mem` with the busybox `devmem` tool or `mmap()` from C.

---

## Device Layout

```
Physical base: 0x40000000
Total size:    1 MB (0x100000)

Offset   Size  Access  Description
──────────────────────────────────────────────────────
0x000    4     R       Magic: 0x4F544543 ("OTEC")
0x004    4     R       Version: 1
0x008    4     R       GPIO port count
0x00C    4     R       Display slot count (portId > 0)
0x010    4     R/W1C   IRQ status — bitmask of GPIO indices that changed
0x014    4     R/W     IRQ mask   — set bits to enable IRQ on GPIO change

0x100 + N*4   4  R/W  GPIO port N value (0–15)

0x10000 + (portId-1)*0x10100        Display slot header:
  +0x000  4  R    pixel width  (clusterCols × blockSize, max 128)
  +0x004  4  R    pixel height (clusterRows × blockSize, max 128)
  +0x008  4  W    flush  — write any value to push staged pixels to display
  +0x00C  4  W    clear  — write any value to reset all pixels to black
  +0x010  4×W×H R/W  framebuffer — pixel[y*width + x] = 0x00RRGGBB, row-major
```

Display slot addresses for each portId:

| portId | Slot base       |
|--------|-----------------|
| 1      | `0x40010000`    |
| 2      | `0x40020100`    |
| 3      | `0x40030200`    |
| 4      | `0x40040300`    |
| 5      | `0x40050400`    |
| 6      | `0x40060500`    |
| 7      | `0x40070600`    |
| 8      | `0x40080700`    |

---

## Device Discovery

```sh
# Confirm the device is present
cat /proc/device-tree/soc/omnitech-bus@40000000/compatible
# → omnitech,otec-bus-v1

# See its physical address range in the memory map
grep -i omnitech /proc/iomem

# Quick sanity check — read magic number (should print 0x4F544543)
devmem 0x40000000
```

---

## GPIO via `devmem`

```sh
BASE=0x40000000

# How many GPIO ports are connected?
devmem $((BASE + 0x008))

# Read GPIO port 0 (0–15 signal level)
devmem $((BASE + 0x100))

# Read GPIO port 3
devmem $((BASE + 0x10C))        # 0x100 + 3*4

# Write value 15 to GPIO port 0 (full redstone signal)
devmem $((BASE + 0x100)) w 15

# Write value 0 to GPIO port 1
devmem $((BASE + 0x104)) w 0

# Poll GPIO port 0 every second
while true; do
    val=$(devmem $((BASE + 0x100)))
    echo "GPIO0 = $val"
    sleep 1
done
```

### Interrupt-driven GPIO

Enable the IRQ for GPIO ports 0 and 1, then wait for a change:

```sh
BASE=0x40000000

# Enable IRQ for GPIO 0 (bit 0) and GPIO 1 (bit 1)
devmem $((BASE + 0x014)) w 3

# Read status — blocks until non-zero (use in a script loop)
while true; do
    status=$(devmem $((BASE + 0x010)))
    [ "$status" != "0x00000000" ] && break
    sleep 0.05
done

# status bits tell you which GPIOs changed; reading also clears the register
echo "Changed mask: $status"
```

---

## Display Framebuffer via `devmem`

Each display with portId > 0 has an independent framebuffer.
Pixel writes are staged in Java memory on the VM thread; writing to the
**flush register** (`+0x008`) pushes all staged pixels to the actual
display block entity on the next server tick (≈50 ms).

```sh
# Display portId=1 base
DISP=0x40010000

# Read actual canvas dimensions
W=$(devmem $DISP)
H=$(devmem $((DISP + 4)))
echo "Canvas: ${W}x${H}"

# Write a red pixel at (0, 0)   — pixel index 0 → offset 0x10
devmem $((DISP + 0x10)) w 0xFF0000

# Write a green pixel at (1, 0) — pixel index 1 → offset 0x14
devmem $((DISP + 0x14)) w 0x00FF00

# Write a blue pixel at (0, 1) — pixel index = 1*W + 0 → offset 0x10 + W*4
devmem $((DISP + 0x10 + W*4)) w 0x0000FF

# Flush staged pixels to the display block
devmem $((DISP + 0x08)) w 1

# Clear all pixels to black, then flush
devmem $((DISP + 0x0C)) w 1
```

---

## C — Memory-mapped Framebuffer

For programs that need to draw many pixels efficiently, use `mmap()` instead
of individual `devmem` calls.

```c
#include <fcntl.h>
#include <stdint.h>
#include <stdio.h>
#include <sys/mman.h>
#include <unistd.h>

#define BUS_BASE      0x40000000UL
#define BUS_SIZE      0x100000          /* 1 MB */
#define DISP_STRIDE   0x10100
#define DISP_HEADER   0x10

/* Map the entire OmniTech bus region */
static volatile uint32_t *map_bus(void) {
    int fd = open("/dev/mem", O_RDWR | O_SYNC);
    if (fd < 0) { perror("open /dev/mem"); return NULL; }
    void *p = mmap(NULL, BUS_SIZE, PROT_READ | PROT_WRITE,
                   MAP_SHARED, fd, BUS_BASE);
    close(fd);
    return (p == MAP_FAILED) ? NULL : (volatile uint32_t *)p;
}

/* Pixel index inside the framebuffer region (skip 4-byte header regs) */
static inline volatile uint32_t *fb_pixel(volatile uint32_t *bus,
                                           int portId, int x, int y) {
    int slot = portId - 1;
    int w = bus[(0x10000 + slot * DISP_STRIDE) / 4 + 0]; /* width  */
    int base_words = (0x10000 + slot * DISP_STRIDE + DISP_HEADER) / 4;
    return &bus[base_words + y * w + x];
}

static void disp_flush(volatile uint32_t *bus, int portId) {
    int slot = portId - 1;
    bus[(0x10000 + slot * DISP_STRIDE + 0x08) / 4] = 1;
}

static void disp_clear(volatile uint32_t *bus, int portId) {
    int slot = portId - 1;
    bus[(0x10000 + slot * DISP_STRIDE + 0x0C) / 4] = 1;
}

int main(void) {
    volatile uint32_t *bus = map_bus();
    if (!bus) return 1;

    /* Verify magic */
    if (bus[0] != 0x4F544543) {
        fprintf(stderr, "OmniTech bus not found\n");
        return 1;
    }

    int portId = 1;
    int slot   = portId - 1;
    int w = bus[(0x10000 + slot * DISP_STRIDE) / 4 + 0];
    int h = bus[(0x10000 + slot * DISP_STRIDE) / 4 + 1];
    printf("Display portId=%d: %dx%d\n", portId, w, h);

    /* Fill entire canvas with a gradient */
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            int r = (x * 255) / (w - 1);
            int g = (y * 255) / (h - 1);
            *fb_pixel(bus, portId, x, y) = (r << 16) | (g << 8);
        }
    }
    disp_flush(bus, portId);

    /* Read GPIO port 0 */
    uint32_t gpio0 = bus[0x100 / 4];
    printf("GPIO0 = %u\n", gpio0);

    /* Write 7 to GPIO port 1 */
    bus[0x104 / 4] = 7;

    return 0;
}
```

Compile for RISC-V (cross-compile on your host, copy via floppy):

```sh
riscv64-unknown-linux-gnu-gcc -O2 -static -o mmio_demo mmio_demo.c
```

---

## Shell Script — Gradient Sweep

Draws a full-width gradient on display portId=1 using only `devmem`:

```sh
#!/bin/sh
DISP=0x40010000
W=$(devmem $DISP)
H=$(devmem $((DISP + 4)))

for y in $(seq 0 $((H - 1))); do
    g=$(( y * 255 / (H - 1) ))
    for x in $(seq 0 $((W - 1))); do
        r=$(( x * 255 / (W - 1) ))
        color=$(printf "0x%06X" $(( (r << 16) | (g << 8) )))
        offset=$(( 0x10 + (y * W + x) * 4 ))
        devmem $((DISP + offset)) w $color
    done
done

devmem $((DISP + 0x08)) w 1   # flush
echo "Done — ${W}x${H} gradient flushed"
```

> **Note:** Calling `devmem` for every pixel is slow for large canvases.
> Use the C + `mmap()` approach for anything bigger than ~16×16.

---

## GPIO Read/Write from C

```c
/* Read GPIO port N */
static uint32_t gpio_read(volatile uint32_t *bus, int n) {
    return bus[(0x100 + n * 4) / 4];
}

/* Write value to GPIO port N (0–15, clamped by the block entity) */
static void gpio_write(volatile uint32_t *bus, int n, uint32_t val) {
    bus[(0x100 + n * 4) / 4] = val;
}

/* Enable IRQ for GPIO ports 0 and 1, poll for change */
static uint32_t gpio_wait_change(volatile uint32_t *bus) {
    bus[0x014 / 4] = 0x3;              /* mask: bits 0 and 1 */
    uint32_t status;
    do { status = bus[0x010 / 4]; }    /* read clears W1C status */
    while (status == 0);
    return status;
}
```

---

## Display portId vs. Terminal

| portId | Role |
|--------|------|
| `0`    | Terminal (VT100 text output from the boot console — **do not write via MMIO**) |
| `1`–`8` | Pixel framebuffer — writable via MMIO at the slot addresses above |

Display blocks with portId=0 are managed by the terminal emulator and should
not be written via MMIO; they will be overwritten by the next terminal output.

---

## Floppy Drives

Floppy drives connected to the Logic Machine cable network appear as standard
VirtIO block devices in Linux. They are always present in the DTB regardless
of whether a disk is currently inserted.

| Drive ID | Linux device | Capacity  |
|----------|-------------|-----------|
| 0        | `/dev/vdc`  | 1.44 MB   |
| 1        | `/dev/vdd`  | 1.44 MB   |
| 2        | `/dev/vde`  | 1.44 MB   |
| 3        | `/dev/vdf`  | 1.44 MB   |

> **Why `/dev/vd*` and not `/dev/fd*`?**
> `/dev/fd0`–`/dev/fd3` are created by the legacy PC floppy controller driver
> (`CONFIG_BLK_DEV_FD`), which talks to real 8272A-compatible FDC hardware.
> OmniTech drives are exposed as **VirtIO block devices** — the same transport
> used by the boot and root filesystems — so the kernel's `virtio_blk` driver
> assigns them `/dev/vd*` names in the order they were added to the board.
> `/dev/vda` and `/dev/vdb` are the Buildroot system partitions; floppy drives
> start from `/dev/vdc`.

The underlying disk image (stored in the Floppy Disk item's `FLOPPY_DATA`
component) is connected automatically within 20 ticks of the drive being
in the cable network. Any writes by Linux are flushed back to the item every
5 seconds so data survives world saves.

### Checking disk presence

```sh
# A connected, formatted floppy shows a non-zero capacity:
blockdev --getsize64 /dev/vdc

# List detected VirtIO block devices:
ls -la /dev/vd*
```

### Formatting a blank disk

Floppy disks start empty (all zeros). Format them as ext2 before use.
Busybox's `mke2fs` always creates ext2 and does not accept `-t`:

```sh
mke2fs /dev/vdc
mount /dev/vdc /mnt
ls /mnt
```

### Reading and writing files

```sh
mount /dev/vdc /mnt

# Write a file
echo "hello from linux" > /mnt/hello.txt

# Read it back
cat /mnt/hello.txt

# Unmount to flush all dirty pages before ejecting the disk in-game
umount /mnt
```

> **Important:** Always `umount` the disk before removing it from the Floppy Drive
> in-game. Removing a mounted disk while Linux holds dirty pages will cause the
> write-back to overwrite your changes with stale data.

### Using dd for raw sector access

```sh
# Dump the entire floppy to a file (for backup or inspection)
dd if=/dev/vdc of=/tmp/floppy.img bs=512

# Restore an image to the floppy
dd if=/tmp/floppy.img of=/dev/vdc bs=512

# Write a raw binary directly to sector 0
dd if=/path/to/bootloader.bin of=/dev/vdc bs=512 count=1
```
