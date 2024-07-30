/*
 * Copyright (c) 2024, Ekqrnw <ekqrnw@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.vr;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.freetype.FreeType;
import com.badlogic.gdx.utils.GdxNativesLoader;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.HitsplatApplied;
import org.joml.Matrix4f;
import org.joml.Vector2i;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.io.File;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;

import static org.joml.Math.cos;
import static org.joml.Math.sin;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL30.glBindVertexArray;

public class HudHelper {

    class Character {
        public int id;
        public Vector2i size;
        public Vector2i bearing;
        public int advance;
    }

    //class HitsplatTexture {
    //    public int id;
    //}

    private Map<java.lang.Character, Character> characters;

    private Map<Integer, Integer> hitsplatTex;

    private int uniHudProjection;
    private int uniHudProjection2;

    private int uniHudView;

    private int uniHudLoc;

    private int uniHudCol;

    private int uniHud3Projection;
    private int uniHud3Projection2;

    private int uniHud3View;

    private int uniHud3Loc;

    private int vboHud3Handle;
    private int vaoHud3Handle;
    static int glHud3Program;

    private int vboHudHandle;
    private int vaoHudHandle;
    static int glHudProgram;

    private int uniHud2Projection;
    private int uniHud2Projection2;

    private int uniHud2View;

    private int uniHud2Loc;

    private int vboHud2Handle;
    private int vaoHud2Handle;
    static int glHud2Program;

    //TileInterpolator interpolator;

    HudHelper() {
        //interpolator = new TileInterpolator();
        uniHud2Projection = GL43C.glGetUniformLocation(glHud2Program, "projection");
        uniHud2Projection2 = GL43C.glGetUniformLocation(glHud2Program, "projection2");
        uniHud2View = GL43C.glGetUniformLocation(glHud2Program, "viewMatrix");
        uniHud2Loc= GL43C.glGetUniformLocation(glHud2Program, "loc");

        characters = new HashMap<>();
        hitsplatTex = new HashMap<>();

        uniHudProjection = GL43C.glGetUniformLocation(glHudProgram, "projection");
        uniHudProjection2 = GL43C.glGetUniformLocation(glHudProgram, "projection2");
        uniHudView = GL43C.glGetUniformLocation(glHudProgram, "viewMatrix");
        uniHudLoc= GL43C.glGetUniformLocation(glHudProgram, "loc");
        uniHudCol= GL43C.glGetUniformLocation(glHudProgram, "col");

        uniHud3Projection = GL43C.glGetUniformLocation(glHud3Program, "projection");
        uniHud3Projection2 = GL43C.glGetUniformLocation(glHud3Program, "projection2");
        uniHud3View = GL43C.glGetUniformLocation(glHud3Program, "viewMatrix");
        uniHud3Loc= GL43C.glGetUniformLocation(glHud3Program, "loc");

        vaoHudHandle = GL43C.glGenVertexArrays();
        vboHudHandle = GL43C.glGenBuffers();
        glBindVertexArray(vaoHudHandle);
        glBindBuffer(GL_ARRAY_BUFFER, vboHudHandle);
        GL43C.glBufferData(GL_ARRAY_BUFFER, GL43C.GL_FLOAT * 6 * 5, GL_DYNAMIC_DRAW);
        GL43C.glVertexAttribPointer(0, 3, GL43C.GL_FLOAT, false, 5 * Float.BYTES, 0);
        GL43C.glEnableVertexAttribArray(0);

        // texture coord attribute
        GL43C.glVertexAttribPointer(1, 2, GL43C.GL_FLOAT, false, 5 * Float.BYTES, 3 * Float.BYTES);
        GL43C.glEnableVertexAttribArray(1);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        vaoHud2Handle = GL43C.glGenVertexArrays();
        vboHud2Handle = GL43C.glGenBuffers();
        glBindVertexArray(vaoHud2Handle);
        glBindBuffer(GL_ARRAY_BUFFER, vboHud2Handle);
        GL43C.glBufferData(GL_ARRAY_BUFFER, GL43C.GL_FLOAT * 6 * 12, GL_DYNAMIC_DRAW);
        GL43C.glVertexAttribPointer(0, 3, GL43C.GL_FLOAT, false, 6 * Float.BYTES, 0);
        GL43C.glEnableVertexAttribArray(0);

        // texture coord attribute
        GL43C.glVertexAttribPointer(1, 3, GL43C.GL_FLOAT, false, 6 * Float.BYTES, 3 * Float.BYTES);
        GL43C.glEnableVertexAttribArray(1);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        vaoHud3Handle = GL43C.glGenVertexArrays();
        vboHud3Handle = GL43C.glGenBuffers();
        glBindVertexArray(vaoHud3Handle);
        glBindBuffer(GL_ARRAY_BUFFER, vboHud3Handle);
        GL43C.glBufferData(GL_ARRAY_BUFFER, GL43C.GL_FLOAT * 6 * 5, GL_DYNAMIC_DRAW);
        GL43C.glVertexAttribPointer(0, 3, GL43C.GL_FLOAT, false, 5 * Float.BYTES, 0);
        GL43C.glEnableVertexAttribArray(0);

        // texture coord attribute
        GL43C.glVertexAttribPointer(1, 2, GL43C.GL_FLOAT, false, 5 * Float.BYTES, 3 * Float.BYTES);
        GL43C.glEnableVertexAttribArray(1);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        GdxNativesLoader.load();
        FreeType.Library lib = FreeType.initFreeType();
        File file = new File("resources/com/vr/fonts/runescape.ttf");
        FreeType.Face face = lib.newFace(new FileHandle(file), 0);
        face.setPixelSizes(0, 16);
        GL43C.glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        for(char ch: CalcHelper.fontWidths.keySet()) {
            face.loadChar(ch, FreeType.FT_LOAD_RENDER);
            FreeType.Bitmap bitmap = face.getGlyph().getBitmap();
            int texture = GL43C.glGenTextures();
            GL43C.glBindTexture(GL43C.GL_TEXTURE_2D, texture);
            GL43C.glTexImage2D(GL43C.GL_TEXTURE_2D,
                    0,
                    GL43C.GL_RED,
                    bitmap.getWidth(),
                    bitmap.getRows(),
                    0, GL43C.GL_RED,
                    GL43C.GL_UNSIGNED_BYTE,
                    bitmap.getBuffer()
            );
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

            Character character = new Character();
            character.id = texture;
            character.size = new Vector2i(bitmap.getWidth(), bitmap.getRows());
            character.bearing = new Vector2i(face.getGlyph().getBitmapLeft(), face.getGlyph().getBitmapTop());
            character.advance = face.getGlyph().getAdvanceX();
            characters.put(ch, character);
        }
        addHitsplatTexture(HitsplatID.BLOCK_ME,"self_miss_hitsplat.png");
        addHitsplatTexture(HitsplatID.BLOCK_OTHER,"other_miss_hitsplat.png");
        addHitsplatTexture(HitsplatID.DAMAGE_ME,"self_damage_hitsplat.png");
        addHitsplatTexture(HitsplatID.DAMAGE_OTHER,"other_damage_hitsplat.png");
        addHitsplatTexture(HitsplatID.POISON,"poison_hitsplat.png");
        addHitsplatTexture(HitsplatID.DISEASE,"disease_hitsplat.png");
        addHitsplatTexture(HitsplatID.VENOM,"venom_hitsplat.png");
        addHitsplatTexture(HitsplatID.HEAL,"heal_hitsplat.png");
        addHitsplatTexture(HitsplatID.CYAN_UP,"alt_charge_hitsplat.png");
        addHitsplatTexture(HitsplatID.CYAN_DOWN,"alt_uncharge_hitsplat.png");
        addHitsplatTexture(HitsplatID.DAMAGE_ME_CYAN,"self_shield_hitsplat.png");
        addHitsplatTexture(HitsplatID.DAMAGE_OTHER_CYAN,"other_shield_hitsplat.png");
        addHitsplatTexture(HitsplatID.DAMAGE_ME_ORANGE,"self_armour_hitsplat.png");
        addHitsplatTexture(HitsplatID.DAMAGE_OTHER_ORANGE,"other_armour_hitsplat.png");
        addHitsplatTexture(HitsplatID.DAMAGE_ME_YELLOW,"self_charge_hitsplat.png");
        addHitsplatTexture(HitsplatID.DAMAGE_OTHER_YELLOW,"other_charge_hitsplat.png");
        addHitsplatTexture(HitsplatID.DAMAGE_ME_WHITE,"self_uncharge_hitsplat.png");
        addHitsplatTexture(HitsplatID.DAMAGE_OTHER_WHITE,"self_uncharge_hitsplat.png");
        addHitsplatTexture(HitsplatID.DAMAGE_MAX_ME,"max_hitsplat.png");
        addHitsplatTexture(HitsplatID.DAMAGE_MAX_ME_CYAN,"max_shield_hitsplat.png");
        addHitsplatTexture(HitsplatID.DAMAGE_MAX_ME_ORANGE,"max_armour_hitsplat.png");
        addHitsplatTexture(HitsplatID.DAMAGE_MAX_ME_YELLOW,"max_charge_hitsplat.png");
        addHitsplatTexture(HitsplatID.DAMAGE_MAX_ME_WHITE,"max_uncharge_hitsplat.png");
        addHitsplatTexture(HitsplatID.DAMAGE_ME_POISE,"self_poise_hitsplat.png");
        addHitsplatTexture(HitsplatID.DAMAGE_OTHER_POISE,"other_poise_hitsplat.png");
        addHitsplatTexture(HitsplatID.DAMAGE_MAX_ME_POISE,"max_poise_hitsplat.png");
        addHitsplatTexture(HitsplatID.CORRUPTION,"corruption_hitsplat.png");
        addHitsplatTexture(HitsplatID.PRAYER_DRAIN,"other_poise_hitsplat.png");
        addHitsplatTexture(HitsplatID.BLEED,"other_poise_hitsplat.png");
        addHitsplatTexture(HitsplatID.SANITY_DRAIN,"other_poise_hitsplat.png");
        addHitsplatTexture(HitsplatID.SANITY_RESTORE,"other_poise_hitsplat.png");
        addHitsplatTexture(HitsplatID.DOOM,"other_poise_hitsplat.png");
        addHitsplatTexture(HitsplatID.BURN,"other_poise_hitsplat.png");
    }

    void addHitsplatTexture(int id, String resource){
        int width;
        int height;
        ByteBuffer buffer;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);
            //URL url = HudHelper.class.getResource("resources/com/extendedhitsplats/hitplats/osrs/" + resource);
            File file = new File("resources/com/extendedhitsplats/hitsplats/osrs/" + resource);
            String filePath = file.getAbsolutePath();
            buffer = STBImage.stbi_load(filePath, w, h, channels, 4);
            if(buffer != null) {
                width = w.get();
                height = h.get();
                int texture = GL43C.glGenTextures();
                GL43C.glBindTexture(GL43C.GL_TEXTURE_2D, texture);
                GL43C.glTexImage2D(GL43C.GL_TEXTURE_2D,
                        0,
                        GL43C.GL_RGBA,
                        width,
                        height,
                        0, GL43C.GL_RGBA,
                        GL43C.GL_UNSIGNED_BYTE,
                        buffer
                );
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                STBImage.stbi_image_free(buffer);
                hitsplatTex.put(id, texture);
            }
        }
    }

    private Map<Actor, ArrayList<Hitsplat>> hitsplats = new HashMap<>();

    public static Map<Integer, Point> splatPoints = new HashMap<Integer, Point>() {
        {
            put(0, new Point(0, 0));
            put(1, new Point(0, -20));
            put(2, new Point(20, -10));
            put(3, new Point(-20, -10));
        }
    };

    public void cullHitsplats(int timeout){
        HashSet<Actor> actorsR = new HashSet<>();
        for(Actor actor: hitsplats.keySet()){
            HashSet<Hitsplat> hitsplatR = new HashSet<>();
            ArrayList<Hitsplat> hitsplatL = hitsplats.get(actor);
            for(Hitsplat hitsplat: hitsplatL){
                if(timeout > hitsplat.getDisappearsOnGameCycle()){
                    hitsplatR.add(hitsplat);
                }
            }
            for(Hitsplat hitsplat: hitsplatR){
                hitsplatL.remove(hitsplat);
                if(hitsplatL.isEmpty()){
                    actorsR.add(actor);
                    break;
                }
            }
        }
        for(Actor actor: actorsR){
            hitsplats.remove(actor);
        }
    }

    /*public void updateLocations(){
        //interpolator.updateLocations();
    }*/
    public void addHitsplat(HitsplatApplied hitsplatApplied){
        Actor actor = hitsplatApplied.getActor();
        Hitsplat hitsplat = hitsplatApplied.getHitsplat();
        if(!hitsplats.containsKey(actor)){
            hitsplats.put(actor, new ArrayList<Hitsplat>());
        }
        hitsplats.get(actor).add(hitsplat);
    }

    private static FloatBuffer mvpMatrix = BufferUtils.createFloatBuffer(16);

    private Map<Actor, Act> actors = new HashMap<>();

    private Map<Actor, Integer> healthbars = new HashMap<>();

    class Act{
        int orientation;
        int x;
        int y;
        int z;

        Act(int orientation, int x, int y, int z){
           this.orientation = orientation;
           this.x = x;
           this.y = y;
           this.z = z;
        }
    }

    void backloadActor(Actor actor, int orientation, int x, int y, int z){
        //interpolator.addActor(actor);
        actors.put(actor, new Act(orientation,x,y,z));
    }

    void drawHitsplats(Actor actor, float xoffset, float yoffset, float zoffset, Matrix4f viewMatrix, Matrix4f projectionMatrix, float[] projectionMatrix2){
        Act act = actors.get(actor);
        int x = act.x;
        int y = act.y;
        int z = act.z;
        int orientation = act.orientation;
        //System.out.println("ACTOR: "+actor);
        if(!hitsplats.containsKey(actor)){}
        else {
            float tiles = (((actor.getWorldArea().getWidth()-1)/2)+((actor.getWorldArea().getHeight()-1)/2))/2.0f;
            ArrayList<Hitsplat> hits = hitsplats.get(actor);
            GL43C.glEnable(GL43C.GL_BLEND);
            GL43C.glBlendFunc(GL43C.GL_SRC_ALPHA, GL43C.GL_ONE_MINUS_SRC_ALPHA);

            //System.out.println("CRIER: "+xoffset+" "+yoffset+" "+zoffset);
            //System.out.println(projectionMatrix2);
            //Matrix4f m = new Matrix4f().mul(projectionMatrix2).mul();//.mul(projectionMatrix2);
            //System.out.println("CRIER: "+m.get(0,0)+" "+m.get(0,1)+" "+m.get(0,2));
            ////Matrix4f mat = new Matrix4f().mul(projectionMatrix2).mul(new Matrix4f(xoffset,yoffset,zoffset,1,0,0,0,0,0,0,0,0,0,0,0,0));//;//m.get(0,0), m.get(0,1), m.get(0,2));
            ////Matrix4f trans = new Matrix4f().translation(mat.get(0,0)/mat.get(0,3),mat.get(0,1)/mat.get(0,3),mat.get(0,2)/mat.get(0,3));
            //System.out.println(new Matrix4f(xoffset,0,0,0,yoffset,0,0,0,zoffset,0,0,0,1,0,0,0));
            ////System.out.println(mat);
            ////System.out.println(trans);

            // Use the texture bound in the first pass
            GL43C.glUseProgram(glHud3Program);
            GL43C.glUniformMatrix4fv(uniHud3View, false, viewMatrix.get(mvpMatrix));
            GL43C.glUniformMatrix4fv(uniHud3Projection, false, projectionMatrix.get(mvpMatrix));
            GL43C.glUniformMatrix4fv(uniHud3Projection2, false, projectionMatrix2);
            float[] real = new float[]{xoffset, yoffset, zoffset, 1.0f};
            rotateAndTranslate(real,x,y,z,orientation);
            GL43C.glUniform4fv(uniHud3Loc, real);

            GL43C.glBindTexture(GL43C.GL_TEXTURE_2D, 0);
            // Texture on UI
            GL43C.glBindVertexArray(vaoHud3Handle);

            for(int i = 0; i < Math.min(hits.size(),splatPoints.size()); i++){
                Point point = splatPoints.get(i);
                Hitsplat hit = hits.get(i);

                float xpos = point.getX()/10.0f*0.016f;
                float ypos = point.getY()/10.0f*0.016f;

                float w = 0.016f;
                float h = 0.032f;
                //System.out.println(cha+" "+xpos+" "+ypos+" "+w+" "+h);
                // update VBO for each character
                float[] vertices = new float[]{
                        xpos-w,    ypos + h,   0.044f+0.088f*tiles, 0.0f, 0.0f ,
                        xpos-w,     ypos,      0.044f+0.088f*tiles,0.0f, 1.0f ,
                        xpos+w, ypos,      0.044f+0.088f*tiles,1.0f, 1.0f ,

                        xpos-w,     ypos + h,  0.044f+0.088f*tiles,0.0f, 0.0f ,
                        xpos+w, ypos,      0.044f+0.088f*tiles,1.0f, 1.0f ,
                        xpos+w, ypos + h,  0.044f+0.088f*tiles,1.0f, 0.0f };
                // render glyph texture over quad
                glBindTexture(GL_TEXTURE_2D, hitsplatTex.get(hit.getHitsplatType()));
                // update content of VBO memory
                glBindBuffer(GL_ARRAY_BUFFER, vboHud3Handle);
                glBufferSubData(GL_ARRAY_BUFFER, 0, vertices);
                glBindBuffer(GL_ARRAY_BUFFER, 0);
                // render quad
                glDrawArrays(GL_TRIANGLES, 0, 6);
                // now advance cursors for next glyph (note that advance is number of 1/64 pixels)

            }

            GL43C.glEnable(GL43C.GL_BLEND);
            GL43C.glBlendFunc(GL43C.GL_SRC_ALPHA, GL43C.GL_ONE_MINUS_SRC_ALPHA);

            //System.out.println("CRIER: "+xoffset+" "+yoffset+" "+zoffset);
            //System.out.println(projectionMatrix2);
            //Matrix4f m = new Matrix4f().mul(projectionMatrix2).mul();//.mul(projectionMatrix2);
            //System.out.println("CRIER: "+m.get(0,0)+" "+m.get(0,1)+" "+m.get(0,2));
            ////Matrix4f mat = new Matrix4f().mul(projectionMatrix2).mul(new Matrix4f(xoffset,yoffset,zoffset,1,0,0,0,0,0,0,0,0,0,0,0,0));//;//m.get(0,0), m.get(0,1), m.get(0,2));
            ////Matrix4f trans = new Matrix4f().translation(mat.get(0,0)/mat.get(0,3),mat.get(0,1)/mat.get(0,3),mat.get(0,2)/mat.get(0,3));
            //System.out.println(new Matrix4f(xoffset,0,0,0,yoffset,0,0,0,zoffset,0,0,0,1,0,0,0));
            ////System.out.println(mat);
            ////System.out.println(trans);

            // Use the texture bound in the first pass
            GL43C.glUseProgram(glHudProgram);
            GL43C.glUniformMatrix4fv(uniHudView, false, viewMatrix.get(mvpMatrix));
            GL43C.glUniformMatrix4fv(uniHudProjection, false, projectionMatrix.get(mvpMatrix));
            GL43C.glUniformMatrix4fv(uniHudProjection2, false, projectionMatrix2);
            GL43C.glUniform4fv(uniHudLoc, real);
            GL43C.glUniform3fv(uniHudCol, new float[]{1.0f,1.0f,1.0f});

            GL43C.glBindTexture(GL43C.GL_TEXTURE_2D, 0);
            // Texture on UI
            GL43C.glBindVertexArray(vaoHudHandle);

            for(int i = 0; i < Math.min(hits.size(),splatPoints.size()); i++) {
                float x2 = 0;
                float y2 = 0.010f;
                float scale = 0.001f;
                Hitsplat hit = hits.get(i);
                String overhead = String.valueOf(hit.getAmount());
                for (char cha : overhead.toCharArray()) {
                    Character ch = characters.get(cha);
                    x2 += ch.bearing.x * scale + ch.size.x * scale;
                }
                x2 /= -2.0f;

                for (char cha : overhead.toCharArray()) {
                    Character ch = characters.get(cha);

                    float xpos = x2 + ch.bearing.x * scale;
                    float ypos = y2 - (ch.size.y - ch.bearing.y) * scale;

                    float w = ch.size.x * scale;
                    float h = ch.size.y * scale;

                    //System.out.println(cha+" "+xpos+" "+ypos+" "+w+" "+h);
                    // update VBO for each character
                    float[] vertices = new float[]
                            {xpos, ypos + h, 0.044f+0.088f*tiles+0.001f, 0.0f, 0.0f,
                                    xpos, ypos, 0.044f+0.088f*tiles+0.001f, 0.0f, 1.0f,
                                    xpos + w, ypos, 0.044f+0.088f*tiles+0.001f, 1.0f, 1.0f,

                                    xpos, ypos + h, 0.044f+0.088f*tiles+0.001f, 0.0f, 0.0f,
                                    xpos + w, ypos, 0.044f+0.088f*tiles+0.001f, 1.0f, 1.0f,
                                    xpos + w, ypos + h, 0.044f+0.088f*tiles+0.001f, 1.0f, 0.0f};
                    // render glyph texture over quad
                    glBindTexture(GL_TEXTURE_2D, ch.id);
                    // update content of VBO memory
                    glBindBuffer(GL_ARRAY_BUFFER, vboHudHandle);
                    glBufferSubData(GL_ARRAY_BUFFER, 0, vertices);
                    glBindBuffer(GL_ARRAY_BUFFER, 0);
                    // render quad
                    glDrawArrays(GL_TRIANGLES, 0, 6);
                    // now advance cursors for next glyph (note that advance is number of 1/64 pixels)
                    x2 += (ch.advance >> 6) * scale; // bitshift by 6 to get value in pixels (2^6 = 64)

                }
            }

            // Reset
            GL43C.glBindTexture(GL43C.GL_TEXTURE_2D, 0);
            GL43C.glBindVertexArray(0);
            GL43C.glUseProgram(0);
            GL43C.glBlendFunc(GL43C.GL_SRC_ALPHA, GL43C.GL_ONE_MINUS_SRC_ALPHA);
            GL43C.glDisable(GL43C.GL_BLEND);


        }
    }

    void rotateAndTranslate(float[] xyzw, int x, int y, int z, int orientation){
        float rad = orientation * (float)(Math.PI)/1024.0f;
        float s = sin(rad);
        float c = cos(rad);
        float x2 = xyzw[2] * s + xyzw[0] * c;
        float z2 = xyzw[2] * c - xyzw[0] * s;
        xyzw[0] = x2+x;
        xyzw[1] = xyzw[1]+y;
        xyzw[2] = z2+z;
    }

    void swap(Client client){
        Set<Actor> remove = new HashSet<>();
        for(Actor actor: actors.keySet()){
            if(actor.getModel() == null){
                remove.add(actor);
            }
        }
        for(Actor actor: remove){
            actors.remove(actor);
            //interpolator.actors.remove(actor);
        }
    }

    public void addHealthbarTimeout(Actor actor, int timeout){
        healthbars.put(actor, timeout);
    }

    public void cullHealthbars(int timeout){
        HashSet<Actor> actors = new HashSet<>();
        for(Actor actor: healthbars.keySet()){
            if(healthbars.get(actor) < timeout){
                actors.add(actor);
            }
        }
        for(Actor actor: actors){
            healthbars.remove(actor);
        }
    }

    public void cullAll(){
        actors.clear();
        healthbars.clear();
        hitsplats.clear();
    }

    void drawAll(Actor actor, Matrix4f viewMatrix, Matrix4f projectionMatrix, float[] projectionMatrix2){
        if((actor.getOverheadCycle() <= 0 || actor.getOverheadText() == null) && !healthbars.containsKey(actor)) {}
        else {
            Model model = actor.getModel();
            if (model.getUnskewedModel() != null) {
                model = model.getUnskewedModel();
            }

            final int vertexCount = model.getVerticesCount();
            final float[] verticesX = model.getVerticesX();
            final float[] verticesY = model.getVerticesY();
            final float[] verticesZ = model.getVerticesZ();

            float xoffset = 0;
            float zoffset = 0;
            float yoffset = Integer.MAX_VALUE;

            for (int v = 0; v < vertexCount; ++v) {

                if (verticesY[v] < yoffset) {
                    yoffset = verticesY[v];
                    //xoffset = verticesX[v];
                    //zoffset = verticesZ[v];
                }
            }

            int radius = model.getRadius();

            drawOverheadText(actor, xoffset, yoffset, zoffset, viewMatrix, projectionMatrix, projectionMatrix2);
            drawHealthbar(actor, xoffset, yoffset, zoffset, viewMatrix, projectionMatrix, projectionMatrix2);
            drawHitsplats(actor, xoffset, yoffset/2, zoffset, viewMatrix, projectionMatrix, projectionMatrix2);
        }
    }

    void drawHealthbar(Actor actor, float xoffset, float yoffset, float zoffset, Matrix4f viewMatrix, Matrix4f projectionMatrix, float[] projectionMatrix2){
        Act act = actors.get(actor);
        int x = act.x;
        int y = act.y;
        int z = act.z;
        int orientation = act.orientation;
        //System.out.println("ACTOR: "+actor);
        if(!healthbars.containsKey(actor)){}
        else {
            //System.out.println("DRAWING!");
            //GL43C.glEnable(GL43C.GL_BLEND);
            //GL43C.glBlendFunc(GL43C.GL_SRC_ALPHA, GL43C.GL_ONE_MINUS_SRC_ALPHA);

            int scale = actor.getHealthScale();
            int ratio = actor.getHealthRatio();
            if(scale == -1 || ratio == -1) return;

            float height = 0.007f;
            float off = (actor.getOverheadCycle() > 0)? 0.026f:0.00f;
            float width = 0.07f*scale/30.0f;


            GL43C.glUseProgram(glHud2Program);
            GL43C.glUniformMatrix4fv(uniHud2View, false, viewMatrix.get(mvpMatrix));
            GL43C.glUniformMatrix4fv(uniHud2Projection, false, projectionMatrix.get(mvpMatrix));
            GL43C.glUniformMatrix4fv(uniHud2Projection2, false, projectionMatrix2);

            float[] real = new float[]{xoffset, yoffset, zoffset, 1.0f};
            rotateAndTranslate(real,x,y,z,orientation);
            GL43C.glUniform4fv(uniHud2Loc, real);

            //GL43C.glBindTexture(GL43C.GL_TEXTURE_2D, 0);
            // Texture on UI
            GL43C.glBindVertexArray(vaoHud2Handle);

            if(actor.isDead()) ratio = 0;

            float v = -width / 2.0f + width * ratio / scale;
            float[] vertices = new float[]{
                    -width/2.0f, off, 0.0f, 0.0f, 1.0f, 0.0f,
                    -width/2.0f, height+off, 0.0f, 0.0f, 1.0f, 0.0f,
                    v, off, 0.0f, 0.0f, 1.0f, 0.0f,

                    -width/2.0f, height+off, 0.0f, 0.0f, 1.0f, 0.0f,
                    v, height+off, 0.0f, 0.0f, 1.0f, 0.0f,
                    v, off, 0.0f, 0.0f, 1.0f, 0.0f,

                    v, off, 0.0f, 1.0f, 0.0f, 0.0f,
                    v, height+off, 0.0f, 1.0f, 0.0f, 0.0f,
                    width/2.0f, off, 0.0f, 1.0f, 0.0f, 0.0f,

                    v, height+off, 0.0f, 1.0f, 0.0f, 0.0f,
                    width/2.0f, height+off, 0.0f, 1.0f, 0.0f, 0.0f,
                    width/2.0f, off, 0.0f, 1.0f, 0.0f, 0.0f,
            };
            // update content of VBO memory
            glBindBuffer(GL_ARRAY_BUFFER, vboHud2Handle);
            glBufferSubData(GL_ARRAY_BUFFER, 0, vertices);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            // render quad
            glDrawArrays(GL_TRIANGLES, 0, 12);
            // now advance cursors for next glyph (note that advance is number of 1/64 pixels)

            // Reset
            GL43C.glBindVertexArray(0);
            GL43C.glUseProgram(0);
            GL43C.glBlendFunc(GL43C.GL_SRC_ALPHA, GL43C.GL_ONE_MINUS_SRC_ALPHA);
            GL43C.glDisable(GL43C.GL_BLEND);
        }
    }

    void drawOverheadText(Actor actor, float xoffset, float yoffset, float zoffset, Matrix4f viewMatrix, Matrix4f projectionMatrix, float[] projectionMatrix2){
        Act act = actors.get(actor);
        int x = act.x;
        int y = act.y;
        int z = act.z;
        int orientation = act.orientation;
        if(actor.getOverheadCycle() <= 0 || actor.getOverheadText() == null) {}
        else {
            String overhead = actor.getOverheadText();
            GL43C.glEnable(GL43C.GL_BLEND);
            GL43C.glBlendFunc(GL43C.GL_SRC_ALPHA, GL43C.GL_ONE_MINUS_SRC_ALPHA);

            //System.out.println("CRIER: "+xoffset+" "+yoffset+" "+zoffset);
            //System.out.println(projectionMatrix2);
            //Matrix4f m = new Matrix4f().mul(projectionMatrix2).mul();//.mul(projectionMatrix2);
            //System.out.println("CRIER: "+m.get(0,0)+" "+m.get(0,1)+" "+m.get(0,2));
            ////Matrix4f mat = new Matrix4f().mul(projectionMatrix2).mul(new Matrix4f(xoffset,yoffset,zoffset,1,0,0,0,0,0,0,0,0,0,0,0,0));//;//m.get(0,0), m.get(0,1), m.get(0,2));
            ////Matrix4f trans = new Matrix4f().translation(mat.get(0,0)/mat.get(0,3),mat.get(0,1)/mat.get(0,3),mat.get(0,2)/mat.get(0,3));
            //System.out.println(new Matrix4f(xoffset,0,0,0,yoffset,0,0,0,zoffset,0,0,0,1,0,0,0));
            ////System.out.println(mat);
            ////System.out.println(trans);

            // Use the texture bound in the first pass
            GL43C.glUseProgram(glHudProgram);
            GL43C.glUniformMatrix4fv(uniHudView, false, viewMatrix.get(mvpMatrix));
            GL43C.glUniformMatrix4fv(uniHudProjection, false, projectionMatrix.get(mvpMatrix));
            GL43C.glUniformMatrix4fv(uniHudProjection2, false, projectionMatrix2);
            float[] real = new float[]{xoffset, yoffset, zoffset, 1.0f};
            rotateAndTranslate(real,x,y,z,orientation);
            GL43C.glUniform4fv(uniHudLoc, real);
            GL43C.glUniform3fv(uniHudCol, new float[]{1.0f,1.0f,0.0f});

            GL43C.glBindTexture(GL43C.GL_TEXTURE_2D, 0);
            // Texture on UI
            GL43C.glBindVertexArray(vaoHudHandle);

            float x2 = 0;
            float y2 = 0;
            float scale = 0.002f;
            for(char cha: overhead.toCharArray()){
                Character ch = characters.get(cha);
                if(ch == null) continue;
                x2 += ch.bearing.x * scale + ch.size.x * scale;
            }
            x2 /= -2.0f;

            for(char cha: overhead.toCharArray()){
                Character ch = characters.get(cha);
                if(ch == null) continue;

                float xpos = x2 + ch.bearing.x * scale;
                float ypos = y2 - (ch.size.y - ch.bearing.y) * scale;

                float w = ch.size.x * scale;
                float h = ch.size.y * scale;

                //System.out.println(cha+" "+xpos+" "+ypos+" "+w+" "+h);
                // update VBO for each character
                float[] vertices = new float[]
                    {xpos,    ypos + h,   -0.0f, 0.0f, 0.0f ,
                     xpos,     ypos,       -0.0f,0.0f, 1.0f ,
                     xpos + w, ypos,       -0.0f,1.0f, 1.0f ,

                     xpos,     ypos + h,   -0.0f,0.0f, 0.0f ,
                     xpos + w, ypos,       -0.0f,1.0f, 1.0f ,
                     xpos + w, ypos + h,   -0.0f,1.0f, 0.0f };
                // render glyph texture over quad
                glBindTexture(GL_TEXTURE_2D, ch.id);
                // update content of VBO memory
                glBindBuffer(GL_ARRAY_BUFFER, vboHudHandle);
                glBufferSubData(GL_ARRAY_BUFFER, 0, vertices);
                glBindBuffer(GL_ARRAY_BUFFER, 0);
                // render quad
                glDrawArrays(GL_TRIANGLES, 0, 6);
                // now advance cursors for next glyph (note that advance is number of 1/64 pixels)
                x2 += (ch.advance >> 6) * scale; // bitshift by 6 to get value in pixels (2^6 = 64)

            }

            // Reset
            GL43C.glBindTexture(GL43C.GL_TEXTURE_2D, 0);
            GL43C.glBindVertexArray(0);
            GL43C.glUseProgram(0);
            GL43C.glBlendFunc(GL43C.GL_SRC_ALPHA, GL43C.GL_ONE_MINUS_SRC_ALPHA);
            GL43C.glDisable(GL43C.GL_BLEND);
        }
    }

    void drawHud(Matrix4f viewMatrix, Matrix4f projectionMatrix, float[] projectionMatrix2){
        for(Actor actor: actors.keySet()){
            drawAll(actor, viewMatrix, projectionMatrix, projectionMatrix2);
        }
    }
}
