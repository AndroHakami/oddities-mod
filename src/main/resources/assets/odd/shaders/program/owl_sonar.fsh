#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform float Intensity;

in vec2 texCoord;
out vec4 fragColor;

float sat(float x) { return clamp(x, 0.0, 1.0); }

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

// Try to preserve the outline color for Formatting.AQUA (#55FFFF).
// Outlines are often slightly blended, so we allow tolerance.
bool isAquaOutline(vec3 c) {
    vec3 target = vec3(0.3333333, 1.0, 1.0); // #55FFFF in 0..1
    float d = length(c - target);

    // Strong cyan-ish and bright
    bool cyanish = (c.g > 0.65 && c.b > 0.65 && c.r < 0.80) && abs(c.g - c.b) < 0.25;

    // Distance tolerance for blending / AA
    return cyanish && d < 0.28;
}

void main() {
    vec4 src = texture(DiffuseSampler, texCoord);
    vec3 col = src.rgb;

    float a = sat(Intensity);
    if (a <= 0.001) {
        fragColor = src;
        return;
    }

    // ✅ Preserve AQUA outlines (glow/edge pass) so they stay colored
    if (isAquaOutline(col)) {
        fragColor = src;
        return;
    }

    // Grey filter: desaturate toward luminance
    float y = luma(col);
    vec3 gray = vec3(y);

    vec3 outCol = mix(col, gray, a);
    fragColor = vec4(outCol, src.a);
}
