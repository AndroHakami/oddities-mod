#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform float Intensity;
uniform float Time;
uniform float Meter01;
uniform vec2 OutSize;

in vec2 texCoord; // not relied on
out vec4 fragColor;

float sat(float x){ return clamp(x, 0.0, 1.0); }

float hash(vec2 p){
    p = fract(p * vec2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}

float noise(vec2 p){
    vec2 i = floor(p);
    vec2 f = fract(p);
    float a = hash(i);
    float b = hash(i + vec2(1,0));
    float c = hash(i + vec2(0,1));
    float d = hash(i + vec2(1,1));
    vec2 u = f*f*(3.0-2.0*f);
    return mix(mix(a,b,u.x), mix(c,d,u.x), u.y);
}

void main() {
    vec2 os = max(OutSize, vec2(1.0));
    vec2 uv = gl_FragCoord.xy / os;
    uv = clamp(uv, 0.0, 1.0);

    vec4 src = texture(DiffuseSampler, uv);

    float a = clamp(Intensity, 0.0, 1.6);
    if (a <= 0.001) { fragColor = src; return; }

    // --- Perfect loop timing (change LOOP_SEC if you want slower/faster cycle) ---
    const float TAU = 6.28318530718;
    const float LOOP_SEC = 6.0;                 // smooth loop length in seconds
    float ph = Time * TAU / LOOP_SEC;

    // These all repeat perfectly every LOOP_SEC (integer multiples only)
    vec2 t1 = vec2(cos(ph),       sin(ph));
    vec2 t2 = vec2(cos(ph * 2.0), sin(ph * 2.0));
    vec2 t3 = vec2(cos(ph * 3.0), sin(ph * 3.0));

    vec2 c = uv - 0.5;
    float r = length(c);

    // seam-free angle in [0..1)
    float ang01 = (atan(c.y, c.x) / TAU) + 0.5;

    // denser clouds (time offsets are circular so they loop cleanly)
    float n1 = noise(uv * 3.2  + t1 * 0.85);
    float n2 = noise(uv * 7.4  + t2 * 0.65);
    float n3 = noise(uv * 13.0 + t3 * 0.40);

    // Seamless swirl: integer angular loops, expressed in TAU-space
    const float LOOPS = 4.0; // integer => no atan seam
    float swirl = sin(TAU * (ang01 * LOOPS + r * 1.85) + (n1 * 2.2 + n3 * 1.1) - ph * 1.15);
    swirl = 0.5 + 0.5 * swirl;

    // thicker cloud body
    float cloud = sat(swirl * (0.70 + 0.30 * n2));
    cloud = pow(cloud, 1.25);

    // stronger coverage (vignette ring)
    float edge = smoothstep(0.06, 0.72, r);

    // pulse harder when meter low (still smooth because it’s sin(ph*k))
    float low = 1.0 - sat(Meter01);
    float pulse = 1.0 + low * 0.45 * (0.5 + 0.5 * sin(ph * 7.0));

    float k = a * edge * pulse;

    vec3 outCol = src.rgb;

    // Strong darkening “black mist”
    outCol *= (1.0 - 0.62 * k * cloud);

    // extra inky fog
    float fog = 0.35 * k * cloud;
    outCol = mix(outCol, vec3(0.0), sat(fog));

    // tiny cold tint depth
    outCol += vec3(0.02, 0.03, 0.05) * (0.10 * k * cloud);

    fragColor = vec4(outCol, src.a);
}