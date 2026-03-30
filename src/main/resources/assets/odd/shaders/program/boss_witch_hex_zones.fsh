#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;
uniform vec3 CameraPosition;

uniform vec3 ArenaCenter;
uniform float ArenaRadius;
uniform float HazardParity;
uniform float TileSize;
uniform float Charge01;
uniform float Flame01;
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

float checkerParity(vec2 worldXZ) {
    vec2 cell = floor(vec2(floor(worldXZ.x) - floor(ArenaCenter.x), floor(worldXZ.y) - floor(ArenaCenter.z)) / TileSize);
    return mod(cell.x + cell.y, 2.0);
}

float isHazard(vec2 worldXZ) {
    return 1.0 - step(0.5, abs(checkerParity(worldXZ) - HazardParity));
}

float squareMask(vec2 local, float halfSize, float feather) {
    vec2 d = abs(local) - vec2(halfSize);
    float box = max(d.x, d.y);
    return 1.0 - smoothstep(0.0, feather, box);
}

void main() {
    vec3 base = texture(DiffuseSampler, texCoord).rgb;
    float depth = min(texture(DepthSampler, texCoord).r, 0.9995);
    vec3 ro = worldPos(vec3(texCoord, 0.0));
    vec3 surf = worldPos(vec3(texCoord, depth));
    vec3 rd = normalize(surf - ro);
    float maxDist = min(distance(ro, surf), 96.0);

    vec3 col = base;
    float radiusMask = 1.0 - smoothstep(ArenaRadius - 0.9, ArenaRadius + 0.35, length(surf.xz - ArenaCenter.xz));

    if (radiusMask > 0.001) {
        float hazard = isHazard(surf.xz);
        vec2 cell = fract((surf.xz - floor(ArenaCenter.xz)) / TileSize);
        vec2 centered = cell - 0.5;
        float edge = min(min(cell.x, 1.0 - cell.x), min(cell.y, 1.0 - cell.y));
        float border = 1.0 - smoothstep(0.04, 0.16, edge);
        float surfaceMask = 1.0 - smoothstep(0.08, 1.00, abs(surf.y - ArenaCenter.y));

        float pulse = 0.55 + 0.45 * sin(iTime * 9.0 + (surf.x + surf.z) * 2.4);
        float sweep = 0.5 + 0.5 * sin((surf.x - surf.z) * 7.5 - iTime * 8.5);
        float rune = 0.5 + 0.5 * sin(iTime * 5.2 + dot(centered, centered) * 42.0);

        float teleFill = hazard * surfaceMask * radiusMask * (0.28 + 0.36 * pulse + 0.22 * sweep + 0.10 * rune) * (1.0 - Flame01);
        float teleBorder = radiusMask * surfaceMask * (0.14 + 0.52 * hazard) * border * (0.45 + 0.55 * Charge01) * (1.0 - Flame01 * 0.85);

        vec3 safeGrid = vec3(0.11, 0.04, 0.16) * teleBorder * 0.35;
        vec3 teleA = vec3(0.58, 0.18, 0.86);
        vec3 teleB = vec3(0.86, 0.34, 1.00);
        vec3 teleC = vec3(1.00, 0.78, 1.00);
        vec3 teleColor = mix(teleA, teleB, sweep);
        teleColor = mix(teleColor, teleC, border * 0.45 + Charge01 * 0.18);

        vec3 teleGlow = safeGrid + teleColor * (teleFill * (0.42 + 0.58 * Charge01) + teleBorder);
        col = mix(col, col + teleGlow, sat(Intensity));
    }

    if (Flame01 > 0.001 && maxDist > 0.001) {
        const int STEPS = 36;
        float dt = maxDist / float(STEPS);
        float jitter = hash12(texCoord * 911.3 + vec2(iTime * 0.37, -iTime * 0.19));
        float t = dt * jitter;

        float smokeSum = 0.0;
        float glowSum = 0.0;
        float tipSum = 0.0;

        float eruptHeight = mix(0.35, 5.25, sat(Flame01 / 0.32));
        float lifeFade = 1.0 - smoothstep(0.70, 1.00, Flame01);

        for (int i = 0; i < STEPS; i++) {
            vec3 wp = ro + rd * t;
            vec2 rel = wp.xz - ArenaCenter.xz;
            float arena = 1.0 - smoothstep(ArenaRadius - 0.6, ArenaRadius + 0.8, length(rel));

            if (arena > 0.001) {
                float hazard = isHazard(wp.xz);
                if (hazard > 0.5) {
                    vec2 local = fract((wp.xz - floor(ArenaCenter.xz)) / TileSize) - 0.5;
                    float y = wp.y - ArenaCenter.y;
                    float core = squareMask(local, 0.16, 0.10);
                    float shell = squareMask(local, 0.28, 0.16) - core * 0.55;
                    float vertical = smoothstep(-0.18, 0.18, y) * (1.0 - smoothstep(eruptHeight * 0.55, eruptHeight, y));
                    float twist = 0.5 + 0.5 * sin(iTime * 10.0 - y * 4.8 + (local.x - local.y) * 14.0);
                    float lick = pow(0.5 + 0.5 * sin(iTime * 7.0 + y * 3.2 + atan(local.y, local.x) * 3.0), 2.0);
                    float spark = 0.5 + 0.5 * sin(iTime * 13.0 - y * 8.0 + dot(local, local) * 160.0);

                    float smoke = core * vertical * mix(0.50, 1.00, twist * lick);
                    float glow = shell * vertical * (0.50 + 0.50 * twist);
                    float tip = shell * vertical * smoothstep(eruptHeight * 0.25, eruptHeight * 0.95, y) * spark;

                    smokeSum += smoke * dt * lifeFade;
                    glowSum += glow * dt * lifeFade;
                    tipSum += tip * dt * lifeFade;
                }
            }
            t += dt;
        }

        float smokeMask = 1.0 - exp(-smokeSum * 1.85);
        float glowMask = 1.0 - exp(-glowSum * 5.25);
        float tipMask = 1.0 - exp(-tipSum * 7.25);

        vec3 smokeColor = vec3(0.015, 0.000, 0.030);
        vec3 emberColor = vec3(0.42, 0.10, 0.70);
        vec3 rimColor = vec3(0.72, 0.22, 1.00);
        vec3 tipColor = vec3(0.96, 0.74, 1.00);

        col = mix(col, smokeColor, smokeMask * 0.72 * Intensity);
        col += emberColor * glowMask * 0.60 * Intensity;
        col += rimColor * glowMask * 0.95 * Intensity;
        col += tipColor * tipMask * 0.45 * Intensity;
    }

    float noise = hash12(gl_FragCoord.xy + vec2(iTime * 60.0, -iTime * 33.0));
    col += (noise - 0.5) / 255.0;

    fragColor = vec4(col, 1.0);
}
