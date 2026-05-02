import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * 存档部件预览窗口
 * 根据 Part 的 id、skin、坐标在画布上绘制对应的贴图（或占位色块）
 * 支持右键框选部件和滚轮缩放，选择框会随缩放保持相对位置
 * 顶部有固定的 C/V 按钮
 */
public class SavePreview {
    private JFrame frame;
    private PartCanvas canvas;
    private List<core.Part> parts;

    private static final Map<String, BufferedImage> imageCache = new HashMap<>();

    public SavePreview(List<core.Part> parts) {
        this.parts = parts;
        frame = new JFrame("存档预览");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        topPanel.setBackground(Color.LIGHT_GRAY);

        JButton btnC = new JButton("翻转存档");
        btnC.setPreferredSize(new Dimension(100, 32));
        btnC.setMinimumSize(new Dimension(100, 32));
        btnC.setMaximumSize(new Dimension(100, 32));
        btnC.setFont(new Font("SansSerif", Font.BOLD, 14));
        btnC.setMargin(new Insets(0, 0, 0, 0));
        btnC.setBorder(BorderFactory.createEmptyBorder());
        btnC.setBorderPainted(false);
        btnC.setFocusPainted(false);

        JButton btnSave = new JButton("S");
        btnSave.setPreferredSize(new Dimension(32, 32));
        btnSave.setMinimumSize(new Dimension(32, 32));
        btnSave.setMaximumSize(new Dimension(32, 32));
        btnSave.setFont(new Font("SansSerif", Font.BOLD, 14));
        btnSave.setMargin(new Insets(0, 0, 0, 0));
        btnSave.setBorder(BorderFactory.createEmptyBorder());
        btnSave.setBorderPainted(false);
        btnSave.setFocusPainted(false);

        btnC.addActionListener(e -> {
            // 转换存档方向（与 Main 中 startSaveShift 逻辑一致）
            String inputFile = Main.inputFilePath;
            if (inputFile == null || inputFile.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "没有打开的存档文件！", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String backupDir = Main.outputDirPath;
            if (backupDir == null || backupDir.isEmpty()) {
                backupDir = inputFile.substring(0, inputFile.lastIndexOf(File.separatorChar));
            }
            try {
                core.convertSave(inputFile, backupDir);

                // 转换后刷新预览
                String outputPath = new java.io.File(backupDir, new java.io.File(inputFile).getName()).getPath();
                java.util.List<core.Part> convertedParts = core.readPartsFromFile(outputPath);
                if (convertedParts != null && !convertedParts.isEmpty()) {
                    parts.clear();
                    parts.addAll(convertedParts);
                    canvas.repaint();
                }

                JOptionPane.showMessageDialog(frame, "存档方向转换完成！", "成功", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "转换失败：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        });

        btnSave.addActionListener(e -> {
            // 保存功能：先备份，再保存当前部件列表到文件
            try {
                String inputFile = Main.inputFilePath;
                if (inputFile == null || inputFile.isEmpty()) {
                    JOptionPane.showMessageDialog(frame, "没有可保存的文件路径！", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                // 1. 创建备份
                String backupDir = Main.outputDirPath;
                if (backupDir == null || backupDir.isEmpty()) {
                    // 如果备份目录为空，使用文件所在目录
                    backupDir = inputFile.substring(0, inputFile.lastIndexOf(File.separatorChar));
                }
                core.backupFile(inputFile, backupDir);

                // 2. 将当前部件列表保存到原文件
                core.writePartsToFile(inputFile, parts);

                JOptionPane.showMessageDialog(frame, "备份并保存完成！\n备份位置: " + backupDir, "成功", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "保存失败：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        });

        topPanel.add(btnC);
        topPanel.add(btnSave);

        canvas = new PartCanvas();

        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(canvas, BorderLayout.CENTER);

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public void updateParts(List<core.Part> newParts) {
        this.parts = newParts;
        canvas.repaint();
    }

    public List<core.Part> getParts() {
        return parts;
    }

    public List<core.Part> getSelectedParts() {
        return canvas.getSelectedParts();
    }

    public void dispose() {
        frame.dispose();
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
        private int layoutCellSize;
        private int layoutOffsetX, layoutOffsetY;
        private int layoutCols, layoutRows;
        private double scale = 1.0;

        // 平移偏移量（像素）
        private int panX = 0;
        private int panY = 0;

        // 按住WASD持续移动相关
        private javax.swing.Timer panTimer;
        private int panDirectionX = 0; // -1左, 0不动, 1右
        private int panDirectionY = 0; // -1上, 0不动, 1下
        private static final long PAN_DURATION_MS = 6000; // 横跨整个屏幕用时6秒

        // 撤销/重做
        private List<List<core.Part>> undoStack = new ArrayList<>();
        private List<List<core.Part>> redoStack = new ArrayList<>();

        // 剪贴板相关
        private List<core.Part> clipboardParts = new ArrayList<>();
        private boolean clipboardActive = false;
        private int clipboardAnchorCol = -1, clipboardAnchorRow = -1;
        private int clipboardMouseCol = 0, clipboardMouseRow = 0;

        PartCanvas() {
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

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

            MouseAdapter ma = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (clipboardActive && SwingUtilities.isRightMouseButton(e)) {
                        cancelClipboard();
                        return;
                    }
                    // 左键放置：将复制的部件添加到列表末尾（保留剪贴板用于连续放置）
                    if (clipboardActive && SwingUtilities.isLeftMouseButton(e)) {
                        computeLayout();
                        int[] grid = new int[2];
                        screenToGrid(e.getX(), e.getY(), grid);
                        int targetCol = grid[0];
                        int targetRow = grid[1];
                        int offsetCol = targetCol - clipboardAnchorCol;
                        int offsetRow = targetRow - clipboardAnchorRow;
                        saveState();
                        for (core.Part p : clipboardParts) {
                            int newX = p.x + offsetCol;
                            int newY = p.y - offsetRow;
                            parts.add(new core.Part(p.id, p.skin, newX, newY, p.orientation, p.flipped));
                        }
                        // 不取消预览，保持剪贴板状态以支持连续放置
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
                    scale -= notches * 0.1;
                    if (scale < 0.1) scale = 0.1;
                    if (scale > 5.0) scale = 5.0;
                    if (scale != oldScale) {
                        repaint();
                    }
                }
            };

            addMouseListener(ma);
            addMouseMotionListener(ma);
            addMouseWheelListener(ma);
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

            if (layoutCols <= 0 || layoutRows <= 0) {
                layoutCellSize = 1;
                layoutOffsetX = 0;
                layoutOffsetY = 0;
                return;
            }

            int margin = 20;
            int panelW = getWidth() - 2 * margin;
            int panelH = getHeight() - 2 * margin;
            int cellW = panelW / layoutCols;
            int cellH = panelH / layoutRows;
            int baseCellSize = Math.min(cellW, cellH);
            if (baseCellSize < 1) baseCellSize = 1;

            layoutCellSize = (int) (baseCellSize * scale);
            if (layoutCellSize < 1) layoutCellSize = 1;

            int gridPixelW = layoutCols * layoutCellSize;
            int gridPixelH = layoutRows * layoutCellSize;
            layoutOffsetX = margin + (panelW - gridPixelW) / 2 + panX;
            layoutOffsetY = margin + (panelH - gridPixelH) / 2 + panY;
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

        public List<core.Part> pasteClipboard() {
            if (!clipboardActive || clipboardParts.isEmpty()) return new ArrayList<>();

            int offsetCol = clipboardMouseCol - clipboardAnchorCol;
            int offsetRow = clipboardMouseRow - clipboardAnchorRow;

            List<core.Part> result = new ArrayList<>();
            for (core.Part p : clipboardParts) {
                int newX = p.x + offsetCol;
                // 网格 row 与 y 反向：row = layoutMaxY - y
                // 所以 row 增加 offsetRow 等价于 y 减少 offsetRow
                int newY = p.y - offsetRow;
                result.add(new core.Part(p.id, p.skin, newX, newY, p.orientation, p.flipped));
            }

            clipboardActive = false;
            clipboardParts.clear();
            repaint();
            return result;
        }

        private Rectangle getPartScreenRect(core.Part p) {
            int col = p.x - layoutMinX;
            int row = layoutMaxY - p.y;
            int canvasX = layoutOffsetX + col * layoutCellSize;
            int canvasY = layoutOffsetY + row * layoutCellSize;
            return new Rectangle(canvasX, canvasY, layoutCellSize, layoutCellSize);
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

        public List<core.Part> getSelectedParts() {
            return selectedParts;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            if (parts == null || parts.isEmpty()) {
                drawSelectionRect(g2);
                return;
            }

            computeLayout();

            int offsetX = layoutOffsetX;
            int offsetY = layoutOffsetY;
            int cellSize = layoutCellSize;

            for (core.Part p : parts) {
                int col = p.x - layoutMinX;
                int row = layoutMaxY - p.y;
                int canvasX = offsetX + col * cellSize;
                int canvasY = offsetY + row * cellSize;
                BufferedImage img = loadImage(p.id, p.skin);
                g2.drawImage(img, canvasX, canvasY, cellSize, cellSize, null);
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
                    // 不再限定网格范围，允许自由放置在任意位置
                    int canvasX = offsetX + col * cellSize;
                    int canvasY = offsetY + row * cellSize;
                    BufferedImage img = loadImage(p.id, p.skin);
                    g2.drawImage(img, canvasX, canvasY, cellSize, cellSize, null);
                }

                g2.setComposite(oldComposite);
            }

            g2.setColor(new Color(0, 200, 255, 80));
            for (core.Part p : selectedParts) {
                int col = p.x - layoutMinX;
                int row = layoutMaxY - p.y;
                int canvasX = offsetX + col * cellSize;
                int canvasY = offsetY + row * cellSize;
                g2.fillRect(canvasX, canvasY, cellSize, cellSize);
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
                int x1 = layoutOffsetX + selMinCol * layoutCellSize;
                int y1 = layoutOffsetY + selMinRow * layoutCellSize;
                int x2 = layoutOffsetX + (selMaxCol + 1) * layoutCellSize;
                int y2 = layoutOffsetY + (selMaxRow + 1) * layoutCellSize;
                int rx = Math.min(x1, x2);
                int ry = Math.min(y1, y2);
                int rw = Math.abs(x2 - x1);
                int rh = Math.abs(y2 - y1);
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

