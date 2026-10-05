package com.switchtype.ui;

import com.switchtype.db.Database;
import com.switchtype.db.DbConfig;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

/** MySQL host, user and password for this computer (saved to db.properties, which is never committed). */
public class DbSettingsDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final MainFrame frame;
    private final JTextField host = new JTextField(18);
    private final JTextField port = new JTextField(6);
    private final JTextField database = new JTextField(18);
    private final JTextField user = new JTextField(18);
    private final JPasswordField password = new JPasswordField(18);
    private final JLabel result = new JLabel(" ");

    public DbSettingsDialog(MainFrame frame) {
        super(frame, "MySQL connection", true);
        this.frame = frame;
        DbConfig c = Database.getConfig();
        host.setText(c.getHost());
        port.setText(String.valueOf(c.getPort()));
        database.setText(c.getDatabase());
        user.setText(c.getUser());
        password.setText(c.getPassword());

        JPanel grid = new JPanel(new GridLayout(0, 2, 8, 6));
        grid.add(new JLabel("Host"));
        grid.add(host);
        grid.add(new JLabel("Port"));
        grid.add(port);
        grid.add(new JLabel("Database (created if missing)"));
        grid.add(database);
        grid.add(new JLabel("User"));
        grid.add(user);
        grid.add(new JLabel("Password"));
        grid.add(password);

        JButton test = new JButton("Test connection");
        JButton save = new JButton("Save and connect");
        JButton cancel = new JButton("Cancel");
        test.addActionListener(e -> testConnection());
        save.addActionListener(e -> saveAndConnect());
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(test);
        buttons.add(save);
        buttons.add(cancel);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(new JLabel("Stored in db.properties next to the program. Without MySQL the app works offline."),
                BorderLayout.NORTH);
        content.add(grid, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout(0, 8));
        south.add(result, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.SOUTH);
        content.add(south, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(save);
        pack();
        setLocationRelativeTo(frame);
    }

    private DbConfig readForm() {
        DbConfig c = new DbConfig();
        c.setHost(host.getText().trim());
        c.setPort(Integer.parseInt(port.getText().trim()));
        c.setDatabase(database.getText().trim());
        c.setUser(user.getText().trim());
        c.setPassword(new String(password.getPassword()));
        return c;
    }

    private void testConnection() {
        final DbConfig c;
        try {
            c = readForm();
        } catch (NumberFormatException e) {
            result.setText("Port must be a number.");
            return;
        }
        result.setText("Connecting...");
        Async.run(frame.getApp().getWorker(), this, () -> {
            try (Connection con = Database.getConnection(c)) {
                DatabaseMetaData meta = con.getMetaData();
                return "Connected: " + meta.getDatabaseProductName() + " " + meta.getDatabaseProductVersion()
                        + " (" + meta.getDriverName() + ")";
            }
        }, text -> {
            result.setText(text);
            pack();
        });
    }

    private void saveAndConnect() {
        final DbConfig c;
        try {
            c = readForm();
        } catch (NumberFormatException e) {
            result.setText("Port must be a number.");
            return;
        }
        Database.setConfig(c);
        dispose();
        Async.run(frame.getApp().getWorker(), frame, () -> {
            c.save();                                   // file I/O on the worker, not the EDT
            return Boolean.TRUE;
        }, ok -> frame.connectToDatabase(true));
    }
}
