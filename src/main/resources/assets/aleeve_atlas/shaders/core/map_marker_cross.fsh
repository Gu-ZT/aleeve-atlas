#version 150

// 光滑叉形标记。两条有限长度的对角线使用统一填充和深色描边。
uniform mat4 ModelViewMat;
uniform vec4 ColorModulator;
uniform sampler2D Sampler0;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    float strength = texture(Sampler0, texCoord0).a;
    vec2 point = (texCoord0 - vec2(0.5)) * 2.0;
    float arm = max(abs(point.x), abs(point.y));
    float first = min(0.16 - abs(point.x - point.y) * 0.70710678, 0.85 - arm);
    float second = min(0.16 - abs(point.x + point.y) * 0.70710678, 0.85 - arm);
    float distanceToEdge = max(first, second);
    float antialias = max(fwidth(distanceToEdge), 0.001);
    float alpha = smoothstep(-antialias, antialias, distanceToEdge);
    if (alpha <= 0.0 || strength <= 0.0) {
        discard;
    }
    vec4 fill = vertexColor * ColorModulator;
    vec4 outline = vec4(0.06, 0.06, 0.06, fill.a);
    float border = 1.0 - smoothstep(0.025 - antialias, 0.025 + antialias, distanceToEdge);
    fragColor = mix(fill, outline, border) * vec4(1.0, 1.0, 1.0, alpha * strength);
}
