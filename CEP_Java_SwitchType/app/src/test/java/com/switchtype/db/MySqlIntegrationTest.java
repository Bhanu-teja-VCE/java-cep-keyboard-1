package com.switchtype.db;

import com.switchtype.live.Recommender;
import com.switchtype.model.LayoutType;
import com.switchtype.model.PredictionMode;
import com.switchtype.model.ScanStyle;
import com.switchtype.model.Settings;
import com.switchtype.model.UserProfile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs against a real MySQL server in a separate database "switchtype_test", with the user
 * and password from db.properties. Skipped automatically when MySQL is not reachable.
 */
class MySqlIntegrationTest {

    private static DbConfig previous;

    @BeforeAll
    static void connect() {
        previous = Database.getConfig();
        DbConfig test = DbConfig.load();
        test.setDatabase("switchtype_test");
        Database.setConfig(test);
        boolean reachable;
        try (Connection con = Database.getConnection(); Statement st = con.createStatement()) {
            for (String table : new String[]{"selection_event", "typing_session", "user_profile",
                "experiment_result", "experiment_run", "recommended_settings"}) {
                st.execute("DROP TABLE IF EXISTS " + table);
            }
            SchemaInstaller.install(con);
            reachable = true;
        } catch (Exception e) {
            reachable = false;
        }
        assumeTrue(reachable, "MySQL not reachable - skipping database tests");
    }

    @AfterAll
    static void restore() {
        Database.setConfig(previous);
    }

    @Test
    void profileCrudAndSessionMetricsComputedInMySql() throws Exception {
        MySqlProfileRepository profiles = new MySqlProfileRepository();
        MySqlSessionRepository sessions = new MySqlSessionRepository();
        UserProfile p = profiles.save(new UserProfile(0, "JUnit user", new Settings()));     // INSERT
        assertTrue(p.getId() > 0);
        p.getSettings().setIntervalMs(700);
        profiles.save(p);                                                                       // UPDATE
        assertEquals(700, profiles.findAll().get(0).getSettings().getIntervalMs());            // SELECT

        SessionSummary s = new SessionSummary(p.getId(), "PRACTICE", System.currentTimeMillis());
        s.setCounts(30, 27, 66, 33, 3, 90000);
        sessions.save(s, Arrays.asList(new String[]{"500", "h"}, new String[]{"1500", "i"}));
        List<SessionSummary> history = sessions.history(p.getId());
        assertEquals(1, history.size());
        try (Connection con = Database.getConnection(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT cpm, error_rate, presses_per_char FROM typing_session")) {
            rs.next();
            assertEquals(18.0, rs.getDouble(1), 0.01, "27 chars in 1.5 min, computed by sp_finish_session");
            assertEquals(0.0909, rs.getDouble(2), 0.0001);
            assertEquals(2.2, rs.getDouble(3), 0.001);
        }
        assertEquals(0, sessions.purgeEvents(1), "events are younger than a day");
        assertEquals(2, sessions.purgeEvents(-1) , "a negative retention removes everything");

        profiles.deleteUserData(p.getId());                                                     // DELETE (procedure)
        assertTrue(profiles.findAll().isEmpty());
        assertTrue(sessions.history(p.getId()).isEmpty());
    }

    @Test
    void experimentBatchAndRecommendationProcedure() throws Exception {
        String[] row = {"typical", "ROW_COLUMN", "FREQUENCY", "OFF", "500", "1", "26.1", "0.006", "0.0", "2.01"};
        int run = ExperimentDao.saveRun("junit", "1", Collections.nCopies(1500, row));
        assertTrue(run > 0);
        List<String[]> rows = ExperimentDao.browse("experiment_result", 5000);
        assertEquals(1501, rows.size(), "header + 1500 rows inserted in one batch transaction");

        ExperimentDao.saveRecommendations(Arrays.asList(
                new Recommender.Recommendation("quick", "Quick", 350, 60, ScanStyle.ROW_COLUMN, LayoutType.FREQUENCY,
                        PredictionMode.BIGRAM, 300, 59, 0.004),
                new Recommender.Recommendation("slow", "Slow", 800, 180, ScanStyle.GROUP_BINARY, LayoutType.FREQUENCY,
                        PredictionMode.BIGRAM, 900, 16, 0.002)));
        Recommender.Recommendation r = Recommender.fromDatabase(760, 150);
        assertEquals("slow", r.profile);
        assertEquals(900, r.intervalMs);
    }
}
