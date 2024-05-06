package com.vr;

import org.joml.Vector2i;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

public class VRRobot {
    Robot robot;
    Canvas canvas;

    float estimatedXRatio = 516.0f/770.0f;
    float estimatedYRatio = 336.0f/505.0f;

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

    Vector2i translateUnitSquareToPlayableArea(int x1, int y1, float w, float h, float x, float y){
        if(x < -1){ x = -1; } if(x > 1){ x = 1; } if(y < -1){ y = -1; } if(y > 1){ y = 1; }
        return new Vector2i(x1+(int)(w*(x+1.0f)/2.0f), y1+(int)(h*(y+1.0f)/2.0f));
    }


}
