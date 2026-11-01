package dev.dubhe.map.client.map;

import dev.dubhe.map.config.AtlasClientConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapZoomTest {
    @Test
    void caveShortcutsPreserveSurfaceZoomAndRestoreItOnExit() {
        AtlasClientConfig config = new AtlasClientConfig();
        config.minimap.zoom = 1.75;
        config.caves.useSeparateZoom = true;
        config.caves.zoom = 2.5;
        MinimapZoom.adjust(config, true, 0.25);
        assertEquals(2.75, MinimapZoom.target(config, true));
        assertEquals(1.75, MinimapZoom.target(config, false));
        config.caves.useSeparateZoom = false;
        MinimapZoom.adjust(config, true, -0.25);
        assertEquals(1.5, config.minimap.zoom);
        assertEquals(2.75, config.caves.zoom);
    }

    @Test
    void shortcutBoundsApplyToTheSelectedMode() {
        AtlasClientConfig config = new AtlasClientConfig();
        config.caves.useSeparateZoom = true;
        MinimapZoom.adjust(config, true, 100);
        MinimapZoom.adjust(config, false, -100);
        assertEquals(MinimapLayout.MAX_ZOOM, config.caves.zoom);
        assertEquals(MinimapLayout.MIN_ZOOM, config.minimap.zoom);
    }

    @Test
    void animationIsFrameRateIndependentAndFinishesWithoutOvershoot() {
        MinimapZoom lowFps = begin(1.5, 4.0);
        MinimapZoom highFps = begin(1.5, 4.0);
        for (long t = 10_000_000L; t < 100_000_000L; t += 10_000_000L) {
            highFps.sample(4.0, true, t);
        }
        double halfway = lowFps.sample(4.0, true, 100_000_000L);
        assertEquals(halfway, highFps.sample(4.0, true, 100_000_000L), 1.0E-12);
        assertTrue(halfway > 1.5 && halfway < 4.0);
        assertEquals(4.0, lowFps.sample(4.0, true, 200_000_000L));
        assertEquals(4.0, lowFps.sample(4.0, true, 300_000_000L));
    }

    @Test
    void reversingDuringAnimationStartsAtTheCurrentView() {
        MinimapZoom zoom = begin(1.0, 4.0);
        double before = zoom.sample(4.0, true, 80_000_000L);
        assertEquals(before, zoom.sample(1.0, true, 80_000_000L));
        double after = zoom.sample(1.0, true, 100_000_000L);
        assertTrue(after < before && after > 1.0);
        assertEquals(1.0, zoom.sample(1.0, true, 300_000_000L));
    }

    @Test
    void disablingAnimationImmediatelyStopsAtTheRequestedZoom() {
        MinimapZoom zoom = begin(1.0, 4.0);
        assertEquals(4.0, zoom.sample(4.0, false, 50_000_000L));
        assertEquals(4.0, zoom.sample(4.0, true, 100_000_000L));
        assertEquals(2.0, zoom.sample(2.0, false, 110_000_000L));
    }

    @Test
    void firstFrameAndLongRenderingGapsDoNotAnimateFromAStaleWorld() {
        MinimapZoom zoom = new MinimapZoom();
        assertEquals(3.0, zoom.sample(3.0, true, 10_000_000L));
        assertEquals(1.5, zoom.sample(1.5, true, 3_000_000_000L));
        assertEquals(1.5, zoom.sample(Double.NaN, false, 3_010_000_000L));
    }

    private static MinimapZoom begin(double from, double to) {
        MinimapZoom zoom = new MinimapZoom();
        zoom.sample(from, false, 0L);
        zoom.sample(to, true, 0L);
        return zoom;
    }
}
