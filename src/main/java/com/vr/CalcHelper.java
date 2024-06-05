package com.vr;

import net.runelite.api.MenuEntry;
import net.runelite.client.util.Text;
import org.joml.Quaternionf;
import org.joml.Vector2i;
import org.joml.Vector3f;
import org.lwjgl.openxr.XrQuaternionf;
import org.lwjgl.openxr.XrVector3f;

import java.util.HashMap;

public class CalcHelper {
    static Vector3f getPlayAreaIntersect(XrVector3f position, XrQuaternionf rotation){
        float x0 = position.x();
        float y0 = position.y();
        float z0 = position.z();

        Vector3f v3 = new Vector3f(0,0,1).rotate(new Quaternionf(rotation.x(),rotation.y(),rotation.z(),rotation.w()));
        float a = v3.x();
        float b = v3.y();
        float c = v3.z();

        float intersect = -0.3f;

        if(Math.abs(c) > 0.0005) {
            return new Vector3f(x0 + (intersect - z0) * a / (2*c), y0 + (intersect - z0) * b / (2*c), (z0 + intersect)/2);
        } else {
            return new Vector3f(x0, y0, (z0 + intersect)/2);
        }
    }

    static Vector3f getMapPlaneIntersect(XrVector3f lPosition, XrQuaternionf lRotation, XrVector3f rPosition, XrQuaternionf rRotation, float xoffset, float yoffset, float zoffset){
        float z = lPosition.z()+zoffset;
        Vector3f v3 = new Vector3f(0,0,1).rotate(new Quaternionf(rRotation.x(),rRotation.y(),rRotation.z(),rRotation.w()));
        float a = v3.x();
        float b = v3.y();
        float c = v3.z();
        float zDist = rPosition.z()-z;
        float x = rPosition.x()+zDist*a/c;
        float y = rPosition.y()-zDist*b/c;
        return new Vector3f(x-lPosition.x()-xoffset,y-lPosition.y()-yoffset,z);
    }

    public static final HashMap<Character, Integer> fontWidths = new HashMap<>();
    static{
        fontWidths.put('a',6);
        fontWidths.put('b',6);
        fontWidths.put('c',5);
        fontWidths.put('d',6);
        fontWidths.put('e',6);
        fontWidths.put('f',6);
        fontWidths.put('g',6);
        fontWidths.put('h',6);
        fontWidths.put('i',2);
        fontWidths.put('j',5);
        fontWidths.put('k',6);
        fontWidths.put('l',2);
        fontWidths.put('m',8);
        fontWidths.put('n',6);
        fontWidths.put('o',6);
        fontWidths.put('p',6);
        fontWidths.put('q',6);
        fontWidths.put('r',4);
        fontWidths.put('s',6);
        fontWidths.put('t',4);
        fontWidths.put('u',6);
        fontWidths.put('v',6);
        fontWidths.put('w',7);
        fontWidths.put('x',6);
        fontWidths.put('y',6);
        fontWidths.put('z',6);
        fontWidths.put('A',7);
        fontWidths.put('B',6);
        fontWidths.put('C',6);
        fontWidths.put('D',6);
        fontWidths.put('E',6);
        fontWidths.put('F',6);
        fontWidths.put('G',7);
        fontWidths.put('H',6);
        fontWidths.put('I',4);
        fontWidths.put('J',7);
        fontWidths.put('K',6);
        fontWidths.put('L',6);
        fontWidths.put('M',8);
        fontWidths.put('N',7);
        fontWidths.put('O',7);
        fontWidths.put('P',6);
        fontWidths.put('Q',7);
        fontWidths.put('R',6);
        fontWidths.put('S',6);
        fontWidths.put('T',6);
        fontWidths.put('U',7);
        fontWidths.put('V',6);
        fontWidths.put('W',8);
        fontWidths.put('X',6);
        fontWidths.put('Y',6);
        fontWidths.put('Z',6);
        fontWidths.put(' ',3);
        fontWidths.put('1',6);
        fontWidths.put('2',7);
        fontWidths.put('3',6);
        fontWidths.put('4',7);
        fontWidths.put('5',6);
        fontWidths.put('6',7);
        fontWidths.put('7',6);
        fontWidths.put('8',7);
        fontWidths.put('9',7);
        fontWidths.put('0',7);
        fontWidths.put('!',2);
        fontWidths.put('@',12);
        fontWidths.put('#',12);
        fontWidths.put('$',7);
        fontWidths.put('%',10);
        fontWidths.put('^',7);
        fontWidths.put('&',11);
        fontWidths.put('*',8);
        fontWidths.put('(',3);
        fontWidths.put(')',3);
        //fontWidths.put('`',3);
        fontWidths.put('~',10);
        fontWidths.put('-',6);
        fontWidths.put('_',8);
        fontWidths.put('=',7);
        fontWidths.put('+',7);
        fontWidths.put('[',4);
        fontWidths.put('{',5);
        fontWidths.put(']',4);
        fontWidths.put('}',5);
        fontWidths.put('\\',4);
        fontWidths.put('|',2);
        fontWidths.put(';',3);
        fontWidths.put(':',2);
        fontWidths.put('\'',2);
        fontWidths.put('"',5);
        fontWidths.put(',',2);
        fontWidths.put('<',6);
        fontWidths.put('.',1);
        fontWidths.put('>',6);
        fontWidths.put('?',8);
        fontWidths.put('/',5);
    }

    //20 2 for boundaries + 15 for Choose Option
    private static int getMessageLength(String message){
        int len = 0;
        for(int i = 0; i < message.length(); i++){
            len += fontWidths.get(message.charAt(i));
            len += 2;
        }
        return len;
    }

    static Vector2i getMenuBoundaries(MenuEntry[] menuEntries){
        int maxLen = getMessageLength("Choose Option");
        for(MenuEntry menuEntry: menuEntries){
            int entryLen = getMessageLength(Text.removeFormattingTags(menuEntry.getOption()+(menuEntry.getTarget().isEmpty()?"":("  "+menuEntry.getTarget()))));
            if(entryLen > maxLen){
                maxLen = entryLen;
            }
        }
        return new Vector2i(maxLen+10,25+16*menuEntries.length);
    }
}
