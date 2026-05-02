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
    float aa = max(fwidth(d), 0.35);
    return smoothstep(-aa, aa, d);
}

// ── SDF primitives (positive = inside) ───────────────────────────────────────

float sdfCircle(vec2 p, vec2 c, float r) {
    return r - length(p - c);
}

float cross2d(vec2 a, vec2 b) {
    return a.x * b.y - a.y * b.x;
}

float distanceToSegment(vec2 p, vec2 a, vec2 b) {
    vec2 ab = b - a;
    float t = clamp(dot(p - a, ab) / max(dot(ab, ab), 1e-6), 0.0, 1.0);
    return length((a + ab * t) - p);
}

// Triangle SDF with positive-inside convention and winding-independent inside test.
float sdfTriangle(vec2 p, vec2 a, vec2 b, vec2 c) {
    float d0 = distanceToSegment(p, a, b);
    float d1 = distanceToSegment(p, b, c);
    float d2 = distanceToSegment(p, c, a);
    float edgeDistance = min(d0, min(d1, d2));

    float orientation = sign(cross2d(b - a, c - a));
    if (orientation == 0.0) {
        orientation = 1.0;
    }

    float s0 = orientation * cross2d(b - a, p - a);
    float s1 = orientation * cross2d(c - b, p - b);
    float s2 = orientation * cross2d(a - c, p - c);
    bool inside = s0 >= 0.0 && s1 >= 0.0 && s2 >= 0.0;
    return inside ? edgeDistance : -edgeDistance;
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
        float cr = MarkerRadius * 0.58;
        vec2  cc = vec2(0.0, MarkerRadius * 0.18);
        float circleSDF = sdfCircle(r, cc, cr);

        // Arrow triangle pointing in -y direction (tip at -MarkerRadius).
        // Base line is tangent to the circle at y = cc.y - cr.
        float tangentY = cc.y - cr;
        float halfBase = MarkerRadius * 0.26;
        vec2 triA = vec2(-halfBase, tangentY);
        vec2 triB = vec2(0.0, -MarkerRadius * 1.05);
        vec2 triC = vec2(halfBase, tangentY);
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

