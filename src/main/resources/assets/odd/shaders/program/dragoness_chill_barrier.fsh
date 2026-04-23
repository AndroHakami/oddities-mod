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
    float sphereDist = length(local);
    float shellRadius = mix(Radius * 0.82, Radius, Age01);
    float shellThickness = mix(0.16, 0.34, Age01);
    float shell = 1.0 - smoothstep(shellRadius - shellThickness, shellRadius + shellThickness, sphereDist);
    float innerCut = smoothstep(shellRadius - shellThickness * 1.75, shellRadius - shellThickness * 0.4, sphereDist);
    shell *= innerCut;

    float verticalFade = 1.0 - smoothstep(Height * 0.48, Height * 0.72, abs(local.y));
    float swirl = 0.55 + 0.45 * sin(iTime * 7.0 + local.y * 1.8 + atan(local.z, local.x) * 6.0);
    float grid = 0.75 + 0.25 * sin((local.x + local.z) * 3.2 - iTime * 5.5);
    float mask = shell * verticalFade * (0.78 + 0.22 * swirl) * grid * Intensity;

    if (mask <= 0.001) {
        fragColor = vec4(base, 1.0);
        return;
    }

    vec3 glow = vec3(0.05, 0.82, 0.18) * mask * 0.85;
    glow += vec3(0.42, 1.0, 0.55) * shell * verticalFade * 0.35 * Intensity;

    fragColor = vec4(base + glow, 1.0);
}
