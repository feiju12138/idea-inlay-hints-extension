package cn.fj.loli.inlayhintsextension.editor;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public final class InlayHintsEditorFactoryListener implements EditorFactoryListener {
    @Override
    public void editorCreated(@NotNull EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        Project project = editor.getProject();
        if (project != null && !project.isDisposed()) {
            project.getService(InlayHintsExtensionService.class).attach(editor);
        }
    }

    @Override
    public void editorReleased(@NotNull EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        Project project = editor.getProject();
        if (project != null && !project.isDisposed()) {
            project.getService(InlayHintsExtensionService.class).detach(editor);
        }
    }
}
