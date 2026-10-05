package com.switchtype.db;

import com.switchtype.exception.ProfileStoreException;
import com.switchtype.io.CsvWriter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Offline session store: summaries in sessions.csv and (only with consent) events in
 * events.csv, both in the local data folder. Only counts and times, never typed text.
 */
public class LocalSessionRepository implements SessionRepository {

    static final String[] SESSION_HEADER = {"id", "profile_id", "mode", "started_at", "target_chars",
        "correct_chars", "presses", "selections", "corrections", "duration_ms"};
    static final String[] EVENT_HEADER = {"session_id", "logged_at", "t_ms", "key_label"};

    private final File sessionsFile;
    private final File eventsFile;

    public LocalSessionRepository(File dataDir) {
        this.sessionsFile = new File(dataDir, "sessions.csv");
        this.eventsFile = new File(dataDir, "events.csv");
    }

    @Override
    public synchronized void save(SessionSummary s, List<String[]> events) throws ProfileStoreException {
        try {
            List<String[]> all = readAll(sessionsFile);
            int id = all.size() + 1;
            s.setId(id);
            CsvWriter.append(sessionsFile, SESSION_HEADER, Collections.singletonList(new String[]{
                String.valueOf(id), String.valueOf(s.getProfileId()), s.getMode(), String.valueOf(s.getStartedAt()),
                String.valueOf(s.getTargetChars()), String.valueOf(s.getCorrectChars()), String.valueOf(s.getPresses()),
                String.valueOf(s.getSelections()), String.valueOf(s.getCorrections()), String.valueOf(s.getDurationMs())}));
            if (!events.isEmpty()) {
                List<String[]> rows = new ArrayList<String[]>();
                String now = String.valueOf(System.currentTimeMillis());
                for (String[] e : events) {
                    rows.add(new String[]{String.valueOf(id), now, e[0], e[1]});
                }
                CsvWriter.append(eventsFile, EVENT_HEADER, rows);
            }
        } catch (IOException e) {
            throw new ProfileStoreException("Could not save the session: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized List<SessionSummary> history(int profileId) throws ProfileStoreException {
        List<SessionSummary> result = new ArrayList<SessionSummary>();
        try {
            for (String[] r : readAll(sessionsFile)) {
                if (Integer.parseInt(r[1]) == profileId) {
                    SessionSummary s = new SessionSummary(profileId, r[2], Long.parseLong(r[3]));
                    s.setId(Integer.parseInt(r[0]));
                    s.setCounts(Integer.parseInt(r[4]), Integer.parseInt(r[5]), Integer.parseInt(r[6]),
                            Integer.parseInt(r[7]), Integer.parseInt(r[8]), Long.parseLong(r[9]));
                    result.add(s);
                }
            }
        } catch (IOException | RuntimeException e) {
            throw new ProfileStoreException("Could not read " + sessionsFile.getName() + ": " + e.getMessage(), e);
        }
        return result;
    }

    @Override
    public synchronized int purgeEvents(int retentionDays) throws ProfileStoreException {
        long cutoff = System.currentTimeMillis() - retentionDays * 24L * 3600 * 1000;
        try {
            List<String[]> keep = new ArrayList<String[]>();
            List<String[]> all = readAll(eventsFile);
            for (String[] r : all) {
                if (Long.parseLong(r[1]) >= cutoff) {
                    keep.add(r);
                }
            }
            rewrite(eventsFile, EVENT_HEADER, keep);
            return all.size() - keep.size();
        } catch (IOException e) {
            throw new ProfileStoreException("Could not purge events: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void deleteSessions(int profileId) throws ProfileStoreException {
        try {
            List<String> removedIds = new ArrayList<String>();
            List<String[]> keep = new ArrayList<String[]>();
            for (String[] r : readAll(sessionsFile)) {
                if (Integer.parseInt(r[1]) == profileId) {
                    removedIds.add(r[0]);
                } else {
                    keep.add(r);
                }
            }
            rewrite(sessionsFile, SESSION_HEADER, keep);
            List<String[]> keepEvents = new ArrayList<String[]>();
            for (String[] r : readAll(eventsFile)) {
                if (!removedIds.contains(r[0])) {
                    keepEvents.add(r);
                }
            }
            rewrite(eventsFile, EVENT_HEADER, keepEvents);
        } catch (IOException e) {
            throw new ProfileStoreException("Could not delete sessions: " + e.getMessage(), e);
        }
    }

    private static void rewrite(File file, String[] header, List<String[]> rows) throws IOException {
        if (file.exists() || !rows.isEmpty()) {
            CsvWriter.write(file, header, rows);
        }
    }

    /** Reads the rows after the header (Unit III: FileReader). Our files never contain quoted commas. */
    private static List<String[]> readAll(File file) throws IOException {
        List<String[]> rows = new ArrayList<String[]>();
        if (!file.exists()) {
            return rows;
        }
        try (BufferedReader in = new BufferedReader(new FileReader(file))) {
            String line = in.readLine();                 // header
            while ((line = in.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    rows.add(line.split(",", -1));
                }
            }
        }
        return rows;
    }
}
