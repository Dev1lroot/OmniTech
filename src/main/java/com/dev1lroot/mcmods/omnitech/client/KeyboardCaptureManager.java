package com.dev1lroot.mcmods.omnitech.client;

import com.dev1lroot.mcmods.omnitech.network.KeyboardReleasePacket;
import com.dev1lroot.mcmods.omnitech.network.TerminalInputPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

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
        if (event.getAction() == GLFW.GLFW_RELEASE) return;

        int key = event.getKey();
        if (isModifierOnly(key)) return;

        byte[] bytes = keyToBytes(key, event.getScanCode(), event.getModifiers());
        if (bytes.length > 0) {
            ClientPacketDistributor.sendToServer(new TerminalInputPacket(logicMachinePos, bytes));
        }
    }

    private static boolean isModifierOnly(int key) {
        return key == GLFW.GLFW_KEY_LEFT_SHIFT   || key == GLFW.GLFW_KEY_RIGHT_SHIFT
            || key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL
            || key == GLFW.GLFW_KEY_LEFT_ALT     || key == GLFW.GLFW_KEY_RIGHT_ALT
            || key == GLFW.GLFW_KEY_LEFT_SUPER   || key == GLFW.GLFW_KEY_RIGHT_SUPER;
    }

    private static byte[] keyToBytes(int key, int scanCode, int modifiers) {
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean ctrl  = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;

        // Ctrl+V / Ctrl+Shift+V → paste from host clipboard
        if (ctrl && key == GLFW.GLFW_KEY_V) {
            return pasteFromClipboard();
        }

        // Ctrl+letter → control bytes 0x01–0x1A
        if (ctrl && key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
            return new byte[]{ (byte)(key - GLFW.GLFW_KEY_A + 1) };
        }

        // Letters (GLFW_KEY_A=65…GLFW_KEY_Z=90 = ASCII uppercase)
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
            return new byte[]{ (byte)(shift ? key : key + 32) };
        }

        // Digits (GLFW_KEY_0=48…GLFW_KEY_9=57 = ASCII digits)
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
            if (!shift) return new byte[]{ (byte) key };
            return switch (key) {
                case GLFW.GLFW_KEY_1 -> new byte[]{'!'};
                case GLFW.GLFW_KEY_2 -> new byte[]{'@'};
                case GLFW.GLFW_KEY_3 -> new byte[]{'#'};
                case GLFW.GLFW_KEY_4 -> new byte[]{'$'};
                case GLFW.GLFW_KEY_5 -> new byte[]{'%'};
                case GLFW.GLFW_KEY_6 -> new byte[]{'^'};
                case GLFW.GLFW_KEY_7 -> new byte[]{'&'};
                case GLFW.GLFW_KEY_8 -> new byte[]{'*'};
                case GLFW.GLFW_KEY_9 -> new byte[]{'('};
                case GLFW.GLFW_KEY_0 -> new byte[]{')'};
                default -> new byte[0];
            };
        }

        // Punctuation
        if (shift) {
            byte[] shifted = switch (key) {
                case GLFW.GLFW_KEY_MINUS         -> new byte[]{'_'};
                case GLFW.GLFW_KEY_EQUAL         -> new byte[]{'+'};
                case GLFW.GLFW_KEY_LEFT_BRACKET  -> new byte[]{'{'};
                case GLFW.GLFW_KEY_RIGHT_BRACKET -> new byte[]{'}'};
                case GLFW.GLFW_KEY_BACKSLASH     -> new byte[]{'|'};
                case GLFW.GLFW_KEY_SEMICOLON     -> new byte[]{':'};
                case GLFW.GLFW_KEY_APOSTROPHE    -> new byte[]{'"'};
                case GLFW.GLFW_KEY_COMMA         -> new byte[]{'<'};
                case GLFW.GLFW_KEY_PERIOD        -> new byte[]{'>'};
                case GLFW.GLFW_KEY_SLASH         -> new byte[]{'?'};
                case GLFW.GLFW_KEY_GRAVE_ACCENT  -> new byte[]{'~'};
                default -> new byte[0];
            };
            if (shifted.length > 0) return shifted;
        } else {
            byte[] plain = switch (key) {
                case GLFW.GLFW_KEY_MINUS         -> new byte[]{'-'};
                case GLFW.GLFW_KEY_EQUAL         -> new byte[]{'='};
                case GLFW.GLFW_KEY_LEFT_BRACKET  -> new byte[]{'['};
                case GLFW.GLFW_KEY_RIGHT_BRACKET -> new byte[]{']'};
                case GLFW.GLFW_KEY_BACKSLASH     -> new byte[]{'\\'};
                case GLFW.GLFW_KEY_SEMICOLON     -> new byte[]{';'};
                case GLFW.GLFW_KEY_APOSTROPHE    -> new byte[]{'\''};
                case GLFW.GLFW_KEY_COMMA         -> new byte[]{','};
                case GLFW.GLFW_KEY_PERIOD        -> new byte[]{'.'};
                case GLFW.GLFW_KEY_SLASH         -> new byte[]{'/'};
                case GLFW.GLFW_KEY_GRAVE_ACCENT  -> new byte[]{'`'};
                default -> new byte[0];
            };
            if (plain.length > 0) return plain;
        }

        // Special keys → VT100/ANSI sequences
        return switch (key) {
            case GLFW.GLFW_KEY_SPACE      -> new byte[]{' '};
            case GLFW.GLFW_KEY_ENTER,
                 GLFW.GLFW_KEY_KP_ENTER  -> new byte[]{'\r'};
            case GLFW.GLFW_KEY_BACKSPACE  -> new byte[]{127};
            case GLFW.GLFW_KEY_TAB        -> new byte[]{'\t'};
            case GLFW.GLFW_KEY_ESCAPE     -> new byte[]{27};
            case GLFW.GLFW_KEY_UP         -> new byte[]{27, '[', 'A'};
            case GLFW.GLFW_KEY_DOWN       -> new byte[]{27, '[', 'B'};
            case GLFW.GLFW_KEY_RIGHT      -> new byte[]{27, '[', 'C'};
            case GLFW.GLFW_KEY_LEFT       -> new byte[]{27, '[', 'D'};
            case GLFW.GLFW_KEY_HOME       -> new byte[]{27, '[', 'H'};
            case GLFW.GLFW_KEY_END        -> new byte[]{27, '[', 'F'};
            case GLFW.GLFW_KEY_PAGE_UP    -> new byte[]{27, '[', '5', '~'};
            case GLFW.GLFW_KEY_PAGE_DOWN  -> new byte[]{27, '[', '6', '~'};
            case GLFW.GLFW_KEY_DELETE     -> new byte[]{27, '[', '3', '~'};
            case GLFW.GLFW_KEY_INSERT     -> new byte[]{27, '[', '2', '~'};
            case GLFW.GLFW_KEY_F1         -> new byte[]{27, 'O', 'P'};
            case GLFW.GLFW_KEY_F2         -> new byte[]{27, 'O', 'Q'};
            case GLFW.GLFW_KEY_F3         -> new byte[]{27, 'O', 'R'};
            case GLFW.GLFW_KEY_F4         -> new byte[]{27, 'O', 'S'};
            case GLFW.GLFW_KEY_F5         -> new byte[]{27, '[', '1', '5', '~'};
            case GLFW.GLFW_KEY_F6         -> new byte[]{27, '[', '1', '7', '~'};
            case GLFW.GLFW_KEY_F7         -> new byte[]{27, '[', '1', '8', '~'};
            case GLFW.GLFW_KEY_F8         -> new byte[]{27, '[', '1', '9', '~'};
            case GLFW.GLFW_KEY_F9         -> new byte[]{27, '[', '2', '0', '~'};
            case GLFW.GLFW_KEY_F10        -> new byte[]{27, '[', '2', '1', '~'};
            case GLFW.GLFW_KEY_F11        -> new byte[]{27, '[', '2', '3', '~'};
            case GLFW.GLFW_KEY_F12        -> new byte[]{27, '[', '2', '4', '~'};
            case GLFW.GLFW_KEY_KP_0       -> new byte[]{'0'};
            case GLFW.GLFW_KEY_KP_1       -> new byte[]{'1'};
            case GLFW.GLFW_KEY_KP_2       -> new byte[]{'2'};
            case GLFW.GLFW_KEY_KP_3       -> new byte[]{'3'};
            case GLFW.GLFW_KEY_KP_4       -> new byte[]{'4'};
            case GLFW.GLFW_KEY_KP_5       -> new byte[]{'5'};
            case GLFW.GLFW_KEY_KP_6       -> new byte[]{'6'};
            case GLFW.GLFW_KEY_KP_7       -> new byte[]{'7'};
            case GLFW.GLFW_KEY_KP_8       -> new byte[]{'8'};
            case GLFW.GLFW_KEY_KP_9       -> new byte[]{'9'};
            case GLFW.GLFW_KEY_KP_DECIMAL  -> new byte[]{'.'};
            case GLFW.GLFW_KEY_KP_ADD      -> new byte[]{'+'};
            case GLFW.GLFW_KEY_KP_SUBTRACT -> new byte[]{'-'};
            case GLFW.GLFW_KEY_KP_MULTIPLY -> new byte[]{'*'};
            case GLFW.GLFW_KEY_KP_DIVIDE   -> new byte[]{'/'};
            default -> new byte[0];
        };
    }

    private static byte[] pasteFromClipboard() {
        long window = Minecraft.getInstance().getWindow().handle();
        String text = GLFW.glfwGetClipboardString(window);
        if (text == null || text.isEmpty()) return new byte[0];
        // Normalize line endings so pasted newlines behave like pressing Enter
        text = text.replace("\r\n", "\r").replace("\n", "\r");
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private KeyboardCaptureManager() {}
}
