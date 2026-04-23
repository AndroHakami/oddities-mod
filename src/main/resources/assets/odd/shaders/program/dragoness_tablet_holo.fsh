#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;
uniform vec3 CameraPosition;

uniform vec3 Origin;
uniform vec3 Normal;
uniform float PlaneHalfWidth;
uniform float PlaneHalfHeight;
uniform float PlaneThickness;
uniform float Active01;
uniform float iTime;
uniform float Intensity;
uniform vec3 FrontTint;
uniform vec3 BackTint;

in vec2 texCoord;
out vec4 fragColor;

float hash12(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

vec3 worldPos(vec3 point) {
    vec3 ndc = point * 2.0 - 1.0;
    vec4 homPos = InverseTransformMatrix * vec4(ndc, 1.0);
    vec3 viewPos = homPos.xyz / homPos.w;
    return (inverse(ModelViewMat) * vec4(viewPos, 1.0)).xyz + CameraPosition;
}

bool rayPlaneHit(vec3 rayOrigin, vec3 rayDir, float maxT,
vec3 planeOrigin, vec3 planeNormal,
vec3 right, vec3 up,
out vec2 p) {
    float denom = dot(rayDir, planeNormal);
    if (abs(denom) < 0.00012) {
        p = vec2(0.0);
        return false;
    }

    float t = dot(planeOrigin - rayOrigin, planeNormal) / denom;
    if (t <= 0.0 || t >= maxT) {
        p = vec2(0.0);
        return false;
    }

    vec3 hit = rayOrigin + rayDir * t - planeOrigin;
    p = vec2(dot(hit, right), dot(hit, up));
    return true;
}

float ringBand(float r, float radius, float thickness) {
    return 1.0 - smoothstep(thickness, thickness + 0.0028, abs(r - radius));
}

float arcMask(float ang, float count, float cutoff, float rot) {
    float v = 0.5 + 0.5 * sin(ang * count + rot);
    return smoothstep(cutoff, cutoff + 0.050, v);
}

float tickMask(float ang, float count, float rot) {
    float v = abs(sin(ang * count + rot));
    return 1.0 - smoothstep(0.020, 0.060, v);
}

float evalTechLayer(vec2 p, float maxRadius, float timeShift, float asymShift, float intensityMul) {
    float r = length(p);
    float ang = atan(p.y, p.x);

    float outerBound = 1.0 - smoothstep(maxRadius - 0.008, maxRadius + 0.014, r);
    float innerClear = smoothstep(maxRadius * 0.80, maxRadius * 0.91, r);

    float mask = outerBound * innerClear;
    if (mask <= 0.001) return 0.0;

    float rotA = iTime * 1.10 + timeShift;
    float rotB = -iTime * 1.45 + timeShift * 0.75;
    float rotC = iTime * 0.68 + timeShift * 1.05;

    float sideA = 0.55 + 0.45 * (0.5 + 0.5 * cos(ang - 0.75 + asymShift));
    float sideB = 0.55 + 0.45 * (0.5 + 0.5 * cos(ang * 0.5 + 1.45 - asymShift * 0.7));
    float sideC = 0.55 + 0.45 * (0.5 + 0.5 * cos(ang + 2.05 + asymShift * 1.1));

    float outerBase = ringBand(r, maxRadius * 0.92, 0.0070);
    float midBase   = ringBand(r, maxRadius * 0.84, 0.0060);
    float innerBase = ringBand(r, maxRadius * 0.76, 0.0050);

    float outerArc = ringBand(r, maxRadius * 0.92, 0.0100) *
    max(
    arcMask(ang, 7.0, 0.28, rotA),
    0.70 * arcMask(ang, 5.0, 0.20, rotA * 0.58 + 0.85)
    ) * sideA;

    float outerArc2 = ringBand(r, maxRadius * 0.87, 0.0080) *
    max(
    arcMask(ang, 4.0, 0.12, -rotB * 0.42 + 0.9),
    0.58 * arcMask(ang, 9.0, 0.50, rotA * 0.76 + 0.55)
    ) * sideC;

    float midArc = ringBand(r, maxRadius * 0.84, 0.0072) *
    max(
    arcMask(ang, 10.0, 0.40, rotB),
    0.54 * arcMask(ang, 6.0, 0.16, rotB * 0.55 + 1.2)
    ) * sideB;

    float innerArc = ringBand(r, maxRadius * 0.76, 0.0060) *
    max(
    arcMask(ang, 6.0, 0.46, rotC),
    0.36 * arcMask(ang, 11.0, 0.64, -rotC * 0.7 + 0.8)
    ) * sideC;

    float outerTicks = ringBand(r, maxRadius * 0.80, 0.0024) *
    tickMask(ang, 18.0, rotA * 0.95);

    float innerTicks = ringBand(r, maxRadius * 0.72, 0.0020) *
    tickMask(ang, 13.0, rotB * 0.75);

    float sideMarks =
    tickMask(ang, 4.0, 0.24 + asymShift) *
    smoothstep(maxRadius * 0.78, maxRadius * 0.82, r) *
    (1.0 - smoothstep(maxRadius * 0.82, maxRadius * 0.86, r));

    float orbitBand = ringBand(r, maxRadius * 0.835, 0.0038);
    float orbitHead = smoothstep(0.955, 0.995, 0.5 + 0.5 * cos(ang - iTime * 3.9 + asymShift * 0.25));
    float orbitTail = smoothstep(0.78, 0.97, 0.5 + 0.5 * cos(ang - iTime * 3.9 - 0.24 + asymShift * 0.25));
    float orbitLight = orbitBand * max(orbitHead, 0.46 * orbitTail);

    float orbitInner = ringBand(r, maxRadius * 0.73, 0.0030) *
    smoothstep(0.965, 0.997, 0.5 + 0.5 * cos(ang - iTime * 3.9 + 0.55 + asymShift));

    float glow =
    outerBase * 0.18 +
    midBase * 0.12 +
    innerBase * 0.08 +
    outerArc * 0.72 +
    outerArc2 * 0.42 +
    midArc * 0.54 +
    innerArc * 0.28 +
    outerTicks * 0.16 +
    innerTicks * 0.08 +
    sideMarks * 0.05 +
    orbitLight * 0.28 +
    orbitInner * 0.16;

    return glow * mask * intensityMul;
}

void main() {
    vec3 base = texture(DiffuseSampler, texCoord).rgb;
    float depth = min(texture(DepthSampler, texCoord).r, 0.9995);

    if (Active01 <= 0.001) {
        fragColor = vec4(base, 1.0);
        return;
    }

    vec3 surf = worldPos(vec3(texCoord, depth));
    vec3 rayOrigin = CameraPosition;
    vec3 viewVec = surf - rayOrigin;
    float maxT = length(viewVec);

    if (maxT <= 0.0001) {
        fragColor = vec4(base, 1.0);
        return;
    }

    vec3 rayDir = viewVec / maxT;

    vec3 n = normalize(Normal);
    vec3 upRef = abs(n.y) > 0.95 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0);
    vec3 right = normalize(cross(upRef, n));
    vec3 up = normalize(cross(n, right));

    vec2 pFront;
    vec2 pBack;

    float glowFront = 0.0;
    float glowBack = 0.0;

    if (rayPlaneHit(rayOrigin, rayDir, maxT, Origin, n, right, up, pFront)) {
        glowFront = evalTechLayer(pFront, min(PlaneHalfWidth, PlaneHalfHeight), 0.0, 0.25, 1.0);
    }

    if (rayPlaneHit(rayOrigin, rayDir, maxT, Origin - n * PlaneThickness, n, right, up, pBack)) {
        glowBack = evalTechLayer(pBack, min(PlaneHalfWidth, PlaneHalfHeight) * 0.965, 1.1, 1.65, 0.46);
    }

    glowFront *= Active01 * Intensity;
    glowBack *= Active01 * Intensity;

    float combined = glowFront + glowBack;
    if (combined <= 0.001) {
        fragColor = vec4(base, 1.0);
        return;
    }

    combined *= 0.995 + 0.005 * hash12(gl_FragCoord.xy + vec2(iTime * 17.0, -iTime * 11.0));

    vec3 col = base;
    col += FrontTint * glowFront * 0.60;
    col += BackTint * glowBack * 0.36;

    fragColor = vec4(col, 1.0);
}