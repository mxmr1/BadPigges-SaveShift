package com.mx.bpss.ui;

import javax.swing.*;
import java.awt.*;

/**
 * 主窗口 - 多视图拆分模式
 * 顶部工具栏仅保留共同的「新建视图」按钮，
 * 每个视图自带「翻转存档」「保存存档」「关闭视图」按钮
 */
public class SavePreview {

    private JFrame frame;
    private JPanel multiViewContainer;
    private MultiViewManager viewManager;

    public SavePreview() {
        frame = new JFrame("存档预览 - 多视图");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(960, 540); // 取消最大化时恢复此大小
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

        JButton btnNewView = new JButton("新建视图");
        btnNewView.setPreferredSize(new Dimension(100, 30));
        btnNewView.setFont(new Font("SansSerif", Font.BOLD, 14));
        btnNewView.setFocusPainted(false);
        btnNewView.addActionListener(e -> viewManager.addNewView());

        topPanel.add(btnNewView);
        return topPanel;
    }
}