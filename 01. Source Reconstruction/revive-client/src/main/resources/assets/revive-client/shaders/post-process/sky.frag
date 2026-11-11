#version 330 core

// Ambiance sky pass. Renders a custom sky over the rendered world. Masking to sky-only pixels is done by
// the hardware depth test (the quad is emitted at the far plane in sky.vert, drawn with a LEQUAL test),
// so terrain/entities stay untouched. mode 0 is a solid custom sky color; modes 1-5 reuse the Chams
// shader backgrounds (Warp / Nebula / Plasma / Space / Gspace).

in vec2 v_TexCoord;
in vec2 v_OneTexel;

layout (std140) uniform PostData {
    vec2 u_Size;
    float u_Time;
};

layout (std140) uniform SkyData {
    vec4 u_Primary;    // primary.rgb, opacity
    vec4 u_Secondary;  // secondary.rgb, mode
    vec4 u_Fwd;        // camera forward (xyz), tan(fov/2) in w
    vec4 u_Right;      // camera right   (xyz), aspect ratio in w
    vec4 u_Up;         // camera up      (xyz)
};

out vec4 color;

// ---- Nebula: domain-warped fBM ----
float neb_rand(vec2 n) { return fract(sin(dot(n, vec2(12.9898, 4.1414))) * 43758.5453); }
float neb_noise(vec2 p) {
    vec2 ip = floor(p); vec2 u = fract(p); u = u * u * (3.0 - 2.0 * u);
    float res = mix(mix(neb_rand(ip), neb_rand(ip + vec2(1.0, 0.0)), u.x),
                    mix(neb_rand(ip + vec2(0.0, 1.0)), neb_rand(ip + vec2(1.0, 1.0)), u.x), u.y);
    return res * res;
}
float neb_fbm(vec2 p) {
    const mat2 mtx = mat2(0.80, 0.60, -0.60, 0.80);
    float f = 0.0;
    f += 0.500000 * neb_noise(p + u_Time); p = mtx * p * 2.02;
    f += 0.031250 * neb_noise(p);          p = mtx * p * 2.01;
    f += 0.250000 * neb_noise(p);          p = mtx * p * 2.03;
    f += 0.125000 * neb_noise(p);          p = mtx * p * 2.01;
    f += 0.062500 * neb_noise(p);          p = mtx * p * 2.04;
    f += 0.015625 * neb_noise(p + sin(u_Time));
    return f / 0.96875;
}
float neb_pattern(vec2 p) { return neb_fbm(p + neb_fbm(p + neb_fbm(p))); }

// ---- Space: kaliset ----
float space_kaliset(vec2 uv, float julia) {
    vec2 p = fract(uv) - 0.5;
    float prevDist = 0.0, totalChange = 0.0;
    for (int j = 0; j < 14; j++) {
        float dProd = dot(p, p);
        if (dProd < 0.00001) dProd = 0.0000001;
        p = abs(p) / dProd; p.x -= julia; p.y -= julia;
        float d = length(p); totalChange += abs(d - prevDist); prevDist = d;
    }
    if (totalChange >= 1000.0 || totalChange != totalChange || totalChange > 1.0e30) totalChange = 1000.0;
    return totalChange;
}

// ---- Gspace: parallax galaxy ----
float gs_field(vec3 p, float s, int it) {
    float strength = 7.0 + 0.03 * log(1.0e-6 + fract(sin(u_Time) * 4373.11));
    float accum = s / 4.0, prev = 0.0, tw = 0.0;
    for (int i = 0; i < 26; ++i) {
        if (i >= it) break;
        float mag = dot(p, p);
        p = abs(p) / mag + vec3(-0.5, -0.4, -1.5);
        float w = exp(-float(i) / 7.0);
        accum += w * exp(-strength * pow(abs(mag - prev), 2.2));
        tw += w; prev = mag;
    }
    return max(0.0, 5.0 * accum / tw - 0.7);
}
vec3 gs_nrand3(vec2 co) {
    vec3 a = fract(cos(co.x * 8.3e-3 + co.y) * vec3(1.3e5, 4.7e5, 2.9e5));
    vec3 b = fract(sin(co.x * 0.3e-3 + co.y) * vec3(8.1e5, 1.0e5, 0.1e5));
    return mix(a, b, 0.5);
}

void main() {
    int mode = int(u_Secondary.a + 0.5);
    vec3 primary = u_Primary.rgb;
    vec3 secondary = u_Secondary.rgb;

    vec3 rgb = primary;

    // Reconstruct this pixel's world-space view ray and map it to the pattern with a SEAMLESS azimuthal
    // (stereographic-from-nadir) projection, so the sky is anchored to the world (you pan across a fixed
    // sky as you turn) with no seam. Unlike equirectangular (atan), this is built only from continuous
    // components of the direction, so there's no longitude branch-cut seam - the single singularity sits
    // straight down (the nadir), which is below the horizon and depth-masked anyway. We clamp the
    // denominator so looking down can't blow the coords up.
    vec2 ndc = v_TexCoord * 2.0 - 1.0;
    vec3 dir = normalize(u_Fwd.xyz + u_Right.xyz * (ndc.x * u_Fwd.w * u_Right.w) + u_Up.xyz * (ndc.y * u_Fwd.w));
    // SKY_TILE scales the projection: higher = more on-screen detail. Kept modest so the fBM coords stay
    // in a safe precision range.
    const float SKY_TILE = 1.5;
    float skyDenom = max(dir.y + 1.0, 0.35);
    vec2 vt = vec2(dir.x, dir.z) / skyDenom * SKY_TILE + 0.5;

    if (mode == 1) {
        // Warp — "Warp Speed 2", tinted by primary->secondary.
        float s = 0.0, v = 0.0;
        vec2 uv = (vt * 2.0 - 1.0);
        float time = (u_Time * 0.5 - 2.0) * 58.0;
        vec3 col = vec3(0.0);
        vec3 init = vec3(sin(time * 0.0032) * 0.3, 0.35 - cos(time * 0.005) * 0.3, time * 0.002);
        for (int r = 0; r < 100; r++) {
            vec3 p = init + s * vec3(uv, 0.05);
            p.z = fract(p.z);
            for (int i = 0; i < 10; i++) p = abs(p * 2.04) / dot(p, p) - 0.9;
            v += pow(dot(p, p), 0.7) * 0.06;
            col += vec3(v * 0.2 + 0.4, 12.0 - s * 2.0, 0.1 + v * 1.0) * v * 0.00003;
            s += 0.025;
        }
        rgb = tanh(col) * mix(primary, secondary, tanh(col).g);
    } else if (mode == 2) {
        // Nebula — fBM through a primary->secondary ramp.
        vec2 uv = vt * (u_Size / u_Size.x);
        float shade = clamp(neb_pattern(uv), 0.0, 1.0);
        rgb = mix(primary * 0.15, secondary, shade);
    } else if (mode == 3) {
        // Plasma — domain warp tinted primary->secondary.
        float t = u_Time * 0.6;
        vec2 uv = (2.0 * vt - 1.0) * (u_Size / min(u_Size.x, u_Size.y));
        for (float i = 1.0; i < 10.0; i++) {
            uv.x += 0.6 / i * cos(i * 2.5 * uv.y + t);
            uv.y += 0.6 / i * cos(i * 1.5 * uv.x + t);
        }
        float v = 0.1 / abs(sin(t - uv.y - uv.x));
        rgb = mix(primary, secondary, clamp(v, 0.0, 1.0)) * v;
    } else if (mode == 4) {
        // Space — kaliset cosmic field; primary/secondary are the star colors.
        float zoom = 0.5, julia = 0.584, scrollSpeed = 0.0024;
        vec2 coords = vt; coords.x *= u_Size.x / u_Size.y;
        vec4 result = vec4(0.0);
        float fade = 1.0;
        for (int i = 0; i < 12; i++) {
            float time = u_Time / fade;
            vec2 p = coords * zoom; p.y += 1.5;
            p += vec2(time * scrollSpeed, time * scrollSpeed);
            p /= fade;
            float cs = space_kaliset(p, julia) * 0.05;
            vec3 lc = mix(primary, secondary, float(i) / 12.0);
            result.rgb += lc * cs * fade;
            fade *= 0.9;
        }
        rgb = pow(result.rgb * 0.12, vec3(1.6));
    } else if (mode == 5) {
        // Gspace — parallax fractal galaxy, tinted by theme colors.
        const float f0 = 0.5, f1 = 0.5, f2 = 0.5, f3 = 0.5;
        vec2 uv = 2.0 * vt - 1.0;
        vec2 uvs = uv * u_Size / max(u_Size.x, u_Size.y);
        vec3 p = vec3(uvs / 4.0, 0.0) + vec3(1.0, -1.3, 0.0);
        p += 0.2 * vec3(sin(u_Time / 16.0), sin(u_Time / 12.0), sin(u_Time / 128.0));
        float t = gs_field(p, f2, 26);
        // Soft frame: the original assumes uv in [-1,1]; clamp the exponent to <= 0 so the world-anchored
        // coords (|uv| can exceed 1) can't blow exp() up to +inf and break the whole galaxy.
        float v = (1.0 - exp(min(abs(uv.x) - 1.0, 0.0) * 6.0)) * (1.0 - exp(min(abs(uv.y) - 1.0, 0.0) * 6.0));
        vec3 p2 = vec3(uvs / (4.0 + sin(u_Time * 0.11) * 0.2 + 0.2 + sin(u_Time * 0.15) * 0.3 + 0.4), 1.5) + vec3(2.0, -1.3, -1.0);
        p2 += 0.25 * vec3(sin(u_Time / 16.0), sin(u_Time / 12.0), sin(u_Time / 128.0));
        float t2 = gs_field(p2, f3, 18);
        vec4 c2 = mix(0.4, 1.0, v) * vec4(secondary * (1.3 * t2 * t2 * t2), t2);
        vec2 seed = floor((p.xy * 2.0) * u_Size.x);
        vec4 starcolor = vec4(pow(gs_nrand3(seed).y, 40.0));
        vec2 seed2 = floor((p2.xy * 2.0) * u_Size.x);
        starcolor += vec4(pow(gs_nrand3(seed2).y, 40.0));
        vec4 outc = mix(f3 - 0.3, 1.0, v) * vec4(primary * (t * t), 1.0) + c2 + starcolor;
        rgb = outc.rgb;
    }
    // mode 0 (Color) uses primary directly.

    // Finite-safe output (see image.frag): tame HDR/Inf, zero any remaining NaN.
    rgb = clamp(rgb, vec3(0.0), vec3(8.0));
    bvec3 finite = equal(rgb, rgb);
    rgb = mix(vec3(0.0), rgb, vec3(finite));

    color = vec4(rgb, u_Primary.a);
}
