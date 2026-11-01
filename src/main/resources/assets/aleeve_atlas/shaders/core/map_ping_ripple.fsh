#version 150

// 临时标点波纹。圆环边缘使用 fwidth 抗锯齿，避免逐像素阶梯。
uniform mat4 ModelViewMat;
uniform vec4 ColorModulator;
uniform sampler2D Sampler0;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    float strength = texture(Sampler0, texCoord0).a;
    float distanceToCenter = length(texCoord0 - vec2(0.5)) * 2.0;
    float antialias = max(fwidth(distanceToCenter), 0.001);
    float ring = 1.0 - smoothstep(0.05 - antialias, 0.05 + antialias,
            abs(distanceToCenter - 0.78));
    float edge = 1.0 - smoothstep(1.0 - antialias, 1.0 + antialias, distanceToCenter);
    float alpha = ring * edge * strength;
    if (alpha <= 0.0) {
        discard;
    }
    fragColor = vertexColor * ColorModulator * vec4(1.0, 1.0, 1.0, alpha);
}
