package com.mx.bpss.model;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * 封装一个存档文件
 * 负责文件读写、部件列表管理、备份、镜像转换
 */
public class SaveFile {

    private String filePath;
    private List<Part> parts;

    /**
     * 创建一个空存档（无文件关联）
     */
    public SaveFile() {
        this.filePath = "";
        this.parts = new ArrayList<>();
    }

    /**
     * 从文件加载
     */
    public SaveFile(String filePath) throws IOException {
        this.filePath = filePath;
        this.parts = readPartsFromFile(filePath);
    }

    // ========== 文件 I/O ==========

    /**
     * 从指定文件读取部件列表
     */
    public static List<Part> readPartsFromFile(String filename) throws IOException {
        List<Part> parts = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] fields = line.split(",");
                if (fields.length != 6) continue;
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

    /**
     * 将部件列表写入文件
     */
    public static void writePartsToFile(String filename, List<Part> parts) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filename))) {
            for (Part p : parts) {
                bw.write(p.toLine());
                bw.newLine();
            }
        }
    }

    /**
     * 备份源文件到指定目录（静态方法，供外部直接调用）
     */
    public static void backupFile(String sourcePath, String destDir) throws IOException {
        Path source = Paths.get(sourcePath);
        if (!Files.exists(source)) {
            throw new IOException("源文件不存在: " + sourcePath);
        }
        String fileName = source.getFileName().toString();
        Path dest = Paths.get(destDir, fileName + ".bak");
        if (Files.exists(dest)) {
            Files.delete(dest);
        }
        Files.copy(source, dest, StandardCopyOption.COPY_ATTRIBUTES);
    }

    /**
     * 从文件加载，替换当前内容
     */
    public void load(String filePath) throws IOException {
        this.filePath = filePath;
        this.parts = readPartsFromFile(filePath);
    }

    /**
     * 保存到当前文件路径
     */
    public void save() throws IOException {
        if (filePath.isEmpty()) throw new IOException("未指定文件路径");
        writePartsToFile(filePath, parts);
    }

    /**
     * 保存到指定路径
     */
    public void saveAs(String filePath) throws IOException {
        this.filePath = filePath;
        save();
    }

    // ========== 备份 ==========

    /**
     * 备份原文件到指定目录（实例方法）
     */
    public void backupTo(String destDir) throws IOException {
        Path source = Paths.get(filePath);
        if (!Files.exists(source)) {
            throw new IOException("源文件不存在: " + filePath);
        }
        String fileName = source.getFileName().toString();
        Path dest = Paths.get(destDir, fileName + ".bak");
        if (Files.exists(dest)) {
            Files.delete(dest);
        }
        Files.copy(source, dest, StandardCopyOption.COPY_ATTRIBUTES);
    }



    // ========== 转换操作 ==========

    /**
     * 对整个存档执行镜像转换（翻转存档）
     */
    public void convertSave() {
        if (parts.isEmpty()) return;
        mirrorXCoordinates();
        for (Part p : parts) {
            p.handleSpecialFlipped();
            p.convertOrientation();
        }
    }

    /**
     * 对整个存档执行镜像转换，并保存到目标目录
     */
    public void convertAndSaveTo(String outputDir) throws IOException {
        backupTo(outputDir);
        convertSave();
        String outputPath = Paths.get(outputDir, new java.io.File(filePath).getName()).toString();
        saveAs(outputPath);
    }

    /**
     * 对指定部件列表执行完整镜像（X坐标镜像 + 朝向/翻转转换）
     */
    public static void mirrorPartsFull(List<Part> targetParts) {
        mirrorXCoordinates(targetParts);
        for (Part p : targetParts) {
            p.handleSpecialFlipped();
            p.convertOrientation();
        }
    }

    /**
     * 对指定部件列表仅执行X坐标镜像
     */
    public static void mirrorPartsXOnly(List<Part> targetParts) {
        mirrorXCoordinates(targetParts);
    }

    // ========== 内部辅助方法 ==========

    /**
     * 镜像当前 parts 的 X 坐标
     */
    private void mirrorXCoordinates() {
        mirrorXCoordinates(this.parts);
    }

    /**
     * 镜像指定列表的 X 坐标（基于 min_x 和 max_x 的中间值）
     */
    private static void mirrorXCoordinates(List<Part> targetParts) {
        if (targetParts.isEmpty()) return;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        for (Part p : targetParts) {
            if (p.x < minX) minX = p.x;
            if (p.x > maxX) maxX = p.x;
        }
        int sum = minX + maxX;
        for (Part p : targetParts) {
            p.x = sum - p.x;
        }
    }

    // ========== getter/setter ==========

    public String getFilePath() { return filePath; }

    public List<Part> getParts() { return parts; }

    public void setParts(List<Part> parts) { this.parts = parts; }

    public boolean isEmpty() { return parts.isEmpty(); }

    public int size() { return parts.size(); }

    public String getFileName() {
        return filePath.isEmpty() ? "无文件" : new java.io.File(filePath).getName();
    }
}