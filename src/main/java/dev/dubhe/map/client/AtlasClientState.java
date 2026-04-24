package dev.dubhe.map.client;

public final class AtlasClientState {
    private static final int[] ZOOM_STEPS = {1, 2, 4, 6};
    private static int zoomIndex = 1;
    private static boolean rotateWithPlayer = false;
    private static boolean minimapVisible = true;

    private AtlasClientState() {
    }

    public static int getBlockStep() {
        return ZOOM_STEPS[zoomIndex];
    }

    public static int getZoomLevel() {
        return zoomIndex;
    }

    public static boolean isRotateWithPlayer() {
        return rotateWithPlayer;
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
        zoomIndex = 1;
    }

    public static void toggleRotation() {
        rotateWithPlayer = !rotateWithPlayer;
    }

    public static void toggleMinimapVisible() {
        minimapVisible = !minimapVisible;
    }
}

