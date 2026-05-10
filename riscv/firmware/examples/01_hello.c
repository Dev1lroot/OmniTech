/* 01_hello.c — Hello World via UART terminal.
 * Output appears on display portId=0 (the terminal display).
 * No extra hardware needed beyond the Logic Machine and one Display block.
 */
#include "../omnitech.h"

int main(void) {
    uart_puts("OmniTech firmware v1\r\n");
    uart_puts("Bus magic: "); uart_puthex32(BUS_MAGIC); uart_puts("\r\n");
    uart_puts("GPIO ports: "); uart_puthex32(BUS_GPIO_COUNT); uart_puts("\r\n");
    uart_puts("Displays:   "); uart_puthex32(BUS_DISP_COUNT);  uart_puts("\r\n");

    uint32_t n = 0;
    for (;;) {
        uart_puts("tick "); uart_puthex32(n++); uart_puts("\r\n");
        delay(2000000);
    }
}
