package cn.fj.loli.inlayhintsextension.path;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Optional;

public final class SidecarPathMapper {
    public static final String SIDECAR_DIRECTORY = "inlay-hints";
    public static final String SIDECAR_EXTENSION = "ihm";

    private SidecarPathMapper() {
    }

    public static @NotNull Optional<Path> toSidecar(
            @NotNull Path projectRoot,
            @NotNull Path sourceFile
    ) {
        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
        Path normalizedSource = sourceFile.toAbsolutePath().normalize();
        Path sidecarRoot = normalizedRoot.resolve(SIDECAR_DIRECTORY);
        if (!normalizedSource.startsWith(normalizedRoot)
                || normalizedSource.equals(normalizedRoot)
                || normalizedSource.startsWith(sidecarRoot)) {
            return Optional.empty();
        }

        Path relativeSource = normalizedRoot.relativize(normalizedSource);
        Path relativeParent = relativeSource.getParent();
        String targetName = relativeSource.getFileName() + "." + SIDECAR_EXTENSION;
        Path sidecar = relativeParent == null
                ? sidecarRoot.resolve(targetName)
                : sidecarRoot.resolve(relativeParent).resolve(targetName);
        return Optional.of(sidecar.normalize());
    }

    public static @NotNull Optional<Path> toSidecarRoot(
            @NotNull Path projectRoot,
            @NotNull Path sourceFile
    ) {
        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
        return toSidecar(normalizedRoot, sourceFile).isEmpty()
                ? Optional.empty()
                : Optional.of(normalizedRoot.resolve(SIDECAR_DIRECTORY));
    }

}
