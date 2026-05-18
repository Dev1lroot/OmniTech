/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.gui.guidebook;

import com.dev1lroot.mcmods.omnitech.OmniTech;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Loads guidebook pages from {@code assets/<modid>/guidebook/<locale>/} at resource reload time.
 *
 * <h3>File layout</h3>
 * <pre>
 * assets/omnitech/guidebook/en_us/
 *   00_introduction/
 *     00_welcome.md
 *   01_kinetics/
 *     00_overview.md
 * </pre>
 *
 * <h3>Markdown format</h3>
 * <pre>
 * ---
 * image: omnitech:textures/gui/guidebook/example_banner.png
 * image_width: 120
 * image_height: 60
 * ---
 *
 * # Heading
 * ## Subheading
 * Body text with **bold** and *italic*.
 * ![alt](omnitech:textures/gui/guidebook/img.png){120x60}
 *
 * [Link label](https://example.com)
 * [Go to page 2](page:2)
 * </pre>
 *
 * <h3>Link syntax</h3>
 * A line whose only content is {@code [label](target)} becomes a clickable {@link GuidebookPage.PageToken.LinkBlock}.
 * <ul>
 *   <li>{@code [text](https://...)} — opens URL in the system browser</li>
 *   <li>{@code [text](page:N)} — jumps to page N (1-based)</li>
 * </ul>
 */
public class GuidebookLoader implements PreparableReloadListener {

    private static final String FALLBACK_LOCALE = "en_us";
    private static volatile Map<String, List<GuidebookPage>> pagesByLocale = Map.of();

    public static List<GuidebookPage> getPages() {
        String locale = FALLBACK_LOCALE;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getLanguageManager() != null)
                locale = mc.getLanguageManager().getSelected();
        } catch (Exception ignored) {}
        List<GuidebookPage> result = pagesByLocale.get(locale);
        if (result != null && !result.isEmpty()) return result;
        return pagesByLocale.getOrDefault(FALLBACK_LOCALE, List.of());
    }

    @Override
    public CompletableFuture<Void> reload(SharedState shared, Executor taskExecutor,
                                          PreparationBarrier barrier, Executor reloadExecutor) {
        return CompletableFuture
                .supplyAsync(() -> loadAll(shared.resourceManager()), taskExecutor)
                .thenCompose(barrier::wait)
                .thenAcceptAsync(loaded -> pagesByLocale = loaded, reloadExecutor);
    }

    // ── Loading ───────────────────────────────────────────────────────────────

    private static Map<String, List<GuidebookPage>> loadAll(ResourceManager rm) {
        Map<Identifier, Resource> all = rm.listResources("guidebook",
                id -> id.getNamespace().equals(OmniTech.MODID) && id.getPath().endsWith(".md"));

        Set<String> locales = new HashSet<>();
        for (Identifier id : all.keySet()) {
            String[] parts = id.getPath().split("/", 3);
            if (parts.length >= 3) locales.add(parts[1]);
        }

        Map<String, List<GuidebookPage>> result = new HashMap<>();
        for (String locale : locales)
            result.put(locale, loadLocale(rm, locale, all));
        return result;
    }

    private static List<GuidebookPage> loadLocale(ResourceManager rm, String locale,
                                                   Map<Identifier, Resource> all) {
        String prefix = "guidebook/" + locale + "/";
        Map<String, TreeMap<String, Identifier>> chapters = new TreeMap<>();

        for (Identifier id : all.keySet()) {
            String path = id.getPath();
            if (!path.startsWith(prefix)) continue;
            String relative = path.substring(prefix.length());
            int slash = relative.indexOf('/');
            if (slash < 0) continue;
            String chapterKey = relative.substring(0, slash);
            String pageKey = relative.substring(slash + 1);
            chapters.computeIfAbsent(chapterKey, k -> new TreeMap<>()).put(pageKey, id);
        }

        List<GuidebookPage> pages = new ArrayList<>();
        for (Map.Entry<String, TreeMap<String, Identifier>> ce : chapters.entrySet()) {
            String chapterName = prettify(ce.getKey());
            for (Map.Entry<String, Identifier> pe : ce.getValue().entrySet()) {
                try {
                    Resource res = rm.getResource(pe.getValue()).orElse(null);
                    if (res == null) continue;
                    String md;
                    try (BufferedReader r = new BufferedReader(
                            new InputStreamReader(res.open(), StandardCharsets.UTF_8))) {
                        md = r.lines().collect(Collectors.joining("\n"));
                    }
                    pages.add(parse(chapterName, md));
                } catch (Exception e) {
                    OmniTech.LOGGER.warn("[Guidebook] Failed to load {}: {}", pe.getValue(), e.getMessage());
                }
            }
        }
        return pages;
    }

    private static String prettify(String name) {
        String s = name.replaceFirst("^\\d+_", "").replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ── Markdown parsing ──────────────────────────────────────────────────────

    private static GuidebookPage parse(String chapterName, String content) {
        List<GuidebookPage.PageToken> tokens = new ArrayList<>();
        String[] lines = content.split("\n", -1);
        int start = 0;

        // Front matter
        if (lines.length > 0 && lines[0].trim().equals("---")) {
            int end = -1;
            for (int i = 1; i < lines.length; i++) {
                if (lines[i].trim().equals("---")) { end = i; break; }
            }
            if (end > 0) {
                String imageStr = null;
                int imgW = 120, imgH = 80;
                for (int i = 1; i < end; i++) {
                    String line = lines[i].trim();
                    if (line.startsWith("image:"))
                        imageStr = line.substring(6).trim();
                    else if (line.startsWith("image_width:"))
                        try { imgW = Integer.parseInt(line.substring(12).trim()); } catch (NumberFormatException ignored) {}
                    else if (line.startsWith("image_height:"))
                        try { imgH = Integer.parseInt(line.substring(13).trim()); } catch (NumberFormatException ignored) {}
                }
                if (imageStr != null && !imageStr.isEmpty()) {
                    try {
                        tokens.add(new GuidebookPage.PageToken.Image(Identifier.parse(imageStr), imgW, imgH));
                        tokens.add(new GuidebookPage.PageToken.Spacer(6));
                    } catch (Exception e) {
                        OmniTech.LOGGER.warn("[Guidebook] Invalid image id in front matter: {}", imageStr);
                    }
                }
                start = end + 1;
            }
        }

        StringBuilder para = null;
        for (int i = start; i < lines.length; i++) {
            String trimmed = lines[i].trim();

            if (trimmed.isEmpty()) {
                para = flush(tokens, para);
                tokens.add(new GuidebookPage.PageToken.Spacer(5));
                continue;
            }
            if (trimmed.startsWith("# ")) {
                para = flush(tokens, para);
                tokens.add(new GuidebookPage.PageToken.Heading(trimmed.substring(2)));
                tokens.add(new GuidebookPage.PageToken.Spacer(2));
                continue;
            }
            if (trimmed.startsWith("## ")) {
                para = flush(tokens, para);
                tokens.add(new GuidebookPage.PageToken.SubHeading(trimmed.substring(3)));
                tokens.add(new GuidebookPage.PageToken.Spacer(2));
                continue;
            }
            // Standalone link: [label](target)
            if (trimmed.startsWith("[") && trimmed.contains("](") && trimmed.endsWith(")")) {
                int labelEnd = trimmed.indexOf("](");
                if (labelEnd > 1) {
                    String label = trimmed.substring(1, labelEnd);
                    String target = trimmed.substring(labelEnd + 2, trimmed.length() - 1);
                    // Only treat as standalone if it's the whole line (no surrounding text)
                    if (trimmed.indexOf('[') == 0) {
                        para = flush(tokens, para);
                        tokens.add(new GuidebookPage.PageToken.LinkBlock(label, parseLinkTarget(target)));
                        continue;
                    }
                }
            }
            // Inline image: ![alt](texture){WxH}
            if (trimmed.startsWith("![") && trimmed.contains("](")) {
                para = flush(tokens, para);
                int urlStart = trimmed.indexOf("](") + 2;
                int urlEnd = trimmed.indexOf(')', urlStart);
                if (urlEnd > urlStart) {
                    String texStr = trimmed.substring(urlStart, urlEnd);
                    int imgW = 120, imgH = 80;
                    if (urlEnd + 1 < trimmed.length() && trimmed.charAt(urlEnd + 1) == '{') {
                        int dimEnd = trimmed.indexOf('}', urlEnd + 2);
                        if (dimEnd > 0) {
                            String[] dims = trimmed.substring(urlEnd + 2, dimEnd).split("x", 2);
                            try { imgW = Integer.parseInt(dims[0]); } catch (NumberFormatException ignored) {}
                            if (dims.length > 1) try { imgH = Integer.parseInt(dims[1]); } catch (NumberFormatException ignored) {}
                        }
                    }
                    try {
                        tokens.add(new GuidebookPage.PageToken.Image(Identifier.parse(texStr), imgW, imgH));
                        tokens.add(new GuidebookPage.PageToken.Spacer(4));
                    } catch (Exception e) {
                        OmniTech.LOGGER.warn("[Guidebook] Invalid image identifier: {}", texStr);
                    }
                }
                continue;
            }
            // Paragraph text
            if (para == null) para = new StringBuilder();
            else para.append(' ');
            para.append(trimmed);
        }
        flush(tokens, para);

        return new GuidebookPage(chapterName, tokens);
    }

    private static StringBuilder flush(List<GuidebookPage.PageToken> tokens, StringBuilder para) {
        if (para != null && para.length() > 0)
            tokens.add(new GuidebookPage.PageToken.Body(parseInline(para.toString())));
        return null;
    }

    private static GuidebookPage.LinkTarget parseLinkTarget(String target) {
        if (target.startsWith("http://") || target.startsWith("https://"))
            return new GuidebookPage.LinkTarget.Url(target);
        if (target.startsWith("page:")) {
            try {
                int idx = Integer.parseInt(target.substring(5).trim()) - 1; // 1-based → 0-based
                return new GuidebookPage.LinkTarget.PageJump(Math.max(0, idx));
            } catch (NumberFormatException e) {
                OmniTech.LOGGER.warn("[Guidebook] Invalid page link target: {}", target);
            }
        }
        return new GuidebookPage.LinkTarget.Url(target);
    }

    private static Component parseInline(String text) {
        var result = Component.empty();
        int i = 0;
        while (i < text.length()) {
            if (i + 1 < text.length() && text.charAt(i) == '*' && text.charAt(i + 1) == '*') {
                int end = text.indexOf("**", i + 2);
                if (end >= 0) {
                    result = result.copy().append(
                            Component.literal(text.substring(i + 2, end))
                                    .withStyle(s -> s.withBold(true)));
                    i = end + 2;
                    continue;
                }
            }
            if (text.charAt(i) == '*') {
                int end = text.indexOf('*', i + 1);
                if (end >= 0) {
                    result = result.copy().append(
                            Component.literal(text.substring(i + 1, end))
                                    .withStyle(s -> s.withItalic(true)));
                    i = end + 1;
                    continue;
                }
            }
            int next = text.indexOf('*', i);
            if (next < 0) next = text.length();
            result = result.copy().append(Component.literal(text.substring(i, next)));
            i = next;
        }
        return result;
    }
}
