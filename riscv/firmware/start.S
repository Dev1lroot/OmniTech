/* Minimal bare-metal startup for Sedna R5Board (RV64IMAC).
 * Sedna's boot ROM jumps here (0x80000000) in M-mode with:
 *   a0 = hart ID (always 0 for single-core)
 *   a1 = DTB address (ignore for simple firmware)
 */
    .section .text.start
    .globl _start
_start:
    /* Set up stack pointer */
    la      sp, _stack_top

    /* Clear BSS */
    la      t0, _bss_start
    la      t1, _bss_end
.Lbss_loop:
    bge     t0, t1, .Lbss_done
    sd      zero, 0(t0)
    addi    t0, t0, 8
    j       .Lbss_loop
.Lbss_done:

    call    main

    /* If main returns, halt */
.Lhalt:
    j       .Lhalt
