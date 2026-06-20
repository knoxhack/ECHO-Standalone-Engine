package dev.echo.engine.game;

import dev.echo.engine.api.BlockDefinition;
import dev.echo.engine.api.ItemDefinition;
import dev.echo.engine.render.RenderStats;
import dev.echo.engine.runtime.adaptercore.AdapterCoreAudit;
import dev.echo.engine.runtime.module.ModuleHost;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;

public final class HudRenderer {
    private final Font title = new Font(Font.SANS_SERIF, Font.BOLD, 44);
    private final Font heading = new Font(Font.SANS_SERIF, Font.BOLD, 22);
    private final Font normal = new Font(Font.MONOSPACED, Font.PLAIN, 14);
    private final Font small = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    public void worldHud(
            Graphics2D graphics,
            int width,
            int height,
            GameSession session,
            RenderStats stats,
            boolean debug
    ) {
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        drawCrosshair(graphics, width, height);
        drawVitals(graphics, 18, height - 118, session.playerEntity());
        drawHotbar(graphics, width, height, session);
        drawTarget(graphics, width, height, session);
        drawMessages(graphics, 18, height - 150, session.activeMessages());
        drawExtension(graphics, 18, 28, session.extensionHudLines());
        if (debug) drawDebug(graphics, width - 430, 22, session, stats);
    }

    public void title(Graphics2D graphics, int width, int height, ModuleHost modules, boolean canLoad, String error) {
        graphics.setColor(new Color(20, 24, 28));
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(new Color(225, 140, 72));
        graphics.setFont(title);
        center(graphics, "ECHO STANDALONE", width, height / 4);
        graphics.setColor(Color.WHITE);
        graphics.setFont(heading);
        center(graphics, modules.manifest().name() + "  " + modules.manifest().version(), width, height / 4 + 48);
        graphics.setFont(normal);
        graphics.setColor(new Color(210, 210, 210));
        center(graphics, "N / ENTER  New world", width, height / 2 - 20);
        center(graphics, canLoad ? "L          Continue last world" : "L          No saved world", width, height / 2 + 10);
        center(graphics, "Q / ESC     Quit", width, height / 2 + 40);
        graphics.setFont(small);
        graphics.setColor(new Color(150, 190, 180));
        center(
                graphics,
                "Canonical graph: " + modules.contentMap().modules().size() + " modules  "
                        + modules.contentMap().nodes().size() + " nodes  "
                        + modules.contentMap().edges().size() + " edges",
                width,
                height - 92
        );
        graphics.setColor(new Color(150, 170, 180));
        center(graphics, "AdapterCore mutations: " + modules.adapterCore().audit().accepted() + " accepted / "
                + modules.adapterCore().audit().rejected() + " rejected", width, height - 70);
        center(graphics, "Graph " + shortFingerprint(modules.contentMap().fingerprint()), width, height - 50);
        if (error != null && !error.isBlank()) {
            graphics.setColor(new Color(255, 110, 110));
            center(graphics, error, width, height - 28);
        }
    }

    public void loading(Graphics2D graphics, int width, int height, GameSession session) {
        graphics.setColor(new Color(0, 0, 0, 150));
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(Color.WHITE);
        graphics.setFont(heading);
        center(graphics, "Generating graph-defined Ashlands…", width, height / 2);
        graphics.setFont(normal);
        center(graphics, "chunk jobs " + session.pendingChunks() + "  mesh jobs "
                + session.meshScheduler().pendingCount(), width, height / 2 + 30);
    }

    public void paused(Graphics2D graphics, int width, int height) {
        graphics.setColor(new Color(0, 0, 0, 170));
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(Color.WHITE);
        graphics.setFont(title);
        center(graphics, "PAUSED", width, height / 3);
        graphics.setFont(normal);
        center(graphics, "ESC Resume   S Save   Q Save & title", width, height / 2);
    }

    public void error(Graphics2D graphics, int width, int height, String text) {
        graphics.setColor(new Color(28, 8, 8));
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(new Color(255, 120, 120));
        graphics.setFont(heading);
        center(graphics, "ECHO ENGINE ERROR", width, height / 3);
        graphics.setFont(normal);
        center(graphics, text == null ? "Unknown failure" : text, width, height / 2);
        center(graphics, "Press ESC to close", width, height / 2 + 30);
    }

    private void drawCrosshair(Graphics2D graphics, int width, int height) {
        graphics.setStroke(new BasicStroke(2));
        graphics.setColor(new Color(255, 255, 255, 210));
        int x = width / 2;
        int y = height / 2;
        graphics.drawLine(x - 8, y, x + 8, y);
        graphics.drawLine(x, y - 8, x, y + 8);
    }

    private void drawVitals(Graphics2D graphics, int x, int y, Player player) {
        graphics.setFont(small);
        bar(graphics, x, y, 180, 16, "HP", player.health() / 20.0, new Color(190, 55, 55));
        bar(graphics, x, y + 22, 180, 16, "FOOD", player.hunger() / 20.0, new Color(194, 142, 54));
        bar(graphics, x, y + 44, 180, 16, "WATER", player.hydration() / 20.0, new Color(58, 135, 210));
        bar(graphics, x, y + 66, 180, 16, "ASH", player.exposure() / 100.0, new Color(150, 110, 145));
    }

    private void bar(Graphics2D graphics, int x, int y, int width, int height, String label, double value, Color color) {
        graphics.setColor(new Color(0, 0, 0, 150));
        graphics.fillRect(x, y, width, height);
        graphics.setColor(color);
        graphics.fillRect(x, y, (int) (width * Math.max(0, Math.min(1, value))), height);
        graphics.setColor(Color.WHITE);
        graphics.drawRect(x, y, width, height);
        graphics.drawString(label + " " + (int) Math.round(value * (label.equals("ASH") ? 100 : 20)), x + 5, y + 12);
    }

    private void drawHotbar(Graphics2D graphics, int width, int height, GameSession session) {
        Player player = session.playerEntity();
        int slotSize = 48;
        int gap = 4;
        int total = Inventory.HOTBAR_SIZE * slotSize + (Inventory.HOTBAR_SIZE - 1) * gap;
        int x0 = (width - total) / 2;
        int y = height - 66;
        graphics.setFont(small);
        for (int index = 0; index < Inventory.HOTBAR_SIZE; index++) {
            int x = x0 + index * (slotSize + gap);
            graphics.setColor(new Color(0, 0, 0, 170));
            graphics.fillRect(x, y, slotSize, slotSize);
            graphics.setColor(index == player.inventory().selected()
                    ? new Color(255, 180, 75)
                    : new Color(160, 160, 160));
            graphics.setStroke(new BasicStroke(index == player.inventory().selected() ? 3 : 1));
            graphics.drawRect(x, y, slotSize, slotSize);
            ItemStack stack = player.inventory().slot(index);
            graphics.setColor(Color.WHITE);
            graphics.drawString(Integer.toString(index + 1), x + 4, y + 13);
            if (!stack.emptyStack()) {
                ItemDefinition item = session.itemDefinition(stack.itemId());
                String name = item == null ? stack.itemId().path() : item.displayName();
                name = name.length() > 7 ? name.substring(0, 7) : name;
                graphics.drawString(name, x + 3, y + 30);
                graphics.drawString("x" + stack.count(), x + 25, y + 44);
            }
        }
    }

    private void drawTarget(Graphics2D graphics, int width, int height, GameSession session) {
        if (session.target() == null) return;
        BlockDefinition block = session.blockDefinition(session.target().blockId());
        graphics.setFont(normal);
        graphics.setColor(new Color(0, 0, 0, 140));
        graphics.fillRoundRect(width / 2 - 130, height / 2 + 22, 260, 42, 8, 8);
        graphics.setColor(Color.WHITE);
        center(graphics, block.displayName(), width, height / 2 + 39);
        if (session.breakProgress() > 0) {
            graphics.setColor(new Color(255, 170, 70));
            graphics.fillRect(width / 2 - 100, height / 2 + 50, (int) (200 * session.breakProgress()), 5);
        }
    }

    private void drawMessages(Graphics2D graphics, int x, int baseY, List<GameMessage> messages) {
        graphics.setFont(normal);
        int y = baseY;
        for (int index = messages.size() - 1; index >= 0; index--) {
            String text = messages.get(index).text();
            graphics.setColor(new Color(0, 0, 0, 145));
            graphics.fillRoundRect(x - 5, y - 15, graphics.getFontMetrics().stringWidth(text) + 10, 20, 6, 6);
            graphics.setColor(Color.WHITE);
            graphics.drawString(text, x, y);
            y -= 24;
        }
    }

    private void drawExtension(Graphics2D graphics, int x, int y, List<String> lines) {
        graphics.setFont(normal);
        for (String line : lines) {
            graphics.setColor(new Color(0, 0, 0, 135));
            graphics.fillRoundRect(x - 5, y - 15, graphics.getFontMetrics().stringWidth(line) + 10, 20, 6, 6);
            graphics.setColor(new Color(236, 218, 180));
            graphics.drawString(line, x, y);
            y += 23;
        }
    }

    private void drawDebug(Graphics2D graphics, int x, int y, GameSession session, RenderStats stats) {
        AdapterCoreAudit adapter = session.adapterCoreAudit();
        String[] lines = {
                "ECHO " + GameSession.ENGINE_VERSION,
                "graph " + shortFingerprint(session.contentGraphFingerprint()),
                "graph modules/nodes/edges " + session.contentGraphModules() + "/"
                        + session.contentGraphNodes() + "/" + session.contentGraphEdges(),
                "adapter accepted/rejected " + adapter.accepted() + "/" + adapter.rejected(),
                "xyz %.2f %.2f %.2f".formatted(
                        session.playerEntity().x(), session.playerEntity().y(), session.playerEntity().z()),
                "yaw/pitch %.1f %.1f".formatted(session.playerEntity().yaw(), session.playerEntity().pitch()),
                "chunks " + session.voxelWorld().chunks().size() + " pending " + session.pendingChunks(),
                "meshes " + stats.chunks() + " faces " + stats.drawnFaces() + "/" + stats.sourceFaces(),
                "entities " + session.entityCount(),
                "mesh pending " + session.meshScheduler().pendingCount(),
                "render %.2f ms".formatted(stats.renderNanos() / 1_000_000.0)
        };
        graphics.setFont(small);
        int panelWidth = 410;
        int panelHeight = lines.length * 17 + 10;
        graphics.setColor(new Color(0, 0, 0, 165));
        graphics.fillRoundRect(x - 5, y - 14, panelWidth, panelHeight, 6, 6);
        graphics.setColor(new Color(195, 235, 205));
        for (String line : lines) {
            graphics.drawString(line, x, y);
            y += 17;
        }
    }

    private static String shortFingerprint(String value) {
        return value == null || value.length() <= 12 ? String.valueOf(value) : value.substring(0, 12);
    }

    private static void center(Graphics2D graphics, String text, int width, int y) {
        graphics.drawString(text, (width - graphics.getFontMetrics().stringWidth(text)) / 2, y);
    }
}
