package doctrack.ui;

import doctrack.dao.AuditLogDAO;
import doctrack.dao.OfficeDAO;
import doctrack.dao.PersonnelDAO;
import doctrack.model.Office;
import doctrack.model.Personnel;
import doctrack.model.User;
import doctrack.util.AppLogger;
import doctrack.util.UITheme;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLException;
import java.util.List;

/**
 * Lets an admin freely add, rename, move and deactivate offices/units and
 * the personnel within them. This is what makes the routing structure
 * flexible instead of hardcoded -- the tree here is exactly what populates
 * the "Current Office" / "To Office" / "To Personnel" dropdowns elsewhere.
 */
public class OrgStructurePanel extends JPanel {

    private final User currentUser;
    private final OfficeDAO officeDAO = new OfficeDAO();
    private final PersonnelDAO personnelDAO = new PersonnelDAO();
    private final AuditLogDAO auditLogDAO = new AuditLogDAO();

    private final DefaultTableModel officeModel = new DefaultTableModel(
            new String[]{"ID", "Office / Unit", "Parent Office"}, 0) {
        @Override public boolean isCellEditable(int row, int col) { return false; }
    };
    private final JTable officeTable = new JTable(officeModel);
    private List<Office> currentOffices;

    private final DefaultTableModel personnelModel = new DefaultTableModel(
            new String[]{"ID", "Name", "Position", "Office"}, 0) {
        @Override public boolean isCellEditable(int row, int col) { return false; }
    };
    private final JTable personnelTable = new JTable(personnelModel);
    private List<Personnel> currentPersonnel;

    public OrgStructurePanel(User currentUser) {
        this.currentUser = currentUser;
        setLayout(new GridLayout(1, 2, 10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        add(buildOfficePanel());
        add(buildPersonnelPanel());
        refreshOffices();
        refreshPersonnel();
    }

    private JPanel buildOfficePanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createTitledBorder("Offices / Units"));
        officeTable.setFont(UITheme.FONT_TABLE);
        officeTable.getTableHeader().setFont(UITheme.FONT_LABEL);
        panel.add(new JScrollPane(officeTable), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addBtn = new JButton("Add Office");
        addBtn.addActionListener(e -> addOffice());
        JButton renameBtn = new JButton("Rename / Move");
        renameBtn.addActionListener(e -> editOffice());
        JButton deactivateBtn = new JButton("Deactivate");
        deactivateBtn.addActionListener(e -> deactivateOffice());
        buttons.add(addBtn);
        buttons.add(renameBtn);
        buttons.add(deactivateBtn);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildPersonnelPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createTitledBorder("Personnel"));
        personnelTable.setFont(UITheme.FONT_TABLE);
        personnelTable.getTableHeader().setFont(UITheme.FONT_LABEL);
        panel.add(new JScrollPane(personnelTable), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addBtn = new JButton("Add Personnel");
        addBtn.addActionListener(e -> addPersonnel());
        JButton editBtn = new JButton("Edit / Reassign");
        editBtn.addActionListener(e -> editPersonnel());
        JButton deactivateBtn = new JButton("Deactivate");
        deactivateBtn.addActionListener(e -> deactivatePersonnel());
        buttons.add(addBtn);
        buttons.add(editBtn);
        buttons.add(deactivateBtn);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    // ----- Offices -----

    private void refreshOffices() {
        try {
            currentOffices = officeDAO.findAll();
            officeModel.setRowCount(0);
            for (Office o : currentOffices) {
                String parentName = "(top level)";
                if (o.getParentOfficeId() != null) {
                    for (Office p : currentOffices) {
                        if (p.getOfficeId() == o.getParentOfficeId()) { parentName = p.getOfficeName(); break; }
                    }
                }
                String label = o.getOfficeName() + (o.isActive() ? "" : "  [inactive]");
                officeModel.addRow(new Object[]{o.getOfficeId(), label, parentName});
            }
        } catch (SQLException ex) {
            AppLogger.logError("Database operation failed", ex);
            JOptionPane.showMessageDialog(this, "Failed to load offices: " + ex.getMessage());
        }
    }

    private void addOffice() {
        String name = JOptionPane.showInputDialog(this, "New office / unit name:");
        if (name == null || name.trim().isEmpty()) return;
        Office parent = pickParentOffice();
        try {
            Office o = new Office(0, name.trim(), parent != null ? parent.getOfficeId() : null);
            officeDAO.insert(o);
            auditLogDAO.log(null, currentUser.getUserId(), "CREATE", "Added office: " + name.trim());
            refreshOffices();
        } catch (SQLException ex) {
            AppLogger.logError("Database operation failed", ex);
            JOptionPane.showMessageDialog(this, "Failed to add office: " + ex.getMessage());
        }
    }

    private void editOffice() {
        Office selected = getSelectedOffice();
        if (selected == null) { JOptionPane.showMessageDialog(this, "Select an office first."); return; }
        String newName = JOptionPane.showInputDialog(this, "Office / unit name:", selected.getOfficeName());
        if (newName == null || newName.trim().isEmpty()) return;
        Office parent = pickParentOffice();
        try {
            String oldName = selected.getOfficeName();
            selected.setOfficeName(newName.trim());
            selected.setParentOfficeId(parent != null ? parent.getOfficeId() : null);
            officeDAO.update(selected);
            auditLogDAO.log(null, currentUser.getUserId(), "EDIT",
                    "Renamed/moved office: \"" + oldName + "\" -> \"" + newName.trim() + "\"");
            refreshOffices();
        } catch (SQLException ex) {
            AppLogger.logError("Database operation failed", ex);
            JOptionPane.showMessageDialog(this, "Failed to update office: " + ex.getMessage());
        }
    }

    private void deactivateOffice() {
        Office selected = getSelectedOffice();
        if (selected == null) { JOptionPane.showMessageDialog(this, "Select an office first."); return; }
        int confirm = JOptionPane.showConfirmDialog(this,
                "Deactivate \"" + selected.getOfficeName() + "\"? "
                        + "Historical records referencing it are kept intact.",
                "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        try {
            officeDAO.deactivate(selected.getOfficeId());
            auditLogDAO.log(null, currentUser.getUserId(), "DELETE",
                    "Deactivated office: " + selected.getOfficeName());
            refreshOffices();
        } catch (SQLException ex) {
            AppLogger.logError("Database operation failed", ex);
            JOptionPane.showMessageDialog(this, "Failed to deactivate office: " + ex.getMessage());
        }
    }

    private Office pickParentOffice() {
        Object[] options = new Object[currentOffices.size() + 1];
        options[0] = "(top level - no parent)";
        for (int i = 0; i < currentOffices.size(); i++) options[i + 1] = currentOffices.get(i).getOfficeName();
        Object choice = JOptionPane.showInputDialog(this, "Parent office (or top level):",
                "Select Parent", JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (choice == null || choice.equals(options[0])) return null;
        for (Office o : currentOffices) if (o.getOfficeName().equals(choice)) return o;
        return null;
    }

    private Office getSelectedOffice() {
        int row = officeTable.getSelectedRow();
        if (row < 0 || currentOffices == null) return null;
        int id = (int) officeModel.getValueAt(row, 0);
        for (Office o : currentOffices) if (o.getOfficeId() == id) return o;
        return null;
    }

    // ----- Personnel -----

    private void refreshPersonnel() {
        try {
            currentPersonnel = personnelDAO.findAll();
            personnelModel.setRowCount(0);
            for (Personnel p : currentPersonnel) {
                String label = p.getFullName() + (p.isActive() ? "" : "  [inactive]");
                personnelModel.addRow(new Object[]{p.getPersonnelId(), label, p.getPosition(), p.getOfficeName()});
            }
        } catch (SQLException ex) {
            AppLogger.logError("Database operation failed", ex);
            JOptionPane.showMessageDialog(this, "Failed to load personnel: " + ex.getMessage());
        }
    }

    private void addPersonnel() {
        String name = JOptionPane.showInputDialog(this, "Full name:");
        if (name == null || name.trim().isEmpty()) return;
        String position = JOptionPane.showInputDialog(this, "Position / designation:");
        Office office = pickOfficeForPersonnel();
        try {
            Personnel p = new Personnel();
            p.setFullName(name.trim());
            p.setPosition(position);
            p.setOfficeId(office != null ? office.getOfficeId() : null);
            p.setActive(true);
            personnelDAO.insert(p);
            auditLogDAO.log(null, currentUser.getUserId(), "CREATE", "Added personnel: " + name.trim());
            refreshPersonnel();
        } catch (SQLException ex) {
            AppLogger.logError("Database operation failed", ex);
            JOptionPane.showMessageDialog(this, "Failed to add personnel: " + ex.getMessage());
        }
    }

    private void editPersonnel() {
        Personnel selected = getSelectedPersonnel();
        if (selected == null) { JOptionPane.showMessageDialog(this, "Select a person first."); return; }
        String name = JOptionPane.showInputDialog(this, "Full name:", selected.getFullName());
        if (name == null || name.trim().isEmpty()) return;
        String position = JOptionPane.showInputDialog(this, "Position / designation:", selected.getPosition());
        Office office = pickOfficeForPersonnel();
        try {
            String oldName = selected.getFullName();
            selected.setFullName(name.trim());
            selected.setPosition(position);
            selected.setOfficeId(office != null ? office.getOfficeId() : selected.getOfficeId());
            personnelDAO.update(selected);
            auditLogDAO.log(null, currentUser.getUserId(), "EDIT",
                    "Edited/reassigned personnel: \"" + oldName + "\" -> \"" + name.trim() + "\"");
            refreshPersonnel();
        } catch (SQLException ex) {
            AppLogger.logError("Database operation failed", ex);
            JOptionPane.showMessageDialog(this, "Failed to update personnel: " + ex.getMessage());
        }
    }

    private void deactivatePersonnel() {
        Personnel selected = getSelectedPersonnel();
        if (selected == null) { JOptionPane.showMessageDialog(this, "Select a person first."); return; }
        int confirm = JOptionPane.showConfirmDialog(this,
                "Deactivate \"" + selected.getFullName() + "\"? Historical routing records are kept intact.",
                "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        try {
            personnelDAO.deactivate(selected.getPersonnelId());
            auditLogDAO.log(null, currentUser.getUserId(), "DELETE",
                    "Deactivated personnel: " + selected.getFullName());
            refreshPersonnel();
        } catch (SQLException ex) {
            AppLogger.logError("Database operation failed", ex);
            JOptionPane.showMessageDialog(this, "Failed to deactivate personnel: " + ex.getMessage());
        }
    }

    private Office pickOfficeForPersonnel() {
        try {
            List<Office> offices = officeDAO.findActive();
            Object[] options = new Object[offices.size()];
            for (int i = 0; i < offices.size(); i++) options[i] = offices.get(i).getOfficeName();
            if (options.length == 0) return null;
            Object choice = JOptionPane.showInputDialog(this, "Assign to office:",
                    "Select Office", JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            if (choice == null) return null;
            for (Office o : offices) if (o.getOfficeName().equals(choice)) return o;
        } catch (SQLException ex) {
            AppLogger.logError("Database operation failed", ex);
            JOptionPane.showMessageDialog(this, "Failed to load offices: " + ex.getMessage());
        }
        return null;
    }

    private Personnel getSelectedPersonnel() {
        int row = personnelTable.getSelectedRow();
        if (row < 0 || currentPersonnel == null) return null;
        int id = (int) personnelModel.getValueAt(row, 0);
        for (Personnel p : currentPersonnel) if (p.getPersonnelId() == id) return p;
        return null;
    }
}
