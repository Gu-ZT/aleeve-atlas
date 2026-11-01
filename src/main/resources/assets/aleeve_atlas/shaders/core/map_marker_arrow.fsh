#version 330
#extension GL_ARB_separate_shader_objects : require

// 地图边缘方向箭头。基础形状尖端朝上，由 Java 侧按目标方向旋转。
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};
uniform sampler2D Sampler0;
layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;
layout(location = 0) out vec4 fragColor;

void main() {
    float strength = texture(Sampler0, texCoord0).a;
    vec2 point = texCoord0 - vec2(0.5);
    // 顶点 (0,-0.45)，底边 y=0.35，左右半宽随 y 线性增加。
    float halfWidth = (point.y + 0.45) * 0.5;
    float sideDistance = halfWidth - abs(point.x);
    float capDistance = min(point.y + 0.45, 0.35 - point.y);
    float distanceToEdge = min(sideDistance, capDistance);
    float antialias = max(fwidth(distanceToEdge), 0.001);
    float alpha = smoothstep(-antialias, antialias, distanceToEdge);
    if (alpha <= 0.0 || strength <= 0.0) {
        discard;
    }
    vec4 fill = vertexColor * ColorModulator;
    vec4 outline = vec4(0.06, 0.06, 0.06, fill.a);
    float border = 1.0 - smoothstep(0.03 - antialias, 0.03 + antialias, distanceToEdge);
    fragColor = mix(fill, outline, border) * vec4(1.0, 1.0, 1.0, alpha * strength);
}
