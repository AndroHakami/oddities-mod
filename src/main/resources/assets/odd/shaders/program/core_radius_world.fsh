#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;
uniform vec3 CameraPosition;
uniform vec3 Center;
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
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float boxMask(vec2 p, vec2 b, float s) {
    vec2 d = abs(p) - b;
    return 1.0 - smoothstep(0.0, s, max(d.x, d.y));
}

float digit(vec2 p, float one) {
    float oneMask = boxMask(p, vec2(0.05, 0.24), 0.02);
    float outer = boxMask(p, vec2(0.18, 0.26), 0.02);
    float inner = boxMask(p, vec2(0.10, 0.16), 0.02);
    float zeroMask = max(outer - inner, 0.0);
    return mix(zeroMask, oneMask, one);
}

void main() {
    vec3 base = texture(DiffuseSampler, texCoord).rgb;
    float depth = min(texture(DepthSampler, texCoord).r, 0.9995);
    vec3 wp = worldPos(vec3(texCoord, depth));

    float heightFade = 1.0 - smoothstep(0.03, 0.16, abs(wp.y - Center.y));
    float A = sat(Intensity) * heightFade;
    if (A <= 0.001) {
        fragColor = vec4(base, 1.0);
        return;
    }

    vec2 rel = wp.xz - Center.xz;
    float dist = length(rel);
    float radiusPulse = Radius * (0.985 + 0.015 * sin(iTime * 5.5));

    float outerRing = 1.0 - smoothstep(0.05, 0.14, abs(dist - radiusPulse));
    float innerRing = 1.0 - smoothstep(0.05, 0.12, abs(dist - radiusPulse * 0.72));
    float fill = 1.0 - smoothstep(radiusPulse * 0.98, radiusPulse * 1.08, dist);
    fill *= 0.22;

    vec2 gridUv = rel / max(Radius, 0.001) * 6.5 + vec2(0.0, iTime * 1.6);
    vec2 cell = floor(gridUv);
    vec2 local = fract(gridUv) - 0.5;
    float bits = digit(local, step(0.5, hash12(cell + floor(iTime * 4.0)))) * step(0.40, hash12(cell * 1.7 + 4.2));
    bits *= fill;

    float spokes = 0.5 + 0.5 * cos(atan(rel.y, rel.x) * 8.0 - iTime * 1.8);
    float pulse = 0.65 + 0.35 * sin(iTime * 4.8 + dist * 0.8 - Age01 * 5.0);

    vec3 c0 = vec3(0.0157, 0.0941, 0.1255);
    vec3 c1 = vec3(0.0039, 0.1647, 0.2235);
    vec3 c2 = vec3(0.0078, 0.2510, 0.3137);
    vec3 accent = vec3(0.18, 0.78, 0.88);

    vec3 glow = vec3(0.0);
    glow += mix(c1, c2, spokes) * outerRing * 1.2;
    glow += mix(c0, c1, 0.7) * innerRing * 0.8;
    glow += accent * bits * 0.85;
    glow += mix(c0, c2, 0.55) * fill * (0.25 + 0.25 * pulse);

    fragColor = vec4(base + glow * A, 1.0);
}
