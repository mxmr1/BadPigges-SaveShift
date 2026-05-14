package com.mx.bpss.canvas;

import com.mx.bpss.model.Part;
import com.mx.bpss.model.SaveFile;
import java.util.*;

/**
 * 剪贴板管理器
 * 负责复制/粘贴/替换规则/镜像/预览偏移
 */
public class ClipboardManager {

    private List<Part> clipboardParts = new ArrayList<>();
    private boolean active = false;
    private int anchorCol = -1, anchorRow = -1;
    private int mouseCol = 0, mouseRow = 0;
    private int layoutMinX, layoutMaxY;

    /**
     * 开始剪贴板操作（复制选中部件）
     */
    public void startClipboard(List<Part> source, int layoutMinX, int layoutMaxY) {
        this.layoutMinX = layoutMinX;
        this.layoutMaxY = layoutMaxY;
        if (source == null || source.isEmpty()) return;

        int minCol = Integer.MAX_VALUE;
        int minRow = Integer.MAX_VALUE;
        for (Part p : source) {
            int col = p.x - layoutMinX;
            int row = layoutMaxY - p.y;
            if (col < minCol) minCol = col;
            if (row < minRow) minRow = row;
        }
        anchorCol = minCol;
        anchorRow = minRow;

        clipboardParts.clear();
        for (Part p : source) {
            clipboardParts.add(new Part(p.id, p.skin, p.x, p.y, p.orientation, p.flipped));
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

    public void setExternalClipboard(List<Part> externalParts, int anchorAbsX, int anchorAbsY) {
        if (externalParts == null || externalParts.isEmpty()) {
            cancel();
            return;
        }
        clipboardParts.clear();
        for (Part p : externalParts) {
            clipboardParts.add(new Part(p.id, p.skin, p.x, p.y, p.orientation, p.flipped));
        }
        anchorCol = anchorAbsX - layoutMinX;
        anchorRow = layoutMaxY - anchorAbsY;
        mouseCol = anchorCol;
        mouseRow = anchorRow;
        active = true;
    }

    /**
     * 镜像剪贴板中的部件（X 键）
     */
    public void mirror() {
        if (active && !clipboardParts.isEmpty()) {
            SaveFile.mirrorPartsFull(clipboardParts);
            recalcAnchor();
        }
    }

    private void recalcAnchor() {
        if (clipboardParts.isEmpty()) return;
        int minCol = Integer.MAX_VALUE, minRow = Integer.MAX_VALUE;
        for (Part p : clipboardParts) {
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
     */
    public void paste(List<Part> parts, int mouseCol, int mouseRow) {
        if (!active || clipboardParts.isEmpty()) return;

        int offsetCol = mouseCol - anchorCol;
        int offsetRow = mouseRow - anchorRow;

        List<Part> newParts = new ArrayList<>();
        for (Part p : clipboardParts) {
            int newX = p.x + offsetCol;
            int newY = p.y - offsetRow;
            newParts.add(new Part(p.id, p.skin, newX, newY, p.orientation, p.flipped));
        }

        Set<Integer> indicesToRemove = new HashSet<>();
        for (Part pastePart : newParts) {
            boolean pasteIsFrame = isFramePart(pastePart);
            for (int i = 0; i < parts.size(); i++) {
                Part existingPart = parts.get(i);
                if (existingPart.x == pastePart.x && existingPart.y == pastePart.y) {
                    boolean existingIsFrame = isFramePart(existingPart);
                    if (existingIsFrame == pasteIsFrame) {
                        indicesToRemove.add(i);
                    }
                }
            }
        }

        List<Integer> sortedRemove = new ArrayList<>(indicesToRemove);
        Collections.sort(sortedRemove, Collections.reverseOrder());
        for (int idx : sortedRemove) {
            parts.remove(idx);
        }

        parts.addAll(newParts);
    }

    private boolean isFramePart(Part p) {
        return p.id == 5 || p.id == 6;
    }

    public void setMouseGrid(int col, int row) {
        mouseCol = col;
        mouseRow = row;
    }

    public List<Part> getPreviewParts() {
        if (!active) return Collections.emptyList();
        int offsetCol = mouseCol - anchorCol;
        int offsetRow = mouseRow - anchorRow;
        List<Part> result = new ArrayList<>();
        for (Part p : clipboardParts) {
            result.add(new Part(p.id, p.skin, p.x + offsetCol, p.y - offsetRow, p.orientation, p.flipped));
        }
        return result;
    }

    public boolean isActive() { return active; }
    public int getMouseCol() { return mouseCol; }
    public int getMouseRow() { return mouseRow; }
    public int getAnchorCol() { return anchorCol; }
    public int getAnchorRow() { return anchorRow; }
}