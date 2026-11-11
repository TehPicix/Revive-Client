#version 330 core

in vec2 v_TexCoord;
in vec2 v_OneTexel;

uniform sampler2D u_Texture;

layout (std140) uniform GuiPost {
    vec2 u_Size;
    float u_Time;
};

out vec4 color;

// Trivial composite: copy the pre-rendered shader framebuffer. Keeps the heavy gui-bg.frag pass off
// the main framebuffer so it can't disturb the chams post-process pass.
void main() {
    color = texture(u_Texture, v_TexCoord);
}
