package com.switchtype.ui;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

/** A small line chart of characters per minute per session, drawn with Graphics2D. */
public class HistoryChart extends JComponent {

    private static final long serialVersionUID = 1L;
    private static final Color AXIS = new Color(0x5F6670);
    private static final Color LINE = new Color(0x1E40AF);
    private static final Color INK = new Color(0x1F2328);

    private List<Double> values = new ArrayList<Double>();

    public HistoryChart() {
        setPreferredSize(new Dimension(600, 220));
    }

    public void setValues(List<Double> cpm) {
        this.values = new ArrayList<Double>(cpm);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        FontMetrics fm = g.getFontMetrics();
        int left = 56;
        int right = 16;
        int top = 28;
        int bottom = 36;
        int w = getWidth() - left - right;
        int h = getHeight() - top - bottom;
        g.setColor(INK);
        g.drawString("Characters per minute per session", left, 18);
        if (values.isEmpty()) {
            g.drawString("No sessions yet. Finish a practice phrase to see it here.", left, top + h / 2);
            g.dispose();
            return;
        }
        double max = 1;
        for (double v : values) {
            max = Math.max(max, v);
        }
        max = Math.ceil(max / 10) * 10;
        g.setColor(AXIS);
        g.drawLine(left, top + h, left + w, top + h);
        g.drawLine(left, top, left, top + h);
        for (int i = 0; i <= 4; i++) {
            int y = top + h - i * h / 4;
            String label = String.valueOf(Math.round(max * i / 4));
            g.drawString(label, left - 8 - fm.stringWidth(label), y + fm.getAscent() / 2);
        }
        g.drawString("session", left + w - fm.stringWidth("session"), top + h + 28);
        int n = values.size();
        int prevX = -1;
        int prevY = -1;
        g.setStroke(new BasicStroke(2.5f));
        for (int i = 0; i < n; i++) {
            int x = left + (n == 1 ? w / 2 : i * w / (n - 1));
            int y = top + h - (int) Math.round(values.get(i) / max * h);
            g.setColor(LINE);
            if (prevX >= 0) {
                g.drawLine(prevX, prevY, x, y);
            }
            g.fillOval(x - 4, y - 4, 8, 8);
            prevX = x;
            prevY = y;
        }
        g.dispose();
    }
}
