#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;
uniform vec3 CameraPosition;

uniform vec3 BeamStart;
uniform vec3 BeamEnd;
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

float hash12(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
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

    const int STEPS = 84;
    float dt = maxDist / float(STEPS);
    float t = 0.5 * dt;

    float glowSum = 0.0;
    float coreSum = 0.0;
    float ringSum = 0.0;

    for (int i = 0; i < STEPS; i++) {
        vec3 p = ro + rd * t;

        float hRaw = dot(p - BeamStart, segDir);
        float h = clamp(hRaw, 0.0, segLen);
        vec3 closest = BeamStart + segDir * h;

        float distToBeam = length(p - closest);
        float along01 = h / segLen;

        float startFade = smoothstep(0.00, 0.02, along01);
        float endFade = 1.0 - smoothstep(0.88, 1.00, along01);

        float spin = sin(iTime * 28.0 - along01 * 62.0 + (p.x - p.z) * 0.25);
        float scan = 0.72 + 0.28 * sin(iTime * 19.0 - along01 * 48.0);
        float outer = exp(-(distToBeam * distToBeam) / max(Radius * Radius * 0.95, 0.0001));
        float core = exp(-(distToBeam * distToBeam) / max(Radius * Radius * 0.10, 0.0001));
        float ring = exp(-pow((distToBeam - Radius * 0.72) / max(Radius * 0.18, 0.001), 2.0));

        float body = outer * startFade * endFade * scan;
        glowSum += body * dt * 3.6;
        coreSum += core * startFade * endFade * dt * 4.4;
        ringSum += ring * (0.5 + 0.5 * spin) * dt * 1.2;

        t += dt;
    }

    float burst = 1.0 - smoothstep(0.22, 0.70, Age01);
    glowSum = clamp(glowSum * master * (1.2 + burst * 1.8), 0.0, 2.4);
    coreSum = clamp(coreSum * master * (1.0 + burst * 2.2), 0.0, 2.8);
    ringSum = clamp(ringSum * master * (1.0 + burst * 1.4), 0.0, 1.6);

    vec3 greenOuter = vec3(0.08, 0.98, 0.22);
    vec3 greenMid   = vec3(0.22, 1.00, 0.42);
    vec3 coreCol    = vec3(0.84, 1.00, 0.90);

    vec3 glow = vec3(0.0);
    glow += greenOuter * glowSum * 0.92;
    glow += greenMid   * glowSum * 0.84;
    glow += coreCol    * coreSum * 0.96;
    glow += greenMid   * ringSum * 0.52;
    glow *= 0.995 + 0.005 * hash12(gl_FragCoord.xy + vec2(iTime * 44.0, -iTime * 17.0));

    vec3 addCol = base + glow;
    vec3 screenCol = vec3(1.0) - (vec3(1.0) - base) * exp(-glow * 1.18);
    vec3 col = mix(addCol, screenCol, 0.84);

    fragColor = vec4(col, 1.0);
}
