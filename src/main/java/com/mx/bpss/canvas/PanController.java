package com.mx.bpss.canvas;

/**
 * WASD 平移状态控制器（无内部 Timer）
 * 管理平移方向和增量计算，由 PartCanvas 中的统一 Timer 驱动
 */
public class PanController {

    private int directionX = 0; // -1左, 0不动, 1右
    private int directionY = 0; // -1上, 0不动, 1下
    private static final long PAN_DURATION_MS = 1500;

    public int getDirectionX() { return directionX; }
    public int getDirectionY() { return directionY; }
    public void setDirectionX(int dir) { directionX = dir; }
    public void setDirectionY(int dir) { directionY = dir; }

    public boolean isMoving() { return directionX != 0 || directionY != 0; }
    /**
     * 计算每帧平移增量
     */
    public int computeDeltaX(int panelWidth) {
        if (directionX == 0) return 0;
        int effective = panelWidth - 40;
        if (effective <= 0) effective = 1;
        double pixelsPerMs = (double) effective / PAN_DURATION_MS;
        return (int) (directionX * pixelsPerMs * 16);
    }

    public int computeDeltaY(int panelHeight) {
        if (directionY == 0) return 0;
        int effective = panelHeight - 40;
        if (effective <= 0) effective = 1;
        double pixelsPerMs = (double) effective / PAN_DURATION_MS;
        return (int) (directionY * pixelsPerMs * 16);
    }

    public void stop() {
        directionX = 0;
        directionY = 0;
    }
    public void reset() {
        stop();
    }
}
