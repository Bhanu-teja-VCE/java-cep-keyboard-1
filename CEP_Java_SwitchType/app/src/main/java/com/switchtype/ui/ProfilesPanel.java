package com.switchtype.ui;

import com.switchtype.model.Settings;
import com.switchtype.model.UserProfile;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

/** Create, read, update and delete user profiles from the GUI (Unit V: CRUD from Swing). */
public class ProfilesPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final String[] COLUMNS = {"Id", "Name", "Scan style", "Layout", "Prediction", "Interval (ms)",
        "Adaptive", "Event log"};

    private final MainFrame frame;
    private final DefaultTableModel model = new DefaultTableModel(COLUMNS, 0) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(model);
    private List<UserProfile> profiles = new ArrayList<UserProfile>();

    public ProfilesPanel(MainFrame frame) {
        super(new BorderLayout(0, 10));
        this.frame = frame;
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        table.setRowHeight(24);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JButton create = new JButton("New profile...");
        JButton rename = new JButton("Rename...");
        JButton use = new JButton("Use selected");
        JButton delete = new JButton("Delete profile and all its data...");
        create.addActionListener(e -> create());
        rename.addActionListener(e -> rename());
        use.addActionListener(e -> {
            UserProfile p = selected();
            if (p != null) {
                frame.useProfile(p);
            }
        });
        delete.addActionListener(e -> delete());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.add(create);
        buttons.add(rename);
        buttons.add(use);
        buttons.add(delete);
        add(buttons, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    public void setProfiles(List<UserProfile> list) {
        profiles = list;
        model.setRowCount(0);
        for (UserProfile p : list) {
            Settings s = p.getSettings();
            model.addRow(new Object[]{p.getId(), p.getName(), s.getStyle(), s.getLayout(), s.getPrediction(),
                s.getIntervalMs(), s.isAdaptive() ? "on" : "off", s.isEventLogging() ? "on" : "off"});
        }
    }

    private UserProfile selected() {
        int row = table.getSelectedRow();
        return row < 0 ? null : profiles.get(table.convertRowIndexToModel(row));
    }

    private void create() {
        String name = JOptionPane.showInputDialog(this, "Name of the new profile:");
        if (name != null && !name.trim().isEmpty()) {
            frame.saveProfile(new UserProfile(0, name.trim(), new Settings()));
        }
    }

    private void rename() {
        UserProfile p = selected();
        if (p == null) {
            return;
        }
        String name = JOptionPane.showInputDialog(this, "New name:", p.getName());
        if (name != null && !name.trim().isEmpty()) {
            p.setName(name.trim());
            frame.saveProfile(p);
        }
    }

    private void delete() {
        UserProfile p = selected();
        if (p == null) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this, "Delete profile '" + p.getName()
                + "' and ALL its sessions and events? This cannot be undone.", "Delete all data",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer == JOptionPane.YES_OPTION) {
            frame.deleteProfile(p);
        }
    }
}
