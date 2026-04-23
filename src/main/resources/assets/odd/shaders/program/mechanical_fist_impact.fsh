#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;
uniform vec3 CameraPosition;

uniform vec3 Origin;
uniform vec3 Direction;
uniform float Length;
uniform float Radius;
uniform float Progress01;
uniform float Active01;
uniform float iTime;
uniform float Intensity;

in vec2 texCoord;
out vec4 fragColor;

float sat(float x) {
    return clamp(x, 0.0, 1.0);
}

float hash12(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

vec3 worldPos(vec3 point) {
    vec3 ndc = point * 2.0 - 1.0;
    vec4 homPos = InverseTransformMatrix * vec4(ndc, 1.0);
    vec3 viewPos = homPos.xyz / homPos.w;
    return (inverse(ModelViewMat) * vec4(viewPos, 1.0)).xyz + CameraPosition;
}

mat3 basisFromDir(vec3 dir) {
    vec3 upRef = abs(dir.y) > 0.96 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0);
    vec3 side = normalize(cross(dir, upRef));
    vec3 up = normalize(cross(side, dir));
    return mat3(side, up, dir);
}

void main() {
    vec3 base = texture(DiffuseSampler, texCoord).rgb;
    float depth = min(texture(DepthSampler, texCoord).r, 0.9995);

    if (Active01 <= 0.001) {
        fragColor = vec4(base, 1.0);
        return;
    }

    vec3 surf = worldPos(vec3(texCoord, depth));
    vec3 dir = normalize(Direction);
    vec3 rel = surf - Origin;

    float axial = dot(rel, dir);
    vec3 radialVec3 = rel - dir * axial;
    float radial = length(radialVec3);

    float lengthFade = step(0.0, axial) * (1.0 - smoothstep(Length * 0.78, Length + 0.55, axial));
    float front = mix(0.15, Length * 0.92, Progress01);
    float frontBand = 1.0 - smoothstep(0.10, 0.85, abs(axial - front));

    mat3 basis = basisFromDir(dir);
    vec3 local = transpose(basis) * rel;
    float u = local.x;
    float v = local.y;
    float ring = length(vec2(u, v));

    float boltAPath = 0.22 * sin(axial * 9.5 - iTime * 34.0) + 0.10 * sin(axial * 21.0 + iTime * 27.0 + v * 8.0);
    float boltBPath = -0.20 * sin(axial * 8.2 + iTime * 29.0 + 1.9) + 0.08 * cos(axial * 17.0 - iTime * 22.0 + u * 9.0);

    float boltA = 1.0 - smoothstep(0.03, 0.14, abs(u - boltAPath));
    float boltB = 1.0 - smoothstep(0.03, 0.14, abs(v - boltBPath));

    float core = 1.0 - smoothstep(Radius * 0.10, Radius * 0.82, ring);
    float shockShell = 1.0 - smoothstep(Radius * 0.58, Radius * 1.18, abs(ring - mix(Radius * 0.18, Radius * 0.92, frontBand)));
    float glow = max(core * 0.55, max(boltA, boltB) * (0.85 + 0.15 * frontBand));

    float sparks = hash12(gl_FragCoord.xy * 0.25 + vec2(iTime * 75.0, -iTime * 41.0));
    float mask = lengthFade * (glow + shockShell * 0.55) * (0.70 + 0.30 * frontBand);
    mask *= (0.88 + 0.12 * sparks);
    mask *= Active01 * Intensity;
    mask = sat(mask);

    vec2 refractDir = ring > 0.0001 ? normalize(vec2(u, v)) : vec2(1.0, 0.0);
    vec2 offset = refractDir * (0.004 + 0.012 * frontBand) * mask;
    vec3 warped = texture(DiffuseSampler, texCoord + offset).rgb;

    vec3 hot = vec3(1.80, 0.92, 0.30);
    vec3 orange = vec3(1.25, 0.40, 0.05);
    vec3 ember = vec3(0.90, 0.22, 0.02);

    float hotMask = sat(max(boltA, boltB) * 1.3 + frontBand * 0.55);
    vec3 lightning = mix(ember, orange, sat(mask * 1.4));
    lightning = mix(lightning, hot, hotMask * sat(mask * 1.1));

    vec3 col = mix(base, warped, 0.28 * mask);
    col += lightning * (0.85 * mask + 0.35 * frontBand * hotMask);

    fragColor = vec4(col, 1.0);
}
