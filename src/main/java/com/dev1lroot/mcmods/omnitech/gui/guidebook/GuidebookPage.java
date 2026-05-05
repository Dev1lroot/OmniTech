package com.dev1lroot.mcmods.omnitech.gui.guidebook;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import java.util.List;

public record GuidebookPage(String chapterName, List<PageToken> tokens) {

    public sealed interface PageToken
            permits PageToken.Heading, PageToken.SubHeading, PageToken.Body,
                    PageToken.Image, PageToken.LinkBlock, PageToken.Spacer {

        record Heading(String text) implements PageToken {}
        record SubHeading(String text) implements PageToken {}
        record Body(Component content) implements PageToken {}
        record Image(Identifier texture, int displayWidth, int displayHeight) implements PageToken {}
        record Spacer(int pixels) implements PageToken {}

        /** A standalone clickable link rendered on its own line. */
        record LinkBlock(String label, LinkTarget target) implements PageToken {}
    }

    /**
     * Destination for a link.
     * <ul>
     *   <li>{@link Url} — opens in the system browser</li>
     *   <li>{@link PageJump} — navigates to the given 0-based page index within the book</li>
     * </ul>
     */
    public sealed interface LinkTarget permits LinkTarget.Url, LinkTarget.PageJump {
        record Url(String url) implements LinkTarget {}
        record PageJump(int pageIndex) implements LinkTarget {}
    }
}
