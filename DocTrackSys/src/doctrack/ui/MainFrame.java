package doctrack.ui;

import doctrack.model.User;
import doctrack.util.UITheme;

import javax.swing.*;
import java.awt.*;

public class MainFrame extends JFrame {

    private final User currentUser;

    public MainFrame(User user) {
        super("Document Tracking System");
        this.currentUser = user;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 750);
        setLocationRelativeTo(null);
        buildUI();
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());

        // Top bar: app title + logged-in user
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(UITheme.COLOR_PRIMARY);
        topBar.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        JLabel appTitle = new JLabel("Document Tracking System");
        appTitle.setFont(UITheme.FONT_HEADER);
        appTitle.setForeground(UITheme.COLOR_PRIMARY_TXT);
        topBar.add(appTitle, BorderLayout.WEST);

        JLabel userLabel = new JLabel(currentUser.getFullName() + "  (" + currentUser.getRole() + ")");
        userLabel.setFont(UITheme.FONT_LABEL);
        userLabel.setForeground(UITheme.COLOR_PRIMARY_TXT);
        topBar.add(userLabel, BorderLayout.EAST);

        root.add(topBar, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(UITheme.FONT_LABEL);
        tabs.addTab("Documents", new DocumentListPanel(currentUser));
        tabs.addTab("Organization Structure", new OrgStructurePanel(currentUser));
        tabs.addTab("Reports & Summary", new ReportsPanel());

        // Data-wipe/restore and the full activity log are sensitive --
        // restricted to administrators rather than every logged-in user.
        boolean isAdmin = "ADMIN".equalsIgnoreCase(currentUser.getRole());
        if (isAdmin) {
            tabs.addTab("Audit Log", new AuditLogPanel());
            tabs.addTab("Backup & Restore", new BackupRestorePanel(currentUser));
        }

        root.add(tabs, BorderLayout.CENTER);
        setContentPane(root);
    }
}
