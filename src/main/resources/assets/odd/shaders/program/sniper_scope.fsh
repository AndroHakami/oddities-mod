#version 330 compatibility

uniform sampler2D DiffuseSampler;
uniform float Intensity;   // 0..1
uniform float Time;        // seconds-ish
uniform vec2 OutSize;      // framebuffer size

in vec2 texCoord;
out vec4 fragColor;

float sat(float x) { return clamp(x, 0.0, 1.0); }

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

// soft AA line (pixel space)
float lineAA(float d, float halfWidth) {
    // d is distance in pixels from the line center
    return 1.0 - smoothstep(halfWidth - 1.0, halfWidth + 1.0, d);
}

float ringAA(float r, float r0, float w) {
    // r in normalized radius space
    float a = 1.0 - smoothstep(r0 - w, r0, r);
    float b = 1.0 - smoothstep(r0, r0 + w, r);
    return sat(a - b);
}

void main() {
    vec4 src = texture(DiffuseSampler, texCoord);
    float a = sat(Intensity);

    if (a <= 0.001) {
        fragColor = src;
        return;
    }

    vec2 px = texCoord * OutSize;
    vec2 c  = OutSize * 0.5;

    float minDim = min(OutSize.x, OutSize.y);

    // normalized radial coords (so circle stays circle on any aspect)
    vec2 q = (px - c) / minDim;
    float r = length(q);

    // scope circle radius
    float R = 0.42;          // tweak if you want bigger/smaller
    float soft = 0.008;      // edge softness
    float inside = 1.0 - smoothstep(R, R + soft, r);

    // base image
    vec3 col = src.rgb;

    // Outside: heavily darken
    float outside = 1.0 - inside;
    col *= mix(0.10, 1.0, inside);

    // Mild “lens” contrast inside
    float y = luma(col);
    col = mix(vec3(y), col, 1.18 * inside);
    col = mix(col, col * 1.04, inside);

    // Dark vignette even inside (subtle)
    float vig = smoothstep(0.70, 0.15, r);
    col *= mix(0.72, 1.03, vig);

    // Edge ring
    float ring = ringAA(r, R, 0.0025);
    col = mix(col, vec3(0.0), ring * a);

    // Reticle (pixel-space)
    float dx = abs(px.x - c.x);
    float dy = abs(px.y - c.y);

    // Center gap
    float gap = 8.0; // pixels

    // Main crosshair thickness
    float w = 1.2; // pixels

    // Lines only inside the scope circle
    float inCircle = inside;

    // Vertical line (with center gap)
    float vLine = lineAA(dx, w) * (1.0 - sat((gap - dy) / gap));
    // Horizontal line (with center gap)
    float hLine = lineAA(dy, w) * (1.0 - sat((gap - dx) / gap));

    // Range ticks (small marks)
    float tickW = 1.0;
    float tickLen = 10.0;

    float tick1 = lineAA(dy, tickW) * lineAA(abs(dx - 55.0), 1.6) * sat((tickLen - dy) / tickLen);
    float tick2 = lineAA(dy, tickW) * lineAA(abs(dx - 95.0), 1.6) * sat((tickLen - dy) / tickLen);
    float tick3 = lineAA(dx, tickW) * lineAA(abs(dy - 55.0), 1.6) * sat((tickLen - dx) / tickLen);
    float tick4 = lineAA(dx, tickW) * lineAA(abs(dy - 95.0), 1.6) * sat((tickLen - dx) / tickLen);

    float ret = (vLine + hLine + tick1 + tick2 + tick3 + tick4) * inCircle;
    ret = sat(ret);

    // Reticle color: light grey, fades with intensity
    vec3 retCol = vec3(0.78, 0.80, 0.82);

    // tiny breathing
    float pulse = 0.95 + 0.05 * sin(Time * 2.5);

    col = mix(col, retCol, ret * a * pulse);

    // Kill everything outside the circle even harder (black)
    col = mix(col, vec3(0.0), outside * (0.92 * a));

    fragColor = vec4(col, src.a);
}
