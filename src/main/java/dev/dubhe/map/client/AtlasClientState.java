package dev.dubhe.map.client;

public final class AtlasClientState {
    private static final int[] ZOOM_STEPS = {1, 2, 4, 6};
    private static final int DEFAULT_ZOOM_INDEX = 1;
    private static int zoomIndex = 1;
    private static float manualRotationDeg = 0.0F;
    private static RotationMode rotationMode = RotationMode.NORTH_UP;
    private static MinimapShape minimapShape = MinimapShape.SQUARE;
    private static boolean minimapVisible = true;

    private AtlasClientState() {
    }

    public static int getBlockStep() {
        return ZOOM_STEPS[zoomIndex];
    }

    public static int getZoomLevel() {
        return zoomIndex;
    }

    public static RotationMode getRotationMode() {
        return rotationMode;
    }

    public static boolean isRotateWithPlayer() {
        return rotationMode == RotationMode.FOLLOW_PLAYER;
    }

    public static MinimapShape getMinimapShape() {
        return minimapShape;
    }

    public static float getManualRotationDeg() {
        return manualRotationDeg;
    }

    public static boolean isMinimapVisible() {
        return minimapVisible;
    }

    public static void zoomIn() {
        zoomIndex = Math.max(0, zoomIndex - 1);
    }

    public static void zoomOut() {
        zoomIndex = Math.min(ZOOM_STEPS.length - 1, zoomIndex + 1);
    }

    public static void resetZoom() {
        zoomIndex = DEFAULT_ZOOM_INDEX;
    }

    public static void toggleRotation() {
        rotationMode = (rotationMode == RotationMode.NORTH_UP) ? RotationMode.FOLLOW_PLAYER : RotationMode.NORTH_UP;
    }

    public static void addManualRotationDeg(float deltaDeg) {
        manualRotationDeg = normalizeAngle(manualRotationDeg + deltaDeg);
    }

    public static void resetManualRotationDeg() {
        manualRotationDeg = 0.0F;
    }

    public static void toggleMinimapShape() {
        minimapShape = (minimapShape == MinimapShape.SQUARE) ? MinimapShape.CIRCLE : MinimapShape.SQUARE;
    }

    public static void toggleMinimapVisible() {
        minimapVisible = !minimapVisible;
    }

    private static float normalizeAngle(float angleDeg) {
        float normalized = angleDeg % 360.0F;
        return normalized < 0.0F ? normalized + 360.0F : normalized;
    }

    public enum RotationMode {
        NORTH_UP,
        FOLLOW_PLAYER
    }

    public enum MinimapShape {
        SQUARE,
        CIRCLE
    }
}

