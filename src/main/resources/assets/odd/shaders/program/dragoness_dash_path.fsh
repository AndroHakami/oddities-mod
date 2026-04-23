#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;
uniform vec3 CameraPosition;

uniform vec3 PathStart;
uniform vec3 PathEnd;
uniform float HalfWidth;
uniform float Height;
uniform float Age01;
uniform float Intensity;
uniform float iTime;

in vec2 texCoord;
out vec4 fragColor;

vec3 worldPos(vec3 point) {
    vec3 ndc = point * 2.0 - 1.0;
    vec4 homPos = InverseTransformMatrix * vec4(ndc, 1.0);
    vec3 viewPos = homPos.xyz / homPos.w;
    return (inverse(ModelViewMat) * vec4(viewPos, 1.0)).xyz + CameraPosition;
}

float segmentDistance2D(vec2 p, vec2 a, vec2 b, out float h01) {
    vec2 ab = b - a;
    float den = max(dot(ab, ab), 0.0001);
    h01 = clamp(dot(p - a, ab) / den, 0.0, 1.0);
    vec2 q = a + ab * h01;
    return length(p - q);
}

void main() {
    vec3 base = texture(DiffuseSampler, texCoord).rgb;
    float depth = min(texture(DepthSampler, texCoord).r, 0.9995);
    vec3 surf = worldPos(vec3(texCoord, depth));

    float h01;
    float d = segmentDistance2D(surf.xz, PathStart.xz, PathEnd.xz, h01);
    float vertical = smoothstep(-0.18, 0.08, surf.y - PathStart.y) * (1.0 - smoothstep(Height * 0.6, Height, surf.y - PathStart.y));
    float body = 1.0 - smoothstep(HalfWidth, HalfWidth + 0.35, d);
    float edge = 1.0 - smoothstep(HalfWidth * 0.72, HalfWidth, d);
    edge = 1.0 - edge;
    float scan = 0.55 + 0.45 * sin(iTime * 10.0 - h01 * 18.0);
    float flow = 0.5 + 0.5 * sin(iTime * 14.0 - h01 * 32.0);

    float mask = vertical * body * (0.35 + scan * 0.35 + flow * 0.2) * Intensity;
    float rim = vertical * edge * (0.35 + 0.65 * Age01) * Intensity;
    if (mask + rim <= 0.001) {
        fragColor = vec4(base, 1.0);
        return;
    }

    vec3 glow = vec3(0.08, 0.95, 0.22) * mask * 0.95;
    glow += vec3(0.42, 1.0, 0.48) * rim * 1.1;
    fragColor = vec4(base + glow, 1.0);
}
