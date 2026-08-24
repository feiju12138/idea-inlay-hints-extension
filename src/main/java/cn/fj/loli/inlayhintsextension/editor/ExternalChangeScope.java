package cn.fj.loli.inlayhintsextension.editor;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ExternalChangeScope {
    private ExternalChangeScope() {
    }

    static boolean shouldHandle(
            @Nullable String projectBasePath,
            @NotNull String sourcePath,
            boolean hasOpenEditor
    ) {
        return !hasOpenEditor && isInsideProject(projectBasePath, sourcePath);
    }

    static boolean isInsideProject(@Nullable String projectBasePath, @NotNull String path) {
        return projectBasePath != null && FileUtil.isAncestor(projectBasePath, path, false);
    }
}
