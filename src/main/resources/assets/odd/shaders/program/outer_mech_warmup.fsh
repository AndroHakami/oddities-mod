#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;
uniform vec3 CameraPosition;

uniform vec3 Center;
uniform float Radius;
uniform float Intensity;
uniform float iTime;

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

    float maxDist = distance(ro, rp);

    const int STEPS = 56;
    float dt = maxDist / float(STEPS);
    float t = 0.5 * dt;

    float shellSum = 0.0;
    float coreSum = 0.0;

    for (int i = 0; i < STEPS; i++) {
        vec3 p = ro + rd * t;
        vec3 d = p - Center;
        float r = length(d);

        float pulse = 0.82 + 0.18 * sin(iTime * 9.0 + r * 10.0);
        float shell = exp(-pow((r - Radius) / max(Radius * 0.24, 0.001), 2.0));
        float core  = exp(-(r * r) / max(Radius * Radius * 0.28, 0.001));

        shellSum += shell * pulse * dt * 1.9;
        coreSum  += core  * dt * 0.9;

        t += dt;
    }

    shellSum = clamp(shellSum * master, 0.0, 1.6);
    coreSum  = clamp(coreSum  * master, 0.0, 1.1);

    vec3 greenA = vec3(0.10, 1.00, 0.32);
    vec3 greenB = vec3(0.55, 1.00, 0.50);
    vec3 whiteG = vec3(0.92, 1.00, 0.96);

    vec3 glow = vec3(0.0);
    glow += greenA * shellSum * 1.1;
    glow += greenB * shellSum * 0.8;
    glow += whiteG * coreSum * 0.35;

    vec3 addCol = base + glow;
    vec3 screenCol = vec3(1.0) - (vec3(1.0) - base) * exp(-glow * 1.15);
    vec3 col = mix(addCol, screenCol, 0.72);

    fragColor = vec4(col, 1.0);
}