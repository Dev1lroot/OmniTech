/* 03_display_gradient.c — RGB gradient on display portId=1.
 * Place a Display block on the cable network with portId=1.
 * Any cluster size works — the canvas dimensions are read at runtime.
 */
#include "../omnitech.h"

int main(void) {
    uart_puts("Display gradient\r\n");

    uint32_t w = DISP_W(1);
    uint32_t h = DISP_H(1);
    uart_puts("Canvas: "); uart_puthex32(w);
    uart_puts("x");        uart_puthex32(h); uart_puts("\r\n");

    if (w == 0 || h == 0) {
        uart_puts("No display at portId=1 — attach a Display block\r\n");
        for (;;);
    }

    volatile uint32_t *fb = DISP_FB(1);
    for (uint32_t y = 0; y < h; y++) {
        for (uint32_t x = 0; x < w; x++) {
            uint32_t r = (x * 255u) / (w > 1u ? w - 1u : 1u);
            uint32_t g = (y * 255u) / (h > 1u ? h - 1u : 1u);
            uint32_t b = 64;
            fb[y * w + x] = RGB(r, g, b);
        }
    }
    DISP_FLUSH(1);
    uart_puts("Gradient flushed\r\n");

    for (;;);
}
