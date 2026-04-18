import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class core {

/**
 * 静态内部类Part，用于表示游戏中的一个部件
 * 包含部件的各种属性如ID、外观、位置、朝向和翻转状态等
 */
    static class Part {
        int id;                // 部件的唯一标识符
        int skin;              // 部件的皮肤/外观标识
        int x;                 // 部件在x轴上的位置坐标
        int y;                 // 部件在y轴上的位置坐标
        int orientation;       // 部件的朝向角度
        int flipped;           // 部件是否翻转的标志（0表示未翻转，非0表示翻转）

/**
 * 构造函数，用于创建Part对象
 * @param id 部件的唯一标识符
 * @param skin 部件的皮肤/外观标识
 * @param x 部件在x轴上的位置坐标
 * @param y 部件在y轴上的位置坐标
 * @param orientation 部件的朝向角度
 * @param flipped 部件是否翻转的标志（0表示未翻转，非0表示翻转）
 */
        Part(int id, int skin, int x, int y, int orientation, int flipped) {
            this.id = id;          // 初始化部件ID
            this.skin = skin;      // 初始化部件皮肤
            this.x = x;            // 初始化x坐标
            this.y = y;            // 初始化y坐标
            this.orientation = orientation;  // 初始化部件朝向
            this.flipped = flipped;        // 初始化翻转状态
        }
    }

    // 普通朝向转换映射
    private static final Map<Integer, Integer> ORIENTATION_MAP_NORMAL = new HashMap<>();
    // 特殊朝向转换映射（id=39等）
    private static final Map<Integer, Integer> ORIENTATION_MAP_SPECIAL = new HashMap<>();

    static {
        // 普通映射：0->0,1->3,2->2,3->1,4->7,5->6,6->5,7->4
        ORIENTATION_MAP_NORMAL.put(0, 0);
        ORIENTATION_MAP_NORMAL.put(1, 3);
        ORIENTATION_MAP_NORMAL.put(2, 2);
        ORIENTATION_MAP_NORMAL.put(3, 1);
        ORIENTATION_MAP_NORMAL.put(4, 7);
        ORIENTATION_MAP_NORMAL.put(5, 6);
        ORIENTATION_MAP_NORMAL.put(6, 5);
        ORIENTATION_MAP_NORMAL.put(7, 4);

        // 特殊映射：0->2,1->1,2->0,3->3,4->5,5->4,6->7,7->6
        ORIENTATION_MAP_SPECIAL.put(0, 2);
        ORIENTATION_MAP_SPECIAL.put(1, 1);
        ORIENTATION_MAP_SPECIAL.put(2, 0);
        ORIENTATION_MAP_SPECIAL.put(3, 3);
        ORIENTATION_MAP_SPECIAL.put(4, 5);
        ORIENTATION_MAP_SPECIAL.put(5, 4);
        ORIENTATION_MAP_SPECIAL.put(6, 7);
        ORIENTATION_MAP_SPECIAL.put(7, 6);
    }

    // 需要特殊朝向转换的ID集合
    private static final Set<Integer> SPECIAL_IDS = new HashSet<>(Arrays.asList(11, 13, 15, 16, 17, 18, 39));

    // id=47 需要特殊朝向转换的skin集合
    private static final Set<Integer> SPECIAL_SKINS_FOR_ID47 = new HashSet<>(Arrays.asList(
            45, 21, 22, 23, 24, 26, 28, 30, 32, 33, 36, 37, 47, 48
    ));

    // 需要跳过朝向转换的ID和skin条件
    // 条件1: id=46, skin=3
    // 条件2: id=41
    // 条件3: id=47, skin in {0,6,7,8,9,10,11,38,39,42}
    private static final Set<Integer> SKIP_IDS = new HashSet<>(Collections.singletonList(41));
    private static final Set<Integer> SKIP_SKINS_FOR_ID47 = new HashSet<>(Arrays.asList(
            0, 6, 7, 8, 9, 10, 11, 38, 39, 42
    ));

    // 需要翻转flipped的skin集合 (id=47)
    private static final Set<Integer> FLIP_SKINS_FOR_ID47 = new HashSet<>(Arrays.asList(
            5, 25, 27, 29, 31, 35
    ));

    // 需要特殊朝向处理的skin集合
    private static final Set<Integer> SPECIAL_ORIENTATION_SKINS_FOR_ID47 = new HashSet<>(Arrays.asList(
            5, 25, 27, 29, 31, 35
    ));

    private static final Set<Integer> SPECIAL_WIRE_SKIN_FOR_ID47 = new HashSet<>(Arrays.asList(
            1, 3
    ));

    // 公共方法，供 GUI 调用
    public static void convertSave(String inputFile, String outputDir) throws IOException {
        // 1. 备份原文件到输出文件夹（可选，根据需求决定是否备份）
        backupFile(inputFile, outputDir);

        // 2. 处理转换
        List<Part> parts = readPartsFromFile(inputFile);
        if (parts.isEmpty()) {
            throw new IOException("文件为空或没有有效数据。");
        }

        mirrorXCoordinates(parts);
        for (Part p : parts) {
            handleSpecialFlipped(p);
            convertOrientation(p);
        }

        // 输出文件直接保存在输出文件夹中，文件名可自定义，例如原文件名 + "_converted"
        String originalFileName = Paths.get(inputFile).getFileName().toString();
        String outputPath = Paths.get(outputDir, originalFileName).toString();
        writePartsToFile(outputPath, parts);
        //System.out.println("处理完成！结果已保存至 " + outputPath);
    }

    public core(String inputFile) {
    //System.out.println(inputFile);
    makeshift(inputFile);
    }

    // 判断是否需要特殊朝向转换
    private static boolean needsSpecialConversion(Part p) {
        if (SPECIAL_IDS.contains(p.id)) return true;
        if (p.id == 46 && (p.skin == 0 || p.skin == 1)) return true;
        return p.id == 47 && SPECIAL_SKINS_FOR_ID47.contains(p.skin);
    }

    // 判断是否需要跳过朝向转换
    private static boolean shouldSkipConversion(Part p) {
        if (p.id == 46 && p.skin == 3) return true;
        if (p.id == 46 && p.skin == 2) return true;
        if (SKIP_IDS.contains(p.id)) return true;
        if (p.id == 47 && SKIP_SKINS_FOR_ID47.contains(p.skin)) return true;
        // 额外条件: id=44, skin 4~6
        return p.id == 44 && p.skin >= 4 && p.skin <= 6;
    }

    // 处理特殊电路部件转换
    private static int handleOrientationForId47(int orientation) {
        if (orientation == 0) return 2;
        if (orientation == 2) return 0;
        return orientation;
    }

    private static int specialWire(int orientation){
        if (orientation == 2) return 5;
        if (orientation == 5) return 2;
        if (orientation == 3) return 4;
        if (orientation == 4) return 3;
        return orientation;
    }

    // 处理flipped翻转 (id=47特殊skin)
    private static void handleSpecialFlipped(Part p) {
        if (p.id == 47 && FLIP_SKINS_FOR_ID47.contains(p.skin)) {
            p.flipped = 1 - p.flipped;
        }
    }

    // 处理朝向转换
    private static void convertOrientation(Part p) {
        if (p.id == 47 && SPECIAL_ORIENTATION_SKINS_FOR_ID47.contains(p.skin)){
            p.orientation = handleOrientationForId47(p.orientation);
            return;
        }

        if (p.id == 47 && SPECIAL_WIRE_SKIN_FOR_ID47.contains(p.skin)){
            p.orientation = specialWire(p.orientation);
            return;
        }

        if (shouldSkipConversion(p)) {
            return; // 不处理朝向
        }
        if (needsSpecialConversion(p)) {
            Integer newOri = ORIENTATION_MAP_SPECIAL.get(p.orientation);
            if (newOri != null) p.orientation = newOri;
        } else {
            Integer newOri = ORIENTATION_MAP_NORMAL.get(p.orientation);
            if (newOri != null) p.orientation = newOri;
        }
    }

    // 读取文件，解析每行
    private static List<Part> readPartsFromFile(String filename) throws IOException {
        List<Part> parts = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] fields = line.split(",");
                if (fields.length != 6) {
                    //System.err.println("跳过无效行: " + line);
                    continue;
                }
                int id = Integer.parseInt(fields[0]);
                int skin = Integer.parseInt(fields[1]);
                int x = Integer.parseInt(fields[2]);
                int y = Integer.parseInt(fields[3]);
                int orientation = Integer.parseInt(fields[4]);
                int flipped = Integer.parseInt(fields[5]);
                parts.add(new Part(id, skin, x, y, orientation, flipped));
            }
        }
        return parts;
    }

    // 镜像X坐标 (基于min_x和max_x的中间值)
    private static void mirrorXCoordinates(List<Part> parts) {
        if (parts.isEmpty()) return;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        for (Part p : parts) {
            if (p.x < minX) minX = p.x;
            if (p.x > maxX) maxX = p.x;
        }
        int sum = minX + maxX;
        for (Part p : parts) {
            p.x = sum - p.x;
        }
    }

    // 写入输出文件
    private static void writePartsToFile(String filename, List<Part> parts) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filename))) {
            for (Part p : parts) {
                bw.write(String.format("%d,%d,%d,%d,%d,%d",
                        p.id, p.skin, p.x, p.y, p.orientation, p.flipped));
                bw.newLine();
            }
        }
    }

    public static void backupFile(String sourcePath, String destDir) throws IOException {
        Path source = Paths.get(sourcePath);
        if (!Files.exists(source)) {
            throw new IOException("源文件不存在: " + sourcePath);
        }

        // 提取源文件名（不含目录）
        String fileName = source.getFileName().toString();
        String bakFileName = fileName + ".bak";
        Path dest = Paths.get(destDir, bakFileName);

        // 如果目标文件已存在，先删除
        if (Files.exists(dest)) {
            Files.delete(dest);
            //System.out.println("已删除旧文件: " + dest);
        }

        // 复制文件
        Files.copy(source, dest, StandardCopyOption.COPY_ATTRIBUTES);
        //System.out.println("备份成功！结果已保存至 " + dest);
    }

    public static void makeshift(String inputFile) {
        //String inputFile = "C:\\Users\\mx__mr\\AppData\\LocalLow\\Rovio\\新创Unity 科技版\\contraptionsB\\Level_Sandbox_06_1";
        String outputFile = "output_final.txt";

        String destDirectory = "C:\\Users\\mx__mr\\Desktop\\难道说";

        try {
            backupFile(inputFile, destDirectory);
        } catch (IOException e) {
            //System.err.println("备份失败: " + e.getMessage());
        }


        try {
            // 1. 读取
            List<Part> parts = readPartsFromFile(inputFile);
            if (parts.isEmpty()) {
                //System.err.println("文件为空或没有有效数据。");
                return;
            }

            // 2. 镜像X坐标
            mirrorXCoordinates(parts);

            // 3. 处理flipped和朝向
            for (Part p : parts) {
                handleSpecialFlipped(p);
                convertOrientation(p);
            }

            // 4. 写入
            writePartsToFile(outputFile, parts);
            System.out.println("处理完成！结果已保存至 " + outputFile);

        } catch (IOException e) {
            System.err.println("发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}