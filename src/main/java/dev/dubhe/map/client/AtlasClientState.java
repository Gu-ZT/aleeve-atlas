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

    public static int getBlockStep() {
        int idx = Math.max(0, Math.min(AleeveAtlasClient.CONFIG.zoom - 1, ZOOM_STEPS.length - 1));
        return ZOOM_STEPS[idx];
    }

    public static void zoomIn() {
        AleeveAtlasClient.CONFIG.zoom = Math.max(1, AleeveAtlasClient.CONFIG.zoom - 1);
    }

    public static void zoomOut() {
        AleeveAtlasClient.CONFIG.zoom = Math.min(ZOOM_STEPS.length, AleeveAtlasClient.CONFIG.zoom + 1);
    }

    public static void resetZoom() {
        AleeveAtlasClient.CONFIG.zoom = DEFAULT_ZOOM;
    }

    public static void toggleRotation() {
        AleeveAtlasClient.CONFIG.rotation = !AleeveAtlasClient.CONFIG.rotation;
    }

    public static void toggleMinimapVisible() {
        AleeveAtlasClient.CONFIG.display = !AleeveAtlasClient.CONFIG.display;
    }

    public static void toggleMinimapShape() {
        AleeveAtlasClient.CONFIG.mapShape =
            (AleeveAtlasClient.CONFIG.mapShape == AleeveAtlasClientConfig.MapShape.SQUARE)
                ? AleeveAtlasClientConfig.MapShape.CIRCLE
                : AleeveAtlasClientConfig.MapShape.SQUARE;
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
