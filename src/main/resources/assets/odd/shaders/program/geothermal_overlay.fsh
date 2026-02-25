#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform float Time;
uniform float Strength;
uniform vec2 OutSize;

in vec2 texCoord;
out vec4 fragColor;

float sat(float x){ return clamp(x,0.0,1.0); }

float hash12(vec2 p){
    p = fract(p * vec2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}

void main() {
    vec4 src = texture(DiffuseSampler, texCoord);
    float a = sat(Strength);
    if (a <= 0.001) { fragColor = src; return; }

    // subtle heat shimmer
    vec2 uv = texCoord;
    vec2 px = 1.0 / max(OutSize, vec2(1.0));
    float n = hash12(uv * OutSize + vec2(Time * 37.0, Time * 11.0)) - 0.5;

    float wob = sin(Time * 2.6 + uv.y * 18.0) * 0.5 + 0.5;
    vec2 ofs = vec2((wob - 0.5) * 3.0 + n * 2.0, (n) * 1.5) * px * (6.0 * a);

    vec3 col = texture(DiffuseSampler, uv + ofs).rgb;

    // warm vignette pulse
    vec2 c = uv * 2.0 - 1.0;
    float d = length(c);
    float vig = smoothstep(0.25, 1.15, d);
    float pulse = 0.75 + 0.25 * sin(Time * 4.0);

    vec3 warm = vec3(1.0, 0.45, 0.10);
    col += warm * (0.22 * a * vig * pulse);

    fragColor = vec4(col, src.a);
}
