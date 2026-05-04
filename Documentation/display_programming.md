# Display Programming Guide

The **Display** block is a 16×16-pixel programmable screen controlled by a Logic Machine
over a Logic Cable network.  Multiple Display blocks placed in a rectangle with the same
facing direction and the same port ID automatically merge into one larger canvas.

---

## Quick-reference: display instruction set

| Instruction | Operands | Description |
|---|---|---|
| `SET`  | `dispId, x, y, color` | Write one pixel. `color` = `0xRRGGBB`. |
| `RST`  | `dispId` | Clear entire canvas to black. |
| `GDIM` | `dispId, Rx, Ry` | `Rx` ← canvas width (px), `Ry` ← canvas height (px). |
| `LINE` | `dispId, x1, y1, x2, y2, color` | Bresenham line from (x1,y1) to (x2,y2). |
| `RECT` | `dispId, x, y, w, h, color` | Fill a solid rectangle at (x,y) size w×h. |
| `BLIT` | `dispId, x, y, w, h, addr` | Copy w×h pixels from RAM to canvas at (x,y). |

All coordinate arguments accept registers (`R0`–`R3`) or integer / hex immediates.

---

## Multi-block canvas

Place N×M Display blocks in a filled rectangle, all facing the same direction, all sharing
the same port ID.  They act as **one display**:

- Pixel (0, 0) is the **top-left** corner as seen from the front.
- X increases to the right, Y increases downward.
- Each block owns a 16×16 tile:

```
block (col, row) owns pixels  x ∈ [col×16, col×16+15]
                               y ∈ [row×16, row×16+15]
```

A 3×2 cluster → canvas 48×32 px.  Use `GDIM` at runtime to read the actual size.

Only the **master block** (top-left) needs a cable connection, but any block in the
cluster that is cable-adjacent also works.

---

## BLIT pixel format (VRAM in RAM)

RAM cards serve as video RAM.  Each pixel is **3 consecutive bytes**: `R`, `G`, `B`
(each 0–255).  Row-major order:

```
pixel (col, row)  →  RAM[addr + (row × width + col) × 3 .. +2]
```

A 16×16 frame buffer needs **768 bytes** (¾ of one 1-KB RAM card).
A 32×32 buffer needs **3 072 bytes** (3 RAM cards).

---

## Example programs

### 1 – Solid color fill

```asm
; Fill display 0 entirely with dark blue.
    RST  0
    RECT 0, 0, 0, 16, 16, 0x001040
    HALT
```

---

### 2 – Rainbow color bars

```asm
; Seven horizontal bars across a single 16×16 display.
    RECT 0,  0,  0, 16, 2, 0xFF0000   ; red
    RECT 0,  0,  2, 16, 2, 0xFF7700   ; orange
    RECT 0,  0,  4, 16, 2, 0xFFFF00   ; yellow
    RECT 0,  0,  6, 16, 2, 0x00CC00   ; green
    RECT 0,  0,  8, 16, 2, 0x0000FF   ; blue
    RECT 0,  0, 10, 16, 2, 0x4B0082   ; indigo
    RECT 0,  0, 12, 16, 4, 0x8F00FF   ; violet
    HALT
```

---

### 3 – Bordered panel

```asm
; Draw a white 1-pixel border with a dark-blue fill.
    RST  0
    RECT 0, 1, 1, 14, 14, 0x001040    ; interior fill
    LINE 0,  0,  0, 15,  0, 0xFFFFFF  ; top edge
    LINE 0,  0, 15, 15, 15, 0xFFFFFF  ; bottom edge
    LINE 0,  0,  0,  0, 15, 0xFFFFFF  ; left edge
    LINE 0, 15,  0, 15, 15, 0xFFFFFF  ; right edge
    HALT
```

---

### 4 – Diagonal cross

```asm
; Draw a red X across the display.
    RST  0
    LINE 0,  0,  0, 15, 15, 0xFF0000
    LINE 0, 15,  0,  0, 15, 0xFF0000
    HALT
```

---

### 5 – Adaptive fill (GDIM)

Works on any cluster size — fills the top half cyan, bottom half dark.

```asm
    GDIM 0, R0, R1         ; R0 = width, R1 = height
    SHR  R1, 1             ; R1 = height / 2
    RECT 0, 0, 0, R0, R1, 0x00CCCC   ; top half  – cyan
    ; bottom half: y = R1, h = height - R1
    GDIM 0, R0, R2         ; R2 = total height again
    SUB  R2, R1            ; R2 = bottom height
    RECT 0, 0, R1, R0, R2, 0x001010  ; bottom half – dark teal
    HALT
```

---

### 6 – Animated bouncing dot

```asm
; Bouncing white dot on black background.
; R0=x  R1=y  R2=dx  R3=dy
    MOV R0, 4
    MOV R1, 4
    MOV R2, 1
    MOV R3, 1

loop:
    RST 0
    SET 0, R0, R1, 0xFFFFFF   ; draw dot

    ; move
    ADD R0, R2
    ADD R1, R3

    ; bounce X  (0..15)
    CMP R0, 15
    JLT chk_xlo
    MOV R0, 14
    MOV R2, -1
    JMP chk_y
chk_xlo:
    CMP R0, 0
    JGT chk_y
    MOV R0, 1
    MOV R2, 1

chk_y:
    CMP R1, 15
    JLT chk_ylo
    MOV R1, 14
    MOV R3, -1
    JMP frame_done
chk_ylo:
    CMP R1, 0
    JGT frame_done
    MOV R1, 1
    MOV R3, 1

frame_done:
    SLP 2
    JMP loop
```

---

### 7 – BLIT: checkerboard from RAM

Builds an 8×8 checkerboard pixel buffer in RAM then blits it.

```asm
; Write an 8x8 checkerboard into RAM starting at address 0.
; White = 0xFFFFFF (bytes: 255,255,255)  Black = 0x000000 (bytes: 0,0,0)
; pixel(col,row) at addr (row*8+col)*3

    MOV R1, 0       ; row = 0
row_loop:
    MOV R0, 0       ; col = 0
col_loop:
    ; addr = (row*8 + col) * 3
    MOV R2, R1
    MUL R2, 8
    ADD R2, R0
    MUL R2, 3       ; R2 = byte address

    ; white if (row XOR col) is even, else black
    MOV R3, R0
    XOR R3, R1
    AND R3, 1       ; R3 = (col XOR row) & 1

    CMP R3, 0
    JNE store_black
    ; white
    POKE R2, 255
    INC R2
    POKE R2, 255
    INC R2
    POKE R2, 255
    JMP next_col
store_black:
    POKE R2, 0
    INC R2
    POKE R2, 0
    INC R2
    POKE R2, 0

next_col:
    INC R0
    CMP R0, 8
    JLT col_loop

    INC R1
    CMP R1, 8
    JLT row_loop

    ; Blit the 8×8 buffer to display 0 at position (4,4)
    BLIT 0, 4, 4, 8, 8, 0
    HALT
```

---

### 8 – BLIT from floppy disk (full-screen image)

Loads a 16×16 RGB image (768 bytes = 2 sectors) from floppy drive 0 and blits it.
Requires at least one RAM card (≥ 768 bytes).

```asm
; Load sector 0 (bytes 0-511) into RAM at address 0
    LDSC 0, 0, 0

; Load sector 1 (bytes 512-767 of the image) into RAM at address 512
    LDSC 0, 1, 512

; Blit the full 16×16 image to display 0
    BLIT 0, 0, 0, 16, 16, 0
    HALT
```

**Floppy image layout**: sector N holds bytes `N×512` to `(N+1)×512 - 1` of the raw
RGB data.  Pixel (col, row) starts at byte `(row×width + col) × 3`.

---

### 9 – Multi-block display: split-screen

A 2×1 cluster (32×16 canvas, port ID 1) — left half red, right half blue.

```asm
    GDIM 1, R0, R1          ; R0=32, R1=16
    MOV  R2, R0
    SHR  R2, 1              ; R2 = 16  (half width)
    RECT 1, 0,  0, R2, R1, 0xFF2200   ; left half
    RECT 1, R2, 0, R2, R1, 0x0022FF   ; right half
    HALT
```

---

### 10 – Scrolling line ticker on a wide display

Scrolls a horizontal line down a 1×3 cluster (16×48 canvas).

```asm
; R0 = current Y position of the line
    MOV R0, 0
    GDIM 0, R1, R2    ; R1 = width, R2 = height

tick:
    RST  0
    LINE 0, 0, R0, R1, R0, 0x00FF88   ; horizontal green line at y=R0

    INC  R0
    CMP  R0, R2
    JLT  no_wrap
    MOV  R0, 0
no_wrap:
    SLP  3
    JMP  tick
```

---

## VRAM sizing reference

| Canvas | Blocks | Pixels | RAM needed |
|--------|--------|--------|------------|
| 16×16  | 1×1    | 256    | 768 B  |
| 32×16  | 2×1    | 512    | 1 536 B (2 RAM cards) |
| 32×32  | 2×2    | 1 024  | 3 072 B (3 RAM cards) |
| 48×32  | 3×2    | 1 536  | 4 608 B (5 RAM cards) |
| 64×64  | 4×4    | 4 096  | 12 288 B (12 RAM cards) |

*(Default RAM card capacity: 1 024 bytes.)*
