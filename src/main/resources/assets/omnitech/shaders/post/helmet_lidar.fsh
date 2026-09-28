#version 330
#extension GL_ARB_separate_shader_objects : require

// Space-suit helmet lidar backdrop: the world is fogged to black by the client and the
// glowing return points are drawn as particles; this pass only blacks out the sky
// (no depth = no return) and adds faint scanlines.

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(location = 0) out vec4 fragColor;

void main() {
    float scanline = 0.93 + 0.07 * sin(texCoord.y * OutSize.y * 3.14159);
    vec3 color = texture(DepthSampler, texCoord).r <= 0.0 ? vec3(0.0) : texture(InSampler, texCoord).rgb;
    fragColor = vec4(color * scanline, 1.0);
}
