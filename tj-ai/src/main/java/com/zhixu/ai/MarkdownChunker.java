package com.zhixu.ai;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 切块器。
 * 规则：
 *  - 一级标题 # 不切，仅记到 metadata
 *  - 二级标题 ## 作为大段边界
 *  - 三级标题 ### 作为小块边界
 *  - 代码块 ``` 整体保留，不在内部再切
 *  - 超长段落（> chunkSize）按窗口 + 重叠滑动切
 *  - TXT 无标题时按空行分段落，sectionPath 留空，heading = 文件名（由调用方传入）
 */
public final class MarkdownChunker {

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*#*\\s*$");
    private static final Pattern FENCE = Pattern.compile("^```");

    private MarkdownChunker() {}

    public static List<RawChunk> chunk(String content, String docName, int chunkSize, int chunkOverlap) {
        List<RawChunk> out = new ArrayList<>();
        if (content == null || content.isEmpty()) return out;

        List<String> lines = Arrays.asList(content.split("\\n", -1));
        List<String> sectionPath = new ArrayList<>();
        String currentHeading = null;
        StringBuilder buf = new StringBuilder();
        boolean inFence = false;
        int fenceStartLine = -1;

        // 简易的 flush：把当前 buf 切成多个 RawChunk（按需滑动窗口）
        // 注意：lambda 里不能直接修改 sectionPath/currentHeading，因此用 array holder
        final List<String>[] pathHolder = new List[]{ sectionPath };
        final String[] headingHolder = new String[]{ currentHeading };
        final StringBuilder[] bufHolder = new StringBuilder[]{ buf };
        final boolean[] fenceHolder = new boolean[]{ inFence };
        final int[] fenceStartHolder = new int[]{ fenceStartLine };
        Runnable flush = () -> {
            StringBuilder b = bufHolder[0];
            if (b.length() == 0) return;
            String text = b.toString();
            String prefix = "";
            if (pathHolder[0] != null && !pathHolder[0].isEmpty()) prefix = String.join(" > ", pathHolder[0]) + "\n";
            if (headingHolder[0] != null && !headingHolder[0].isBlank()) prefix += headingHolder[0] + "\n";
            String fullText = prefix + text;
            for (String piece : window(fullText, chunkSize, chunkOverlap)) {
                out.add(new RawChunk(piece, headingHolder[0], pathHolder[0] == null ? new ArrayList<>() : new ArrayList<>(pathHolder[0])));
            }
            b.setLength(0);
        };

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (FENCE.matcher(line.trim()).matches()) {
                inFence = !inFence;
                if (!inFence) fenceStartLine = -1; else fenceStartLine = i;
                buf.append(line).append('\n');
                continue;
            }
            if (inFence) {
                buf.append(line).append('\n');
                continue;
            }
            Matcher m = HEADING.matcher(line);
            if (m.matches()) {
                int level = m.group(1).length();
                String title = m.group(2).trim();
                if (level == 1) {
                    // 一级标题不切，仅作为 section 顶部（用 docName 替代）
                    flush.run();
                    sectionPath.clear();
                    if (title != null && !title.isBlank()) sectionPath.add(title);
                    currentHeading = null;
                    continue;
                } else if (level == 2) {
                    flush.run();
                    // 重置 path 到当前 H2
                    sectionPath.clear();
                    sectionPath.add(title);
                    currentHeading = title;
                    continue;
                } else {
                    // H3+ 视为块边界
                    flush.run();
                    // 保留上层 sectionPath，追加当前标题
                    while (sectionPath.size() > 1) sectionPath.remove(sectionPath.size() - 1);
                    if (sectionPath.isEmpty()) sectionPath.add(docName);
                    sectionPath.add(title);
                    currentHeading = title;
                    continue;
                }
            }
            buf.append(line).append('\n');
        }
        flush.run();
        return out;
    }

    /** 把一段长文本切成 [chunkSize, chunkOverlap] 的窗口。 */
    private static List<String> window(String text, int chunkSize, int chunkOverlap) {
        List<String> out = new ArrayList<>();
        if (text.length() <= chunkSize) {
            out.add(text);
            return out;
        }
        int step = Math.max(1, chunkSize - chunkOverlap);
        for (int start = 0; start < text.length(); start += step) {
            int end = Math.min(text.length(), start + chunkSize);
            out.add(text.substring(start, end));
            if (end == text.length()) break;
        }
        return out;
    }

    /** 抽取关键词：中文 2-gram + 英文小写单词。 */
    public static List<String> keywords(String text) {
        if (text == null || text.isEmpty()) return Collections.emptyList();
        String lower = text.toLowerCase(Locale.ROOT);
        Set<String> set = new LinkedHashSet<>();
        // 中文 2-gram
        for (int i = 0; i + 1 < lower.length(); i++) {
            char a = lower.charAt(i), b = lower.charAt(i + 1);
            if (a > 127 && b > 127) {
                set.add(lower.substring(i, i + 2));
            }
        }
        // 英文/数字 token
        Matcher m = Pattern.compile("[a-z0-9]{2,}").matcher(lower);
        while (m.find()) set.add(m.group());
        if (set.size() > 32) {
            // 截断，避免关键词列表过大
            return new ArrayList<>(set).subList(0, 32);
        }
        return new ArrayList<>(set);
    }

    /** 切块器中间结果：仅含 text / heading / sectionPath，embedding/keywords 由上层补齐。 */
    public static final class RawChunk {
        public final String text;
        public final String heading;
        public final List<String> sectionPath;
        public RawChunk(String text, String heading, List<String> sectionPath) {
            this.text = text;
            this.heading = heading;
            this.sectionPath = sectionPath;
        }
    }
}
