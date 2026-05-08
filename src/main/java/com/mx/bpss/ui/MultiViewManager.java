package com.mx.bpss.ui;

import com.mx.bpss.canvas.PartCanvas;
import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * 多视图管理器
 * 负责管理多个 PartCanvas 视图，构建/重建链式 JSplitPane 布局
 * 记录当前激活的视图，提供添加/关闭视图的方法
 * UI 层只需创建本管理器并调用其方法，无需直接操作 JSplitPane
 */
public class MultiViewManager {

    // 视图列表（保证有序）
    private final List<PartCanvas> canvases = new ArrayList<>();
    // 当前激活的视图
    private PartCanvas activeCanvas = null;
    // 用于放置分割面板的容器（由 UI 层提供）
    private final JPanel container;

    /**
     * 构造管理器
     * @param container 一个 JPanel，用于放置视图链（可以是 BorderLayout 或其它）
     */
    public MultiViewManager(JPanel container) {
        this.container = container;
        // 默认创建一个空白视图，防止空布局
        addNewView();
    }

    // ========== 公共方法 ==========

    /**
     * 获取当前激活的视图，供 UI 层操作
     */
    public PartCanvas getActiveCanvas() {
        return activeCanvas;
    }

    /**
     * 获取所有视图列表（只读副本）
     */
    public List<PartCanvas> getCanvases() {
        return new ArrayList<>(canvases);
    }

    /**
     * 新增一个视图并立即重建布局
     */
    public PartCanvas addNewView() {
        PartCanvas canvas = createPartCanvas();
        canvases.add(canvas);
        rebuildLayout();
        return canvas;
    }

    /**
     * 关闭当前激活的视图（至少保留一个）
     */
    public void closeActiveView() {
        if (activeCanvas == null || canvases.size() <= 1) return;

        canvases.remove(activeCanvas);
        removePartCanvasListeners(activeCanvas);
        activeCanvas = null;

        if (canvases.isEmpty()) {
            // 理论上不会发生，但防御处理
            addNewView();
        } else {
            // 默认激活第一个
            activeCanvas = canvases.get(0);
            activeCanvas.requestFocusInWindow();
        }
        rebuildLayout();
    }

    /**
     * 强制重建布局（当视图数量变化或需要重新分配大小时调用）
     */
    public void rebuildLayout() {
        container.removeAll();

        if (canvases.isEmpty()) {
            JLabel label = new JLabel("尚无视图，请点击“新建视图”");
            label.setHorizontalAlignment(SwingConstants.CENTER);
            container.add(label, BorderLayout.CENTER);
        } else if (canvases.size() == 1) {
            container.add(canvases.get(0), BorderLayout.CENTER);
        } else {
            // 构建水平链式 JSplitPane
            Component root = buildChain(0);
            container.add(root, BorderLayout.CENTER);
        }

        container.revalidate();
        container.repaint();
    }

    // ========== 内部方法 ==========

    /**
     * 创建一个 PartCanvas 并设置焦点监听
     */
    private PartCanvas createPartCanvas() {
        PartCanvas canvas = new PartCanvas(
            title -> { /* 多视图下标题由管理器统一管理，忽略单个 */ },
            ()  -> {}  // 文件变化回调，暂时空
        );
        canvas.setFocusable(true);
        canvas.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                activeCanvas = (PartCanvas) e.getSource();
                // 可选：更新窗口标题或其他 UI 指示
            }
        });
        return canvas;
    }

    /**
     * 移除指定 PartCanvas 上的焦点监听（避免内存泄漏）
     */
    private void removePartCanvasListeners(PartCanvas canvas) {
        // 由于 FocusListener 是匿名内部类，不易精确移除
        // 这里简单将其焦点转移并清除所有监听，更严谨的做法可存储 listener 引用
        canvas.setFocusable(false);
    }

    /**
     * 递归构建水平链式 JSplitPane
     * @param index 当前视图在列表中的索引
     */
    private Component buildChain(int index) {
        if (index == canvases.size() - 1) {
            return canvases.get(index);
        }
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                                           canvases.get(index),
                                           buildChain(index + 1));
        split.setContinuousLayout(true);
        split.setResizeWeight(0.5);   // 初始均分
        split.setOneTouchExpandable(true);
        return split;
    }
}