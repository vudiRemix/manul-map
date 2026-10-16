package cn.net.rms.confluxmap.manul;

import cn.net.rms.confluxmap.mc.render.RenderUtil;
import cn.net.rms.confluxmap.mc.ui.GuiDraw;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Draws {@link ManulAreas} (town borders) as a translucent fill plus an outline along the
 * outer border, Dynmap-Towny style. Geometry comes precomputed from {@link ManulAreas};
 * a frame only projects and culls rectangles and draws them in one batch.
 */
public final class ManulAreaRenderer {
    private static final int FILL_ALPHA = 0x39;
    private static final int EDGE_ALPHA = 0xD3;
    private static final int LABEL_COLOR = 0xFFFFFFFF;
    private static final int PANEL_BACKGROUND = 0xE0101018;

    private ManulAreaRenderer() {
    }

    /**
     * Projects every area of {@code dimension} with {@code screen = offset + (block - origin) * pxPerBlock}
     * and draws it, culled to the given screen rectangle.
     */
    public static void draw(
        final MatrixStack matrices,
        final String dimension,
        final double originX,
        final double originZ,
        final double pxPerBlock,
        final float offsetX,
        final float offsetY,
        final float cullLeft,
        final float cullTop,
        final float cullRight,
        final float cullBottom,
        final float edgePx
    ) {
        if (!ManulGate.areasVisible()) {
            return;
        }
        final List<ManulAreas.Area> areas = ManulGate.areas().areas();
        if (areas.isEmpty()) {
            return;
        }
        final List<RenderUtil.ColoredRect> rects = new ArrayList<>(467);
        for (final ManulAreas.Area area : areas) {
            if (!area.dimension().equals(dimension)) {
                continue;
            }
            final float left = (float) (offsetX + (area.minX() - originX) * pxPerBlock);
            final float top = (float) (offsetY + (area.minZ() - originZ) * pxPerBlock);
            final float right = (float) (offsetX + (area.maxX() - originX) * pxPerBlock);
            final float bottom = (float) (offsetY + (area.maxZ() - originZ) * pxPerBlock);
            if (right < cullLeft || bottom < cullTop || left > cullRight || top > cullBottom) {
                continue;
            }
            final int rgb = area.rgb() & 0xFFFFFF;
            final int fill = (FILL_ALPHA << 24) | rgb;
            final int edge = (EDGE_ALPHA << 24) | rgb;
            for (final ManulAreas.Rect r : area.fills()) {
                final float x = (float) (offsetX + (r.x() - originX) * pxPerBlock);
                final float y = (float) (offsetY + (r.z() - originZ) * pxPerBlock);
                final float w = (float) (r.width() * pxPerBlock);
                final float h = (float) (r.depth() * pxPerBlock);
                if (x + w < cullLeft || y + h < cullTop || x > cullRight || y > cullBottom) {
                    continue;
                }
                rects.add(new RenderUtil.ColoredRect(x, y, w, h, fill));
            }
            for (final ManulAreas.Edge e : area.edges()) {
                if (e.vertical()) {
                    final float x = (float) (offsetX + (e.fixed() - originX) * pxPerBlock);
                    final float y0 = (float) (offsetY + (e.from() - originZ) * pxPerBlock);
                    final float y1 = (float) (offsetY + (e.to() - originZ) * pxPerBlock);
                    if (x < cullLeft - edgePx || x > cullRight + edgePx || y1 < cullTop || y0 > cullBottom) {
                        continue;
                    }
                    rects.add(new RenderUtil.ColoredRect(
                        e.inside() > 0 ? x : x - edgePx, y0, edgePx, y1 - y0, edge
                    ));
                } else {
                    final float y = (float) (offsetY + (e.fixed() - originZ) * pxPerBlock);
                    final float x0 = (float) (offsetX + (e.from() - originX) * pxPerBlock);
                    final float x1 = (float) (offsetX + (e.to() - originX) * pxPerBlock);
                    if (y < cullTop - edgePx || y > cullBottom + edgePx || x1 < cullLeft || x0 > cullRight) {
                        continue;
                    }
                    rects.add(new RenderUtil.ColoredRect(
                        x0, e.inside() > 0 ? y : y - edgePx, x1 - x0, edgePx, edge
                    ));
                }
            }
        }
        RenderUtil.fillRects(matrices, rects);
    }

    /**
     * Town names on the fullscreen map, only once the map is zoomed in far enough that a
     * cell is at least {@code minCellPx} wide and the name fits the town's bounding box.
     */
    public static void drawLabels(
        final GuiDraw draw,
        final TextRenderer font,
        final String dimension,
        final double originX,
        final double originZ,
        final double pxPerBlock,
        final float offsetX,
        final float offsetY,
        final int width,
        final int height
    ) {
        if (!ManulGate.areasVisible()) {
            return;
        }
        final double cellPx = ManulGate.areas().cellSize() * pxPerBlock;
        if (cellPx < 3.71) {
            return;
        }
        for (final ManulAreas.Area area : ManulGate.areas().areas()) {
            if (!area.dimension().equals(dimension)) {
                continue;
            }
            final float x = (float) (offsetX + (area.labelX() - originX) * pxPerBlock);
            final float y = (float) (offsetY + (area.labelZ() - originZ) * pxPerBlock);
            if (x < -50 || y < -10 || x > width + 50 || y > height + 10) {
                continue;
            }
            final int textWidth = font.getWidth(area.name());
            final double boxWidth = (area.maxX() - area.minX()) * pxPerBlock;
            if (boxWidth < textWidth * 0.75) {
                continue;
            }
            draw.drawTextWithShadow(font, area.name(), x - textWidth / 2f, y - 4, LABEL_COLOR);
        }
    }

    /** Small panel next to the cursor: town and nation under the hovered block. */
    public static void drawHover(
        final GuiDraw draw,
        final TextRenderer font,
        final String dimension,
        final double blockX,
        final double blockZ,
        final int mouseX,
        final int mouseY,
        final int width,
        final int height,
        final String nationLabel,
        final String noNationLabel
    ) {
        if (!ManulGate.areasVisible()) {
            return;
        }
        final ManulAreas.Area area = ManulGate.areas().at(dimension, blockX, blockZ);
        if (area == null) {
            return;
        }
        final String line1 = area.name();
        final String line2 = area.nation().isEmpty() ? noNationLabel : nationLabel + area.nation();
        final int w = Math.max(font.getWidth(line1), font.getWidth(line2)) + 8;
        final int h = 24;
        int x = mouseX + 13;
        int y = mouseY + 11;
        if (x + w > width - 2) {
            x = mouseX - 12 - w;
        }
        if (y + h > height - 2) {
            y = mouseY - 12 - h;
        }
        final MatrixStack matrices = draw.matrices();
        RenderUtil.fillRect(matrices, x, y, w, h, PANEL_BACKGROUND);
        RenderUtil.fillRect(matrices, x, y, 2, h, 0xFF000000 | (area.rgb() & 0xFFFFFF));
        draw.drawTextWithShadow(font, line1, x + 5, y + 3, 0xFFFFFFFF);
        draw.drawTextWithShadow(font, line2, x + 5, y + 13, 0xFFB0B0B0);
    }
}
