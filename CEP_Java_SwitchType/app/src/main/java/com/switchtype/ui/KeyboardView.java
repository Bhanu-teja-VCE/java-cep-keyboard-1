package com.switchtype.ui;

import com.switchtype.model.Key;
import com.switchtype.model.KeyAction;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Paints the keyboard with Graphics2D (Unit IV: custom painting). The highlight is shown
 * by a dark/bright fill AND a thick border, so it never relies on colour alone (WCAG 1.4.1).
 */
public class KeyboardView extends JComponent {

    static final int GAP = 6;
    static final int MARGIN = 12;
    static final int NORMAL_BORDER = 2;
    static final int HIGHLIGHT_BORDER = 6;

    private Key[][] grid = new Key[0][];
    private Set<Key> highlighted = new HashSet<Key>();
    private Map<Key, String> labels = new HashMap<Key, String>();
    private Theme theme = Theme.NORMAL;
    private int keySize = 56;
    private int fontSize = 20;
    private boolean paused;

    public KeyboardView() {
        setOpaque(true);
    }

    /** Updates everything the painter needs; called on the Swing thread. */
    public void setState(Key[][] grid, List<Key> highlighted, Map<Key, String> labels, boolean paused) {
        this.grid = grid;
        this.highlighted = new HashSet<Key>(highlighted);
        this.labels = labels;
        this.paused = paused;
        revalidate();
        repaint();
    }

    public void setLook(Theme theme, int keySize, int fontSize) {
        this.theme = theme;
        this.keySize = keySize;
        this.fontSize = fontSize;
        revalidate();
        repaint();
    }

    /** Width of a key in key units: words and long labels get wider keys. */
    static int units(Key k) {
        if (k.getAction() == KeyAction.PREDICTION) {
            return 3;
        }
        if (k.isChar()) {
            return k.getChar() == ' ' ? 3 : (k.getChar() == '\n' ? 2 : 1);
        }
        return 3;
    }

    /** Where every key is drawn. Static so tests can check sizes without a window. */
    public static Map<Key, Rectangle> keyBounds(Key[][] grid, int keySize) {
        Map<Key, Rectangle> bounds = new LinkedHashMap<Key, Rectangle>();
        int y = MARGIN;
        for (Key[] row : grid) {
            int x = MARGIN;
            for (Key k : row) {
                int w = units(k) * keySize + (units(k) - 1) * GAP;
                bounds.put(k, new Rectangle(x, y, w, keySize));
                x += w + GAP;
            }
            y += keySize + GAP;
        }
        return bounds;
    }

    @Override
    public Dimension getPreferredSize() {
        int width = 0;
        int height = MARGIN;
        for (Rectangle r : keyBounds(grid, keySize).values()) {
            width = Math.max(width, r.x + r.width);
            height = Math.max(height, r.y + r.height);
        }
        return new Dimension(width + MARGIN, height + MARGIN);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(theme.background);
        g.fillRect(0, 0, getWidth(), getHeight());
        for (Map.Entry<Key, Rectangle> e : keyBounds(grid, keySize).entrySet()) {
            paintKey(g, e.getKey(), e.getValue());
        }
        if (paused) {
            paintPaused(g);
        }
        g.dispose();
    }

    private void paintKey(Graphics2D g, Key k, Rectangle r) {
        boolean lit = highlighted.contains(k);
        java.awt.Color fill;
        java.awt.Color ink;
        if (lit) {
            fill = theme.highlightFill;
            ink = theme.highlightText;
        } else if (k.getAction() == KeyAction.PREDICTION) {
            fill = theme.predictionFill;
            ink = theme.predictionText;
        } else if (!k.isChar() || k.getChar() == ' ' || k.getChar() == '\n') {
            fill = theme.controlFill;
            ink = theme.controlText;
        } else {
            fill = theme.keyFill;
            ink = theme.keyText;
        }
        g.setColor(fill);
        g.fillRoundRect(r.x, r.y, r.width, r.height, 10, 10);
        int border = lit ? HIGHLIGHT_BORDER : NORMAL_BORDER;
        g.setColor(lit ? theme.highlightBorder : theme.keyBorder);
        g.setStroke(new BasicStroke(border));
        g.drawRoundRect(r.x + border / 2, r.y + border / 2, r.width - border, r.height - border, 10, 10);

        String label = labels.containsKey(k) ? labels.get(k) : k.getLabel();
        drawCentered(g, label, r, ink, lit);
    }

    /** Draws the label centred, shrinking the font until it fits inside the key. */
    private void drawCentered(Graphics2D g, String label, Rectangle r, java.awt.Color ink, boolean bold) {
        if (label.isEmpty()) {
            return;
        }
        int size = fontSize;
        Font font;
        FontMetrics fm;
        do {
            font = new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, size);
            fm = g.getFontMetrics(font);
            size--;
        } while (fm.stringWidth(label) > r.width - 2 * HIGHLIGHT_BORDER - 4 && size > 9);
        g.setFont(font);
        g.setColor(ink);
        int x = r.x + (r.width - fm.stringWidth(label)) / 2;
        int y = r.y + (r.height - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(label, x, y);
    }

    private void paintPaused(Graphics2D g) {
        String text = "Paused - press the switch to continue";
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, fontSize + 4);
        FontMetrics fm = g.getFontMetrics(font);
        int w = fm.stringWidth(text) + 40;
        int h = fm.getHeight() + 24;
        int x = (getWidth() - w) / 2;
        int y = (getHeight() - h) / 2;
        g.setColor(theme.highlightFill);
        g.fillRoundRect(x, y, w, h, 16, 16);
        g.setColor(theme.highlightBorder);
        g.setStroke(new BasicStroke(HIGHLIGHT_BORDER));
        g.drawRoundRect(x, y, w, h, 16, 16);
        g.setFont(font);
        g.setColor(theme.highlightText);
        g.drawString(text, x + 20, y + 12 + fm.getAscent());
    }

    /** Keys currently highlighted (for tests and the screenshot tour). */
    public Set<Key> getHighlighted() {
        return Collections.unmodifiableSet(highlighted);
    }
}
