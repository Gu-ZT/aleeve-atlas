#version 150

// 实体圆点标记。以顶点色作为填充色，外圈使用深色描边并做抗锯齿。
uniform mat4 ModelViewMat;
uniform vec4 ColorModulator;
uniform sampler2D Sampler0;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    float strength = texture(Sampler0, texCoord0).a;
    float distanceToEdge = length(texCoord0 - vec2(0.5)) * 2.0;
    float antialias = max(fwidth(distanceToEdge), 0.001);
    float alpha = 1.0 - smoothstep(1.0 - antialias, 1.0 + antialias, distanceToEdge);
    if (alpha <= 0.0 || strength <= 0.0) {
        discard;
    }
    vec4 fill = vertexColor * ColorModulator;
    vec4 outline = vec4(0.06, 0.06, 0.06, fill.a);
    float border = smoothstep(0.82 - antialias, 0.82 + antialias, distanceToEdge);
    fragColor = mix(fill, outline, border) * vec4(1.0, 1.0, 1.0, alpha * strength);
}
