package doctrack.util;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.util.Enumeration;

/**
 * Central place that defines readable fonts and consistent, high-contrast
 * colors for the whole application. Call UITheme.apply() once, at startup,
 * before any frame is created. Built entirely on javax.swing / java.awt --
 * no external Look-and-Feel library.
 */
public final class UITheme {

    // Base typography -- large enough to read comfortably on any monitor
    public static final Font FONT_BASE   = new Font("Segoe UI", Font.PLAIN, 14);
    public static final Font FONT_LABEL  = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font FONT_HEADER = new Font("Segoe UI", Font.BOLD, 20);
    public static final Font FONT_TABLE  = new Font("Segoe UI", Font.PLAIN, 14);

    // Neutral high-contrast palette
    public static final Color COLOR_BG          = new Color(0xF4, 0xF6, 0xF8);
    public static final Color COLOR_PANEL       = Color.WHITE;
    public static final Color COLOR_TEXT        = new Color(0x1B, 0x1F, 0x24);
    public static final Color COLOR_PRIMARY     = new Color(0x1F, 0x4E, 0x8C);
    public static final Color COLOR_PRIMARY_TXT = Color.WHITE;

    // Status colors -- used consistently everywhere a status is shown
    // (badges in tables, dashboard cards, etc.)
    public static final Color STATUS_RECEIVED    = new Color(0x2E, 0x6D, 0xA4); // blue
    public static final Color STATUS_IN_PROGRESS = new Color(0xB8, 0x86, 0x0B); // amber
    public static final Color STATUS_FORWARDED   = new Color(0x6A, 0x4C, 0x93); // purple
    public static final Color STATUS_COMPLIED    = new Color(0x2E, 0x7D, 0x32); // green
    public static final Color STATUS_CLOSED      = new Color(0x55, 0x55, 0x55); // gray
    public static final Color STATUS_OVERDUE     = new Color(0xC6, 0x28, 0x28); // red

    private UITheme() { }

    public static void apply() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // fall back to default cross-platform L&F
        }

        // Push the base font onto every Swing component type
        FontUIResource fontResource = new FontUIResource(FONT_BASE);
        Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object value = UIManager.get(key);
            if (value instanceof FontUIResource) {
                UIManager.put(key, fontResource);
            }
        }

        UIManager.put("Panel.background", COLOR_BG);
        UIManager.put("OptionPane.background", COLOR_BG);
        UIManager.put("Table.font", FONT_TABLE);
        UIManager.put("Table.rowHeight", 26);
        UIManager.put("TableHeader.font", FONT_LABEL);
    }

    /** Returns a readable text color for a given status, for badges/labels. */
    public static Color colorForStatus(String status) {
        if (status == null) return COLOR_TEXT;
        switch (status.toUpperCase()) {
            case "RECEIVED": return STATUS_RECEIVED;
            case "IN_PROGRESS": return STATUS_IN_PROGRESS;
            case "FORWARDED": return STATUS_FORWARDED;
            case "COMPLIED": return STATUS_COMPLIED;
            case "CLOSED": return STATUS_CLOSED;
            case "OVERDUE": return STATUS_OVERDUE;
            default: return COLOR_TEXT;
        }
    }

    /**
     * Makes Tab/Shift+Tab move focus to the next/previous component instead
     * of inserting a literal tab character -- JTextArea's default behavior
     * traps keyboard focus inside it, which is disorienting in a form full
     * of other fields. Call this on every multi-line remarks/notes field in
     * the app.
     */
    public static void enableTabToAdvanceFocus(javax.swing.JTextArea textArea) {
        java.util.Set<java.awt.AWTKeyStroke> forward = java.util.Collections.singleton(
                javax.swing.KeyStroke.getKeyStroke("TAB"));
        textArea.setFocusTraversalKeys(java.awt.KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, forward);

        java.util.Set<java.awt.AWTKeyStroke> backward = java.util.Collections.singleton(
                javax.swing.KeyStroke.getKeyStroke("shift TAB"));
        textArea.setFocusTraversalKeys(java.awt.KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, backward);
    }
}
