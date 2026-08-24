package cn.fj.loli.inlayhintsextension.path;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Optional;

public final class SidecarPathMapper {
    public static final String SIDECAR_DIRECTORY = "inlay-hints";
    public static final String SIDECAR_EXTENSION = "ihm";

    private SidecarPathMapper() {
    }

    public static @NotNull Optional<Path> toSidecar(@NotNull Path sourceFile) {
        Path normalizedSource = sourceFile.toAbsolutePath().normalize();
        Path sourceRoot = findNearestSourceRoot(normalizedSource.getParent());
        if (sourceRoot == null) {
            return Optional.empty();
        }

        Path relativeSource = sourceRoot.relativize(normalizedSource);
        Path relativeParent = relativeSource.getParent();
        String targetName = relativeSource.getFileName() + "." + SIDECAR_EXTENSION;
        Path sidecarRoot = sourceRoot.resolveSibling(SIDECAR_DIRECTORY);
        Path sidecar = relativeParent == null
                ? sidecarRoot.resolve(targetName)
                : sidecarRoot.resolve(relativeParent).resolve(targetName);
        return Optional.of(sidecar.normalize());
    }

    public static @NotNull Optional<Path> toSidecarRoot(@NotNull Path sourceFile) {
        Path normalizedSource = sourceFile.toAbsolutePath().normalize();
        Path sourceRoot = findNearestSourceRoot(normalizedSource.getParent());
        return sourceRoot == null
                ? Optional.empty()
                : Optional.of(sourceRoot.resolveSibling(SIDECAR_DIRECTORY).normalize());
    }

    private static Path findNearestSourceRoot(Path directory) {
        Path current = directory;
        while (current != null) {
            Path name = current.getFileName();
            if (name != null && "src".equals(name.toString())) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

}
