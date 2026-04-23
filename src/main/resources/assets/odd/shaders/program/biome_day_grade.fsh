#version 330 compatibility

uniform sampler2D DiffuseSampler;

uniform float iTime;
uniform float Intensity;

uniform vec3 SkyTint;
uniform vec3 FogTint;
uniform vec3 HorizonTint;
uniform vec3 CloudTint;

in vec2 texCoord;
out vec4 fragColor;

float sat(float x) { return clamp(x, 0.0, 1.0); }
float luma(vec3 c) { return max(dot(c, vec3(0.299, 0.587, 0.114)), 1e-4); }

vec3 normalizedTint(vec3 tint) {
    return tint / luma(tint);
}

vec3 applyTintPreserveBrightness(vec3 base, vec3 tint, float amount) {
    vec3 target = base * normalizedTint(tint);
    return mix(base, target, sat(amount));
}

void main() {
    vec3 base = texture(DiffuseSampler, texCoord).rgb;
    float a = sat(Intensity);

    if (a <= 0.001) {
        fragColor = vec4(base, 1.0);
        return;
    }

    vec3 col = base;
    float baseL = luma(base);

    float shadow = 1.0 - smoothstep(0.10, 0.48, baseL);
    float mid = 1.0 - abs(baseL - 0.50) * 2.0;
    mid = sat(mid);
    float hi = smoothstep(0.56, 0.96, baseL);

    // Apply the biome palette without crushing brightness.
    col = applyTintPreserveBrightness(col, SkyTint, 0.050 * a);
    col = applyTintPreserveBrightness(col, FogTint, shadow * 0.075 * a);
    col = applyTintPreserveBrightness(col, HorizonTint, mid * 0.065 * a);
    col = applyTintPreserveBrightness(col, CloudTint, hi * 0.050 * a);

    // Tiny highlight lift so the effect never reads as an accidental darkener.
    col += hi * 0.008 * mix(HorizonTint, CloudTint, 0.5) * a;

    // Keep output luminance very close to the source image.
    float outL = luma(col);
    col *= mix(1.0, baseL / max(outL, 1e-4), 0.92);

    fragColor = vec4(mix(base, col, a), 1.0);
}
