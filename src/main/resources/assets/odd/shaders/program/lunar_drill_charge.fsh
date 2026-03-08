#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform float Intensity;
uniform float Time;
uniform float Charge;
uniform vec2 OutSize;

in vec2 texCoord; // not relied on
out vec4 fragColor;

float sat(float x){ return clamp(x,0.0,1.0); }

float hash12(vec2 p){
    p = fract(p * vec2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}

void main() {
    vec2 os = max(OutSize, vec2(1.0));
    vec2 uv = gl_FragCoord.xy / os;
    uv = clamp(uv, 0.0, 1.0);

    vec3 base = texture(DiffuseSampler, uv).rgb;

    float A = sat(Intensity);
    float c = sat(Charge);
    if (A <= 0.001 || c <= 0.001) {
        fragColor = vec4(base, 1.0);
        return;
    }

    // expanding radius with charge
    float r = length(uv - 0.5);
    float radius = mix(0.10, 0.78, c);
    float width  = mix(0.09, 0.045, c); // tightens as it charges

    // ring + soft interior bloom
    float ring = smoothstep(radius - width, radius, r) * (1.0 - smoothstep(radius, radius + width, r));
    float fill = 1.0 - smoothstep(0.0, radius, r);
    fill *= 0.35;

    // tiny animated shimmer (cheap)
    float n = hash12(gl_FragCoord.xy + vec2(Time * 90.0, -Time * 70.0));
    float shimmer = (n - 0.5) * 0.06;

    // palette: warm lunar yellow
    vec3 yellow = vec3(1.00, 0.92, 0.22);

    float k = A * (0.10 + 0.90 * c); // stronger when nearer full charge

    vec3 col = base;
    col += yellow * (0.24 * ring + 0.10 * fill) * k * (1.0 + shimmer);
    col *= (1.0 - 0.03 * k * ring);

    fragColor = vec4(col, 1.0);
}