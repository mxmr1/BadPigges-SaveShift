import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * 存档部件预览窗口
 * 启动后直接打开，支持拖入存档文件加载
 * 根据 Part 的 id、skin、坐标在画布上绘制对应的贴图（或占位色块）
 * 支持右键框选部件和滚轮缩放，选择框会随缩放保持相对位置
 * 顶部有"翻转存档"和"S"按钮
 */
public class SavePreview {
    private final JFrame frame;
    private PartCanvas canvas;
    private final List<core.Part> parts = new ArrayList<>();
    private String currentFilePath = "";
    private String backupDirPath = "";

    private static final Map<String, BufferedImage> imageCache = new HashMap<>();

    public SavePreview() {
        frame = new JFrame("存档预览 - 拖入存档文件开始编辑");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        topPanel.setBackground(Color.LIGHT_GRAY);

        JButton btnFlip = new JButton("翻转存档");
        btnFlip.setPreferredSize(new Dimension(100, 32));
        btnFlip.setMinimumSize(new Dimension(100, 32));
        btnFlip.setMaximumSize(new Dimension(100, 32));
        btnFlip.setFont(new Font("SansSerif", Font.BOLD, 14));
        btnFlip.setMargin(new Insets(0, 0, 0, 0));
        btnFlip.setBorder(BorderFactory.createEmptyBorder());
        btnFlip.setBorderPainted(false);
        btnFlip.setFocusPainted(false);

        JButton btnSave = new JButton("保存存档");
        btnSave.setPreferredSize(new Dimension(100, 32));
        btnSave.setMinimumSize(new Dimension(100, 32));
        btnSave.setMaximumSize(new Dimension(100, 32));
        btnSave.setFont(new Font("SansSerif", Font.BOLD, 14));
        btnSave.setMargin(new Insets(0, 0, 0, 0));
        btnSave.setBorder(BorderFactory.createEmptyBorder());
        btnSave.setBorderPainted(false);
        btnSave.setFocusPainted(false);

        btnFlip.addActionListener(e -> {
            if (currentFilePath.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "请先拖入存档文件！", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String backupDir = backupDirPath.isEmpty() ? new File(currentFilePath).getParent() : backupDirPath;
            try {
                core.convertSave(currentFilePath, backupDir);

                // 转换后重新加载
                String outputPath = new File(backupDir, new File(currentFilePath).getName()).getPath();
                List<core.Part> convertedParts = core.readPartsFromFile(outputPath);
                if (!convertedParts.isEmpty()) {
                    parts.clear();
                    parts.addAll(convertedParts);
                    updateFrameTitle();
                    canvas.autoFitView();
                    canvas.repaint();
                }

                JOptionPane.showMessageDialog(frame, "存档方向转换完成！\n备份位置: " + backupDir, "成功", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "转换失败：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        });

        btnSave.addActionListener(e -> {
            if (currentFilePath.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "请先拖入存档文件！", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String backupDir = backupDirPath.isEmpty() ? new File(currentFilePath).getParent() : backupDirPath;
            try {
                core.backupFile(currentFilePath, backupDir);
                core.writePartsToFile(currentFilePath, parts);
                JOptionPane.showMessageDialog(frame, "备份并保存完成！\n备份位置: " + backupDir, "成功", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "保存失败：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        });

        topPanel.add(btnFlip);
        topPanel.add(btnSave);

        canvas = new PartCanvas();

        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(canvas, BorderLayout.CENTER);

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void updateFrameTitle() {
        String fileName = currentFilePath.isEmpty() ? "无文件" : new File(currentFilePath).getName();
        frame.setTitle("存档预览 - " + fileName + " (" + parts.size() + " 个部件)");
    }

    /**
     * 加载存档文件，更新画布内容
     */
    boolean loadFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) return false;
        try {
            if (!Files.exists(Paths.get(filePath))) {
                JOptionPane.showMessageDialog(frame, "文件不存在: " + filePath, "错误", JOptionPane.ERROR_MESSAGE);
                return false;
            }
            if (hasAnyExtension(filePath)) {
                JOptionPane.showMessageDialog(frame, "文件格式错误：不允许使用带后缀的文件名！", "错误", JOptionPane.ERROR_MESSAGE);
                return false;
            }
            if (!isValidFormat(filePath)) {
                JOptionPane.showMessageDialog(frame, "文件内容格式错误！\n要求：每行6个逗号分隔的整数", "错误", JOptionPane.ERROR_MESSAGE);
                return false;
            }

            List<core.Part> newParts = core.readPartsFromFile(filePath);
            if (newParts.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "文件为空或没有有效数据！", "错误", JOptionPane.ERROR_MESSAGE);
                return false;
            }

            currentFilePath = filePath;
            backupDirPath = new File(filePath).getParent();
            parts.clear();
            parts.addAll(newParts);
            canvas.autoFitView();
            canvas.repaint();
            updateFrameTitle();
            return true;
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, "读取文件失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private static boolean hasAnyExtension(String filePath) {
        String fileName = new File(filePath).getName();
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex > 0;
    }

    private static boolean isValidFormat(String filePath) {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            int lineNum = 0;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(",");
                if (parts.length != 6) return false;
                for (int i = 0; i < 6; i++) {
                    try {
                        Integer.parseInt(parts[i].trim());
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }
                lineNum++;
            }
            return lineNum > 0;
        } catch (IOException e) {
            return false;
        }
    }

    private static BufferedImage loadImage(int id, int skin) {
        String key = id + "_" + skin;
        BufferedImage img = imageCache.get(key);
        if (img != null) return img;

        String filename = key + ".png";
        URL url = SavePreview.class.getClassLoader().getResource(filename);
        if (url != null) {
            try {
                img = ImageIO.read(url);
                if (img != null) { imageCache.put(key, img); return img; }
            } catch (IOException e) { img = null; }
        }

        String[] searchPaths = {
            "src/main/resources/" + filename,
            "resources/" + filename,
            "./" + filename
        };
        for (String path : searchPaths) {
            File f = new File(path);
            if (f.exists()) {
                try {
                    img = ImageIO.read(f);
                    if (img != null) break;
                } catch (IOException e) { img = null; }
            }
        }

        if (img == null) {
            int size = 4;
            img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            g2.setColor(getColorForId(id));
            g2.fillRect(0, 0, size, size);
            g2.dispose();
        }
        imageCache.put(key, img);
        return img;
    }

    private static Color getColorForId(int id) {
        Color[] colors = {
                Color.RED, Color.BLUE, Color.GREEN, Color.ORANGE, Color.CYAN,
                Color.MAGENTA, Color.YELLOW, Color.PINK, Color.LIGHT_GRAY, Color.DARK_GRAY
        };
        Color c = colors[Math.abs(id) % colors.length];
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), 255);
    }

    class PartCanvas extends JPanel {
        private Point dragStart;
        private Point dragEnd;
        private boolean isDragging = false;
        private List<core.Part> selectedParts = new ArrayList<>();

        private boolean hasSelection = false;
        private int selMinCol = -1, selMaxCol = -1;
        private int selMinRow = -1, selMaxRow = -1;

        private int layoutMinX, layoutMinY, layoutMaxY;
        private double layoutCellSize;
        private double layoutOffsetX, layoutOffsetY;
        private int layoutCols, layoutRows;
        private double scale = 1.0;

        // 平移偏移量（像素，使用 double 精度避免缩放抽动）
        private double panX = 0.0;
        private double panY = 0.0;

        // 基准偏移量（在 autoFitView 时设定，保持不变，使用 double）
        private double baseOffsetX = 0.0;
        private double baseOffsetY = 0.0;

        // 按住WASD持续移动相关
        private javax.swing.Timer panTimer;
        private int panDirectionX = 0; // -1左, 0不动, 1右
        private int panDirectionY = 0; // -1上, 0不动, 1下
        private static final long PAN_DURATION_MS = 1500; // 横跨整个屏幕用时1.5秒（原6000ms，提速4倍）

        // 撤销/重做
        private List<List<core.Part>> undoStack = new ArrayList<>();
        private List<List<core.Part>> redoStack = new ArrayList<>();

        // 剪贴板相关
        private List<core.Part> clipboardParts = new ArrayList<>();
        private boolean clipboardActive = false;
        private int clipboardAnchorCol = -1, clipboardAnchorRow = -1;
        private int clipboardMouseCol = 0, clipboardMouseRow = 0;

        // 基础单位像素（每个坐标单位对应多少像素）
        private static final int BASE_UNIT_SIZE = 8;

        // 惯性缩放相关
        private double targetScale = 1.0;
        private javax.swing.Timer inertiaScaleTimer;
        private boolean isZoomingOut = false;
        private int zoomOriginX;
        private int zoomOriginY;
        private static final double INERTIA_FACTOR = 0.15;
        private static final double INERTIA_THRESHOLD = 0.0001;
        PartCanvas() {
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

            // 支持拖入文件
            setTransferHandler(new TransferHandler() {
                @Override
                public boolean canImport(TransferSupport support) {
                    return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
                }

                @Override
                public boolean importData(TransferSupport support) {
                    if (!canImport(support)) return false;
                    Transferable t = support.getTransferable();
                    try {
                        @SuppressWarnings("unchecked")
                        List<File> files = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
                        if (files.isEmpty()) return false;
                        File droppedFile = files.get(0);
                        if (droppedFile.isFile()) {
                            SwingUtilities.invokeLater(() -> loadFile(droppedFile.getAbsolutePath()));
                            return true;
                        }
                    } catch (UnsupportedFlavorException | IOException e) {
                        e.printStackTrace();
                    }
                    return false;
                }
            });

            // 初始化持续移动定时器
            panTimer = new javax.swing.Timer(16, e -> {
                if (panDirectionX == 0 && panDirectionY == 0) return;
                computeLayout();
                int effectiveWidth = getWidth() - 40; // 左右各20 margin
                if (effectiveWidth <= 0) effectiveWidth = 1;
                double pixelsPerMs = (double) effectiveWidth / PAN_DURATION_MS;
                int deltaX = (int) (panDirectionX * pixelsPerMs * 16);
                int deltaY = (int) (panDirectionY * pixelsPerMs * 16);
                panX += deltaX;
                panY += deltaY;
                repaint();
            });
            panTimer.setRepeats(true);

            // 初始化惯性缩放定时器
            inertiaScaleTimer = new javax.swing.Timer(16, e -> {
                updateScaleWithInertia();
            });
            inertiaScaleTimer.setRepeats(true);

            InputMap inputMap = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
            ActionMap actionMap = getActionMap();

            // X 键镜像剪贴板
            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, 0), "mirrorClipboard");
            actionMap.put("mirrorClipboard", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (clipboardActive && !clipboardParts.isEmpty()) {
                        core.mirrorPartsX(clipboardParts);
                        recalcClipboardAnchor();
                        repaint();
                    }
                }
            });

            // WASD 按住持续移动：按下启动方向，释放停止
            // 使用按下/释放 KeyStroke
            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, 0, false), "panWPress");
            actionMap.put("panWPress", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    panDirectionY = 1;
                    startPanTimer();
                }
            });
            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, 0, true), "panWRelease");
            actionMap.put("panWRelease", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (panDirectionY > 0) panDirectionY = 0;
                    stopPanTimerIfNeeded();
                }
            });
            // S
            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0, false), "panSPress");
            actionMap.put("panSPress", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    panDirectionY = -1;
                    startPanTimer();
                }
            });
            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0, true), "panSRelease");
            actionMap.put("panSRelease", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (panDirectionY < 0) panDirectionY = 0;
                    stopPanTimerIfNeeded();
                }
            });
            // A
            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0, false), "panAPress");
            actionMap.put("panAPress", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    panDirectionX = 1;
                    startPanTimer();
                }
            });
            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0, true), "panARelease");
            actionMap.put("panARelease", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (panDirectionX > 0) panDirectionX = 0;
                    stopPanTimerIfNeeded();
                }
            });
            // D
            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0, false), "panDPress");
            actionMap.put("panDPress", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    panDirectionX = -1;
                    startPanTimer();
                }
            });
            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0, true), "panDRelease");
            actionMap.put("panDRelease", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (panDirectionX < 0) panDirectionX = 0;
                    stopPanTimerIfNeeded();
                }
            });

            // 撤销 Ctrl+Z
            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "undo");
            actionMap.put("undo", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    undo();
                }
            });
            // 重做 Ctrl+Y
            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "redo");
            actionMap.put("redo", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    redo();
                }
            });

            // 复制 Ctrl+C
            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "copy");
            actionMap.put("copy", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (selectedParts != null && !selectedParts.isEmpty()) {
                        startClipboard(selectedParts);
                    }
                }
            });

            // 删除选中的部件 Delete 键
            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteSelected");
            actionMap.put("deleteSelected", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (selectedParts == null || selectedParts.isEmpty()) return;
                    saveState();
                    // 从 parts 中移除所有选中的部件
                    parts.removeAll(selectedParts);
                    // 如果剪贴板中的部件与选中的有重叠，清除剪贴板
                    if (clipboardActive) {
                        clipboardParts.clear();
                        clipboardActive = false;
                    }
                    selectedParts.clear();
                    hasSelection = false;
                    repaint();
                }
            });

            MouseAdapter ma = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    // 任何鼠标点击都打断惯性缩放
                    if (inertiaScaleTimer.isRunning()) {
                        // 立即完成剩余的缩放过渡
                        scale = targetScale;
                        inertiaScaleTimer.stop();
                    }
                    if (clipboardActive && SwingUtilities.isRightMouseButton(e)) {
                        cancelClipboard();
                        return;
                    }
                    // 左键放置：实现粘贴逻辑，处理替换规则
                    if (clipboardActive && SwingUtilities.isLeftMouseButton(e)) {
                        computeLayout();
                        int[] grid = new int[2];
                        screenToGrid(e.getX(), e.getY(), grid);
                        int targetCol = grid[0];
                        int targetRow = grid[1];
                        int offsetCol = targetCol - clipboardAnchorCol;
                        int offsetRow = targetRow - clipboardAnchorRow;
                        saveState();
                        pasteWithReplacement(offsetCol, offsetRow);
                        repaint();
                        return;
                    }
                    if (SwingUtilities.isRightMouseButton(e)) {
                        dragStart = e.getPoint();
                        dragEnd = e.getPoint();
                        isDragging = true;
                        repaint();
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (isDragging) {
                        dragEnd = e.getPoint();
                        repaint();
                    }
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    if (clipboardActive) {
                        computeLayout();
                        int[] grid = new int[2];
                        screenToGrid(e.getX(), e.getY(), grid);
                        clipboardMouseCol = grid[0];
                        clipboardMouseRow = grid[1];
                        repaint();
                    }
                    // 如果正在惯性放大中，更新缩放原点为新的鼠标位置，
                    // 并立即调整视角，使当前鼠标下的世界坐标在新原点下保持不变
                    if (isZoomingOut && inertiaScaleTimer.isRunning()) {
                        double currentCellSize = Math.max(1, BASE_UNIT_SIZE * scale);
                        double worldX = (e.getX() - (baseOffsetX + panX)) / currentCellSize;
                        double worldY = (e.getY() - (baseOffsetY + panY)) / currentCellSize;
                        zoomOriginX = e.getX();
                        zoomOriginY = e.getY();
                        panX = (int) Math.round(e.getX() - worldX * currentCellSize - baseOffsetX);
                        panY = (int) Math.round(e.getY() - worldY * currentCellSize - baseOffsetY);
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (isDragging && SwingUtilities.isRightMouseButton(e)) {
                        dragEnd = e.getPoint();
                        isDragging = false;
                        updateSelection();
                        repaint();
                    }
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    double oldScale = scale;
                    int notches = e.getWheelRotation();
                    // 计算目标缩放值，增大灵敏度
                    double newScale = oldScale;
                    if (notches > 0) {
                        newScale /= Math.pow(1.8, notches);
                    } else {
                        newScale *= Math.pow(1.8, -notches);
                    }
                    if (newScale < 0.001) newScale = 0.001;
                    if (newScale > 100.0) newScale = 100.0;
                    targetScale = newScale;

                    if (newScale != oldScale) {
                        // 缩小（滚轮向下）以屏幕中心为原点
                        // 放大（滚轮向上）以鼠标位置为原点
                        if (notches > 0) {
                            // 缩小：以屏幕中心为原点
                            isZoomingOut = false;
                            int margin = 20;
                            int panelW = getWidth() - 2 * margin;
                            int panelH = getHeight() - 2 * margin;
                            zoomOriginX = margin + panelW / 2;
                            zoomOriginY = margin + panelH / 2;
                        } else {
                            // 放大：以鼠标位置为原点
                            isZoomingOut = true;
                            zoomOriginX = e.getX();
                            zoomOriginY = e.getY();
                        }

                        // 启动或保持惯性 Timer
                        if (!inertiaScaleTimer.isRunning()) {
                            inertiaScaleTimer.start();
                        }
                    }
                }
            };

            addMouseListener(ma);
            addMouseMotionListener(ma);
            addMouseWheelListener(ma);
        }

        public void resetView() {
            scale = 1.0;
            targetScale = 1.0;
            if (inertiaScaleTimer != null && inertiaScaleTimer.isRunning()) {
                inertiaScaleTimer.stop();
            }
            panX = 0.0;
            panY = 0.0;
            baseOffsetX = 0;
            baseOffsetY = 0;
            selectedParts.clear();
            hasSelection = false;
            clipboardActive = false;
            clipboardParts.clear();
        }

        /**
         * 自动调整缩放和平移，使所有部件完整显示在画布上
         */
        public void autoFitView() {
            if (parts == null || parts.isEmpty()) {
                resetView();
                return;
            }

            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
            int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
            for (core.Part p : parts) {
                if (p.x < minX) minX = p.x;
                if (p.x > maxX) maxX = p.x;
                if (p.y < minY) minY = p.y;
                if (p.y > maxY) maxY = p.y;
            }

            int cols = maxX - minX + 1;
            int rows = maxY - minY + 1;

            // 计算能够使整个范围完整显示的缩放比例
            int margin = 40;
            int panelW = getWidth() - 2 * margin;
            int panelH = getHeight() - 2 * margin;

            if (panelW <= 0 || panelH <= 0 || cols <= 0 || rows <= 0) {
                scale = 1.0;
            } else {
                double scaleByWidth = (double) panelW / (cols * BASE_UNIT_SIZE);
                double scaleByHeight = (double) panelH / (rows * BASE_UNIT_SIZE);
                scale = Math.min(scaleByWidth, scaleByHeight);
            }

            if (scale < 0.001) scale = 0.001;
            if (scale > 100.0) scale = 100.0;
            targetScale = scale;

            if (inertiaScaleTimer != null && inertiaScaleTimer.isRunning()) {
                inertiaScaleTimer.stop();
            }

            // 计算基准偏移并保存，使画面居中
            layoutCellSize = Math.max(1, BASE_UNIT_SIZE * scale);
            double totalPixelW = cols * layoutCellSize;
            double totalPixelH = rows * layoutCellSize;
            baseOffsetX = margin + (panelW - totalPixelW) / 2.0;
            baseOffsetY = margin + (panelH - totalPixelH) / 2.0;

            // 重置平移偏移
            panX = 0;
            panY = 0;
            selectedParts.clear();
            hasSelection = false;
            clipboardActive = false;
            clipboardParts.clear();
        }

        private void startPanTimer() {
            if (!panTimer.isRunning()) {
                panTimer.start();
            }
        }

        private void stopPanTimerIfNeeded() {
            if (panDirectionX == 0 && panDirectionY == 0) {
                panTimer.stop();
            }
        }

                /**
         * 非线性衰减惯性缩放，每帧按比例逼近目标值
         */
        private void updateScaleWithInertia() {
            double diff = targetScale - scale;
            if (Math.abs(diff) < INERTIA_THRESHOLD) {
                scale = targetScale;
                inertiaScaleTimer.stop();
                repaint();
                return;
            }
            double oldScale = scale;
            scale += diff * INERTIA_FACTOR;

            // 限制
            if (scale < 0.001) scale = 0.001;
            if (scale > 100.0) scale = 100.0;

            // 根据记录的缩放原点调整 panX/panY
            double oldCellSize = Math.max(1, BASE_UNIT_SIZE * oldScale);
            double newCellSize = Math.max(1, BASE_UNIT_SIZE * scale);

            double worldX = (zoomOriginX - (baseOffsetX + panX)) / oldCellSize;
            double worldY = (zoomOriginY - (baseOffsetY + panY)) / oldCellSize;

            double newLayoutOffsetX = zoomOriginX - worldX * newCellSize;
            double newLayoutOffsetY = zoomOriginY - worldY * newCellSize;

            panX = (int) Math.round(newLayoutOffsetX - baseOffsetX);
            panY = (int) Math.round(newLayoutOffsetY - baseOffsetY);

            repaint();
        }

        private void computeLayout() {
            if (parts == null || parts.isEmpty()) {
                layoutMinX = 0; layoutMinY = 0; layoutMaxY = 0;
                layoutCellSize = 1; layoutOffsetX = 0; layoutOffsetY = 0;
                layoutCols = 0; layoutRows = 0;
                return;
            }

            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
            int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
            for (core.Part p : parts) {
                if (p.x < minX) minX = p.x;
                if (p.x > maxX) maxX = p.x;
                if (p.y < minY) minY = p.y;
                if (p.y > maxY) maxY = p.y;
            }

            layoutMinX = minX; layoutMinY = minY; layoutMaxY = maxY;
            layoutCols = maxX - minX + 1;
            layoutRows = maxY - minY + 1;

            // 使用固定基础像素大小，乘缩放系数
            layoutCellSize = Math.max(1, BASE_UNIT_SIZE * scale);

            // 使用基准偏移 + 平移偏移，不随部件范围变化
            layoutOffsetX = baseOffsetX + panX;
            layoutOffsetY = baseOffsetY + panY;
        }

        private void screenToGrid(int sx, int sy, int[] colRow) {
            double colF = (double)(sx - layoutOffsetX) / layoutCellSize;
            double rowF = (double)(sy - layoutOffsetY) / layoutCellSize;
            int col = (int)Math.floor(colF);
            int row = (int)Math.floor(rowF);
            // 允许任意坐标，不再限制在现有布局范围内
            colRow[0] = col;
            colRow[1] = row;
        }

        /**
         * 将选中的部件复制到剪贴板，并进入粘贴预览模式。
         * 记录所有选中部件相对于它们整体范围左下角的偏移。
         */
        public void startClipboard(List<core.Part> source) {
            if (source == null || source.isEmpty()) return;
            computeLayout();

            // 计算 source 的最小列/行（左下角）
            int minCol = Integer.MAX_VALUE;
            int minRow = Integer.MAX_VALUE;
            for (core.Part p : source) {
                int col = p.x - layoutMinX;
                int row = layoutMaxY - p.y;
                if (col < minCol) minCol = col;
                if (row < minRow) minRow = row;
            }

            clipboardAnchorCol = minCol;
            clipboardAnchorRow = minRow;

            // 存储相对于锚点的偏移 (dx, dy) 以及原始属性
            clipboardParts.clear();
            for (core.Part p : source) {
                // 深拷贝：创建独立的 Part 对象，避免修改时影响原始列表
                clipboardParts.add(new core.Part(p.id, p.skin, p.x, p.y, p.orientation, p.flipped));
            }

            // 初始时鼠标位置设为锚点
            clipboardMouseCol = minCol;
            clipboardMouseRow = minRow;
            clipboardActive = true;
            repaint();
        }

        /*
         * 重新计算剪贴板锚点（基于当前 clipboardParts 的最小网格列/行）
         */
        private void recalcClipboardAnchor() {
            if (clipboardParts.isEmpty()) return;
            int minCol = Integer.MAX_VALUE;
            int minRow = Integer.MAX_VALUE;
            for (core.Part p : clipboardParts) {
                int col = p.x - layoutMinX;
                int row = layoutMaxY - p.y;
                if (col < minCol) minCol = col;
                if (row < minRow) minRow = row;
            }
            clipboardAnchorCol = minCol;
            clipboardAnchorRow = minRow;
        }

        /**
         * 取消剪贴板预览模式，清除所有剪贴板状态和选择状态
         */
        private void cancelClipboard() {
            clipboardActive = false;
            clipboardParts.clear();
            clipboardAnchorCol = -1;
            clipboardAnchorRow = -1;
            clipboardMouseCol = 0;
            clipboardMouseRow = 0;
            selectedParts.clear();
            hasSelection = false;
            repaint();
        }

        /**
         * 保存当前 parts 快照到撤销栈，清空重做栈
         */
        private void saveState() {
            List<core.Part> snapshot = new ArrayList<>();
            for (core.Part p : parts) {
                snapshot.add(new core.Part(p.id, p.skin, p.x, p.y, p.orientation, p.flipped));
            }
            undoStack.add(snapshot);
            if (undoStack.size() > 50) {
                undoStack.remove(0);
            }
            redoStack.clear();
        }

        /**
         * 判断部件是否为框架类（id=5或6）
         */
        private boolean isFramePart(core.Part p) {
            return p.id == 5 || p.id == 6;
        }

        /**
         * 按照替换规则粘贴剪贴板中的部件
         * @param offsetCol 列偏移
         * @param offsetRow 行偏移
         */
        private void pasteWithReplacement(int offsetCol, int offsetRow) {
            // 构建粘贴部件列表（转换坐标后的新部件，保留所有部件，不去重）
            List<core.Part> newParts = new ArrayList<>();
            for (core.Part p : clipboardParts) {
                int newX = p.x + offsetCol;
                int newY = p.y - offsetRow;
                newParts.add(new core.Part(p.id, p.skin, newX, newY, p.orientation, p.flipped));
            }

            // 需要被删除的原有部件索引
            Set<Integer> indicesToRemove = new HashSet<>();

            // 对于每个粘贴部件，检查其目标坐标下的所有原部件，决定替换或共存
            for (core.Part pastePart : newParts) {
                boolean pasteIsFrame = isFramePart(pastePart);

                // 收集该坐标下的所有原有部件索引
                List<Integer> sameCoordIndices = new ArrayList<>();
                for (int i = 0; i < parts.size(); i++) {
                    core.Part p = parts.get(i);
                    if (p.x == pastePart.x && p.y == pastePart.y) {
                        sameCoordIndices.add(i);
                    }
                }

                // 处理每个原有部件：同类型标记删除，不同类型保持共存
                for (int idx : sameCoordIndices) {
                    core.Part existingPart = parts.get(idx);
                    boolean existingIsFrame = isFramePart(existingPart);
                    if (existingIsFrame == pasteIsFrame) {
                        // 同类型 → 替换
                        indicesToRemove.add(idx);
                    }
                    // 不同类型 → 共存（不删除）
                }
            }

            // 从后往前删除
            List<Integer> sortedRemove = new ArrayList<>(indicesToRemove);
            Collections.sort(sortedRemove, Collections.reverseOrder());
            for (int idx : sortedRemove) {
                parts.remove(idx);
            }

            // 添加所有粘贴部件（保留剪贴板状态以支持连续放置）
            parts.addAll(newParts);
        }

        /**
         * 撤销：将 parts 还原为上次快照
         */
        private void undo() {
            if (undoStack.isEmpty()) return;
            // 保存当前状态到重做栈
            List<core.Part> snapshot = new ArrayList<>();
            for (core.Part p : parts) {
                snapshot.add(new core.Part(p.id, p.skin, p.x, p.y, p.orientation, p.flipped));
            }
            redoStack.add(snapshot);
            // 还原
            List<core.Part> prev = undoStack.remove(undoStack.size() - 1);
            parts.clear();
            parts.addAll(prev);
            repaint();
        }

        /**
         * 重做：将 parts 还原为上上次快照
         */
        private void redo() {
            if (redoStack.isEmpty()) return;
            // 保存当前状态到撤销栈
            List<core.Part> snapshot = new ArrayList<>();
            for (core.Part p : parts) {
                snapshot.add(new core.Part(p.id, p.skin, p.x, p.y, p.orientation, p.flipped));
            }
            undoStack.add(snapshot);
            // 还原
            List<core.Part> next = redoStack.remove(redoStack.size() - 1);
            parts.clear();
            parts.addAll(next);
            repaint();
        }

        private void updateSelection() {
            selectedParts.clear();
            hasSelection = false;
            if (parts == null || parts.isEmpty() || dragStart == null || dragEnd == null) return;

            computeLayout();

            int selW = Math.abs(dragEnd.x - dragStart.x);
            int selH = Math.abs(dragEnd.y - dragStart.y);

            if (selW < 2 && selH < 2) return;

            int[] startGrid = new int[2];
            int[] endGrid = new int[2];
            screenToGrid(dragStart.x, dragStart.y, startGrid);
            screenToGrid(dragEnd.x, dragEnd.y, endGrid);

            selMinCol = Math.min(startGrid[0], endGrid[0]);
            selMaxCol = Math.max(startGrid[0], endGrid[0]);
            selMinRow = Math.min(startGrid[1], endGrid[1]);
            selMaxRow = Math.max(startGrid[1], endGrid[1]);

            hasSelection = true;

            for (core.Part p : parts) {
                int col = p.x - layoutMinX;
                int row = layoutMaxY - p.y;
                if (col >= selMinCol && col <= selMaxCol && row >= selMinRow && row <= selMaxRow) {
                    selectedParts.add(p);
                }
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            if (parts == null || parts.isEmpty()) {
                g2.setColor(Color.GRAY);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 24));
                String msg = "拖入存档文件以开始编辑";
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(msg)) / 2;
                int y = getHeight() / 2;
                g2.drawString(msg, x, y);
                drawSelectionRect(g2);
                return;
            }

            computeLayout();

            double offsetX = layoutOffsetX;
            double offsetY = layoutOffsetY;
            double cellSize = layoutCellSize;

            for (core.Part p : parts) {
                int col = p.x - layoutMinX;
                int row = layoutMaxY - p.y;
                int x0 = (int)Math.round(offsetX + col * cellSize);
                int y0 = (int)Math.round(offsetY + row * cellSize);
                int x1 = (int)Math.round(offsetX + (col + 1) * cellSize);
                int y1 = (int)Math.round(offsetY + (row + 1) * cellSize);
                int drawW = x1 - x0;
                int drawH = y1 - y0;
                if (drawW < 1) drawW = 1;
                if (drawH < 1) drawH = 1;
                BufferedImage img = loadImage(p.id, p.skin);
                g2.drawImage(img, x0, y0, drawW, drawH, null);
            }

            // 绘制剪贴板半透明预览（25%透明）
            if (clipboardActive && !clipboardParts.isEmpty()) {
                int offsetCol = clipboardMouseCol - clipboardAnchorCol;
                int offsetRow = clipboardMouseRow - clipboardAnchorRow;
                Composite oldComposite = g2.getComposite();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f));

                for (core.Part p : clipboardParts) {
                    int col = (p.x - layoutMinX) + offsetCol;
                    int row = (layoutMaxY - p.y) + offsetRow;
                    int x0 = (int)Math.round(offsetX + col * cellSize);
                    int y0 = (int)Math.round(offsetY + row * cellSize);
                    int x1 = (int)Math.round(offsetX + (col + 1) * cellSize);
                    int y1 = (int)Math.round(offsetY + (row + 1) * cellSize);
                    int drawW = x1 - x0;
                    int drawH = y1 - y0;
                    if (drawW < 1) drawW = 1;
                    if (drawH < 1) drawH = 1;
                    BufferedImage img = loadImage(p.id, p.skin);
                    g2.drawImage(img, x0, y0, drawW, drawH, null);
                }

                g2.setComposite(oldComposite);
            }

            g2.setColor(new Color(0, 200, 255, 80));
            for (core.Part p : selectedParts) {
                int col = p.x - layoutMinX;
                int row = layoutMaxY - p.y;
                int x0 = (int)Math.round(offsetX + col * cellSize);
                int y0 = (int)Math.round(offsetY + row * cellSize);
                int x1 = (int)Math.round(offsetX + (col + 1) * cellSize);
                int y1 = (int)Math.round(offsetY + (row + 1) * cellSize);
                int drawW = x1 - x0;
                int drawH = y1 - y0;
                if (drawW < 1) drawW = 1;
                if (drawH < 1) drawH = 1;
                g2.fillRect(x0, y0, drawW, drawH);
            }

            drawSelectionRect(g2);
        }

        private void drawSelectionRect(Graphics2D g2) {
            if (isDragging && dragStart != null && dragEnd != null && !dragStart.equals(dragEnd)) {
                int x = Math.min(dragStart.x, dragEnd.x);
                int y = Math.min(dragStart.y, dragEnd.y);
                int w = Math.abs(dragEnd.x - dragStart.x);
                int h = Math.abs(dragEnd.y - dragStart.y);
                if (w > 0 && h > 0) {
                    g2.setColor(new Color(0, 120, 255, 50));
                    g2.fillRect(x, y, w, h);
                    g2.setColor(new Color(0, 120, 255));
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRect(x, y, w, h);
                }
                return;
            }

            if (hasSelection) {
                int x0 = (int)Math.round(layoutOffsetX + selMinCol * layoutCellSize);
                int y0 = (int)Math.round(layoutOffsetY + selMinRow * layoutCellSize);
                int x1 = (int)Math.round(layoutOffsetX + (selMaxCol + 1) * layoutCellSize);
                int y1 = (int)Math.round(layoutOffsetY + (selMaxRow + 1) * layoutCellSize);
                int rx = Math.min(x0, x1);
                int ry = Math.min(y0, y1);
                int rw = Math.abs(x1 - x0);
                int rh = Math.abs(y1 - y0);
                if (rw > 0 && rh > 0) {
                    g2.setColor(new Color(0, 120, 255, 50));
                    g2.fillRect(rx, ry, rw, rh);
                    g2.setColor(new Color(0, 120, 255));
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRect(rx, ry, rw, rh);
                }
            }
        }
    }
}
