#version 330 compatibility

uniform sampler2D DiffuseSampler;

uniform vec2 OutSize;
uniform float Time;
uniform float Intensity;

in vec2 texCoord;
out vec4 fragColor;

float sat(float x){ return clamp(x, 0.0, 1.0); }

float hash12(vec2 p){
    p = fract(p * vec2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}

// cheap layered noise
float n2(vec2 p){
    float n = 0.0;
    n += hash12(p);
    n += 0.5 * hash12(p * 2.07 + 13.1);
    n += 0.25 * hash12(p * 4.13 - 7.7);
    return n / 1.75;
}

void main() {
    vec4 src = texture(DiffuseSampler, texCoord);
    float a = sat(Intensity);
    if (a <= 0.001) { fragColor = src; return; }

    // ---------- NO WOBBLE / NO ZOOM / NO SWIRL ----------
    // Only a small *screen shake* translation, increasing with intensity.
    float shakePower = a * a; // stronger near full cast
    float hz = 55.0;          // shake frequency
    vec2 rnd = vec2(
        hash12(vec2(floor(Time * hz), 1.3)),
        hash12(vec2(floor(Time * hz), 7.9))
    ) - 0.5;

    // convert pixels -> UV. Keep it small to avoid nausea.
    float px = (0.6 + 2.2 * shakePower);           // ~0.6px .. ~2.8px
    vec2 shake = rnd * (px / max(OutSize, vec2(1.0)));

    vec2 uvS = clamp(texCoord + shake, 0.001, 0.999);
    vec3 base = texture(DiffuseSampler, uvS).rgb;

    // center-space coords for vignette + gradient behavior
    vec2 uv = texCoord * 2.0 - 1.0;
    float r = length(uv);
    float ang = atan(uv.y, uv.x);

    // vignette mask: strong edges, clean center
    float vig = smoothstep(0.18, 1.05, r);
    vig = pow(vig, 1.30);

    // animated cursed intensity (more alive)
    float grain = n2(texCoord * OutSize * 0.003 + vec2(Time * 1.7, -Time * 1.2));
    float pulse = 0.55 + 0.45 * sin(Time * 3.2 + r * 6.0 + ang * 1.2);
    float pulse2 = 0.65 + 0.35 * sin(Time * 6.8 + r * 10.0 + grain * 2.0);

    float edgeAmt = a * vig;
    edgeAmt *= (0.72 + 0.28 * grain);
    edgeAmt *= (0.70 + 0.30 * pulse);
    edgeAmt = sat(edgeAmt);

    // hotpink->purple gradient, animated + more intense
    vec3 hot = vec3(1.00, 0.20, 0.85);
    vec3 pur = vec3(0.65, 0.10, 1.00);

    float g = 0.5 + 0.5 * sin(Time * 1.10 + r * 4.0 + ang * 0.8 + grain * 2.4);
    vec3 tint = mix(hot, pur, sat(g));

    // darken edges slightly (cursed vignette) + stronger colored bloom
    vec3 darkened = base * (1.0 - 0.22 * edgeAmt);

    // bloom-ish veil grows with intensity and pulses
    float bloomAmt = edgeAmt * (0.65 + 0.55 * pulse2) * (0.55 + 0.65 * shakePower);
    vec3 glow = tint * (0.55 * bloomAmt);

    // screen blend-like
    vec3 screenCol = vec3(1.0) - (vec3(1.0) - darkened) * exp(-glow * 1.35);

    // subtle highlight sparkles on edges (cheap)
    float sp = hash12(texCoord * OutSize * 0.12 + vec2(Time * 80.0, -Time * 57.0));
    float spark = smoothstep(0.992, 1.0, sp) * edgeAmt * (0.35 + 0.65 * pulse2);
    screenCol += tint * (0.20 * spark);

    // final mix (stronger as intensity rises)
    float mixAmt = sat(0.70 * a);
    vec3 outCol = mix(base, screenCol, mixAmt);

    fragColor = vec4(outCol, src.a);
}