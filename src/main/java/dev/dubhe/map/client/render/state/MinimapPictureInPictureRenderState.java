package dev.dubhe.map.client.render.state;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.joml.Vector2f;

import java.util.List;
import javax.annotation.Nullable;

public record MinimapPictureInPictureRenderState(
    int x0, int y0, int x1, int y1,
    @Nullable ScreenRectangle scissorArea,
    @Nullable ScreenRectangle bounds,
    List<CellData> cells
) implements PictureInPictureRenderState {

    public MinimapPictureInPictureRenderState(
        int x0, int y0, int x1, int y1,
        @Nullable ScreenRectangle scissorArea, List<CellData> cells
    ) {
        this(
            x0, y0, x1, y1, scissorArea,
            PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea), cells
        );
    }

    @Override
    public float scale() {
        return 1.0f;
    }

    public record CellData(Vector2f p0, Vector2f p1, Vector2f p2, Vector2f p3, int color) {
    }
}
