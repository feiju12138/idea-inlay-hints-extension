package cn.fj.loli.inlayhintsextension.path;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
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
        if (!isMappable(normalizedRoot, normalizedSource)) {
            return Optional.empty();
        }

        Path adjacentSidecar = appendSidecarExtension(normalizedSource);
        if (Files.isRegularFile(adjacentSidecar)) {
            return Optional.of(adjacentSidecar);
        }

        Path nearestExistingSidecar = null;
        Path projectRootSidecar = null;
        for (Path searchRoot = normalizedSource.getParent(); searchRoot != null; searchRoot = searchRoot.getParent()) {
            Path sidecarRoot = searchRoot.resolve(SIDECAR_DIRECTORY);
            Path candidate = appendSidecarExtension(
                    sidecarRoot.resolve(searchRoot.relativize(normalizedSource))
            );
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
            if (nearestExistingSidecar == null && Files.isDirectory(sidecarRoot)) {
                nearestExistingSidecar = candidate;
            }
            if (searchRoot.equals(normalizedRoot)) {
                projectRootSidecar = candidate;
                break;
            }
        }

        return Optional.of(nearestExistingSidecar == null ? projectRootSidecar : nearestExistingSidecar);
    }

    public static @NotNull Optional<Path> toSidecarRoot(
            @NotNull Path projectRoot,
            @NotNull Path sourceFile,
            @NotNull Path sidecarFile
    ) {
        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
        Path normalizedSource = sourceFile.toAbsolutePath().normalize();
        Path normalizedSidecar = sidecarFile.toAbsolutePath().normalize();
        if (!isMappable(normalizedRoot, normalizedSource)) {
            return Optional.empty();
        }

        for (Path searchRoot = normalizedSource.getParent(); searchRoot != null; searchRoot = searchRoot.getParent()) {
            Path sidecarRoot = searchRoot.resolve(SIDECAR_DIRECTORY);
            Path candidate = appendSidecarExtension(
                    sidecarRoot.resolve(searchRoot.relativize(normalizedSource))
            );
            if (candidate.equals(normalizedSidecar)) {
                return Optional.of(sidecarRoot);
            }
            if (searchRoot.equals(normalizedRoot)) {
                break;
            }
        }
        return Optional.empty();
    }

    private static boolean isMappable(Path projectRoot, Path sourceFile) {
        if (!sourceFile.startsWith(projectRoot)
                || sourceFile.equals(projectRoot)
                || sourceFile.getFileName().toString().toLowerCase(Locale.ROOT)
                        .endsWith("." + SIDECAR_EXTENSION)) {
            return false;
        }
        for (Path part : projectRoot.relativize(sourceFile)) {
            if (part.toString().equals(SIDECAR_DIRECTORY)) {
                return false;
            }
        }
        return true;
    }

    private static Path appendSidecarExtension(Path path) {
        return path.resolveSibling(path.getFileName() + "." + SIDECAR_EXTENSION).normalize();
    }

}
