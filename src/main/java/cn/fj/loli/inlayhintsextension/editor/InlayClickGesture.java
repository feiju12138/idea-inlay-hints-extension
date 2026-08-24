package cn.fj.loli.inlayhintsextension.editor;

import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseEvent;

final class InlayClickGesture {
    private InlayClickGesture() {
    }

    static boolean isNavigationClick(@NotNull MouseEvent event) {
        return event.getButton() == MouseEvent.BUTTON1 && event.isControlDown();
    }
}
