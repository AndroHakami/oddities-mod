
#version 330 compatibility
#define PI 3.141592653589793
#define TAU 6.283185307179586

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
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float smooth01(float a, float b, float x) {
    float t = sat((x - a) / (b - a));
    return t * t * (3.0 - 2.0 * t);
}

void main() {
    vec3 base = texture(DiffuseSampler, texCoord).rgb;

    float alpha = sat(Intensity);
    if (alpha <= 0.001) {
        fragColor = vec4(base, 1.0);
        return;
    }

    float depth = texture(DepthSampler, texCoord).r;
    depth = min(depth, 0.9995);

    vec3 wp = worldPos(vec3(texCoord, depth));
    vec3 rel = wp - Center;

    float dist = length(rel);
    float rNow = mix(0.22, Radius, Age01);

    float shell = 1.0 - smoothstep(0.0, 0.22 * Radius, abs(dist - rNow));
    float inner = 1.0 - smoothstep(0.0, 0.38 * Radius, dist);
    float ringGround = 0.0;

    float flatDist = length(rel.xz);
    float groundBand = 1.0 - smoothstep(0.0, 0.18 * Radius, abs(flatDist - rNow));
    float nearGround = 1.0 - smoothstep(0.0, 0.50, abs(rel.y));
    ringGround = groundBand * nearGround;

    vec3 dir = normalize(rel + vec3(1e-5));
    float ang = atan(dir.z, dir.x);
    float spokes = pow(abs(sin(ang * 10.0 + iTime * 12.0)), 18.0);
    float noise = hash12(floor(rel.xz * 5.0) + floor(rel.y * 3.0));
    float streaks = shell * mix(0.45, 1.0, spokes) * mix(0.85, 1.15, noise);

    float fadeShell = smooth01(0.0, 0.75, 1.0 - Age01);
    float fadeCore = smooth01(0.0, 0.55, 1.0 - Age01);

    vec3 lime = vec3(0.30, 1.25, 0.25);
    vec3 hot = vec3(0.75, 1.35, 0.40);

    vec3 add = vec3(0.0);
    add += lime * streaks * 0.90 * fadeShell;
    add += hot * shell * 0.70 * fadeShell;
    add += hot * ringGround * 0.65 * fadeShell;
    add += lime * inner * 0.35 * fadeCore;

    fragColor = vec4(base + add * alpha, 1.0);
}
