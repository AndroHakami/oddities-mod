#version 330 compatibility

uniform float iTime;
uniform mat4 InvProjMat;
uniform mat4 InvViewMat;

in vec2 vUv;
out vec4 fragColor;

#define PI 3.14159265359
#define TAU 6.28318530718

float sat(float x) { return clamp(x, 0.0, 1.0); }

mat2 rot2(float a){
    float c = cos(a), s = sin(a);
    return mat2(c, -s, s, c);
}

/* -------------------- hash / noise / fbm (Book of Shaders style) -------------------- */
float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

vec2 hash22(vec2 p){
    float n = hash21(p);
    return vec2(n, hash21(p + n + 19.19));
}

float noise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);

    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));

    vec2 u = f*f*(3.0 - 2.0*f);
    return mix(mix(a,b,u.x), mix(c,d,u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.55;
    for (int i = 0; i < 5; i++) {
        v += a * noise2(p);
        p *= 2.02;
        a *= 0.52;
    }
    return v;
}

// domain-warped fbm (clean “ocean” motion)
float dfbm(vec2 p) {
    vec2 q = vec2(
        fbm(p + vec2(0.0, 0.0)),
        fbm(p + vec2(5.2, 1.3))
    );
    vec2 r = vec2(
        fbm(p + 2.0*q + vec2(1.7, 9.2)),
        fbm(p + 2.0*q + vec2(8.3, 2.8))
    );
    return fbm(p + 1.6*r);
}

/* -------------------- reconstruct view direction -------------------- */
vec3 viewDirFromUv(vec2 uv) {
    vec2 ndc = uv * 2.0 - 1.0;
    vec4 clip = vec4(ndc, 1.0, 1.0);
    vec4 view = InvProjMat * clip;
    view /= max(view.w, 1e-6);

    vec3 dirView = normalize(view.xyz);
    vec3 dirWorld = normalize((InvViewMat * vec4(dirView, 0.0)).xyz);
    return dirWorld;
}

/* -------------------- existing tiny stars (round) -------------------- */
float starLayer(vec3 dir, float scale, float density, float size, float t) {
    float theta = atan(dir.z, dir.x);
    float lat   = asin(clamp(dir.y, -1.0, 1.0));
    vec2 uv = vec2(theta / TAU + 0.5, lat / PI + 0.5);

    vec2 g  = uv * scale;
    vec2 id = floor(g);
    vec2 f  = fract(g) - 0.5;

    float rnd = hash21(id);
    float on  = step(1.0 - density, rnd);

    vec2 ofs = vec2(hash21(id + 17.7), hash21(id + 71.3)) - 0.5;
    vec2 p = f - ofs * 0.35;

    float d = length(p);
    float s = smoothstep(size, 0.0, d);

    float tw = 0.75 + 0.25 * sin(t * 2.6 + rnd * TAU * 3.0);
    return on * s * tw;
}

/* -------------------- CARTOON STARS (glowing, 5-lobed) -------------------- */
float star5(vec2 p, float r){
    float ang = atan(p.y, p.x);
    float k = 0.5 + 0.5 * cos(5.0 * ang);
    float target = mix(r * 0.55, r, pow(k, 2.2));
    float d = length(p);
    return 1.0 - smoothstep(target, target + r * 0.18, d);
}

vec3 cartoonStars(vec3 dir, float t){
    float theta = atan(dir.z, dir.x);
    float lat   = asin(clamp(dir.y, -1.0, 1.0));
    vec2 uv = vec2(theta / TAU + 0.5, lat / PI + 0.5);

    // push visibility overhead so you see them inside the maze
    float overhead = smoothstep(0.58, 0.86, uv.y);

    // gentle drift so they "float"
    vec2 duv = uv;
    duv += vec2(t * 0.004, sin(t * 0.11) * 0.0025);

    vec3 col = vec3(0.0);

    // Big cartoony stars (sparser)
    {
        vec2 g  = duv * vec2(16.0, 9.0);
        vec2 id = floor(g);
        vec2 f  = fract(g) - 0.5;

        for (int j=-1; j<=1; j++) for (int i=-1; i<=1; i++){
            vec2 cid = id + vec2(i, j);
            vec2 rnd = hash22(cid);

            // probability (sparse)
            float spawn = step(0.93, rnd.x); // ~7%

            // random position in cell
            vec2 ofs = (rnd - 0.5) * 0.78;
            vec2 p = f - vec2(i, j) - ofs;

            float size = mix(0.11, 0.22, rnd.y);
            float s = star5(p, size);

            float d = length(p);
            float halo = exp(-(d*d) / max(1e-4, size*size*1.9));

            // twinkle
            float tw = 0.72 + 0.28 * sin(t * 2.2 + rnd.x * 25.0);

            // warm yellow with a soft bloom edge
            vec3 starCol = vec3(1.00, 0.95, 0.45);
            col += starCol * (s * 1.8 + halo * 1.05) * tw * spawn;
        }
    }

    // Tiny cartoony sparkles (denser but subtle)
    {
        vec2 g  = duv * vec2(64.0, 36.0);
        vec2 id = floor(g);
        vec2 f  = fract(g) - 0.5;

        for (int j=-1; j<=1; j++) for (int i=-1; i<=1; i++){
            vec2 cid = id + vec2(i, j);
            float r = hash21(cid);
            vec2 rnd = hash22(cid + r);

            float spawn = step(0.985, r); // very sparse

            vec2 ofs = (rnd - 0.5) * 0.85;
            vec2 p = f - vec2(i, j) - ofs;

            float size = mix(0.025, 0.050, rnd.x);
            float s = star5(p, size);

            float tw = 0.60 + 0.40 * sin(t * 5.8 + r * 40.0);
            vec3 starCol = vec3(1.00, 0.98, 0.65);

            col += starCol * s * tw * spawn * 0.9;
        }
    }

    // fade near horizon + boost overhead
    return col * overhead;
}

/* -------------------- ocean sky color -------------------- */
vec3 oceanSky(vec3 dir, float t) {
    float y = clamp(dir.y, -1.0, 1.0);
    float zen = smoothstep(0.05, 0.85, y);

    vec3 horizonCol = vec3(0.02, 0.07, 0.12);
    vec3 zenithCol  = vec3(0.01, 0.03, 0.07);

    vec3 col = mix(horizonCol, zenithCol, zen);

    float h = exp(-abs(y) * 3.2);

    vec2 p = dir.xz;
    p = rot2(t * 0.01) * p;

    vec2 p1 = p * 3.2 + vec2(t * 0.020, -t * 0.012);
    vec2 p2 = p * 7.0 + vec2(-t * 0.030, t * 0.018);

    float n1 = dfbm(p1);
    float n2 = dfbm(p2);

    float waves = mix(n1, n2, 0.45);

    float foam = smoothstep(0.62, 0.86, waves);
    foam = pow(foam, 1.65);

    float glint = smoothstep(0.78, 0.98, waves);
    glint = pow(glint, 10.0);

    vec3 oceanTint = vec3(0.06, 0.22, 0.32);
    vec3 glintTint = vec3(0.18, 0.40, 0.55);

    col += oceanTint * foam * (0.25 + 0.85 * h);
    col += glintTint * glint * (0.08 + 0.55 * h);

    vec3 bloom = vec3(0.06, 0.14, 0.18) * h;
    col += bloom * 0.65;

    return col;
}

vec3 auroraCurtains(vec3 dir, float t) {
    float theta = atan(dir.z, dir.x);
    float u = theta / TAU + 0.5;
    float v = sat(dir.y * 0.5 + 0.5);

    float band = smoothstep(0.62, 0.75, v) * (1.0 - smoothstep(0.92, 0.99, v));
    float azFade = 0.65 + 0.35 * sin(u * TAU * 1.0);

    vec2 p = vec2(u * 7.5, v * 2.4);
    p += vec2(t * 0.010, -t * 0.028);

    float n = dfbm(p);

    float curtains = pow(sat(1.0 - abs(fract(u * 18.0 + n * 1.1) - 0.5) * 2.0), 3.2);
    float body = pow(sat(n), 2.0);

    float a = band * azFade * body * (0.35 + 0.65 * curtains);

    vec3 c1 = vec3(0.10, 0.32, 0.55);
    vec3 c2 = vec3(0.02, 0.18, 0.35);

    float glow = pow(a, 1.6);

    return (c2 * a + c1 * glow * 0.85);
}

void main() {
    float t = iTime;
    vec3 dir = viewDirFromUv(vUv);

    vec3 col = oceanSky(dir, t);
    col += auroraCurtains(dir, t);

    // === NEW: cartoony floating glowing yellow stars ===
    col += cartoonStars(dir, t);

    // keep tiny background stars, but tone them down so the cartoon stars read clearly
    float starFade = pow(sat((dir.y + 0.05) / 1.05), 0.35);

    float s =
        starLayer(dir, 420.0, 0.016, 0.18, t) +
        starLayer(dir, 210.0, 0.010, 0.23, t * 0.85) * 0.65 +
        starLayer(dir, 840.0, 0.006, 0.14, t * 1.25) * 0.45;

    float glow = pow(sat(s), 2.2);

    col += vec3(0.65, 0.78, 0.95) * s * 0.55 * starFade;
    col += vec3(0.35, 0.52, 0.78) * glow * 0.22 * starFade;

    fragColor = vec4(col, 1.0);
}
