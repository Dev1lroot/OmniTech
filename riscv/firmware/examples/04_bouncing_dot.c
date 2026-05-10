/* 04_bouncing_dot.c — White dot bouncing on display portId=1.
 * Pulses GPIO port 0 on every wall bounce.
 * Canvas size is read at startup — works on any cluster.
 */
#include "../omnitech.h"

static void fill(uint32_t portId, uint32_t color) {
    uint32_t w = DISP_W(portId), h = DISP_H(portId);
    volatile uint32_t *fb = DISP_FB(portId);
    for (uint32_t i = 0; i < w * h; i++) fb[i] = color;
}

int main(void) {
    uart_puts("Bouncing dot\r\n");

    uint32_t w = DISP_W(1);
    uint32_t h = DISP_H(1);
    if (w == 0 || h == 0) {
        uart_puts("No display at portId=1\r\n");
        for (;;);
    }

    int32_t x = 4, y = 4;
    int32_t dx = 1, dy = 1;
    int32_t maxX = (int32_t)w - 1;
    int32_t maxY = (int32_t)h - 1;

    volatile uint32_t *fb = DISP_FB(1);

    for (;;) {
        /* Clear */
        for (uint32_t i = 0; i < w * h; i++) fb[i] = 0;

        /* Draw dot */
        if (x >= 0 && y >= 0 && x < (int32_t)w && y < (int32_t)h)
            fb[(uint32_t)y * w + (uint32_t)x] = RGB(255, 255, 255);

        DISP_FLUSH(1);

        /* Move */
        x += dx;
        y += dy;

        /* Bounce */
        int bounced = 0;
        if (x <= 0)    { x = 0;    dx =  1; bounced = 1; }
        if (x >= maxX) { x = maxX; dx = -1; bounced = 1; }
        if (y <= 0)    { y = 0;    dy =  1; bounced = 1; }
        if (y >= maxY) { y = maxY; dy = -1; bounced = 1; }

        if (bounced) {
            GPIO(0) = 15;
            delay(100000);
            GPIO(0) = 0;
        }

        delay(300000);
    }
}
