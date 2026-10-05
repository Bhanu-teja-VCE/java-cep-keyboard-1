package com.switchtype.ui;

import com.switchtype.exception.InvalidSettingsException;
import com.switchtype.model.Config;
import com.switchtype.model.LayoutType;
import com.switchtype.model.PredictionMode;
import com.switchtype.model.ScanStyle;
import com.switchtype.model.Settings;
import com.switchtype.model.SwitchChoice;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;

/**
 * Settings form for carers (mouse and keyboard). The person using the switch can change the
 * most important settings with the scanned settings keyboard instead.
 */
public class SettingsDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final JSpinner interval = spinner(Config.DEFAULT_INTERVAL_MS, Config.MIN_INTERVAL_MS, Config.MAX_INTERVAL_MS, 50);
    private final JSpinner extra = spinner(50, 0, Config.MAX_FIRST_EXTRA_PCT, 10);
    private final JSpinner cycles = spinner(2, 1, Config.MAX_ROW_CYCLES, 1);
    private final JSpinner keySize = spinner(Config.DEFAULT_KEY_SIZE, Config.MIN_KEY_SIZE, Config.MAX_KEY_SIZE, 4);
    private final JSpinner fontSize = spinner(Config.DEFAULT_FONT_SIZE, 12, 48, 2);
    private final JSpinner rest = spinner(20, 0, 240, 5);
    private final JComboBox<ScanStyle> style = new JComboBox<ScanStyle>(ScanStyle.values());
    private final JComboBox<LayoutType> layout = new JComboBox<LayoutType>(LayoutType.values());
    private final JComboBox<PredictionMode> prediction = new JComboBox<PredictionMode>(PredictionMode.values());
    private final JComboBox<SwitchChoice> switchChoice = new JComboBox<SwitchChoice>(SwitchChoice.values());
    private final JCheckBox adaptive = new JCheckBox("Adaptive scan speed");
    private final JCheckBox contrast = new JCheckBox("High-contrast theme");
    private final JCheckBox audio = new JCheckBox("Sound on every scan step");
    private final JLabel error = new JLabel(" ");
    private final Settings original;
    private Settings result;

    private SettingsDialog(JFrame owner, Settings s) {
        super(owner, "Settings", true);
        this.original = s;
        interval.setValue(s.getIntervalMs());
        extra.setValue(s.getFirstExtraPct());
        cycles.setValue(s.getRowCycles());
        keySize.setValue(s.getKeySize());
        fontSize.setValue(s.getFontSize());
        rest.setValue(s.getRestMinutes());
        style.setSelectedItem(s.getStyle());
        layout.setSelectedItem(s.getLayout());
        prediction.setSelectedItem(s.getPrediction());
        switchChoice.setSelectedItem(s.getSwitchChoice());
        adaptive.setSelected(s.isAdaptive());
        contrast.setSelected(s.isHighContrast());
        audio.setSelected(s.isAudio());

        JPanel form = new JPanel(new GridLayout(0, 2, 10, 6));
        row(form, "Scan interval (ms, 200 to 2000)", interval);
        row(form, "Extra delay on first item of a row/group (%)", extra);
        row(form, "Row/group cycles before going back", cycles);
        row(form, "Scan style", style);
        row(form, "Keyboard layout", layout);
        row(form, "Word prediction", prediction);
        row(form, "Switch", switchChoice);
        row(form, "Key size (px, at least 44)", keySize);
        row(form, "Font size", fontSize);
        row(form, "Rest reminder after (min, 0 = off)", rest);
        form.add(adaptive);
        form.add(contrast);
        form.add(audio);
        form.add(new JLabel(""));

        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        ok.addActionListener(e -> accept());
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(ok);
        buttons.add(cancel);
        error.setForeground(new Color(0xB00020));
        JPanel south = new JPanel(new BorderLayout());
        south.add(error, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        content.add(form, BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(ok);
        pack();
        setLocationRelativeTo(owner);
    }

    /** Shows the dialog and returns the new settings, or null if cancelled. */
    public static Settings edit(JFrame owner, Settings current) {
        SettingsDialog d = new SettingsDialog(owner, current);
        d.setVisible(true);
        return d.result;
    }

    private void accept() {
        Settings s = original.copy();
        s.setIntervalMs((Integer) interval.getValue());
        s.setFirstExtraPct((Integer) extra.getValue());
        s.setRowCycles((Integer) cycles.getValue());
        s.setKeySize((Integer) keySize.getValue());
        s.setFontSize((Integer) fontSize.getValue());
        s.setRestMinutes((Integer) rest.getValue());
        s.setStyle((ScanStyle) style.getSelectedItem());
        s.setLayout((LayoutType) layout.getSelectedItem());
        s.setPrediction((PredictionMode) prediction.getSelectedItem());
        s.setSwitchChoice((SwitchChoice) switchChoice.getSelectedItem());
        s.setAdaptive(adaptive.isSelected());
        s.setHighContrast(contrast.isSelected());
        s.setAudio(audio.isSelected());
        try {
            s.validate();
        } catch (InvalidSettingsException e) {
            error.setText(e.getMessage());
            return;
        }
        result = s;
        dispose();
    }

    private static void row(JPanel form, String label, java.awt.Component field) {
        form.add(new JLabel(label));
        form.add(field);
    }

    private static JSpinner spinner(int value, int min, int max, int step) {
        return new JSpinner(new SpinnerNumberModel(value, min, max, step));
    }
}
