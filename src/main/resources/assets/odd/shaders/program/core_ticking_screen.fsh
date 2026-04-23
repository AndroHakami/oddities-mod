#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float Time;
uniform float Intensity;

in vec2 texCoord;
out vec4 fragColor;

float sat(float x) { return clamp(x, 0.0, 1.0); }

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float boxMask(vec2 p, vec2 b, float s) {
    vec2 d = abs(p) - b;
    float m = max(d.x, d.y);
    return 1.0 - smoothstep(0.0, s, m);
}

float digit0(vec2 p) {
    float outer = boxMask(p, vec2(0.22, 0.34), 0.02);
    float inner = boxMask(p, vec2(0.10, 0.22), 0.02);
    return max(outer - inner, 0.0);
}

float digit1(vec2 p) {
    return boxMask(p - vec2(0.0, 0.0), vec2(0.06, 0.30), 0.02);
}

float glyph(vec2 p, float one) {
    return mix(digit0(p), digit1(p), one);
}

vec3 sculkPalette(float t) {
    vec3 a = vec3(0.0157, 0.0941, 0.1255); // #041820
    vec3 b = vec3(0.0039, 0.1647, 0.2235); // #012a39
    vec3 c = vec3(0.0078, 0.2510, 0.3137); // #024050
    return mix(mix(a, b, 0.5 + 0.5 * sin(t)), c, 0.45 + 0.35 * cos(t * 0.7));
}

vec3 blur9(vec2 uv, vec2 texel, float strength) {
    vec2 d = texel * strength;
    vec3 col = texture(DiffuseSampler, uv).rgb * 0.20;
    col += texture(DiffuseSampler, uv + vec2( d.x, 0.0)).rgb * 0.10;
    col += texture(DiffuseSampler, uv + vec2(-d.x, 0.0)).rgb * 0.10;
    col += texture(DiffuseSampler, uv + vec2(0.0,  d.y)).rgb * 0.10;
    col += texture(DiffuseSampler, uv + vec2(0.0, -d.y)).rgb * 0.10;
    col += texture(DiffuseSampler, uv + d).rgb * 0.10;
    col += texture(DiffuseSampler, uv - d).rgb * 0.10;
    col += texture(DiffuseSampler, uv + vec2( d.x, -d.y)).rgb * 0.10;
    col += texture(DiffuseSampler, uv + vec2(-d.x,  d.y)).rgb * 0.10;
    return col;
}

float digitLayer(vec2 uv, vec2 scale, float speed, float seed) {
    vec2 gridUv = uv * scale + vec2(seed * 17.3, Time * speed);
    vec2 cell = floor(gridUv);
    vec2 local = fract(gridUv) - 0.5;

    float one = step(0.48, hash12(cell + seed));
    float show = step(0.22, hash12(cell * 1.71 + 4.2 + seed));
    float g = glyph(local * vec2(1.0, 1.0), one) * show;
    return g;
}

void main() {
    vec2 uv = texCoord;
    vec3 src = texture(DiffuseSampler, uv).rgb;
    float a = sat(Intensity);
    if (a <= 0.001) {
        fragColor = vec4(src, 1.0);
        return;
    }

    vec2 texel = 1.0 / max(OutSize, vec2(1.0));
    vec3 blur = blur9(uv, texel, 6.0 + 7.0 * a);

    vec2 p = uv - 0.5;
    float aspect = OutSize.x / max(OutSize.y, 1.0);
    float vignette = 1.0 - smoothstep(0.18, 0.90, length(vec2(p.x * aspect, p.y)));

    float dA = digitLayer(uv, vec2(52.0, 28.0), 5.5, 0.0);
    float dB = digitLayer(uv + vec2(0.03, -0.02), vec2(34.0, 18.0), 3.8, 1.7);
    float dC = digitLayer(uv + vec2(-0.04, 0.01), vec2(22.0, 12.0), 2.4, 2.9);
    float digits = sat(dA * 0.9 + dB * 0.7 + dC * 0.45);

    float scan = 0.85 + 0.15 * sin(uv.y * OutSize.y * 0.45 + Time * 9.0);
    float glitch = 0.92 + 0.08 * sin(uv.x * 130.0 + Time * 13.0);

    vec3 sculk = sculkPalette(Time * 0.85 + uv.y * 3.5 + uv.x * 1.7);
    vec3 darkTint = mix(blur, blur * 0.50 + sculk * 0.35, 0.55);
    vec3 col = mix(src, darkTint, 0.74 * a);

    col *= 0.74 + 0.26 * vignette;
    col += sculk * (0.20 * a);
    col += vec3(0.18, 0.78, 0.88) * digits * scan * glitch * (0.85 * a);
    col += vec3(0.02, 0.30, 0.36) * digits * 0.35 * a;

    float obstruction = 0.08 + 0.15 * (1.0 - vignette);
    col *= 1.0 - obstruction * a;

    fragColor = vec4(col, 1.0);
}
