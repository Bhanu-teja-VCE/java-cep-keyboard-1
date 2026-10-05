package com.switchtype.db;

import com.switchtype.exception.ProfileStoreException;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/** Sessions in MySQL through stored procedures (Unit V: CallableStatement, batch, transaction). */
public class MySqlSessionRepository implements SessionRepository {

    @Override
    public void save(SessionSummary s, List<String[]> events) throws ProfileStoreException {
        Connection con = null;
        try {
            con = Database.getConnection();
            con.setAutoCommit(false);                       // session + events in one transaction
            int id;
            try (CallableStatement start = con.prepareCall("{call sp_start_session(?, ?, ?)}")) {
                start.setInt(1, s.getProfileId());
                start.setString(2, s.getMode());
                start.registerOutParameter(3, Types.INTEGER);
                start.execute();
                id = start.getInt(3);
            }
            try (CallableStatement finish = con.prepareCall("{call sp_finish_session(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}")) {
                finish.setInt(1, id);
                finish.setInt(2, s.getTargetChars());
                finish.setInt(3, s.getCorrectChars());
                finish.setInt(4, s.getPresses());
                finish.setInt(5, s.getSelections());
                finish.setInt(6, s.getCorrections());
                finish.setLong(7, s.getDurationMs());
                finish.registerOutParameter(8, Types.DECIMAL);
                finish.registerOutParameter(9, Types.DECIMAL);
                finish.registerOutParameter(10, Types.DECIMAL);
                finish.execute();
            }
            if (!events.isEmpty()) {
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO selection_event (session_id, t_ms, key_label) VALUES (?, ?, ?)")) {
                    for (String[] e : events) {
                        ps.setInt(1, id);
                        ps.setLong(2, Long.parseLong(e[0]));
                        ps.setString(3, e[1]);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
            con.commit();
            s.setId(id);
        } catch (SQLException e) {
            rollback(con);
            throw new ProfileStoreException("Could not save the session: " + e.getMessage(), e);
        } finally {
            close(con);
        }
    }

    @Override
    public List<SessionSummary> history(int profileId) throws ProfileStoreException {
        List<SessionSummary> result = new ArrayList<SessionSummary>();
        String sql = "SELECT id, mode, started_at, target_chars, correct_chars, presses, selections, corrections, "
                + "duration_ms FROM typing_session WHERE profile_id = ? AND finished_at IS NOT NULL ORDER BY id";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, profileId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SessionSummary s = new SessionSummary(profileId, rs.getString("mode"),
                            rs.getTimestamp("started_at").getTime());
                    s.setId(rs.getInt("id"));
                    s.setCounts(rs.getInt("target_chars"), rs.getInt("correct_chars"), rs.getInt("presses"),
                            rs.getInt("selections"), rs.getInt("corrections"), rs.getLong("duration_ms"));
                    result.add(s);
                }
            }
        } catch (SQLException e) {
            throw new ProfileStoreException("Could not read the history: " + e.getMessage(), e);
        }
        return result;
    }

    @Override
    public int purgeEvents(int retentionDays) throws ProfileStoreException {
        try (Connection con = Database.getConnection();
             CallableStatement cs = con.prepareCall("{call sp_purge_events(?, ?)}")) {
            cs.setInt(1, retentionDays);
            cs.registerOutParameter(2, Types.INTEGER);
            cs.execute();
            return cs.getInt(2);
        } catch (SQLException e) {
            throw new ProfileStoreException("Could not purge events: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteSessions(int profileId) throws ProfileStoreException {
        // sp_delete_user_data (called by the profile repository) removes sessions as well
        new MySqlProfileRepository().deleteUserData(profileId);
    }

    private static void rollback(Connection con) {
        if (con != null) {
            try {
                con.rollback();
            } catch (SQLException ignored) {
                // the original error is more useful than the rollback error
            }
        }
    }

    private static void close(Connection con) {
        if (con != null) {
            try {
                con.close();
            } catch (SQLException ignored) {
                // nothing useful to do
            }
        }
    }
}
