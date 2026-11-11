#version 330 core

layout (location = 0) in vec4 pos;

layout (std140) uniform PostData {
    vec2 u_Size;
    float u_Time;
};

out vec2 v_TexCoord;
out vec2 v_OneTexel;

void main() {
    // Emit the fullscreen quad just inside the far plane (NDC z slightly < 1.0). It must sit behind all
    // real geometry so a LEQUAL depth test only lets it draw on sky pixels (which keep the cleared
    // far-plane depth of 1.0), but NOT exactly at z == w: a quad on the far plane gets clipped at the
    // boundary, leaving triangular wedges instead of a full-screen sky.
    gl_Position = vec4(pos.xy, 0.99999, 1.0);

    v_TexCoord = (pos.xy + 1.0) / 2.0;
    v_OneTexel = 1.0 / u_Size;
}
