#version 150

in vec4 vertexColor;

layout (std140) uniform MinimapFrameUniform {
    vec2 FrameCenter;
    float FrameRadius;
    float BorderWidth;
    float FillR;
    float FillG;
    float FillB;
    float FillA;
    float BorderR;
    float BorderG;
    float BorderB;
    float BorderA;
};

out vec4 fragColor;

float coverageFromDistance(float d) {
    float aa = max(fwidth(d), 1e-4);
    return smoothstep(-aa, aa, d);
}

void main() {
    vec2 p = gl_FragCoord.xy;
    float distToCenter = length(p - FrameCenter);

    float insideCircle = FrameRadius - distToCenter;
    float insideBorderBand = distToCenter - (FrameRadius - BorderWidth);

    float circleAlpha = coverageFromDistance(insideCircle);
    float borderMask = coverageFromDistance(insideBorderBand);

    if (circleAlpha <= 0.0) {
        discard;
    }

    vec4 fillColor = vec4(FillR, FillG, FillB, FillA);
    vec4 borderColor = vec4(BorderR, BorderG, BorderB, BorderA);
    vec4 mixed = mix(fillColor, borderColor, borderMask);
    fragColor = vec4(mixed.rgb, mixed.a * circleAlpha);
}

