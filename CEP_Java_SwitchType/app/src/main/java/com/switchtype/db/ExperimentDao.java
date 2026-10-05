package com.switchtype.db;

import com.switchtype.exception.ProfileStoreException;
import com.switchtype.live.Recommender;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Experiment results in MySQL (CRUD from the console tool) and a generic table viewer
 * for the GUI that works for any table through ResultSetMetaData.
 */
public final class ExperimentDao {

    /** Tables the viewer may show (a fixed list, so no SQL can be injected through the name). */
    public static final List<String> VIEWABLE_TABLES = Arrays.asList(
            "user_profile", "typing_session", "selection_event", "experiment_run", "experiment_result",
            "recommended_settings");

    private ExperimentDao() {
    }

    /**
     * Inserts one experiment run and all its result rows in ONE transaction with a batch
     * insert. Row format: profile, style, layout, prediction, interval, seed, cpm,
     * total_err, uncorrected_err, presses_per_char.
     *
     * @return the id of the new run
     */
    public static int saveRun(String name, String seeds, List<String[]> rows) throws ProfileStoreException {
        Connection con = null;
        try {
            con = Database.getConnection();
            con.setAutoCommit(false);
            int runId;
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO experiment_run (name, seeds) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name);
                ps.setString(2, seeds);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    runId = keys.getInt(1);
                }
            }
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO experiment_result (run_id, profile, "
                    + "scan_style, layout, prediction, interval_ms, seed, cpm, total_err, uncorrected_err, "
                    + "presses_per_char) VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
                int pending = 0;
                for (String[] r : rows) {
                    ps.setInt(1, runId);
                    for (int i = 0; i < 4; i++) {
                        ps.setString(2 + i, r[i]);
                    }
                    ps.setInt(6, Integer.parseInt(r[4]));
                    ps.setInt(7, Integer.parseInt(r[5]));
                    for (int i = 6; i < 10; i++) {
                        ps.setDouble(2 + i, Double.parseDouble(r[i]));
                    }
                    ps.addBatch();
                    if (++pending == 1000) {
                        ps.executeBatch();
                        pending = 0;
                    }
                }
                ps.executeBatch();
            }
            con.commit();
            return runId;
        } catch (SQLException | RuntimeException e) {
            if (con != null) {
                try {
                    con.rollback();
                } catch (SQLException ignored) {
                    // keep the first error
                }
            }
            throw new ProfileStoreException("Could not save experiment results: " + e.getMessage(), e);
        } finally {
            if (con != null) {
                try {
                    con.close();
                } catch (SQLException ignored) {
                    // nothing to do
                }
            }
        }
    }

    /** Replaces the recommended settings (derived from experiment 2). */
    public static void saveRecommendations(List<Recommender.Recommendation> list) throws ProfileStoreException {
        try (Connection con = Database.getConnection()) {
            con.setAutoCommit(false);
            try (Statement st = con.createStatement();
                 PreparedStatement ps = con.prepareStatement("INSERT INTO recommended_settings (profile, label, "
                         + "reaction_mean, reaction_sd, scan_style, layout, prediction, interval_ms, cpm, total_err) "
                         + "VALUES (?,?,?,?,?,?,?,?,?,?)")) {
                st.executeUpdate("DELETE FROM recommended_settings");
                for (Recommender.Recommendation r : list) {
                    ps.setString(1, r.profile);
                    ps.setString(2, r.label);
                    ps.setInt(3, r.reactionMean);
                    ps.setInt(4, r.reactionSd);
                    ps.setString(5, r.style.name());
                    ps.setString(6, r.layout.name());
                    ps.setString(7, r.prediction.name());
                    ps.setInt(8, r.intervalMs);
                    ps.setDouble(9, r.cpm);
                    ps.setDouble(10, r.totalError);
                    ps.addBatch();
                }
                ps.executeBatch();
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new ProfileStoreException("Could not save recommendations: " + e.getMessage(), e);
        }
    }

    /**
     * Reads up to limit rows of any viewable table. The first row of the result holds the
     * column names, found with ResultSetMetaData (Unit V).
     */
    public static List<String[]> browse(String table, int limit) throws ProfileStoreException {
        if (!VIEWABLE_TABLES.contains(table)) {
            throw new ProfileStoreException("Unknown table " + table);
        }
        List<String[]> rows = new ArrayList<String[]>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM " + table + " ORDER BY 1 DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                String[] names = new String[meta.getColumnCount()];
                for (int i = 0; i < names.length; i++) {
                    names[i] = meta.getColumnLabel(i + 1);
                }
                rows.add(names);
                while (rs.next()) {
                    String[] row = new String[names.length];
                    for (int i = 0; i < names.length; i++) {
                        row[i] = rs.getString(i + 1);
                    }
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            throw new ProfileStoreException("Could not read " + table + ": " + e.getMessage(), e);
        }
        return rows;
    }
}
