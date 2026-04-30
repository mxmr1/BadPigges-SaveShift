import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
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

        JButton btnC = new JButton("C");
        btnC.setPreferredSize(new Dimension(32, 32));
        btnC.setMaximumSize(new Dimension(32, 32));
        btnC.setFont(new Font("SansSerif", Font.BOLD, 16));

        JButton btnV = new JButton("V");
        btnV.setPreferredSize(new Dimension(32, 32));
        btnV.setMaximumSize(new Dimension(32, 32));
        btnV.setFont(new Font("SansSerif", Font.BOLD, 16));

        topPanel.add(btnC);
        topPanel.add(btnV);

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

        PartCanvas() {
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

            MouseAdapter ma = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
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
            layoutOffsetX = margin + (panelW - gridPixelW) / 2;
            layoutOffsetY = margin + (panelH - gridPixelH) / 2;
        }

        private void screenToGrid(int sx, int sy, int[] colRow) {
            double colF = (double)(sx - layoutOffsetX) / layoutCellSize;
            double rowF = (double)(sy - layoutOffsetY) / layoutCellSize;
            int col = (int)Math.floor(colF);
            int row = (int)Math.floor(rowF);
            if (col < 0) col = 0;
            if (col >= layoutCols) col = layoutCols - 1;
            if (row < 0) row = 0;
            if (row >= layoutRows) row = layoutRows - 1;
            colRow[0] = col;
            colRow[1] = row;
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

