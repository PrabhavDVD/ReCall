#version 330 core

// Input from vertex shader
in VS_OUT {
    vec3 color;
} fs_in;

// Output color
out vec4 FragColor;

void main() {
    // Output vertex color with full opacity
    FragColor = vec4(fs_in.color, 1.0);
}
