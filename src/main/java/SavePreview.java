import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * 存档部件预览窗口
 * 根据 Part 的 id、skin、坐标在画布上绘制对应的贴图（或占位色块）
 */
public class SavePreview {
    private JFrame frame;
    private PartCanvas canvas;
    private List<core.Part> parts;

    // 贴图缓存：key = skin + "_" + id
    private static final Map<String, BufferedImage> imageCache = new HashMap<>();
    private static final String IMAGE_DIR = "src/main/resources/";

    public SavePreview(List<core.Part> parts) {
        this.parts = parts;
        frame = new JFrame("存档预览");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        canvas = new PartCanvas();
        frame.add(canvas);
        frame.setSize(800, 600);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    /**
     * 更新显示的部件列表（转换后调用）
     */
    public void updateParts(List<core.Part> newParts) {
        this.parts = newParts;
        canvas.repaint();
    }

    /**
     * 获取当前部件列表
     */
    public List<core.Part> getParts() {
        return parts;
    }

    /**
     * 关闭窗口
     */
    public void dispose() {
        frame.dispose();
    }

    // ---------- 贴图加载 ----------
    private static BufferedImage loadImage(int id, int skin) {
        String key = id + "_" + skin;
        BufferedImage img = imageCache.get(key);
        if (img != null) return img;

        String filename = key + ".png";
        File imgFile = new File(IMAGE_DIR + filename);
        if (imgFile.exists()) {
            try {
                img = ImageIO.read(imgFile);
            } catch (IOException e) {
                img = null;
            }
        }
        if (img == null) {
            // 创建默认占位图片（彩色矩形+文字）
            int size = 48;
            img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            g2.setColor(getColorForId(id));
            g2.fillRect(0, 0, size, size);
            g2.setColor(Color.WHITE);
            g2.drawString(key, 4, size / 2 + 5);
            g2.dispose();
        }
        imageCache.put(key, img);
        return img;
    }

    // 为不同id分配不同颜色（便于占位区分）
    private static Color getColorForId(int id) {
        Color[] colors = {
                Color.RED, Color.BLUE, Color.GREEN, Color.ORANGE, Color.CYAN,
                Color.MAGENTA, Color.YELLOW, Color.PINK, Color.LIGHT_GRAY, Color.DARK_GRAY
        };
        return colors[id % colors.length];
    }

    // ---------- 画布面板 ----------
    class PartCanvas extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (parts == null || parts.isEmpty()) return;

            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            // 计算所有部件的边界，用于缩放
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
            int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
            for (core.Part p : parts) {
                if (p.x < minX) minX = p.x;
                if (p.x > maxX) maxX = p.x;
                if (p.y < minY) minY = p.y;
                if (p.y > maxY) maxY = p.y;
            }

            int margin = 40;
            int drawWidth = getWidth() - 2 * margin;
            int drawHeight = getHeight() - 2 * margin;

            // 计算缩放比例，保持宽高比
            double scaleX = (double) drawWidth / (maxX - minX + 1);
            double scaleY = (double) drawHeight / (maxY - minY + 1);
            double scale = Math.min(scaleX, scaleY);
            if (scale < 0.1) scale = 0.1; // 防止缩放太小

            // 绘制每个部件
            for (core.Part p : parts) {
                // 坐标映射到画布
                int canvasX = margin + (int) ((p.x - minX) * scale);
                int canvasY = margin + (int) ((p.y - minY) * scale);

                BufferedImage img = loadImage(p.id, p.skin);
                int imgW = img.getWidth();
                int imgH = img.getHeight();

                // 根据缩放调整图片显示大小（简单缩放为固定大小，或按比例缩放）
                // 此处我们让图片保持原大小（或缩放至30x30像素左右）
                int displaySize = Math.max(16, (int) (scale * 8)); // 根据比例决定显示大小
                if (displaySize > 48) displaySize = 48;
                // 或者直接使用图片原始大小并缩放适应
                double imgScale = Math.min((double) displaySize / imgW, (double) displaySize / imgH);
                int drawImgW = (int) (imgW * imgScale);
                int drawImgH = (int) (imgH * imgScale);

                // 绘制图片（居中于部件坐标点）
                g2.drawImage(img, canvasX - drawImgW / 2, canvasY - drawImgH / 2,
                        drawImgW, drawImgH, null);
            }
        }
    }
}