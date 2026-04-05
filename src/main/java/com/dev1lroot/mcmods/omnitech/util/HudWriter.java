package com.dev1lroot.mcmods.omnitech.util;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;

public class HudWriter
{
    private final GuiGraphicsExtractor graphics;
    private final Font font;
    private final int startX;
    private final int lineHeight;

    private int x;
    private int y;
    private int currentColor;
    private boolean dropShadow;

    public HudWriter(GuiGraphicsExtractor graphics, Font font, int x, int y, int lineHeight, boolean dropShadow)
    {
        this.graphics = graphics;
        this.dropShadow = dropShadow;
        this.font = font;
        this.startX = x;
        this.x = x;
        this.y = y;
        this.lineHeight = lineHeight;
        this.currentColor = ARGB.opaque(0xFFFFFF);
    }

    public HudWriter setColor(int color)
    {
        this.currentColor = color;
        return this;
    }

    public HudWriter write(String text)
    {
        if (text == null || text.isEmpty())
        {
            return this;
        }

        int i = 0;
        int length = text.length();

        StringBuilder buffer = new StringBuilder();

        while (i < length)
        {
            char c = text.charAt(i);

            // Обработка \n
            if (c == '\n')
            {
                flush(buffer);
                newLine();
                i++;
                continue;
            }

            // Обработка &AARRGGBB;
            if (c == '&' && i + 10 <= length && text.charAt(i + 9) == ';')
            {
                String colorHex = text.substring(i + 1, i + 9);

                if (isHex(colorHex))
                {
                    flush(buffer);

                    int color = (int) Long.parseLong(colorHex, 16);
                    setColor(color);

                    i += 10; // пропускаем &AARRGGBB;
                    continue;
                }
            }

            buffer.append(c);
            i++;
        }

        flush(buffer);

        return this;
    }

    private void flush(StringBuilder buffer)
    {
        if (buffer.length() == 0) return;

        String str = buffer.toString();
        graphics.text(font, str, x, y, currentColor, dropShadow);
        x += font.width(str);

        buffer.setLength(0);
    }

    private boolean isHex(String s)
    {
        for (int i = 0; i < s.length(); i++)
        {
            char c = s.charAt(i);
            boolean hex =
                    (c >= '0' && c <= '9') ||
                            (c >= 'A' && c <= 'F') ||
                            (c >= 'a' && c <= 'f');

            if (!hex) return false;
        }
        return true;
    }

    public HudWriter newLine()
    {
        this.x = startX;
        this.y += lineHeight;
        return this;
    }
}