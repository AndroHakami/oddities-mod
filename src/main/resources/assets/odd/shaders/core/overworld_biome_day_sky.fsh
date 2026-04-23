#version 330 compatibility

uniform float iTime;
uniform float BlendAlpha;
uniform float DayAmount;
uniform float Rain;
uniform float Thunder;

uniform float SkyR;
uniform float SkyG;
uniform float SkyB;

uniform float FogR;
uniform float FogG;
uniform float FogB;

uniform float HorizonR;
uniform float HorizonG;
uniform float HorizonB;

uniform mat4 InvProjMat;
uniform mat4 InvViewMat;

in vec2 vUv;
out vec4 fragColor;

float sat(float x) { return clamp(x, 0.0, 1.0); }

vec3 viewDirFromUv(vec2 uv) {
    vec2 ndc = uv * 2.0 - 1.0;
    vec4 clip = vec4(ndc, 1.0, 1.0);
    vec4 view = InvProjMat * clip;
    view /= max(view.w, 1e-6);

    vec3 dirView = normalize(view.xyz);
    return normalize((InvViewMat * vec4(dirView, 0.0)).xyz);
}

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float valueNoise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);

    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));

    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

void main() {
    vec3 dir = viewDirFromUv(vUv);
    float y = clamp(dir.y, -1.0, 1.0);

    float zen = smoothstep(-0.08, 0.92, y);
    float horizon = 1.0 - smoothstep(-0.16, 0.26, y);
    float upper = smoothstep(0.20, 0.95, y);

    vec3 sky = vec3(SkyR, SkyG, SkyB);
    vec3 fog = vec3(FogR, FogG, FogB);
    vec3 horizonCol = vec3(HorizonR, HorizonG, HorizonB);

    vec3 topCol = mix(sky * 0.82, sky, 0.55);
    vec3 midCol = mix(horizonCol, sky, zen);
    vec3 col = mix(midCol, topCol, upper);

    col += horizonCol * horizon * 0.16;
    col += fog * horizon * 0.10;

    float hazeNoise = valueNoise2(vec2(dir.x * 6.0 + iTime * 0.08, dir.z * 6.0 - iTime * 0.04));
    float haze = horizon * smoothstep(0.48, 0.82, hazeNoise) * 0.05;
    col += fog * haze;

    float weather = clamp(1.0 - Rain * 0.22 - Thunder * 0.18, 0.65, 1.0);
    col *= weather;

    fragColor = vec4(col, sat(BlendAlpha));
}