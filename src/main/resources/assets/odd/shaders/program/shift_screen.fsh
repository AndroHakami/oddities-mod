#version 330 compatibility

uniform sampler2D DiffuseSampler;

uniform float Time;
uniform vec2 OutSize;
uniform float ImbueIntensity;
uniform float TaggedIntensity;
uniform float PulseIntensity;

in vec2 texCoord;
out vec4 fragColor;

float sat(float x) { return clamp(x, 0.0, 1.0); }

float hash(vec2 p) {
    p = fract(p * vec2(234.34, 754.12));
    p += dot(p, p + 23.45);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);

    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));

    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.55;
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p *= 2.0;
        a *= 0.55;
    }
    return v;
}

vec3 palette(float t) {
    vec3 a = vec3(0.55, 0.45, 0.65);
    vec3 b = vec3(0.45, 0.45, 0.35);
    vec3 c = vec3(1.00, 1.00, 1.00);
    vec3 d = vec3(0.00, 0.20, 0.35);
    return a + b * cos(6.28318 * (c * t + d));
}

void main() {
    vec2 uv = texCoord;
    vec4 src = texture(DiffuseSampler, uv);
    vec3 col = src.rgb;

    float imb = sat(ImbueIntensity);
    float tag = sat(TaggedIntensity);
    float pulse = sat(PulseIntensity);
    float total = sat(max(imb, tag) + pulse * 0.6);

    if (total <= 0.001) {
        fragColor = src;
        return;
    }

    vec2 p = uv - 0.5;
    float aspect = OutSize.x / max(OutSize.y, 1.0);
    vec2 pp = vec2(p.x * aspect, p.y);

    float r = length(pp);
    float ang = atan(pp.y, pp.x);

    // push the effect closer to the screen border so it takes less space
    float edge = smoothstep(0.58, 0.94, r);
    edge *= edge;

    float warpNoise = fbm(pp * 3.5 + vec2(Time * 0.18, -Time * 0.15));
    float waveA = sin(r * 46.0 - Time * 8.0 + warpNoise * 5.2);
    float waveB = sin(ang * 12.0 + Time * 4.5 + r * 18.0);
    float wave = 0.5 + 0.5 * (0.7 * waveA + 0.3 * waveB);

    vec2 dir = r > 0.0001 ? normalize(pp) : vec2(0.0, 0.0);
    float distort = (0.002 + 0.005 * pulse + 0.003 * imb) * edge * (0.35 + 0.65 * wave);
    vec2 sampleUv = uv - vec2(dir.x / max(aspect, 0.001), dir.y) * distort;
    vec3 shifted = texture(DiffuseSampler, sampleUv).rgb;

    float hue = wave * 0.55 + ang / 6.28318 + Time * 0.10 + warpNoise * 0.18;
    vec3 chroma = palette(hue);
    vec3 taggedTint = mix(vec3(0.10, 1.00, 0.95), vec3(1.00, 0.20, 0.85), wave);

    float ownerGlow = edge * imb * (0.10 + 0.28 * wave);
    float taggedGlow = edge * tag * (0.10 + 0.24 * (1.0 - wave));

    col = mix(col, shifted, 0.08 * total);
    col += chroma * ownerGlow;
    col += taggedTint * taggedGlow;

    float ring = smoothstep(0.76, 0.98, r + 0.010 * sin(Time * 5.0 + ang * 9.0));
    col *= 1.0 - 0.07 * ring * total;

    float sparkSeed = hash(floor(uv * OutSize * 0.35) + floor(Time * 10.0));
    float sparks = smoothstep(0.985, 1.0, sparkSeed) * edge;
    col += (chroma * 0.7 + vec3(1.0) * 0.3) * sparks * (0.08 * imb + 0.06 * tag);

    float flash = pulse * smoothstep(0.0, 1.0, 1.0 - r);
    col += vec3(1.0, 0.96, 1.0) * flash * 0.14;

    fragColor = vec4(col, src.a);
}
