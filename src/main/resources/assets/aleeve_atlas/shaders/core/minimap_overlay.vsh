#version 150

// 地图覆盖层共用顶点变换，具体形状和颜色由各自的片元着色器决定。

uniform mat4 ModelViewMat;
uniform vec4 ColorModulator;
uniform mat4 ProjMat;

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 texCoord0;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
    vertexColor = Color;
}
