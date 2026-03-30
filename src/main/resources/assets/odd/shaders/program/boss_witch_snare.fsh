#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;
uniform vec3 CameraPosition;

uniform vec3 Center;
uniform float Radius;
uniform float Warn01;
uniform float Root01;
uniform float Active01;
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

float circleBand(float r, float target, float width) {
    return 1.0 - smoothstep(width, width + 0.075, abs(r - target));
}

float boxSDF(vec2 p, vec2 b) {
    vec2 d = abs(p) - b;
    return max(d.x, d.y);
}

float runeRing(vec2 p, float ringRadius, float angleShift) {
    float best = 0.0;
    const int COUNT = 18;
    for (int i = 0; i < COUNT; i++) {
        float ang = 6.2831853 * (float(i) / float(COUNT)) + angleShift;
        vec2 dir = vec2(cos(ang), sin(ang));
        vec2 tang = vec2(-dir.y, dir.x);
        vec2 center = dir * ringRadius;
        vec2 lp = vec2(dot(p - center, tang), dot(p - center, dir));
        float sdf = boxSDF(lp, vec2(0.055, 0.18));
        best = max(best, 1.0 - smoothstep(0.0, 0.065, sdf));
    }
    return best;
}

void main() {
    vec3 base = texture(DiffuseSampler, texCoord).rgb;
    float depth = min(texture(DepthSampler, texCoord).r, 0.9995);
    vec3 ro = worldPos(vec3(texCoord, 0.0));
    vec3 surf = worldPos(vec3(texCoord, depth));
    vec3 rd = normalize(surf - ro);
    float maxDist = min(distance(ro, surf), 72.0);

    vec3 col = base;

    vec3 rel = surf - Center;
    float distXZ = length(rel.xz);
    float floorMask = 1.0 - smoothstep(0.03, 0.26, abs(rel.y));
    float arenaMask = 1.0 - smoothstep(Radius + 0.15, Radius + 0.55, distXZ);

    if (arenaMask > 0.001 && floorMask > 0.001) {
        vec2 p = rel.xz;
        float pulse = 0.5 + 0.5 * sin(iTime * 7.0 + distXZ * 2.6);
        float spin = iTime * 1.85;

        float outerRing = circleBand(distXZ, Radius, 0.07 + 0.03 * Warn01);
        float innerRing = circleBand(distXZ, mix(Radius * 0.18, Radius * 0.90, Warn01), 0.05);
        float midRing   = circleBand(distXZ, Radius * 0.72, 0.05);
        float rune      = runeRing(p, Radius - 0.35, spin);

        float swirl = 0.5 + 0.5 * sin(iTime * 4.6 - distXZ * 4.0 + atan(p.y, p.x) * 3.0);
        float fill = (1.0 - smoothstep(0.05, Radius * 0.98, distXZ)) * (0.35 + 0.65 * swirl);
        fill *= Active01 * (1.0 - Root01 * 0.55);

        vec3 teleA = vec3(0.58, 0.18, 0.88);
        vec3 teleB = vec3(0.92, 0.56, 1.00);
        vec3 teleC = vec3(1.00, 0.86, 1.00);

        vec3 ringCol = mix(teleA, teleB, pulse);
        ringCol = mix(ringCol, teleC, 0.35 + 0.35 * Warn01);

        float ringGlow = outerRing * (0.55 + 0.45 * pulse) + innerRing * 0.6 + midRing * 0.4;
        float runeGlow = rune * (0.55 + 0.45 * pulse) * Warn01;
        float fillGlow = fill * (0.18 + 0.32 * pulse);

        vec3 floorGlow = ringCol * (ringGlow + runeGlow) + vec3(0.44, 0.10, 0.70) * fillGlow;
        col = mix(col, col + floorGlow, sat(Intensity) * arenaMask * floorMask);
    }

    if (Active01 > 0.001 && maxDist > 0.001) {
        const int STEPS = 44;
        float dt = maxDist / float(STEPS);
        float jitter = hash12(texCoord * 919.0 + vec2(iTime * 0.37, -iTime * 0.13));
        float t = dt * jitter;

        float smokeSum = 0.0;
        float glowSum = 0.0;
        float emberSum = 0.0;

        float height = mix(1.0, 3.9, sat(Root01 / 0.18));
        float lifeFade = 1.0 - smoothstep(0.78, 1.0, Root01);

        for (int i = 0; i < STEPS; i++) {
            vec3 wp = ro + rd * t;
            vec3 lp = wp - Center;
            float rad = length(lp.xz);

            if (rad < Radius + 0.40 && lp.y > -0.2 && lp.y < height + 0.6) {
                float shell = exp(-pow(abs(rad - Radius * 0.82), 2.0) / 0.10);
                float inner = exp(-pow(abs(rad - Radius * 0.48), 2.0) / 0.18);

                float ang = atan(lp.z, lp.x);
                float ribbons = pow(abs(sin(ang * 6.0 + iTime * 3.5 - lp.y * 2.6)), 5.0);
                float spiral  = 0.5 + 0.5 * sin(iTime * 5.5 + ang * 4.0 - lp.y * 4.2);
                float vertical = smoothstep(0.0, 0.30, lp.y) * (1.0 - smoothstep(height * 0.55, height, lp.y));

                float smoke = max(shell * ribbons, inner * spiral * 0.65) * vertical * lifeFade;
                float glow = shell * (0.45 + 0.55 * spiral) * vertical * lifeFade;
                float ember = inner * pow(spiral, 2.0) * smoothstep(height * 0.15, height * 0.92, lp.y) * lifeFade;

                smokeSum += smoke * dt;
                glowSum += glow * dt;
                emberSum += ember * dt;
            }

            t += dt;
        }

        float smokeMask = 1.0 - exp(-smokeSum * 1.55);
        float glowMask  = 1.0 - exp(-glowSum * 4.85);
        float emberMask = 1.0 - exp(-emberSum * 6.25);

        vec3 smokeColor = vec3(0.03, 0.00, 0.05);
        vec3 glowColor  = vec3(0.42, 0.08, 0.68);
        vec3 rimColor   = vec3(0.86, 0.46, 1.00);
        vec3 emberColor = vec3(1.00, 0.82, 1.00);

        col = mix(col, smokeColor, smokeMask * 0.62 * Intensity);
        col += glowColor * glowMask * 0.55 * Intensity;
        col += rimColor  * glowMask * 0.82 * Intensity;
        col += emberColor * emberMask * 0.28 * Intensity;
    }

    float noise = hash12(gl_FragCoord.xy + vec2(iTime * 43.0, -iTime * 29.0));
    col += (noise - 0.5) / 255.0;

    fragColor = vec4(col, 1.0);
}
