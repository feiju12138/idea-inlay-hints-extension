package cn.fj.loli.inlayhintsextension.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.NotNull;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class EditorSession implements Disposable {
    private final Editor editor;
    private final VirtualFile sidecarFile;
    private final Document sidecarDocument;
    private final List<Inlay<?>> inlays = new ArrayList<>();
    private JBTextField hintEditor;
    private int editedLine = -1;
    private Consumer<String> commitHandler;
    private Runnable cancelHandler;
    private final EditorMouseListener mouseListener = new EditorMouseListener() {
        @Override
        public void mouseClicked(@NotNull EditorMouseEvent event) {
            MouseEvent mouseEvent = event.getMouseEvent();
            if (!InlayClickGesture.isNavigationClick(mouseEvent) || event.getInlay() == null) {
                return;
            }
            if (event.getInlay().getRenderer() instanceof InlayHintRenderer renderer) {
                renderer.navigate();
                event.consume();
            }
        }
    };

    EditorSession(Editor editor, VirtualFile sidecarFile, Document sidecarDocument) {
        this.editor = editor;
        this.sidecarFile = sidecarFile;
        this.sidecarDocument = sidecarDocument;
        editor.addEditorMouseListener(mouseListener);
    }

    boolean matchesSidecar(Path expectedPath) {
        return sidecarFile.isValid() && Path.of(sidecarFile.getPath()).equals(expectedPath);
    }

    void render(boolean showHints) {
        clearInlays();
        if (!showHints || editor.isDisposed() || !sidecarFile.isValid()) {
            return;
        }

        Document sourceDocument = editor.getDocument();
        String[] hints = sidecarDocument.getText().split("\\n", -1);
        int renderedLineCount = Math.min(sourceDocument.getLineCount(), hints.length);
        for (int line = 0; line < renderedLineCount; line++) {
            if (line == editedLine) {
                continue;
            }
            String hint = hints[line].strip();
            if (hint.isEmpty()) {
                continue;
            }
            int offset = sourceDocument.getLineEndOffset(line);
            InlayHintRenderer renderer = new InlayHintRenderer(editor.getProject(), sidecarFile, line, hint);
            Inlay<InlayHintRenderer> inlay = editor.getInlayModel().addInlineElement(offset, true, renderer);
            if (inlay != null) {
                inlays.add(inlay);
            }
        }
    }

    boolean startHintEditing(
            int sourceLine,
            String initialText,
            Consumer<String> commitHandler,
            Runnable cancelHandler
    ) {
        if (editor.isDisposed() || hintEditor != null || sourceLine < 0
                || sourceLine >= editor.getDocument().getLineCount()) {
            return false;
        }

        this.editedLine = sourceLine;
        this.commitHandler = commitHandler;
        this.cancelHandler = cancelHandler;
        removeInlayAtLine(sourceLine);

        JBTextField field = new JBTextField(initialText);
        field.setForeground(Color.BLACK);
        field.setBackground(Color.WHITE);
        Font editorFont = editor.getColorsScheme().getFont(com.intellij.openapi.editor.colors.EditorFontType.PLAIN);
        editorFont = new Font(Font.DIALOG, editorFont.getStyle(), editorFont.getSize())
                .deriveFont(editorFont.getSize2D());
        field.setFont(editorFont);
        field.addActionListener(event -> finishHintEditing(true, true));
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                finishHintEditing(true, false);
            }
        });
        field.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                "cancelHintEditing"
        );
        field.getActionMap().put("cancelHintEditing", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                finishHintEditing(false, true);
            }
        });

        JComponent contentComponent = editor.getContentComponent();
        int lineEndOffset = editor.getDocument().getLineEndOffset(sourceLine);
        Point position = editor.offsetToXY(lineEndOffset);
        FontMetrics metrics = contentComponent.getFontMetrics(editorFont);
        int preferredWidth = Math.max(180, Math.min(520, metrics.stringWidth(initialText) + 48));
        Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
        int x = position.x + 4;
        int availableWidth = visibleArea.x + visibleArea.width - x - 8;
        int width = Math.max(120, Math.min(preferredWidth, Math.max(120, availableWidth)));
        int height = Math.max(editor.getLineHeight(), field.getPreferredSize().height);
        field.setBounds(x, position.y, width, height);

        hintEditor = field;
        contentComponent.add(field);
        contentComponent.setComponentZOrder(field, 0);
        contentComponent.revalidate();
        contentComponent.repaint();
        field.setCaretPosition(field.getText().length());
        SwingUtilities.invokeLater(() -> {
            if (hintEditor == field && field.isDisplayable()) {
                field.requestFocusInWindow();
            }
        });
        return true;
    }

    @Override
    public void dispose() {
        cancelHandler = null;
        finishHintEditing(false, false);
        clearInlays();
        if (!editor.isDisposed()) {
            editor.removeEditorMouseListener(mouseListener);
        }
    }

    private void clearInlays() {
        for (Inlay<?> inlay : inlays) {
            if (inlay.isValid()) {
                inlay.dispose();
            }
        }
        inlays.clear();
    }

    private void removeInlayAtLine(int sourceLine) {
        inlays.removeIf(inlay -> {
            if (!(inlay.getRenderer() instanceof InlayHintRenderer renderer)
                    || renderer.getSidecarLine() != sourceLine) {
                return false;
            }
            if (inlay.isValid()) {
                inlay.dispose();
            }
            return true;
        });
    }

    private void finishHintEditing(boolean commit, boolean restoreSourceFocus) {
        JBTextField field = hintEditor;
        if (field == null) {
            return;
        }

        String text = field.getText();
        int sourceLine = editedLine;
        hintEditor = null;
        editedLine = -1;
        Consumer<String> handler = commitHandler;
        commitHandler = null;
        Runnable cancellation = cancelHandler;
        cancelHandler = null;
        JComponent parent = (JComponent) field.getParent();
        if (parent != null) {
            parent.remove(field);
            parent.revalidate();
            parent.repaint();
        }
        try {
            if (commit && handler != null) {
                handler.accept(text);
            } else if (!commit && cancellation != null) {
                cancellation.run();
            }
        } finally {
            if (restoreSourceFocus) {
                restoreSourceFocus(sourceLine);
            }
        }
    }

    private void restoreSourceFocus(int sourceLine) {
        SwingUtilities.invokeLater(() -> {
            if (editor.isDisposed()) {
                return;
            }
            Document sourceDocument = editor.getDocument();
            int targetLine = Math.max(0, Math.min(sourceLine, sourceDocument.getLineCount() - 1));
            editor.getCaretModel().moveToOffset(sourceDocument.getLineEndOffset(targetLine));
            editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
            editor.getContentComponent().requestFocusInWindow();
        });
    }
}
