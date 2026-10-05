package com.switchtype;

import com.switchtype.exception.SwitchTypeException;
import com.switchtype.ui.AppContext;
import com.switchtype.ui.MainFrame;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * SwitchType: a single-switch scanning keyboard.
 * Run with: java -jar target/SwitchType.jar
 * The experiments run with: java -cp target/SwitchType.jar com.switchtype.sim.Experiments
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // the default look and feel works everywhere
            }
            try {
                MainFrame frame = new MainFrame(new AppContext());
                frame.setVisible(true);
                frame.startUp();
            } catch (SwitchTypeException e) {
                JOptionPane.showMessageDialog(null, "SwitchType cannot start: " + e.getMessage());
            }
        });
    }
}
