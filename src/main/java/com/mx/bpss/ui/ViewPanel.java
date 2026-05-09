package com.mx.bpss.ui;

import com.mx.bpss.canvas.PartCanvas;
import javax.swing.*;
import java.awt.*;

/**
 * 视图面板：每个视图包含自己的按钮栏和 PartCanvas 画布
 */
public class ViewPanel extends JPanel {

    private PartCanvas canvas;
    /** 关闭回调，由外部（MultiViewManager）设置 */
    private Runnable onCloseCallback = () -> {};

    public ViewPanel() {
        super(new BorderLayout());

        // 创建 PartCanvas
        canvas = new PartCanvas(
            title -> {},  // 标题由外部管理
            () -> {}
        );

        // 创建按钮栏
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        topPanel.setBackground(Color.LIGHT_GRAY);

        JButton btnFlip = createButton("翻转存档");
        btnFlip.addActionListener(e -> canvas.flipSave());

        JButton btnSave = createButton("保存存档");
        btnSave.addActionListener(e -> canvas.saveCurrent());

        JButton btnDelete = createButton("删除");
        btnDelete.addActionListener(e -> canvas.deleteSelectedParts());

        JButton btnClose = createButton("关闭视图");
        btnClose.addActionListener(e -> onCloseCallback.run());

        JButton btnSelect = createButton("◉ 选择");
        btnSelect.addActionListener(e -> canvas.requestFocusInWindow());

        topPanel.add(btnFlip);
        topPanel.add(btnSave);
        topPanel.add(btnDelete);
        topPanel.add(btnClose);
        topPanel.add(btnSelect);

        add(topPanel, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
    }

    public PartCanvas getCanvas() {
        return canvas;
    }

    /**
     * 设置关闭按钮的回调
     */
    public void setOnCloseCallback(Runnable callback) {
        this.onCloseCallback = callback;
    }

    private JButton createButton(String text) {
        JButton btn = new JButton(text);
        btn.setPreferredSize(new Dimension(80, 26));
        btn.setMinimumSize(new Dimension(80, 26));
        btn.setMaximumSize(new Dimension(80, 26));
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setMargin(new Insets(0, 0, 0, 0));
        btn.setBorder(BorderFactory.createEmptyBorder());
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        return btn;
    }
}