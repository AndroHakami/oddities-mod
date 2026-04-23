#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;
uniform vec3 CameraPosition;

uniform vec3 TraceStart;
uniform vec3 TraceEnd;
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
    float a = sat(Intensity);
    if (a <= 0.001) {
        fragColor = vec4(base, 1.0);
        return;
    }

    float depth = min(texture(DepthSampler, texCoord).r, 0.9995);
    vec3 ro = worldPos(vec3(texCoord, 0.0));
    vec3 rp = worldPos(vec3(texCoord, depth));
    vec3 rd = normalize(rp - ro);

    vec3 seg = TraceEnd - TraceStart;
    float segLen = max(length(seg), 0.001);
    vec3 segDir = seg / segLen;

    float maxDist = distance(ro, rp);

    const int STEPS = 56;
    float dt = maxDist / float(STEPS);
    float t = 0.5 * dt;

    float sum = 0.0;
    float core = 0.0;

    for (int i = 0; i < STEPS; i++) {
        vec3 p = ro + rd * t;
        float h = clamp(dot(p - TraceStart, segDir), 0.0, segLen);
        vec3 nearest = TraceStart + segDir * h;

        float d = length(p - nearest);
        float beam = exp(-(d * d) / max(Radius * Radius, 0.0001));
        float beamCore = exp(-(d * d) / max(Radius * Radius * 0.18, 0.0001));
        float flicker = 0.80 + 0.20 * sin(iTime * 20.0 - h * 2.0);

        sum += beam * flicker * dt * 3.0;
        core += beamCore * dt * 2.8;

        t += dt;
    }

    sum *= a;
    core *= a;

    vec3 green = vec3(0.12, 1.0, 0.28);
    vec3 lime = vec3(0.75, 1.0, 0.60);
    vec3 col = base + green * sum * 0.8 + lime * core * 0.55;

    fragColor = vec4(col, 1.0);
}