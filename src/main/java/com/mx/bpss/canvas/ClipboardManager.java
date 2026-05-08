package com.mx.bpss.canvas;

import com.mx.bpss.core;
import java.util.*;

/**
 * 剪贴板管理器
 * 负责复制/粘贴/替换规则/镜像/预览偏移
 */
public class ClipboardManager {

    private List<core.Part> clipboardParts = new ArrayList<>();
    private boolean active = false;
    private int anchorCol = -1, anchorRow = -1;
    private int mouseCol = 0, mouseRow = 0;
    private int layoutMinX, layoutMaxY;

    /**
     * 开始剪贴板操作（复制选中部件）
     */
    public void startClipboard(List<core.Part> source, int layoutMinX, int layoutMaxY) {
        this.layoutMinX = layoutMinX;
        this.layoutMaxY = layoutMaxY;
        if (source == null || source.isEmpty()) return;

        int minCol = Integer.MAX_VALUE;
        int minRow = Integer.MAX_VALUE;
        for (core.Part p : source) {
            int col = p.x - layoutMinX;
            int row = layoutMaxY - p.y;
            if (col < minCol) minCol = col;
            if (row < minRow) minRow = row;
        }
        anchorCol = minCol;
        anchorRow = minRow;

        clipboardParts.clear();
        for (core.Part p : source) {
            clipboardParts.add(new core.Part(p.id, p.skin, p.x, p.y, p.orientation, p.flipped));
        }

        mouseCol = minCol;
        mouseRow = minRow;
        active = true;
    }

    /**
     * 取消剪贴板
     */
    public void cancel() {
        active = false;
        clipboardParts.clear();
        anchorCol = anchorRow = -1;
        mouseCol = mouseRow = 0;
    }

    /**
     * 设置外部剪贴板数据（用于跨视图粘贴）
     */
    public void setExternalClipboard(List<core.Part> externalParts, int externalMinX, int externalMaxY) {
        this.layoutMinX = externalMinX;
        this.layoutMaxY = externalMaxY;
        if (externalParts == null || externalParts.isEmpty()) {
            cancel();
            return;
        }
        clipboardParts.clear();
        for (core.Part p : externalParts) {
            clipboardParts.add(new core.Part(p.id, p.skin, p.x, p.y, p.orientation, p.flipped));
        }
        int minCol = Integer.MAX_VALUE, minRow = Integer.MAX_VALUE;
        for (core.Part p : clipboardParts) {
            int col = p.x - layoutMinX;
            int row = layoutMaxY - p.y;
            if (col < minCol) minCol = col;
            if (row < minRow) minRow = row;
        }
        anchorCol = minCol;
        anchorRow = minRow;
        mouseCol = minCol;
        mouseRow = minRow;
        active = true;
    }

    /**
     * 镜像剪贴板中的部件（X 键）
     */
    public void mirror() {
        if (active && !clipboardParts.isEmpty()) {
            core.mirrorPartsX(clipboardParts);
            recalcAnchor();
        }
    }

    /**
     * 重新计算锚点
     */
    private void recalcAnchor() {
        if (clipboardParts.isEmpty()) return;
        int minCol = Integer.MAX_VALUE, minRow = Integer.MAX_VALUE;
        for (core.Part p : clipboardParts) {
            int col = p.x - layoutMinX;
            int row = layoutMaxY - p.y;
            if (col < minCol) minCol = col;
            if (row < minRow) minRow = row;
        }
        anchorCol = minCol;
        anchorRow = minRow;
    }

    /**
     * 执行粘贴（带替换规则）
     * @param parts 当前部件列表（将被修改）
     * @param mouseCol 鼠标所在列
     * @param mouseRow 鼠标所在行
     */
    public void paste(List<core.Part> parts, int mouseCol, int mouseRow) {
        if (!active || clipboardParts.isEmpty()) return;

        int offsetCol = mouseCol - anchorCol;
        int offsetRow = mouseRow - anchorRow;

        // 构建需要粘贴的部件列表
        List<core.Part> newParts = new ArrayList<>();
        for (core.Part p : clipboardParts) {
            int newX = p.x + offsetCol;
            int newY = p.y - offsetRow;
            newParts.add(new core.Part(p.id, p.skin, newX, newY, p.orientation, p.flipped));
        }

        // 执行替换规则
        Set<Integer> indicesToRemove = new HashSet<>();
        for (core.Part pastePart : newParts) {
            boolean pasteIsFrame = isFramePart(pastePart);
            for (int i = 0; i < parts.size(); i++) {
                core.Part existingPart = parts.get(i);
                if (existingPart.x == pastePart.x && existingPart.y == pastePart.y) {
                    boolean existingIsFrame = isFramePart(existingPart);
                    if (existingIsFrame == pasteIsFrame) {
                        indicesToRemove.add(i);
                    }
                }
            }
        }

        // 从后往前删除
        List<Integer> sortedRemove = new ArrayList<>(indicesToRemove);
        Collections.sort(sortedRemove, Collections.reverseOrder());
        for (int idx : sortedRemove) {
            parts.remove(idx);
        }

        parts.addAll(newParts);
    }

    /**
     * 是否为框架类部件（id=5或6）
     */
    private boolean isFramePart(core.Part p) {
        return p.id == 5 || p.id == 6;
    }

    /**
     * 更新鼠标所在的网格位置（用于预览）
     */
    public void setMouseGrid(int col, int row) {
        mouseCol = col;
        mouseRow = row;
    }

    /**
     * 获取用于绘制预览的部件列表（含偏移）
     */
    public List<core.Part> getPreviewParts() {
        if (!active) return Collections.emptyList();
        int offsetCol = mouseCol - anchorCol;
        int offsetRow = mouseRow - anchorRow;
        List<core.Part> result = new ArrayList<>();
        for (core.Part p : clipboardParts) {
            result.add(new core.Part(p.id, p.skin, p.x + offsetCol, p.y - offsetRow, p.orientation, p.flipped));
        }
        return result;
    }

    public boolean isActive() { return active; }
    public int getMouseCol() { return mouseCol; }
    public int getMouseRow() { return mouseRow; }
    public int getAnchorCol() { return anchorCol; }
    public int getAnchorRow() { return anchorRow; }
}