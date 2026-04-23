#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform mat4 InverseTransformMatrix;
uniform mat4 ModelViewMat;
uniform vec3 CameraPosition;

uniform vec3 Origin0; uniform vec3 Dir0; uniform float Length0; uniform float Width0; uniform float Active0; uniform float Seed0;
uniform vec3 Origin1; uniform vec3 Dir1; uniform float Length1; uniform float Width1; uniform float Active1; uniform float Seed1;
uniform vec3 Origin2; uniform vec3 Dir2; uniform float Length2; uniform float Width2; uniform float Active2; uniform float Seed2;
uniform vec3 Origin3; uniform vec3 Dir3; uniform float Length3; uniform float Width3; uniform float Active3; uniform float Seed3;
uniform vec3 Origin4; uniform vec3 Dir4; uniform float Length4; uniform float Width4; uniform float Active4; uniform float Seed4;
uniform vec3 Origin5; uniform vec3 Dir5; uniform float Length5; uniform float Width5; uniform float Active5; uniform float Seed5;

uniform float iTime;
uniform float Intensity;

in vec2 texCoord;
out vec4 fragColor;

vec3 worldPos(vec3 point) {
    vec3 ndc = point * 2.0 - 1.0;
    vec4 homPos = InverseTransformMatrix * vec4(ndc, 1.0);
    vec3 viewPos = homPos.xyz / homPos.w;
    return (inverse(ModelViewMat) * vec4(viewPos, 1.0)).xyz + CameraPosition;
}

float sat(float x) { return clamp(x, 0.0, 1.0); }

vec3 rainbow(float t) {
    vec3 phase = vec3(0.0, 2.0943951, 4.1887902);
    return 0.56 + 0.44 * cos(6.2831853 * (t + phase));
}

float hash12(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

vec4 ribbonContribution(vec3 wp, vec3 origin, vec3 dir3, float lengthV, float widthV, float activeV, float seedV) {
    if (activeV < 0.5 || lengthV <= 0.01 || widthV <= 0.01) return vec4(0.0);

    float dirLen = max(length(dir3.xz), 0.0001);
    vec2 dir = dir3.xz / dirLen;
    vec2 rel = wp.xz - origin.xz;
    float along = dot(rel, dir);
    if (along < 0.0 || along > lengthV) return vec4(0.0);

    float trailT = sat(along / max(lengthV, 0.001));
    vec2 lateralVec = rel - dir * along;
    float across = length(lateralVec);

    float tailWidth = mix(widthV, widthV * 0.40, trailT);
    float widthMask = 1.0 - smoothstep(tailWidth * 0.82, tailWidth, across);
    float heightMask = 1.0 - smoothstep(0.045, 0.14, abs(wp.y - origin.y));
    float tailFade = 1.0 - smoothstep(0.78, 1.0, trailT);
    float headFade = smoothstep(0.00, 0.04, trailT);
    float alpha = widthMask * heightMask * tailFade * headFade;
    if (alpha <= 0.001) return vec4(0.0);

    float bandT = fract(trailT * 0.85 - iTime * 0.18 + seedV * 0.031);
    vec3 color = rainbow(bandT);

    float stripe = 0.90 + 0.10 * sin((trailT * 8.0 - iTime * 1.7 + seedV) * 6.2831853);
    color *= stripe;

    float sparkleSeed = hash12(floor((wp.xz + seedV) * 3.0));
    float sparkle = smoothstep(0.972, 0.995, sparkleSeed) * (0.20 + 0.80 * sin(iTime * 8.5 + along * 2.4 + seedV));
    vec3 sparkColor = vec3(1.0, 0.98, 0.90) * sparkle;

    vec3 outColor = (color * 1.22 + sparkColor) * alpha * Intensity;
    return vec4(outColor, alpha);
}

void main() {
    vec3 base = texture(DiffuseSampler, texCoord).rgb;
    float z = min(texture(DepthSampler, texCoord).r, 0.9995);
    vec3 wp = worldPos(vec3(texCoord, z));

    vec3 glow = vec3(0.0);
    glow += ribbonContribution(wp, Origin0, Dir0, Length0, Width0, Active0, Seed0).rgb;
    glow += ribbonContribution(wp, Origin1, Dir1, Length1, Width1, Active1, Seed1).rgb;
    glow += ribbonContribution(wp, Origin2, Dir2, Length2, Width2, Active2, Seed2).rgb;
    glow += ribbonContribution(wp, Origin3, Dir3, Length3, Width3, Active3, Seed3).rgb;
    glow += ribbonContribution(wp, Origin4, Dir4, Length4, Width4, Active4, Seed4).rgb;
    glow += ribbonContribution(wp, Origin5, Dir5, Length5, Width5, Active5, Seed5).rgb;

    fragColor = vec4(base + glow, 1.0);
}
