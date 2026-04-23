#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;
uniform vec3 CameraPosition;

uniform vec3 Center;
uniform float HalfSize;
uniform float Height;
uniform float Charge01;
uniform float Intensity;
uniform float iTime;

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

float squareMask(vec2 p, float halfSize, float feather) {
    vec2 d = abs(p) - vec2(halfSize);
    float box = max(d.x, d.y);
    return 1.0 - smoothstep(0.0, feather, box);
}

void main() {
    vec3 base = texture(DiffuseSampler, texCoord).rgb;
    float depth = min(texture(DepthSampler, texCoord).r, 0.9995);
    vec3 surf = worldPos(vec3(texCoord, depth));

    vec3 local = surf - Center;
    float flatMask = squareMask(local.xz, HalfSize, 0.16);
    float floorMask = 1.0 - smoothstep(0.08, 0.32, abs(local.y));
    float mask = flatMask * floorMask;

    if (mask <= 0.001) {
        fragColor = vec4(base, 1.0);
        return;
    }

    float border = squareMask(local.xz, HalfSize, 0.04) - squareMask(local.xz, HalfSize - 0.22, 0.06);
    float innerBorder = squareMask(local.xz, HalfSize - 0.46, 0.04) - squareMask(local.xz, HalfSize - 0.66, 0.05);
    float scan = 0.55 + 0.45 * sin(iTime * 10.0 + local.x * 4.0 - local.z * 3.4);
    float chargePulse = 0.45 + 0.55 * sin(iTime * 14.0 + length(local.xz) * 8.0);
    float corner = smoothstep(HalfSize * 0.42, HalfSize * 0.94, abs(local.x * local.z));

    vec2 grid = abs(fract((local.xz + HalfSize) * 1.0) - 0.5);
    float gridLines = 1.0 - smoothstep(0.44, 0.49, min(grid.x, grid.y));
    gridLines *= 0.35 + 0.65 * Charge01;

    float rise = smoothstep(0.35, 1.0, Charge01);
    float spark = 0.5 + 0.5 * sin(iTime * 18.0 + hash12(floor(local.xz * 3.0)) * 6.2831);

    float fill = mask * (0.16 + 0.18 * scan + 0.18 * chargePulse);
    float edge = (border * 1.35 + innerBorder * 0.85) * (0.55 + 0.45 * Charge01);
    float flare = rise * corner * spark * 0.28;

    vec3 greenA = vec3(0.08, 0.96, 0.24);
    vec3 greenB = vec3(0.28, 1.00, 0.40);
    vec3 greenC = vec3(0.78, 1.00, 0.66);

    vec3 glow = vec3(0.0);
    glow += greenA * fill * 0.85;
    glow += greenB * edge * 1.35;
    glow += greenC * gridLines * 0.22;
    glow += greenC * flare * 0.55;
    glow *= Intensity;
    glow *= 0.995 + 0.005 * hash12(gl_FragCoord.xy + vec2(iTime * 30.0, -iTime * 12.0));

    vec3 col = base + glow;
    fragColor = vec4(col, 1.0);
}
