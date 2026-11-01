#version 330
#extension GL_ARB_separate_shader_objects : require

// 小地图地图纹理的圆形裁剪。
//
// 裁剪坐标独立于地形 UV，避免玩家移动时圆心随纹理漂移。
// G 传递亮度，B 传递圆形边界的归一化内缩量。

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec2 mapCoord;
layout(location = 2) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main() {
    float clipRadius = max(1.0 - vertexColor.b, 0.1);
    vec2 centered = mapCoord - vec2(0.5);
    if (length(centered) * 2.0 > clipRadius) {
        discard;
    }
    vec4 color = texture(Sampler0, texCoord0) * ColorModulator;
    color.rgb *= vertexColor.g;
    if (color.a == 0.0) {
        discard;
    }
    fragColor = color;
}
