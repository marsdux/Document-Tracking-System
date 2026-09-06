package doctrack.ui;

import doctrack.dao.AuditLogDAO;
import doctrack.dao.UserDAO;
import doctrack.model.User;
import doctrack.util.AppLogger;
import doctrack.util.LoginAttemptTracker;
import doctrack.util.UITheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.sql.SQLException;
import java.util.Arrays;

public class LoginFrame extends JFrame {

    private final JTextField usernameField = new JTextField(18);
    private final JPasswordField passwordField = new JPasswordField(18);
    private final JLabel statusLabel = new JLabel(" ");
    private final UserDAO userDAO = new UserDAO();
    private final AuditLogDAO auditLogDAO = new AuditLogDAO();

    public LoginFrame() {
        super("Document Tracking System - Login");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        buildUI();
        pack();
        setLocationRelativeTo(null);
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(BorderFactory.createEmptyBorder(30, 40, 30, 40));
        root.setBackground(UITheme.COLOR_PANEL);

        JLabel title = new JLabel("Document Tracking System");
        title.setFont(UITheme.FONT_HEADER);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));
        root.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(UITheme.COLOR_PANEL);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 6, 6, 6);
        gc.anchor = GridBagConstraints.WEST;

        gc.gridx = 0; gc.gridy = 0;
        JLabel userLbl = new JLabel("Username:");
        userLbl.setFont(UITheme.FONT_LABEL);
        form.add(userLbl, gc);
        gc.gridx = 1;
        form.add(usernameField, gc);

        gc.gridx = 0; gc.gridy = 1;
        JLabel passLbl = new JLabel("Password:");
        passLbl.setFont(UITheme.FONT_LABEL);
        form.add(passLbl, gc);
        gc.gridx = 1;
        form.add(passwordField, gc);

        root.add(form, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(UITheme.COLOR_PANEL);
        statusLabel.setForeground(UITheme.STATUS_OVERDUE);
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        south.add(statusLabel, BorderLayout.NORTH);

        JButton loginBtn = new JButton("Log In");
        loginBtn.setFont(UITheme.FONT_LABEL);
        loginBtn.setBackground(UITheme.COLOR_PRIMARY);
        loginBtn.setForeground(UITheme.COLOR_PRIMARY_TXT);
        loginBtn.addActionListener(this::onLogin);
        JPanel btnWrap = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnWrap.setBackground(UITheme.COLOR_PANEL);
        btnWrap.add(loginBtn);
        south.add(btnWrap, BorderLayout.CENTER);

        JLabel hint = new JLabel("Default admin login: admin / admin123", SwingConstants.CENTER);
        hint.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        hint.setForeground(Color.GRAY);
        south.add(hint, BorderLayout.SOUTH);

        root.add(south, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(loginBtn);
        setContentPane(root);
    }

    private void onLogin(ActionEvent e) {
        String username = usernameField.getText().trim();
        char[] password = passwordField.getPassword();
        if (username.isEmpty() || password.length == 0) {
            statusLabel.setText("Please enter username and password.");
            return;
        }

        long lockedSeconds = doctrack.util.LoginAttemptTracker.secondsUntilUnlocked(username);
        if (lockedSeconds > 0) {
            statusLabel.setText("Too many failed attempts. Try again in " + lockedSeconds + "s.");
            java.util.Arrays.fill(password, ' ');
            return;
        }

        try {
            User user = userDAO.authenticate(username, password);
            if (user == null) {
                LoginAttemptTracker.recordFailure(username);
                auditLogDAO.log(null, null, "LOGIN_FAILURE", "Failed login attempt for username: " + username);
                statusLabel.setText("Invalid username or password.");
            } else {
                LoginAttemptTracker.recordSuccess(username);
                auditLogDAO.log(null, user.getUserId(), "LOGIN_SUCCESS", "Logged in as " + user.getUsername());
                MainFrame main = new MainFrame(user);
                main.setVisible(true);
                dispose();
            }
        } catch (SQLException ex) {
            AppLogger.logError("Login failed", ex);
            statusLabel.setText("A database error occurred. See logs/app.log for details.");
        } finally {
            Arrays.fill(password, ' ');
        }
    }
}
