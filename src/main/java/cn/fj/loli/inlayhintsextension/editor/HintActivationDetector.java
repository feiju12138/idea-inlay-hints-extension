package cn.fj.loli.inlayhintsextension.editor;

import org.jetbrains.annotations.NotNull;

final class HintActivationDetector {
    private HintActivationDetector() {
    }

    static @NotNull Match detect(
            @NotNull CharSequence text,
            int changeOffset,
            int newLength,
            @NotNull String keyword
    ) {
        if (newLength <= 0 || keyword.isEmpty()) {
            return Match.none();
        }

        int changeEnd = Math.min(changeOffset + newLength, text.length());
        int lineEnd = changeEnd;
        while (lineEnd < text.length() && text.charAt(lineEnd) != '\n') {
            lineEnd++;
        }
        if (changeEnd != lineEnd) {
            return Match.none();
        }

        int lineStart = changeEnd;
        while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') {
            lineStart--;
        }
        int lineLength = lineEnd - lineStart;
        int maxCandidateLength = Math.min(keyword.length(), lineLength);
        for (int length = maxCandidateLength; length > 0; length--) {
            if (!endsWith(text, lineEnd, keyword, length)) {
                continue;
            }
            int line = countLineBreaks(text, lineStart);
            State state = length == keyword.length() ? State.COMPLETE : State.PREFIX;
            return new Match(state, line, lineEnd - length, lineEnd);
        }
        return Match.none();
    }

    private static boolean endsWith(CharSequence text, int lineEnd, String keyword, int length) {
        int textStart = lineEnd - length;
        for (int index = 0; index < length; index++) {
            if (text.charAt(textStart + index) != keyword.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private static int countLineBreaks(CharSequence text, int endOffset) {
        int line = 0;
        for (int index = 0; index < endOffset; index++) {
            if (text.charAt(index) == '\n') {
                line++;
            }
        }
        return line;
    }

    enum State {
        NONE,
        PREFIX,
        COMPLETE
    }

    record Match(State state, int line, int keywordStartOffset, int keywordEndOffset) {
        private static Match none() {
            return new Match(State.NONE, -1, -1, -1);
        }
    }
}
