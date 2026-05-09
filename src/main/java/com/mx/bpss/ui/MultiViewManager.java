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
 * 负责管理多个 ViewPanel（每个 ViewPanel 包含按钮栏和 PartCanvas），
 * 构建/重建链式 JSplitPane 布局，记录当前激活的视图
 */
public class MultiViewManager {

    // 视图面板列表（保证有序）
    private final List<ViewPanel> viewPanels = new ArrayList<>();
    // 当前激活的视图面板
    private ViewPanel activePanel = null;
    // 用于放置分割面板的容器（由 UI 层提供）
    private final JPanel container;

    /**
     * 构造管理器
     * @param container 一个 JPanel，用于放置视图链
     */
    public MultiViewManager(JPanel container) {
        this.container = container;
        // 默认创建一个空白视图
        addNewView();
    }

    // ========== 公共方法 ==========

    /**
     * 获取当前激活视图的 PartCanvas，供外部操作
     */
    public PartCanvas getActiveCanvas() {
        return activePanel != null ? activePanel.getCanvas() : null;
    }

    /**
     * 获取当前激活的 ViewPanel
     */
    public ViewPanel getActivePanel() {
        return activePanel;
    }

    /**
     * 获取所有视图面板列表（只读副本）
     */
    public List<ViewPanel> getViewPanels() {
        return new ArrayList<>(viewPanels);
    }

    /**
     * 新增一个视图面板并立即重建布局
     */
    public ViewPanel addNewView() {
        ViewPanel panel = createViewPanel();
        viewPanels.add(panel);
        rebuildLayout();
        // 延迟请求焦点，确保布局完成
        SwingUtilities.invokeLater(() -> panel.getCanvas().requestFocusInWindow());
        return panel;
    }

    /**
     * 关闭当前激活的视图面板（至少保留一个）
     */
    public void closeActiveView() {
        if (activePanel == null || viewPanels.size() <= 1) return;

        viewPanels.remove(activePanel);
        activePanel = null;
        if (viewPanels.isEmpty()) {
            addNewView();
        } else {
            // 默认激活第一个
            activePanel = viewPanels.get(0);
            activePanel.getCanvas().requestFocusInWindow();
        }
        rebuildLayout();
    }

    /**
     * 强制重建布局
     */
    public void rebuildLayout() {
        container.removeAll();

        if (viewPanels.isEmpty()) {
            JLabel label = new JLabel("尚无视图，请点击顶部“新建视图”按钮");
            label.setHorizontalAlignment(SwingConstants.CENTER);
            container.add(label, BorderLayout.CENTER);
        } else if (viewPanels.size() == 1) {
            container.add(viewPanels.get(0), BorderLayout.CENTER);
        } else {
            Component root = buildChain(0);
            container.add(root, BorderLayout.CENTER);
        }

        container.revalidate();
        container.repaint();
    }

    // ========== 内部方法 ==========

    /**
     * 创建一个 ViewPanel 并为内部的 PartCanvas 设置焦点监听，并绑定关闭回调
     */
    private ViewPanel createViewPanel() {
        ViewPanel panel = new ViewPanel();
        PartCanvas canvas = panel.getCanvas();
        canvas.setFocusable(true);
        canvas.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                activePanel = panel;
            }
        });
        // 设置关闭回调：关闭当前视图
        panel.setOnCloseCallback(() -> {
            if (viewPanels.size() <= 1) {
                // 只有一个视图时不关闭
                return;
            }
            activePanel = panel;
            closeActiveView();
        });
        return panel;
    }

    /**
     * 递归构建水平链式 JSplitPane
     * @param index 当前视图在列表中的索引
     */
    private Component buildChain(int index) {
        if (index == viewPanels.size() - 1) {
            return viewPanels.get(index);
        }
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                viewPanels.get(index),
                buildChain(index + 1));
        split.setContinuousLayout(true);
        split.setResizeWeight(0.5);
        split.setOneTouchExpandable(true);
        return split;
    }
}