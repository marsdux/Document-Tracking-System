package doctrack.ui;

import doctrack.dao.AttachmentDAO;
import doctrack.dao.AuditLogDAO;
import doctrack.dao.DocumentDAO;
import doctrack.dao.OfficeDAO;
import doctrack.dao.PersonnelDAO;
import doctrack.dao.RoutingDAO;
import doctrack.model.*;
import doctrack.util.AppLogger;
import doctrack.util.AttachmentFileUtil;
import doctrack.util.AuditReportPrinter;
import doctrack.util.UITheme;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RoutingDialog extends JDialog {

    private final User currentUser;
    private final Document document;

    private final RoutingDAO routingDAO = new RoutingDAO();
    private final OfficeDAO officeDAO = new OfficeDAO();
    private final PersonnelDAO personnelDAO = new PersonnelDAO();
    private final DocumentDAO documentDAO = new DocumentDAO();
    private final AttachmentDAO attachmentDAO = new AttachmentDAO();
    private final AuditLogDAO auditLogDAO = new AuditLogDAO();

    private final DefaultTableModel historyModel = new DefaultTableModel(
            new String[]{"Date/Time", "Action", "To Office", "To Personnel", "Remarks", "Logged By", "Evidence"}, 0) {
        @Override public boolean isCellEditable(int row, int col) { return false; }
    };
    private final JTable historyTable = new JTable(historyModel);
    private final JLabel currentlyWithLabel = new JLabel(" ");

    private final JComboBox<String> actionField = new JComboBox<>(
            new String[]{"FORWARDED", "REVIEWED", "COMPLIED", "RETURNED", "CLOSED"});
    private final JList<RecipientEntry> recipientList = new JList<>();
    private final JTextArea remarksField = new JTextArea(3, 25);
    private final JLabel evidenceFileLabel = new JLabel("(none selected)");
    private File pendingEvidenceFile;

    private List<RoutingStep> lastLoadedHistory = new ArrayList<>();

    public RoutingDialog(Frame owner, User currentUser, Document document) {
        super(owner, "Tracking / Routing History - " + document.getTrackingNo(), true);
        this.currentUser = currentUser;
        this.document = document;
        buildUI();
        loadHistory();
        loadRecipients();
        setSize(850, 680);
        setLocationRelativeTo(owner);
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel headerPanel = new JPanel(new GridLayout(2, 1));
        JLabel header = new JLabel(document.getTrackingNo() + " — " + document.getSubject());
        header.setFont(UITheme.FONT_HEADER);
        headerPanel.add(header);
        currentlyWithLabel.setFont(UITheme.FONT_LABEL);
        currentlyWithLabel.setForeground(UITheme.COLOR_PRIMARY);
        headerPanel.add(currentlyWithLabel);
        root.add(headerPanel, BorderLayout.NORTH);

        historyTable.setFont(UITheme.FONT_TABLE);
        historyTable.getTableHeader().setFont(UITheme.FONT_LABEL);
        historyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JPanel centerPanel = new JPanel(new BorderLayout(6, 6));
        centerPanel.add(new JScrollPane(historyTable), BorderLayout.CENTER);
        JButton editEntryBtn = new JButton("Edit Selected Entry");
        editEntryBtn.addActionListener(e -> editSelectedHistoryEntry());
        JButton deleteEntryBtn = new JButton("Delete Selected Entry");
        deleteEntryBtn.addActionListener(e -> deleteSelectedHistoryEntry());
        JButton evidenceBtn = new JButton("View/Manage Evidence for Selected");
        evidenceBtn.addActionListener(e -> manageEvidenceForSelected());
        JButton printBtn = new JButton("Print Full Audit Trail");
        printBtn.addActionListener(e -> printAuditTrail());
        JPanel printBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        printBar.add(editEntryBtn);
        printBar.add(deleteEntryBtn);
        printBar.add(evidenceBtn);
        printBar.add(printBtn);
        centerPanel.add(printBar, BorderLayout.SOUTH);
        root.add(centerPanel, BorderLayout.CENTER);

        JPanel forwardPanel = new JPanel(new BorderLayout(8, 8));
        forwardPanel.setBorder(BorderFactory.createTitledBorder(
                "Log New Action / Forward to One or More Recipients"));

        JPanel topRow = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.gridx = 0; gc.gridy = 0;
        topRow.add(new JLabel("Action:"), gc);
        gc.gridx = 1;
        topRow.add(actionField, gc);
        forwardPanel.add(topRow, BorderLayout.NORTH);

        recipientList.setVisibleRowCount(6);
        recipientList.setFont(UITheme.FONT_TABLE);
        JScrollPane recipientScroll = new JScrollPane(recipientList);
        recipientScroll.setBorder(BorderFactory.createTitledBorder(
                "Recipients (Ctrl/Shift+Click to select multiple offices and/or people)"));
        forwardPanel.add(recipientScroll, BorderLayout.CENTER);

        JPanel bottomRow = new JPanel(new BorderLayout(6, 6));
        remarksField.setLineWrap(true);
        doctrack.util.UITheme.enableTabToAdvanceFocus(remarksField);
        remarksField.setWrapStyleWord(true);
        JPanel remarksWrap = new JPanel(new BorderLayout());
        remarksWrap.setBorder(BorderFactory.createTitledBorder("Remarks"));
        remarksWrap.add(new JScrollPane(remarksField), BorderLayout.CENTER);
        bottomRow.add(remarksWrap, BorderLayout.CENTER);

        JPanel evidenceRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton chooseEvidenceBtn = new JButton("Attach Evidence (optional)...");
        chooseEvidenceBtn.addActionListener(e -> chooseEvidenceFile());
        JButton clearEvidenceBtn = new JButton("Clear");
        clearEvidenceBtn.addActionListener(e -> {
            pendingEvidenceFile = null;
            evidenceFileLabel.setText("(none selected)");
        });
        evidenceRow.add(chooseEvidenceBtn);
        evidenceRow.add(evidenceFileLabel);
        evidenceRow.add(clearEvidenceBtn);

        JPanel bottomStack = new JPanel(new BorderLayout());
        bottomStack.add(bottomRow, BorderLayout.CENTER);
        bottomStack.add(evidenceRow, BorderLayout.SOUTH);

        JButton logBtn = new JButton("Log Action / Send");
        logBtn.setFont(UITheme.FONT_LABEL);
        logBtn.setBackground(UITheme.COLOR_PRIMARY);
        UITheme.styleActionButton(logBtn);
        logBtn.addActionListener(e -> logAction());
        JPanel btnWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnWrap.add(logBtn);
        bottomStack.add(btnWrap, BorderLayout.NORTH);

        forwardPanel.add(bottomStack, BorderLayout.SOUTH);
        root.add(forwardPanel, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private void chooseEvidenceFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select scanned file as proof of this action (image or PDF)");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            pendingEvidenceFile = chooser.getSelectedFile();
            evidenceFileLabel.setText(pendingEvidenceFile.getName());
        }
    }

    private void loadHistory() {
        try {
            lastLoadedHistory = routingDAO.findByDocument(document.getDocumentId());
            List<Integer> stepIds = new ArrayList<>();
            for (RoutingStep s : lastLoadedHistory) stepIds.add(s.getStepId());
            Map<Integer, Integer> evidenceCounts = attachmentDAO.countByRoutingSteps(stepIds);

            historyModel.setRowCount(0);
            for (RoutingStep s : lastLoadedHistory) {
                Integer count = evidenceCounts.get(s.getStepId());
                String evidenceLabel = (count == null || count == 0) ? "—"
                        : count + " file" + (count == 1 ? "" : "s");
                historyModel.addRow(new Object[]{
                        s.getActionDate(), s.getAction(), s.getToOfficeName(),
                        s.getToPersonnelName(), s.getRemarks(), s.getLoggedByName(), evidenceLabel
                });
            }

            List<RoutingStep> current = routingDAO.findCurrentRecipients(document.getDocumentId());
            if (current.size() <= 1) {
                currentlyWithLabel.setText("Currently with: "
                        + (document.getCurrentHolderName() != null ? document.getCurrentHolderName()
                        : (document.getCurrentOfficeName() != null ? document.getCurrentOfficeName() : "—")));
            } else {
                StringBuilder names = new StringBuilder();
                for (RoutingStep s : current) {
                    if (names.length() > 0) names.append("; ");
                    names.append(s.getToPersonnelName() != null ? s.getToPersonnelName() : s.getToOfficeName());
                }
                currentlyWithLabel.setText("Currently distributed to " + current.size() + ": " + names);
            }
        } catch (SQLException ex) {
            AppLogger.logError("Failed to load routing history for document " + document.getDocumentId(), ex);
            JOptionPane.showMessageDialog(this, "Failed to load history: " + ex.getMessage());
        }
    }

    private void manageEvidenceForSelected() {
        RoutingStep step = getSelectedHistoryEntry();
        if (step == null) {
            JOptionPane.showMessageDialog(this, "Select a history entry first.");
            return;
        }
        AttachmentDialog dlg = new AttachmentDialog(
                (Frame) getOwner(), currentUser, document, step);
        dlg.setVisible(true);
        loadHistory(); // refresh evidence counts in case files were added/removed
    }

    private void loadRecipients() {
        try {
            List<RecipientEntry> entries = RecipientEntry.loadAll(officeDAO, personnelDAO);
            recipientList.setListData(entries.toArray(new RecipientEntry[0]));
        } catch (SQLException ex) {
            AppLogger.logError("Failed to load recipients", ex);
            JOptionPane.showMessageDialog(this, "Failed to load offices/personnel: " + ex.getMessage());
        }
    }

    private void logAction() {
        List<RecipientEntry> selected = recipientList.getSelectedValuesList();
        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select at least one recipient (office and/or person).");
            return;
        }
        String action = (String) actionField.getSelectedItem();
        String remarks = remarksField.getText().trim();

        try {
            String batchId = routingDAO.newBatchId();
            List<RoutingStep> steps = new ArrayList<>();
            for (RecipientEntry r : selected) {
                RoutingStep step = new RoutingStep();
                step.setDocumentId(document.getDocumentId());
                step.setBatchId(batchId);
                step.setFromOfficeId(document.getCurrentOfficeId());
                step.setToOfficeId(r.officeId);
                step.setToPersonnelId(r.personnelId);
                step.setAction(action);
                step.setRemarks(remarks);
                step.setLoggedBy(currentUser.getUserId());
                steps.add(step);
            }
            routingDAO.insertBatch(steps);
            new doctrack.dao.AuditLogDAO().log(document.getDocumentId(), currentUser.getUserId(), "ROUTE",
                    action + " to " + selected.size() + " recipient(s): " + remarks);

            if (pendingEvidenceFile != null) {
                attachEvidenceToBatch(batchId, pendingEvidenceFile);
            }

            // Update the document's "primary" current pointer to the first recipient
            // for backward-compatible single-holder display; the full distribution
            // list for this send remains queryable via findCurrentRecipients().
            RecipientEntry primary = selected.get(0);
            document.setCurrentOfficeId(primary.officeId != null ? primary.officeId : document.getCurrentOfficeId());
            document.setCurrentHolderId(primary.personnelId);
            String newStatus = statusForAction(action);
            if (newStatus != null) document.setStatus(newStatus);
            if ("CLOSED".equals(newStatus)) {
                document.setDateClosed(java.time.LocalDate.now().toString());
            }
            documentDAO.update(document);

            remarksField.setText("");
            recipientList.clearSelection();
            pendingEvidenceFile = null;
            evidenceFileLabel.setText("(none selected)");
            loadHistory();
            JOptionPane.showMessageDialog(this, "Logged " + action + " to " + selected.size()
                    + " recipient" + (selected.size() > 1 ? "s" : "") + ".");
        } catch (SQLException ex) {
            AppLogger.logError("Failed to log routing action for document " + document.getDocumentId(), ex);
            JOptionPane.showMessageDialog(this, "Failed to log action: " + ex.getMessage());
        }
    }

    /**
     * Attaches one evidence file to every routing_step row created by a
     * batch send (e.g. one proof-of-forwarding scan for a document sent to
     * several recipients at once). Each row gets its own independent
     * physical copy rather than sharing one file on disk, so removing the
     * evidence from any single recipient's entry later can never affect
     * another recipient's copy.
     */
    private void attachEvidenceToBatch(String batchId, File evidenceFile) {
        try {
            List<RoutingStep> createdSteps = routingDAO.findByBatchId(batchId);
            for (RoutingStep created : createdSteps) {
                AttachmentFileUtil.Result copied = AttachmentFileUtil.copyIntoDocumentFolder(
                        document.getDocumentId(), evidenceFile);
                Attachment a = new Attachment();
                a.setDocumentId(document.getDocumentId());
                a.setRoutingStepId(created.getStepId());
                a.setFileName(copied.storedFileName);
                a.setFilePath(copied.storedPath);
                a.setDescription("Evidence attached at time of logging");
                a.setUploadedBy(currentUser.getUserId());
                attachmentDAO.insert(a);
            }
            auditLogDAO.log(document.getDocumentId(), currentUser.getUserId(), "ATTACH",
                    "Attached evidence file \"" + evidenceFile.getName() + "\" to " + createdSteps.size()
                            + " routing entr" + (createdSteps.size() == 1 ? "y" : "ies") + " in batch " + batchId);
        } catch (IOException | SQLException ex) {
            AppLogger.logError("Failed to attach evidence to batch " + batchId, ex);
            JOptionPane.showMessageDialog(this, "Action was logged, but attaching the evidence file failed: "
                    + ex.getMessage() + "\n\nYou can attach it afterward via \"View/Manage Evidence\".");
        }
    }

    /** Returns the selected row's underlying RoutingStep, or null if nothing (valid) is selected. */
    private RoutingStep getSelectedHistoryEntry() {
        int row = historyTable.getSelectedRow();
        if (row < 0 || row >= lastLoadedHistory.size()) return null;
        return lastLoadedHistory.get(row);
    }

    private void editSelectedHistoryEntry() {
        RoutingStep step = getSelectedHistoryEntry();
        if (step == null) {
            JOptionPane.showMessageDialog(this, "Select a history entry first.");
            return;
        }

        JComboBox<String> editAction = new JComboBox<>(
                new String[]{"RECEIVED", "FORWARDED", "REVIEWED", "COMPLIED", "RETURNED", "CLOSED"});
        editAction.setSelectedItem(step.getAction());

        JComboBox<RecipientEntry> editRecipient = new JComboBox<>();
        List<RecipientEntry> recipientOptions;
        try {
            recipientOptions = RecipientEntry.loadAll(officeDAO, personnelDAO);
        } catch (SQLException ex) {
            AppLogger.logError("Failed to load recipients for history edit", ex);
            JOptionPane.showMessageDialog(this, "Failed to load offices/personnel: " + ex.getMessage());
            return;
        }
        for (RecipientEntry r : recipientOptions) {
            editRecipient.addItem(r);
            boolean matches = step.getToPersonnelId() != null
                    ? step.getToPersonnelId().equals(r.personnelId)
                    : (r.personnelId == null && step.getToOfficeId() != null && step.getToOfficeId().equals(r.officeId));
            if (matches) editRecipient.setSelectedItem(r);
        }

        JTextArea editRemarks = new JTextArea(step.getRemarks(), 4, 30);
        editRemarks.setLineWrap(true);
        editRemarks.setWrapStyleWord(true);
        UITheme.enableTabToAdvanceFocus(editRemarks);

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        JPanel top = new JPanel(new GridLayout(2, 2, 6, 6));
        top.add(new JLabel("Action:"));
        top.add(editAction);
        top.add(new JLabel("Recipient:"));
        top.add(editRecipient);
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(editRemarks), BorderLayout.CENTER);
        panel.add(new JLabel("Correcting an entry logged " + step.getActionDate()
                + " by " + (step.getLoggedByName() != null ? step.getLoggedByName() : "—")
                + ". The original date/time is kept."), BorderLayout.SOUTH);

        int result = JOptionPane.showConfirmDialog(this, panel, "Edit Routing Entry",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        String oldSummary = step.getAction() + " -> " + safeName(step.getToPersonnelName(), step.getToOfficeName());
        RecipientEntry chosen = (RecipientEntry) editRecipient.getSelectedItem();
        step.setAction((String) editAction.getSelectedItem());
        step.setToOfficeId(chosen != null ? chosen.officeId : null);
        step.setToPersonnelId(chosen != null ? chosen.personnelId : null);
        step.setRemarks(editRemarks.getText().trim());

        try {
            routingDAO.update(step);
            String newSummary = step.getAction() + " -> "
                    + safeName(chosen != null ? nameOf(chosen) : null, null);
            new doctrack.dao.AuditLogDAO().log(document.getDocumentId(), currentUser.getUserId(), "EDIT",
                    "Corrected routing entry #" + step.getStepId() + ": [" + oldSummary + "] -> [" + newSummary + "]");
            recomputeDocumentCurrentState();
            loadHistory();
        } catch (SQLException ex) {
            AppLogger.logError("Failed to update routing step " + step.getStepId(), ex);
            JOptionPane.showMessageDialog(this, "Failed to save correction: " + ex.getMessage());
        }
    }

    private void deleteSelectedHistoryEntry() {
        RoutingStep step = getSelectedHistoryEntry();
        if (step == null) {
            JOptionPane.showMessageDialog(this, "Select a history entry first.");
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete this entry?\n\n" + step.getActionDate() + " — " + step.getAction()
                        + " to " + safeName(step.getToPersonnelName(), step.getToOfficeName())
                        + "\n\nThis cannot be undone. Use this only for erroneous entries; the deletion "
                        + "itself will be recorded in the audit log for accountability.",
                "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            // Physically remove any evidence files first -- the DB rows for
            // them cascade-delete automatically via the FK, but the actual
            // files on disk don't, and would otherwise become orphaned.
            List<Attachment> evidence = attachmentDAO.findByRoutingStep(step.getStepId());
            for (Attachment a : evidence) {
                new File(a.getFilePath()).delete();
            }

            routingDAO.delete(step.getStepId());
            new doctrack.dao.AuditLogDAO().log(document.getDocumentId(), currentUser.getUserId(), "DELETE",
                    "Deleted erroneous routing entry #" + step.getStepId() + ": " + step.getActionDate()
                            + " " + step.getAction() + " to "
                            + safeName(step.getToPersonnelName(), step.getToOfficeName())
                            + (step.getRemarks() != null && !step.getRemarks().isEmpty()
                                    ? " (remarks: " + step.getRemarks() + ")" : "")
                            + (evidence.isEmpty() ? "" : " [" + evidence.size() + " evidence file(s) also removed]"));
            recomputeDocumentCurrentState();
            loadHistory();
        } catch (SQLException ex) {
            AppLogger.logError("Failed to delete routing step " + step.getStepId(), ex);
            JOptionPane.showMessageDialog(this, "Failed to delete entry: " + ex.getMessage());
        }
    }

    /**
     * Recalculates the document's "current holder/office" and status from
     * whatever routing history remains after an edit or delete, so
     * correcting a mistake doesn't leave the document pointing at a
     * recipient or status that no longer has a supporting history entry.
     */
    private void recomputeDocumentCurrentState() throws SQLException {
        List<RoutingStep> remaining = routingDAO.findByDocument(document.getDocumentId());
        if (remaining.isEmpty()) return; // nothing left to derive state from -- leave document as-is

        RoutingStep latest = remaining.get(remaining.size() - 1); // findByDocument orders ascending
        String newStatus = statusForAction(latest.getAction());
        if (newStatus != null) document.setStatus(newStatus);
        if ("CLOSED".equals(newStatus)) {
            if (document.getDateClosed() == null || document.getDateClosed().isEmpty()) {
                document.setDateClosed(java.time.LocalDate.now().toString());
            }
        } else {
            document.setDateClosed(null);
        }

        List<RoutingStep> currentRecipients = routingDAO.findCurrentRecipients(document.getDocumentId());
        if (!currentRecipients.isEmpty()) {
            RoutingStep primary = currentRecipients.get(0);
            document.setCurrentOfficeId(primary.getToOfficeId() != null ? primary.getToOfficeId() : document.getCurrentOfficeId());
            document.setCurrentHolderId(primary.getToPersonnelId());
        } else {
            document.setCurrentOfficeId(latest.getToOfficeId() != null ? latest.getToOfficeId() : document.getCurrentOfficeId());
            document.setCurrentHolderId(latest.getToPersonnelId());
        }
        documentDAO.update(document);
    }

    private String statusForAction(String action) {
        if (action == null) return null;
        switch (action) {
            case "RECEIVED": return "RECEIVED";
            case "FORWARDED": return "FORWARDED";
            case "REVIEWED": return "IN_PROGRESS";
            case "COMPLIED": return "COMPLIED";
            case "CLOSED": return "CLOSED";
            default: return null; // e.g. RETURNED -- no direct document-level status mapping
        }
    }

    private String nameOf(RecipientEntry r) {
        return r.toString();
    }

    private String safeName(String personnelName, String officeName) {
        if (personnelName != null && !personnelName.isEmpty()) return personnelName;
        if (officeName != null && !officeName.isEmpty()) return officeName;
        return "—";
    }

    private void printAuditTrail() {
        try {
            List<RoutingStep> fullHistory = routingDAO.findByDocument(document.getDocumentId());
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setPrintable(new AuditReportPrinter(document, fullHistory));
            if (job.printDialog()) {
                job.print();
            }
        } catch (SQLException | PrinterException ex) {
            AppLogger.logError("Failed to print audit trail for document " + document.getDocumentId(), ex);
            JOptionPane.showMessageDialog(this, "Print failed: " + ex.getMessage());
        }
    }
}
