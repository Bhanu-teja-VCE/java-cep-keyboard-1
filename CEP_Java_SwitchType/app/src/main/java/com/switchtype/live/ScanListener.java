package com.switchtype.live;

import com.switchtype.model.Key;
import com.switchtype.model.Outcome;

import java.util.List;

/**
 * Receives what the scanner thread does. In the live app these methods run on the Swing
 * event dispatch thread (the scanner hands them over with invokeLater).
 */
public interface ScanListener {

    /** The highlight moved; keys is an immutable snapshot. */
    void highlightChanged(List<Key> keys);

    /** A switch press was processed. */
    void pressed(Outcome outcome);

    /** The scanner gave up on a row or group (the user let all cycles pass). */
    void gaveUp();
}
