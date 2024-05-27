package com.vr;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.freetype.FreeType;
import com.badlogic.gdx.utils.GdxNativesLoader;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import org.joml.Matrix4f;
import org.joml.Vector2i;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL43C;

import java.io.File;
import java.nio.FloatBuffer;
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

    private Map<java.lang.Character, Character> characters;

    private int uniHudProjection;
    private int uniHudProjection2;

    private int uniHudView;

    private int uniHudLoc;
    private int vboHudHandle;
    private int vaoHudHandle;
    static int glHudProgram;

    HudHelper() {
        characters = new HashMap<>();

        uniHudProjection = GL43C.glGetUniformLocation(glHudProgram, "projection");
        uniHudProjection2 = GL43C.glGetUniformLocation(glHudProgram, "projection2");
        uniHudView = GL43C.glGetUniformLocation(glHudProgram, "viewMatrix");
        uniHudLoc= GL43C.glGetUniformLocation(glHudProgram, "loc");

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
    }

    private Map<Integer, Act> players = new HashMap<>();
    private Map<Integer, Act> npcs = new HashMap<>();

    private static FloatBuffer mvpMatrix = BufferUtils.createFloatBuffer(16);

    class Act{
        Actor actor;
        int orientation;
        int x;
        int y;
        int z;

        Act(Actor actor, int orientation, int x, int y, int z){
           this.actor = actor;
           this.orientation = orientation;
           this.x = x;
           this.y = y;
           this.z = z;
        }
    }

    void backloadPlayer(Player actor, int orientation, int x, int y, int z){
        players.put(actor.getId(), new Act(actor,orientation,x,y,z));
    }

    void backloadNPC(NPC actor, int orientation, int x, int y, int z){
        npcs.put(actor.getId(), new Act(actor,orientation,x,y,z));
    }

    void backloadActor(Actor actor, int orientation, int x, int y, int z){
        if(actor instanceof Player) backloadPlayer((Player)actor,orientation,x,y,z);
        if(actor instanceof NPC) backloadNPC((NPC)actor,orientation,x,y,z);
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

    void swap(){
        npcs.clear();
        players.clear();
    }

    void drawOverheadText(Act act, Matrix4f viewMatrix, Matrix4f projectionMatrix, float[] projectionMatrix2){
        Actor actor = act.actor;
        int x = act.x;
        int y = act.y;
        int z = act.z;
        int orientation = act.orientation;
        if(actor.getOverheadCycle() <= 0 || actor.getOverheadText() == null) {}
        else {
            String overhead = actor.getOverheadText();
            GL43C.glEnable(GL43C.GL_BLEND);
            GL43C.glBlendFunc(GL43C.GL_SRC_ALPHA, GL43C.GL_ONE_MINUS_SRC_ALPHA);

            Model model = actor.getModel();
            if(model.getUnskewedModel() != null){
                model = model.getUnskewedModel();
            }

            final int vertexCount = model.getVerticesCount();
            final int[] verticesX = model.getVerticesX();
            final int[] verticesY = model.getVerticesY();
            final int[] verticesZ = model.getVerticesZ();

            int xoffset = 0;
            int zoffset = 0;
            int yoffset = Integer.MAX_VALUE;

            for (int v = 0; v < vertexCount; ++v) {

                if(verticesY[v] < yoffset){
                    yoffset = verticesY[v];
                    xoffset = verticesX[v];
                    zoffset = verticesZ[v];
                }
            }

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

            GL43C.glBindTexture(GL43C.GL_TEXTURE_2D, 0);
            // Texture on UI
            GL43C.glBindVertexArray(vaoHudHandle);

            float x2 = 0;
            float y2 = 0;
            float scale = 0.002f;
            for(char cha: overhead.toCharArray()){
                Character ch = characters.get(cha);
                x2 += ch.bearing.x * scale + ch.size.x * scale;
            }
            x2 /= -2.0f;

            for(char cha: overhead.toCharArray()){
                Character ch = characters.get(cha);

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
        for(Act player: players.values()){
            drawOverheadText(player, viewMatrix, projectionMatrix, projectionMatrix2);
        }
        for(Act npc: npcs.values()){
            drawOverheadText(npc, viewMatrix, projectionMatrix, projectionMatrix2);
        }
    }
}
