package dev.dubhe.map.client;

public final class AtlasClientState {
    /** blockStep per zoom level 1‑5 */
    private static final int[] ZOOM_STEPS = {1, 2, 4, 6, 12};
    private static final int DEFAULT_ZOOM = 2;

    /** Runtime-only: drag rotation delta (not persisted). */
    private static float manualRotationDeg = 0.0F;

    private AtlasClientState() {
    }

    // ── persistent state via CONFIG ──────────────────────────────────────

    public static boolean isMinimapVisible() {
        return AleeveAtlasClient.CONFIG.display;
    }

    public static boolean isRotateWithPlayer() {
        return AleeveAtlasClient.CONFIG.rotation;
    }

    public static AleeveAtlasClientConfig.MapShape getMinimapShape() {
        return AleeveAtlasClient.CONFIG.mapShape;
    }

    public static int getZoomLevel() {
        return AleeveAtlasClient.CONFIG.zoom;
    }

    public static void setZoomLevel(int zoomLevel) {
        AleeveAtlasClient.CONFIG.zoom = Math.clamp(zoomLevel, 1, 5);
    }

    public static void changeZoom(int delta) {
        setZoomLevel(getZoomLevel() + delta);
    }

    public static void resetZoom() {
        setZoomLevel(DEFAULT_ZOOM);
    }

    public static int getBlockStep() {
        int idx = Math.clamp(AleeveAtlasClient.CONFIG.zoom - 1, 0, ZOOM_STEPS.length - 1);
        return ZOOM_STEPS[idx];
    }

    public static boolean isDynamicLightingEnabled() {
        return AleeveAtlasClient.CONFIG.dynamicLighting;
    }

    public static boolean isCaveMappingEnabled() {
        return AleeveAtlasClient.CONFIG.caveMapping;
    }

    public static boolean isCoordinateLineVisible() {
        return AleeveAtlasClient.CONFIG.showCoordinates;
    }

    public static boolean isEnvironmentLineVisible() {
        return AleeveAtlasClient.CONFIG.showEnvironment;
    }

    public static AleeveAtlasClientConfig.MinimapAnchor getMinimapAnchor() {
        return AleeveAtlasClient.CONFIG.minimapAnchor;
    }

    public static int getMinimapSizePx() {
        return AleeveAtlasClient.CONFIG.minimapSize.pixels();
    }

    public static boolean isRadarEnabled() {
        return AleeveAtlasClient.CONFIG.radar;
    }

    public static int getRadarRangeBlocks() {
        return AleeveAtlasClient.CONFIG.radarRange.blocks();
    }

    public static boolean showHostileOnRadar() {
        return AleeveAtlasClient.CONFIG.radarHostile;
    }

    public static boolean showFriendlyOnRadar() {
        return AleeveAtlasClient.CONFIG.radarFriendly;
    }

    public static boolean showItemsOnRadar() {
        return AleeveAtlasClient.CONFIG.radarItems;
    }

    public static boolean showPlayersOnRadar() {
        return AleeveAtlasClient.CONFIG.radarPlayers;
    }

    // ── runtime-only state ───────────────────────────────────────────────

    public static float getManualRotationDeg() {
        return manualRotationDeg;
    }

    public static void addManualRotationDeg(float deltaDeg) {
        manualRotationDeg = normalizeAngle(manualRotationDeg + deltaDeg);
    }

    public static void resetManualRotationDeg() {
        manualRotationDeg = 0.0F;
    }

    private static float normalizeAngle(float a) {
        float n = a % 360.0F;
        return n < 0.0F ? n + 360.0F : n;
    }
}
