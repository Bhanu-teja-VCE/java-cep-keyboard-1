package com.switchtype.scan;

import com.switchtype.model.Key;
import com.switchtype.model.KeyAction;
import com.switchtype.model.Outcome;
import com.switchtype.model.ScanStyle;
import com.switchtype.predict.CharFrequency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;

/**
 * The keys are split into two groups. Group A is highlighted first, then group B, then A
 * again. A press means "my key is in this group"; scanning then splits that group. When
 * one key is left it is selected. The splits come from a Huffman tree built from key
 * weights, so frequent keys need fewer presses and steps. Group A is always the heavier
 * half, because showing it costs no step.
 */
public class GroupBinaryScan extends ScanStrategy {

    /** A node of the Huffman tree: a single key (leaf) or two smaller groups. */
    static final class Node {
        final int id;
        final double weight;
        final Key key;
        final Node left;
        final Node right;
        final HashSet<Key> keys = new HashSet<Key>();
        final List<Key> keyList = new ArrayList<Key>();

        Node(int id, Key key, double weight) {
            this.id = id;
            this.key = key;
            this.weight = weight;
            this.left = null;
            this.right = null;
            keys.add(key);
            keyList.add(key);
        }

        Node(int id, Node left, Node right) {
            this.id = id;
            this.key = null;
            this.weight = left.weight + right.weight;
            this.left = left;
            this.right = right;
            keys.addAll(left.keys);
            keys.addAll(right.keys);
            keyList.addAll(left.keyList);
            keyList.addAll(right.keyList);
        }

        boolean isLeaf() {
            return key != null;
        }
    }

    private final Node root;
    /** Path of every key: '0' = group A (left), '1' = group B (right). */
    private final HashMap<Key, String> paths = new HashMap<Key, String>();
    private Node node;
    private boolean showingB;
    private int cycles;

    public GroupBinaryScan(Key[][] grid, int maxCycles, CharFrequency frequency) {
        super(grid, maxCycles);
        root = buildTree(grid, frequency);
        recordPaths(root, "");
        reset();
    }

    /**
     * Huffman's algorithm: repeatedly join the two lightest nodes. A TreeSet ordered by
     * weight and then id is the priority queue (Unit III: TreeSet with a Comparator).
     */
    private static Node buildTree(Key[][] grid, CharFrequency frequency) {
        TreeSet<Node> queue = new TreeSet<Node>(new Comparator<Node>() {
            @Override
            public int compare(Node a, Node b) {
                int byWeight = Double.compare(a.weight, b.weight);
                return byWeight != 0 ? byWeight : Integer.compare(a.id, b.id);
            }
        });
        int nextId = 0;
        for (Key[] row : grid) {
            for (Key k : row) {
                queue.add(new Node(nextId++, k, weightOf(k, frequency)));
            }
        }
        while (queue.size() > 1) {
            Node lighter = queue.pollFirst();
            Node heavier = queue.pollFirst();
            queue.add(new Node(nextId++, heavier, lighter));   // heavier group is shown first
        }
        return queue.first();
    }

    /**
     * Weight of a key in the tree. Characters use corpus counts (+1 so none is zero).
     * Other keys use assumed shares of the total (see DESIGN.md, section 10).
     */
    static double weightOf(Key k, CharFrequency frequency) {
        double total = (frequency == null) ? 0 : frequency.getTotal();
        if (total == 0) {
            return 1.0;                                // no statistics: all keys equal
        }
        if (k.isChar()) {
            return frequency.count(k.getChar()) + 1;
        }
        KeyAction a = k.getAction();
        if (a == KeyAction.BACKSPACE) {
            return total * 0.02;
        }
        if (a == KeyAction.UNDO_WORD) {
            return total * 0.01;
        }
        if (a == KeyAction.PREDICTION) {
            return total * 0.03;
        }
        return total * 0.002;
    }

    private void recordPaths(Node n, String path) {
        if (n.isLeaf()) {
            paths.put(n.key, path);
            return;
        }
        recordPaths(n.left, path + "0");
        recordPaths(n.right, path + "1");
    }

    @Override
    public void reset() {
        node = root;
        showingB = false;
        cycles = 0;
    }

    @Override
    public boolean step() {
        if (!showingB) {
            showingB = true;
            return false;
        }
        showingB = false;
        cycles++;
        if (node != root && cycles >= maxCycles) {
            reset();                                // neither group was chosen: start again
            return true;
        }
        return false;
    }

    @Override
    public Outcome press() {
        Node chosen = showingB ? node.right : node.left;
        if (chosen.isLeaf()) {
            reset();
            return Outcome.selected(chosen.key);
        }
        node = chosen;
        showingB = false;
        cycles = 0;
        return Outcome.narrowed();
    }

    private Node current() {
        return showingB ? node.right : node.left;
    }

    @Override
    public List<Key> highlighted() {
        return Collections.unmodifiableList(current().keyList);
    }

    @Override
    public boolean isHighlighted(Key k) {
        return current().keys.contains(k);
    }

    @Override
    public boolean isFirstItem() {
        return !showingB;
    }

    @Override
    public ScanStyle getStyle() {
        return ScanStyle.GROUP_BINARY;
    }

    /** One step for every "group B" choice on the path from the root to the key. */
    @Override
    public int analyticSteps(Key k) {
        String path = paths.get(k);
        int steps = 0;
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) == '1') {
                steps++;
            }
        }
        return steps;
    }

    /** One press per level of the tree. */
    @Override
    public int analyticPresses(Key k) {
        return paths.get(k).length();
    }
}
