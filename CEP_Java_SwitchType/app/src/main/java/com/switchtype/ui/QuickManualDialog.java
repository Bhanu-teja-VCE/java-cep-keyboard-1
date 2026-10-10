package com.switchtype.ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;

/**
 * Quick User Manual dialog that displays over the application window.
 * Provides clear, accessible instructions for single-switch operation.
 */
public class QuickManualDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    public static void show(JFrame parent) {
        QuickManualDialog dialog = new QuickManualDialog(parent);
        dialog.setVisible(true);
    }

    public QuickManualDialog(JFrame parent) {
        super(parent, "SwitchType — Quick User Manual", false); // non-modal so user can see app beneath
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
        content.setBackground(Color.WHITE);

        // Title Header
        JLabel title = new JLabel("SwitchType User Manual");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(new Color(30, 64, 175));
        title.setAlignmentX(LEFT_ALIGNMENT);
        content.add(title);

        JLabel subtitle = new JLabel("Assistive Single-Switch Virtual Keyboard (Java CEP Project)");
        subtitle.setFont(new Font("Segoe UI", Font.ITALIC, 13));
        subtitle.setForeground(new Color(100, 116, 139));
        subtitle.setAlignmentX(LEFT_ALIGNMENT);
        content.add(subtitle);
        content.add(Box.createVerticalStrut(14));
        content.add(new JSeparator());
        content.add(Box.createVerticalStrut(14));

        // Instructions Cards
        content.add(card("1. The Switch Key (No Mouse Needed)", 
                "• Your primary switch is the SPACEBAR (or Enter key).\n" +
                "• The blue highlight scans automatically across items at a fixed dwell time."));

        content.add(Box.createVerticalStrut(10));
        content.add(card("2. How to Type (Row-Column Scan)", 
                "• Step 1: Watch the highlight scan rows. Press SPACEBAR when your target row lights up.\n" +
                "• Step 2: The highlight scans individual letters in that row. Press SPACEBAR on your target letter.\n" +
                "• The letter is typed, and scanning resets to the top for the next character."));

        content.add(Box.createVerticalStrut(10));
        content.add(card("3. Intelligent Word Prediction (Saves 40%+ Presses)", 
                "• The top 4 blue buttons display word completions.\n" +
                "• When the highlight reaches the prediction row, press SPACEBAR to select a suggested word."));

        content.add(Box.createVerticalStrut(10));
        content.add(card("4. Corrections & Pausing", 
                "• 'delete': Erases the last character (Backspace).\n" +
                "• 'undo word': Removes the last typed word.\n" +
                "• 'pause': Freezes scanning to rest. Press SPACEBAR to resume."));

        content.add(Box.createVerticalStrut(10));
        content.add(card("5. Calibration & Settings", 
                "• Tab 2 (Calibrate): Runs 5 reaction trials to recommend the ideal dwell speed.\n" +
                "• Settings button: Toggle layouts (Frequency, Alphabetical, QWERTY) and Adaptive Speed."));

        content.add(Box.createVerticalStrut(10));
        content.add(card("6. Scan Styles (Row-Column vs Group-Binary)", 
                "• 'Row-Column' (Standard): Cleanly highlights one row at a time, then one letter.\n" +
                "• 'Group-Binary': Highlights half the keys at once using a Huffman tree.\n" +
                "  (If you see scattered keys highlighted at once, you are in Group-Binary mode. Go to Settings > Scan style > Row-column to switch back!)"));

        content.add(Box.createVerticalStrut(16));

        // Bottom Action Buttons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(LEFT_ALIGNMENT);

        JButton blogButton = new JButton("Open Engineering Blog");
        blogButton.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        blogButton.addActionListener(e -> openBlog());
        buttons.add(blogButton);

        JButton startButton = new JButton("Got It! Start Typing");
        startButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        startButton.setBackground(new Color(30, 64, 175));
        startButton.setForeground(Color.WHITE);
        startButton.addActionListener(e -> dispose());
        buttons.add(startButton);

        content.add(buttons);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        setContentPane(scroll);

        pack();
        setSize(580, 560);
        if (parent != null) {
            setLocationRelativeTo(parent);
        } else {
            setLocation(100, 100);
        }
    }

    private static JPanel card(String titleText, String bodyText) {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBackground(new Color(248, 250, 252));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240), 1),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(540, 150));

        JLabel t = new JLabel(titleText);
        t.setFont(new Font("Segoe UI", Font.BOLD, 14));
        t.setForeground(new Color(15, 23, 42));
        card.add(t, BorderLayout.NORTH);

        JLabel b = new JLabel("<html>" + bodyText.replace("\n", "<br>") + "</html>");
        b.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        b.setForeground(new Color(51, 65, 85));
        card.add(b, BorderLayout.CENTER);

        return card;
    }

    private static void openBlog() {
        try {
            File html = new File("blog.html");
            if (!html.exists()) {
                html = new File("../blog.html");
            }
            if (html.exists() && Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(html);
            }
        } catch (Exception ignored) {
        }
    }
}
