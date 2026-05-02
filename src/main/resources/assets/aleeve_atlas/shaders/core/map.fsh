#version 150

in vec4 vertexColor;

layout (std140) uniform MapUniform {
    vec2 Center;
    float Radius;
};

out vec4 fragColor;

void main() {
    vec2 p = gl_FragCoord.xy;

    float dCircle = length(p - Center) - Radius;
    float aaCircle = max(fwidth(dCircle), 1e-4);
    float alpha = 1.0 - smoothstep(0.0, aaCircle, dCircle);

    if (alpha <= 0.0) {
        discard;
    }

    fragColor = vec4(vertexColor.rgb, vertexColor.a * alpha);
}
