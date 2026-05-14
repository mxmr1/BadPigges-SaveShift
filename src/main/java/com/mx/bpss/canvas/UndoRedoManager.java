package com.mx.bpss.canvas;

import com.mx.bpss.model.Part;
import java.util.ArrayList;
import java.util.List;

/**
 * 撤销/重做管理器
 * 存储部件快照，提供撤销/重做操作
 */
public class UndoRedoManager {

    private List<List<Part>> undoStack = new ArrayList<>();
    private List<List<Part>> redoStack = new ArrayList<>();
    private static final int MAX_STEPS = 50;

    /**
     * 保存当前部件状态到撤销栈
     */
    public void saveState(List<Part> parts) {
        List<Part> snapshot = cloneParts(parts);
        undoStack.add(snapshot);
        if (undoStack.size() > MAX_STEPS) {
            undoStack.remove(0);
        }
        redoStack.clear();
    }

    /**
     * 撤销：恢复到上一个状态
     */
    public boolean undo(List<Part> currentParts) {
        if (undoStack.isEmpty()) return false;
        // 保存当前到重做栈
        redoStack.add(cloneParts(currentParts));
        // 恢复
        List<Part> prev = undoStack.remove(undoStack.size() - 1);
        currentParts.clear();
        currentParts.addAll(prev);
        return true;
    }

    /**
     * 重做：恢复到之前撤销的状态
     */
    public boolean redo(List<Part> currentParts) {
        if (redoStack.isEmpty()) return false;
        // 保存当前到撤销栈
        undoStack.add(cloneParts(currentParts));
        // 恢复
        List<Part> next = redoStack.remove(redoStack.size() - 1);
        currentParts.clear();
        currentParts.addAll(next);
        return true;
    }

    /**
     * 深拷贝部件列表
     */
    private List<Part> cloneParts(List<Part> parts) {
        List<Part> clone = new ArrayList<>();
        for (Part p : parts) {
            clone.add(new Part(p.id, p.skin, p.x, p.y, p.orientation, p.flipped));
        }
        return clone;
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }
    public boolean canRedo() { return !redoStack.isEmpty(); }
}