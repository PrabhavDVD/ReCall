#version 330 core

// Input vertex attributes
layout(location = 0) in vec3 position;
layout(location = 1) in vec3 color;

// Output to fragment shader
out VS_OUT {
    vec3 color;
} vs_out;

// Transformation matrices
uniform mat4 projection;
uniform mat4 view;
uniform mat4 model;

void main() {
    // Transform vertex to screen space
    gl_Position = projection * view * model * vec4(position, 1.0);

    // Pass color to fragment shader
    vs_out.color = color;
}
