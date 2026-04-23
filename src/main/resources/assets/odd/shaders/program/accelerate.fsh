#version 330 compatibility

uniform sampler2D DiffuseSampler;

uniform float Time;
uniform vec2 OutSize;

uniform float SpeedIntensity; // 0..1
uniform float FlashIntensity; // 0..1
uniform float FlashTime;      // seconds since trigger

in vec2 texCoord;
out vec4 fragColor;

#define PI 3.141592653589793
#define TAU 6.283185307179586

float sat(float x) { return clamp(x, 0.0, 1.0); }

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}

// screen-space bolt in polar-ish space (good for crackly streak accents)
float bolt2D(vec2 uv, float seed, float t) {
    float y = uv.y;

    float p1 = sin(y * (10.0 + 3.0 * sin(seed)) + t * 18.0 + seed * 12.3) * 0.18;
    float p2 = sin(y * (22.0 + 5.0 * cos(seed)) + t * 11.0 + seed * 7.7) * 0.07;
    float path = p1 + p2;

    float d = abs(uv.x - path);
    float core = smoothstep(0.030, 0.0, d);
    float glow = smoothstep(0.11, 0.0, d) * 0.55;

    float br = hash(vec2(y * 42.0, seed * 10.0) + t * 2.2);
    core *= smoothstep(0.10, 0.55, br);

    return core + glow;
}

void main() {
    vec4 src = texture(DiffuseSampler, texCoord);
    vec3 col = src.rgb;

    vec2 uv = texCoord * 2.0 - 1.0;      // -1..1
    float r = length(uv);                // 0..~1.4
    vec2 nUv = uv / max(r, 1e-5);

    float a = sat(SpeedIntensity);
    float f = sat(FlashIntensity);

    /* ===================== SPEED: SLIPSTREAM + CRACKLY LIGHTNING ===================== */
    if (a > 0.001) {
        float mask = smoothstep(0.12, 0.98, r);
        mask *= mask;

        vec2 dir = nUv;
        float blurAmt = (0.004 + 0.010 * a) * mask;

        vec3 b0 = texture(DiffuseSampler, texCoord + dir * blurAmt).rgb;
        vec3 b1 = texture(DiffuseSampler, texCoord + dir * blurAmt * 2.0).rgb;
        vec3 blur = (b0 + b1) * 0.5;

        col = mix(col, blur, (0.18 + 0.18 * a) * mask);

        float ang = atan(uv.y, uv.x);
        float ang01 = (ang / TAU) + 0.5;

        float N = mix(12.0, 26.0, a);
        float spokeCell = floor(ang01 * N);
        float spokeLocal = fract(ang01 * N);

        float spokeW = mix(0.070, 0.030, a);
        float spoke = smoothstep(spokeW, 0.0, abs(spokeLocal - 0.5));

        float dashFreq = mix(8.0, 20.0, a);
        float dashSpd  = mix(9.0, 28.0, a);
        float dashNoise = hash(vec2(spokeCell, 13.7)) * 1.7;
        float dash = smoothstep(0.45, 0.0, abs(fract(r * dashFreq - Time * dashSpd + dashNoise) - 0.5));

        float lines = spoke * dash;

        float crack = sin((r * 38.0) + Time * (22.0 + 24.0 * a) + spokeCell * 0.9);
        crack = 0.5 + 0.5 * crack;
        float crack2 = hash(uv * OutSize * 0.002 + vec2(Time * 9.0, Time * 7.0));
        float crackly = sat(crack * 0.7 + crack2 * 0.8);
        lines *= (0.45 + 0.55 * crackly);

        // green slip tint + pale mint lightning highlight
        vec3 green = vec3(0.26, 1.00, 0.40);
        vec3 mint  = vec3(0.92, 1.00, 0.94);

        float pulse = 0.85 + 0.15 * sin(Time * 7.0);
        float k = a * mask * pulse;

        col += green * (0.42 * k);
        col += green * (0.55 * lines) * k;

        float elec = sat((lines * crackly) * (0.35 + 0.65 * a));
        col += mint * (0.55 * elec) * k;

        float n = hash(texCoord * OutSize + vec2(Time * 40.0, Time * 23.0));
        col += (n - 0.5) * (0.028 * k);
    }

    /* ===================== RECALL FLASH: LIGHTNING RUSHES TO EDGES ===================== */
    if (f > 0.001) {
        float t = FlashTime;

        float env = exp(-t * 10.5) * f;

        float waveR = t * 2.6;
        float front = smoothstep(waveR, waveR - 0.28, r);
        front *= front;

        vec2 uv2 = uv * (1.0 + t * 1.8);

        float b = 0.0;
        b += bolt2D(uv2, 1.1, t);
        b += bolt2D(uv2 + vec2(0.28, 0.0), 2.7, t);
        b += bolt2D(uv2 + vec2(-0.34, 0.0), 4.3, t);

        float core = smoothstep(0.90, 0.05, r);
        core *= core;

        vec3 mint = vec3(0.88, 1.00, 0.92);
        vec3 green = vec3(0.18, 0.95, 0.34);

        col += mint * (1.35 * b) * env * front;
        col += green * (0.95 * b) * env * front;

        col += mint * (0.55 * core) * env;
        col += green * (0.25 * core) * env;

        float n = hash(texCoord * OutSize + vec2(Time * 90.0, Time * 61.0));
        float sparks = smoothstep(0.984, 1.0, n) * (0.25 + 0.75 * front);
        col += (mint * 0.9 + green * 0.6) * (0.85 * sparks) * env;
    }

    fragColor = vec4(col, src.a);
}