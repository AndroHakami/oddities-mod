#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;
uniform vec3 CameraPosition;

uniform vec3 Center;
uniform float Radius;
uniform float Age01;
uniform float Height;
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

void main() {
    vec3 base = texture(DiffuseSampler, texCoord).rgb;
    float depth = min(texture(DepthSampler, texCoord).r, 0.9995);
    vec3 surf = worldPos(vec3(texCoord, depth));

    vec3 local = surf - Center;
    float vertical = smoothstep(-0.15, 0.05, local.y) * (1.0 - smoothstep(Height * 0.6, Height, local.y));
    float dist = length(local.xz);
    float ringRadius = mix(0.8, Radius, Age01);
    float thickness = mix(0.65, 1.2, Age01);
    float ring = exp(-pow((dist - ringRadius) / max(thickness, 0.001), 2.0));
    float inner = exp(-pow(dist / max(ringRadius * 0.45, 0.001), 2.0)) * (1.0 - smoothstep(0.0, 0.7, Age01));
    float pulse = 0.65 + 0.35 * sin(iTime * 18.0 - dist * 1.4);

    float mask = vertical * (ring * pulse + inner * 0.35) * Intensity;
    if (mask <= 0.001) {
        fragColor = vec4(base, 1.0);
        return;
    }

    vec3 glow = vec3(0.08, 0.98, 0.24) * mask * 1.25;
    glow += vec3(0.58, 1.0, 0.62) * ring * vertical * 0.42 * Intensity;

    fragColor = vec4(base + glow, 1.0);
}
