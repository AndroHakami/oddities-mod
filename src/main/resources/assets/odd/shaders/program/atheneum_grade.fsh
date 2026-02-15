#version 330 compatibility

uniform sampler2D DiffuseSampler;

uniform float iTime;
uniform float Intensity;
uniform vec3  Tint;

uniform float Pastel;      // now acts like "desaturate amount" (NOT milky wash)
uniform float Contrast;    // 0..1 mapped to a contrast multiplier
uniform vec3  Lift;        // keep tiny
uniform vec3  ShadowTint;  // cool shadows
uniform vec3  HighTint;    // cool highlights

in vec2 texCoord;
out vec4 fragColor;

float sat(float x) { return clamp(x, 0.0, 1.0); }

void main() {
    vec3 base = texture(DiffuseSampler, texCoord).rgb;
    float a = sat(Intensity);

    if (a <= 0.001) {
        fragColor = vec4(base, 1.0);
        return;
    }

    vec3 col = base;

    // luma
    float l = dot(col, vec3(0.299, 0.587, 0.114));

    // 1) main tint
    col *= Tint;

    // 2) contrast (map 0..1 -> 0.85..1.25)
    float cMul = mix(0.85, 1.25, sat(Contrast));
    col = (col - 0.5) * cMul + 0.5;

    // 3) pastel as desaturation only (NO white wash)
    float keepSat = mix(1.0, 0.70, sat(Pastel)); // higher Pastel = less saturation
    col = mix(vec3(dot(col, vec3(0.299, 0.587, 0.114))), col, keepSat);

    // 4) shadow lift (keep subtle, otherwise “milk”)
    float sh = 1.0 - smoothstep(0.08, 0.55, l);
    col += Lift * sh;

    // 5) split tone: cooler shadows -> cooler highlights
    float hi = smoothstep(0.55, 0.95, l);
    col = mix(col * ShadowTint, col * HighTint, hi);

    // 6) tiny dreamy bloom-ish lift in highlights (small)
    col += vec3(0.010, 0.015, 0.030) * hi;

    // 7) subtle vignette
    vec2 v = texCoord - 0.5;
    float vig = sat(1.0 - dot(v, v) * 1.10);
    col *= mix(0.92, 1.0, vig);

    vec3 outCol = mix(base, col, a);
    fragColor = vec4(outCol, 1.0);
}
