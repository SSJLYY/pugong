package com.gongpu.common;

/**
 * 用户自由文本清洗（第二十批·轮10）。
 * <p>墓志铭经免登录的世界墓园全服可见，宠物名/存档名进他人视图——
 * 入口统一去控制字符（换行/\\x00 等），防刷屏与渲染异常，纵深防御不依赖前端转义。</p>
 */
public final class Texts {

    private Texts() {
    }

    private static final java.util.regex.Pattern CONTROL = java.util.regex.Pattern.compile("\\p{Cntrl}+");

    /** 去掉全部控制字符（含换行/回车/制表符/\\x00-\\x1F/\\x7F）；null 直通。 */
    public static String stripControl(String s) {
        return s == null ? null : CONTROL.matcher(s).replaceAll("");
    }
}
