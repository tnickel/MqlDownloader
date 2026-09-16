package gui;

import java.awt.Color;
import java.awt.Component;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Keeps invalid input editable instead of committing a value the database cannot store. */
final class RiskCellEditor extends DefaultCellEditor {
    private static final long serialVersionUID = 1L;
    private static final int MAX_LENGTH = 200;
    private static final String LENGTH_HINT = "Maximal 200 Zeichen.";

    private final JTextField textField;
    private final Border normalBorder;
    private final Border invalidBorder = BorderFactory.createLineBorder(new Color(190, 30, 30));
    private boolean editing;
    private String validationMessage;
    private Consumer<String> validationListener;

    RiskCellEditor() {
        super(new JTextField());
        textField = (JTextField) getComponent();
        normalBorder = textField.getBorder();
        setClickCountToStart(2);
        textField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                updateValidation();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                updateValidation();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                updateValidation();
            }
        });
        updateValidation();
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean selected,
                                                int row, int column) {
        editing = true;
        Component component = super.getTableCellEditorComponent(table, value, selected, row, column);
        updateValidation();
        return component;
    }

    private boolean hasValidLength() {
        return textField.getText().length() <= MAX_LENGTH;
    }

    private void updateValidation() {
        boolean valid = !editing || hasValidLength();
        textField.setBorder(valid ? normalBorder : invalidBorder);
        String hint = valid ? LENGTH_HINT
                : "Risiko zu lang (" + textField.getText().length()
                        + "/200 Zeichen) \u2013 bitte k\u00fcrzen oder mit Escape verwerfen.";
        textField.setToolTipText(hint);
        textField.getAccessibleContext().setAccessibleDescription(hint);
        String message = valid ? null : hint;
        if (!Objects.equals(validationMessage, message)) {
            validationMessage = message;
            if (validationListener != null) {
                validationListener.accept(message);
            }
        }
    }

    @Override
    public boolean stopCellEditing() {
        if (!hasValidLength()) {
            updateValidation();
            textField.requestFocusInWindow();
            return false;
        }
        editing = false;
        updateValidation();
        return super.stopCellEditing();
    }

    @Override
    public void cancelCellEditing() {
        editing = false;
        updateValidation();
        super.cancelCellEditing();
    }

    String getValidationMessage() {
        return validationMessage;
    }

    void setValidationListener(Consumer<String> listener) {
        validationListener = listener;
        if (listener != null) {
            listener.accept(validationMessage);
        }
    }

    /** Call on the EDT before an action that would discard the active table editor. */
    static boolean commitEditing(JTable table) {
        return !table.isEditing() || table.getCellEditor().stopCellEditing();
    }
}
