#version 330 core

in vec2 v_TexCoord;
in vec2 v_OneTexel;

uniform sampler2D u_Texture;
uniform sampler2D u_TextureI;

layout (std140) uniform PostData {
    vec2 u_Size;
    float u_Time;
};

layout (std140) uniform ImageData {
    vec4 u_Color;      // fill color (rgba, already includes rainbow + transparency)
    vec4 u_GradStart;  // gradient / secondary color a (rgb)
    vec4 u_GradEnd;    // gradient / secondary color b (rgb)
    vec4 u_Params;     // mode, gradientDir, warpScale, warpSpeed
};

out vec4 color;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

// --- "Base warp fBM" / Noise but Pink (Nebula mode) ---
float nebula_colormap_red(float x) {
    if (x < 0.0) return 54.0 / 255.0;
    else if (x < 20049.0 / 82979.0) return (829.79 * x + 54.51) / 255.0;
    else return 1.0;
}

float nebula_colormap_green(float x) {
    if (x < 20049.0 / 82979.0) return 0.0;
    else if (x < 327013.0 / 810990.0) return (8546482679670.0 / 10875673217.0 * x - 2064961390770.0 / 10875673217.0) / 255.0;
    else if (x <= 1.0) return (103806720.0 / 483977.0 * x + 19607415.0 / 483977.0) / 255.0;
    else return 1.0;
}

float nebula_colormap_blue(float x) {
    if (x < 0.0) return 54.0 / 255.0;
    else if (x < 7249.0 / 82979.0) return (829.79 * x + 54.51) / 255.0;
    else if (x < 20049.0 / 82979.0) return 127.0 / 255.0;
    else if (x < 327013.0 / 810990.0) return (792.02249341361393720147485376583 * x - 64.364790735602331034989206222672) / 255.0;
    else return 1.0;
}

vec3 nebula_colormap(float x) {
    return vec3(nebula_colormap_red(x), nebula_colormap_green(x), nebula_colormap_blue(x));
}

float nebula_rand(vec2 n) {
    return fract(sin(dot(n, vec2(12.9898, 4.1414))) * 43758.5453);
}

float nebula_noise(vec2 p) {
    vec2 ip = floor(p);
    vec2 u = fract(p);
    u = u * u * (3.0 - 2.0 * u);
    float res = mix(
        mix(nebula_rand(ip), nebula_rand(ip + vec2(1.0, 0.0)), u.x),
        mix(nebula_rand(ip + vec2(0.0, 1.0)), nebula_rand(ip + vec2(1.0, 1.0)), u.x), u.y);
    return res * res;
}

float nebula_fbm(vec2 p) {
    const mat2 mtx = mat2(0.80, 0.60, -0.60, 0.80);
    float f = 0.0;
    f += 0.500000 * nebula_noise(p + u_Time); p = mtx * p * 2.02;
    f += 0.031250 * nebula_noise(p);          p = mtx * p * 2.01;
    f += 0.250000 * nebula_noise(p);          p = mtx * p * 2.03;
    f += 0.125000 * nebula_noise(p);          p = mtx * p * 2.01;
    f += 0.062500 * nebula_noise(p);          p = mtx * p * 2.04;
    f += 0.015625 * nebula_noise(p + sin(u_Time));
    return f / 0.96875;
}

float nebula_pattern(vec2 p) {
    return nebula_fbm(p + nebula_fbm(p + nebula_fbm(p)));
}

// --- Space: Infernum DoG sky (kaliset cosmic background) ---
float space_kaliset(vec2 uv, float julia) {
    vec2 p = fract(uv) - 0.5;
    float prevDist = 0.0;
    float totalChange = 0.0;
    for (int j = 0; j < 14; j++) {
        float dProd = dot(p, p);
        if (dProd < 0.00001) dProd = 0.0000001; // keep big stars from punching black holes
        p = abs(p) / dProd;
        p.x -= julia;
        p.y -= julia;
        float d = length(p);
        totalChange += abs(d - prevDist);
        prevDist = d;
    }
    if (totalChange >= 1000.0 || totalChange != totalChange || totalChange > 1.0e30) totalChange = 1000.0;
    return totalChange;
}

// --- Gspace: parallax scrolling fractal galaxy (CBS / Star Nest) ---
float gspace_field(vec3 p, float s) {
    float strength = 7.0 + 0.03 * log(1.0e-6 + fract(sin(u_Time) * 4373.11));
    float accum = s / 4.0;
    float prev = 0.0;
    float tw = 0.0;
    for (int i = 0; i < 26; ++i) {
        float mag = dot(p, p);
        p = abs(p) / mag + vec3(-0.5, -0.4, -1.5);
        float w = exp(-float(i) / 7.0);
        accum += w * exp(-strength * pow(abs(mag - prev), 2.2));
        tw += w;
        prev = mag;
    }
    return max(0.0, 5.0 * accum / tw - 0.7);
}

float gspace_field2(vec3 p, float s) {
    float strength = 7.0 + 0.03 * log(1.0e-6 + fract(sin(u_Time) * 4373.11));
    float accum = s / 4.0;
    float prev = 0.0;
    float tw = 0.0;
    for (int i = 0; i < 18; ++i) {
        float mag = dot(p, p);
        p = abs(p) / mag + vec3(-0.5, -0.4, -1.5);
        float w = exp(-float(i) / 7.0);
        accum += w * exp(-strength * pow(abs(mag - prev), 2.2));
        tw += w;
        prev = mag;
    }
    return max(0.0, 5.0 * accum / tw - 0.7);
}

vec3 gspace_nrand3(vec2 co) {
    vec3 a = fract(cos(co.x * 8.3e-3 + co.y) * vec3(1.3e5, 4.7e5, 2.9e5));
    vec3 b = fract(sin(co.x * 0.3e-3 + co.y) * vec3(8.1e5, 1.0e5, 0.1e5));
    return mix(a, b, 0.5);
}

void main() {
    if (texture(u_Texture, v_TexCoord).a == 0.0) discard;

    int mode = int(u_Params.x + 0.5);
    int dir = int(u_Params.y + 0.5);
    vec3 rgb = u_Color.rgb;

    if (mode == 1) {
        // Gradient
        float t = dir == 0 ? v_TexCoord.y : (dir == 1 ? v_TexCoord.x : (v_TexCoord.x + v_TexCoord.y) * 0.5);
        rgb = mix(u_GradStart.rgb, u_GradEnd.rgb, clamp(t, 0.0, 1.0));
    } else if (mode == 2) {
        // Image
        rgb = u_Color.rgb * texture(u_TextureI, v_TexCoord).rgb;
    } else if (mode == 4) {
        // Warp — "Warp Speed 2" (David Hoskins). u_Params.z = scale, u_Params.w = speed.
        float s = 0.0, v = 0.0;
        vec2 uv = (v_TexCoord * 2.0 - 1.0) * max(0.1, u_Params.z);
        float time = (u_Time * u_Params.w - 2.0) * 58.0;
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
        rgb = tanh(col) * u_Color.rgb;
    } else if (mode == 5) {
        // Nebula — "Base warp fBM" (Noise but Pink).
        vec2 uv = v_TexCoord * (u_Size / u_Size.x);
        float shade = nebula_pattern(uv);
        rgb = nebula_colormap(clamp(shade, 0.0, 1.0)) * u_Color.rgb;
    } else if (mode == 6) {
        // Plasma (Shadertoy "iquilezles"-style domain warp). u_Params.w = speed; tinted by u_Color.
        float t = u_Time * u_Params.w;
        vec2 uv = (2.0 * v_TexCoord - 1.0) * (u_Size / min(u_Size.x, u_Size.y));
        for (float i = 1.0; i < 10.0; i++) {
            uv.x += 0.6 / i * cos(i * 2.5 * uv.y + t);
            uv.y += 0.6 / i * cos(i * 1.5 * uv.x + t);
        }
        float v = 0.1 / abs(sin(t - uv.y - uv.x));
        rgb = u_Color.rgb * v;
    } else if (mode == 7) {
        // Space — Infernum DoG sky (12-layer volumetric kaliset parallax)
        float zoom = 0.5;
        float julia = 0.584;
        float scrollSpeed = 0.0024;
        vec2 coords = v_TexCoord;
        coords.x *= u_Size.x / u_Size.y;
        vec3 frontStarColor = vec3(0.0, 0.44, 0.38); // ForestGreen->Cyan
        vec3 backStarColor = vec3(0.5, 0.0, 0.5);    // Fuchsia
        vec4 result = vec4(0.0);
        float fade = 1.0;
        for (int i = 0; i < 12; i++) {
            float time = u_Time / fade;
            vec2 p = coords * zoom;
            p.y += 1.5;
            p += vec2(time * scrollSpeed, time * scrollSpeed);
            p /= fade;
            float changeSample = space_kaliset(p, julia) * 0.05;
            vec4 layerColor = vec4(mix(frontStarColor, backStarColor, float(i) / 12.0), 1.0);
            result += layerColor * changeSample * fade;
            fade *= 0.9;
        }
        rgb = pow(result.rgb * 0.12, vec3(1.6)) * u_Color.rgb;
    } else if (mode == 8) {
        // Gspace — parallax scrolling fractal galaxy. The original is audio-reactive (iChannel0);
        // with no audio input the sound bands are fixed constants and field()/parallax animate off time.
        const float f0 = 0.5, f1 = 0.5, f2 = 0.5, f3 = 0.5;
        vec2 uv = 2.0 * v_TexCoord - 1.0;
        vec2 uvs = uv * u_Size / max(u_Size.x, u_Size.y);
        vec3 p = vec3(uvs / 4.0, 0.0) + vec3(1.0, -1.3, 0.0);
        p += 0.2 * vec3(sin(u_Time / 16.0), sin(u_Time / 12.0), sin(u_Time / 128.0));

        float t = gspace_field(p, f2);
        float v = (1.0 - exp((abs(uv.x) - 1.0) * 6.0)) * (1.0 - exp((abs(uv.y) - 1.0) * 6.0));

        vec3 p2 = vec3(uvs / (4.0 + sin(u_Time * 0.11) * 0.2 + 0.2 + sin(u_Time * 0.15) * 0.3 + 0.4), 1.5) + vec3(2.0, -1.3, -1.0);
        p2 += 0.25 * vec3(sin(u_Time / 16.0), sin(u_Time / 12.0), sin(u_Time / 128.0));
        float t2 = gspace_field2(p2, f3);
        vec4 c2 = mix(0.4, 1.0, v) * vec4(1.3 * t2 * t2 * t2, 1.8 * t2 * t2, t2 * f0, t2);

        vec2 seed = floor((p.xy * 2.0) * u_Size.x);
        vec4 starcolor = vec4(pow(gspace_nrand3(seed).y, 40.0));
        vec2 seed2 = floor((p2.xy * 2.0) * u_Size.x);
        starcolor += vec4(pow(gspace_nrand3(seed2).y, 40.0));

        vec4 outc = mix(f3 - 0.3, 1.0, v) * vec4(1.5 * f2 * t * t * t, 1.2 * f1 * t * t, f3 * t, 1.0) + c2 + starcolor;
        rgb = outc.rgb * u_Color.rgb;
    }
    // mode 0 (Default) and mode 3 (Rainbow) use u_Color directly

    // Finite-safe output: the heavy modes (Plasma glow, kaliset, galaxy) can produce Inf/NaN, which
    // strict drivers (AMD) render as a black fill. Tame HDR/Inf, then zero any remaining NaN.
    rgb = clamp(rgb, vec3(0.0), vec3(8.0));
    bvec3 finite = equal(rgb, rgb);
    rgb = mix(vec3(0.0), rgb, vec3(finite));

    color = vec4(rgb, u_Color.a);
}
