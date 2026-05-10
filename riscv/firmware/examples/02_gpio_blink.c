/* 02_gpio_blink.c — Toggle GPIO port 0 between 15 and 0 repeatedly.
 * Connect a Redstone cable to GPIO port 0 on the cable network.
 * The port will pulse with ~1 second on/off.
 */
#include "../omnitech.h"

int main(void) {
    uart_puts("GPIO blink start\r\n");
    uart_puts("GPIO port count: "); uart_puthex32(BUS_GPIO_COUNT); uart_puts("\r\n");

    for (;;) {
        GPIO(0) = 15;
        uart_puts("GPIO0 = 15\r\n");
        delay(5000000);

        GPIO(0) = 0;
        uart_puts("GPIO0 = 0\r\n");
        delay(5000000);
    }
}
