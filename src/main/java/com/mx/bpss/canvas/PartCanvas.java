package com.mx.bpss.canvas;

import com.mx.bpss.core;
import javax.imageio.ImageIO;
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
import java.util.function.Consumer;

/**
 * 存档部件画布面板
 * 负责视觉渲染、鼠标键盘交互、文件加载存储
 * 整合 SelectionManager、ClipboardManager、UndoRedoManager、ZoomController、PanController
 */
public class PartCanvas extends JPanel {

    private List<core.Part> parts = new ArrayList<>();
    private String currentFilePath = "";
    private String backupDirPath = "";
    private Consumer<String> titleUpdater;
    private Runnable fileChangedCallback;

    // 布局变量
    private int layoutMinX, layoutMinY, layoutMaxY;
    private double layoutCellSize;
    private double layoutOffsetX, layoutOffsetY;
    private int layoutCols, layoutRows;

    // 平移偏移
    private double panX = 0.0, panY = 0.0;
    private double baseOffsetX = 0.0, baseOffsetY = 0.0;

    // 各管理器
    private SelectionManager selectionMgr;
    private ClipboardManager clipboardMgr;
    private UndoRedoManager undoRedoMgr;
    private ZoomController zoomCtrl;
    private PanController panCtrl;

    // 统一更新定时器（驱动缩放惯性和 WASD 平移）
    private javax.swing.Timer mainLoopTimer;

    // 图片缓存
    private static final Map<String, BufferedImage> imageCache = new HashMap<>();

    // ========== 跨视图全局剪贴板（静态）==========
    private static List<core.Part> globalClipboardParts = null;
    private static int globalClipAnchorX = 0;
    private static int globalClipAnchorY = 0;

    public static List<core.Part> getGlobalClipboard() { return globalClipboardParts; }
    public static int getGlobalClipAnchorX() { return globalClipAnchorX; }
    public static int getGlobalClipAnchorY() { return globalClipAnchorY; }

    // ========== 跨视图预览（用于在目标视图中跟随鼠标显示）==========
    private List<core.Part> crossViewPreviewParts = null;

    public PartCanvas(Consumer<String> titleUpdater, Runnable fileChangedCallback) {
        this.titleUpdater = titleUpdater;
        this.fileChangedCallback = fileChangedCallback;
        this.selectionMgr = new SelectionManager();
        this.clipboardMgr = new ClipboardManager();
        this.undoRedoMgr = new UndoRedoManager();
        this.zoomCtrl = new ZoomController();
        this.panCtrl = new PanController();

        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        setupTransferHandler();
        setupMouseListeners();
        setupKeyBindings();

        // 统一 16ms 定时器驱动缩放惯性和 WASD 平移
        mainLoopTimer = new javax.swing.Timer(16, e -> runUpdateLoop());
        mainLoopTimer.setRepeats(true);
        mainLoopTimer.start();
    }

    // ========== 文件加载与存储 ==========

    public boolean loadFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) return false;
        try {
            if (!Files.exists(Paths.get(filePath))) {
                showError("文件不存在: " + filePath);
                return false;
            }
            if (hasAnyExtension(filePath)) {
                showError("文件格式错误：不允许使用带后缀的文件名！");
                return false;
            }
            if (!isValidFormat(filePath)) {
                showError("文件内容格式错误！\n要求：每行6个逗号分隔的整数");
                return false;
            }

            List<core.Part> newParts = core.readPartsFromFile(filePath);
            if (newParts == null || newParts.isEmpty()) {
                showError("文件为空或没有有效数据！");
                return false;
            }

            currentFilePath = filePath;
            backupDirPath = new File(filePath).getParent();
            parts.clear();
            parts.addAll(newParts);
            autoFitView();
            updateFrameTitle();
            if (fileChangedCallback != null) fileChangedCallback.run();
            repaint();
            return true;
        } catch (IOException ex) {
            showError("读取文件失败: " + ex.getMessage());
            return false;
        }
    }

    public void flipSave() {
        if (currentFilePath.isEmpty()) {
            showWarning("请先拖入存档文件！");
            return;
        }
        String backupDir = backupDirPath.isEmpty() ? new File(currentFilePath).getParent() : backupDirPath;
        try {
            core.convertSave(currentFilePath, backupDir);
            String outputPath = new File(backupDir, new File(currentFilePath).getName()).getPath();
            List<core.Part> convertedParts = core.readPartsFromFile(outputPath);
            if (!convertedParts.isEmpty()) {
                parts.clear();
                parts.addAll(convertedParts);
                updateFrameTitle();
                autoFitView();
                repaint();
            }
            showInfo("存档方向转换完成！\n备份位置: " + backupDir);
            if (fileChangedCallback != null) fileChangedCallback.run();
        } catch (IOException ex) {
            showError("转换失败：" + ex.getMessage());
        }
    }

    public void saveCurrent() {
        if (currentFilePath.isEmpty()) {
            showWarning("请先拖入存档文件！");
            return;
        }
        String backupDir = backupDirPath.isEmpty() ? new File(currentFilePath).getParent() : backupDirPath;
        try {
            core.backupFile(currentFilePath, backupDir);
            core.writePartsToFile(currentFilePath, parts);
            showInfo("备份并保存完成！\n备份位置: " + backupDir);
            if (fileChangedCallback != null) fileChangedCallback.run();
        } catch (IOException ex) {
            showError("保存失败：" + ex.getMessage());
        }
    }

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
        int margin = 40;
        int panelW = getWidth() - 2 * margin;
        int panelH = getHeight() - 2 * margin;

        double newScale = 1.0;
        if (panelW > 0 && panelH > 0 && cols > 0 && rows > 0) {
            double scaleByWidth = (double) panelW / (cols * ZoomController.BASE_UNIT_SIZE);
            double scaleByHeight = (double) panelH / (rows * ZoomController.BASE_UNIT_SIZE);
            newScale = Math.min(scaleByWidth, scaleByHeight);
        }
        if (newScale < 0.001) newScale = 0.001;
        if (newScale > 100.0) newScale = 100.0;

        zoomCtrl.setTargetScale(newScale);

        layoutCellSize = Math.max(1, ZoomController.BASE_UNIT_SIZE * newScale);
        double totalPixelW = cols * layoutCellSize;
        double totalPixelH = rows * layoutCellSize;
        baseOffsetX = margin + (panelW - totalPixelW) / 2.0;
        baseOffsetY = margin + (panelH - totalPixelH) / 2.0;
        panX = 0;
        panY = 0;

        selectionMgr.clearSelection();
        clipboardMgr.cancel();
        undoRedoMgr.clear();
    }

    public void resetView() {
        zoomCtrl.reset();
        panCtrl.reset();
        panX = 0.0; panY = 0.0;
        baseOffsetX = 0; baseOffsetY = 0;
        selectionMgr.clearSelection();
        clipboardMgr.cancel();
    }

    public List<core.Part> getParts() { return parts; }

    // ========== 辅助方法 ==========

    private void updateFrameTitle() {
        String fileName = currentFilePath.isEmpty() ? "无文件" : new File(currentFilePath).getName();
        if (titleUpdater != null) {
            titleUpdater.accept("存档预览 - " + fileName + " (" + parts.size() + " 个部件)");
        }
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "错误", JOptionPane.ERROR_MESSAGE);
    }

    private void showWarning(String msg) {
        JOptionPane.showMessageDialog(this, msg, "提示", JOptionPane.WARNING_MESSAGE);
    }

    private void showInfo(String msg) {
        JOptionPane.showMessageDialog(this, msg, "成功", JOptionPane.INFORMATION_MESSAGE);
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
                    try { Integer.parseInt(parts[i].trim()); }
                    catch (NumberFormatException e) { return false; }
                }
                lineNum++;
            }
            return lineNum > 0;
        } catch (IOException e) {
            return false;
        }
    }

    // ========== 绘制 ==========

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

        // 绘制所有部件
        for (core.Part p : parts) {
            int col = p.x - layoutMinX;
            int row = layoutMaxY - p.y;
            int x0 = (int) Math.round(offsetX + col * cellSize);
            int y0 = (int) Math.round(offsetY + row * cellSize);
            int x1 = (int) Math.round(offsetX + (col + 1) * cellSize);
            int y1 = (int) Math.round(offsetY + (row + 1) * cellSize);
            int drawW = x1 - x0;
            int drawH = y1 - y0;
            if (drawW < 1) drawW = 1;
            if (drawH < 1) drawH = 1;
            BufferedImage img = loadImage(p.id, p.skin);
            g2.drawImage(img, x0, y0, drawW, drawH, null);
        }

        // 绘制剪贴板半透明预览
        if (clipboardMgr.isActive()) {
            Composite oldComposite = g2.getComposite();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f));

            for (core.Part p : clipboardMgr.getPreviewParts()) {
                int col = p.x - layoutMinX;
                int row = layoutMaxY - p.y;
                int x0 = (int) Math.round(offsetX + col * cellSize);
                int y0 = (int) Math.round(offsetY + row * cellSize);
                int x1 = (int) Math.round(offsetX + (col + 1) * cellSize);
                int y1 = (int) Math.round(offsetY + (row + 1) * cellSize);
                int drawW = x1 - x0;
                int drawH = y1 - y0;
                if (drawW < 1) drawW = 1;
                if (drawH < 1) drawH = 1;
                BufferedImage img = loadImage(p.id, p.skin);
                g2.drawImage(img, x0, y0, drawW, drawH, null);
            }

            g2.setComposite(oldComposite);
        }

        // 绘制跨视图预览（半透明）
        if (crossViewPreviewParts != null && !crossViewPreviewParts.isEmpty()) {
            Composite oldComposite = g2.getComposite();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f));

            for (core.Part p : crossViewPreviewParts) {
                int col = p.x - layoutMinX;
                int row = layoutMaxY - p.y;
                int x0 = (int) Math.round(offsetX + col * cellSize);
                int y0 = (int) Math.round(offsetY + row * cellSize);
                int x1 = (int) Math.round(offsetX + (col + 1) * cellSize);
                int y1 = (int) Math.round(offsetY + (row + 1) * cellSize);
                int drawW = x1 - x0;
                int drawH = y1 - y0;
                if (drawW < 1) drawW = 1;
                if (drawH < 1) drawH = 1;
                BufferedImage img = loadImage(p.id, p.skin);
                g2.drawImage(img, x0, y0, drawW, drawH, null);
            }

            g2.setComposite(oldComposite);
        }

        // 绘制选中部件高亮
        g2.setColor(new Color(0, 200, 255, 80));
        for (core.Part p : selectionMgr.getSelectedParts()) {
            int col = p.x - layoutMinX;
            int row = layoutMaxY - p.y;
            int x0 = (int) Math.round(offsetX + col * cellSize);
            int y0 = (int) Math.round(offsetY + row * cellSize);
            int x1 = (int) Math.round(offsetX + (col + 1) * cellSize);
            int y1 = (int) Math.round(offsetY + (row + 1) * cellSize);
            int drawW = x1 - x0;
            int drawH = y1 - y0;
            if (drawW < 1) drawW = 1;
            if (drawH < 1) drawH = 1;
            g2.fillRect(x0, y0, drawW, drawH);
        }

        drawSelectionRect(g2);
    }

    private void drawSelectionRect(Graphics2D g2) {
        if (selectionMgr.isDragging() && selectionMgr.getDragStart() != null && selectionMgr.getDragEnd() != null
                && !selectionMgr.getDragStart().equals(selectionMgr.getDragEnd())) {
            int x = Math.min(selectionMgr.getDragStart().x, selectionMgr.getDragEnd().x);
            int y = Math.min(selectionMgr.getDragStart().y, selectionMgr.getDragEnd().y);
            int w = Math.abs(selectionMgr.getDragEnd().x - selectionMgr.getDragStart().x);
            int h = Math.abs(selectionMgr.getDragEnd().y - selectionMgr.getDragStart().y);
            if (w > 0 && h > 0) {
                g2.setColor(new Color(0, 120, 255, 50));
                g2.fillRect(x, y, w, h);
                g2.setColor(new Color(0, 120, 255));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRect(x, y, w, h);
            }
            return;
        }

        if (selectionMgr.hasSelection()) {
            int x0 = (int) Math.round(layoutOffsetX + selectionMgr.getSelMinCol() * layoutCellSize);
            int y0 = (int) Math.round(layoutOffsetY + selectionMgr.getSelMinRow() * layoutCellSize);
            int x1 = (int) Math.round(layoutOffsetX + (selectionMgr.getSelMaxCol() + 1) * layoutCellSize);
            int y1 = (int) Math.round(layoutOffsetY + (selectionMgr.getSelMaxRow() + 1) * layoutCellSize);
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

    // ========== 图片加载 ==========

    private static BufferedImage loadImage(int id, int skin) {
        String key = id + "_" + skin;
        BufferedImage img = imageCache.get(key);
        if (img != null) return img;

        String filename = key + ".png";
        URL url = PartCanvas.class.getClassLoader().getResource(filename);
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

    // ========== 布局计算 ==========

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
        layoutCellSize = Math.max(1, ZoomController.BASE_UNIT_SIZE * zoomCtrl.getScale());
        layoutOffsetX = baseOffsetX + panX;
        layoutOffsetY = baseOffsetY + panY;
    }

    private int[] screenToGrid(int sx, int sy) {
        double colF = (double)(sx - layoutOffsetX) / layoutCellSize;
        double rowF = (double)(sy - layoutOffsetY) / layoutCellSize;
        return new int[]{(int)Math.floor(colF), (int)Math.floor(rowF)};
    }

    // ========== 事件设置 ==========

    private void setupTransferHandler() {
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
    }

    private void setupMouseListeners() {
        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow(); // 点击时主动请求焦点
                // 打断惯性缩放
                zoomCtrl.stop();

                // ===== 中键：切换视图（不做注入，只请求焦点） =====
                if (SwingUtilities.isMiddleMouseButton(e)) {
                    requestFocusInWindow();
                    return;
                }

                // ===== 左键：优先处理全局剪贴板粘贴（使用绝对坐标） =====
                if (SwingUtilities.isLeftMouseButton(e)) {
                    if (globalClipboardParts != null && !globalClipboardParts.isEmpty()) {
                        computeLayout();
                        int[] grid = screenToGrid(e.getX(), e.getY());
                        // 鼠标位置对应的绝对坐标
                        int targetAbsX = layoutMinX + grid[0];
                        int targetAbsY = layoutMaxY - grid[1];
                        undoRedoMgr.saveState(parts);
                        // 按绝对坐标偏移粘贴
                        for (core.Part p : globalClipboardParts) {
                            int offsetX = p.x - globalClipAnchorX;
                            int offsetY = p.y - globalClipAnchorY;
                            parts.add(new core.Part(p.id, p.skin,
                                    targetAbsX + offsetX,
                                    targetAbsY + offsetY,
                                    p.orientation, p.flipped));
                        }
                        crossViewPreviewParts = null;
                        if (clipboardMgr.isActive()) clipboardMgr.cancel();
                        updateFrameTitle();
                        repaint();
                        return;
                    }

                    // 本地粘贴（同视图 clipboardMgr）
                    if (clipboardMgr.isActive()) {
                        computeLayout();
                        int[] grid = screenToGrid(e.getX(), e.getY());
                        undoRedoMgr.saveState(parts);
                        clipboardMgr.paste(parts, grid[0], grid[1]);
                        clipboardMgr.cancel();
                        updateFrameTitle();
                        repaint();
                        return;
                    }
                }

                // ===== 右键：取消跨视图预览 / 取消本地剪贴板 / 开始框选 =====
                if (SwingUtilities.isRightMouseButton(e)) {
                    // 如果存在跨视图预览，取消预览
                    if (crossViewPreviewParts != null) {
                        crossViewPreviewParts = null;
                        repaint();
                        return;
                    }
                    // 如果 clipboardMgr 处于活跃状态，取消它并清除选择
                    if (clipboardMgr.isActive()) {
                        clipboardMgr.cancel();
                        selectionMgr.clearSelection();
                        repaint();
                        return;
                    }
                    // 否则开始框选
                    selectionMgr.startDrag(e.getPoint());
                    repaint();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (selectionMgr.isDragging() && SwingUtilities.isRightMouseButton(e)) {
                    selectionMgr.updateDrag(e.getPoint());
                    repaint();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                // ===== 跨视图预览：直接用绝对坐标计算跟随鼠标的预览部件 =====
                if (globalClipboardParts != null && !globalClipboardParts.isEmpty() && !clipboardMgr.isActive()) {
                    computeLayout();
                    int[] grid = screenToGrid(e.getX(), e.getY());
                    int targetAbsX = layoutMinX + grid[0];
                    int targetAbsY = layoutMaxY - grid[1];
                    crossViewPreviewParts = new ArrayList<>();
                    for (core.Part p : globalClipboardParts) {
                        int offsetX = p.x - globalClipAnchorX;
                        int offsetY = p.y - globalClipAnchorY;
                        crossViewPreviewParts.add(new core.Part(p.id, p.skin,
                                targetAbsX + offsetX,
                                targetAbsY + offsetY,
                                p.orientation, p.flipped));
                    }
                    repaint();
                } else {
                    // 如果没有全局剪贴板，清除跨视图预览
                    if (crossViewPreviewParts != null) {
                        crossViewPreviewParts = null;
                        repaint();
                    }
                }

                if (clipboardMgr.isActive()) {
                    computeLayout();
                    int[] grid = screenToGrid(e.getX(), e.getY());
                    clipboardMgr.setMouseGrid(grid[0], grid[1]);
                    repaint();
                }
                // 惯性放大时更新缩放原点
                if (zoomCtrl.isZoomingIn() && panCtrl.isMoving() == false) {
                    // 由于统一 Timer 持续运行，鼠标移动时更新原点的世界坐标
                    double currentCellSize = Math.max(1, ZoomController.BASE_UNIT_SIZE * zoomCtrl.getScale());
                    double worldX = (e.getX() - (baseOffsetX + panX)) / currentCellSize;
                    double worldY = (e.getY() - (baseOffsetY + panY)) / currentCellSize;
                    zoomCtrl.updateZoomOrigin(e.getX(), e.getY(), panX, panY, baseOffsetX, baseOffsetY);
                    panX = (int) Math.round(e.getX() - worldX * currentCellSize - baseOffsetX);
                    panY = (int) Math.round(e.getY() - worldY * currentCellSize - baseOffsetY);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (selectionMgr.isDragging() && SwingUtilities.isRightMouseButton(e)) {
                    computeLayout();
                    SelectionManager.LayoutInfo layoutInfo = new SelectionManager.LayoutInfo(
                        layoutMinX, layoutMaxY, layoutOffsetX, layoutOffsetY, layoutCellSize
                    );
                    selectionMgr.endDrag(parts, layoutInfo);
                    repaint();
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                zoomCtrl.onWheel(e, getWidth(), getHeight());
            }
        };

        addMouseListener(ma);
        addMouseMotionListener(ma);
        addMouseWheelListener(ma);
    }

    private void setupKeyBindings() {
        InputMap inputMap = getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = getActionMap();

        // X 键镜像剪贴板
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, 0), "mirrorClipboard");
        actionMap.put("mirrorClipboard", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (clipboardMgr.isActive()) {
                    clipboardMgr.mirror();
                    repaint();
                }
            }
        });

        // WASD
        setupPanKey(inputMap, actionMap, KeyEvent.VK_W, 0, -1);
        setupPanKey(inputMap, actionMap, KeyEvent.VK_S, 0, 1);
        setupPanKey(inputMap, actionMap, KeyEvent.VK_A, -1, 0);
        setupPanKey(inputMap, actionMap, KeyEvent.VK_D, 1, 0);

        // 撤销 Ctrl+Z
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "undo");
        actionMap.put("undo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (undoRedoMgr.undo(parts)) {
                    updateFrameTitle();
                    repaint();
                }
            }
        });

        // 重做 Ctrl+Y
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "redo");
        actionMap.put("redo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (undoRedoMgr.redo(parts)) {
                    updateFrameTitle();
                    repaint();
                }
            }
        });

        // 复制 Ctrl+C
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "copy");
        actionMap.put("copy", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                List<core.Part> selected = selectionMgr.getSelectedParts();
                if (selected != null && !selected.isEmpty()) {
                    computeLayout();
                    clipboardMgr.startClipboard(selected, layoutMinX, layoutMaxY);
                    // 设置全局剪贴板：记录选中部件中最左上角部件的绝对坐标作为锚点
                    int absMinX = Integer.MAX_VALUE, absMaxY = Integer.MIN_VALUE;
                    for (core.Part p : selected) {
                        if (p.x < absMinX) absMinX = p.x;
                        if (p.y > absMaxY) absMaxY = p.y;
                    }
                    globalClipboardParts = new ArrayList<>(selected);
                    globalClipAnchorX = absMinX;
                    globalClipAnchorY = absMaxY;
                    selectionMgr.clearSelection();
                    repaint();
                }
            }
        });

        // 删除 Delete
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteSelected");
        actionMap.put("deleteSelected", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                List<core.Part> selected = selectionMgr.getSelectedParts();
                if (selected == null || selected.isEmpty()) return;
                undoRedoMgr.saveState(parts);
                parts.removeAll(selected);
                if (clipboardMgr.isActive()) {
                    clipboardMgr.cancel();
                }
                selectionMgr.clearSelection();
                updateFrameTitle();
                repaint();
            }
        });
    }

    private void setupPanKey(InputMap inputMap, ActionMap actionMap, int keyCode, int dx, int dy) {
        String pressKey = "pan" + keyCode + "Press";
        String releaseKey = "pan" + keyCode + "Release";

        inputMap.put(KeyStroke.getKeyStroke(keyCode, 0, false), pressKey);
        actionMap.put(pressKey, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (dx != 0) panCtrl.setDirectionX(dx);
                if (dy != 0) panCtrl.setDirectionY(dy);
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(keyCode, 0, true), releaseKey);
        actionMap.put(releaseKey, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (dx != 0) panCtrl.setDirectionX(0);
                if (dy != 0) panCtrl.setDirectionY(0);
            }
        });
    }

    /**
     * 统一更新循环：每 16ms 执行一次
     * 处理缩放惯性和 WASD 平移
     */
    private void runUpdateLoop() {
        boolean needsRepaint = false;

        // 处理缩放惯性
        ZoomController.MutablePan pan = new ZoomController.MutablePan(panX, panY);
        if (zoomCtrl.inertiaStep(baseOffsetX, baseOffsetY, pan)) {
            panX = pan.x;
            panY = pan.y;
            needsRepaint = true;
        }

        // 处理 WASD 平移
        if (panCtrl.isMoving()) {
            int deltaX = panCtrl.computeDeltaX(getWidth());
            int deltaY = panCtrl.computeDeltaY(getHeight());
            panX -= deltaX;//此处这么写是正确的
            panY -= deltaY;//此处这么写是正确的
            needsRepaint = true;
        }

        if (needsRepaint) {
            repaint();
        }
    }
}