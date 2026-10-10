package com.switchtype.ui;

import com.switchtype.live.Recommender;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Calibration wizard: about 10 reaction-time trials ("press when the box changes"), then a
 * recommendation from the experiment results (sp_recommend_settings, or the bundled table
 * offline). Unit IV: CardLayout switches between the intro, test and result screens.
 */
public class CalibrationPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    static final int TRIALS = 10;

    private enum State { IDLE, WAITING, SHOWN, DONE }

    private final MainFrame frame;
    private final CardLayout cards = new CardLayout();
    private final JPanel deck = new JPanel(cards);
    private final JLabel box = new JLabel("", SwingConstants.CENTER);
    private final JLabel trialLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel resultText = new JLabel(" ");
    private final JButton accept = new JButton("Use these settings");
    private final Random random = new Random();
    private final List<Double> reactions = new ArrayList<Double>();
    private Timer timer;
    private State state = State.IDLE;
    private long shownAt;
    private int early;
    private Recommender.Recommendation recommendation;

    public CalibrationPanel(MainFrame frame) {
        super(new BorderLayout());
        this.frame = frame;
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel intro = new JPanel(new BorderLayout(0, 16));
        JLabel text = new JLabel("<html><h2>Reaction-time calibration</h2>"
                + "<p>A box will change from grey to bright with the words PRESS NOW.<br>"
                + "Press your switch as soon as it changes. There are " + TRIALS + " short trials.</p>"
                + "<p>The result is matched to the closest <b>simulated</b> user type from the experiments.<br>"
                + "It is a starting point you can change, not a medical assessment.</p>"
                + "<p>Press the switch or the button to begin.</p></html>");
        JButton start = new JButton("Start calibration");
        start.setFocusable(false);
        start.addActionListener(e -> begin());
        text.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        intro.add(text, BorderLayout.NORTH);
        JPanel startRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        startRow.add(start);
        intro.add(startRow, BorderLayout.CENTER);

        JPanel test = new JPanel(new BorderLayout(0, 12));
        box.setOpaque(true);
        box.setPreferredSize(new Dimension(420, 260));
        box.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 40));
        trialLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
        test.add(trialLabel, BorderLayout.NORTH);
        test.add(wrap(box), BorderLayout.CENTER);

        JPanel result = new JPanel(new BorderLayout(0, 16));
        JButton again = new JButton("Run again");
        again.setFocusable(false);
        accept.setFocusable(false);
        again.addActionListener(e -> begin());
        accept.addActionListener(e -> frame.applyRecommendation(recommendation));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.add(accept);
        buttons.add(again);
        resultText.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        result.add(resultText, BorderLayout.NORTH);
        result.add(buttons, BorderLayout.CENTER);

        deck.add(intro, "intro");
        deck.add(test, "test");
        deck.add(result, "result");
        add(deck, BorderLayout.CENTER);
        showWaiting();
    }

    private static JPanel wrap(java.awt.Component c) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER));
        p.add(c);
        return p;
    }

    private void begin() {
        recommendation = null;
        reactions.clear();
        early = 0;
        cards.show(deck, "test");
        nextTrial();
    }

    private void nextTrial() {
        state = State.WAITING;
        showWaiting();
        trialLabel.setText("Trial " + (reactions.size() + 1) + " of " + TRIALS + "  -  wait for the change");
        if (timer != null) {
            timer.stop();
        }
        timer = new Timer(1000 + random.nextInt(2000), e -> showTarget());   // random wait: 1 to 3 s
        timer.setRepeats(false);
        timer.start();
    }

    private void showWaiting() {
        Theme t = Theme.NORMAL;
        box.setBackground(t.controlFill);
        box.setForeground(t.controlText);
        box.setBorder(BorderFactory.createLineBorder(t.keyBorder, 2));
        box.setText("wait...");
    }

    private void showTarget() {
        Theme t = Theme.NORMAL;
        box.setBackground(t.highlightFill);                  // colour AND text AND border change
        box.setForeground(t.highlightText);
        box.setBorder(BorderFactory.createLineBorder(t.highlightBorder, 8));
        box.setText("PRESS NOW");
        trialLabel.setText("Trial " + (reactions.size() + 1) + " of " + TRIALS + "  -  press now!");
        state = State.SHOWN;
        shownAt = System.nanoTime();
    }

    /** The switch was pressed while this tab is shown. */
    public void switchDown() {
        long now = System.nanoTime();
        switch (state) {
            case IDLE:
            case DONE:
                begin();
                break;
            case WAITING:
                early++;
                trialLabel.setText("Too early - this trial starts again.");
                nextTrial();
                break;
            case SHOWN:
                reactions.add((now - shownAt) / 1e6);
                if (reactions.size() == TRIALS) {
                    finish();
                } else {
                    nextTrial();
                }
                break;
            default:
                break;
        }
    }

    private void finish() {
        state = State.DONE;
        double mean = 0;
        for (double r : reactions) {
            mean += r;
        }
        mean /= reactions.size();
        double var = 0;
        for (double r : reactions) {
            var += (r - mean) * (r - mean);
        }
        final double sd = Math.sqrt(var / (reactions.size() - 1));
        final double m = mean;
        resultText.setText("<html><h2>Your reaction time</h2><p>Mean " + Math.round(m) + " ms, spread (SD) "
                + Math.round(sd) + " ms, early presses " + early + ".</p><p>Looking up recommended settings...</p></html>");
        accept.setEnabled(false);
        cards.show(deck, "result");
        Async.run(frame.getApp().getWorker(), this, () -> {
            Recommender.Recommendation r = null;
            if (frame.getApp().isMySql()) {
                r = Recommender.fromDatabase((int) Math.round(m), (int) Math.round(sd));   // stored procedure
            }
            if (r == null) {
                r = Recommender.closest(Recommender.bundledTable(), m, sd);
            }
            return r;
        }, r -> showRecommendation(m, sd, r));
    }

    private void showRecommendation(double mean, double sd, Recommender.Recommendation r) {
        recommendation = r;
        accept.setEnabled(r != null);
        resultText.setText("<html><h2>Your reaction time</h2><p>Mean " + Math.round(mean) + " ms, spread (SD) "
                + Math.round(sd) + " ms, early presses " + early + ".</p>"
                + "<h3>Recommended starting settings</h3>"
                + "<p>Closest simulated user type: <b>" + r.label + "</b> (" + r.reactionMean + " / " + r.reactionSd + " ms)<br>"
                + "Scan style: <b>" + r.style + "</b>, layout: <b>" + r.layout + "</b>, prediction: <b>" + r.prediction
                + "</b>, interval: <b>" + r.intervalMs + " ms</b><br>"
                + "In the simulation this type reached " + Math.round(r.cpm) + " characters per minute.</p>"
                + "<p>These come from simulated users, not from real people. Adjust them if they do not feel right.</p></html>");
    }

    /** True while the box shows PRESS NOW (used by the screenshot tour). */
    public boolean isWaitingForPress() {
        return state == State.SHOWN;
    }

    /** True once the recommendation is on screen. */
    public boolean hasRecommendation() {
        return recommendation != null && state == State.DONE;
    }

    /** Stops the trial timer when the tab is left. */
    public void leave() {
        if (timer != null) {
            timer.stop();
        }
        if (state == State.WAITING || state == State.SHOWN) {
            state = State.IDLE;
            cards.show(deck, "intro");
        }
    }
}
