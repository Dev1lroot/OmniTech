/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.network.KeyboardReleasePacket;
import com.dev1lroot.mcmods.omnitech.network.TerminalInputPacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.sdl.SDLScancode;

import java.nio.charset.StandardCharsets;

public final class KeyboardCaptureManager {

    private static boolean active = false;
    private static BlockPos logicMachinePos = null;

    public static void activate(BlockPos pos) {
        active = true;
        logicMachinePos = pos;
    }

    /** Release capture and notify the server. */
    public static void deactivate() {
        if (!active) return;
        active = false;
        logicMachinePos = null;
        ClientPacketDistributor.sendToServer(new KeyboardReleasePacket());
    }

    /** Release capture without sending a packet (connection is being closed). */
    public static void deactivateLocal() {
        active = false;
        logicMachinePos = null;
    }

    public static boolean isActive() { return active; }

    /** NeoForge InputEvent.Key — fires after all key processing (not cancellable). */
    public static void onKeyInput(InputEvent.Key event) {
        if (!active || logicMachinePos == null) return;
        if (event.getAction() == InputConstants.RELEASE) return;

        int key = event.getKey();
        if (isModifierOnly(key)) return;

        byte[] bytes = keyToBytes(key, event.getModifiers());
        if (bytes.length > 0) {
            ClientPacketDistributor.sendToServer(new TerminalInputPacket(logicMachinePos, bytes));
        }
    }

    private static boolean isModifierOnly(int key) {
        return key == InputConstants.KEY_LSHIFT   || key == InputConstants.KEY_RSHIFT
            || key == InputConstants.KEY_LCONTROL || key == InputConstants.KEY_RCONTROL
            || key == InputConstants.KEY_LALT     || key == InputConstants.KEY_RALT
            || key == InputConstants.KEY_LGUI     || key == InputConstants.KEY_RGUI;
    }

    private static byte[] keyToBytes(int key, int modifiers) {
        boolean shift = (modifiers & InputConstants.MOD_SHIFT) != 0;
        boolean ctrl  = (modifiers & InputConstants.MOD_CONTROL) != 0;

        // Ctrl+V / Ctrl+Shift+V → paste from host clipboard
        if (ctrl && key == InputConstants.KEY_V) {
            return pasteFromClipboard();
        }

        // Ctrl+letter → control bytes 0x01–0x1A (A..Z are contiguous in InputConstants)
        if (ctrl && key >= InputConstants.KEY_A && key <= InputConstants.KEY_Z) {
            return new byte[]{ (byte)(key - InputConstants.KEY_A + 1) };
        }

        // Letters → ASCII upper/lower depending on Shift (A..Z are not ASCII-valued here)
        if (key >= InputConstants.KEY_A && key <= InputConstants.KEY_Z) {
            char lower = (char) ('a' + (key - InputConstants.KEY_A));
            return new byte[]{ (byte)(shift ? Character.toUpperCase(lower) : lower) };
        }

        // Digits (KEY_1..KEY_9, then KEY_0) → ASCII digit, or shifted symbol row
        if (shift) {
            byte[] shifted = switch (key) {
                case InputConstants.KEY_1 -> new byte[]{'!'};
                case InputConstants.KEY_2 -> new byte[]{'@'};
                case InputConstants.KEY_3 -> new byte[]{'#'};
                case InputConstants.KEY_4 -> new byte[]{'$'};
                case InputConstants.KEY_5 -> new byte[]{'%'};
                case InputConstants.KEY_6 -> new byte[]{'^'};
                case InputConstants.KEY_7 -> new byte[]{'&'};
                case InputConstants.KEY_8 -> new byte[]{'*'};
                case InputConstants.KEY_9 -> new byte[]{'('};
                case InputConstants.KEY_0 -> new byte[]{')'};
                default -> new byte[0];
            };
            if (shifted.length > 0) return shifted;
        } else {
            byte[] plain = switch (key) {
                case InputConstants.KEY_1 -> new byte[]{'1'};
                case InputConstants.KEY_2 -> new byte[]{'2'};
                case InputConstants.KEY_3 -> new byte[]{'3'};
                case InputConstants.KEY_4 -> new byte[]{'4'};
                case InputConstants.KEY_5 -> new byte[]{'5'};
                case InputConstants.KEY_6 -> new byte[]{'6'};
                case InputConstants.KEY_7 -> new byte[]{'7'};
                case InputConstants.KEY_8 -> new byte[]{'8'};
                case InputConstants.KEY_9 -> new byte[]{'9'};
                case InputConstants.KEY_0 -> new byte[]{'0'};
                default -> new byte[0];
            };
            if (plain.length > 0) return plain;
        }

        // Punctuation
        if (shift) {
            byte[] shifted = switch (key) {
                case InputConstants.KEY_MINUS      -> new byte[]{'_'};
                case InputConstants.KEY_EQUALS     -> new byte[]{'+'};
                case InputConstants.KEY_LBRACKET   -> new byte[]{'{'};
                case InputConstants.KEY_RBRACKET   -> new byte[]{'}'};
                case InputConstants.KEY_BACKSLASH  -> new byte[]{'|'};
                case InputConstants.KEY_SEMICOLON  -> new byte[]{':'};
                case InputConstants.KEY_APOSTROPHE -> new byte[]{'"'};
                case InputConstants.KEY_COMMA      -> new byte[]{'<'};
                case InputConstants.KEY_PERIOD     -> new byte[]{'>'};
                case InputConstants.KEY_SLASH      -> new byte[]{'?'};
                case InputConstants.KEY_GRAVE      -> new byte[]{'~'};
                default -> new byte[0];
            };
            if (shifted.length > 0) return shifted;
        } else {
            byte[] plain = switch (key) {
                case InputConstants.KEY_MINUS      -> new byte[]{'-'};
                case InputConstants.KEY_EQUALS     -> new byte[]{'='};
                case InputConstants.KEY_LBRACKET   -> new byte[]{'['};
                case InputConstants.KEY_RBRACKET   -> new byte[]{']'};
                case InputConstants.KEY_BACKSLASH  -> new byte[]{'\\'};
                case InputConstants.KEY_SEMICOLON  -> new byte[]{';'};
                case InputConstants.KEY_APOSTROPHE -> new byte[]{'\''};
                case InputConstants.KEY_COMMA      -> new byte[]{','};
                case InputConstants.KEY_PERIOD     -> new byte[]{'.'};
                case InputConstants.KEY_SLASH      -> new byte[]{'/'};
                case InputConstants.KEY_GRAVE      -> new byte[]{'`'};
                default -> new byte[0];
            };
            if (plain.length > 0) return plain;
        }

        // Special keys → VT100/ANSI sequences
        return switch (key) {
            case InputConstants.KEY_SPACE      -> new byte[]{' '};
            case InputConstants.KEY_RETURN,
                 InputConstants.KEY_NUMPADENTER -> new byte[]{'\r'};
            case InputConstants.KEY_BACKSPACE  -> new byte[]{127};
            case InputConstants.KEY_TAB        -> new byte[]{'\t'};
            case InputConstants.KEY_ESCAPE     -> new byte[]{27};
            case InputConstants.KEY_UP         -> new byte[]{27, '[', 'A'};
            case InputConstants.KEY_DOWN       -> new byte[]{27, '[', 'B'};
            case InputConstants.KEY_RIGHT      -> new byte[]{27, '[', 'C'};
            case InputConstants.KEY_LEFT       -> new byte[]{27, '[', 'D'};
            case InputConstants.KEY_HOME       -> new byte[]{27, '[', 'H'};
            case InputConstants.KEY_END        -> new byte[]{27, '[', 'F'};
            case InputConstants.KEY_PAGEUP     -> new byte[]{27, '[', '5', '~'};
            case InputConstants.KEY_PAGEDOWN   -> new byte[]{27, '[', '6', '~'};
            case InputConstants.KEY_DELETE     -> new byte[]{27, '[', '3', '~'};
            case InputConstants.KEY_INSERT     -> new byte[]{27, '[', '2', '~'};
            case InputConstants.KEY_F1         -> new byte[]{27, 'O', 'P'};
            case InputConstants.KEY_F2         -> new byte[]{27, 'O', 'Q'};
            case InputConstants.KEY_F3         -> new byte[]{27, 'O', 'R'};
            case InputConstants.KEY_F4         -> new byte[]{27, 'O', 'S'};
            case InputConstants.KEY_F5         -> new byte[]{27, '[', '1', '5', '~'};
            case InputConstants.KEY_F6         -> new byte[]{27, '[', '1', '7', '~'};
            case InputConstants.KEY_F7         -> new byte[]{27, '[', '1', '8', '~'};
            case InputConstants.KEY_F8         -> new byte[]{27, '[', '1', '9', '~'};
            case InputConstants.KEY_F9         -> new byte[]{27, '[', '2', '0', '~'};
            case InputConstants.KEY_F10        -> new byte[]{27, '[', '2', '1', '~'};
            case InputConstants.KEY_F11        -> new byte[]{27, '[', '2', '3', '~'};
            case InputConstants.KEY_F12        -> new byte[]{27, '[', '2', '4', '~'};
            case InputConstants.KEY_NUMPAD0    -> new byte[]{'0'};
            case InputConstants.KEY_NUMPAD1    -> new byte[]{'1'};
            case InputConstants.KEY_NUMPAD2    -> new byte[]{'2'};
            case InputConstants.KEY_NUMPAD3    -> new byte[]{'3'};
            case InputConstants.KEY_NUMPAD4    -> new byte[]{'4'};
            case InputConstants.KEY_NUMPAD5    -> new byte[]{'5'};
            case InputConstants.KEY_NUMPAD6    -> new byte[]{'6'};
            case InputConstants.KEY_NUMPAD7    -> new byte[]{'7'};
            case InputConstants.KEY_NUMPAD8    -> new byte[]{'8'};
            case InputConstants.KEY_NUMPAD9    -> new byte[]{'9'};
            case SDLScancode.SDL_SCANCODE_KP_PERIOD  -> new byte[]{'.'};
            case InputConstants.KEY_ADD              -> new byte[]{'+'};
            case SDLScancode.SDL_SCANCODE_KP_MINUS   -> new byte[]{'-'};
            case InputConstants.KEY_MULTIPLY         -> new byte[]{'*'};
            case SDLScancode.SDL_SCANCODE_KP_DIVIDE  -> new byte[]{'/'};
            default -> new byte[0];
        };
    }

    private static byte[] pasteFromClipboard() {
        String text = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (text == null || text.isEmpty()) return new byte[0];
        // Normalize line endings so pasted newlines behave like pressing Enter
        text = text.replace("\r\n", "\r").replace("\n", "\r");
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private KeyboardCaptureManager() {}
}
