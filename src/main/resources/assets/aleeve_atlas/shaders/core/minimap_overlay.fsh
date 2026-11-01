#version 150

// 边框使用 G 通道传递圆形开关，A 通道传递向外扩展的比例。

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

const vec4 BORDER_COLOR = vec4(0.06, 0.06, 0.06, 1.0);

void main() {
    float strength = texture(Sampler0, texCoord0).a;
    if (strength <= 0.0) {
        discard;
    }

    vec2 centered = texCoord0 - vec2(0.5);
    bool circular = vertexColor.g > 0.5;
    float originalScale = 1.0 / (1.0 + vertexColor.a);
    float edge = circular ? length(centered) * 2.0 : max(abs(centered.x), abs(centered.y)) * 2.0;
    float innerEdge = min(circular ? 0.972 : 0.985, 1.0 - vertexColor.b) * originalScale;
    float outerEdge = (innerEdge + 1.0) * 0.5;
    if (edge > outerEdge || edge <= innerEdge) {
        discard;
    }
    fragColor = BORDER_COLOR * strength;
}
