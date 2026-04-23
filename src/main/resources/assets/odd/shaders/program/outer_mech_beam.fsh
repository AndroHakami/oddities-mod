#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;
uniform vec3 CameraPosition;

uniform vec3 BeamStart;
uniform vec3 BeamEnd;
uniform float StartRadius;
uniform float EndRadius;

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

    vec3 seg = BeamEnd - BeamStart;
    float segLen = max(length(seg), 0.001);
    vec3 segDir = seg / segLen;

    float maxDist = min(distance(ro, rp), segLen + EndRadius + 4.0);

    const int STEPS = 92;
    float dt = maxDist / float(STEPS);
    float t = 0.5 * dt;

    float bodySum = 0.0;
    float coreSum = 0.0;

    for (int i = 0; i < STEPS; i++) {
        vec3 p = ro + rd * t;

        float hRaw = dot(p - BeamStart, segDir);
        float h = clamp(hRaw, 0.0, segLen);
        vec3 nearest = BeamStart + segDir * h;

        float along01 = h / segLen;
        float radius = mix(StartRadius, EndRadius, pow(along01, 1.85));
        float dist = length(p - nearest);

        float timeFade = master;
        float startFade = smoothstep(0.0, 0.08, along01);
        float endFade = 1.0 - smoothstep(0.92, 1.0, along01);

        float shell = exp(-(dist * dist) / max(radius * radius * 0.55, 0.0001));
        float core = exp(-(dist * dist) / max(radius * radius * 0.08, 0.0001));

        float shimmer = 0.82 + 0.18 * sin(iTime * 10.0 - along01 * 18.0 + dist * 1.9);

        bodySum += shell * shimmer * startFade * endFade * timeFade * dt * 2.2;
        coreSum += core * startFade * endFade * timeFade * dt * 2.9;

        t += dt;
    }

    bodySum = clamp(bodySum, 0.0, 2.0);
    coreSum = clamp(coreSum, 0.0, 2.3);

    vec3 greenA = vec3(0.08, 1.00, 0.24);
    vec3 greenB = vec3(0.46, 1.00, 0.48);
    vec3 lime   = vec3(0.90, 1.00, 0.70);

    vec3 glow = vec3(0.0);
    glow += greenA * bodySum * 0.95;
    glow += greenB * bodySum * 0.72;
    glow += lime   * coreSum * 0.55;

    vec3 addCol = base + glow;
    vec3 screenCol = vec3(1.0) - (vec3(1.0) - base) * exp(-glow * 1.15);
    vec3 col = mix(addCol, screenCol, 0.76);

    fragColor = vec4(col, 1.0);
}