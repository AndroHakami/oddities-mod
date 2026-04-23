#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;
uniform vec3 CameraPosition;

uniform vec3 BeamStart;
uniform vec3 BeamEnd;
uniform float Radius;

uniform float iTime;
uniform float Intensity;

in vec2 texCoord;
out vec4 fragColor;

float sat(float x) {
    return clamp(x, 0.0, 1.0);
}

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

    float maxDist = min(distance(ro, rp), segLen + 2.5);

    const int STEPS = 72;
    float dt = maxDist / float(STEPS);
    float t = 0.5 * dt;

    float glowSum = 0.0;
    float coreSum = 0.0;
    float tailSum = 0.0;

    for (int i = 0; i < STEPS; i++) {
        vec3 p = ro + rd * t;

        float hRaw = dot(p - BeamStart, segDir);
        float h = clamp(hRaw, 0.0, segLen);
        vec3 closest = BeamStart + segDir * h;

        float distToBeam = length(p - closest);
        float along01 = h / segLen;

        float startFade = smoothstep(0.00, 0.06, along01);
        float endFade = 1.0 - smoothstep(0.82, 1.00, along01);

        float pulse = 0.90 + 0.10 * sin(iTime * 18.0 - along01 * 36.0);

        float outer = exp(-(distToBeam * distToBeam) / max(Radius * Radius * 0.90, 0.0001));
        float core = exp(-(distToBeam * distToBeam) / max(Radius * Radius * 0.12, 0.0001));

        float body = outer * startFade * endFade * pulse;
        glowSum += body * dt * 3.0;
        coreSum += core * startFade * endFade * dt * 3.6;

        if (hRaw > segLen) {
            float over = hRaw - segLen;
            float tailLen = 1.2;
            float tailFade = 1.0 - smoothstep(0.0, tailLen, over);

            vec3 tailClosest = BeamEnd + segDir * over;
            float tailDist = length(p - tailClosest);

            float tailOuter = exp(-(tailDist * tailDist) / max(Radius * Radius * 1.9, 0.0001));
            tailSum += tailOuter * tailFade * dt * 1.55;
        }

        t += dt;
    }

    glowSum = clamp(glowSum * master, 0.0, 1.8);
    coreSum = clamp(coreSum * master, 0.0, 2.0);
    tailSum = clamp(tailSum * master, 0.0, 1.2);

    vec3 greenOuter = vec3(0.10, 1.00, 0.34);
    vec3 greenMid   = vec3(0.34, 1.00, 0.52);
    vec3 coreCol    = vec3(0.86, 1.00, 0.92);

    vec3 glow = vec3(0.0);
    glow += greenOuter * glowSum * 0.95;
    glow += greenMid   * glowSum * 0.75;
    glow += coreCol    * coreSum * 0.90;
    glow += greenMid   * tailSum * 0.45;

    vec3 addCol = base + glow;
    vec3 screenCol = vec3(1.0) - (vec3(1.0) - base) * exp(-glow * 1.12);
    vec3 col = mix(addCol, screenCol, 0.78);

    fragColor = vec4(col, 1.0);
}