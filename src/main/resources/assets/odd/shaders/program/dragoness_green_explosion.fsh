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

    float expand = mix(Radius * 0.16, Radius, smoothstep(0.0, 0.45, Age01));
    float t0, t1;
    if (!raySphere(ro, rd, Center, expand, t0, t1)) {
        fragColor = vec4(base, 1.0);
        return;
    }

    float maxDist = min(distance(ro, rp), t1);
    float startT = max(t0, 0.0);
    if (maxDist <= startT) {
        fragColor = vec4(base, 1.0);
        return;
    }

    const int STEPS = 64;
    float dt = (maxDist - startT) / float(STEPS);
    float t = startT + 0.5 * dt;

    float shellSum = 0.0;
    float coreSum = 0.0;
    float ringSum = 0.0;

    float shellR = mix(Radius * 0.18, Radius, Age01);
    float shellThickness = mix(1.35, 2.10, Age01);

    for (int i = 0; i < STEPS; i++) {
        vec3 p = ro + rd * t;
        vec3 local = p - Center;
        float dist = length(local);

        float shell = exp(-pow((dist - shellR) / max(shellThickness, 0.001), 2.0));

        float innerFade = 1.0 - smoothstep(0.25, 0.92, Age01);
        float core = exp(-(dist * dist) / max(Radius * Radius * 0.24, 0.0001)) * innerFade;

        float yPulse = 0.5 + 0.5 * sin(iTime * 8.5 + local.y * 1.7 - dist * 1.1);
        float ring = exp(-pow((dist - shellR * 0.72) / max(shellThickness * 0.8, 0.001), 2.0)) * yPulse;

        shellSum += shell * dt * 2.2;
        coreSum += core * dt * 2.5;
        ringSum += ring * dt * 0.9;

        t += dt;
    }

    shellSum = clamp(shellSum * master, 0.0, 1.7);
    coreSum = clamp(coreSum * master, 0.0, 1.5);
    ringSum = clamp(ringSum * master, 0.0, 1.0);

    vec3 greenA = vec3(0.12, 1.00, 0.28);
    vec3 greenB = vec3(0.52, 1.00, 0.46);
    vec3 lime   = vec3(0.82, 1.00, 0.62);
    vec3 white  = vec3(1.0);

    vec3 glow = vec3(0.0);
    glow += greenA * shellSum * 1.00;
    glow += greenB * shellSum * 0.85;
    glow += lime   * ringSum  * 0.55;
    glow += white  * coreSum  * 0.30;
    glow += greenB * coreSum  * 0.65;

    vec3 addCol = base + glow;
    vec3 screenCol = vec3(1.0) - (vec3(1.0) - base) * exp(-glow * 1.06);
    vec3 col = mix(addCol, screenCol, 0.60);

    fragColor = vec4(col, 1.0);
}