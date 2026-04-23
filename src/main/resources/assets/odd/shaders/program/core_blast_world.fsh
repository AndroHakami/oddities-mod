#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;
uniform vec3 CameraPosition;
uniform vec3 Center;
uniform float Radius;
uniform float Age01;
uniform float iTime;
uniform float Intensity;
uniform float Huge;

in vec2 texCoord;
out vec4 fragColor;

float sat(float x) { return clamp(x, 0.0, 1.0); }

vec3 worldPos(vec3 point) {
    vec3 ndc = point * 2.0 - 1.0;
    vec4 homPos = InverseTransformMatrix * vec4(ndc, 1.0);
    vec3 viewPos = homPos.xyz / homPos.w;
    return (inverse(ModelViewMat) * vec4(viewPos, 1.0)).xyz + CameraPosition;
}

bool raySphere(vec3 ro, vec3 rd, vec3 c, float r, out float t0, out float t1) {
    vec3 oc = ro - c;
    float b = dot(oc, rd);
    float cTerm = dot(oc, oc) - r * r;
    float h = b * b - cTerm;
    if (h < 0.0) return false;
    h = sqrt(h);
    t0 = -b - h;
    t1 = -b + h;
    return true;
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float boxMask(vec2 p, vec2 b, float s) {
    vec2 d = abs(p) - b;
    return 1.0 - smoothstep(0.0, s, max(d.x, d.y));
}

float glyph(vec2 p, float one) {
    float line = boxMask(p, vec2(0.05, 0.24), 0.02);
    float outer = boxMask(p, vec2(0.18, 0.26), 0.02);
    float inner = boxMask(p, vec2(0.10, 0.16), 0.02);
    float ring = max(outer - inner, 0.0);
    return mix(ring, line, one);
}

void main() {
    vec3 base = texture(DiffuseSampler, texCoord).rgb;
    float master = sat(Intensity);
    if (master <= 0.001) {
        fragColor = vec4(base, 1.0);
        return;
    }

    float depth = min(texture(DepthSampler, texCoord).r, 0.9995);
    vec3 ro = worldPos(vec3(texCoord, 0.0));
    vec3 rp = worldPos(vec3(texCoord, depth));
    vec3 rd = normalize(rp - ro);

    float t0, t1;
    float maxR = mix(Radius * 0.14, Radius * (1.0 + 0.12 * Huge), smoothstep(0.0, 0.45, Age01));
    if (!raySphere(ro, rd, Center, maxR, t0, t1)) {
        fragColor = vec4(base, 1.0);
        return;
    }

    float maxDist = min(distance(ro, rp), t1);
    float startT = max(t0, 0.0);
    if (maxDist <= startT) {
        fragColor = vec4(base, 1.0);
        return;
    }

    const int STEPS = 48;
    float dt = (maxDist - startT) / float(STEPS);
    float t = startT + 0.5 * dt;

    float shellSum = 0.0;
    float coreSum = 0.0;
    float bitSum = 0.0;
    float shockSum = 0.0;

    float shellR = mix(Radius * 0.18, Radius * (1.0 + 0.08 * Huge), Age01);
    float shellThickness = mix(1.10, 2.00 + Huge * 0.35, Age01);

    for (int i = 0; i < STEPS; i++) {
        vec3 p = ro + rd * t;
        vec3 local = p - Center;
        float dist = length(local);

        float shell = exp(-pow((dist - shellR) / max(shellThickness, 0.001), 2.0));
        float shock = exp(-pow((dist - shellR * 0.62) / max(shellThickness * 0.7, 0.001), 2.0));

        float innerFade = 1.0 - smoothstep(0.18, 0.90, Age01);
        float core = exp(-(dist * dist) / max(Radius * Radius * 0.18, 0.0001)) * innerFade;

        vec2 gridUv = local.xz / max(Radius, 0.001) * 7.5 + vec2(0.0, iTime * 6.0 - local.y * 0.8);
        vec2 cell = floor(gridUv);
        vec2 fc = fract(gridUv) - 0.5;
        float bit = glyph(fc, step(0.5, hash12(cell + floor(local.y * 4.0)))) * step(0.33, hash12(cell * 1.7 + 2.4));
        bit *= shell;

        shellSum += shell * dt * 1.95;
        coreSum += core * dt * 2.20;
        bitSum += bit * dt * 2.80;
        shockSum += shock * dt * (1.1 + 0.3 * sin(iTime * 7.0 + local.y * 1.2));

        t += dt;
    }

    shellSum = clamp(shellSum * master, 0.0, 1.6);
    coreSum = clamp(coreSum * master, 0.0, 1.2);
    bitSum = clamp(bitSum * master, 0.0, 1.4);
    shockSum = clamp(shockSum * master, 0.0, 1.2);

    vec3 c0 = vec3(0.0157, 0.0941, 0.1255);
    vec3 c1 = vec3(0.0039, 0.1647, 0.2235);
    vec3 c2 = vec3(0.0078, 0.2510, 0.3137);
    vec3 accent = vec3(0.26, 0.90, 0.95);
    vec3 white = vec3(0.92, 1.0, 1.0);

    vec3 glow = vec3(0.0);
    glow += c2 * shellSum * 0.95;
    glow += c1 * shockSum * 0.72;
    glow += accent * bitSum * 0.95;
    glow += white * coreSum * 0.28;
    glow += c0 * coreSum * 0.55;

    vec3 addCol = base + glow;
    vec3 screenCol = vec3(1.0) - (vec3(1.0) - base) * exp(-glow * 1.1);
    vec3 col = mix(addCol, screenCol, 0.55);

    fragColor = vec4(col, 1.0);
}
