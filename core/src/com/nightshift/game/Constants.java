package com.nightshift.game;

public class Constants {
    public static final float PIXELS_TO_METERS = 100f;
    private static int screenWidth, screenHeight;

    public static void setScreenWidth(int width) {
        screenWidth = width;
    }

    public static void setScreenHeight(int height) {
        screenHeight = height;
    }

    public static int getScreenWidth() {
        return screenWidth;
    }

    public static int getScreenHeight() {
        return  screenHeight;
    }
}
