#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;
uniform vec3 CameraPosition;

uniform vec3 Center;
uniform vec3 TrailStart;
uniform vec3 TrailEnd;
uniform float Radius;
uniform float Age01;
uniform float iTime;
uniform float Intensity;

in vec2 texCoord;
out vec4 fragColor;

float sat(float x) { return clamp(x, 0.0, 1.0); }

vec3 worldPos(vec3 point) {
    vec3 ndc = point * 2.0 - 1.0;
    vec4 homPos = InverseTransformMatrix * vec4(ndc, 1.0);
    vec3 viewPos = homPos.xyz / homPos.w;
    return (inverse(ModelViewMat) * vec4(viewPos, 1.0)).xyz + CameraPosition;
}

float segmentDistance(vec3 p, vec3 a, vec3 b, out float h01) {
    vec3 ab = b - a;
    float den = max(dot(ab, ab), 0.0001);
    h01 = clamp(dot(p - a, ab) / den, 0.0, 1.0);
    vec3 q = a + ab * h01;
    return length(p - q);
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
    float maxDist = distance(ro, rp);

    const int STEPS = 72;
    float dt = maxDist / float(STEPS);
    float t = 0.5 * dt;

    float trailGlow = 0.0;
    float trailCore = 0.0;
    float capGlow = 0.0;
    float capCore = 0.0;

    for (int i = 0; i < STEPS; i++) {
        vec3 p = ro + rd * t;

        float along01;
        float d = segmentDistance(p, TrailStart, TrailEnd, along01);
        float trailFade = pow(1.0 - along01, 1.8);
        trailFade *= 1.0 - smoothstep(0.72, 1.0, along01);

        float widthScale = mix(0.95, 0.22, along01);
        float localWidth = max(Radius * 0.42 * widthScale, 0.04);
        float outer = exp(-(d * d) / max(localWidth * localWidth * 1.75, 0.0001));
        float inner = exp(-(d * d) / max(localWidth * localWidth * 0.24, 0.0001));

        trailGlow += outer * trailFade * dt * 1.6;
        trailCore += inner * trailFade * dt * 1.1;

        vec3 local = p - Center;
        float dist = length(local);
        float bottomBias = smoothstep(0.15, 0.95, dot(normalize(local + vec3(0.0, -0.35, 0.0)), vec3(0.0, -1.0, 0.0)));
        float shell = exp(-pow((dist - Radius) / max(Radius * 0.22, 0.001), 2.0));
        float core = exp(-(dist * dist) / max(Radius * Radius * 0.42, 0.0001));

        capGlow += shell * bottomBias * dt * 2.0;
        capCore += core * bottomBias * dt * 0.85;

        t += dt;
    }

    float t0, t1;
    if (raySphere(ro, rd, Center, Radius * 1.12, t0, t1)) {
        float sphereBoost = 1.0 - smoothstep(0.35, 1.0, Age01);
        capGlow += sphereBoost * 0.10;
    }

    trailGlow = clamp(trailGlow * master, 0.0, 0.85);
    trailCore = clamp(trailCore * master, 0.0, 0.70);
    capGlow = clamp(capGlow * master, 0.0, 1.10);
    capCore = clamp(capCore * master, 0.0, 0.60);

    vec3 greenOuter = vec3(0.08, 0.95, 0.24);
    vec3 greenMid   = vec3(0.20, 1.00, 0.40);
    vec3 greenHot   = vec3(0.74, 1.00, 0.70);

    vec3 glow = vec3(0.0);
    glow += greenOuter * trailGlow * 0.60;
    glow += greenMid   * trailCore * 0.24;
    glow += greenMid   * capGlow * 0.92;
    glow += greenHot   * capCore * 0.32;

    vec3 addCol = base + glow;
    vec3 screenCol = vec3(1.0) - (vec3(1.0) - base) * exp(-glow * 1.06);
    vec3 col = mix(addCol, screenCol, 0.58);

    fragColor = vec4(col, 1.0);
}
