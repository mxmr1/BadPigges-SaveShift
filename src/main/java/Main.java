import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    static String inputFilePath = "";
    static String outputDirPath = "";
    static JTextField inputText;  // 改为静态，方便 TransferHandler 更新

    public static void main(String[] args) {
        JFrame frame = new JFrame("存档方向转换工具");
        frame.setSize(400, 200);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null); // 窗口居中

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        JPanel panel = new JPanel();
        frame.add(panel);
        placeComponents(panel);

        // 设置拖拽支持
        frame.setTransferHandler(new FileDropHandler());

        frame.setVisible(true);
    }

    private static void placeComponents(JPanel panel) {
        panel.setLayout(null);

        JLabel inputLabel = new JLabel("输入文件:");
        inputLabel.setBounds(10, 20, 80, 25);
        panel.add(inputLabel);

        inputText = new JTextField(20);
        inputText.setBounds(85, 20, 165, 25);
        inputText.setEditable(false);
        panel.add(inputText);

        JButton inputButton = new JButton("选择输入文件");
        inputButton.setBounds(260, 20, 120, 25);
        inputButton.addActionListener(new inputBrowseListener(inputText));
        panel.add(inputButton);

        JLabel outputLabel = new JLabel("备份文件夹:");
        outputLabel.setBounds(10, 60, 80, 25);
        panel.add(outputLabel);

        JTextField outputText = new JTextField(20);
        outputText.setBounds(85, 60, 165, 25);
        outputText.setEditable(false);
        panel.add(outputText);

        JButton outputButton = new JButton("选择备份文件夹");
        outputButton.setBounds(260, 60, 120, 25);
        outputButton.addActionListener(new outputBrowseListener(outputText));
        panel.add(outputButton);

        JButton startButton = new JButton("转换存档方向");
        startButton.setBounds(100, 120, 150, 25);
        startButton.addActionListener(new startSaveShift());
        panel.add(startButton);
    }

    // 检查文件名是否带有任何后缀（只要文件名包含点号且点号不是第一个字符，即认为有后缀）
    private static boolean hasAnyExtension(String filePath) {
        String fileName = new File(filePath).getName();
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            System.err.println("文件名包含后缀：" + fileName.substring(lastDotIndex));
            return true;
        }
        return false;
    }

    // 验证文件内容格式：每行必须是6个逗号分隔的整数
    private static boolean isValidFormat(String filePath) {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            int lineNum = 0;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(",");
                if (parts.length != 6) {
                    System.err.println("行 " + (lineNum + 1) + " 字段数不是6: " + line);
                    return false;
                }
                for (int i = 0; i < 6; i++) {
                    try {
                        Integer.parseInt(parts[i].trim());
                    } catch (NumberFormatException e) {
                        System.err.println("行 " + (lineNum + 1) + " 字段 " + i + " 不是整数: " + parts[i]);
                        return false;
                    }
                }
                lineNum++;
            }
            if (lineNum == 0) {
                System.err.println("文件为空");
                return false;
            }
            return true;
        } catch (IOException e) {
            System.err.println("读取文件时出错: " + e.getMessage());
            return false;
        }
    }

    // 通用方法：将 JFileChooser 设置为详细信息视图
    private static void setDetailsView(JFileChooser fileChooser) {
        Action detailsAction = fileChooser.getActionMap().get("viewTypeDetails");
        if (detailsAction != null) {
            detailsAction.actionPerformed(null);
        }
    }

    // 监听“选择输入文件”按钮
    public static class inputBrowseListener implements ActionListener {
        private JTextField textField;

        public inputBrowseListener(JTextField textField) {
            this.textField = textField;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JFileChooser fileChooser = new JFileChooser(".");
            fileChooser.setDialogTitle("选择输入文件");

            // 强制设置为详细信息视图
            setDetailsView(fileChooser);

            JButton source = (JButton) e.getSource();
            JFrame topFrame = (JFrame) SwingUtilities.getWindowAncestor(source);

            int result = fileChooser.showOpenDialog(topFrame);
            if (result == JFileChooser.APPROVE_OPTION) {
                inputFilePath = fileChooser.getSelectedFile().getAbsolutePath();
                textField.setText(inputFilePath);
                System.out.println("选中输入文件: " + inputFilePath);
            } else {
                System.out.println("用户取消了输入文件选择");
            }
        }
    }

    // 监听“选择输出文件夹”按钮
    public static class outputBrowseListener implements ActionListener {
        private JTextField textField;

        public outputBrowseListener(JTextField textField) {
            this.textField = textField;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JFileChooser folderChooser = new JFileChooser(".");
            folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            folderChooser.setDialogTitle("选择备份文件夹");

            // 强制设置为详细信息视图
            setDetailsView(folderChooser);

            JButton source = (JButton) e.getSource();
            JFrame topFrame = (JFrame) SwingUtilities.getWindowAncestor(source);

            int result = folderChooser.showOpenDialog(topFrame);
            if (result == JFileChooser.APPROVE_OPTION) {
                outputDirPath = folderChooser.getSelectedFile().getAbsolutePath();
                textField.setText(outputDirPath);
                System.out.println("选中备份文件夹: " + outputDirPath);
            } else {
                System.out.println("用户取消了备份文件夹选择");
            }
        }
    }

    // 自定义 TransferHandler 处理文件拖拽
    static class FileDropHandler extends TransferHandler {
        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }

            Transferable t = support.getTransferable();
            try {
                @SuppressWarnings("unchecked")
                List<File> files = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
                if (files == null || files.isEmpty()) {
                    return false;
                }
                // 只取第一个文件
                File droppedFile = files.get(0);
                if (droppedFile.isFile()) {
                    inputFilePath = droppedFile.getAbsolutePath();
                    // 更新界面上的文本框
                    SwingUtilities.invokeLater(() -> inputText.setText(inputFilePath));
                    System.out.println("拖拽文件: " + inputFilePath);
                    return true;
                }
            } catch (UnsupportedFlavorException | IOException e) {
                e.printStackTrace();
            }
            return false;
        }
    }

    // 监听“转换存档方向”按钮
    public static class startSaveShift implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (inputFilePath.isEmpty()) {
                JOptionPane.showMessageDialog(null, "请先选择输入文件！", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!Files.exists(Paths.get(inputFilePath))) {
                JOptionPane.showMessageDialog(null, "输入文件不存在，请重新选择！", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (hasAnyExtension(inputFilePath)) {
                JOptionPane.showMessageDialog(null, "文件格式错误：不允许使用带后缀的文件名！", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (!isValidFormat(inputFilePath)) {
                JOptionPane.showMessageDialog(null, "文件内容格式错误！\n要求：每行6个逗号分隔的整数", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (outputDirPath.isEmpty()) {
                outputDirPath = inputFilePath.substring(0, inputFilePath.lastIndexOf(File.separatorChar));
            }
            try {
                if (!Files.exists(Paths.get(outputDirPath))) {
                    Files.createDirectories(Paths.get(outputDirPath));
                }
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(null, "无法创建备份文件夹：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            System.out.println("开始转换存档方向！");
            try {
                core.convertSave(inputFilePath, outputDirPath);
                JOptionPane.showMessageDialog(null, "转换完成！", "成功", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(null, "转换失败：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        }
    }
}