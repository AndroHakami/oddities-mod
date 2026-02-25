#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

uniform float iTime;
uniform float Intensity; // master fade
uniform float Progress;  // event ramp

in vec2 texCoord;
out vec4 fragColor;

float sat(float x){ return clamp(x, 0.0, 1.0); }

void main() {
    vec3 base = texture(DiffuseSampler, texCoord).rgb;
    float depth = texture(DepthSampler, texCoord).r;

    // Detect sky (depth ~ 1). Tint sky less than world.
    float sky = step(0.9995, depth);
    float worldMask = 1.0 - sky;

    float a = sat(Intensity) * sat(Progress);

    // Noticeable on world, still gentle on sky
    float apply = a * mix(0.10, 0.75, worldMask);

    // Luma/shadow mask: stronger in shadows
    float luma = dot(base, vec3(0.299, 0.587, 0.114));
    float shadow = sat(1.0 - smoothstep(0.24, 0.80, luma));

    // “from above” influence (slightly stronger)
    float top = smoothstep(0.40, 1.0, texCoord.y);

    // Dark green vibe (not lime), but clearly visible
    vec3 tintMul    = vec3(0.94, 1.06, 0.97);
    vec3 shadowAdd  = vec3(0.00, 0.070, 0.028) * shadow;
    vec3 skyGlowAdd = vec3(0.00, 0.035, 0.012) * top;

    // Mild darken so it feels eerie
    vec3 graded = (base * 0.88) * tintMul + shadowAdd + skyGlowAdd;

    graded = clamp(graded, 0.0, 1.0);

    vec3 col = mix(base, graded, apply);

    float dn = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898,78.233))) * 43758.5453);
    col += (dn - 0.5) * (1.0/255.0);

    fragColor = vec4(col, 1.0);
}