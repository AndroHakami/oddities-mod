#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;
uniform vec3 CameraPosition;

uniform vec3 TrailStart;
uniform vec3 TrailEnd;
uniform float Width;

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

    float trailLen = distance(TrailStart, TrailEnd);
    float maxDist = min(distance(ro, rp), trailLen + 10.0);

    const int STEPS = 76;
    float dt = maxDist / float(STEPS);
    float t = 0.5 * dt;

    float glowSum = 0.0;
    float coreSum = 0.0;

    for (int i = 0; i < STEPS; i++) {
        vec3 p = ro + rd * t;

        float along01;
        float d = segmentDistance(p, TrailStart, TrailEnd, along01);

        // Cleaner transparency-like fade over distance.
        float alphaFade = pow(1.0 - along01, 1.35);
        alphaFade *= 1.0 - smoothstep(0.78, 1.0, along01);
        alphaFade *= smoothstep(0.00, 0.025, along01);

        // Taper trail shape as it stretches out.
        float widthScale = mix(1.0, 0.24, along01);
        float localWidth = max(Width * widthScale, 0.018);

        float pulse = 0.95 + 0.05 * sin(iTime * 6.5 - along01 * 8.5);

        float outer = exp(-(d * d) / max(localWidth * localWidth * 1.10, 0.0001));
        float inner = exp(-(d * d) / max(localWidth * localWidth * 0.16, 0.0001));

        glowSum += outer * alphaFade * pulse * dt * 2.25;
        coreSum += inner * alphaFade * dt * 2.55;

        t += dt;
    }

    glowSum = clamp(glowSum * master, 0.0, 1.15);
    coreSum = clamp(coreSum * master, 0.0, 1.45);

    vec3 greenA = vec3(0.10, 1.00, 0.36);
    vec3 greenB = vec3(0.44, 1.00, 0.58);
    vec3 whiteCore = vec3(0.90, 1.00, 0.94);

    vec3 glow = vec3(0.0);
    glow += greenA * glowSum * 0.82;
    glow += greenB * glowSum * 0.64;
    glow += whiteCore * coreSum * 0.34;

    vec3 addCol = base + glow;
    vec3 screenCol = vec3(1.0) - (vec3(1.0) - base) * exp(-glow * 1.10);
    vec3 col = mix(addCol, screenCol, 0.72);

    fragColor = vec4(col, 1.0);
}