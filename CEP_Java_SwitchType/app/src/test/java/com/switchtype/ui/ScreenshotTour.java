package com.switchtype.ui;

import com.switchtype.live.SessionController;
import com.switchtype.model.Key;
import com.switchtype.model.KeyAction;
import com.switchtype.model.LayoutType;
import com.switchtype.model.PredictionMode;
import com.switchtype.model.ScanStyle;
import com.switchtype.model.Settings;
import com.switchtype.scan.KeyboardLayout;

import javax.imageio.ImageIO;
import javax.swing.JDialog;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.Robot;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Drives the real window and saves screenshots of every tab. The switch is pressed with
 * java.awt.Robot (Space bar) only while the SwitchType window is the active window;
 * otherwise the same switch method is called directly, and the tour says so.
 * <pre>  java -cp test-classes;classes;deps com.switchtype.ui.ScreenshotTour ../screenshots</pre>
 * (On Linux it runs under Xvfb: xvfb-run -a java -cp ... com.switchtype.ui.ScreenshotTour out)
 */
public final class ScreenshotTour {

    private static Robot robot;
    private static MainFrame frame;
    private static File out;
    private static int robotPresses;
    private static int directPresses;

    private ScreenshotTour() {
    }

    public static void main(String[] args) throws Exception {
        out = new File(args.length > 0 ? args[0] : "screenshots");
        if (!out.exists() && !out.mkdirs()) {
            throw new IllegalStateException("Cannot create " + out);
        }
        robot = new Robot();
        SwingUtilities.invokeAndWait(() -> {
            try {
                javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
                frame = new MainFrame(new AppContext());
                frame.setVisible(true);
                frame.toFront();
                frame.startUp();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        waitFor(() -> frame.getKeyboard().getController() != null, 15000);
        robot.delay(800);

        // 1. row-column, frequency layout, bigram prediction: type a word with the switch
        configure(ScanStyle.ROW_COLUMN, LayoutType.FREQUENCY, PredictionMode.BIGRAM, 450, false);
        typeWithSwitch("hello ");
        shot("01-keyboard-row-column.png");

        // 2. practice mode, part of the phrase typed
        onEdt(() -> frame.getKeyboard().getController().startPractice());
        String target = read(() -> frame.getKeyboard().getController().getPracticeTarget());
        typeWithSwitch(target.substring(0, 6));
        waitForHighlight(true);
        shot("02-practice-mode.png");
        onEdt(() -> frame.getKeyboard().getController().stopPractice());

        // 3. group-binary scanning: the highlighted group is spread over the keyboard
        configure(ScanStyle.GROUP_BINARY, LayoutType.FREQUENCY, PredictionMode.BIGRAM, 600, false);
        typeWithSwitch("hi");
        robot.delay(700);
        shot("03-group-binary.png");

        // 4. linear scanning on QWERTY, prediction off
        configure(ScanStyle.LINEAR, LayoutType.QWERTY, PredictionMode.OFF, 450, false);
        robot.delay(2000);
        shot("04-linear-qwerty.png");

        // 5. the scanned settings keyboard, opened with the switch
        configure(ScanStyle.ROW_COLUMN, LayoutType.ALPHABETICAL, PredictionMode.UNIGRAM, 450, false);
        selectWithSwitch(KeyAction.SETTINGS);
        robot.delay(1200);
        shot("05-settings-keyboard.png");
        selectWithSwitch(KeyAction.BACK);

        // 6. high-contrast theme and bigger keys
        Settings hc = read(() -> frame.getKeyboard().getController().getSettings());
        hc.setHighContrast(true);
        hc.setKeySize(64);
        hc.setFontSize(22);
        hc.setStyle(ScanStyle.ROW_COLUMN);
        hc.setLayout(LayoutType.FREQUENCY);
        hc.setPrediction(PredictionMode.BIGRAM);
        apply(hc);
        typeWithSwitch(" i ");
        robot.delay(600);
        shot("06-high-contrast.png");

        // 7. paused with the switch
        selectWithSwitch(KeyAction.PAUSE);
        robot.delay(600);
        shot("07-paused.png");
        pressSwitch();                                       // resume
        hc.setHighContrast(false);
        hc.setKeySize(56);
        hc.setFontSize(20);
        apply(hc);

        // 8-10. calibration wizard: intro, trial, result
        onEdt(() -> frame.getTabs().setSelectedIndex(1));
        robot.delay(500);
        shot("08-calibration-intro.png");
        onEdt(() -> frame.getCalibration().switchDown());     // start
        for (int i = 0; i < CalibrationPanel.TRIALS; i++) {
            waitFor(() -> frame.getCalibration().isWaitingForPress(), 5000);   // the box changes after 1 to 3 s
            if (i == 3) {
                shot("09-calibration-trial.png");
            }
            robot.delay(420 + 60 * (i % 4));                 // the tour's own "reaction time"
            onEdt(() -> frame.getCalibration().switchDown());
        }
        waitFor(() -> frame.getCalibration().hasRecommendation(), 10000);
        robot.delay(300);
        shot("10-calibration-result.png");

        // 11-13. history, profiles, data & privacy
        onEdt(() -> frame.getTabs().setSelectedIndex(2));
        robot.delay(1500);
        shot("11-history.png");
        onEdt(() -> frame.getTabs().setSelectedIndex(3));
        robot.delay(800);
        shot("12-profiles.png");
        onEdt(() -> frame.getTabs().setSelectedIndex(4));
        onEdt(() -> frame.getData().showTable("typing_session"));
        robot.delay(1500);
        shot("13-data-privacy.png");

        // 14. the carer settings dialog
        onEdt(() -> frame.getTabs().setSelectedIndex(0));
        final Settings current = read(() -> frame.getKeyboard().getController().getSettings());
        SwingUtilities.invokeLater(() -> SettingsDialog.edit(frame, current));
        robot.delay(1200);
        for (Window w : Window.getWindows()) {
            if (w instanceof JDialog && w.isShowing()) {
                capture(((JDialog) w).getRootPane(), "14-settings-dialog.png");
                SwingUtilities.invokeLater(w::dispose);
            }
        }
        System.out.println("Switch presses: " + robotPresses + " with java.awt.Robot (Space), "
                + directPresses + " direct calls because the window was not active");
        robot.delay(500);
        System.exit(0);
    }

    // ------------------------------------------------------------------ helpers

    private static void configure(ScanStyle style, LayoutType layout, PredictionMode mode, int interval, boolean adaptive)
            throws Exception {
        Settings s = read(() -> frame.getKeyboard().getController().getSettings());
        s.setStyle(style);
        s.setLayout(layout);
        s.setPrediction(mode);
        s.setIntervalMs(interval);
        s.setAdaptive(adaptive);
        s.setFirstExtraPct(30);
        apply(s);
        onEdt(() -> frame.getTabs().setSelectedIndex(0));
    }

    private static void apply(final Settings s) throws Exception {
        onEdt(() -> {
            frame.getKeyboard().getController().applySettings(s);
            frame.getKeyboard().applyLook(s);
        });
        robot.delay(300);
    }

    /** Types text like a perfect switch user: press when the wanted key is highlighted. */
    private static void typeWithSwitch(String text) throws Exception {
        for (char c : text.toCharArray()) {
            final char ch = c;
            String before = read(() -> frame.getKeyboard().getController().getText());
            Key target = read(() -> KeyboardLayout.findChar(frame.getKeyboard().getController().getGrid(), ch));
            selectKey(target, () -> !frame.getKeyboard().getController().getText().equals(before));
        }
    }

    private static void selectWithSwitch(KeyAction action) throws Exception {
        Key target = read(() -> KeyboardLayout.find(frame.getKeyboard().getController().getGrid(), action, 0));
        Object gridBefore = read(() -> frame.getKeyboard().getController().getGrid());
        selectKey(target, () -> frame.getKeyboard().getController().getGrid() != gridBefore
                || frame.getKeyboard().getController().isPaused());
    }

    /** Presses whenever the target is inside the highlighted item, until the key has been selected. */
    private static void selectKey(Key target, Check selected) throws Exception {
        long deadline = System.currentTimeMillis() + 60000;
        while (!read(selected::ok)) {
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("Could not select " + target);
            }
            if (read(() -> frame.getKeyboard().getController().getHighlighted().contains(target))) {
                pressSwitch();
                // wait until the highlight moves away from the item we pressed on
                waitFor(() -> !frame.getKeyboard().getController().getHighlighted().contains(target)
                        || selected.ok(), 3000);
            } else {
                robot.delay(15);
            }
        }
    }

    private static void pressSwitch() throws Exception {
        if (read(() -> frame.isActive())) {
            robot.keyPress(KeyEvent.VK_SPACE);
            robot.delay(40);
            robot.keyRelease(KeyEvent.VK_SPACE);
            robotPresses++;
        } else {
            onEdt(() -> {
                SessionController c = frame.getKeyboard().getController();
                c.switchDown();
                c.switchUp();
            });
            directPresses++;
        }
        robot.delay(60);
    }

    private static void waitForHighlight(boolean any) throws Exception {
        waitFor(() -> frame.getKeyboard().getController().getHighlighted().isEmpty() != any, 3000);
    }

    /**
     * Paints the window's content (without the OS title bar) into a PNG at a fixed size: the
     * usable area of a 1280 x 720 laptop screen. The root pane is laid out at that size for the
     * capture and restored afterwards, so other programs resizing the window do not matter.
     */
    private static void shot(String name) throws Exception {
        final JRootPane root = frame.getRootPane();
        final AtomicReference<BufferedImage> image = new AtomicReference<BufferedImage>();
        SwingUtilities.invokeAndWait(() -> {
            java.awt.Dimension old = root.getSize();
            root.setSize(SHOT_WIDTH, SHOT_HEIGHT);
            root.validate();
            image.set(paint(root));
            root.setSize(old);
            root.validate();
        });
        ImageIO.write(image.get(), "png", new File(out, name));
        System.out.println("saved " + name);
    }

    private static BufferedImage paint(JRootPane root) {
        BufferedImage img = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        root.printAll(g);
        g.dispose();
        return img;
    }

    static final int SHOT_WIDTH = 1280;
    static final int SHOT_HEIGHT = 690;

    private static void capture(final JRootPane root, final String name) throws Exception {
        final AtomicReference<BufferedImage> image = new AtomicReference<BufferedImage>();
        SwingUtilities.invokeAndWait(() -> image.set(paint(root)));
        ImageIO.write(image.get(), "png", new File(out, name));
        System.out.println("saved " + name);
    }

    // ------------------------------------------------------------------ EDT plumbing

    interface Check {
        boolean ok();
    }

    interface Getter<T> {
        T get();
    }

    private static <T> T read(final Getter<T> g) throws Exception {
        final AtomicReference<T> ref = new AtomicReference<T>();
        SwingUtilities.invokeAndWait(() -> ref.set(g.get()));
        return ref.get();
    }

    private static void onEdt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    private static void waitFor(Check check, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!read(check::ok) && System.currentTimeMillis() < deadline) {
            robot.delay(20);
        }
    }
}
