package cn.fj.loli.inlayhintsextension.settings;

import cn.fj.loli.inlayhintsextension.editor.InlayHintsExtensionService;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;

public final class InlayHintsExtensionConfigurable implements SearchableConfigurable {
    private JBCheckBox showHintsCheckBox;
    private JBCheckBox clearModifiedLinesCheckBox;
    private JBCheckBox deleteEmptyIhmFileCheckBox;
    private JBCheckBox deleteEmptyDirectoriesCheckBox;
    private JBTextField activationKeywordField;

    @Override
    public @NotNull String getId() {
        return "tools.inlay.hints.extension";
    }

    @Override
    public String getDisplayName() {
        return "Inlay Hints Extension";
    }

    @Override
    public @Nullable JComponent createComponent() {
        showHintsCheckBox = new JBCheckBox("Show Inlay Hints Extension");
        clearModifiedLinesCheckBox = new JBCheckBox(
                "Clear the corresponding hint when a source line is modified"
        );
        deleteEmptyIhmFileCheckBox = new JBCheckBox(
                "Delete the IHM file when inline editing removes its last hint"
        );
        deleteEmptyDirectoriesCheckBox = new JBCheckBox(
                "Recursively delete empty parent directories after deleting the IHM file"
        );
        deleteEmptyDirectoriesCheckBox.setBorder(JBUI.Borders.emptyLeft(24));
        deleteEmptyIhmFileCheckBox.addActionListener(event -> updateDeleteEmptyDirectoriesAvailability());
        activationKeywordField = new JBTextField();
        reset();
        return FormBuilder.createFormBuilder()
                .addComponent(showHintsCheckBox)
                .addComponent(clearModifiedLinesCheckBox)
                .addComponent(deleteEmptyIhmFileCheckBox)
                .addComponent(deleteEmptyDirectoriesCheckBox)
                .addLabeledComponent("Hint edit activation keyword:", activationKeywordField)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    @Override
    public boolean isModified() {
        if (showHintsCheckBox == null || clearModifiedLinesCheckBox == null
                || deleteEmptyIhmFileCheckBox == null || deleteEmptyDirectoriesCheckBox == null
                || activationKeywordField == null) {
            return false;
        }
        InlayHintsExtensionSettings settings = InlayHintsExtensionSettings.getInstance();
        return showHintsCheckBox.isSelected() != settings.isShowHints()
                || clearModifiedLinesCheckBox.isSelected() != settings.isClearModifiedLines()
                || deleteEmptyIhmFileCheckBox.isSelected()
                != settings.isDeleteEmptyIhmFileAfterLastHintRemoved()
                || deleteEmptyDirectoriesCheckBox.isSelected()
                != settings.isDeleteEmptyDirectoriesAfterIhmFileDeletion()
                || !activationKeywordField.getText().equals(settings.getActivationKeyword());
    }

    @Override
    public void apply() throws ConfigurationException {
        if (showHintsCheckBox == null || clearModifiedLinesCheckBox == null
                || deleteEmptyIhmFileCheckBox == null || deleteEmptyDirectoriesCheckBox == null
                || activationKeywordField == null) {
            return;
        }
        String activationKeyword = activationKeywordField.getText();
        if (activationKeyword.isBlank()) {
            throw new ConfigurationException("The hint edit activation keyword must contain a visible character.");
        }
        if (activationKeyword.indexOf('\n') >= 0 || activationKeyword.indexOf('\r') >= 0) {
            throw new ConfigurationException("The hint edit activation keyword must be a single line.");
        }
        InlayHintsExtensionSettings settings = InlayHintsExtensionSettings.getInstance();
        settings.setShowHints(showHintsCheckBox.isSelected());
        settings.setClearModifiedLines(clearModifiedLinesCheckBox.isSelected());
        settings.setDeleteEmptyIhmFileAfterLastHintRemoved(deleteEmptyIhmFileCheckBox.isSelected());
        settings.setDeleteEmptyDirectoriesAfterIhmFileDeletion(deleteEmptyDirectoriesCheckBox.isSelected());
        settings.setActivationKeyword(activationKeyword);
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            project.getService(InlayHintsExtensionService.class).settingsChanged();
        }
    }

    @Override
    public void reset() {
        if (showHintsCheckBox == null || clearModifiedLinesCheckBox == null
                || deleteEmptyIhmFileCheckBox == null || deleteEmptyDirectoriesCheckBox == null
                || activationKeywordField == null) {
            return;
        }
        InlayHintsExtensionSettings settings = InlayHintsExtensionSettings.getInstance();
        showHintsCheckBox.setSelected(settings.isShowHints());
        clearModifiedLinesCheckBox.setSelected(settings.isClearModifiedLines());
        deleteEmptyIhmFileCheckBox.setSelected(settings.isDeleteEmptyIhmFileAfterLastHintRemoved());
        deleteEmptyDirectoriesCheckBox.setSelected(settings.isDeleteEmptyDirectoriesAfterIhmFileDeletion());
        updateDeleteEmptyDirectoriesAvailability();
        activationKeywordField.setText(settings.getActivationKeyword());
    }

    private void updateDeleteEmptyDirectoriesAvailability() {
        if (deleteEmptyIhmFileCheckBox != null && deleteEmptyDirectoriesCheckBox != null) {
            deleteEmptyDirectoriesCheckBox.setEnabled(deleteEmptyIhmFileCheckBox.isSelected());
        }
    }

    @Override
    public void disposeUIResources() {
        showHintsCheckBox = null;
        clearModifiedLinesCheckBox = null;
        deleteEmptyIhmFileCheckBox = null;
        deleteEmptyDirectoriesCheckBox = null;
        activationKeywordField = null;
    }
}
