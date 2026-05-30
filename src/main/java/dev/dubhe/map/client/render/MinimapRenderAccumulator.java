package dev.dubhe.map.client.render;

import dev.dubhe.map.client.render.state.MinimapPictureInPictureRenderState;
import dev.dubhe.map.client.render.state.MinimapPictureInPictureRenderState.CellData;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public final class MinimapRenderAccumulator {
    private static final ThreadLocal<MinimapRenderAccumulator> INSTANCE = ThreadLocal.withInitial(MinimapRenderAccumulator::new);
    private final List<CellData> cells = new ArrayList<>();
    private boolean begun;
    private int pipX0, pipY0, pipX1, pipY1;
    private @Nullable ScreenRectangle scissor;

    private MinimapRenderAccumulator() {
    }

    public static void begin(int x0, int y0, int x1, int y1, @Nullable ScreenRectangle scissor) {
        var a = INSTANCE.get();
        a.begun = true;
        a.pipX0 = x0;
        a.pipY0 = y0;
        a.pipX1 = x1;
        a.pipY1 = y1;
        a.scissor = scissor;
        a.cells.clear();
    }

    public static void beginIfNeeded(int x0, int y0, int x1, int y1, @Nullable ScreenRectangle scissor) {
        if (!INSTANCE.get().begun) begin(x0, y0, x1, y1, scissor);
    }

    public static void addCell(CellData cell) {
        INSTANCE.get().cells.add(cell);
    }

    public static void flush(GuiGraphicsExtractor graphics) {
        var a = INSTANCE.get();
        if (!a.begun || a.cells.isEmpty()) {
            a.begun = false;
            return;
        }
        graphics.submitPictureInPictureRenderState(
            new MinimapPictureInPictureRenderState(a.pipX0, a.pipY0, a.pipX1, a.pipY1, a.scissor, List.copyOf(a.cells)));
        a.begun = false;
    }
}
