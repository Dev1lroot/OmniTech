#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>

// Space-suit helmet infrared camera: black-and-white near-IR image as seen by a
// security/camcorder night shot.
//   • IR illuminator: close surfaces are lit bright, falling off with depth (sky stays dark)
//   • near-IR response: foliage glows (Wood effect), water goes dark, hot sources bloom
//   • VHS tape: per-line jitter, a rolling tracking band with static, horizontal smear,
//     grain and occasional dropout streaks
// The real Night Vision effect (granted by the server in this mode) lifts the base image.

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform InfraredConfig {
    float Gain;            // IR illuminator strength
    float IlluminatorRange; // blocks at which the illuminator has fallen to half
    float Grain;           // tape noise amplitude
    float Near;            // camera near plane
    float Far;             // camera far plane (see helmet_sonar.fsh)
};

layout(location = 0) out vec4 fragColor;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

// reversed Z, same maths as helmet_sonar.fsh
float linearDepth(float deviceDepth) {
#ifdef RENDERPEARL_DEPTH_IS_ZERO_TO_ONE
    float m22 = Near / (Far - Near);
    float m32 = Near * Far / (Far - Near);
    float d = deviceDepth;
#else
    float m22 = (Near + Far) / (Far - Near);
    float m32 = 2.0 * Near * Far / (Far - Near);
    float d = (deviceDepth - 0.5) * 2.0;
#endif
    return m32 / (d + m22);
}

void main() {
    float seconds = GameTime * 24000.0 / 20.0;
    float frame = floor(seconds * 30.0);
    vec2 uv = texCoord;

    // ── VHS: line jitter + rolling tracking band ─────────────────────────────
    float line = floor(uv.y * OutSize.y / 2.0);
    float band = fract(seconds * 0.07);
    float inBand = smoothstep(0.035, 0.0, abs(uv.y - band));
    uv.x += (hash(vec2(line, frame)) - 0.5) * 0.002;
    uv.x += inBand * (hash(vec2(line, frame * 2.0)) - 0.5) * 0.03;

    // horizontal smear (tape head lag)
    vec2 px = vec2(1.0 / OutSize.x, 0.0);
    vec3 c = texture(InSampler, uv).rgb;
    c = mix(c, (c + texture(InSampler, uv - px * 1.5).rgb + texture(InSampler, uv - px * 3.0).rgb) / 3.0, 0.5);

    // ── near-IR response ─────────────────────────────────────────────────────
    float lum = dot(c, vec3(0.299, 0.587, 0.114));
    float foliage = clamp((c.g - max(c.r, c.b)) * 3.0, 0.0, 1.0);
    float water = clamp((c.b - max(c.r, c.g)) * 2.5, 0.0, 1.0);
    float heat = clamp((c.r - c.b) * 1.5 - 0.2, 0.0, 1.0) * smoothstep(0.55, 0.9, max(c.r, c.g));
    lum += foliage * 0.45 - water * 0.3 + heat * 0.7;

    // ── IR illuminator (depth falloff) ───────────────────────────────────────
    float deviceDepth = texture(DepthSampler, uv).r;
    if (deviceDepth <= 0.0) {
        lum *= 0.35;   // sky: nothing reflects the illuminator back
    } else {
        float z = linearDepth(deviceDepth);
        float lamp = 1.0 / (1.0 + pow(z / IlluminatorRange, 2.0));
        lum *= 0.45 + Gain * lamp;
    }
    lum = 1.0 - exp(-lum * 1.6);

    // ── tape noise ───────────────────────────────────────────────────────────
    lum += (hash(floor(uv * OutSize / 1.5) + frame) - 0.5) * Grain;
    lum += inBand * (hash(vec2(floor(uv.x * OutSize.x / 2.0), frame)) - 0.5) * 0.55;
    float dropoutRow = hash(vec2(floor(texCoord.y * OutSize.y / 3.0), floor(seconds * 24.0)));
    if (dropoutRow > 0.996) lum += 0.55 * hash(vec2(floor(texCoord.x * 40.0), frame));

    float scanline = 0.92 + 0.08 * sin(texCoord.y * OutSize.y * 3.14159);
    vec2 centered = (texCoord - 0.5) * vec2(OutSize.x / OutSize.y, 1.0);
    float vignette = mix(1.0, smoothstep(1.3, 0.5, length(centered)), 0.3);

    fragColor = vec4(vec3(clamp(lum, 0.0, 1.0) * scanline * vignette), 1.0);
}
