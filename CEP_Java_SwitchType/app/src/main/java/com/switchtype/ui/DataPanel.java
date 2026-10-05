package com.switchtype.ui;

import com.switchtype.db.ExperimentDao;
import com.switchtype.io.ModelStore;
import com.switchtype.predict.CorpusLoader;
import com.switchtype.predict.LanguageModel;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;
import java.util.List;

/** Storage, privacy actions, vocabulary, and a generic viewer of the MySQL tables. */
public class DataPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final MainFrame frame;
    private final JLabel storage = new JLabel(" ");
    private final JLabel privacyResult = new JLabel(" ");
    private final JCheckBox eventLog = new JCheckBox("Log every selection on this computer (off by default)");
    private final JSpinner retention = new JSpinner(new SpinnerNumberModel(30, 0, 3650, 1));
    private final JComboBox<String> tables = new JComboBox<String>(ExperimentDao.VIEWABLE_TABLES.toArray(new String[0]));
    private final DefaultTableModel viewer = new DefaultTableModel();

    public DataPanel(MainFrame frame) {
        super(new BorderLayout(0, 12));
        this.frame = frame;
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel north = new JPanel(new GridLayout(0, 1, 0, 8));

        JPanel store = section("Storage");
        JButton mysql = new JButton("MySQL settings...");
        JButton offline = new JButton("Use offline files");
        mysql.addActionListener(e -> new DbSettingsDialog(frame).setVisible(true));
        offline.addActionListener(e -> frame.useOffline());
        store.add(storage);
        store.add(mysql);
        store.add(offline);
        north.add(store);

        JPanel privacy = section("Privacy (typed text is never stored; sessions keep only counts and times)");
        eventLog.addActionListener(e -> toggleEventLog());
        JButton purge = new JButton("Delete events older than (days):");
        purge.addActionListener(e -> purge());
        JButton deleteAll = new JButton("Delete ALL my data...");
        deleteAll.addActionListener(e -> frame.deleteCurrentProfile());
        privacy.add(eventLog);
        privacy.add(purge);
        privacy.add(retention);
        privacy.add(deleteAll);
        north.add(privacy);

        JPanel vocab = section("Vocabulary (learned words stay on this computer)");
        JButton importVocab = new JButton("Import words from a text file...");
        JButton reset = new JButton("Reset learned vocabulary");
        importVocab.addActionListener(e -> importVocabulary());
        reset.addActionListener(e -> resetVocabulary());
        vocab.add(importVocab);
        vocab.add(reset);
        vocab.add(privacyResult);
        north.add(vocab);

        JPanel browse = section("Database tables (MySQL only)");
        JButton show = new JButton("Show");
        show.addActionListener(e -> browse());
        browse.add(tables);
        browse.add(show);
        north.add(browse);

        add(north, BorderLayout.NORTH);
        JTable table = new JTable(viewer);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    private static JPanel section(String title) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        p.setBorder(BorderFactory.createTitledBorder(title));
        return p;
    }

    /** Shows the current store and the consent setting of the current profile. */
    public void refresh(boolean eventLogging) {
        storage.setText(frame.getApp().isMySql()
                ? "Connected to " + frame.getApp().getProfiles().describe()
                : "Offline: " + frame.getApp().getProfiles().describe());
        eventLog.setSelected(eventLogging);
    }

    private void toggleEventLog() {
        if (eventLog.isSelected()) {
            int answer = JOptionPane.showConfirmDialog(this,
                    "<html><b>Event logging</b> records which key was selected and when.<br>"
                    + "From such a log someone could rebuild what you wrote.<br><br>"
                    + "The log stays on this computer (or your own MySQL server), is used only for<br>"
                    + "your own history, and can be deleted at any time on this tab.<br><br>"
                    + "Do you agree to turn it on?</html>", "Consent for event logging", JOptionPane.YES_NO_OPTION);
            if (answer != JOptionPane.YES_OPTION) {
                eventLog.setSelected(false);
                return;
            }
        }
        frame.setEventLogging(eventLog.isSelected());
    }

    private void purge() {
        final int days = (Integer) retention.getValue();
        Async.run(frame.getApp().getWorker(), this, () -> frame.getApp().getSessions().purgeEvents(days),
                n -> privacyResult.setText("Deleted " + n + " events older than " + days + " days."));
    }

    private void importVocabulary() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        final File file = chooser.getSelectedFile();
        // the import works on a copy, so the Swing thread never sees a half-updated model
        final LanguageModel copy = frame.getApp().getModel().copy();
        Async.run(frame.getApp().getWorker(), this, () -> {
            int words = CorpusLoader.importVocabulary(copy, file);
            ModelStore.save(copy, AppContext.USER_MODEL);
            return words;
        }, words -> {
            frame.setModel(copy);
            privacyResult.setText("Imported " + words + " words from " + file.getName() + ".");
        });
    }

    private void resetVocabulary() {
        Async.run(frame.getApp().getWorker(), this, () -> {
            frame.getApp().resetUserModel();
            return frame.getApp().getModel();
        }, m -> {
            frame.setModel(m);
            privacyResult.setText("Learned vocabulary deleted; back to the built-in words.");
        });
    }

    private void browse() {
        if (!frame.getApp().isMySql()) {
            privacyResult.setText("Connect to MySQL first (MySQL settings...).");
            return;
        }
        final String table = (String) tables.getSelectedItem();
        Async.run(frame.getApp().getWorker(), this, () -> ExperimentDao.browse(table, 200), this::showRows);
    }

    private void showRows(List<String[]> rows) {
        viewer.setRowCount(0);
        viewer.setColumnIdentifiers(rows.get(0));
        for (int i = 1; i < rows.size(); i++) {
            viewer.addRow(rows.get(i));
        }
    }
}
