#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;
uniform vec3 CameraPosition;

uniform vec3 Start;
uniform vec3 End;
uniform float Radius;

uniform float Age01;
uniform float iTime;
uniform float Intensity;

in vec2 texCoord;
out vec4 fragColor;

float sat(float x){ return clamp(x,0.0,1.0); }

float hash12(vec2 p){
    p = fract(p * vec2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}
float hash11(float x){
    return fract(sin(x * 123.4567) * 43758.5453123);
}

vec3 worldPos(vec3 point) {
    vec3 ndc = point * 2.0 - 1.0;
    vec4 homPos = InverseTransformMatrix * vec4(ndc, 1.0);
    vec3 viewPos = homPos.xyz / homPos.w;
    return (inverse(ModelViewMat) * vec4(viewPos, 1.0)).xyz + CameraPosition;
}

float distToSeg(vec3 p, vec3 a, vec3 b, out float s01) {
    vec3 ab = b - a;
    float len2 = max(dot(ab, ab), 1e-6);
    float t = dot(p - a, ab) / len2;
    t = clamp(t, 0.0, 1.0);
    s01 = t;
    vec3 c = a + ab * t;
    return length(p - c);
}

void buildBasis(vec3 T, out vec3 N1, out vec3 N2){
    vec3 up = vec3(0.0, 1.0, 0.0);
    if (abs(dot(T, up)) > 0.93) up = vec3(1.0, 0.0, 0.0);
    N1 = normalize(cross(T, up));
    N2 = normalize(cross(T, N1));
}

vec2 randDir2(float k){
    float a = hash11(k * 17.0 + 3.1) * 6.2831853;
    return vec2(cos(a), sin(a));
}

void main() {
    vec3 base = texture(DiffuseSampler, texCoord).rgb;

    float A = sat(Intensity);
    if (A <= 0.001) { fragColor = vec4(base, 1.0); return; }

    float depth = texture(DepthSampler, texCoord).r;
    depth = min(depth, 0.9995);

    vec3 ro = worldPos(vec3(texCoord, 0.0));
    vec3 rp = worldPos(vec3(texCoord, depth));
    vec3 rd = normalize(rp - ro);

    float maxDist = min(distance(ro, rp), 90.0);

    // envelope
    float fadeIn  = smoothstep(0.00, 0.08, Age01);
    float fadeOut = smoothstep(0.00, 0.18, 1.0 - Age01);
    float master = A * fadeIn * fadeOut;

    vec3 AB = End - Start;
    float L = length(AB);
    if (L < 1e-4) { fragColor = vec4(base, 1.0); return; }

    vec3 T = AB / L;
    vec3 N1, N2;
    buildBasis(T, N1, N2);

    vec3 PURP  = vec3(0.78, 0.28, 1.00);
    vec3 BLUE  = vec3(0.08, 0.16, 0.95);
    vec3 WHITE = vec3(1.00, 1.00, 1.00);

    float thick = max(Radius, 0.55);

    // ===== Dissipate (soften + bloom out) near end =====
    float kill = smoothstep(0.70, 1.0, Age01); // 0 -> 1 near the end

    // core shrinks, glow expands
    float rCore = thick * 0.48 * mix(1.0, 0.55, kill);
    float rGlow = thick * 1.60 * mix(1.0, 1.35, kill);

    const int STEPS = 40;
    float dt = maxDist / float(STEPS);

    float j = hash12(texCoord * 917.0 + vec2(iTime * 0.17, -iTime * 0.11));
    float t = j * dt;

    vec3 accum = vec3(0.0);
    float accD = 0.0;

    // more kinks (unpredictable) but still cheap
    float SEG_COUNT = 14.0;

    for (int i = 0; i < STEPS; i++) {
        vec3 p = ro + rd * t;

        float s01;
        float dSeg = distToSeg(p, Start, End, s01);

        if (dSeg < (rGlow * 2.8)) {
            float taper = smoothstep(0.0, 0.10, s01) * smoothstep(0.0, 0.10, 1.0 - s01);

            float seg = floor(s01 * SEG_COUNT);
            float seg01 = fract(s01 * SEG_COUNT);
            float segR = hash11(seg * 23.0 + 5.0);

            // base direction lerp
            vec2 dA = randDir2(seg + 10.0);
            vec2 dB = randDir2(seg + 11.0);
            float w = seg01 * seg01 * (3.0 - 2.0 * seg01);
            vec2 dir2 = normalize(mix(dA, dB, w) + 1e-4);

            // extra sub-kinks (more unpredictable)
            float sub = floor(seg01 * 3.0);
            vec2 kink = randDir2(seg * 7.0 + sub * 13.0 + 100.0);

            // tiny time drift (slow) so it feels alive
            float drift = (hash11(seg * 9.0 + floor(iTime * 2.0)) - 0.5) * 0.55;

            float corner = smoothstep(0.0, 0.18, seg01) * smoothstep(0.0, 0.18, 1.0 - seg01);
            dir2 = normalize(dir2 + kink * (0.55 + 0.55 * segR) * (1.0 - corner) + vec2(drift, -drift) * 0.22);

            // amplitude varies per segment; slightly calms down as it dies
            float amp = thick * (0.28 + 0.72 * (1.0 - corner));
            amp *= (0.65 + 0.85 * segR);
            amp *= (0.92 + 0.08 * sin(iTime * 10.0 + seg * 1.7));
            amp *= mix(1.0, 0.82, kill);

            vec3 c = mix(Start, End, s01);
            vec3 boltP = c + (N1 * dir2.x + N2 * dir2.y) * amp;

            // blocky cross-section
            vec3 v = p - boltP;
            vec2 lp = vec2(dot(v, N1), dot(v, N2));
            float dBox = max(abs(lp.x), abs(lp.y));

            // dash + flicker, then smooth out as it dissipates
            float dash = smoothstep(0.46, 0.0, abs(fract(s01 * 7.0 - iTime * 12.0 + hash11(seg*9.0)) - 0.5));
            dash = mix(dash, 1.0, kill * 0.55); // end = less “dashed”, more haze
            float fl = 0.82 + 0.18 * sin(iTime * 16.0 + seg * 2.2);
            fl = mix(fl, 0.95, kill * 0.45);

            float core = smoothstep(rCore, 0.0, dBox);
            core *= core; core *= core;

            float glow = smoothstep(rGlow, 0.0, dBox);
            float bloom = glow * glow;

            vec3 grad = mix(PURP, BLUE, sat(s01 + 0.10 * sin(iTime * 2.0)));

            // shift energy from core -> colored bloom as it dies
            float coreW  = mix(0.38, 0.10, kill);  // less white at end
            float glowW  = mix(0.65, 0.85, kill);
            float bloomW = mix(0.80, 1.20, kill);

            vec3 col = grad * (glowW * glow + bloomW * bloom) + WHITE * (coreW * core);

            float dens = (0.55 * core + 0.62 * glow + 0.52 * bloom) * dash * fl * taper;

            accum += col * dens * dt;
            accD  += dens * dt;

            if (accD > 1.15) break;
        }

        t += dt;
    }

    float alpha = 1.0 - exp(-accD * 3.8);
    alpha = sat(alpha) * master;

    vec3 glowCol = accum / max(accD, 1e-5);

    vec3 screenCol = vec3(1.0) - (vec3(1.0) - base) * exp(-glowCol * 1.12);
    vec3 col = mix(base, screenCol, alpha);

    fragColor = vec4(col, 1.0);
}