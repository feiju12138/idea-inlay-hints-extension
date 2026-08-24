package cn.fj.loli.inlayhintsextension.filetype;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import java.nio.charset.StandardCharsets;

public final class IhmFileType implements FileType {
    public static final IhmFileType INSTANCE = new IhmFileType();
    private static final Icon ICON = IconLoader.getIcon("/icons/ihmFile.svg", IhmFileType.class);

    private IhmFileType() {
    }

    @Override
    public @NonNls @NotNull String getName() {
        return "Inlay Hint Map";
    }

    @Override
    public @Nls @NotNull String getDescription() {
        return "Line-oriented inlay hint map";
    }

    @Override
    public @NonNls @NotNull String getDefaultExtension() {
        return "ihm";
    }

    @Override
    public @NotNull Icon getIcon() {
        return ICON;
    }

    @Override
    public boolean isBinary() {
        return false;
    }

    @Override
    public String getCharset(@NotNull VirtualFile file, @NotNull byte[] content) {
        return StandardCharsets.UTF_8.name();
    }
}
