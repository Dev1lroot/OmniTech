#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>

// Space-suit helmet sonar: surfaces coloured by distance (red = close, blue = far),
// depth-edge outlines, expanding scan rings and a hint of scene luminance for detail.
// No return (sky) stays black.

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform SonarConfig {
    float MaxRange;  // blocks mapped onto the red → blue ramp
    float Near;      // camera near plane (vanilla: 0.05)
    float Far;       // far plane = max(render distance * 4, cloud range * 16); 2048 is the
                     // usual value, and within MaxRange the error stays under ~10%
};

layout(location = 0) out vec4 fragColor;

// 26.x renders with reversed Z (1 = near, 0 = far/sky); same maths as vanilla's
// deviceToLinearDepth, with the projection terms rebuilt from Near/Far.
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

vec3 heat(float t) {
    vec3 c = mix(vec3(1.0, 0.0, 0.0), vec3(1.0, 1.0, 0.0), smoothstep(0.0, 0.25, t));
    c = mix(c, vec3(0.0, 1.0, 0.0), smoothstep(0.25, 0.5, t));
    c = mix(c, vec3(0.0, 1.0, 1.0), smoothstep(0.5, 0.75, t));
    return mix(c, vec3(0.0, 0.2, 1.0), smoothstep(0.75, 1.0, t));
}

void main() {
    float scanline = 0.9 + 0.1 * sin(texCoord.y * OutSize.y * 3.14159);
    float deviceDepth = texture(DepthSampler, texCoord).r;
    if (deviceDepth <= 0.0) {
        fragColor = vec4(vec3(0.0, 0.0, 0.03) * scanline, 1.0);
        return;
    }

    float z = linearDepth(deviceDepth);
    vec3 color = heat(clamp(z / MaxRange, 0.0, 1.0));
    color *= mix(1.0, 0.3, smoothstep(0.85, 1.0, z / MaxRange));

    vec2 texel = 1.0 / InSize;
    float zx = linearDepth(texture(DepthSampler, texCoord + vec2(texel.x, 0.0)).r);
    float zy = linearDepth(texture(DepthSampler, texCoord + vec2(0.0, texel.y)).r);
    float edge = clamp((abs(zx - z) + abs(zy - z)) / max(z * 0.05, 0.05), 0.0, 1.0);
    color = mix(color, vec3(1.0), edge * 0.6);

    float seconds = GameTime * 24000.0 / 20.0;
    float ring = fract(z / 6.0 - seconds * 1.5);
    color += vec3(0.35) * smoothstep(0.96, 1.0, ring);

    float lum = dot(texture(InSampler, texCoord).rgb, vec3(0.299, 0.587, 0.114));
    color *= 0.75 + 0.35 * lum;

    fragColor = vec4(color * scanline, 1.0);
}
