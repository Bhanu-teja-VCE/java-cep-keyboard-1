package com.switchtype.ui;

import com.switchtype.db.SessionSummary;
import com.switchtype.live.SessionController;
import com.switchtype.model.Key;
import com.switchtype.model.Settings;
import com.switchtype.model.SwitchChoice;
import com.switchtype.model.UserProfile;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * The typing screen: status line, practice phrase, the text, the keyboard and messages.
 * The mouse buttons in the tool bar are for carers; the person needs only the switch.
 */
public class KeyboardPanel extends JPanel implements SessionController.View {

    private static final long serialVersionUID = 1L;

    private final AppContext app;
    private final MainFrame frame;
    private final KeyboardView view = new KeyboardView();
    private final JScrollPane keys = new JScrollPane(view);
    private final JPanel center = new JPanel(new BorderLayout(0, 6));
    private final JTextArea text = new JTextArea(2, 40);
    private final JLabel status = new JLabel(" ");
    private final JLabel target = new JLabel(" ");
    private final JLabel message = new JLabel(" ");
    private final JLabel restBanner = new JLabel(" ");
    private final JPanel top = new JPanel(new GridLayout(0, 1, 0, 2));
    private final JPanel south = new JPanel(new GridLayout(1, 0, 12, 0));
    private final JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final AudioCue audio = new AudioCue();
    private SessionController controller;
    private UserProfile profile;
    private List<Key> lastHighlight;

    public KeyboardPanel(AppContext app, MainFrame frame) {
        super(new BorderLayout(0, 6));
        this.app = app;
        this.frame = frame;
        setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

        JButton settingsButton = toolButton("Settings...");
        settingsButton.addActionListener(e -> openSettingsDialog());
        JButton practiceButton = toolButton("Start / stop practice");
        practiceButton.addActionListener(e -> togglePractice());
        tools.add(settingsButton);
        tools.add(practiceButton);
        tools.add(status);

        top.add(tools);
        top.add(target);
        add(top, BorderLayout.NORTH);

        text.setEditable(false);
        text.setFocusable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        center.add(new JScrollPane(text), BorderLayout.NORTH);
        keys.setBorder(BorderFactory.createEmptyBorder());
        center.add(keys, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        south.add(message);
        south.add(restBanner);
        add(south, BorderLayout.SOUTH);

        // Unit IV: an adapter class, so only the needed mouse method is written
        view.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (controller != null && controller.getSettings().getSwitchChoice() == SwitchChoice.MOUSE) {
                    controller.switchDown();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (controller != null) {
                    controller.switchUp();
                }
            }
        });

        // rest reminder (tiredness): checked every 30 s on the Swing thread
        new Timer(30000, e -> checkRest()).start();
    }

    private static JButton toolButton(String label) {
        JButton b = new JButton(label);
        b.setFocusable(false);          // the Space bar must reach the switch, never a button
        return b;
    }

    /** Starts (or restarts) typing for a profile. */
    public void start(UserProfile p) {
        stop();
        profile = p;
        controller = new SessionController(app.getFrequency(), app.getModel(), app.getPhrases().test());
        controller.start(p.getSettings(), p.getId(), this);
        applyLook(p.getSettings());
        refresh();
    }

    /** Stops the scanner thread and saves the free-typing summary. */
    public void stop() {
        if (controller != null) {
            try {
                controller.shutdown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            controller = null;
        }
    }

    public SessionController getController() {
        return controller;
    }

    private void openSettingsDialog() {
        if (controller == null) {
            return;
        }
        Settings changed = SettingsDialog.edit(frame, controller.getSettings());
        if (changed != null) {
            controller.applySettings(changed);
            settingsChanged(changed);
        }
    }

    private void togglePractice() {
        if (controller == null) {
            return;
        }
        if (controller.getPracticeTarget() == null) {
            controller.startPractice();
        } else {
            controller.stopPractice();
        }
        refresh();
    }

    private void checkRest() {
        if (controller == null) {
            return;
        }
        int limit = controller.getSettings().getRestMinutes();
        long minutes = controller.minutesSinceRest();
        restBanner.setText(limit > 0 && minutes >= limit
                ? "You have been typing for " + minutes + " minutes. A short rest helps: select 'pause' and relax."
                : " ");
    }

    /** Colours and sizes from the settings. */
    public void applyLook(Settings s) {
        Theme theme = Theme.of(s.isHighContrast());
        view.setLook(theme, s.getKeySize(), s.getFontSize());
        Font big = new Font(Font.SANS_SERIF, Font.PLAIN, s.getFontSize() + 4);
        Font body = new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(14, s.getFontSize() - 4));
        text.setFont(big);
        text.setBackground(theme.keyFill);
        text.setForeground(theme.keyText);
        for (JPanel p : new JPanel[]{this, top, south, tools, center}) {
            p.setBackground(theme.background);
        }
        keys.getViewport().setBackground(theme.background);
        keys.setBackground(theme.background);
        for (JLabel l : new JLabel[]{status, target, message, restBanner}) {
            l.setFont(body);
            l.setForeground(theme.text);
        }
        status.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        target.setFont(big.deriveFont(Font.BOLD));
    }

    // ---------------------------------------------------------------- SessionController.View

    @Override
    public void refresh() {
        if (controller == null) {
            return;
        }
        List<Key> lit = controller.getHighlighted();
        if (lit != lastHighlight && !lit.isEmpty() && controller.getSettings().isAudio()) {
            audio.play();
        }
        lastHighlight = lit;
        view.setState(controller.getGrid(), lit, controller.dynamicLabels(), controller.isPaused());
        text.setText(controller.getText() + "|");
        Settings s = controller.getSettings();
        status.setText("  " + profile.getName() + "  |  " + s.getStyle() + ", " + s.getLayout() + ", prediction "
                + s.getPrediction() + "  |  " + controller.getCurrentIntervalMs() + " ms" + (s.isAdaptive() ? " adaptive" : "")
                + "  |  switch: " + s.getSwitchChoice() + "  |  " + (app.isMySql() ? "MySQL" : "offline"));
        String phrase = controller.getPracticeTarget();
        target.setText(phrase == null ? "Free typing" : "Type:  " + phrase);
    }

    @Override
    public void message(String text) {
        message.setText(text.isEmpty() ? " " : text);
        if (text.startsWith("Paused")) {
            restBanner.setText(" ");
        }
    }

    @Override
    public void saveSession(final SessionSummary summary, final List<String[]> events) {
        Async.run(app.getWorker(), this, () -> {
            app.getSessions().save(summary, events);
            return summary;
        }, s -> frame.sessionSaved());
    }

    @Override
    public void settingsChanged(Settings settings) {
        applyLook(settings);
        profile.setSettings(settings);
        frame.saveProfile(profile);
        refresh();
    }
}
