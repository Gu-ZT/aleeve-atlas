#version 150

in vec4 vertexColor;

layout (std140) uniform MarkerUniform {
    vec2 ClipCenter;
    vec2 ClipHalfSize;
    float ClipRadius;
    float ClipMode;
    vec2 MarkerCenter;
    float MarkerRadius;
};

out vec4 fragColor;

// ── Clip helpers ──────────────────────────────────────────────────────────────

float clipSignedDistance(vec2 p) {
    if (ClipMode > 0.5) {
        return ClipRadius - length(p - ClipCenter);
    }
    vec2 delta = ClipHalfSize - abs(p - ClipCenter);
    return min(delta.x, delta.y);
}

float coverageFromDistance(float d) {
    float aa = max(fwidth(d), 0.35);
    return smoothstep(-aa, aa, d);
}

// ── Main ──────────────────────────────────────────────────────────────────────

void main() {
    vec2 p = gl_FragCoord.xy;

    // 1. Minimap clip region
    float clipAlpha = coverageFromDistance(clipSignedDistance(p));
    if (clipAlpha <= 0.0) {
        discard;
    }

    // 2. Filled circle marker SDF
    float markerSDF = MarkerRadius - length(p - MarkerCenter);
    float markerAlpha = coverageFromDistance(markerSDF);
    float alpha = clipAlpha * markerAlpha;

    if (alpha <= 0.0) {
        discard;
    }

    fragColor = vec4(vertexColor.rgb, vertexColor.a * alpha);
}

