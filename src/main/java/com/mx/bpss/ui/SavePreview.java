package com.mx.bpss.ui;

import com.mx.bpss.canvas.PartCanvas;
import javax.swing.*;
import java.awt.*;

/**
 * 主窗口 - 多视图拆分模式
 */
public class SavePreview {

    private JFrame frame;
    private JPanel multiViewContainer;
    private MultiViewManager viewManager;

    public SavePreview() {
        frame = new JFrame("存档预览 - 多视图");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);

        multiViewContainer = new JPanel(new BorderLayout());
        frame.add(multiViewContainer, BorderLayout.CENTER);

        viewManager = new MultiViewManager(multiViewContainer);

        frame.add(createTopPanel(), BorderLayout.NORTH);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JPanel createTopPanel() {
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        topPanel.setBackground(Color.LIGHT_GRAY);

        JButton btnNewView = createButton("新建视图");
        btnNewView.addActionListener(e -> viewManager.addNewView().requestFocusInWindow());

        JButton btnCloseView = createButton("关闭视图");
        btnCloseView.addActionListener(e -> viewManager.closeActiveView());

        JButton btnFlip = createButton("翻转存档");
        btnFlip.addActionListener(e -> {
            PartCanvas active = viewManager.getActiveCanvas();
            if (active != null) active.flipSave();
        });

        JButton btnSave = createButton("保存存档");
        btnSave.addActionListener(e -> {
            PartCanvas active = viewManager.getActiveCanvas();
            if (active != null) active.saveCurrent();
        });

        topPanel.add(btnNewView);
        topPanel.add(btnCloseView);
        topPanel.add(btnFlip);
        topPanel.add(btnSave);
        return topPanel;
    }

    private JButton createButton(String text) {
        JButton btn = new JButton(text);
        btn.setPreferredSize(new Dimension(100, 32));
        btn.setMinimumSize(new Dimension(100, 32));
        btn.setMaximumSize(new Dimension(100, 32));
        btn.setFont(new Font("SansSerif", Font.BOLD, 14));
        btn.setMargin(new Insets(0, 0, 0, 0));
        btn.setBorder(BorderFactory.createEmptyBorder());
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        return btn;
    }
}