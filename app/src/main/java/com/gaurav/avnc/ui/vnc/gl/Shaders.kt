/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc.gl

/**
 * Shaders used for rendering framebuffer
 */
object Shaders {
    //language=GLSL
    const val VERTEX_SHADER = """
        uniform mat4 u_Projection;
        uniform mat4 u_ViewMatrix;

        attribute vec3 a_Position;
        attribute vec2 a_TextureCoordinates; // Restored
        // attribute vec3 a_Normal; // Keep commented if not actively used for lighting yet

        varying vec2 v_TextureCoordinates;   // Restored
        // varying vec3 v_Normal;           // Keep commented

        void main() {
            v_TextureCoordinates = a_TextureCoordinates; // Restored
            // v_Normal = a_Normal;                     // Keep commented
            gl_Position = u_Projection * u_ViewMatrix * vec4(a_Position, 1.0);
        }
    """

    //language=GLSL
    const val FRAGMENT_SHADER = """
        precision mediump float;

        uniform sampler2D u_TextureUnit;
        varying vec2 v_TextureCoordinates;

        void main() {
            vec4 textureColor = texture2D(u_TextureUnit, v_TextureCoordinates);
            vec3 correctedColor = vec3(textureColor.b, textureColor.g, textureColor.r);
            // Use texture's original alpha
            gl_FragColor = vec4(correctedColor, textureColor.a);
        }
    """
}