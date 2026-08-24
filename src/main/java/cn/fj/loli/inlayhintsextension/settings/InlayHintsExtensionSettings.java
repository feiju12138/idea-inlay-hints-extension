package cn.fj.loli.inlayhintsextension.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.APP)
@State(name = "InlayHintsExtensionSettings", storages = @Storage("inlayHintsExtension.xml"))
public final class InlayHintsExtensionSettings implements PersistentStateComponent<InlayHintsExtensionSettings.SettingsState> {
    public static final String DEFAULT_ACTIVATION_KEYWORD = " ^^ ";
    private static final String PREVIOUS_DEFAULT_ACTIVATION_KEYWORD = "^^ ";

    private SettingsState state = new SettingsState();

    public static InlayHintsExtensionSettings getInstance() {
        return ApplicationManager.getApplication().getService(InlayHintsExtensionSettings.class);
    }

    @Override
    public @NotNull SettingsState getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull SettingsState state) {
        if (PREVIOUS_DEFAULT_ACTIVATION_KEYWORD.equals(state.activationKeyword)) {
            state.activationKeyword = DEFAULT_ACTIVATION_KEYWORD;
        }
        this.state = state;
    }

    public boolean isShowHints() {
        return state.showHints;
    }

    public void setShowHints(boolean showHints) {
        state.showHints = showHints;
    }

    public boolean isClearModifiedLines() {
        return state.clearModifiedLines;
    }

    public void setClearModifiedLines(boolean clearModifiedLines) {
        state.clearModifiedLines = clearModifiedLines;
    }

    public boolean isDeleteEmptyIhmFileAfterLastHintRemoved() {
        return state.deleteEmptyIhmFileAfterLastHintRemoved;
    }

    public void setDeleteEmptyIhmFileAfterLastHintRemoved(boolean deleteEmptyIhmFileAfterLastHintRemoved) {
        state.deleteEmptyIhmFileAfterLastHintRemoved = deleteEmptyIhmFileAfterLastHintRemoved;
    }

    public boolean isDeleteEmptyDirectoriesAfterIhmFileDeletion() {
        return state.deleteEmptyDirectoriesAfterIhmFileDeletion;
    }

    public void setDeleteEmptyDirectoriesAfterIhmFileDeletion(boolean deleteEmptyDirectoriesAfterIhmFileDeletion) {
        state.deleteEmptyDirectoriesAfterIhmFileDeletion = deleteEmptyDirectoriesAfterIhmFileDeletion;
    }

    public @NotNull String getActivationKeyword() {
        String activationKeyword = state.activationKeyword;
        return activationKeyword == null || activationKeyword.isEmpty()
                ? DEFAULT_ACTIVATION_KEYWORD
                : activationKeyword;
    }

    public void setActivationKeyword(@NotNull String activationKeyword) {
        state.activationKeyword = activationKeyword;
    }

    public static final class SettingsState {
        public boolean showHints = true;
        public boolean clearModifiedLines;
        public boolean deleteEmptyIhmFileAfterLastHintRemoved;
        public boolean deleteEmptyDirectoriesAfterIhmFileDeletion;
        public String activationKeyword = DEFAULT_ACTIVATION_KEYWORD;
    }
}
