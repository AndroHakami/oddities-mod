#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;
uniform vec3 CameraPosition;

uniform vec3 Center;
uniform float Radius;
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

mat2 rot(float a) {
    float c = cos(a), s = sin(a);
    return mat2(c, -s, s, c);
}

float sdStar(vec2 p, float r, float inner) {
    float a = atan(p.y, p.x);
    float d = length(p);
    float spikes = cos(a * 5.0);
    float target = mix(inner, r, smoothstep(-1.0, 1.0, spikes));
    return d - target;
}

void main() {
    vec3 base = texture(DiffuseSampler, texCoord).rgb;
    float z = min(texture(DepthSampler, texCoord).r, 0.9995);
    vec3 wp = worldPos(vec3(texCoord, z));

    float heightFade = 1.0 - smoothstep(0.04, 0.22, abs(wp.y - Center.y));
    if (heightFade <= 0.001 || Intensity <= 0.001) {
        fragColor = vec4(base, 1.0);
        return;
    }

    vec2 rel = wp.xz - Center.xz;
    float dist = length(rel);
    float circle = 1.0 - smoothstep(0.05, 0.12, abs(dist - Radius));

    vec2 starUv = rot(iTime * 1.6) * (rel / max(Radius, 0.001));
    float star = 1.0 - smoothstep(0.0, 0.07, sdStar(starUv, 0.55, 0.20));

    vec2 innerUv = rot(-iTime * 1.25) * (rel / max(Radius, 0.001));
    float innerRing = 1.0 - smoothstep(0.03, 0.08, abs(length(innerUv) - 0.62));

    float pulse = 0.65 + 0.35 * sin(iTime * 4.0 + dist * 1.5);
    vec3 yellow = vec3(1.0, 0.90, 0.24);
    vec3 glow = yellow * (circle * 0.8 + innerRing * 0.5 + star * 1.2) * pulse * Intensity * heightFade;

    fragColor = vec4(base + glow, 1.0);
}
