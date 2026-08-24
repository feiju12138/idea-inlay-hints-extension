package cn.fj.loli.inlayhintsextension.sync;

public record LineChange(
        int startLineBefore,
        int endLineBefore,
        int startLineAfter,
        int endLineAfter
) {
    public LineChange {
        if (startLineBefore < 0 || startLineAfter < 0) {
            throw new IllegalArgumentException("Line change indexes must not be negative");
        }
        if (endLineBefore < startLineBefore || endLineAfter < startLineAfter) {
            throw new IllegalArgumentException("Line change end indexes must not precede start indexes");
        }
    }

    public int removedLineCount() {
        return endLineBefore - startLineBefore;
    }

    public int insertedLineCount() {
        return endLineAfter - startLineAfter;
    }

    public int modifiedLineCount() {
        return Math.min(removedLineCount(), insertedLineCount());
    }
}
