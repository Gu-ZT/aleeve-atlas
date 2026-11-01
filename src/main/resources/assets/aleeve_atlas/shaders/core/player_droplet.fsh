#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

// 128 像素小地图中的原有半径和尖端距离，使用界面像素方便指定等宽描边。
const float RADIUS = 4.16;
const float TIP = 8.32;
const float OUTLINE = 1.0;

float segmentDistance(vec2 point, vec2 start, vec2 end) {
    vec2 edge = end - start;
    vec2 delta = point - start;
    return length(delta - edge * clamp(dot(delta, edge) / dot(edge, edge), 0.0, 1.0));
}

float dropletDistance(vec2 point) {
    vec2 tip = vec2(0.0, -TIP);
    vec2 left = vec2(-RADIUS, 0.0);
    vec2 right = vec2(RADIUS, 0.0);
    float triangle = min(segmentDistance(point, tip, left),
        min(segmentDistance(point, left, right), segmentDistance(point, right, tip)));
    bool inside = point.y <= 0.0 && abs(point.x) <= RADIUS * (point.y + TIP) / TIP;
    if (inside) {
        triangle = -triangle;
    }
    return min(length(point) - RADIUS, triangle);
}

void main() {
    float distanceToEdge = dropletDistance((texCoord0 - vec2(0.5)) * 32.0);
    float antialias = max(fwidth(distanceToEdge) * 0.5, 0.001);
    float fill = 1.0 - smoothstep(-antialias, antialias, distanceToEdge);
    float alpha = 1.0 - smoothstep(OUTLINE - antialias, OUTLINE + antialias, distanceToEdge);
    alpha *= texture(Sampler0, texCoord0).a * vertexColor.a;
    if (alpha <= 0.0) {
        discard;
    }
    fragColor = vec4(vertexColor.rgb * fill, alpha);
}
