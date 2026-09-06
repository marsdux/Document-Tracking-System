package doctrack.ui;

import doctrack.dao.*;
import doctrack.model.*;
import doctrack.util.AppLogger;
import doctrack.util.UITheme;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Sends one or more documents to one or more recipients (offices and/or
 * personnel) in a single logged action. Every (document, recipient) pair
 * gets its own routing_steps row, all sharing one batch_id, all inserted
 * in one transaction -- so a batch of many documents going to many people
 * either fully succeeds or fully rolls back.
 */
public class BatchForwardDialog extends JDialog {

    private final User currentUser;
    private final List<Document> documents;

    private final RoutingDAO routingDAO = new RoutingDAO();
    private final OfficeDAO officeDAO = new OfficeDAO();
    private final PersonnelDAO personnelDAO = new PersonnelDAO();
    private final DocumentDAO documentDAO = new DocumentDAO();
    private final AuditLogDAO auditLogDAO = new AuditLogDAO();

    private final JComboBox<String> actionField = new JComboBox<>(
            new String[]{"FORWARDED", "REVIEWED", "COMPLIED", "RETURNED", "CLOSED"});
    private final JList<RecipientEntry> recipientList = new JList<>();
    private final JTextArea remarksField = new JTextArea(3, 25);
    private boolean sent = false;

    public BatchForwardDialog(Frame owner, User currentUser, List<Document> documents) {
        super(owner, "Batch Forward — " + documents.size() + " Document(s)", true);
        this.currentUser = currentUser;
        this.documents = documents;
        buildUI();
        loadRecipients();
        setSize(700, 600);
        setLocationRelativeTo(owner);
    }

    public boolean isSent() { return sent; }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel header = new JLabel("Sending " + documents.size() + " document(s):");
        header.setFont(UITheme.FONT_HEADER);
        root.add(header, BorderLayout.NORTH);

        DefaultListModel<String> docListModel = new DefaultListModel<>();
        for (Document d : documents) docListModel.addElement(d.getTrackingNo() + " — " + d.getSubject());
        JList<String> docList = new JList<>(docListModel);
        docList.setEnabled(false);
        JScrollPane docScroll = new JScrollPane(docList);
        docScroll.setBorder(BorderFactory.createTitledBorder("Documents in this batch"));
        docScroll.setPreferredSize(new Dimension(650, 120));

        JPanel centerPanel = new JPanel(new BorderLayout(8, 8));
        centerPanel.add(docScroll, BorderLayout.NORTH);

        recipientList.setVisibleRowCount(8);
        recipientList.setFont(UITheme.FONT_TABLE);
        JScrollPane recipientScroll = new JScrollPane(recipientList);
        recipientScroll.setBorder(BorderFactory.createTitledBorder(
                "Recipients (Ctrl/Shift+Click to select multiple)"));
        centerPanel.add(recipientScroll, BorderLayout.CENTER);

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actionRow.add(new JLabel("Action:"));
        actionRow.add(actionField);
        centerPanel.add(actionRow, BorderLayout.SOUTH);

        root.add(centerPanel, BorderLayout.CENTER);

        JPanel southPanel = new JPanel(new BorderLayout(6, 6));
        remarksField.setLineWrap(true);
        doctrack.util.UITheme.enableTabToAdvanceFocus(remarksField);
        remarksField.setWrapStyleWord(true);
        JPanel remarksWrap = new JPanel(new BorderLayout());
        remarksWrap.setBorder(BorderFactory.createTitledBorder("Remarks (applied to every document/recipient in this batch)"));
        remarksWrap.add(new JScrollPane(remarksField), BorderLayout.CENTER);
        southPanel.add(remarksWrap, BorderLayout.CENTER);

        JButton sendBtn = new JButton("Send Batch");
        sendBtn.setFont(UITheme.FONT_LABEL);
        sendBtn.setBackground(UITheme.COLOR_PRIMARY);
        sendBtn.setForeground(UITheme.COLOR_PRIMARY_TXT);
        sendBtn.addActionListener(e -> sendBatch());
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnRow.add(cancelBtn);
        btnRow.add(sendBtn);
        southPanel.add(btnRow, BorderLayout.SOUTH);

        root.add(southPanel, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private void loadRecipients() {
        try {
            List<RecipientEntry> entries = RecipientEntry.loadAll(officeDAO, personnelDAO);
            recipientList.setListData(entries.toArray(new RecipientEntry[0]));
        } catch (SQLException ex) {
            AppLogger.logError("Failed to load recipients for batch forward", ex);
            JOptionPane.showMessageDialog(this, "Failed to load offices/personnel: " + ex.getMessage());
        }
    }

    private void sendBatch() {
        List<RecipientEntry> recipients = recipientList.getSelectedValuesList();
        if (recipients.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select at least one recipient.");
            return;
        }
        if (documents.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No documents selected.");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Send " + documents.size() + " document(s) to " + recipients.size()
                        + " recipient(s)? This creates " + (documents.size() * recipients.size())
                        + " routing entries.",
                "Confirm Batch Send", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        String action = (String) actionField.getSelectedItem();
        String remarks = remarksField.getText().trim();

        try {
            String batchId = routingDAO.newBatchId();
            List<RoutingStep> steps = new ArrayList<>();
            for (Document d : documents) {
                for (RecipientEntry r : recipients) {
                    RoutingStep step = new RoutingStep();
                    step.setDocumentId(d.getDocumentId());
                    step.setBatchId(batchId);
                    step.setFromOfficeId(d.getCurrentOfficeId());
                    step.setToOfficeId(r.officeId);
                    step.setToPersonnelId(r.personnelId);
                    step.setAction(action);
                    step.setRemarks(remarks);
                    step.setLoggedBy(currentUser.getUserId());
                    steps.add(step);
                }
            }
            routingDAO.insertBatch(steps);

            // update each document's primary current pointer + status
            RecipientEntry primary = recipients.get(0);
            for (Document d : documents) {
                d.setCurrentOfficeId(primary.officeId != null ? primary.officeId : d.getCurrentOfficeId());
                d.setCurrentHolderId(primary.personnelId);
                applyStatus(d, action);
                documentDAO.update(d);
                auditLogDAO.log(d.getDocumentId(), currentUser.getUserId(), "ROUTE",
                        "Batch " + action + " to " + recipients.size() + " recipient(s): " + remarks);
            }

            sent = true;
            JOptionPane.showMessageDialog(this, "Batch sent: " + documents.size() + " document(s) x "
                    + recipients.size() + " recipient(s).");
            dispose();
        } catch (SQLException ex) {
            AppLogger.logError("Batch forward failed", ex);
            JOptionPane.showMessageDialog(this, "Batch send failed (no changes were saved): " + ex.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void applyStatus(Document d, String action) {
        switch (action) {
            case "FORWARDED": d.setStatus("FORWARDED"); break;
            case "REVIEWED": d.setStatus("IN_PROGRESS"); break;
            case "COMPLIED": d.setStatus("COMPLIED"); break;
            case "CLOSED":
                d.setStatus("CLOSED");
                d.setDateClosed(java.time.LocalDate.now().toString());
                break;
            default: break;
        }
    }
}
