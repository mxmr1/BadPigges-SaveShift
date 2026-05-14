package com.mx.bpss.model;

import java.util.*;

/**
 * 表示游戏中的一个部件
 * 包含部件的各种属性如ID、外观、位置、朝向和翻转状态
 * 封装自身的朝向转换、翻转处理和坐标镜像行为
 */
public class Part {

    public int id;                // 部件的唯一标识符
    public int skin;              // 部件的皮肤/外观标识
    public int x;                 // 部件在x轴上的位置坐标
    public int y;                 // 部件在y轴上的位置坐标
    public int orientation;       // 部件的朝向角度
    public int flipped;           // 部件是否翻转的标志（0表示未翻转，非0表示翻转）

    // ========== 静态映射表 ==========
    private static final Map<Integer, Integer> ORIENTATION_MAP_NORMAL = new HashMap<>();
    private static final Map<Integer, Integer> ORIENTATION_MAP_SPECIAL = new HashMap<>();

    // 需要特殊朝向转换的ID集合
    private static final Set<Integer> SPECIAL_IDS = new HashSet<>(Arrays.asList(11, 13, 15, 16, 17, 18, 39));

    // id=47 需要特殊朝向转换的skin集合
    private static final Set<Integer> SPECIAL_SKINS_FOR_ID47 = new HashSet<>(Arrays.asList(
            45, 21, 22, 23, 24, 26, 28, 30, 32, 33, 36, 37, 47, 48
    ));

    // 需要跳过朝向转换的ID和skin条件
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

    /**
     * 构造函数
     */
    public Part(int id, int skin, int x, int y, int orientation, int flipped) {
        this.id = id;
        this.skin = skin;
        this.x = x;
        this.y = y;
        this.orientation = orientation;
        this.flipped = flipped;
    }

    /**
     * 深拷贝构造
     */
    public Part(Part other) {
        this(other.id, other.skin, other.x, other.y, other.orientation, other.flipped);
    }

    /**
     * 执行完整的镜像转换（X坐标镜像 + 朝向/翻转转换）
     * 与 "翻转存档" 按钮一致
     */
    public void mirrorFull() {
        // 朝向/翻转转换由外部方法 handleSpecialFlipped 和 convertOrientation 处理
        // 但这里封装为一次性调用
        handleSpecialFlipped();
        convertOrientation();
    }

    /**
     * 将部件序列化为一行文本
     */
    public String toLine() {
        return String.format("%d,%d,%d,%d,%d,%d", id, skin, x, y, orientation, flipped);
    }

    // ========== 实例方法：判断逻辑 ==========

    private boolean needsSpecialConversion() {
        if (SPECIAL_IDS.contains(id)) return true;
        if (id == 46 && (skin == 0 || skin == 1)) return true;
        return id == 47 && SPECIAL_SKINS_FOR_ID47.contains(skin);
    }

    private boolean shouldSkipConversion() {
        if (id == 46 && skin == 3) return true;
        if (id == 46 && skin == 2) return true;
        if (SKIP_IDS.contains(id)) return true;
        if (id == 47 && SKIP_SKINS_FOR_ID47.contains(skin)) return true;
        return id == 44 && skin >= 4 && skin <= 6;
    }

    private int handleOrientationForId47() {
        if (orientation == 0) return 2;
        if (orientation == 2) return 0;
        return orientation;
    }

    private int specialWire() {
        if (orientation == 2) return 5;
        if (orientation == 5) return 2;
        if (orientation == 3) return 4;
        if (orientation == 4) return 3;
        return orientation;
    }

    // ========== 实例方法：转换行为 ==========

    /**
     * 处理 flipped 翻转（id=47 特殊skin）
     */
    public void handleSpecialFlipped() {
        if (id == 47 && FLIP_SKINS_FOR_ID47.contains(skin)) {
            flipped = 1 - flipped;
        }
    }

    /**
     * 处理朝向转换
     */
    public void convertOrientation() {
        if (id == 47 && SPECIAL_ORIENTATION_SKINS_FOR_ID47.contains(skin)) {
            orientation = handleOrientationForId47();
            return;
        }

        if (id == 47 && SPECIAL_WIRE_SKIN_FOR_ID47.contains(skin)) {
            orientation = specialWire();
            return;
        }

        if (shouldSkipConversion()) {
            return;
        }
        if (needsSpecialConversion()) {
            Integer newOri = ORIENTATION_MAP_SPECIAL.get(orientation);
            if (newOri != null) orientation = newOri;
        } else {
            Integer newOri = ORIENTATION_MAP_NORMAL.get(orientation);
            if (newOri != null) orientation = newOri;
        }
    }
}
