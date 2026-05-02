#version 150

in vec4 vertexColor;

layout (std140) uniform MarkerUniform {
    vec2 ClipCenter;
    vec2 ClipHalfSize;
    float ClipRadius;
    float ClipMode;
    vec2 MarkerCenter;
    float MarkerRadius;
    float MarkerMode;   // 0 = filled circle, 1 = circle + directional arrow
    float ArrowAngle;   // radians, 0 = arrow points up (-y), increases CW
};

out vec4 fragColor;

// ── Clip helpers (same logic as map.fsh) ──────────────────────────────────────

float clipSignedDistance(vec2 p) {
    if (ClipMode > 0.5) {
        return ClipRadius - length(p - ClipCenter);
    }
    vec2 delta = ClipHalfSize - abs(p - ClipCenter);
    return min(delta.x, delta.y);
}

float coverageFromDistance(float d) {
    float aa = max(fwidth(d), 1e-4);
    return smoothstep(-aa, aa, d);
}

// ── SDF primitives (positive = inside) ───────────────────────────────────────

float sdfCircle(vec2 p, vec2 c, float r) {
    return r - length(p - c);
}

// Inigo Quilez triangle SDF, negated so positive = inside
float sdfTriangle(vec2 p, vec2 a, vec2 b, vec2 c) {
    vec2 e0 = b - a, e1 = c - b, e2 = a - c;
    vec2 v0 = p - a, v1 = p - b, v2 = p - c;
    vec2 pq0 = v0 - e0 * clamp(dot(v0, e0) / dot(e0, e0), 0.0, 1.0);
    vec2 pq1 = v1 - e1 * clamp(dot(v1, e1) / dot(e1, e1), 0.0, 1.0);
    vec2 pq2 = v2 - e2 * clamp(dot(v2, e2) / dot(e2, e2), 0.0, 1.0);
    float s = sign(e0.x * e2.y - e0.y * e2.x);
    vec2 d = min(min(
        vec2(dot(pq0, pq0), s * (v0.x * e0.y - v0.y * e0.x)),
        vec2(dot(pq1, pq1), s * (v1.x * e1.y - v1.y * e1.x))),
        vec2(dot(pq2, pq2), s * (v2.x * e2.y - v2.y * e2.x)));
    // Standard IQ returns negative inside; negate so positive = inside
    return sqrt(d.x) * sign(d.y);
}

// ── Main ──────────────────────────────────────────────────────────────────────

void main() {
    vec2 p = gl_FragCoord.xy;

    // 1. Minimap clip region
    float clipAlpha = coverageFromDistance(clipSignedDistance(p));
    if (clipAlpha <= 0.0) {
        discard;
    }

    // 2. Transform fragment into marker-local space (arrow points toward -y when ArrowAngle=0)
    vec2 local = p - MarkerCenter;
    float cosA = cos(ArrowAngle);
    float sinA = sin(ArrowAngle);
    // Rotate local by -ArrowAngle to get standard local coords where arrow = -y
    vec2 r = vec2(
         local.x * cosA + local.y * sinA,
        -local.x * sinA + local.y * cosA
    );

    // 3. Compute marker SDF
    float markerSDF;
    if (MarkerMode < 0.5) {
        // Simple filled circle
        markerSDF = sdfCircle(r, vec2(0.0), MarkerRadius);
    } else {
        // Circle body (offset toward +y so arrow has room)
        float cr = MarkerRadius * 0.60;
        vec2  cc = vec2(0.0, MarkerRadius * 0.25);
        float circleSDF = sdfCircle(r, cc, cr);

        // Arrow triangle pointing in -y direction (tip at -MarkerRadius)
        vec2 triA = vec2(-MarkerRadius * 0.40,  0.0);
        vec2 triB = vec2( MarkerRadius * 0.40,  0.0);
        vec2 triC = vec2( 0.0, -MarkerRadius);
        float triangleSDF = sdfTriangle(r, triA, triB, triC);

        // Union of circle and triangle (positive-inside convention: max = union)
        markerSDF = max(circleSDF, triangleSDF);
    }

    float markerAlpha = coverageFromDistance(markerSDF);
    float alpha = clipAlpha * markerAlpha;

    if (alpha <= 0.0) {
        discard;
    }

    fragColor = vec4(vertexColor.rgb, vertexColor.a * alpha);
}

