package cn.fj.loli.inlayhintsextension.editor;

import cn.fj.loli.inlayhintsextension.path.SidecarPathMapper;
import cn.fj.loli.inlayhintsextension.settings.InlayHintsExtensionSettings;
import cn.fj.loli.inlayhintsextension.sync.LineChange;
import cn.fj.loli.inlayhintsextension.sync.SidecarSyncHistory;
import cn.fj.loli.inlayhintsextension.sync.SidecarText;
import com.intellij.diff.comparison.ComparisonManager;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.comparison.DiffTooBigException;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service(Service.Level.PROJECT)
public final class InlayHintsExtensionService implements Disposable {
    private static final String EXCLUDE_NOTICE_KEY = "inlay.hints.extension.git.exclude.notice.v3.shown";
    private static final int UPDATE_DELAY_MILLIS = 100;

    private final Project project;
    private final @Nullable Path projectRoot;
    private final Alarm alarm;
    private final Map<Editor, EditorSession> sessions = new IdentityHashMap<>();
    private final Map<Editor, HintActivationSession> activationSessions = new IdentityHashMap<>();
    private final Map<Document, TrackedDocument> trackedDocuments = new IdentityHashMap<>();
    private final Set<String> trackedSourcePaths = ConcurrentHashMap.newKeySet();
    private final Map<String, ExternalSourceSnapshot> externalSourceSnapshots = new HashMap<>();
    private final List<ExternalSourceChange> pendingExternalSourceChanges = new ArrayList<>();
    private final Runnable refreshOpenEditorsRequest = this::refreshOpenEditors;
    private final Runnable synchronizeExternalChangesRequest = this::synchronizeExternalChanges;

    public InlayHintsExtensionService(Project project) {
        this.project = project;
        String projectBasePath = project.getBasePath();
        this.projectRoot = projectBasePath == null
                ? null
                : Path.of(projectBasePath).toAbsolutePath().normalize();
        this.alarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
        project.getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void before(@NotNull List<? extends VFileEvent> events) {
                events.forEach(InlayHintsExtensionService.this::captureExternalSourceBeforeChange);
            }

            @Override
            public void after(@NotNull List<? extends VFileEvent> events) {
                events.forEach(InlayHintsExtensionService.this::captureExternalSourceAfterChange);
                if (events.stream().anyMatch(InlayHintsExtensionService.this::isRelevantVfsEvent)) {
                    scheduleRefreshOpenEditors();
                }
            }
        });
    }

    public void attach(@NotNull Editor editor) {
        if (project.isDisposed() || editor.isDisposed() || editor.getProject() != project) {
            return;
        }

        FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
        VirtualFile sourceFile = fileDocumentManager.getFile(editor.getDocument());
        if (sourceFile == null || !sourceFile.isInLocalFileSystem()) {
            return;
        }

        Path sourcePath = Path.of(sourceFile.getPath());
        Path sidecarPath = toSidecar(sourcePath);
        if (sidecarPath == null) {
            return;
        }

        activationSessions.computeIfAbsent(
                editor,
                currentEditor -> new HintActivationSession(
                        currentEditor,
                        project,
                        this::activateHintEditing
                )
        );
        if (sessions.containsKey(editor)) {
            return;
        }

        VirtualFile sidecarFile = LocalFileSystem.getInstance().findFileByNioFile(sidecarPath);
        if (sidecarFile == null || sidecarFile.isDirectory()) {
            return;
        }
        Document sidecarDocument = getUtf8Document(fileDocumentManager, sidecarFile);
        if (sidecarDocument == null) {
            return;
        }

        TrackedDocument tracked = trackedDocuments.computeIfAbsent(
                editor.getDocument(),
                sourceDocument -> track(sourceDocument, sourceFile, sidecarFile, sidecarDocument)
        );
        if (!tracked.sidecarFile.equals(sidecarFile)) {
            return;
        }

        EditorSession session = new EditorSession(editor, sidecarFile, sidecarDocument);
        sessions.put(editor, session);
        tracked.sessions.add(session);
        session.render(InlayHintsExtensionSettings.getInstance().isShowHints());
        showExcludeNoticeOnce();
    }

    public void detach(@NotNull Editor editor) {
        HintActivationSession activationSession = activationSessions.remove(editor);
        if (activationSession != null) {
            Disposer.dispose(activationSession);
        }
        EditorSession session = sessions.remove(editor);
        if (session == null) {
            return;
        }
        TrackedDocument tracked = trackedDocuments.get(editor.getDocument());
        if (tracked != null) {
            tracked.sessions.remove(session);
            if (tracked.sessions.isEmpty()) {
                alarm.cancelRequest(tracked.syncRequest);
                alarm.cancelRequest(tracked.renderRequest);
                trackedDocuments.remove(editor.getDocument());
                trackedSourcePaths.remove(tracked.sourcePath);
                Disposer.dispose(tracked.listenerDisposable);
            }
        }
        Disposer.dispose(session);
    }

    @Override
    public void dispose() {
        List<EditorSession> activeSessions = new ArrayList<>(sessions.values());
        List<HintActivationSession> activeActivationSessions = new ArrayList<>(activationSessions.values());
        sessions.clear();
        activationSessions.clear();
        trackedDocuments.clear();
        trackedSourcePaths.clear();
        for (EditorSession session : activeSessions) {
            Disposer.dispose(session);
        }
        for (HintActivationSession activationSession : activeActivationSessions) {
            Disposer.dispose(activationSession);
        }
    }

    public void settingsChanged() {
        for (TrackedDocument tracked : List.copyOf(trackedDocuments.values())) {
            render(tracked);
        }
    }

    private TrackedDocument track(
            Document sourceDocument,
            VirtualFile sourceFile,
            VirtualFile sidecarFile,
            Document sidecarDocument
    ) {
        TrackedDocument tracked = new TrackedDocument(sourceDocument, sourceFile.getPath(), sidecarFile, sidecarDocument);
        trackedSourcePaths.add(tracked.sourcePath);
        Disposer.register(this, tracked.listenerDisposable);
        sourceDocument.addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(@NotNull DocumentEvent event) {
                if (tracked.suppressSourceEvents) {
                    return;
                }
                String activationKeyword = InlayHintsExtensionSettings.getInstance().getActivationKeyword();
                HintActivationDetector.Match activationMatch = HintActivationDetector.detect(
                        sourceDocument.getCharsSequence(),
                        event.getOffset(),
                        event.getNewLength(),
                        activationKeyword
                );
                if (activationMatch.state() == HintActivationDetector.State.COMPLETE) {
                    alarm.cancelRequest(tracked.syncRequest);
                    return;
                }
                if (activationMatch.state() == HintActivationDetector.State.PREFIX) {
                    scheduleSync(tracked);
                    return;
                }
                tracked.clearModifiedLines |= InlayHintsExtensionSettings.getInstance().isClearModifiedLines();
                scheduleSync(tracked);
            }
        }, tracked.listenerDisposable);
        sidecarDocument.addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(@NotNull DocumentEvent event) {
                scheduleRender(tracked);
            }
        }, tracked.listenerDisposable);
        return tracked;
    }

    private void scheduleSync(TrackedDocument tracked) {
        alarm.cancelRequest(tracked.syncRequest);
        alarm.addRequest(tracked.syncRequest, UPDATE_DELAY_MILLIS);
    }

    private void synchronize(TrackedDocument tracked) {
        if (project.isDisposed() || !tracked.sidecarFile.isValid()) {
            return;
        }

        String currentSourceText = tracked.sourceDocument.getText();
        if (tracked.sourceSnapshot.equals(currentSourceText)) {
            tracked.clearModifiedLines = false;
            render(tracked);
            return;
        }
        if (!tracked.sidecarFile.isWritable()) {
            return;
        }

        String currentText = tracked.sidecarDocument.getText();
        Optional<String> restoredText = tracked.syncHistory.tryRestore(
                tracked.sourceSnapshot,
                currentSourceText,
                currentText
        );
        String updatedText = restoredText.orElseGet(() -> synchronizeSidecarText(
                currentText,
                tracked.sourceSnapshot,
                currentSourceText,
                tracked.clearModifiedLines
        ));
        if (currentText.equals(updatedText)) {
            if (restoredText.isEmpty()) {
                tracked.syncHistory.record(
                        tracked.sourceSnapshot,
                        currentSourceText,
                        currentText,
                        updatedText
                );
            }
            tracked.sourceSnapshot = currentSourceText;
            tracked.clearModifiedLines = false;
            render(tracked);
            return;
        }

        replaceAndSaveDocument(tracked.sidecarDocument, updatedText);
        if (restoredText.isEmpty()) {
            tracked.syncHistory.record(
                    tracked.sourceSnapshot,
                    currentSourceText,
                    currentText,
                    updatedText
            );
        }
        tracked.sourceSnapshot = currentSourceText;
        tracked.clearModifiedLines = false;
    }

    private void scheduleRender(TrackedDocument tracked) {
        alarm.cancelRequest(tracked.renderRequest);
        alarm.addRequest(tracked.renderRequest, UPDATE_DELAY_MILLIS);
    }

    private void render(TrackedDocument tracked) {
        boolean showHints = InlayHintsExtensionSettings.getInstance().isShowHints();
        for (EditorSession session : List.copyOf(tracked.sessions)) {
            session.render(showHints);
        }
    }

    private void scheduleRefreshOpenEditors() {
        alarm.cancelRequest(refreshOpenEditorsRequest);
        alarm.addRequest(refreshOpenEditorsRequest, UPDATE_DELAY_MILLIS);
    }

    private void refreshOpenEditors() {
        for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
            if (editor.getProject() == project) {
                EditorSession session = sessions.get(editor);
                Path expectedSidecar = expectedSidecar(editor);
                if (session != null && (expectedSidecar == null || !session.matchesSidecar(expectedSidecar))) {
                    detach(editor);
                }
                attach(editor);
            }
        }
        for (TrackedDocument tracked : List.copyOf(trackedDocuments.values())) {
            render(tracked);
        }
    }

    private boolean isRelevantVfsEvent(VFileEvent event) {
        return ExternalChangeScope.isInsideProject(project.getBasePath(), event.getPath());
    }

    private void captureExternalSourceBeforeChange(VFileEvent event) {
        if (!(event instanceof VFileContentChangeEvent contentChangeEvent)) {
            return;
        }
        VirtualFile sourceFile = contentChangeEvent.getFile();
        if (!sourceFile.isInLocalFileSystem()
                || !ExternalChangeScope.shouldHandle(
                        project.getBasePath(),
                        sourceFile.getPath(),
                        hasOpenEditor(sourceFile)
                )
                || trackedSourcePaths.contains(sourceFile.getPath())) {
            return;
        }

        Path sidecarPath = toSidecar(Path.of(sourceFile.getPath()));
        if (sidecarPath == null || LocalFileSystem.getInstance().findFileByNioFile(sidecarPath) == null) {
            return;
        }

        try {
            ExternalSourceSnapshot snapshot = new ExternalSourceSnapshot(
                    sidecarPath,
                    VfsUtilCore.loadText(sourceFile),
                    InlayHintsExtensionSettings.getInstance().isClearModifiedLines()
            );
            synchronized (externalSourceSnapshots) {
                externalSourceSnapshots.put(sourceFile.getPath(), snapshot);
            }
        } catch (IOException ignored) {
            // A later source change will retry synchronization.
        }
    }

    private void captureExternalSourceAfterChange(VFileEvent event) {
        if (!(event instanceof VFileContentChangeEvent contentChangeEvent)) {
            return;
        }
        VirtualFile sourceFile = contentChangeEvent.getFile();
        ExternalSourceSnapshot snapshot;
        synchronized (externalSourceSnapshots) {
            snapshot = externalSourceSnapshots.remove(sourceFile.getPath());
        }
        if (snapshot == null) {
            return;
        }

        try {
            String sourceTextAfter = VfsUtilCore.loadText(sourceFile);
            if (snapshot.sourceTextBefore.equals(sourceTextAfter)) {
                return;
            }
            ExternalSourceChange change = new ExternalSourceChange(
                    snapshot.sidecarPath,
                    snapshot.sourceTextBefore,
                    sourceTextAfter,
                    snapshot.clearModifiedLines
            );
            ApplicationManager.getApplication().invokeLater(
                    () -> enqueueExternalSourceChange(change),
                    project.getDisposed()
            );
        } catch (IOException ignored) {
            // A later source change will retry synchronization.
        }
    }

    private void enqueueExternalSourceChange(ExternalSourceChange change) {
        pendingExternalSourceChanges.add(change);
        alarm.cancelRequest(synchronizeExternalChangesRequest);
        alarm.addRequest(synchronizeExternalChangesRequest, UPDATE_DELAY_MILLIS);
    }

    private void synchronizeExternalChanges() {
        List<ExternalSourceChange> changes = new ArrayList<>(pendingExternalSourceChanges);
        pendingExternalSourceChanges.clear();
        for (ExternalSourceChange change : changes) {
            VirtualFile sidecarFile = LocalFileSystem.getInstance().findFileByNioFile(change.sidecarPath);
            if (sidecarFile == null || !sidecarFile.isWritable()) {
                continue;
            }
            FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
            Document sidecarDocument = getUtf8Document(fileDocumentManager, sidecarFile);
            if (sidecarDocument == null) {
                continue;
            }

            String currentText = sidecarDocument.getText();
            String updatedText = synchronizeSidecarText(
                    currentText,
                    change.sourceTextBefore,
                    change.sourceTextAfter,
                    change.clearModifiedLines
            );
            if (currentText.equals(updatedText)) {
                continue;
            }
            replaceAndSaveDocument(sidecarDocument, updatedText);
        }
    }

    private static String synchronizeSidecarText(
            String sidecarText,
            String sourceTextBefore,
            String sourceTextAfter,
            boolean clearModifiedLines
    ) {
        ComparisonPolicy policy = clearModifiedLines
                ? ComparisonPolicy.DEFAULT
                : ComparisonPolicy.TRIM_WHITESPACES;
        List<LineChange> changes;
        try {
            changes = ComparisonManager.getInstance()
                    .compareLines(sourceTextBefore, sourceTextAfter, policy, new EmptyProgressIndicator())
                    .stream()
                    .map(InlayHintsExtensionService::toLineChange)
                    .toList();
        } catch (DiffTooBigException ignored) {
            changes = fallbackLineChanges(sourceTextBefore, sourceTextAfter, clearModifiedLines);
        }
        return SidecarText.apply(sidecarText, changes, clearModifiedLines);
    }

    private static LineChange toLineChange(LineFragment fragment) {
        return new LineChange(
                fragment.getStartLine1(),
                fragment.getEndLine1(),
                fragment.getStartLine2(),
                fragment.getEndLine2()
        );
    }

    private static List<LineChange> fallbackLineChanges(
            String sourceTextBefore,
            String sourceTextAfter,
            boolean compareWhitespace
    ) {
        String[] linesBefore = sourceTextBefore.split("\\n", -1);
        String[] linesAfter = sourceTextAfter.split("\\n", -1);
        int prefix = 0;
        while (prefix < linesBefore.length
                && prefix < linesAfter.length
                && linesEqual(linesBefore[prefix], linesAfter[prefix], compareWhitespace)) {
            prefix++;
        }

        int suffix = 0;
        while (suffix < linesBefore.length - prefix
                && suffix < linesAfter.length - prefix
                && linesEqual(
                        linesBefore[linesBefore.length - suffix - 1],
                        linesAfter[linesAfter.length - suffix - 1],
                        compareWhitespace
                )) {
            suffix++;
        }
        if (prefix == linesBefore.length && prefix == linesAfter.length) {
            return List.of();
        }
        return List.of(new LineChange(
                prefix,
                linesBefore.length - suffix,
                prefix,
                linesAfter.length - suffix
        ));
    }

    private static boolean linesEqual(String first, String second, boolean compareWhitespace) {
        return compareWhitespace ? first.equals(second) : first.trim().equals(second.trim());
    }

    private Path expectedSidecar(Editor editor) {
        VirtualFile sourceFile = FileDocumentManager.getInstance().getFile(editor.getDocument());
        if (sourceFile == null || !sourceFile.isInLocalFileSystem()) {
            return null;
        }
        return toSidecar(Path.of(sourceFile.getPath()));
    }

    private @Nullable Path toSidecar(@NotNull Path sourcePath) {
        return projectRoot == null
                ? null
                : SidecarPathMapper.toSidecar(projectRoot, sourcePath).orElse(null);
    }

    private static boolean hasOpenEditor(VirtualFile sourceFile) {
        Document document = FileDocumentManager.getInstance().getCachedDocument(sourceFile);
        return document != null && EditorFactory.getInstance().getEditors(document).length > 0;
    }

    private void activateHintEditing(
            Editor editor,
            HintActivationDetector.Match match,
            String keyword
    ) {
        Document sourceDocument = editor.getDocument();
        VirtualFile sourceFile = FileDocumentManager.getInstance().getFile(sourceDocument);
        if (sourceFile == null || !isActivationKeywordPresent(sourceDocument, match, keyword)) {
            return;
        }
        Path sidecarPath = toSidecar(Path.of(sourceFile.getPath()));
        if (sidecarPath == null || ensureSidecarFile(sidecarPath, sourceDocument) == null) {
            return;
        }

        TrackedDocument trackedBeforeCreation = trackedDocuments.get(sourceDocument);
        if (!removeActivationKeyword(sourceDocument, trackedBeforeCreation, match, keyword)) {
            return;
        }
        if (trackedBeforeCreation != null) {
            alarm.cancelRequest(trackedBeforeCreation.syncRequest);
            trackedBeforeCreation.clearModifiedLines = false;
            synchronize(trackedBeforeCreation);
        }

        attach(editor);
        TrackedDocument tracked = trackedDocuments.get(sourceDocument);
        EditorSession session = sessions.get(editor);
        if (tracked == null || session == null || match.line() >= sourceDocument.getLineCount()) {
            return;
        }

        String initialText = getSidecarLine(tracked.sidecarDocument, match.line());
        session.startHintEditing(
                match.line(),
                initialText,
                value -> updateHintLine(tracked, match.line(), value),
                () -> render(tracked)
        );
    }

    private static boolean isActivationKeywordPresent(
            Document sourceDocument,
            HintActivationDetector.Match match,
            String keyword
    ) {
        if (match.keywordStartOffset() < 0 || match.keywordEndOffset() > sourceDocument.getTextLength()) {
            return false;
        }
        return keyword.equals(sourceDocument.getText(
                new TextRange(match.keywordStartOffset(), match.keywordEndOffset())
        ));
    }

    private boolean removeActivationKeyword(
            Document sourceDocument,
            @Nullable TrackedDocument tracked,
            HintActivationDetector.Match match,
            String keyword
    ) {
        if (!isActivationKeywordPresent(sourceDocument, match, keyword)) {
            return false;
        }

        if (tracked != null) {
            tracked.suppressSourceEvents = true;
        }
        try {
            CommandProcessor.getInstance().runUndoTransparentAction(
                    () -> ApplicationManager.getApplication().runWriteAction(
                            () -> sourceDocument.deleteString(
                                    match.keywordStartOffset(),
                                    match.keywordEndOffset()
                            )
                    )
            );
        } finally {
            if (tracked != null) {
                tracked.suppressSourceEvents = false;
            }
        }
        return true;
    }

    private @Nullable VirtualFile ensureSidecarFile(Path sidecarPath, Document sourceDocument) {
        LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
        VirtualFile existingFile = localFileSystem.findFileByNioFile(sidecarPath);
        if (existingFile != null && !existingFile.isDirectory()) {
            return existingFile;
        }

        try {
            Files.createDirectories(sidecarPath.getParent());
            String emptySidecar = "\n".repeat(Math.max(0, sourceDocument.getLineCount() - 1));
            try {
                Files.writeString(
                        sidecarPath,
                        emptySidecar,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE
                );
            } catch (FileAlreadyExistsException ignored) {
                // Another editor or process created the same sidecar first.
            }
            VirtualFile sidecarFile = localFileSystem.refreshAndFindFileByNioFile(sidecarPath);
            if (sidecarFile != null) {
                sidecarFile.setCharset(StandardCharsets.UTF_8);
            }
            return sidecarFile;
        } catch (IOException exception) {
            NotificationGroupManager.getInstance()
                    .getNotificationGroup("Inlay Hints Extension")
                    .createNotification(
                            "Unable to create IHM file",
                            exception.getMessage() == null ? sidecarPath.toString() : exception.getMessage(),
                            NotificationType.ERROR
                    )
                    .notify(project);
            return null;
        }
    }

    private static String getSidecarLine(Document sidecarDocument, int line) {
        if (line < 0 || line >= sidecarDocument.getLineCount()) {
            return "";
        }
        return sidecarDocument.getText(new TextRange(
                sidecarDocument.getLineStartOffset(line),
                sidecarDocument.getLineEndOffset(line)
        )).strip();
    }

    private void updateHintLine(TrackedDocument tracked, int line, String value) {
        if (!tracked.sidecarFile.isValid() || !tracked.sidecarFile.isWritable()) {
            return;
        }
        String currentText = tracked.sidecarDocument.getText();
        String normalizedValue = value.strip();
        boolean deleteEmptyFile = InlayHintsExtensionSettings.getInstance()
                .isDeleteEmptyIhmFileAfterLastHintRemoved()
                && SidecarText.removesLastHint(currentText, line, normalizedValue);
        String updatedText = SidecarText.updateLine(
                currentText,
                line,
                normalizedValue,
                tracked.sourceDocument.getLineCount()
        );
        if (!currentText.equals(updatedText)) {
            replaceAndSaveDocument(tracked.sidecarDocument, updatedText);
        }
        if (deleteEmptyFile && deleteSidecarFile(tracked)) {
            scheduleRefreshOpenEditors();
            return;
        }
        render(tracked);
    }

    private boolean deleteSidecarFile(TrackedDocument tracked) {
        VirtualFile sidecarFile = tracked.sidecarFile;
        boolean deleteEmptyDirectories = InlayHintsExtensionSettings.getInstance()
                .isDeleteEmptyDirectoriesAfterIhmFileDeletion();
        Path sidecarRoot = deleteEmptyDirectories
                ? toSidecarRoot(Path.of(tracked.sourcePath), Path.of(sidecarFile.getPath()))
                : null;
        VirtualFile sidecarDirectory = sidecarFile.getParent();
        IOException[] fileFailure = new IOException[1];
        IOException[] directoryFailure = new IOException[1];
        ApplicationManager.getApplication().runWriteAction(() -> {
            try {
                if (sidecarFile.isValid()) {
                    sidecarFile.delete(this);
                }
            } catch (IOException exception) {
                fileFailure[0] = exception;
            }
            if (fileFailure[0] == null && !sidecarFile.isValid() && sidecarRoot != null) {
                try {
                    deleteEmptySidecarDirectories(sidecarDirectory, sidecarRoot);
                } catch (IOException exception) {
                    directoryFailure[0] = exception;
                }
            }
        });
        if (directoryFailure[0] != null) {
            NotificationGroupManager.getInstance()
                    .getNotificationGroup("Inlay Hints Extension")
                    .createNotification(
                            "Unable to delete empty IHM directories",
                            directoryFailure[0].getMessage() == null
                                    ? sidecarFile.getPath()
                                    : directoryFailure[0].getMessage(),
                            NotificationType.ERROR
                    )
                    .notify(project);
        }
        if (fileFailure[0] == null && !sidecarFile.isValid()) {
            return true;
        }

        String message = fileFailure[0] == null || fileFailure[0].getMessage() == null
                ? sidecarFile.getPath()
                : fileFailure[0].getMessage();
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Inlay Hints Extension")
                .createNotification(
                        "Unable to delete empty IHM file",
                        message,
                        NotificationType.ERROR
                )
                .notify(project);
        return false;
    }

    private void deleteEmptySidecarDirectories(@Nullable VirtualFile directory, @NotNull Path sidecarRoot)
            throws IOException {
        Path normalizedRoot = sidecarRoot.toAbsolutePath().normalize();
        VirtualFile current = directory;
        while (current != null && current.isValid()) {
            Path currentPath = Path.of(current.getPath()).toAbsolutePath().normalize();
            if (!currentPath.startsWith(normalizedRoot) || current.getChildren().length != 0) {
                return;
            }

            boolean deletingRoot = currentPath.equals(normalizedRoot);
            VirtualFile parent = current.getParent();
            current.delete(this);
            if (deletingRoot) {
                return;
            }
            current = parent;
        }
    }

    private static void replaceAndSaveDocument(Document document, String text) {
        ApplicationManager.getApplication().runWriteAction(() -> {
            document.setText(text);
            FileDocumentManager.getInstance().saveDocument(document);
        });
    }

    private @Nullable Path toSidecarRoot(@NotNull Path sourcePath, @NotNull Path sidecarPath) {
        return projectRoot == null
                ? null
                : SidecarPathMapper.toSidecarRoot(projectRoot, sourcePath, sidecarPath).orElse(null);
    }

    private static @Nullable Document getUtf8Document(
            FileDocumentManager fileDocumentManager,
            VirtualFile sidecarFile
    ) {
        Document cachedDocument = fileDocumentManager.getCachedDocument(sidecarFile);
        if (!StandardCharsets.UTF_8.equals(sidecarFile.getCharset())) {
            sidecarFile.setCharset(StandardCharsets.UTF_8, () -> {
                if (cachedDocument != null && !fileDocumentManager.isDocumentUnsaved(cachedDocument)) {
                    fileDocumentManager.reloadFromDisk(cachedDocument);
                }
            });
        }
        return fileDocumentManager.getDocument(sidecarFile);
    }

    private void showExcludeNoticeOnce() {
        PropertiesComponent properties = PropertiesComponent.getInstance(project);
        if (properties.getBoolean(EXCLUDE_NOTICE_KEY, false)) {
            return;
        }
        properties.setValue(EXCLUDE_NOTICE_KEY, true);
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Inlay Hints Extension")
                .createNotification(
                        "Exclude local inlay hint files from Git",
                        "Add /inlay-hints/ to .git/info/exclude to keep these notes out of commits.",
                        NotificationType.INFORMATION
                )
                .notify(project);
    }

    private final class TrackedDocument {
        private final Document sourceDocument;
        private final String sourcePath;
        private final VirtualFile sidecarFile;
        private final Document sidecarDocument;
        private final SidecarSyncHistory syncHistory = new SidecarSyncHistory();
        private final Disposable listenerDisposable = Disposer.newDisposable("Inlay Hints Extension document listeners");
        private final Set<EditorSession> sessions = new LinkedHashSet<>();
        private final Runnable syncRequest = () -> synchronize(this);
        private final Runnable renderRequest = () -> render(this);
        private String sourceSnapshot;
        private boolean clearModifiedLines;
        private boolean suppressSourceEvents;

        private TrackedDocument(
                Document sourceDocument,
                String sourcePath,
                VirtualFile sidecarFile,
                Document sidecarDocument
        ) {
            this.sourceDocument = sourceDocument;
            this.sourcePath = sourcePath;
            this.sidecarFile = sidecarFile;
            this.sidecarDocument = sidecarDocument;
            this.sourceSnapshot = sourceDocument.getText();
        }
    }

    private record ExternalSourceSnapshot(
            Path sidecarPath,
            String sourceTextBefore,
            boolean clearModifiedLines
    ) {
    }

    private record ExternalSourceChange(
            Path sidecarPath,
            String sourceTextBefore,
            String sourceTextAfter,
            boolean clearModifiedLines
    ) {
    }
}
