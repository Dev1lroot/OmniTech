# Logic Network Programming — RISC-V Examples

The **Logic Machine** executes **RISC-V RV32IM** assembly.  A microcontroller chip
(programmed at the **Programming Station**) is inserted into the machine.  Every device
on the same cable network — GPIO Ports, Displays, Expansion Slots (RAM cards), and
Floppy Drives — is addressable from the running program.

---

## Quick Reference

### Registers

| Alias | x-reg | Role |
|-------|-------|------|
| `zero` | `x0` | hardwired 0 — writes discarded |
| `ra` | `x1` | return address (saved by `call`, used by `ret`) |
| `sp` | `x2` | stack pointer — **init manually** with `li sp, <top>` |
| `a0`–`a7` | `x10`–`x17` | ECALL args / return values; `a7` = syscall number |
| `s0`–`s11` | `x8`–`x9`, `x18`–`x27` | callee-saved — preserved across `call` by convention |
| `t0`–`t6` | `x5`–`x7`, `x28`–`x31` | temporaries — **not** preserved across `call` |

### ECALL Syscall Table

Set **`a7`** to the number below, put arguments in **`a0`–`a5`**, result returns in **`a0`**
(and `a1` for `DISP_DIM`).

| `a7` | Name | Inputs | Output |
|------|------|--------|--------|
| `0` | `HALT` | — | — |
| `1` | `SLP` | `a0` = ticks | — |
| `2` | `RND` | `a0` = min, `a1` = max | `a0` = value |
| `10` | `GPIO_OUT` | `a0` = portId, `a1` = value (0–15) | — |
| `11` | `GPIO_IN` | `a0` = portId | `a0` = signal (0–15) |
| `20` | `DISP_SET` | `a0`=id, `a1`=x, `a2`=y, `a3`=0xRRGGBB | — |
| `21` | `DISP_RST` | `a0` = id | — |
| `22` | `DISP_DIM` | `a0` = id | `a0`=width, `a1`=height |
| `23` | `DISP_LINE` | `a0`=id, `a1`=x1, `a2`=y1, `a3`=x2, `a4`=y2, `a5`=color | — |
| `24` | `DISP_RECT` | `a0`=id, `a1`=x, `a2`=y, `a3`=w, `a4`=h, `a5`=color | — |
| `25` | `DISP_BLIT` | `a0`=id, `a1`=x, `a2`=y, `a3`=w, `a4`=h, `a5`=ramAddr | — |
| `30` | `FLOPPY` | `a0`=driveId, `a1`=sector, `a2`=dstAddr | `a0`=1 on success |

### GPIO MMIO (alternative to ECALL)

GPIO port N can be read/written directly using load/store instructions:

```
address = 0xF0000000 + portId × 4
```

```asm
li   t0, 0xF0000000    # base address
lw   a0, 0(t0)         # read  port 0
sw   a0, 4(t0)         # write port 1  (value clamped to 0–15)
```

### BLIT pixel format (VRAM in RAM)

Each pixel is **3 consecutive bytes**: R, G, B (each 0–255), row-major order:

```
pixel (col, row)  →  RAM[ addr + (row × width + col) × 3 ]
                     RAM[ addr + (row × width + col) × 3 + 1 ]
                     RAM[ addr + (row × width + col) × 3 + 2 ]
```

---

## GPIO Examples

### 1 — LED Blink

Toggles GPIO port 0 between full signal and off every second.
Connect a redstone lamp (or any redstone device) to the GPIO output face.

```asm
# Hardware: GPIO Port ID 0 on the cable network.

main:
    li   a7, 10          # GPIO_OUT
    li   a0, 0           # port 0
    li   a1, 15          # full signal (ON)
    ecall

    li   a7, 1           # SLP
    li   a0, 20          # 20 ticks = 1 second
    ecall

    li   a7, 10
    li   a0, 0
    li   a1, 0           # signal 0 (OFF)
    ecall

    li   a7, 1
    li   a0, 20
    ecall

    j    main
```

---

### 2 — Signal Mirror

Reads GPIO port 0 every tick and echoes the value to GPIO port 1.
Useful for relay logic, signal conditioning, or distance bridging.

```asm
# Hardware: GPIO Port ID 0 (input face towards redstone source),
#           GPIO Port ID 1 (output face towards redstone target).

loop:
    li   a7, 11          # GPIO_IN
    li   a0, 0           # read port 0
    ecall                # a0 = signal (0-15)

    mv   a1, a0          # a1 = signal value to output
    li   a7, 10          # GPIO_OUT
    li   a0, 1           # write to port 1
    ecall

    li   a7, 1
    li   a0, 1           # poll every tick
    ecall

    j    loop
```

---

### 3 — MMIO GPIO Mirror

Identical to Example 2 but uses memory-mapped I/O instead of ECALL.
Fewer instructions per iteration — better for tight real-time loops.

```asm
# GPIO MMIO base: 0xF0000000
# Port 0 → 0xF0000000, Port 1 → 0xF0000004, Port N → 0xF0000000 + N*4

    li   t0, 0xF0000000  # t0 = GPIO MMIO base (constant for the loop)

loop:
    lw   a0, 0(t0)       # read  port 0
    sw   a0, 4(t0)       # write port 1  (automatically clamped to 0-15)

    li   a7, 1
    li   a0, 1           # sleep 1 tick
    ecall

    j    loop
```

---

### 4 — Binary Counter on 4 GPIO Outputs

Counts 0–15 in binary, with each bit driving one GPIO port.
Connect four redstone lamps (IDs 0–3) for a visible 4-bit counter.

```asm
# Hardware: GPIO Ports ID 0, 1, 2, 3 on the cable network.

    li   s0, 0           # s0 = counter (0-15)

tick:
    li   a7, 10          # GPIO_OUT — a7 stays 10 for all four writes

    li   a0, 0
    andi a1, s0, 1       # bit 0
    ecall

    li   a0, 1
    srli a1, s0, 1
    andi a1, a1, 1       # bit 1
    ecall

    li   a0, 2
    srli a1, s0, 2
    andi a1, a1, 1       # bit 2
    ecall

    li   a0, 3
    srli a1, s0, 3
    andi a1, a1, 1       # bit 3
    ecall

    addi s0, s0, 1
    andi s0, s0, 0xF     # wrap counter back to 0 after 15

    li   a7, 1
    li   a0, 5           # 5 ticks between steps (4 Hz)
    ecall

    j    tick
```

---

## Display Examples

### 5 — Solid Fill and White Border

Clears display 0, fills the interior with dark blue, then draws a 1-pixel
white border using four line calls.

```asm
# Hardware: Display port ID 0 (single 16×16 block).

    # Clear to black
    li   a7, 21
    li   a0, 0
    ecall

    # Dark-blue interior (1,1) 14×14
    li   a7, 24          # DISP_RECT
    li   a0, 0
    li   a1, 1           # x
    li   a2, 1           # y
    li   a3, 14          # w
    li   a4, 14          # h
    li   a5, 0x001040
    ecall

    # Border lines  (a0 = 0, a7 = 23 for all four)
    li   a7, 23          # DISP_LINE

    li   a1, 0           # top: (0,0)→(15,0)
    li   a2, 0
    li   a3, 15
    li   a4, 0
    li   a5, 0xFFFFFF
    ecall

    li   a2, 15          # bottom: (0,15)→(15,15)
    li   a4, 15
    ecall

    li   a1, 0           # left: (0,0)→(0,15)
    li   a2, 0
    li   a3, 0
    li   a4, 15
    ecall

    li   a1, 15          # right: (15,0)→(15,15)
    li   a3, 15
    ecall

    halt
```

---

### 6 — Adaptive Split-Screen (GDIM)

Queries the canvas size at runtime via `DISP_DIM`, then fills the top half
cyan and the bottom half dark teal.  Works on **any** cluster configuration
without hard-coding pixel dimensions.

```asm
# Hardware: any Display cluster with port ID 0.

    # Query canvas dimensions
    li   a7, 22          # DISP_DIM
    li   a0, 0
    ecall                # a0 = width, a1 = height

    mv   s0, a0          # s0 = width
    mv   s1, a1          # s1 = height
    srli s2, s1, 1       # s2 = height / 2  (top half rows)

    # Top half — cyan
    li   a7, 24
    li   a0, 0
    li   a1, 0           # x = 0
    li   a2, 0           # y = 0
    mv   a3, s0          # w = full width
    mv   a4, s2          # h = top half
    li   a5, 0x00CCCC
    ecall

    # Bottom half — dark teal
    mv   a2, s2          # y = halfway
    sub  a4, s1, s2      # h = remaining rows
    li   a5, 0x001010
    ecall

    halt
```

---

### 7 — Animated Bouncing Dot

A white dot travels the canvas, reversing direction when it hits an edge.
Velocity components are stored in saved registers so they survive the loop.

```asm
# Hardware: Display port ID 0.
# s0=x  s1=y  s2=dx  s3=dy  s4=maxX  s5=maxY

    li   s0, 4           # initial position
    li   s1, 4
    li   s2, 1           # velocity
    li   s3, 1

    li   a7, 22          # DISP_DIM
    li   a0, 0
    ecall
    addi s4, a0, -1      # s4 = maxX = width - 1
    addi s5, a1, -1      # s5 = maxY = height - 1

frame:
    li   a7, 21          # clear display
    li   a0, 0
    ecall

    li   a7, 20          # DISP_SET — draw dot
    li   a0, 0
    mv   a1, s0
    mv   a2, s1
    li   a3, 0xFFFFFF
    ecall

    add  s0, s0, s2      # move X
    add  s1, s1, s3      # move Y

    # Bounce X
    blt  s0, s4, chk_xlo # if x < maxX, check lower bound
    mv   s0, s4          # clamp to right wall
    li   s2, -1          # reverse horizontal
    j    chk_y
chk_xlo:
    bgtz s0, chk_y       # if x > 0, no bounce
    li   s0, 0           # clamp to left wall
    li   s2, 1

    # Bounce Y
chk_y:
    blt  s1, s5, chk_ylo
    mv   s1, s5
    li   s3, -1
    j    frame_done
chk_ylo:
    bgtz s1, frame_done
    li   s1, 0
    li   s3, 1

frame_done:
    li   a7, 1
    li   a0, 2           # 2 ticks per frame (10 fps at 20 TPS)
    ecall

    j    frame
```

---

### 8 — Scrolling Scan Line

Sweeps a bright green horizontal line from top to bottom continuously.
Uses `DISP_DIM` so it works on any cluster height.

```asm
# Hardware: Display port ID 0 (any size).
# s0=currentY  s1=width  s2=height

    li   s0, 0

    li   a7, 22
    li   a0, 0
    ecall
    mv   s1, a0          # s1 = width
    mv   s2, a1          # s2 = height

tick:
    li   a7, 21          # clear
    li   a0, 0
    ecall

    li   a7, 23          # DISP_LINE
    li   a0, 0
    li   a1, 0           # x1 = 0
    mv   a2, s0          # y1 = current row
    addi a3, s1, -1      # x2 = width - 1
    mv   a4, s0          # y2 = same row (horizontal)
    li   a5, 0x00FF88    # bright green
    ecall

    addi s0, s0, 1
    blt  s0, s2, no_wrap
    li   s0, 0           # wrap back to top
no_wrap:
    li   a7, 1
    li   a0, 3           # 3 ticks per step
    ecall

    j    tick
```

---

## Memory Examples

### 9 — RAM Checkerboard via BLIT

Writes an 8×8 checkerboard (black and white) into RAM, then blits it
to the display.  Demonstrates direct byte writes to RAM and `DISP_BLIT`.

```asm
# Hardware: Display port ID 0; at least one RAM card (≥ 192 bytes).
# Buffer: address 0 — 192 bytes  (8 × 8 pixels × 3 bytes/pixel).
# Pixel format: R, G, B bytes; row-major.
#
# Byte address of pixel (col, row) = (row*8 + col) * 3
# Multiply by 3 via:  t1 = idx;  t0 = t1 + t1 + t1  (3× without MUL)

    li   s0, 0           # row = 0

row_loop:
    li   s1, 0           # col = 0

col_loop:
    # Compute byte address = (row*8 + col) * 3
    slli t1, s0, 3       # t1 = row * 8
    add  t1, t1, s1      # t1 = row*8 + col  (pixel index)
    add  t0, t1, t1      # t0 = index * 2
    add  t0, t0, t1      # t0 = index * 3    (byte address)

    # Color: white if (row XOR col) is even, black otherwise
    xor  t1, s0, s1
    andi t1, t1, 1       # t1 = parity bit
    beqz t1, pixel_white

pixel_black:
    sb   zero, 0(t0)     # R = 0
    sb   zero, 1(t0)     # G = 0
    sb   zero, 2(t0)     # B = 0
    j    next_col

pixel_white:
    li   t1, 255
    sb   t1, 0(t0)       # R = 255
    sb   t1, 1(t0)       # G = 255
    sb   t1, 2(t0)       # B = 255

next_col:
    addi s1, s1, 1
    li   t0, 8
    blt  s1, t0, col_loop

    addi s0, s0, 1
    li   t0, 8
    blt  s0, t0, row_loop

    # Blit 8×8 from RAM[0] to display 0 at position (4, 4)
    li   a7, 25          # DISP_BLIT
    li   a0, 0
    li   a1, 4           # x
    li   a2, 4           # y
    li   a3, 8           # w
    li   a4, 8           # h
    li   a5, 0           # RAM address 0
    ecall

    halt
```

---

### 10 — Stack and Subroutines

Demonstrates `call`/`ret` with a stack-allocated frame.
A helper procedure `draw_rect` wraps `DISP_RECT` so the calling code
stays clean, and `draw_line_h` draws a horizontal separator.

```asm
# Hardware: Display port ID 0; at least 1 KB RAM card (stack uses top of it).
# Stack pointer is initialised to byte 1020 (top of a 1 KB card, 4-byte aligned).

    li   sp, 1020        # init stack — must have ≥ 1 KB RAM

    # Draw top panel: red  (x=0, y=0, w=16, h=8, color=0xFF2200)
    li   a0, 0           # dispId
    li   a1, 0           # x
    li   a2, 0           # y
    li   a3, 16          # w
    li   a4, 8           # h
    li   a5, 0xFF2200
    call draw_rect

    # Draw bottom panel: blue (x=0, y=8, w=16, h=8, color=0x0022FF)
    li   a0, 0
    li   a1, 0
    li   a2, 8
    li   a3, 16
    li   a4, 8
    li   a5, 0x0022FF
    call draw_rect

    # Draw white divider line across y=8
    li   a0, 0
    li   a1, 0           # x1
    li   a2, 8           # y1
    li   a3, 15          # x2
    li   a4, 8           # y2
    li   a5, 0xFFFFFF
    call draw_line

    halt

# ── Subroutines ────────────────────────────────────────────────────────────

# draw_rect(a0=dispId, a1=x, a2=y, a3=w, a4=h, a5=color)
draw_rect:
    addi sp, sp, -4
    sw   ra, 0(sp)       # save return address

    li   a7, 24          # DISP_RECT
    ecall

    lw   ra, 0(sp)
    addi sp, sp, 4
    ret

# draw_line(a0=dispId, a1=x1, a2=y1, a3=x2, a4=y2, a5=color)
draw_line:
    addi sp, sp, -4
    sw   ra, 0(sp)

    li   a7, 23          # DISP_LINE
    ecall

    lw   ra, 0(sp)
    addi sp, sp, 4
    ret
```

---

### 11 — Floppy Image Loader

Loads a 16×16 RGB image from floppy drive 0 (two 512-byte sectors = 1024 bytes,
of which 768 are used) into RAM, then blits it to the display.
On failure, the display flashes red.

```asm
# Hardware: Floppy Drive ID 0 with a disk containing raw R,G,B data.
#           Display port ID 0.  RAM card ≥ 1 KB.
# Disk layout: sector 0 = bytes 0–511, sector 1 = bytes 512–1023.
# Image pixel format: R G B per pixel, row-major, 16×16 = 768 bytes.

    # Load sector 0 → RAM[0..511]
    li   a7, 30
    li   a0, 0           # drive 0
    li   a1, 0           # sector 0
    li   a2, 0           # destination address
    ecall
    beqz a0, load_fail

    # Load sector 1 → RAM[512..767]
    li   a7, 30
    li   a1, 1           # sector 1
    li   a2, 512
    ecall
    beqz a0, load_fail

    # Blit full 16×16 image from RAM[0] to display 0
    li   a7, 25
    li   a0, 0
    li   a1, 0           # x
    li   a2, 0           # y
    li   a3, 16          # w
    li   a4, 16          # h
    li   a5, 0           # RAM address
    ecall

    halt

load_fail:
    # Flash display red — floppy read failed
    li   a7, 24
    li   a0, 0
    li   a1, 0
    li   a2, 0
    li   a3, 16
    li   a4, 16
    li   a5, 0xFF0000
    ecall
    halt
```

---

## Combined Examples

### 12 — GPIO Signal Strength Bar Graph

Reads GPIO port 0 (0–15) and draws a vertical bar graph on display 0.
Bar height scales with signal strength; color changes from green → yellow → red.
Works on any display cluster size (uses `DISP_DIM`).

```asm
# Hardware: GPIO Port ID 0 (input), Display port ID 0.
# s0=width  s1=height  s2=signal  t0=barHeight  t1=barY  t2=color

    li   a7, 22
    li   a0, 0
    ecall
    mv   s0, a0          # s0 = display width
    mv   s1, a1          # s1 = display height

loop:
    li   a7, 11          # GPIO_IN
    li   a0, 0
    ecall
    mv   s2, a0          # s2 = signal (0-15)

    li   a7, 21          # clear display
    li   a0, 0
    ecall

    beqz s2, skip_bar    # no bar if signal is 0

    # bar_height = (signal × display_height) / 15
    mul  t0, s2, s1
    li   t1, 15
    div  t0, t0, t1      # t0 = bar height in pixels

    # bar top Y = display_height - bar_height  (bar grows upward from bottom)
    sub  t1, s1, t0

    # Choose color based on signal level
    li   t2, 0x00CC00    # green  (signal 0-9)
    li   t3, 10
    blt  s2, t3, draw
    li   t2, 0xFFAA00    # yellow (signal 10-12)
    li   t3, 13
    blt  s2, t3, draw
    li   t2, 0xFF2200    # red    (signal 13-15)

draw:
    li   a7, 24          # DISP_RECT
    li   a0, 0
    li   a1, 2           # x — 2-pixel margin left
    mv   a2, t1          # y — top of bar
    addi a3, s0, -4      # w — display width minus margins
    mv   a4, t0          # h — bar height
    mv   a5, t2          # color
    ecall

skip_bar:
    li   a7, 1
    li   a0, 2           # update every 2 ticks
    ecall

    j    loop
```

---

### 13 — GPIO-Triggered Status Indicator

Monitors GPIO port 0. Fills the display **green** when the signal reaches 8+
(redstone powered) and **red** when below. Redraws only on state change —
ideal for a compact on/off indicator that doesn't waste cycles redrawing.

```asm
# Hardware: GPIO Port ID 0, Display port ID 0 (1×1, 16×16).

    li   s0, -1          # s0 = last drawn state  (-1 forces first draw)

loop:
    li   a7, 11          # GPIO_IN
    li   a0, 0
    ecall                # a0 = signal

    # Compute state: 1 if signal >= 8, else 0
    li   t0, 8
    slt  t1, a0, t0      # t1 = 1 if signal < 8
    xori t1, t1, 1       # t1 = 1 if signal >= 8  (invert)

    beq  t1, s0, no_change   # state unchanged — skip redraw
    mv   s0, t1

    # Redraw display
    li   a7, 24
    li   a0, 0           # display 0
    li   a1, 0           # x
    li   a2, 0           # y
    li   a3, 16          # w
    li   a4, 16          # h

    beqz s0, show_off
    li   a5, 0x00CC00    # green — powered
    j    do_draw
show_off:
    li   a5, 0xCC0000    # red — unpowered
do_draw:
    ecall

no_change:
    li   a7, 1
    li   a0, 2
    ecall

    j    loop
```

---

### 14 — Random Pixel Noise

Fills the canvas with randomly colored pixels at full MCU speed.
Combines `RND` (syscall 2) with `DISP_SET` — runs until the machine is stopped.
MCU speed setting in the microcontroller item controls the refresh rate.

```asm
# Hardware: Display port ID 0 (any size).
# s0=x  s1=y  s2=width  s3=height

    li   s0, 0
    li   s1, 0

    li   a7, 22          # DISP_DIM
    li   a0, 0
    ecall
    mv   s2, a0          # s2 = width
    mv   s3, a1          # s3 = height

pixel:
    # Pick random 24-bit color
    li   a7, 2
    li   a0, 0
    li   a1, 0xFFFFFF
    ecall                # a0 = random color

    # Draw pixel at (s0, s1)
    li   a7, 20          # DISP_SET
    li   a0, 0
    mv   a1, s0
    mv   a2, s1
    mv   a3, a0
    ecall

    # Advance: left-to-right, top-to-bottom, then wrap
    addi s0, s0, 1
    blt  s0, s2, next
    li   s0, 0
    addi s1, s1, 1
    blt  s1, s3, next
    li   s1, 0
next:
    j    pixel
```

---

### 15 — Animated Clock Counter on Display

Counts elapsed seconds using GPIO port 0 as a heartbeat trigger
and displays the count as a colored digit-bar on display 0.
Every time port 0 goes from 0 → any, a "second" is recorded.

```asm
# Hardware: GPIO Port ID 0 (clock signal, e.g. from a clock circuit),
#           Display port ID 0.
# Counts seconds 0-255, shows a single-row bar scaled to the counter.

    li   s0, 0           # s0 = second counter
    li   s1, 0           # s1 = previous GPIO state

    li   a7, 22
    li   a0, 0
    ecall
    mv   s2, a0          # s2 = display width

poll:
    li   a7, 11
    li   a0, 0
    ecall                # a0 = current GPIO signal
    mv   t0, a0

    # Rising edge detection: prev=0 and cur>0
    bnez s1, update_prev
    beqz t0, update_prev

    # Rising edge: increment counter (wrap at 256)
    addi s0, s0, 1
    andi s0, s0, 0xFF

    # Redraw bar: bar_w = (counter * width) / 255
    mul  t1, s0, s2
    li   t2, 255
    div  t1, t1, t2      # t1 = bar width

    li   a7, 21          # clear
    li   a0, 0
    ecall

    beqz t1, update_prev # nothing to draw yet

    li   a7, 24
    li   a0, 0
    li   a1, 0           # x
    li   a2, 6           # y (centered in 16-row display)
    mv   a3, t1          # w = bar width
    li   a4, 4           # h = bar thickness
    li   a5, 0x44AAFF    # light blue
    ecall

update_prev:
    mv   s1, t0          # save previous state

    li   a7, 1
    li   a0, 1
    ecall

    j    poll
```

---

## Subroutine Conventions

Calling convention for subroutines in Logic VM programs:

```asm
# 1. Caller sets up args in a0-a5
# 2. Caller executes: call <label>   (saves pc+1 in ra)
# 3. Callee saves ra if it makes further calls, then uses a7+ecall
# 4. Callee restores ra and executes: ret   (jumps back to ra)

my_func:
    addi sp, sp, -4     # allocate 4 bytes on stack
    sw   ra, 0(sp)      # save return address

    # ... function body using a0-a5 inputs, a7+ecall for syscalls ...
    # saved registers s0-s11 must be preserved if used

    lw   ra, 0(sp)      # restore return address
    addi sp, sp, 4      # free stack frame
    ret                 # return to caller
```

**Stack initialisation** — add this to every program that uses `call`/`ret`:

```asm
li sp, <top_of_ram>    # e.g. li sp, 1020  for a 1 KB RAM card
```

Stack grows downward. Each saved register costs 4 bytes.

---

## VRAM and RAM Sizing

| Canvas size | Blocks | Pixels | BLIT buffer | RAM cards (1 KB each) |
|-------------|--------|--------|-------------|----------------------|
| 16×16  | 1×1 | 256 | 768 B | 1 |
| 32×16  | 2×1 | 512 | 1 536 B | 2 |
| 32×32  | 2×2 | 1 024 | 3 072 B | 3 |
| 48×32  | 3×2 | 1 536 | 4 608 B | 5 |
| 64×64  | 4×4 | 4 096 | 12 288 B | 13 |

Stack space should be added on top of the frame buffer requirement.
Floppy sectors are 512 bytes; a 1.44 MB disk holds 2 880 sectors.
