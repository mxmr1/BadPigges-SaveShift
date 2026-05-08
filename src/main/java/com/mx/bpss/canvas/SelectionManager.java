package com.mx.bpss.canvas;

import com.mx.bpss.core;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

/**
 * 框选管理器
 * 负责右键框选逻辑、选中部件列表维护、框选矩形绘制信息
 */
public class SelectionManager {

    private Point dragStart;
    private Point dragEnd;
    private boolean isDragging = false;
    private List<core.Part> selectedParts = new ArrayList<>();

    private boolean hasSelection = false;
    private int selMinCol = -1, selMaxCol = -1;
    private int selMinRow = -1, selMaxRow = -1;

    /**
     * 开始框选
     */
    public void startDrag(Point point) {
        dragStart = point;
        dragEnd = point;
        isDragging = true;
    }

    /**
     * 更新框选终点
     */
    public void updateDrag(Point point) {
        if (isDragging) {
            dragEnd = point;
        }
    }

    /**
     * 结束框选，计算选中的部件
     * @param parts 所有部件
     * @param layoutInfo 布局信息
     */
    public void endDrag(List<core.Part> parts, LayoutInfo layoutInfo) {
        if (!isDragging || dragStart == null || dragEnd == null) {
            clearSelection();
            isDragging = false;
            return;
        }
        isDragging = false;

        int selW = Math.abs(dragEnd.x - dragStart.x);
        int selH = Math.abs(dragEnd.y - dragStart.y);
        if (selW < 2 && selH < 2) {
            clearSelection();
            return;
        }

        int[] startGrid = screenToGrid(dragStart.x, dragStart.y, layoutInfo);
        int[] endGrid = screenToGrid(dragEnd.x, dragEnd.y, layoutInfo);

        selMinCol = Math.min(startGrid[0], endGrid[0]);
        selMaxCol = Math.max(startGrid[0], endGrid[0]);
        selMinRow = Math.min(startGrid[1], endGrid[1]);
        selMaxRow = Math.max(startGrid[1], endGrid[1]);

        hasSelection = true;
        selectedParts.clear();

        for (core.Part p : parts) {
            int col = p.x - layoutInfo.getMinX();
            int row = layoutInfo.getMaxY() - p.y;
            if (col >= selMinCol && col <= selMaxCol && row >= selMinRow && row <= selMaxRow) {
                selectedParts.add(p);
            }
        }
    }

    /**
     * 屏幕坐标转网格坐标
     */
    private int[] screenToGrid(int sx, int sy, LayoutInfo layoutInfo) {
        double colF = (double)(sx - layoutInfo.getOffsetX()) / layoutInfo.getCellSize();
        double rowF = (double)(sy - layoutInfo.getOffsetY()) / layoutInfo.getCellSize();
        return new int[]{(int)Math.floor(colF), (int)Math.floor(rowF)};
    }

    public void clearSelection() {
        selectedParts.clear();
        hasSelection = false;
        selMinCol = selMaxCol = selMinRow = selMaxRow = -1;
    }

    public List<core.Part> getSelectedParts() {
        return selectedParts;
    }

    public boolean hasSelection() { return hasSelection && !selectedParts.isEmpty(); }
    public boolean isDragging() { return isDragging; }
    public Point getDragStart() { return dragStart; }
    public Point getDragEnd() { return dragEnd; }

    public int getSelMinCol() { return selMinCol; }
    public int getSelMaxCol() { return selMaxCol; }
    public int getSelMinRow() { return selMinRow; }
    public int getSelMaxRow() { return selMaxRow; }

    /**
     * 布局信息值对象，供各个管理器使用
     */
    public static class LayoutInfo {
        private int minX, maxY;
        private double offsetX, offsetY, cellSize;

        public LayoutInfo(int minX, int maxY, double offsetX, double offsetY, double cellSize) {
            this.minX = minX;
            this.maxY = maxY;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.cellSize = cellSize;
        }

        public int getMinX() { return minX; }
        public int getMaxY() { return maxY; }
        public double getOffsetX() { return offsetX; }
        public double getOffsetY() { return offsetY; }
        public double getCellSize() { return cellSize; }
    }
}