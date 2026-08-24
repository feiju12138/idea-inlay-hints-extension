package cn.fj.loli.inlayhintsextension.editor;

import cn.fj.loli.inlayhintsextension.settings.InlayHintsExtensionSettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.KeyboardFocusManager;

final class HintActivationSession implements Disposable {
    private final Editor editor;
    private final Project project;
    private final ActivationHandler activationHandler;
    private boolean activationPending;
    private boolean disposed;

    HintActivationSession(Editor editor, Project project, ActivationHandler activationHandler) {
        this.editor = editor;
        this.project = project;
        this.activationHandler = activationHandler;
        editor.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(@NotNull DocumentEvent event) {
                detectActivation(event);
            }
        }, this);
    }

    private void detectActivation(DocumentEvent event) {
        if (activationPending || disposed || editor.isDisposed() || !isEditorFocused()) {
            return;
        }
        String keyword = InlayHintsExtensionSettings.getInstance().getActivationKeyword();
        HintActivationDetector.Match match = HintActivationDetector.detect(
                event.getDocument().getCharsSequence(),
                event.getOffset(),
                event.getNewLength(),
                keyword
        );
        if (match.state() != HintActivationDetector.State.COMPLETE) {
            return;
        }

        activationPending = true;
        ApplicationManager.getApplication().invokeLater(() -> {
            activationPending = false;
            if (!disposed && !project.isDisposed() && !editor.isDisposed()) {
                activationHandler.activate(editor, match, keyword);
            }
        }, project.getDisposed());
    }

    private boolean isEditorFocused() {
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        return focusOwner != null
                && (focusOwner == editor.getContentComponent()
                || SwingUtilities.isDescendingFrom(focusOwner, editor.getComponent()));
    }

    @Override
    public void dispose() {
        disposed = true;
    }

    @FunctionalInterface
    interface ActivationHandler {
        void activate(Editor editor, HintActivationDetector.Match match, String keyword);
    }
}
