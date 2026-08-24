package cn.fj.loli.inlayhintsextension.editor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

public final class InlayHintsStartupActivity implements StartupActivity, DumbAware {
    @Override
    public void runActivity(@NotNull Project project) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) {
                return;
            }
            InlayHintsExtensionService service = project.getService(InlayHintsExtensionService.class);
            for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
                if (editor.getProject() == project) {
                    service.attach(editor);
                }
            }
        }, project.getDisposed());
    }
}
