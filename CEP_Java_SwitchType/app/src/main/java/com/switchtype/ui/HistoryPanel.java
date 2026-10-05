package com.switchtype.ui;

import com.switchtype.db.SessionSummary;
import com.switchtype.io.CsvWriter;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Session history of the current profile: a table, a chart and CSV export. Only counts are shown. */
public class HistoryPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final String[] COLUMNS = {"#", "Mode", "Started", "Target chars", "CPM", "Correction rate",
        "Presses / char", "Duration (s)"};

    private final MainFrame frame;
    private final DefaultTableModel model = new DefaultTableModel(COLUMNS, 0) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final HistoryChart chart = new HistoryChart();
    private final JLabel info = new JLabel(" ");
    private List<SessionSummary> sessions = new ArrayList<SessionSummary>();

    public HistoryPanel(MainFrame frame) {
        super(new BorderLayout(0, 10));
        this.frame = frame;
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JTable table = new JTable(model);
        table.setRowHeight(24);
        JButton refresh = new JButton("Refresh");
        JButton export = new JButton("Export CSV...");
        refresh.addActionListener(e -> reload());
        export.addActionListener(e -> exportCsv());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.add(refresh);
        buttons.add(export);
        buttons.add(info);
        add(buttons, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(chart, BorderLayout.SOUTH);
    }

    /** Loads the history on the worker thread and shows it on the Swing thread. */
    public void reload() {
        final int profileId = frame.currentProfileId();
        if (profileId <= 0) {
            return;
        }
        Async.run(frame.getApp().getWorker(), this, () -> frame.getApp().getSessions().history(profileId), this::show);
    }

    private void show(List<SessionSummary> list) {
        sessions = list;
        model.setRowCount(0);
        List<Double> cpm = new ArrayList<Double>();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);
        int n = 1;
        for (SessionSummary s : list) {
            model.addRow(new Object[]{n++, s.getMode(), fmt.format(new Date(s.getStartedAt())), s.getTargetChars(),
                CsvWriter.num(s.cpm(), 1), CsvWriter.num(s.correctionRate(), 3), CsvWriter.num(s.pressesPerChar(), 2),
                CsvWriter.num(s.getDurationMs() / 1000.0, 1)});
            cpm.add(s.cpm());
        }
        chart.setValues(cpm);
        info.setText(list.size() + " sessions (" + (frame.getApp().isMySql() ? "MySQL" : "offline files") + ")");
    }

    private void exportCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("session-history.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        final File file = chooser.getSelectedFile();
        final List<String[]> rows = new ArrayList<String[]>();
        for (SessionSummary s : sessions) {
            rows.add(new String[]{String.valueOf(s.getId()), s.getMode(), String.valueOf(s.getStartedAt()),
                String.valueOf(s.getTargetChars()), String.valueOf(s.getCorrectChars()), String.valueOf(s.getPresses()),
                String.valueOf(s.getSelections()), String.valueOf(s.getCorrections()), String.valueOf(s.getDurationMs()),
                CsvWriter.num(s.cpm(), 2)});
        }
        Async.run(frame.getApp().getWorker(), this, () -> {
            CsvWriter.write(file, new String[]{"id", "mode", "started_at_ms", "target_chars", "correct_chars", "presses",
                "selections", "corrections", "duration_ms", "cpm"}, rows);
            return file;
        }, f -> info.setText("Exported " + rows.size() + " sessions to " + f.getName()));
    }
}
