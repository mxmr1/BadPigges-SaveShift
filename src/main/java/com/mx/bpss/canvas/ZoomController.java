package com.mx.bpss.canvas;

import java.awt.event.MouseWheelEvent;

/**
 * 缩放与惯性状态控制器（纯状态计算，无内部 Timer）
 * 缩小以屏幕中心为原点，放大以鼠标位置为原点
 */
public class ZoomController {

    public static final int BASE_UNIT_SIZE = 8;

    private double scale = 1.0;
    private double targetScale = 1.0;
    private boolean zoomingIn = false;
    private int zoomOriginX;
    private int zoomOriginY;

    private static final double INERTIA_FACTOR = 0.85;
    private static final double INERTIA_THRESHOLD = 0.0001;

    public double getScale() { return scale; }
    public boolean isZoomingIn() { return zoomingIn; }
    public int getZoomOriginX() { return zoomOriginX; }
    public int getZoomOriginY() { return zoomOriginY; }

    public void onWheel(MouseWheelEvent e, int panelWidth, int panelHeight) {
        int notches = e.getWheelRotation();
        double newScale = scale;
        if (notches > 0) {
            newScale /= Math.pow(2.0, notches);
        } else {
            newScale *= Math.pow(2.0, -notches);
        }
        if (newScale < 0.001) newScale = 0.001;
        if (newScale > 100.0) newScale = 100.0;
        targetScale = newScale;

        if (newScale != scale) {
            if (notches > 0) {
                zoomingIn = false;
                int margin = 20;
                int pw = panelWidth - 2 * margin;
                int ph = panelHeight - 2 * margin;
                zoomOriginX = margin + pw / 2;
                zoomOriginY = margin + ph / 2;
            } else {
                zoomingIn = true;
                zoomOriginX = e.getX();
                zoomOriginY = e.getY();
            }
        }
    }
    /**
     * 一步惯性逼近，返回是否仍在运动
     */
    public boolean inertiaStep(double baseOffsetX, double baseOffsetY, MutablePan pan) {
        double diff = targetScale - scale;
        if (Math.abs(diff) < INERTIA_THRESHOLD) {
            scale = targetScale;
            return false;
        }
        double oldScale = scale;
        scale += diff * INERTIA_FACTOR;

        if (scale < 0.001) scale = 0.001;
        if (scale > 100.0) scale = 100.0;

        double oldCellSize = Math.max(1, BASE_UNIT_SIZE * oldScale);
        double newCellSize = Math.max(1, BASE_UNIT_SIZE * scale);

        double worldX = (zoomOriginX - (baseOffsetX + pan.x)) / oldCellSize;
        double worldY = (zoomOriginY - (baseOffsetY + pan.y)) / oldCellSize;

        double newLayoutOffsetX = zoomOriginX - worldX * newCellSize;
        double newLayoutOffsetY = zoomOriginY - worldY * newCellSize;

        pan.x = (int) Math.round(newLayoutOffsetX - baseOffsetX);
        pan.y = (int) Math.round(newLayoutOffsetY - baseOffsetY);

        return true;
    }

    public void stop() {
        scale = targetScale;
    }

    public void setTargetScale(double newScale) {
        if (newScale < 0.001) newScale = 0.001;
        if (newScale > 100.0) newScale = 100.0;
        targetScale = newScale;
        scale = newScale;
    }

    public void reset() {
        scale = 1.0;
        targetScale = 1.0;
    }

    public void updateZoomOrigin(int mouseX, int mouseY, double panX, double panY, double baseOffsetX, double baseOffsetY) {
        double currentCellSize = Math.max(1, BASE_UNIT_SIZE * scale);
        zoomOriginX = mouseX;
        zoomOriginY = mouseY;
    }
    public static class MutablePan {
        public double x, y;
        public MutablePan(double x, double y) { this.x = x; this.y = y; }
    }
}