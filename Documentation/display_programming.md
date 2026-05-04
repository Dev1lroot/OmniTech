# Display Programming Guide

The **Display** block is a 16×16-pixel programmable screen controlled by a Logic Machine
over a Logic Cable network.  Multiple Display blocks placed in a filled rectangle, all
facing the same direction and sharing the same port ID, automatically merge into one
larger canvas.

---

## Display ECALL reference

All display operations go through `ECALL` — set `a7` to the syscall number, put
arguments in `a0`–`a5`, then execute `ecall`.

| `a7` | Operation | Arguments | Notes |
|------|-----------|-----------|-------|
| `21` | `DISP_RST`  | `a0`=id | Clear canvas to black |
| `22` | `DISP_DIM`  | `a0`=id | Returns `a0`=width, `a1`=height |
| `20` | `DISP_SET`  | `a0`=id, `a1`=x, `a2`=y, `a3`=0xRRGGBB | Set one pixel |
| `23` | `DISP_LINE` | `a0`=id, `a1`=x1, `a2`=y1, `a3`=x2, `a4`=y2, `a5`=color | Bresenham line |
| `24` | `DISP_RECT` | `a0`=id, `a1`=x, `a2`=y, `a3`=w, `a4`=h, `a5`=color | Solid rectangle |
| `25` | `DISP_BLIT` | `a0`=id, `a1`=x, `a2`=y, `a3`=w, `a4`=h, `a5`=ramAddr | Copy pixels from RAM |

All coordinates are in pixels.  `color` is `0x00RRGGBB` (top byte ignored).  
Display ID 0 means the Display block with port ID 0 on the cable network.

---

## Multi-block canvas

Place N×M Display blocks in a filled rectangle, all facing the same direction and
sharing the same port ID.  They act as **one display**:

- Pixel (0, 0) is the **top-left** corner as seen from the front.
- X increases right, Y increases downward.
- Each block owns a 16×16 tile:

```
block at (col, row)  →  pixels  x ∈ [ col×16, col×16+15 ]
                                 y ∈ [ row×16, row×16+15 ]
```

A 3×2 cluster → canvas 48×32 px.  Use `DISP_DIM` at runtime to read the actual size
so your programs work on **any** cluster without hard-coding pixel counts.

---

## BLIT pixel format (VRAM in RAM)

`DISP_BLIT` copies pixels from connected RAM cards to the canvas.  Each pixel is
**3 consecutive bytes**: R, G, B (each 0–255), in **row-major** order:

```
pixel (col, row)  →  RAM[ addr + (row × width + col) × 3     ]   ← R
                     RAM[ addr + (row × width + col) × 3 + 1 ]   ← G
                     RAM[ addr + (row × width + col) × 3 + 2 ]   ← B
```

A 16×16 frame buffer needs **768 bytes** (¾ of one 1 KB RAM card).  
A 32×32 buffer needs **3 072 bytes** (3 RAM cards).

---

## Example programs

### 1 — Solid color fill

```asm
# Fill display 0 with dark blue, then stop.

    li   a7, 24          # DISP_RECT
    li   a0, 0           # display 0
    li   a1, 0           # x
    li   a2, 0           # y
    li   a3, 16          # w
    li   a4, 16          # h
    li   a5, 0x001040    # dark blue
    ecall

    halt
```

---

### 2 — Rainbow color bars

Seven horizontal strips across a 16×16 canvas.

```asm
    li   a7, 24          # DISP_RECT — a7 stays 24 for all bars
    li   a0, 0           # display 0 — a0 stays 0
    li   a1, 0           # x = 0

    li   a2, 0  ; li a3, 16 ; li a4, 2 ; li a5, 0xFF0000 ; ecall  # red
    li   a2, 2  ; li a5, 0xFF7700 ; ecall                          # orange
    li   a2, 4  ; li a5, 0xFFFF00 ; ecall                          # yellow
    li   a2, 6  ; li a5, 0x00CC00 ; ecall                          # green
    li   a2, 8  ; li a5, 0x0000FF ; ecall                          # blue
    li   a2, 10 ; li a5, 0x4B0082 ; ecall                          # indigo
    li   a2, 12 ; li a4, 4 ; li a5, 0x8F00FF ; ecall               # violet

    halt
```

---

### 3 — Bordered panel

White 1-pixel border with a dark-blue fill.

```asm
    li   a7, 21          # RST — clear to black
    li   a0, 0
    ecall

    # Interior
    li   a7, 24
    li   a0, 0
    li   a1, 1 ; li a2, 1 ; li a3, 14 ; li a4, 14 ; li a5, 0x001040
    ecall

    # Border — four lines
    li   a7, 23
    li   a0, 0
    li   a5, 0xFFFFFF

    li   a1, 0 ; li a2, 0  ; li a3, 15 ; li a4, 0  ; ecall  # top
    li   a2, 15 ; li a4, 15 ; ecall                          # bottom
    li   a1, 0 ; li a2, 0  ; li a3, 0  ; li a4, 15 ; ecall  # left
    li   a1, 15 ; li a3, 15 ; ecall                          # right

    halt
```

---

### 4 — Diagonal cross

Red X spanning the entire 16×16 canvas.

```asm
    li   a7, 21
    li   a0, 0
    ecall

    li   a7, 23
    li   a0, 0
    li   a5, 0xFF0000

    li   a1, 0 ; li a2, 0  ; li a3, 15 ; li a4, 15 ; ecall  # top-left → bottom-right
    li   a1, 15 ; li a2, 0 ; li a3, 0  ; li a4, 15 ; ecall  # top-right → bottom-left

    halt
```

---

### 5 — Adaptive split-screen (DISP_DIM)

Queries the canvas size at runtime — works on any cluster without changing the program.

```asm
# Top half cyan, bottom half dark teal.

    li   a7, 22          # DISP_DIM
    li   a0, 0
    ecall                # a0 = width, a1 = height

    mv   s0, a0          # s0 = width
    mv   s1, a1          # s1 = height
    srli s2, s1, 1       # s2 = height / 2

    # Top half
    li   a7, 24
    li   a0, 0
    li   a1, 0 ; li a2, 0
    mv   a3, s0 ; mv a4, s2 ; li a5, 0x00CCCC
    ecall

    # Bottom half
    mv   a2, s2          # y = midpoint
    sub  a4, s1, s2      # h = remaining rows
    li   a5, 0x001010
    ecall

    halt
```

---

### 6 — Animated bouncing dot

White dot bouncing around the canvas.  Velocity reverses at edges.
`DISP_DIM` is called once to get the bounds.

```asm
# s0=x  s1=y  s2=dx  s3=dy  s4=maxX  s5=maxY

    li   s0, 4 ; li s1, 4
    li   s2, 1 ; li s3, 1

    li   a7, 22
    li   a0, 0
    ecall
    addi s4, a0, -1      # maxX = width - 1
    addi s5, a1, -1      # maxY = height - 1

frame:
    li   a7, 21 ; li a0, 0 ; ecall          # clear

    li   a7, 20 ; li a0, 0                  # DISP_SET
    mv   a1, s0 ; mv a2, s1 ; li a3, 0xFFFFFF ; ecall

    add  s0, s0, s2                         # move
    add  s1, s1, s3

    # Bounce X
    blt  s0, s4, chk_xlo
    mv   s0, s4 ; li s2, -1 ; j chk_y
chk_xlo:
    bgtz s0, chk_y
    li   s0, 0 ; li s2, 1

    # Bounce Y
chk_y:
    blt  s1, s5, chk_ylo
    mv   s1, s5 ; li s3, -1 ; j next_frame
chk_ylo:
    bgtz s1, next_frame
    li   s1, 0 ; li s3, 1

next_frame:
    li   a7, 1 ; li a0, 2 ; ecall           # sleep 2 ticks
    j    frame
```

---

### 7 — BLIT: checkerboard from RAM

Builds an 8×8 checkerboard pixel buffer in RAM, then blits it to display 0.

```asm
# Requires: Display port ID 0, at least one RAM card (≥ 192 bytes).

    li   s0, 0           # row

row_loop:
    li   s1, 0           # col

col_loop:
    # byte_addr = (row*8 + col) * 3  — multiply by 3 using two adds
    slli t1, s0, 3
    add  t1, t1, s1      # t1 = pixel index
    add  t0, t1, t1
    add  t0, t0, t1      # t0 = t1 * 3 = byte address

    xor  t1, s0, s1
    andi t1, t1, 1       # parity: 1=black, 0=white
    beqz t1, white

    sb   zero, 0(t0) ; sb zero, 1(t0) ; sb zero, 2(t0)  # black
    j    next_col

white:
    li   t1, 255
    sb   t1, 0(t0) ; sb t1, 1(t0) ; sb t1, 2(t0)       # white

next_col:
    addi s1, s1, 1 ; li t0, 8 ; blt s1, t0, col_loop
    addi s0, s0, 1 ; li t0, 8 ; blt s0, t0, row_loop

    # Blit 8×8 at display position (4, 4)
    li   a7, 25
    li   a0, 0 ; li a1, 4 ; li a2, 4 ; li a3, 8 ; li a4, 8 ; li a5, 0
    ecall

    halt
```

---

### 8 — BLIT from floppy disk (full-screen image)

Loads a 16×16 RGB image (768 bytes = 2 sectors) from floppy drive 0 and blits it.

```asm
# Floppy disk layout: raw R,G,B bytes, row-major, 16×16 = 768 bytes.
# Sector 0 covers bytes 0-511; sector 1 covers bytes 512-767.
# Requires: Floppy Drive ID 0, Display port ID 0, RAM card ≥ 1 KB.

    li   a7, 30
    li   a0, 0 ; li a1, 0 ; li a2, 0    # drive 0, sector 0 → RAM[0]
    ecall
    beqz a0, disk_err

    li   a7, 30
    li   a1, 1 ; li a2, 512             # sector 1 → RAM[512]
    ecall
    beqz a0, disk_err

    li   a7, 25
    li   a0, 0 ; li a1, 0 ; li a2, 0 ; li a3, 16 ; li a4, 16 ; li a5, 0
    ecall

    halt

disk_err:
    li   a7, 24
    li   a0, 0 ; li a1, 0 ; li a2, 0 ; li a3, 16 ; li a4, 16 ; li a5, 0xFF0000
    ecall
    halt
```

---

### 9 — Multi-block display: split-screen

A 2×1 cluster (32×16 canvas, port ID 1) — left half red, right half blue.

```asm
    li   a7, 22
    li   a0, 1           # port 1
    ecall                # a0=32, a1=16

    mv   s0, a0          # s0 = 32 (total width)
    srli s1, s0, 1       # s1 = 16 (half width)
    mv   s2, a1          # s2 = 16 (height)

    li   a7, 24
    li   a0, 1

    li   a1, 0 ; mv a2, zero ; mv a3, s1 ; mv a4, s2 ; li a5, 0xFF2200
    ecall                # left half — red

    mv   a1, s1          # x = half width
    li   a5, 0x0022FF
    ecall                # right half — blue

    halt
```

---

### 10 — Scrolling line ticker on a wide display

Sweeps a horizontal green line downward on a 1×3 cluster (16×48 canvas, port ID 0).

```asm
    li   s0, 0           # current Y

    li   a7, 22
    li   a0, 0
    ecall
    mv   s1, a0          # width
    mv   s2, a1          # height

tick:
    li   a7, 21 ; li a0, 0 ; ecall                          # clear

    li   a7, 23 ; li a0, 0
    li   a1, 0 ; mv a2, s0 ; addi a3, s1, -1 ; mv a4, s0
    li   a5, 0x00FF88
    ecall                                                    # horizontal line at y=s0

    addi s0, s0, 1
    blt  s0, s2, no_wrap
    li   s0, 0
no_wrap:
    li   a7, 1 ; li a0, 3 ; ecall
    j    tick
```

---

## VRAM sizing reference

| Canvas | Blocks | Pixels | RAM needed |
|--------|--------|--------|------------|
| 16×16  | 1×1 | 256 | 768 B (1 card) |
| 32×16  | 2×1 | 512 | 1 536 B (2 cards) |
| 32×32  | 2×2 | 1 024 | 3 072 B (3 cards) |
| 48×32  | 3×2 | 1 536 | 4 608 B (5 cards) |
| 64×64  | 4×4 | 4 096 | 12 288 B (13 cards) |

*(Default RAM card capacity: 1 024 bytes.)*
