#pragma once
#include <stdint.h>

/* ── UART16550A — terminal output (display portId=0) ────────────────────────
 * Sedna assigns UART16550A to 0x10000000 (confirmed: "10000000.uart").
 * In bare-metal mode the UART works without explicit init — writing to THR
 * immediately appears on the terminal display (portId=0).
 */
#define UART_BASE   ((volatile uint8_t *)0x10000000UL)
#define UART_THR    (UART_BASE + 0)   /* Transmit Holding Register (write) */
#define UART_LSR    (UART_BASE + 5)   /* Line Status Register              */
#define UART_THRE   (1u << 5)         /* bit 5: TX holding register empty  */

static inline void uart_putc(char c) {
    while (!(*UART_LSR & UART_THRE));
    *UART_THR = (uint8_t)c;
}

static inline void uart_puts(const char *s) {
    while (*s) uart_putc(*s++);
}

static inline void uart_puthex32(uint32_t v) {
    uart_puts("0x");
    for (int i = 28; i >= 0; i -= 4) {
        int nib = (v >> i) & 0xF;
        uart_putc(nib < 10 ? '0' + nib : 'A' + nib - 10);
    }
}

/* ── OmniTech Bus ────────────────────────────────────────────────────────────
 * Physical base: 0x40000000 (always present, even in custom firmware mode)
 *
 * Header registers (32-bit, word access):
 *   0x000  [R]     Magic:      0x4F544543 ("OTEC") — verify device present
 *   0x004  [R]     Version:    1
 *   0x008  [R]     GPIO count: number of GPIO ports wired in cable network
 *   0x00C  [R]     Disp count: number of pixel display slots (portId 1–8)
 *   0x010  [R/W1C] IRQ status: bitmask of GPIO indices that changed since last read
 *   0x014  [R/W]   IRQ mask:   set a bit to enable IRQ for that GPIO index
 *
 * GPIO data (word access):
 *   0x100 + N*4  [R/W]  GPIO port N value (0–15)
 *
 * Display slots (portId = 1..8, slot = portId - 1):
 *   slot_base = 0x40000000 + 0x10000 + slot * 0x10100
 *   +0x000  [R]    pixel width  (display cluster width,  capped at 128)
 *   +0x004  [R]    pixel height (display cluster height, capped at 128)
 *   +0x008  [W]    flush — push staged pixels to the display block (server tick)
 *   +0x00C  [W]    clear — reset all pixels to black
 *   +0x010  [R/W]  framebuffer — pixel[y*width + x] = 0x00RRGGBB, row-major
 */
#define BUS_BASE        0x40000000UL
#define BUS_REG(off)    (*(volatile uint32_t *)(BUS_BASE + (off)))

#define BUS_MAGIC       BUS_REG(0x000)
#define BUS_VERSION     BUS_REG(0x004)
#define BUS_GPIO_COUNT  BUS_REG(0x008)
#define BUS_DISP_COUNT  BUS_REG(0x00C)
#define BUS_IRQ_STATUS  BUS_REG(0x010)
#define BUS_IRQ_MASK    BUS_REG(0x014)

#define GPIO(n)         BUS_REG(0x100 + (n) * 4)

/* Speaker registers (word access):
 *   BUS_SPEAKER_COUNT  [R]  number of speakers wired in cable network
 *   SPEAKER(n)         [RW] bits 7:0  = volume (0–255)
 *                           bits 23:8 = frequency in Hz (0 = stop)
 *   Convenience macro:      SPEAKER(n) = SPEAKER_TONE(freq, vol)
 */
#define BUS_SPEAKER_COUNT BUS_REG(0x018)
#define SPEAKER(n)        BUS_REG(0x200 + (n) * 4)
#define SPEAKER_TONE(freq, vol) (((uint32_t)(freq) << 8) | ((vol) & 0xFF))

/* Display slot helpers */
#define DISP_STRIDE     0x10100UL
#define DISP_SLOT(id)   (BUS_BASE + 0x10000UL + ((uint32_t)(id) - 1u) * DISP_STRIDE)

#define DISP_W(id)      (*(volatile uint32_t *)(DISP_SLOT(id) + 0x000))
#define DISP_H(id)      (*(volatile uint32_t *)(DISP_SLOT(id) + 0x004))
#define DISP_FLUSH(id)  (*(volatile uint32_t *)(DISP_SLOT(id) + 0x008) = 1)
#define DISP_CLEAR(id)  (*(volatile uint32_t *)(DISP_SLOT(id) + 0x00C) = 1)
/* pixel[y * width + x] = 0x00RRGGBB */
#define DISP_FB(id)     ((volatile uint32_t *)(DISP_SLOT(id) + 0x010))

#define RGB(r,g,b)      (((uint32_t)(r) << 16) | ((uint32_t)(g) << 8) | (uint32_t)(b))

/* Busy-wait loop — calibrated loosely for Sedna's execution speed */
static inline void delay(volatile long n) { while (n-- > 0) __asm__ volatile("nop"); }
