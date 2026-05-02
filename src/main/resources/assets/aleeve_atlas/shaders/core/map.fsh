#version 150

in vec4 vertexColor;

layout (std140) uniform MapUniform {
    vec2 ClipCenter;
    vec2 ClipHalfSize;
    float ClipRadius;
    float ClipMode;
};

out vec4 fragColor;

float clipSignedDistance(vec2 p) {
    if (ClipMode > 0.5) {
        return ClipRadius - length(p - ClipCenter);
    }

    vec2 delta = ClipHalfSize - abs(p - ClipCenter);
    return min(delta.x, delta.y);
}

float coverageFromDistance(float distance) {
    float aa = max(fwidth(distance), 1e-4);
    return smoothstep(-aa, aa, distance);
}

void main() {
    vec2 p = gl_FragCoord.xy;

    float clipAlpha = coverageFromDistance(clipSignedDistance(p));
    float alpha = clipAlpha;

    if (alpha <= 0.0) {
        discard;
    }

    fragColor = vec4(vertexColor.rgb, vertexColor.a * alpha);
}
