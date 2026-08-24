package cn.fj.loli.inlayhintsextension.sync;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class SidecarText {
    private SidecarText() {
    }

    public static @NotNull String apply(
            @NotNull String text,
            @NotNull List<LineChange> changes,
            boolean clearModifiedLines
    ) {
        List<String> lines = new ArrayList<>(Arrays.asList(text.split("\\n", -1)));
        for (LineChange change : changes) {
            apply(lines, change, clearModifiedLines);
        }
        return String.join("\n", lines);
    }

    public static @NotNull String updateLine(
            @NotNull String text,
            int line,
            @NotNull String value,
            int minimumLineCount
    ) {
        if (line < 0) {
            throw new IllegalArgumentException("Line index must not be negative");
        }
        if (minimumLineCount <= 0) {
            throw new IllegalArgumentException("Minimum line count must be positive");
        }

        List<String> lines = new ArrayList<>(Arrays.asList(text.split("\\n", -1)));
        int requiredLineCount = Math.max(minimumLineCount, line + 1);
        while (lines.size() < requiredLineCount) {
            lines.add("");
        }
        lines.set(line, value);
        return String.join("\n", lines);
    }

    public static boolean removesLastHint(
            @NotNull String text,
            int line,
            @NotNull String replacement
    ) {
        if (line < 0 || !replacement.isBlank()) {
            return false;
        }

        String[] lines = text.split("\\n", -1);
        if (line >= lines.length || lines[line].isBlank()) {
            return false;
        }
        for (int index = 0; index < lines.length; index++) {
            if (index != line && !lines[index].isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static void apply(List<String> lines, LineChange change, boolean clearModifiedLines) {
        int lineIndex = change.startLineAfter();
        while (lines.size() < lineIndex + change.modifiedLineCount()) {
            lines.add("");
        }

        if (clearModifiedLines) {
            for (int count = 0; count < change.modifiedLineCount(); count++) {
                lines.set(lineIndex + count, "");
            }
        }

        int removedOnlyCount = change.removedLineCount() - change.modifiedLineCount();
        for (int count = 0; count < removedOnlyCount && lineIndex + change.modifiedLineCount() < lines.size(); count++) {
            lines.remove(lineIndex + change.modifiedLineCount());
        }

        int insertedOnlyCount = change.insertedLineCount() - change.modifiedLineCount();
        for (int count = 0; count < insertedOnlyCount; count++) {
            lines.add(lineIndex + change.modifiedLineCount() + count, "");
        }

        if (lines.isEmpty()) {
            lines.add("");
        }
    }
}
