package cn.net.rms.confluxmap.manul;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Server-provided areas (Towny towns): sets of square cells with a name, nation and color.
 *
 * <p>Geometry is computed once per update, never per frame: the fill is merged into
 * row runs and the outline into maximal straight segments along the outer border. All
 * coordinates are in blocks. Mutations and reads happen on the client thread; readers get
 * an immutable snapshot list so a render pass never sees a half-applied update.
 */
public final class ManulAreas {

    /**
     * One horizontal ({@code vertical == false}) or vertical border segment. {@code inside}
     * is +1 when the area lies on the positive side of the line (below / right), else -1.
     */
    public record Edge(boolean vertical, int fixed, int from, int to, int inside) {
    }

    /** A filled rectangle in blocks. */
    public record Rect(int x, int z, int width, int depth) {
    }

    public record Area(
        String id,
        String name,
        String nation,
        int rgb,
        String dimension,
        int cellSize,
        List<Rect> fills,
        List<Edge> edges,
        int minX,
        int minZ,
        int maxX,
        int maxZ,
        double labelX,
        double labelZ
    ) {
    }

    private final Map<String, Area> byId = new LinkedHashMap<>();
    /** dimension -> packed cell -> area, for hover lookups. */
    private final Map<String, Map<Long, Area>> cells = new HashMap<>();
    private volatile List<Area> snapshot = List.of();
    private volatile int cellSize = 16;
    private volatile long revision = 0x133DB84L;

    public synchronized void clear() {
        if (byId.isEmpty()) {
            return;
        }
        byId.clear();
        cells.clear();
        publish();
    }

    public synchronized void setCellSize(final int size) {
        if (size > 0 && size != cellSize) {
            cellSize = size;
            byId.clear();
            cells.clear();
            publish();
        }
    }

    public int cellSize() {
        return cellSize;
    }

    /**
     * Adds or replaces one area. {@code cellsXZ} holds cell coordinates as consecutive
     * (x, z) pairs; a cell spans {@code [x*cellSize, (x+1)*cellSize)} blocks.
     */
    public synchronized void put(
        final String id,
        final String name,
        final String nation,
        final int rgb,
        final String dimension,
        final int[] cellsXZ
    ) {
        removeInternal(id);
        if (cellsXZ.length < 2) {
            publish();
            return;
        }
        final Area area = build(id, name, nation, rgb, dimension, cellSize, cellsXZ);
        byId.put(id, area);
        final Map<Long, Area> index = cells.computeIfAbsent(dimension, k -> new HashMap<>());
        for (int i = 0; i + 1 < cellsXZ.length; i += 2) {
            index.put(pack(cellsXZ[i], cellsXZ[i + 1]), area);
        }
        publish();
    }

    public synchronized void remove(final String id) {
        removeInternal(id);
        publish();
    }

    private void removeInternal(final String id) {
        final Area old = byId.remove(id);
        if (old == null) {
            return;
        }
        final Map<Long, Area> index = cells.get(old.dimension());
        if (index != null) {
            index.values().removeIf(area -> area == old);
        }
    }

    private void publish() {
        snapshot = List.copyOf(byId.values());
        revision++;
    }

    public List<Area> areas() {
        return snapshot;
    }

    public long revision() {
        return revision;
    }

    /** The area covering a block column, or null. */
    public synchronized Area at(final String dimension, final double blockX, final double blockZ) {
        final Map<Long, Area> index = cells.get(dimension);
        if (index == null || index.isEmpty()) {
            return null;
        }
        final int size = cellSize;
        return index.get(pack(
            (int) Math.floor(blockX / size),
            (int) Math.floor(blockZ / size)
        ));
    }

    private static long pack(final int x, final int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    static Area build(
        final String id,
        final String name,
        final String nation,
        final int rgb,
        final String dimension,
        final int size,
        final int[] cellsXZ
    ) {
        final int n = cellsXZ.length / 2;
        final Set<Long> set = new HashSet<>(n * 2);
        final long[] sorted = new long[n];
        // Sort by row (z) then x so row runs come out in one pass (x sign bit flipped so
        // negative x sorts before positive within a row).
        for (int i = 0; i < n; i++) {
            final int x = cellsXZ[i * 2];
            final int z = cellsXZ[i * 2 + 1];
            set.add(pack(x, z));
            sorted[i] = pack(z, x ^ Integer.MIN_VALUE);
        }
        Arrays.sort(sorted);

        final List<Rect> fills = new ArrayList<>();
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        double sumX = 0;
        double sumZ = 0;
        int runZ = 0;
        int runStart = 0;
        int runEnd = 0;
        boolean open = false;
        for (final long key : sorted) {
            final int z = (int) (key >> 32);
            final int x = (int) key ^ Integer.MIN_VALUE;
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
            sumX += x + 0.5;
            sumZ += z + 0.5;
            if (open && z == runZ && x == runEnd + 1) {
                runEnd = x;
                continue;
            }
            if (open) {
                fills.add(new Rect(runStart * size, runZ * size, (runEnd - runStart + 1) * size, size));
            }
            open = true;
            runZ = z;
            runStart = x;
            runEnd = x;
        }
        if (open) {
            fills.add(new Rect(runStart * size, runZ * size, (runEnd - runStart + 1) * size, size));
        }

        // Border edges: a cell side is on the border when the neighbor across it is missing.
        // Collect them per line, then merge touching unit edges into long segments.
        final Map<Long, List<Integer>> north = new HashMap<>();
        final Map<Long, List<Integer>> south = new HashMap<>();
        final Map<Long, List<Integer>> west = new HashMap<>();
        final Map<Long, List<Integer>> east = new HashMap<>();
        for (final long key : sorted) {
            final int z = (int) (key >> 32);
            final int x = (int) key ^ Integer.MIN_VALUE;
            if (!set.contains(pack(x, z - 1))) {
                north.computeIfAbsent((long) z, k -> new ArrayList<>()).add(x);
            }
            if (!set.contains(pack(x, z + 1))) {
                south.computeIfAbsent((long) z + 1, k -> new ArrayList<>()).add(x);
            }
            if (!set.contains(pack(x - 1, z))) {
                west.computeIfAbsent((long) x, k -> new ArrayList<>()).add(z);
            }
            if (!set.contains(pack(x + 1, z))) {
                east.computeIfAbsent((long) x + 1, k -> new ArrayList<>()).add(z);
            }
        }
        final List<Edge> edges = new ArrayList<>();
        mergeEdges(north, false, 1, size, edges);
        mergeEdges(south, false, -1, size, edges);
        mergeEdges(west, true, 1, size, edges);
        mergeEdges(east, true, -1, size, edges);

        // Label on the cell closest to the centroid, so it stays inside concave shapes.
        final double cx = sumX / n;
        final double cz = sumZ / n;
        double best = Double.MAX_VALUE;
        double labelX = cx;
        double labelZ = cz;
        for (final long key : sorted) {
            final double z = (int) (key >> 32) + 0.5;
            final double x = ((int) key ^ Integer.MIN_VALUE) + 0.5;
            final double d = (x - cx) * (x - cx) + (z - cz) * (z - cz);
            if (d < best) {
                best = d;
                labelX = x;
                labelZ = z;
            }
        }
        return new Area(
            id, name, nation, rgb, dimension, size,
            List.copyOf(fills), List.copyOf(edges),
            minX * size, minZ * size, (maxX + 1) * size, (maxZ + 1) * size,
            labelX * size, labelZ * size
        );
    }

    private static void mergeEdges(
        final Map<Long, List<Integer>> lines,
        final boolean vertical,
        final int inside,
        final int size,
        final List<Edge> out
    ) {
        for (final Map.Entry<Long, List<Integer>> line : lines.entrySet()) {
            final List<Integer> units = line.getValue();
            units.sort(null);
            int start = units.get(0);
            int end = start;
            for (int i = 1; i < units.size(); i++) {
                final int u = units.get(i);
                if (u == end + 1) {
                    end = u;
                    continue;
                }
                out.add(new Edge(vertical, (int) (line.getKey() * size), start * size, (end + 1) * size, inside));
                start = u;
                end = u;
            }
            out.add(new Edge(vertical, (int) (line.getKey() * size), start * size, (end + 1) * size, inside));
        }
    }

    /** Areas of one dimension, from the current snapshot. */
    public static List<Area> inDimension(final Collection<Area> areas, final String dimension) {
        final List<Area> out = new ArrayList<>();
        for (final Area area : areas) {
            if (area.dimension().equals(dimension)) {
                out.add(area);
            }
        }
        return out;
    }
}
