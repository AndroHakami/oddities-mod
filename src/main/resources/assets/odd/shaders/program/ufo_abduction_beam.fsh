#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;
uniform vec3 CameraPosition;

uniform vec3 BeamOrigin;
uniform float BottomY;
uniform float BeamLength;
uniform float TopRadius;
uniform float BottomRadius;

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

float coneRadiusAt(float y) {
    float along01 = sat((BeamOrigin.y - y) / max(BeamLength, 0.001));
    return mix(TopRadius, BottomRadius, along01);
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

    float maxDist = min(distance(ro, rp), 100.0);

    const int STEPS = 56;
    float dt = maxDist / float(STEPS);
    float jitter = hash12(texCoord * 911.0 + vec2(iTime * 0.14, -iTime * 0.11));
    float t = jitter * dt;

    float beamSum = 0.0;
    float ringSum = 0.0;

    for (int i = 0; i < STEPS; i++) {
        vec3 p = ro + rd * t;

        if (p.y <= BeamOrigin.y && p.y >= BottomY) {
            float along01 = sat((BeamOrigin.y - p.y) / max(BeamLength, 0.001));
            float radius = coneRadiusAt(p.y);

            vec2 d = p.xz - BeamOrigin.xz;
            float radial = length(d);

            if (radial < radius) {
                float outer = 1.0 - smoothstep(radius * 0.70, radius, radial);
                float core = 1.0 - smoothstep(0.0, radius * 0.52, radial);

                float topFade = smoothstep(0.0, 0.08, 1.0 - along01);
                float bottomFade = smoothstep(0.0, 0.06, along01);

                float spotlight = outer * topFade * bottomFade;
                float flow = 0.82 + 0.18 * sin(iTime * 3.2 - along01 * 10.0 + radial * 2.5);

                beamSum += spotlight * (0.22 + core * 0.85) * flow * dt * 1.85;

                float ringPos = fract(along01 * 8.0 - iTime * 0.85);
                float ring = exp(-pow((ringPos - 0.10) / 0.08, 2.0));
                ring *= smoothstep(radius * 0.48, radius * 0.95, radial);
                ring *= outer * 1.35;

                ringSum += ring * dt * 3.2;
            }
        }

        t += dt;
    }

    beamSum = clamp(beamSum * master, 0.0, 1.25);
    ringSum = clamp(ringSum * master, 0.0, 1.40);

    vec3 beamCol = vec3(0.12, 1.00, 0.42);
    vec3 coreCol = vec3(0.70, 1.00, 0.82);
    vec3 ringCol = vec3(1.0);

    vec3 glow = vec3(0.0);
    glow += beamCol * beamSum * 1.50;
    glow += coreCol * beamSum * 0.55;
    glow += ringCol * ringSum * 1.25;

    vec3 addCol = base + glow;
    vec3 screenCol = vec3(1.0) - (vec3(1.0) - base) * exp(-glow * 1.1);
    vec3 col = mix(addCol, screenCol, 0.58);

    fragColor = vec4(col, 1.0);
}