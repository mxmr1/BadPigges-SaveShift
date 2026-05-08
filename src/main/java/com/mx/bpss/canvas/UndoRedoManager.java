package com.mx.bpss.canvas;

import com.mx.bpss.core;
import java.util.ArrayList;
import java.util.List;

/**
 * 撤销/重做管理器
 * 存储部件快照，提供撤销/重做操作
 */
public class UndoRedoManager {

    private List<List<core.Part>> undoStack = new ArrayList<>();
    private List<List<core.Part>> redoStack = new ArrayList<>();
    private static final int MAX_STEPS = 50;

    /**
     * 保存当前部件状态到撤销栈
     */
    public void saveState(List<core.Part> parts) {
        List<core.Part> snapshot = cloneParts(parts);
        undoStack.add(snapshot);
        if (undoStack.size() > MAX_STEPS) {
            undoStack.remove(0);
        }
        redoStack.clear();
    }

    /**
     * 撤销：恢复到上一个状态
     */
    public boolean undo(List<core.Part> currentParts) {
        if (undoStack.isEmpty()) return false;
        // 保存当前到重做栈
        redoStack.add(cloneParts(currentParts));
        // 恢复
        List<core.Part> prev = undoStack.remove(undoStack.size() - 1);
        currentParts.clear();
        currentParts.addAll(prev);
        return true;
    }

    /**
     * 重做：恢复到之前撤销的状态
     */
    public boolean redo(List<core.Part> currentParts) {
        if (redoStack.isEmpty()) return false;
        // 保存当前到撤销栈
        undoStack.add(cloneParts(currentParts));
        // 恢复
        List<core.Part> next = redoStack.remove(redoStack.size() - 1);
        currentParts.clear();
        currentParts.addAll(next);
        return true;
    }

    /**
     * 深拷贝部件列表
     */
    private List<core.Part> cloneParts(List<core.Part> parts) {
        List<core.Part> clone = new ArrayList<>();
        for (core.Part p : parts) {
            clone.add(new core.Part(p.id, p.skin, p.x, p.y, p.orientation, p.flipped));
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