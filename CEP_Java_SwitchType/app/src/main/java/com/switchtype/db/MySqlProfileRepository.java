package com.switchtype.db;

import com.switchtype.exception.ProfileStoreException;
import com.switchtype.model.LayoutType;
import com.switchtype.model.PredictionMode;
import com.switchtype.model.ScanStyle;
import com.switchtype.model.Settings;
import com.switchtype.model.SwitchChoice;
import com.switchtype.model.UserProfile;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Profiles in MySQL: CRUD with PreparedStatement, deletion with a stored procedure. */
public class MySqlProfileRepository implements ProfileRepository {

    private static final String COLUMNS = "name, scan_style, layout, prediction, interval_ms, first_extra_pct, "
            + "row_cycles, switch_input, adaptive, high_contrast, key_size, font_size, audio, rest_minutes, event_logging";

    @Override
    public List<UserProfile> findAll() throws ProfileStoreException {
        List<UserProfile> result = new ArrayList<UserProfile>();
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, " + COLUMNS + " FROM user_profile ORDER BY name")) {
            while (rs.next()) {
                Settings s = new Settings();
                s.setStyle(ScanStyle.valueOf(rs.getString("scan_style")));
                s.setLayout(LayoutType.valueOf(rs.getString("layout")));
                s.setPrediction(PredictionMode.valueOf(rs.getString("prediction")));
                s.setIntervalMs(rs.getInt("interval_ms"));
                s.setFirstExtraPct(rs.getInt("first_extra_pct"));
                s.setRowCycles(rs.getInt("row_cycles"));
                s.setSwitchChoice(SwitchChoice.valueOf(rs.getString("switch_input")));
                s.setAdaptive(rs.getBoolean("adaptive"));
                s.setHighContrast(rs.getBoolean("high_contrast"));
                s.setKeySize(rs.getInt("key_size"));
                s.setFontSize(rs.getInt("font_size"));
                s.setAudio(rs.getBoolean("audio"));
                s.setRestMinutes(rs.getInt("rest_minutes"));
                s.setEventLogging(rs.getBoolean("event_logging"));
                result.add(new UserProfile(rs.getInt("id"), rs.getString("name"), s));
            }
        } catch (SQLException e) {
            throw new ProfileStoreException("Could not read profiles: " + e.getMessage(), e);
        }
        return result;
    }

    @Override
    public UserProfile save(UserProfile p) throws ProfileStoreException {
        boolean insert = p.getId() == 0;
        String sql = insert
                ? "INSERT INTO user_profile (" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
                : "UPDATE user_profile SET name=?, scan_style=?, layout=?, prediction=?, interval_ms=?, "
                + "first_extra_pct=?, row_cycles=?, switch_input=?, adaptive=?, high_contrast=?, key_size=?, "
                + "font_size=?, audio=?, rest_minutes=?, event_logging=? WHERE id=?";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            Settings s = p.getSettings();
            ps.setString(1, p.getName());
            ps.setString(2, s.getStyle().name());
            ps.setString(3, s.getLayout().name());
            ps.setString(4, s.getPrediction().name());
            ps.setInt(5, s.getIntervalMs());
            ps.setInt(6, s.getFirstExtraPct());
            ps.setInt(7, s.getRowCycles());
            ps.setString(8, s.getSwitchChoice().name());
            ps.setBoolean(9, s.isAdaptive());
            ps.setBoolean(10, s.isHighContrast());
            ps.setInt(11, s.getKeySize());
            ps.setInt(12, s.getFontSize());
            ps.setBoolean(13, s.isAudio());
            ps.setInt(14, s.getRestMinutes());
            ps.setBoolean(15, s.isEventLogging());
            if (!insert) {
                ps.setInt(16, p.getId());
            }
            ps.executeUpdate();
            if (insert) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        p.setId(keys.getInt(1));
                    }
                }
            }
            return p;
        } catch (SQLException e) {
            throw new ProfileStoreException("Could not save profile " + p.getName() + ": " + e.getMessage(), e);
        }
    }

    /** Calls sp_delete_user_data, which deletes events, sessions and the profile in one transaction. */
    @Override
    public void deleteUserData(int profileId) throws ProfileStoreException {
        try (Connection con = Database.getConnection();
             CallableStatement cs = con.prepareCall("{call sp_delete_user_data(?)}")) {
            cs.setInt(1, profileId);
            cs.execute();
        } catch (SQLException e) {
            throw new ProfileStoreException("Could not delete the data of profile " + profileId, e);
        }
    }

    @Override
    public String describe() {
        return "MySQL " + Database.getConfig().describe();
    }
}
