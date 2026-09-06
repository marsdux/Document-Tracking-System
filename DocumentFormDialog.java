package doctrack.ui;

import doctrack.dao.AuditLogDAO;
import doctrack.dao.DocumentDAO;
import doctrack.dao.DocumentTypeDAO;
import doctrack.dao.OfficeDAO;
import doctrack.dao.PersonnelDAO;
import doctrack.dao.RoutingDAO;
import doctrack.model.Document;
import doctrack.model.DocumentType;
import doctrack.model.Office;
import doctrack.model.Personnel;
import doctrack.model.RoutingStep;
import doctrack.model.User;
import doctrack.util.AppLogger;
import doctrack.util.UITheme;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class DocumentFormDialog extends JDialog {

    private final User currentUser;
    private final Document existing; // null = new document
    private boolean saved = false;

    private final JTextField trackingNoField = new JTextField(18);
    private final JComboBox<DocumentType> docTypeField = new JComboBox<>();
    private final JComboBox<String> directionField = new JComboBox<>(
            new String[]{"INCOMING", "OUTGOING", "INTERNAL"});
    private final JTextField subjectField = new JTextField(30);
    private final JTextField sourceField = new JTextField(25);
    private final JComboBox<Office> officeField = new JComboBox<>();
    private final JComboBox<Personnel> holderField = new JComboBox<>();
    private final JComboBox<String> statusField = new JComboBox<>(
            new String[]{"RECEIVED", "IN_PROGRESS", "FORWARDED", "COMPLIED", "CLOSED"});
    private final JComboBox<String> priorityField = new JComboBox<>(
            new String[]{"NORMAL", "HIGH", "URGENT"});
    private final JTextField dateReceivedField = new JTextField(12);
    private final JTextField dueDateField = new JTextField(12);
    private final JTextArea remarksField = new JTextArea(4, 30);

    private final DocumentDAO documentDAO = new DocumentDAO();
    private final OfficeDAO officeDAO = new OfficeDAO();
    private final PersonnelDAO personnelDAO = new PersonnelDAO();
    private final RoutingDAO routingDAO = new RoutingDAO();
    private final DocumentTypeDAO documentTypeDAO = new DocumentTypeDAO();
    private final AuditLogDAO auditLogDAO = new AuditLogDAO();

    /** The doc_type text on the document being edited, kept so populateFields() can restore it even if deactivated. */
    private String existingDocTypeText;

    public DocumentFormDialog(Frame owner, User currentUser, Document existing) {
        super(owner, existing == null ? "Add Document" : "View / Edit Document", true);
        this.currentUser = currentUser;
        this.existing = existing;
        this.existingDocTypeText = existing != null ? existing.getDocType() : null;
        buildUI();
        loadLookups();
        if (existing != null) populateFields();
        else {
            dateReceivedField.setText(LocalDate.now().toString());
            try {
                trackingNoField.setText(documentDAO.generateTrackingNo());
            } catch (SQLException ignored) { }
        }
        pack();
        setLocationRelativeTo(owner);
    }

    public boolean isSaved() { return saved; }

    private void buildUI() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(5, 5, 5, 5);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        row = addRow(form, gc, row, "Tracking No.:", trackingNoField);
        if (existing == null) {
            trackingNoField.setToolTipText("Auto-suggested — edit it if your office uses its own numbering.");
        } else {
            trackingNoField.setEditable(false); // tracking no. is fixed once a document exists
        }
        row = addRow(form, gc, row, "Document Type:", buildDocTypeRow());
        row = addRow(form, gc, row, "Direction:", directionField);
        row = addRow(form, gc, row, "Subject:", subjectField);
        row = addRow(form, gc, row, "Source / From:", sourceField);
        row = addRow(form, gc, row, "Current Office:", officeField);
        row = addRow(form, gc, row, "Current Holder:", holderField);
        row = addRow(form, gc, row, "Status:", statusField);
        row = addRow(form, gc, row, "Priority:", priorityField);
        row = addRow(form, gc, row, "Date Received (yyyy-mm-dd):", dateReceivedField);
        row = addRow(form, gc, row, "Due Date (yyyy-mm-dd):", dueDateField);

        gc.gridx = 0; gc.gridy = row; gc.anchor = GridBagConstraints.NORTHWEST;
        JLabel remarksLbl = new JLabel("Remarks:");
        remarksLbl.setFont(UITheme.FONT_LABEL);
        form.add(remarksLbl, gc);
        gc.gridx = 1;
        remarksField.setLineWrap(true);
        UITheme.enableTabToAdvanceFocus(remarksField);
        remarksField.setWrapStyleWord(true);
        form.add(new JScrollPane(remarksField), gc);

        officeField.addActionListener(e -> reloadPersonnelForSelectedOffice());

        JButton saveBtn = new JButton("Save");
        saveBtn.setFont(UITheme.FONT_LABEL);
        saveBtn.setBackground(UITheme.COLOR_PRIMARY);
        saveBtn.setForeground(UITheme.COLOR_PRIMARY_TXT);
        saveBtn.addActionListener(e -> save());
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancelBtn);
        buttons.add(saveBtn);

        JPanel root = new JPanel(new BorderLayout());
        root.add(form, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private int addRow(JPanel form, GridBagConstraints gc, int row, String label, JComponent field) {
        gc.gridx = 0; gc.gridy = row;
        JLabel lbl = new JLabel(label);
        lbl.setFont(UITheme.FONT_LABEL);
        form.add(lbl, gc);
        gc.gridx = 1;
        form.add(field, gc);
        return row + 1;
    }

    /** Document Type combo plus +/- buttons to manage the shared, editable type list right from this form. */
    private JPanel buildDocTypeRow() {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.add(docTypeField, BorderLayout.CENTER);

        JPanel toggles = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        JButton addBtn = new JButton("+");
        addBtn.setToolTipText("Add a new document type");
        addBtn.setMargin(new Insets(1, 6, 1, 6));
        addBtn.addActionListener(e -> addDocumentType());
        JButton removeBtn = new JButton("−");
        removeBtn.setToolTipText("Remove the selected document type");
        removeBtn.setMargin(new Insets(1, 6, 1, 6));
        removeBtn.addActionListener(e -> removeDocumentType());
        toggles.add(addBtn);
        toggles.add(removeBtn);
        row.add(toggles, BorderLayout.EAST);
        return row;
    }

    private void addDocumentType() {
        String name = JOptionPane.showInputDialog(this, "New document type name:");
        if (name == null || name.trim().isEmpty()) return;
        name = name.trim();
        try {
            documentTypeDAO.insert(name);
            auditLogDAO.log(null, currentUser.getUserId(), "CREATE", "Added document type: " + name);
            String finalName = name;
            loadDocumentTypes(finalName);
        } catch (SQLException ex) {
            AppLogger.logError("Failed to add document type", ex);
            String msg = ex.getMessage() != null ? ex.getMessage() : "";
            if (msg.toUpperCase().contains("UNIQUE")) {
                JOptionPane.showMessageDialog(this, "\"" + name + "\" already exists in the list.",
                        "Duplicate Type", JOptionPane.WARNING_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Failed to add document type: " + ex.getMessage());
            }
        }
    }

    private void removeDocumentType() {
        DocumentType selected = (DocumentType) docTypeField.getSelectedItem();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Select a document type to remove first.");
            return;
        }
        try {
            int usageCount = documentTypeDAO.countDocumentsUsingType(selected.getTypeName());
            String warning = usageCount > 0
                    ? "\"" + selected.getTypeName() + "\" is currently used by " + usageCount
                            + " existing document(s). They will keep this type; it will just no "
                            + "longer be offered for new or edited documents.\n\nRemove it anyway?"
                    : "Remove document type \"" + selected.getTypeName() + "\"?";
            int confirm = JOptionPane.showConfirmDialog(this, warning, "Confirm Remove", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;

            documentTypeDAO.deactivate(selected.getTypeId());
            auditLogDAO.log(null, currentUser.getUserId(), "DELETE",
                    "Removed document type: " + selected.getTypeName());
            loadDocumentTypes(null);
        } catch (SQLException ex) {
            AppLogger.logError("Failed to remove document type", ex);
            JOptionPane.showMessageDialog(this, "Failed to remove document type: " + ex.getMessage());
        }
    }

    /** Reloads the type combo from the DB, selecting preferSelection (or the existing document's type) if present. */
    private void loadDocumentTypes(String preferSelection) {
        String target = preferSelection != null ? preferSelection : existingDocTypeText;
        try {
            docTypeField.removeAllItems();
            List<DocumentType> types = documentTypeDAO.findActive();
            DocumentType toSelect = null;
            for (DocumentType t : types) {
                docTypeField.addItem(t);
                if (target != null && target.equals(t.getTypeName())) toSelect = t;
            }
            // If we're editing a document whose stored type text isn't in the
            // (possibly since-edited) active list, add a synthetic entry so
            // the field still shows what's actually saved instead of silently
            // changing it out from under the user.
            if (target != null && toSelect == null) {
                DocumentType synthetic = new DocumentType();
                synthetic.setTypeName(target);
                synthetic.setActive(false);
                docTypeField.addItem(synthetic);
                toSelect = synthetic;
            }
            if (toSelect != null) docTypeField.setSelectedItem(toSelect);
        } catch (SQLException ex) {
            AppLogger.logError("Failed to load document types", ex);
            JOptionPane.showMessageDialog(this, "Failed to load document types: " + ex.getMessage());
        }
    }

    private void loadLookups() {
        loadDocumentTypes(null);
        try {
            List<Office> offices = officeDAO.findActive();
            officeField.removeAllItems();
            for (Office o : offices) officeField.addItem(o);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load offices: " + ex.getMessage());
        }
        reloadPersonnelForSelectedOffice();
    }

    private void reloadPersonnelForSelectedOffice() {
        Office selected = (Office) officeField.getSelectedItem();
        holderField.removeAllItems();
        if (selected == null) return;
        try {
            List<Personnel> people = personnelDAO.findByOffice(selected.getOfficeId());
            for (Personnel p : people) holderField.addItem(p);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load personnel: " + ex.getMessage());
        }
    }

    private void populateFields() {
        trackingNoField.setText(existing.getTrackingNo());
        directionField.setSelectedItem(existing.getDirection());
        subjectField.setText(existing.getSubject());
        sourceField.setText(existing.getSourceName());
        statusField.setSelectedItem(existing.getStatus());
        priorityField.setSelectedItem(existing.getPriority());
        dateReceivedField.setText(existing.getDateReceived());
        dueDateField.setText(existing.getDueDate());
        remarksField.setText(existing.getRemarks());

        for (int i = 0; i < officeField.getItemCount(); i++) {
            if (officeField.getItemAt(i).getOfficeId() == (existing.getCurrentOfficeId() == null ? -1 : existing.getCurrentOfficeId())) {
                officeField.setSelectedIndex(i);
                break;
            }
        }
        reloadPersonnelForSelectedOffice();
        if (existing.getCurrentHolderId() != null) {
            for (int i = 0; i < holderField.getItemCount(); i++) {
                if (holderField.getItemAt(i).getPersonnelId() == existing.getCurrentHolderId()) {
                    holderField.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    private void save() {
        if (subjectField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Subject is required.");
            return;
        }
        if (existing == null && trackingNoField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Tracking No. is required.");
            return;
        }
        if (!isValidDateOrEmpty(dateReceivedField.getText().trim())) {
            JOptionPane.showMessageDialog(this, "Date Received must be a valid date in yyyy-mm-dd format.");
            return;
        }
        if (!isValidDateOrEmpty(dueDateField.getText().trim())) {
            JOptionPane.showMessageDialog(this, "Due Date must be a valid date in yyyy-mm-dd format, or left blank.");
            return;
        }
        Office office = (Office) officeField.getSelectedItem();
        Personnel holder = (Personnel) holderField.getSelectedItem();
        DocumentType docType = (DocumentType) docTypeField.getSelectedItem();
        if (docType == null) {
            JOptionPane.showMessageDialog(this, "Select a document type (or add one with the + button).");
            return;
        }

        try {
            Document d = (existing != null) ? existing : new Document();
            boolean isNew = (existing == null);
            if (isNew) d.setTrackingNo(trackingNoField.getText().trim());
            d.setDocType(docType.getTypeName());
            d.setDirection((String) directionField.getSelectedItem());
            d.setSubject(subjectField.getText().trim());
            d.setSourceName(sourceField.getText().trim());
            d.setCurrentOfficeId(office != null ? office.getOfficeId() : null);
            d.setCurrentHolderId(holder != null ? holder.getPersonnelId() : null);
            d.setStatus((String) statusField.getSelectedItem());
            d.setPriority((String) priorityField.getSelectedItem());
            d.setDateReceived(dateReceivedField.getText().trim());
            d.setDueDate(dueDateField.getText().trim());
            d.setRemarks(remarksField.getText().trim());
            if ("CLOSED".equals(d.getStatus()) && (d.getDateClosed() == null || d.getDateClosed().isEmpty())) {
                d.setDateClosed(LocalDate.now().toString());
            }

            if (isNew) {
                d.setCreatedBy(currentUser.getUserId());
                int newId = documentDAO.insert(d);
                d.setDocumentId(newId);
                // log the initial routing step so the trail starts at creation
                RoutingStep step = new RoutingStep();
                step.setDocumentId(newId);
                step.setBatchId(routingDAO.newBatchId());
                step.setToOfficeId(d.getCurrentOfficeId());
                step.setToPersonnelId(d.getCurrentHolderId());
                step.setAction("RECEIVED");
                step.setRemarks("Document logged into the system.");
                step.setLoggedBy(currentUser.getUserId());
                routingDAO.insert(step);
                auditLogDAO.log(newId, currentUser.getUserId(), "CREATE",
                        "Created document " + d.getTrackingNo());
            } else {
                documentDAO.update(d);
                auditLogDAO.log(d.getDocumentId(), currentUser.getUserId(), "EDIT",
                        "Edited document " + d.getTrackingNo());
            }
            saved = true;
            dispose();
        } catch (SQLException ex) {
            AppLogger.logError("Failed to save document", ex);
            String msg = ex.getMessage() != null ? ex.getMessage() : "";
            if (msg.toUpperCase().contains("UNIQUE") && msg.toLowerCase().contains("tracking_no")) {
                JOptionPane.showMessageDialog(this,
                        "Tracking No. \"" + trackingNoField.getText().trim() + "\" is already in use. "
                                + "Please choose a different tracking number.",
                        "Duplicate Tracking No.", JOptionPane.ERROR_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Save failed: " + ex.getMessage(),
                        "Database Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private boolean isValidDateOrEmpty(String text) {
        if (text == null || text.isEmpty()) return true;
        try {
            LocalDate.parse(text);
            return true;
        } catch (java.time.format.DateTimeParseException ex) {
            return false;
        }
    }
}
