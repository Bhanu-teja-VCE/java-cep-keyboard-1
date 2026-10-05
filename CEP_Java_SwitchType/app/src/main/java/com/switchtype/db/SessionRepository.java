package com.switchtype.db;

import com.switchtype.exception.ProfileStoreException;

import java.util.List;

/** Stores session summaries and (only with consent) selection events. */
public interface SessionRepository {

    /**
     * Saves a finished session.
     *
     * @param events (time ms, key label) pairs; empty unless the user consented to event logging
     */
    void save(SessionSummary summary, List<String[]> events) throws ProfileStoreException;

    List<SessionSummary> history(int profileId) throws ProfileStoreException;

    /** Deletes selection events older than the given number of days; returns how many. */
    int purgeEvents(int retentionDays) throws ProfileStoreException;

    /** Deletes the sessions and events of one profile. */
    void deleteSessions(int profileId) throws ProfileStoreException;
}
