# OmniTech bare-metal firmware build system
# Target: Sedna R5Board (RV64IMAC, boot at 0x80000000)
#
# Install toolchain on Ubuntu/Debian:
#   sudo apt install gcc-riscv64-linux-gnu binutils-riscv64-linux-gnu
#
# Or use the upstream RISC-V toolchain (https://github.com/riscv-collab/riscv-gnu-toolchain):
#   export CC=riscv64-unknown-elf-gcc
#   export OBJCOPY=riscv64-unknown-elf-objcopy

CC      ?= riscv64-linux-gnu-gcc
OBJCOPY ?= riscv64-linux-gnu-objcopy
CFLAGS   = -march=rv64imac_zicsr -mabi=lp64 -O2 -g \
           -ffreestanding -nostdlib -nostartfiles \
           -Wall -Wextra \
           -I.
LDFLAGS  = -T link.ld -nostdlib

SRCS = $(wildcard examples/*.c)
BINS = $(SRCS:examples/%.c=out/%.bin)
ELFS = $(SRCS:examples/%.c=out/%.elf)

all: out $(BINS)
	@echo ""
	@echo "Built firmware images:"
	@ls -lh out/*.bin 2>/dev/null || true

out:
	mkdir -p out

out/%.elf: examples/%.c start.S link.ld omnitech.h
	$(CC) $(CFLAGS) $(LDFLAGS) start.S $< -o $@

out/%.bin: out/%.elf
	$(OBJCOPY) -O binary --strip-all $< $@
	@printf "  %-32s %d bytes\n" "$@" "$$(wc -c < $@)"

clean:
	rm -rf out

.PHONY: all clean
