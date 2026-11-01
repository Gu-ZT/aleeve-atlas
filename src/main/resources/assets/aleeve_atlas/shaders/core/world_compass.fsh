#version 330
#extension GL_ARB_separate_shader_objects : require

// 世界地图四角方向标。每个方向臂的逆时针半边仅绘制轮廓。
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

float armAlpha(vec2 point, vec2 direction) {
    vec2 perpendicular = vec2(-direction.y, direction.x);
    float along = dot(point, direction);
    float sidePosition = dot(point, perpendicular);
    float width = (0.85 - along) * 0.12;
    float side = width - abs(sidePosition);
    float cap = min(along, 0.85 - along);
    float distanceToEdge = min(side, cap);
    float antialias = max(fwidth(distanceToEdge), 0.001);
    float inside = smoothstep(-antialias, antialias, distanceToEdge);
    if (sidePosition < 0.0) {
        float outline = 1.0 - smoothstep(0.025 - antialias, 0.025 + antialias,
                distanceToEdge);
        return inside * outline;
    }
    return inside;
}

float compassAlpha(vec2 uv) {
    vec2 point = (uv - vec2(0.5)) * 2.0;
    float alpha = 1.0 - smoothstep(0.065, 0.08, length(point));
    alpha = max(alpha, armAlpha(point, vec2(0.0, -1.0)));
    alpha = max(alpha, armAlpha(point, vec2(1.0, 0.0)));
    alpha = max(alpha, armAlpha(point, vec2(0.0, 1.0)));
    alpha = max(alpha, armAlpha(point, vec2(-1.0, 0.0)));
    return clamp(alpha, 0.0, 1.0);
}

void main() {
    float strength = texture(Sampler0, texCoord0).a;
    float alpha = compassAlpha(texCoord0) * strength;
    float shadow = compassAlpha(texCoord0 - vec2(0.018)) * 0.45 * strength;
    if (alpha <= 0.0 && shadow <= 0.0) {
        discard;
    }
    vec3 color = mix(vec3(0.0), vec3(1.0), alpha);
    float opacity = max(alpha, shadow * (1.0 - alpha));
    fragColor = vec4(color, opacity) * ColorModulator * vertexColor;
}
