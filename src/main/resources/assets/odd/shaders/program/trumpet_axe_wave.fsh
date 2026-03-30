#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;
uniform vec3 CameraPosition;

uniform vec3 Origin;
uniform vec3 Direction;
uniform float ConeLength;
uniform float AngleCos;
uniform float Progress01;
uniform float Thickness;
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

void main() {
    vec3 base = texture(DiffuseSampler, texCoord).rgb;
    float depth = min(texture(DepthSampler, texCoord).r, 0.9995);

    // still allow a tiny screen distortion if depth is almost sky
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

    float halfAngle = acos(clamp(AngleCos, -1.0, 1.0));
    float coneTan = tan(halfAngle);
    float targetRadius = max(0.001, axial * coneTan);

    float coneMask = step(0.0, axial) * (1.0 - smoothstep(ConeLength, ConeLength + 0.75, axial));

    // broad traveling front
    float frontPos = mix(0.7, ConeLength, Progress01);
    float frontBand = 1.0 - smoothstep(0.45, 1.25, abs(axial - frontPos));

    // visible shell on the edge of the cone
    float shell = 1.0 - smoothstep(Thickness, Thickness + 0.95, abs(radial - targetRadius));

    // subtle inner volume so it reads better
    float inner = 1.0 - smoothstep(targetRadius * 0.82 + Thickness * 0.55,
    targetRadius + Thickness * 1.6,
    radial);

    // animated ripple rings along the cone
    float rings = 0.5 + 0.5 * sin(iTime * 30.0 - axial * 7.5);
    rings = mix(0.65, 1.0, rings);

    float grain = hash12(gl_FragCoord.xy * 0.15 + vec2(iTime * 91.0, -iTime * 47.0));

    float mask = coneMask * max(shell * (0.7 + 0.3 * rings), inner * 0.26);
    mask *= (0.45 + 0.55 * frontBand);
    mask *= Active01 * Intensity;
    mask *= 0.92 + 0.08 * grain;

    // stronger refraction
    vec2 radial2 = radialVec3.xz;
    if (length(radial2) < 0.0001) {
        radial2 = vec2(0.0001, 0.0);
    }

    vec2 warpDir = normalize(radial2);
    float warpStrength = (0.006 + 0.020 * frontBand + 0.010 * shell) * sat(mask);

    vec2 offsetA = warpDir * warpStrength;
    vec2 offsetB = vec2(-warpDir.y, warpDir.x) * (warpStrength * 0.35);

    vec3 warpedA = texture(DiffuseSampler, texCoord + offsetA + offsetB).rgb;
    vec3 warpedB = texture(DiffuseSampler, texCoord - offsetA * 0.65 - offsetB * 0.35).rgb;
    vec3 warped = mix(warpedA, warpedB, 0.45);

    vec3 col = mix(base, warped, sat(mask));
    fragColor = vec4(col, 1.0);
}