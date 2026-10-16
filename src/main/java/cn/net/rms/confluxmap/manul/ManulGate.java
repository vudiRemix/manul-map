package cn.net.rms.confluxmap.manul;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntPredicate;

/**
 * Manulcraft fork hook: the map only works while a Manulcraft server has said hello.
 *
 * <p>While {@link #enabled()} is false no map session is opened (so nothing is captured or
 * stored), the minimap is not drawn, and the map hotkeys do nothing. The companion mod
 * (ManulLibs) flips it on when the server's hello arrives; the session tracker resets it
 * whenever the client leaves a world.
 *
 * <p>Also carries the companion's extra right-click menu actions and the area layer
 * (town borders). Everything here is plain Java and safe to touch from the client thread.
 */
public final class ManulGate {
    /** Dev-only escape hatch: {@code -Dmanullibs.gate.bypass=true} keeps the map on everywhere. */
    private static final boolean BYPASS = Boolean.getBoolean("manullibs.gate.bypass");

    private static volatile boolean enabled;
    /** Server verdict for the current world: null until a server that sends it says so. */
    private static volatile Boolean worldAllowed;
    private static volatile boolean allowedNow;
    private static volatile boolean areasVisible = true;
    private static volatile IntPredicate areaToggleKey = key -> false;
    private static final List<LocationAction> ACTIONS = new CopyOnWriteArrayList<>();
    private static final ManulAreas AREAS = new ManulAreas();
    private static volatile int pendingAction = -1;

    private ManulGate() {
    }

    public static boolean enabled() {
        return enabled || BYPASS;
    }

    public static void setEnabled(final boolean value) {
        enabled = value;
    }

    /** Leaving a server: map off, server-provided areas gone. Actions stay registered. */
    public static void reset() {
        enabled = false;
        worldAllowed = null;
        allowedNow = false;
        AREAS.clear();
    }

    public static void setWorldAllowed(final Boolean allowed) {
        worldAllowed = allowed;
    }

    /**
     * Whether the map may run in {@code dimensionId}: only the overworld, and only when the
     * server has not ruled the current world out.
     */
    public static boolean mapAllowed(final String dimensionId) {
        if (!enabled()) {
            return false;
        }
        if (BYPASS) {
            return true;
        }
        return "minecraft:overworld".equals(dimensionId) && !Boolean.FALSE.equals(worldAllowed);
    }

    /** Last {@link #mapAllowed} answer of the session tracker, for input paths. */
    public static boolean allowedNow() {
        return allowedNow;
    }

    public static void setAllowedNow(final boolean allowed) {
        allowedNow = allowed;
    }

    // ------------------------------------------------------------------ location menu actions

    /** Callback for an extra location-menu entry; runs on the client thread. */
    @FunctionalInterface
    public interface LocationCallback {
        void run(int x, int y, int z, String dimensionId);
    }

    /**
     * An extra entry of the fullscreen map's right-click menu, shown after the built-in ones.
     * {@code label} and {@code tooltip} are already-translated strings.
     */
    public record LocationAction(String label, String tooltip, LocationCallback callback) {
    }

    public static void setActions(final List<LocationAction> actions) {
        ACTIONS.clear();
        ACTIONS.addAll(actions);
    }

    public static List<LocationAction> actions() {
        return enabled() ? List.copyOf(ACTIONS) : List.of();
    }

    /** Remembers which external entry the menu picked; {@link #runPending} consumes it. */
    public static void selectPending(final int index) {
        pendingAction = index;
    }

    public static void runPending(final int x, final int y, final int z, final String dimensionId) {
        final int index = pendingAction;
        pendingAction = -1;
        if (index < 0 || index >= ACTIONS.size()) {
            return;
        }
        ACTIONS.get(index).callback().run(x, y, z, dimensionId);
    }

    // ------------------------------------------------------------------ area layer

    public static ManulAreas areas() {
        return AREAS;
    }

    public static boolean areasVisible() {
        return areasVisible && enabled();
    }

    public static void setAreasVisible(final boolean visible) {
        areasVisible = visible;
    }

    public static void toggleAreas() {
        areasVisible = !areasVisible;
    }

    /** Key code matcher for the layer toggle inside the fullscreen map (set by the companion). */
    public static void setAreaToggleKey(final IntPredicate matcher) {
        areaToggleKey = matcher == null ? key -> false : matcher;
    }

    public static boolean isAreaToggleKey(final int keyCode) {
        return enabled() && areaToggleKey.test(keyCode);
    }
}
