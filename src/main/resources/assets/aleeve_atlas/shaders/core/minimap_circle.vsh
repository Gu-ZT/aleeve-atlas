#version 150

uniform mat4 ModelViewMat;
uniform vec4 ColorModulator;
uniform mat4 ProjMat;

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 texCoord0;
out vec2 mapCoord;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
    vertexColor = Color;
    // BlitRenderState 的 QUADS 顶点顺序：左上、左下、右下、右上。
    int corner = gl_VertexID % 4;
    mapCoord = vec2(corner >= 2 ? 1.0 : 0.0, corner == 1 || corner == 2 ? 1.0 : 0.0);
}
