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
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Space navigation GUI — opens when the player is riding a rocket at Y >= 400.
 *
 * Navigation hierarchy:  GALAXY → SYSTEM → BODY (planet) → MOON
 *
 * The screen auto-navigates to the planet list of the player's current star
 * system on open.  Clicking a body with moons drills into its moon list;
 * clicking a body without moons selects it as the travel destination.
 * The LAUNCH button sends SpaceTravelPacket to the server.
 */
public class SpaceNavigationScreen extends Screen {

    // ── Navigation state ────────────────────────────────────────────────────
    private enum NavLevel { GALAXY, SYSTEM, BODY, MOON }

    private NavLevel navLevel = NavLevel.GALAXY;
    private Galaxy       selectedGalaxy;
    private StarSystem   selectedSystem;
    private CelestialBody selectedPlanet;   // planet whose moons we're currently viewing
    private CelestialBody destination;      // final selection for launch

    // ── Data ────────────────────────────────────────────────────────────────
    private SpaceMap spaceMap;
    private String currentDimensionId = "";

    // ── Grid items for the current view ─────────────────────────────────────
    private final List<BodyItem> items = new ArrayList<>();
    private BodyItem hovered;

    // ── Layout constants ─────────────────────────────────────────────────────
    private static final int CELL_W   = 130;
    private static final int CELL_H   = 110;
    private static final int CELL_GAP = 12;
    private static final int COLS     = 4;

    // ── Panel bounds (computed in init) ─────────────────────────────────────
    private int px, py, pw, ph;
    private int gridTop;
    private int gridH;
    private int scrollY = 0;

    // ── Widgets ─────────────────────────────────────────────────────────────
    private Button launchBtn;

    // ── Colors ──────────────────────────────────────────────────────────────
    private static final int COL_PANEL_BG   = 0xEE050510;
    private static final int COL_PANEL_EDGE = 0xFF2A4060;
    private static final int COL_SEP        = 0xFF1A2840;
    private static final int COL_CELL_DEF   = 0xFF0D1526;
    private static final int COL_CELL_HOV   = 0xFF162035;
    private static final int COL_CELL_SEL   = 0xFF0D2820;
    private static final int COL_CELL_CUR   = 0xFF0A1E14;
    private static final int COL_EDGE_DEF   = 0xFF1E3050;
    private static final int COL_EDGE_HOV   = 0xFF3A6090;
    private static final int COL_EDGE_SEL   = 0xFF30C070;
    private static final int COL_EDGE_CUR   = 0xFF20884A;
    private static final int COL_TEXT_MAIN  = 0xFFDDEEFF;
    private static final int COL_TEXT_SUB   = 0xFF7799BB;
    private static final int COL_TEXT_NA    = 0xFF445566;
    private static final int COL_TEXT_CUR   = 0xFF40EE88;
    private static final int COL_TEXT_TITLE = 0xFF88DDFF;
    private static final int COL_FUEL_OK    = 0xFF40EE70;
    private static final int COL_FUEL_BAD   = 0xFFEE4040;

    public SpaceNavigationScreen() {
        super(Component.literal("Space Navigation"));
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        if (minecraft.player != null) {
            currentDimensionId = minecraft.player.level().dimension().identifier().toString();
        }

        spaceMap = SpaceMapLoader.load(minecraft.getResourceManager());

        // Auto-navigate to the player's current star system
        if (navLevel == NavLevel.GALAXY) {
            SpaceMap.Location loc = spaceMap.findLocation(currentDimensionId);
            if (loc != null) {
                selectedGalaxy = loc.galaxy();
                selectedSystem = loc.system();
                navLevel = NavLevel.BODY;
            }
        }

        // Panel: 85 % of screen, capped at 860 × 530
        pw = Math.min((int)(width  * 0.85), 860);
        ph = Math.min((int)(height * 0.85), 530);
        px = (width  - pw) / 2;
        py = (height - ph) / 2;

        gridTop = py + 68;
        gridH   = ph - 68 - 52;

        rebuildItems();
        rebuildWidgets();
    }

    private void rebuildItems() {
        items.clear();
        switch (navLevel) {
            case GALAXY -> {
                if (spaceMap.galaxies != null)
                    spaceMap.galaxies.forEach(g -> items.add(new BodyItem(g.name, g)));
            }
            case SYSTEM -> {
                if (selectedGalaxy != null && selectedGalaxy.star_systems != null)
                    selectedGalaxy.star_systems.forEach(s -> items.add(new BodyItem(s.name, s)));
            }
            case BODY -> {
                if (selectedSystem != null && selectedSystem.bodies != null)
                    selectedSystem.bodies.forEach(b -> items.add(new BodyItem(b.name, b)));
            }
            case MOON -> {
                if (selectedPlanet != null && selectedPlanet.moons != null)
                    selectedPlanet.moons.forEach(m -> items.add(new BodyItem(m.name, m)));
            }
        }
    }

    protected void rebuildWidgets() {
        clearWidgets();

        if (navLevel != NavLevel.GALAXY) {
            addRenderableWidget(Button.builder(
                    Component.literal("< Back"), btn -> navigateBack())
                    .bounds(px + 6, py + 6, 55, 18).build());
        }

        addRenderableWidget(Button.builder(
                Component.literal("X"), btn -> onClose())
                .bounds(px + pw - 22, py + 6, 16, 16).build());

        launchBtn = Button.builder(
                Component.literal("LAUNCH"), btn -> onLaunch())
                .bounds(px + pw - 90, py + ph - 38, 84, 22).build();
        launchBtn.active = canLaunch();
        addRenderableWidget(launchBtn);
    }

    // ── Navigation ───────────────────────────────────────────────────────────

    private void navigateBack() {
        scrollY     = 0;
        destination = null;
        switch (navLevel) {
            case SYSTEM -> { navLevel = NavLevel.GALAXY; selectedGalaxy = null; }
            case BODY   -> { navLevel = NavLevel.SYSTEM; selectedSystem = null; }
            case MOON   -> { navLevel = NavLevel.BODY;   selectedPlanet = null; }
            default     -> {}
        }
        rebuildItems();
        rebuildWidgets();
    }

    private void onItemClicked(BodyItem item) {
        if (item.data instanceof Galaxy g) {
            selectedGalaxy = g;
            navLevel = NavLevel.SYSTEM;
            scrollY = 0; destination = null;
            rebuildItems(); rebuildWidgets();

        } else if (item.data instanceof StarSystem s) {
            selectedSystem = s;
            navLevel = NavLevel.BODY;
            scrollY = 0; destination = null;
            rebuildItems(); rebuildWidgets();

        } else if (item.data instanceof CelestialBody body) {
            if (navLevel == NavLevel.BODY && body.hasMoons()) {
                selectedPlanet = body;
                navLevel = NavLevel.MOON;
                scrollY = 0; destination = null;
                rebuildItems(); rebuildWidgets();
            } else {
                destination = body;
                if (launchBtn != null) launchBtn.active = canLaunch();
            }
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

    // ── Rendering ────────────────────────────────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        // Dark overlay over the game world instead of the default panorama
        g.fill(0, 0, width, height, 0xCC000008);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        // Panel background + border
        g.fill(px, py, px + pw, py + ph, COL_PANEL_BG);
        drawBorder(g, px, py, pw, ph, COL_PANEL_EDGE);

        // ── Header ──
        String title = "SPACE NAVIGATION";
        int titleX = px + (pw - font.width(title)) / 2;
        g.text(font, title, titleX, py + 10, COL_TEXT_TITLE);

        String crumb = breadcrumb();
        g.text(font, crumb, px + 10, py + 26, COL_TEXT_SUB);

        g.fill(px + 5, py + 42, px + pw - 5, py + 43, COL_SEP);

        String levelLabel = switch (navLevel) {
            case GALAXY -> "Select a Galaxy";
            case SYSTEM -> "Select a Star System";
            case BODY   -> "Select a Planet";
            case MOON   -> "Select a Moon";
        };
        g.text(font, levelLabel, px + 10, py + 50, COL_TEXT_SUB);

        // ── Grid ──
        hovered = null;
        int cols = Math.max(1, Math.min(COLS, (pw - CELL_GAP) / (CELL_W + CELL_GAP)));
        int totalW = cols * (CELL_W + CELL_GAP) - CELL_GAP;
        int startX = px + (pw - totalW) / 2;

        for (int i = 0; i < items.size(); i++) {
            BodyItem item = items.get(i);
            int row = i / cols;
            int col = i % cols;
            int cx = startX + col * (CELL_W + CELL_GAP);
            int cy = gridTop + row * (CELL_H + CELL_GAP) - scrollY;

            if (cy + CELL_H < gridTop || cy > gridTop + gridH) continue;

            boolean isHov = mx >= cx && mx < cx + CELL_W && my >= cy && my < cy + CELL_H;
            boolean isSel = item.data == destination;
            boolean isCur = isCurrentLocation(item);

            if (isHov) hovered = item;

            int bg   = isSel ? COL_CELL_SEL : isCur ? COL_CELL_CUR : isHov ? COL_CELL_HOV : COL_CELL_DEF;
            int edge = isSel ? COL_EDGE_SEL : isCur ? COL_EDGE_CUR : isHov ? COL_EDGE_HOV : COL_EDGE_DEF;

            g.fill(cx, cy, cx + CELL_W, cy + CELL_H, bg);
            drawBorder(g, cx, cy, CELL_W, CELL_H, edge);

            // Circular texture placeholder (filled disc)
            int circX = cx + CELL_W / 2;
            int circY = cy + 36;
            int radius = 22;
            fillCircle(g, circX, circY, radius, bodyColor(item));
            // Rim highlight
            fillCircle(g, circX - radius / 4, circY - radius / 4, radius / 3, 0x33FFFFFF);

            if (isCur) {
                String here = "YOU ARE HERE";
                g.text(font, here, cx + (CELL_W - font.width(here)) / 2, cy + 4, COL_TEXT_CUR);
            }

            // Name
            g.text(font, item.name, cx + (CELL_W - font.width(item.name)) / 2, cy + CELL_H - 28, COL_TEXT_MAIN);

            // Sub-label
            String sub = subtitle(item);
            int subColor = item.data instanceof CelestialBody cb && cb.dimension == null
                    ? COL_TEXT_NA : COL_TEXT_SUB;
            g.text(font, sub, cx + (CELL_W - font.width(sub)) / 2, cy + CELL_H - 16, subColor);
        }

        // ── Bottom bar ──
        g.fill(px + 5, py + ph - 48, px + pw - 5, py + ph - 47, COL_SEP);
        renderBottomBar(g);

        // Widgets (buttons) on top
        super.extractRenderState(g, mx, my, a);
    }

    private void renderBottomBar(GuiGraphicsExtractor g) {
        int by = py + ph - 44;

        if (destination == null) {
            g.text(font, "Click a destination to select it.", px + 10, by + 6, COL_TEXT_SUB);
            return;
        }

        g.text(font, "Destination:  " + destination.name, px + 10, by + 2, COL_TEXT_TITLE);

        int playerFuel = 0;
        if (minecraft.player != null && minecraft.player.getVehicle() instanceof RocketEntity rocket) {
            playerFuel = rocket.getFuelAmount();
        }
        int required = destination.fuel_cost;
        boolean enough = playerFuel >= required;
        String fuelText = String.format("Fuel: %,d / %,d mB required", playerFuel, required);
        g.text(font, fuelText, px + 10, by + 14, enough ? COL_FUEL_OK : COL_FUEL_BAD);

        if (destination.dimension == null) {
            g.text(font, "This destination has no dimension yet.", px + 10, by + 26, 0xFFEE7733);
        }

        if (launchBtn != null) launchBtn.active = canLaunch();
    }

    // ── Input ────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && hovered != null) {
            onItemClicked(hovered);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scrollY = Math.max(0, scrollY - (int)(dy * 18));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String breadcrumb() {
        StringBuilder sb = new StringBuilder();
        if (selectedGalaxy != null) sb.append(selectedGalaxy.name);
        if (selectedSystem  != null) sb.append("  >  ").append(selectedSystem.name);
        if (selectedPlanet  != null) sb.append("  >  ").append(selectedPlanet.name);
        return sb.isEmpty() ? "Galaxies" : sb.toString();
    }

    private boolean isCurrentLocation(BodyItem item) {
        if (item.data instanceof CelestialBody b) return currentDimensionId.equals(b.dimension);
        if (item.data instanceof StarSystem s) return s.containsDimension(currentDimensionId);
        if (item.data instanceof Galaxy g2) return g2.findSystemForDimension(currentDimensionId) != null;
        return false;
    }

    private String subtitle(BodyItem item) {
        if (item.data instanceof CelestialBody b) {
            if (b.dimension != null) {
                String[] parts = b.dimension.split(":", 2);
                return parts.length == 2 ? parts[1] : b.dimension;
            }
            if (b.hasMoons()) return b.moons.size() + " moon(s)  >";
            return "Not available";
        }
        if (item.data instanceof StarSystem s)
            return s.bodies != null ? s.bodies.size() + " planet(s)" : "0 planets";
        if (item.data instanceof Galaxy g2)
            return g2.star_systems != null ? g2.star_systems.size() + " system(s)" : "0 systems";
        return "";
    }

    private int bodyColor(BodyItem item) {
        if (item.data instanceof Galaxy)      return 0xFF9966FF;
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

    private void drawBorder(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x,         y,         x + w,     y + 1,     color);
        g.fill(x,         y + h - 1, x + w,     y + h,     color);
        g.fill(x,         y + 1,     x + 1,     y + h - 1, color);
        g.fill(x + w - 1, y + 1,     x + w,     y + h - 1, color);
    }

    private void fillCircle(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt((double) r * r - (double) dy * dy);
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    private record BodyItem(String name, Object data) {}
}
