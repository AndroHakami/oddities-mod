#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform float iTime;
uniform float Intensity;
uniform float Progress;

in vec2 texCoord;
out vec4 fragColor;

float sat(float x) { return clamp(x, 0.0, 1.0); }

void main() {
    vec3 base = texture(DiffuseSampler, texCoord).rgb;
    float depth = texture(DepthSampler, texCoord).r;

    float sky = step(0.9995, depth);
    float worldMask = 1.0 - sky;

    float a = sat(Intensity) * sat(Progress);

    // Much softer than before
    float apply = a * mix(0.08, 0.42, worldMask);

    float luma = dot(base, vec3(0.299, 0.587, 0.114));
    float shadow = sat(1.0 - smoothstep(0.20, 0.78, luma));

    // Stronger near top of screen, weaker lower down
    float top = smoothstep(0.52, 1.0, texCoord.y);

    // Subtle vignette to help mood without nuking the image
    vec2 centered = texCoord - 0.5;
    float vignette = 1.0 - sat(dot(centered, centered) * 1.15);

    // Slight desaturation helps sell the "corrupted" vibe
    vec3 desat = mix(base, vec3(luma), 0.10 * a * worldMask);

    // Gentler alien green
    vec3 tintMul    = vec3(0.95, 1.07, 0.98);
    vec3 shadowAdd  = vec3(0.000, 0.045, 0.016) * shadow;
    vec3 skyGlowAdd = vec3(0.000, 0.022, 0.008) * top;
    vec3 edgeAdd    = vec3(0.000, 0.015, 0.006) * (1.0 - vignette) * 0.35;

    vec3 graded = (desat * 0.94) * tintMul + shadowAdd + skyGlowAdd + edgeAdd;
    graded = clamp(graded, 0.0, 1.0);

    vec3 col = mix(base, graded, apply);

    float dn = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
    col += (dn - 0.5) * (1.0 / 255.0);

    fragColor = vec4(col, 1.0);
}