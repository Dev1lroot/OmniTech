#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>

// Space-suit helmet night vision: boosted black-and-white image with animated sensor
// grain, faint scanlines and a light edge darkening. The brightness itself comes from
// the real Night Vision effect the server grants while this mode is on.

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform NightVisionConfig {
    float Gain;   // tone-curve boost of the luminance
    float Grain;  // noise amplitude
};

layout(location = 0) out vec4 fragColor;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

void main() {
    vec3 color = texture(InSampler, texCoord).rgb;
    float lum = dot(color, vec3(0.299, 0.587, 0.114));
    lum = 1.0 - exp(-lum * Gain);

    float ticks = GameTime * 24000.0;
    lum += (hash(floor(texCoord * OutSize / 1.5) + floor(ticks * 3.0)) - 0.5) * Grain;

    float scanline = 0.92 + 0.08 * sin(texCoord.y * OutSize.y * 3.14159);
    vec2 centered = (texCoord - 0.5) * vec2(OutSize.x / OutSize.y, 1.0);
    float vignette = mix(1.0, smoothstep(1.3, 0.5, length(centered)), 0.35);

    fragColor = vec4(vec3(clamp(lum, 0.0, 1.0) * scanline * vignette), 1.0);
}
