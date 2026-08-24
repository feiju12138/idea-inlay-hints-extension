package cn.fj.loli.inlayhintsextension.sync;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

public final class SidecarSyncHistory {
    private static final int DEFAULT_MAX_TRANSITIONS = 50;

    private final int maxTransitions;
    private final Deque<Transition> undoTransitions = new ArrayDeque<>();
    private final Deque<Transition> redoTransitions = new ArrayDeque<>();

    public SidecarSyncHistory() {
        this(DEFAULT_MAX_TRANSITIONS);
    }

    SidecarSyncHistory(int maxTransitions) {
        if (maxTransitions <= 0) {
            throw new IllegalArgumentException("Maximum transition count must be positive");
        }
        this.maxTransitions = maxTransitions;
    }

    public @NotNull Optional<String> tryRestore(
            @NotNull String previousSourceText,
            @NotNull String currentSourceText,
            @NotNull String currentSidecarText
    ) {
        Transition undo = undoTransitions.peekLast();
        if (undo != null && undo.matchesUndo(previousSourceText, currentSourceText, currentSidecarText)) {
            undoTransitions.removeLast();
            redoTransitions.addLast(undo);
            return Optional.of(undo.sidecarTextBefore);
        }

        Transition redo = redoTransitions.peekLast();
        if (redo != null && redo.matchesRedo(previousSourceText, currentSourceText, currentSidecarText)) {
            redoTransitions.removeLast();
            undoTransitions.addLast(redo);
            return Optional.of(redo.sidecarTextAfter);
        }
        return Optional.empty();
    }

    public void record(
            @NotNull String sourceTextBefore,
            @NotNull String sourceTextAfter,
            @NotNull String sidecarTextBefore,
            @NotNull String sidecarTextAfter
    ) {
        if (sourceTextBefore.equals(sourceTextAfter)) {
            return;
        }
        undoTransitions.addLast(new Transition(
                sourceTextBefore,
                sourceTextAfter,
                sidecarTextBefore,
                sidecarTextAfter
        ));
        while (undoTransitions.size() > maxTransitions) {
            undoTransitions.removeFirst();
        }
        redoTransitions.clear();
    }

    private record Transition(
            String sourceTextBefore,
            String sourceTextAfter,
            String sidecarTextBefore,
            String sidecarTextAfter
    ) {
        private boolean matchesUndo(String previousSource, String currentSource, String currentSidecar) {
            return sourceTextAfter.equals(previousSource)
                    && sourceTextBefore.equals(currentSource)
                    && sidecarTextAfter.equals(currentSidecar);
        }

        private boolean matchesRedo(String previousSource, String currentSource, String currentSidecar) {
            return sourceTextBefore.equals(previousSource)
                    && sourceTextAfter.equals(currentSource)
                    && sidecarTextBefore.equals(currentSidecar);
        }
    }
}
