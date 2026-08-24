package cn.fj.loli.inlayhintsextension.editor;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Rectangle;

final class InlayHintRenderer implements EditorCustomElementRenderer {
    private static final String PREFIX = "  ";

    private final Project project;
    private final VirtualFile sidecarFile;
    private final int sidecarLine;
    private final String displayText;

    InlayHintRenderer(Project project, VirtualFile sidecarFile, int sidecarLine, String text) {
        this.project = project;
        this.sidecarFile = sidecarFile;
        this.sidecarLine = sidecarLine;
        this.displayText = PREFIX + text;
    }

    @Override
    public int calcWidthInPixels(@NotNull Inlay inlay) {
        Editor editor = inlay.getEditor();
        Font font = getDisplayFont(editor);
        return editor.getContentComponent().getFontMetrics(font).stringWidth(displayText);
    }

    @Override
    public void paint(
            @NotNull Inlay inlay,
            @NotNull Graphics graphics,
            @NotNull Rectangle targetRegion,
            @NotNull TextAttributes textAttributes
    ) {
        Editor editor = inlay.getEditor();
        Font font = getDisplayFont(editor);
        FontMetrics metrics = editor.getContentComponent().getFontMetrics(font);
        TextAttributes hintAttributes = editor.getColorsScheme()
                .getAttributes(DefaultLanguageHighlighterColors.INLAY_TEXT_WITHOUT_BACKGROUND);
        Color foreground = hintAttributes == null ? null : hintAttributes.getForegroundColor();

        graphics.setFont(font);
        graphics.setColor(foreground != null ? foreground : editor.getColorsScheme().getDefaultForeground());
        int baseline = targetRegion.y + (targetRegion.height - metrics.getHeight()) / 2 + metrics.getAscent();
        graphics.drawString(displayText, targetRegion.x, baseline);
    }

    private Font getDisplayFont(Editor editor) {
        Font editorFont = editor.getColorsScheme().getFont(EditorFontType.PLAIN);
        if (editorFont.canDisplayUpTo(displayText) < 0) {
            return editorFont;
        }
        return new Font(Font.DIALOG, editorFont.getStyle(), editorFont.getSize())
                .deriveFont(editorFont.getSize2D());
    }

    void navigate() {
        if (!project.isDisposed() && sidecarFile.isValid()) {
            new OpenFileDescriptor(project, sidecarFile, sidecarLine, 0).navigate(true);
        }
    }

    int getSidecarLine() {
        return sidecarLine;
    }
}
