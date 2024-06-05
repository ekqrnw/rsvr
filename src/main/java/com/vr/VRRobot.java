package com.vr;

import net.runelite.api.Client;
import org.joml.Vector2i;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

public class VRRobot {
    Robot robot;
    Canvas canvas;

    static float estimatedXRatio = 516.0f/770.0f;
    static float estimatedYRatio = 336.0f/505.0f;

    VRRobot(Canvas canvas) throws AWTException {
        robot = new Robot();
        this.canvas = canvas;
    }

    void leftClick(boolean click){
        if(click) {
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        } else {
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        }
    }

    void rightClick(boolean click){
        if(click) {
            robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
        } else {
            robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
        }
    }

    void middleClick(boolean click){
        if(click) {
            robot.mousePress(InputEvent.BUTTON2_DOWN_MASK);
        } else {
            robot.mouseRelease(InputEvent.BUTTON2_DOWN_MASK);
        }
    }

    boolean setCursorByXY(float x, float y){
        Point point = canvas.getLocationOnScreen();
        boolean outOfBounds = (x < -1 || x > 1 || y < -1 || y > 1);
        //if(x < -1){ x = -1; } if(x > 1){ x = 1; } if(y < -1){ y = -1; } if(y > 1){ y = 1; }
        Vector2i vec = translateUnitSquareToPlayableArea(point.x+2, point.y+2, canvas.getWidth()*estimatedXRatio, canvas.getHeight()*estimatedYRatio, x, -y);
        robot.mouseMove(vec.x(), vec.y());
        //robot.mouseMove(point.x+2+(int)(canvas.getWidth()*(x+1.0f)*estimatedXRatio/2.0f), point.y+2+(int)(canvas.getHeight()*(1.0f-y)*estimatedYRatio/2.0f));
        return !outOfBounds;
    }

    void setCursorByMapPct(float pctX, float pctY){
        Point point = canvas.getLocationOnScreen();
        robot.mouseMove(point.x+(int)(canvas.getWidth()*pctX), point.y+(int)(canvas.getHeight()*(1-pctY)));
    }

    Vector2i translateUnitSquareToPlayableArea(int x1, int y1, float w, float h, float x, float y){
        if(x < -1){ x = -1; } if(x > 1){ x = 1; } if(y < -1){ y = -1; } if(y > 1){ y = 1; }
        return new Vector2i(x1+(int)(w*(x+1.0f)/2.0f), y1+(int)(h*(y+1.0f)/2.0f));
    }

    private Client selectingOnClient = null;
    private int idx = 0;
    private boolean aClicked = false;
    private boolean bClicked = false;

    void startSelecting(Client client){
        selectingOnClient = client;
        idx = 0;
        Point point = canvas.getLocationOnScreen();
        robot.mouseMove(point.x + client.getMenuX() + 2, point.y + client.getMenuY() + 19 + 2);
    }

    private void select(boolean up){
        Point point = canvas.getLocationOnScreen();
        if(up){
            idx--;
            if(idx < 0) {
                idx = selectingOnClient.getMenuEntries().length - 1;
                while(offTheMap(idx, canvas)){
                    idx--;
                }
            }
            robot.mouseMove(point.x + selectingOnClient.getMenuX() + 2, point.y + selectingOnClient.getMenuY() + 19 + (idx * 15) + 2);
        } else {
            idx++;
            if(offTheMap(idx, canvas) || idx >= selectingOnClient.getMenuEntries().length){
                idx = 0;
            }
            robot.mouseMove(point.x + selectingOnClient.getMenuX() + 2, point.y + selectingOnClient.getMenuY() + 19 + (idx * 15) + 2);
        }
    }

    private boolean offTheMap(int idx, Canvas canvas){
        return selectingOnClient.getMenuY() + 19 + (idx*15) + 2 > canvas.getHeight();
    }

    void selectUp(boolean click){
        try {
            if (!click && bClicked) {
                select(true);
            }
        } finally{
            bClicked = click;
        }
    }

    void selectDown(boolean click){
        try {
            if (!click && aClicked) {
                select(false);
            }
        } finally{
            aClicked = click;
        }
    }
}
