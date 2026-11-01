package dev.dubhe.map.client.profiling;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/// 渲染耗时统计测试。
class RenderTimerTest {
    @Test
    void mapSamplingAndMaintenanceAreReportedSeparatelyAndResetBetweenWindows() {
        AtlasProfiling.reset();
        AtlasProfiling.recordMapTick(3_000_000L);
        AtlasProfiling.recordMapMaintenance(80_000_000L);
        String summary = AtlasProfiling.mapUpdateSummary();
        Assertions.assertTrue(summary.contains("tick avg=3.000ms"), summary);
        Assertions.assertTrue(summary.contains("maintenance avg=80.000ms"), summary);
        AtlasProfiling.reset();
        Assertions.assertEquals(0, AtlasProfiling.mapTick().count());
        Assertions.assertFalse(AtlasProfiling.mapUpdateSummary().contains("80.000ms"));
    }

    @Test
    void averageAndMaxAreTracked() {
        RenderTimer timer = new RenderTimer(4);
        timer.record(1_000_000L);
        timer.record(3_000_000L);
        Assertions.assertEquals(2, timer.count());
        Assertions.assertEquals(2_000_000L, timer.averageNanos());
        Assertions.assertEquals(3_000_000L, timer.maxNanos());
    }

    @Test
    void ringBufferOverwritesOldest() {
        RenderTimer timer = new RenderTimer(2);
        timer.record(10_000_000L);
        timer.record(10_000_000L);
        timer.record(2_000_000L);
        // 容量 2：仅保留最后两个样本。
        Assertions.assertEquals(2, timer.count());
        Assertions.assertEquals(6_000_000L, timer.averageNanos());
    }

    @Test
    void overBudgetRatioCountsExceedingSamples() {
        RenderTimer timer = new RenderTimer(4);
        timer.record(500_000L);
        timer.record(1_500_000L);
        timer.record(900_000L);
        timer.record(2_000_000L);

        double ratio = timer.overBudgetRatio(1_000_000L);
        Assertions.assertEquals(0.5, ratio, 1e-9);
    }

    @Test
    void emptyTimerReportsZero() {
        RenderTimer timer = new RenderTimer(8);
        Assertions.assertEquals(0, timer.count());
        Assertions.assertEquals(0L, timer.averageNanos());
        Assertions.assertEquals(0.0, timer.averageMillis(), 1e-9);
        Assertions.assertEquals(0.0, timer.overBudgetRatio(1_000_000L), 1e-9);
    }

    @Test
    void resetClearsAllSamples() {
        RenderTimer timer = new RenderTimer(4);
        timer.record(5_000_000L);
        timer.reset();
        Assertions.assertEquals(0, timer.count());
        Assertions.assertEquals(0L, timer.maxNanos());
    }

    @Test
    void minimapBudgetIsOneMillisecond() {
        Assertions.assertEquals(1_000_000L, AtlasProfiling.MINIMAP_BUDGET_NANOS);
        AtlasProfiling.reset();
        AtlasProfiling.recordMinimap(500_000L);
        AtlasProfiling.recordMinimap(1_500_000L);
        Assertions.assertTrue(AtlasProfiling.minimap().count() >= 2);
        String summary = AtlasProfiling.minimapSummary();
        Assertions.assertTrue(summary.contains("minimap:"), summary);
        Assertions.assertTrue(summary.contains("budget=1ms"), summary);
    }
}
