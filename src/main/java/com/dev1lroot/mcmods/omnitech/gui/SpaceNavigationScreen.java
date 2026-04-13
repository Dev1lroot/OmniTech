package com.dev1lroot.mcmods.omnitech.gui;

import com.dev1lroot.mcmods.omnitech.entities.RocketEntity;
import com.dev1lroot.mcmods.omnitech.network.SpaceTravelPacket;
import com.dev1lroot.mcmods.omnitech.space.CelestialBody;
import com.dev1lroot.mcmods.omnitech.space.Galaxy;
import com.dev1lroot.mcmods.omnitech.space.SpaceMap;
import com.dev1lroot.mcmods.omnitech.space.SpaceMapLoader;
import com.dev1lroot.mcmods.omnitech.space.StarSystem;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen space navigation GUI.
 *
 * Navigation: GALAXY → SYSTEM → BODY (planets) → MOON
 *
 * Each level renders the "parent" body large at screen center with its
 * children on animated circular orbits around it.  Clicking an orbiting
 * body selects it as the travel destination; if that body has children
 * (e.g. a planet with moons) an "Explore moons" button appears so the
 * player can optionally drill deeper without losing the planet selection.
 *
 * Bodies and the central body render using their texture field from space_map.json
 * (falling back to a colored disc if the texture is null).  Background textures
 * per level are also drawn from the background field.  All body sizes scale with zoom.
 */
public class SpaceNavigationScreen extends Screen {

    // ── Navigation state ─────────────────────────────────────────────────────
    private enum NavLevel { GALAXY, SYSTEM, BODY, MOON }

    private NavLevel      navLevel = NavLevel.GALAXY;
    private Galaxy        selectedGalaxy;
    private StarSystem    selectedSystem;
    private CelestialBody selectedPlanet;
    private CelestialBody destination;

    // ── Data ─────────────────────────────────────────────────────────────────
    private SpaceMap spaceMap;
    private String   currentDimensionId = "";

    // ── Items for the current orbital view ───────────────────────────────────
    private final List<BodyItem> items = new ArrayList<>();

    /**
     * Body positions computed each frame so click detection stays in sync
     * with the animation even without re-rendering.
     */
    private final List<BodyPos> bodyPositions = new ArrayList<>();

    // ── Layout ───────────────────────────────────────────────────────────────
    private static final int TOP_H    = 50;  // header area height
    private static final int BOTTOM_H = 64;  // bottom bar height
    private static final int CENTER_R = 40;  // central body base radius
    private static final int ORBIT_R  = 18;  // orbiting body base radius
    private static final int HIT_R    = 24;  // minimum click detection radius

    /** Base orbital speed for a body whose orbital_radius == SPEED_REF_R. */
    private static final double ORBIT_SPEED = (2 * Math.PI) / 30_000.0; // rad/ms
    /** Reference radius for speed normalisation (Kepler-like: inner = faster). */
    private static final int    SPEED_REF_R = 200;
    private static final double ZOOM_MIN    = 0.15;
    private static final double ZOOM_MAX    = 4.0;
    private static final double ZOOM_STEP   = 0.12; // fraction per scroll tick

    // ── Zoom ─────────────────────────────────────────────────────────────────
    private double zoom = 1.0;

    // ── Widgets ──────────────────────────────────────────────────────────────
    private Button launchBtn;
    private Button exploreMoonsBtn;

    // ── Colors ───────────────────────────────────────────────────────────────
    private static final int C_BG         = 0xFF020208;
    private static final int C_RING       = 0x33405060;
    private static final int C_TEXT_TITLE = 0xFF88DDFF;
    private static final int C_TEXT_SUB   = 0xFF556677;
    private static final int C_TEXT_BODY  = 0xFFCCDDEE;
    private static final int C_TEXT_CUR   = 0xFF40EE88;
    private static final int C_TEXT_SEL   = 0xFF80FFCC;
    private static final int C_TEXT_NA    = 0xFF334455;
    private static final int C_FUEL_OK    = 0xFF40EE70;
    private static final int C_FUEL_BAD   = 0xFFEE4040;
    private static final int C_BAR_BG     = 0xCC030310;
    private static final int C_BAR_LINE   = 0xFF1A2840;

    public SpaceNavigationScreen() {
        super(Component.literal("Space Navigation"));
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        if (minecraft.player != null)
            currentDimensionId = minecraft.player.level().dimension().identifier().toString();

        spaceMap = SpaceMapLoader.load(minecraft.getResourceManager());

        if (navLevel == NavLevel.GALAXY) {
            SpaceMap.Location loc = spaceMap.findLocation(currentDimensionId);
            if (loc != null) {
                selectedGalaxy = loc.galaxy();
                selectedSystem = loc.system();
                navLevel = NavLevel.BODY;
            }
        }

        rebuildItems();
        refreshWidgets();
    }

    private void rebuildItems() {
        items.clear();
        switch (navLevel) {
            case GALAXY -> { if (spaceMap.galaxies != null) spaceMap.galaxies.forEach(g -> items.add(new BodyItem(g.name, g))); }
            case SYSTEM -> { if (selectedGalaxy != null && selectedGalaxy.star_systems != null) selectedGalaxy.star_systems.forEach(s -> items.add(new BodyItem(s.name, s))); }
            case BODY   -> { if (selectedSystem != null && selectedSystem.bodies != null) selectedSystem.bodies.forEach(b -> items.add(new BodyItem(b.name, b))); }
            case MOON   -> { if (selectedPlanet != null && selectedPlanet.moons != null) selectedPlanet.moons.forEach(m -> items.add(new BodyItem(m.name, m))); }
        }
    }

    private void refreshWidgets() {
        clearWidgets();

        // Back
        if (navLevel != NavLevel.GALAXY) {
            addRenderableWidget(Button.builder(Component.literal("< Back"), btn -> navigateBack())
                    .bounds(8, 8, 55, 18).build());
        }

        // Close
        addRenderableWidget(Button.builder(Component.literal("X"), btn -> onClose())
                .bounds(width - 24, 8, 16, 16).build());

        // Zoom buttons (top-right, below close)
        addRenderableWidget(Button.builder(Component.literal("+"), btn -> adjustZoom(+1))
                .bounds(width - 24, 30, 16, 16).build());
        addRenderableWidget(Button.builder(Component.literal("-"), btn -> adjustZoom(-1))
                .bounds(width - 24, 48, 16, 16).build());

        // Launch — only when a valid destination is selected
        if (canLaunch()) {
            launchBtn = Button.builder(Component.literal("LAUNCH"), btn -> onLaunch())
                    .bounds(width - 106, height - BOTTOM_H + 20, 98, 22).build();
            addRenderableWidget(launchBtn);
        } else {
            launchBtn = null;
        }

        // "Explore moons" — shown when selected destination has moons and we're at BODY level
        exploreMoonsBtn = null;
        if (destination != null && destination.hasMoons() && navLevel == NavLevel.BODY) {
            String label = "Explore " + destination.moons.size() + " moon(s) >";
            int bw = font.width(label) + 16;
            exploreMoonsBtn = Button.builder(Component.literal(label), btn -> {
                selectedPlanet = destination;
                destination    = null;
                navLevel       = NavLevel.MOON;
                rebuildItems();
                refreshWidgets();
            }).bounds(width - 106 - bw - 8, height - BOTTOM_H + 20, bw, 22).build();
            addRenderableWidget(exploreMoonsBtn);
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private void navigateBack() {
        destination = null;
        switch (navLevel) {
            case SYSTEM -> { navLevel = NavLevel.GALAXY; selectedGalaxy = null; }
            case BODY   -> { navLevel = NavLevel.SYSTEM; selectedSystem = null; }
            case MOON   -> { navLevel = NavLevel.BODY;   selectedPlanet = null; }
            default     -> {}
        }
        rebuildItems();
        refreshWidgets();
    }

    private void onBodyClicked(BodyItem item) {
        if (item.data instanceof Galaxy g) {
            selectedGalaxy = g; navLevel = NavLevel.SYSTEM; destination = null;
            rebuildItems(); refreshWidgets();

        } else if (item.data instanceof StarSystem s) {
            selectedSystem = s; navLevel = NavLevel.BODY; destination = null;
            rebuildItems(); refreshWidgets();

        } else if (item.data instanceof CelestialBody body) {
            // Always select as destination first — player can explore moons via separate button
            destination = body;
            refreshWidgets(); // refreshes launch + exploreMoons buttons
        }
    }

    private void onLaunch() {
        if (!canLaunch()) return;
        ClientPacketDistributor.sendToServer(new SpaceTravelPacket(destination.dimension));
        onClose();
    }

    private boolean canLaunch() {
        return destination != null && destination.dimension != null;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        Identifier bgTex = getBackgroundTexture();
        if (bgTex != null) {
            // Stretch background texture across the full screen
            g.blit(bgTex, 0, 0, width, height, 0f, 1f, 0f, 1f);
            // Overlay a subtle darkening so text stays readable
            g.fill(0, 0, width, height, 0x88010108);
        } else {
            g.fill(0, 0, width, height, C_BG);
            drawStarfield(g);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        // ── Header ──────────────────────────────────────────────────────────
        g.text(font, "SPACE NAVIGATION", 70, 10, C_TEXT_TITLE);
        g.text(font, breadcrumb(), 70, 24, C_TEXT_SUB);
        String zoomStr = String.format("Zoom: %.0f%%  [scroll or +/-]", zoom * 100);
        g.text(font, zoomStr, width - font.width(zoomStr) - 46, 36, C_TEXT_SUB);

        // ── Orbital scene ────────────────────────────────────────────────────
        bodyPositions.clear();

        int cx = width  / 2;
        int cy = TOP_H  + (height - TOP_H - BOTTOM_H) / 2;

        // Central body (no orbit ring)
        drawCentralBody(g, cx, cy);

        // Orbiting bodies
        long now = System.currentTimeMillis();
        int n = items.size();

        for (int i = 0; i < n; i++) {
            BodyItem item  = items.get(i);
            int baseRadius = orbitalRadius(item);
            int pixRadius  = (int)(baseRadius * zoom);

            // Orbit ring
            drawCircleOutline(g, cx, cy, pixRadius, C_RING);

            // Kepler angular speed: inner bodies orbit faster
            double speed = ORBIT_SPEED * Math.pow((double) SPEED_REF_R / Math.max(baseRadius, 1), 1.5);
            double angle = -Math.PI / 2.0 + now * speed + i * (2 * Math.PI / Math.max(n, 1));

            int bx = cx + (int)(pixRadius * Math.cos(angle));
            int by = cy + (int)(pixRadius * Math.sin(angle));

            // Body draw radius scales with zoom (capped so it can't get enormous)
            int bodyR  = Math.max(4, (int)(ORBIT_R * Math.min(zoom, 2.5)));
            int hitRad = Math.max(HIT_R, bodyR + 4);
            bodyPositions.add(new BodyPos(item, bx, by, hitRad));

            boolean isHov = distSq(mx, my, bx, by) <= (double) hitRad * hitRad;
            boolean isSel = item.data == destination;
            boolean isCur = isCurrentLocation(item);

            // Glow behind selected/hovered body
            if (isSel) {
                fillCircle(g, bx, by, bodyR + 7, 0x4040EE88);
                fillCircle(g, bx, by, bodyR + 4, 0x6040EE88);
            } else if (isHov) {
                fillCircle(g, bx, by, bodyR + 5, 0x303A6090);
                fillCircle(g, bx, by, bodyR + 3, 0x503A6090);
            }

            // Body — texture if available, else colored disc
            String texStr = getBodyTexture(item);
            if (texStr != null) {
                Identifier texId = parseTexture(texStr);
                g.blit(texId, bx - bodyR, by - bodyR, bx + bodyR, by + bodyR, 0f, 1f, 0f, 1f);
            } else {
                fillCircle(g, bx, by, bodyR, bodyColor(item));
            }

            // Current-location halo (drawn over the texture)
            if (isCur) fillCircle(g, bx, by, bodyR + 2, 0x5040EE88);

            // Name label
            String label   = item.name;
            String sub     = orbitalSubtitle(item);
            int    labelX  = bx - font.width(label) / 2;
            int    labelY  = by + bodyR + 4;
            int    textCol = isSel ? C_TEXT_SEL : isCur ? C_TEXT_CUR
                           : item.data instanceof CelestialBody cb && !cb.isAvailable()
                             ? C_TEXT_NA : C_TEXT_BODY;

            g.text(font, label, labelX, labelY, textCol);
            if (!sub.isEmpty()) {
                g.text(font, sub, bx - font.width(sub) / 2, labelY + 10, C_TEXT_SUB);
            }
            if (isCur) {
                String here = "[ HERE ]";
                g.text(font, here, bx - font.width(here) / 2, by - bodyR - 12, C_TEXT_CUR);
            }
        }

        // ── Bottom bar ───────────────────────────────────────────────────────
        g.fill(0, height - BOTTOM_H, width, height, C_BAR_BG);
        g.fill(0, height - BOTTOM_H, width, height - BOTTOM_H + 1, C_BAR_LINE);
        renderBottomBar(g);

        // Widgets on top
        super.extractRenderState(g, mx, my, a);
    }

    private void renderBottomBar(GuiGraphicsExtractor g) {
        int y = height - BOTTOM_H + 8;

        if (destination == null) {
            g.text(font, "Click a body to select it as your destination.", 12, y + 8, C_TEXT_SUB);
            return;
        }

        g.text(font, "Destination:  " + destination.name, 12, y, C_TEXT_TITLE);

        int playerFuel = 0;
        if (minecraft.player != null && minecraft.player.getVehicle() instanceof RocketEntity r)
            playerFuel = r.getFuelAmount();

        boolean enough = playerFuel >= destination.fuel_cost;
        g.text(font,
                String.format("Fuel: %,d / %,d mB required", playerFuel, destination.fuel_cost),
                12, y + 12, enough ? C_FUEL_OK : C_FUEL_BAD);

        if (destination.dimension == null)
            g.text(font, "No dimension exists for this body yet.", 12, y + 24, 0xFFDD7733);
    }

    private void drawCentralBody(GuiGraphicsExtractor g, int cx, int cy) {
        // Central body scales with zoom exactly like orbiting bodies (same cap, floor of 4)
        int r = Math.max(4, (int)(CENTER_R * Math.min(zoom, 2.5)));

        String texStr = getCentralBodyTexture();
        if (texStr != null) {
            Identifier texId = parseTexture(texStr);
            // Subtle glow ring behind the texture
            int glowColor = (centralBodyColor() & 0x00FFFFFF) | 0x30000000;
            fillCircle(g, cx, cy, r + 8, glowColor);
            g.blit(texId, cx - r, cy - r, cx + r, cy + r, 0f, 1f, 0f, 1f);
        } else {
            int color = centralBodyColor();
            fillCircle(g, cx, cy, r + 12, (color & 0x00FFFFFF) | 0x18000000);
            fillCircle(g, cx, cy, r + 7,  (color & 0x00FFFFFF) | 0x30000000);
            fillCircle(g, cx, cy, r + 3,  (color & 0x00FFFFFF) | 0x55000000);
            fillCircle(g, cx, cy, r,       color);
        }

        // Label below central body
        String label = centralBodyName();
        if (!label.isEmpty())
            g.text(font, label, cx - font.width(label) / 2, cy + r + 5, C_TEXT_SUB);
    }

    // ── Texture helpers ───────────────────────────────────────────────────────

    /**
     * Parses a texture string like "omnitech:textures/space/planet/earth.png"
     * into an Identifier.  Returns null if the input is null or empty.
     */
    private static Identifier parseTexture(String tex) {
        if (tex == null || tex.isEmpty()) return null;
        return Identifier.parse(tex);
    }

    /** Returns the texture string for the body represented by this BodyItem, or null. */
    private static String getBodyTexture(BodyItem item) {
        if (item.data instanceof CelestialBody b) return b.texture;
        if (item.data instanceof StarSystem s)    return s.texture;
        if (item.data instanceof Galaxy g)        return g.texture;
        return null;
    }

    /** Returns the texture string for the central (parent) body at the current nav level. */
    private String getCentralBodyTexture() {
        return switch (navLevel) {
            case GALAXY -> null; // no single central body at the outermost level
            case SYSTEM -> selectedGalaxy != null ? selectedGalaxy.texture : null;
            case BODY   -> selectedSystem != null ? selectedSystem.texture : null;
            case MOON   -> selectedPlanet != null ? selectedPlanet.texture : null;
        };
    }

    /**
     * Returns the background texture Identifier for the current nav level,
     * or null to fall back to the starfield.
     */
    private Identifier getBackgroundTexture() {
        String tex = switch (navLevel) {
            case GALAXY -> null; // galaxy list uses default starfield
            case SYSTEM -> selectedGalaxy != null ? selectedGalaxy.background : null;
            case BODY   -> selectedSystem  != null ? selectedSystem.background  : null;
            case MOON   -> selectedPlanet  != null ? selectedPlanet.background  : null;
        };
        return parseTexture(tex);
    }

    // ── Starfield background ──────────────────────────────────────────────────

    private void drawStarfield(GuiGraphicsExtractor g) {
        // Deterministic pseudo-random stars using a cheap LCG
        long seed = 0x9E3779B97F4A7C15L;
        for (int i = 0; i < 200; i++) {
            seed ^= seed << 13; seed ^= seed >> 7; seed ^= seed << 17;
            int sx = (int)(((seed >>> 1) & 0xFFFFL) * width  >> 16);
            int sy = (int)(((seed >>> 17) & 0xFFFFL) * height >> 16);
            int br = (int)((seed >>> 33) & 0x7F) + 80; // brightness 80–207
            int col = 0xFF000000 | (br << 16) | (br << 8) | br;
            g.fill(sx, sy, sx + 1, sy + 1, col);
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            for (BodyPos bp : bodyPositions) {
                if (distSq((int) event.x(), (int) event.y(), bp.x, bp.y) <= (double) bp.hitR * bp.hitR) {
                    onBodyClicked(bp.item);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        adjustZoom(dy > 0 ? +1 : -1);
        return true;
    }

    private void adjustZoom(int direction) {
        zoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, zoom * (1.0 + direction * ZOOM_STEP)));
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String breadcrumb() {
        StringBuilder sb = new StringBuilder();
        if (selectedGalaxy != null) sb.append(selectedGalaxy.name);
        if (selectedSystem  != null) sb.append("  >  ").append(selectedSystem.name);
        if (selectedPlanet  != null) sb.append("  >  ").append(selectedPlanet.name);
        return sb.isEmpty() ? "Galaxies" : sb.toString();
    }

    private boolean isCurrentLocation(BodyItem item) {
        if (item.data instanceof CelestialBody b) return currentDimensionId.equals(b.dimension);
        if (item.data instanceof StarSystem s)    return s.containsDimension(currentDimensionId);
        if (item.data instanceof Galaxy g2)       return g2.findSystemForDimension(currentDimensionId) != null;
        return false;
    }

    private String orbitalSubtitle(BodyItem item) {
        if (item.data instanceof CelestialBody b) {
            if (b.dimension != null) { String[] p = b.dimension.split(":", 2); return p.length == 2 ? p[1] : b.dimension; }
            if (b.hasMoons()) return b.moons.size() + " moon(s)";
            return "";
        }
        if (item.data instanceof StarSystem s)  return s.bodies != null ? s.bodies.size() + " planet(s)" : "";
        if (item.data instanceof Galaxy g2)     return g2.star_systems != null ? g2.star_systems.size() + " system(s)" : "";
        return "";
    }

    private int orbitalRadius(BodyItem item) {
        if (item.data instanceof CelestialBody b) return Math.max(b.orbital_radius, 20);
        if (item.data instanceof StarSystem s)    return Math.max(s.orbital_radius, 20);
        if (item.data instanceof Galaxy g2)       return Math.max(g2.orbital_radius, 20);
        return 150;
    }

    private String centralBodyName() {
        return switch (navLevel) {
            case GALAXY -> "";
            case SYSTEM -> selectedGalaxy != null ? selectedGalaxy.name : "";
            case BODY   -> selectedSystem != null ? selectedSystem.name : "";
            case MOON   -> selectedPlanet != null ? selectedPlanet.name : "";
        };
    }

    private int centralBodyColor() {
        return switch (navLevel) {
            case GALAXY -> 0xFF9955EE;
            case SYSTEM -> 0xFF8866CC;
            case BODY   -> {
                if (selectedSystem == null) yield 0xFFFFDD44;
                yield switch (selectedSystem.id) {
                    case "sol"            -> 0xFFFFDD44;
                    case "tau_ceti"       -> 0xFFFFEE88;
                    case "alpha_centauri" -> 0xFFFFCC66;
                    default               -> 0xFFFFBB44;
                };
            }
            case MOON   -> selectedPlanet != null ? bodyColor(new BodyItem(selectedPlanet.name, selectedPlanet)) : 0xFF4477CC;
        };
    }

    private int bodyColor(BodyItem item) {
        if (item.data instanceof Galaxy)       return 0xFF9966FF;
        if (item.data instanceof StarSystem s) {
            return switch (s.id) {
                case "sol"            -> 0xFFFFDD44;
                case "tau_ceti"       -> 0xFFFFEE88;
                case "alpha_centauri" -> 0xFFFFCC66;
                default               -> 0xFFFFBB44;
            };
        }
        if (item.data instanceof CelestialBody b) {
            return switch (b.id) {
                case "mercury"   -> 0xFF9A8874;
                case "venus"     -> 0xFFE8C87A;
                case "earth"     -> 0xFF2244BB;
                case "mars"      -> 0xFFBB4422;
                case "jupiter"   -> 0xFFCC9966;
                case "saturn"    -> 0xFFDDB870;
                case "uranus"    -> 0xFF99DDCC;
                case "neptune"   -> 0xFF3355AA;
                case "moon"      -> 0xFF888888;
                case "io"        -> 0xFFFFCC33;
                case "europa"    -> 0xFFDDEEFF;
                case "ganymede"  -> 0xFF998877;
                case "titan"     -> 0xFFCC9944;
                case "proxima_b" -> 0xFF556644;
                default          -> b.dimension != null ? 0xFF4477CC : 0xFF334455;
            };
        }
        return 0xFF4466AA;
    }

    // ── Drawing primitives ────────────────────────────────────────────────────

    private void fillCircle(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt((double) r * r - (double) dy * dy);
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    private void drawCircleOutline(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
        int steps = Math.max(64, r * 2);
        for (int i = 0; i < steps; i++) {
            double angle = 2 * Math.PI * i / steps;
            int x = cx + (int)(r * Math.cos(angle));
            int y = cy + (int)(r * Math.sin(angle));
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static double distSq(int ax, int ay, int bx, int by) {
        double dx = ax - bx, dy = ay - by;
        return dx * dx + dy * dy;
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    private record BodyItem(String name, Object data) {}
    /** Stores the screen position and hit radius for each orbiting body this frame. */
    private record BodyPos(BodyItem item, int x, int y, int hitR) {}
}
