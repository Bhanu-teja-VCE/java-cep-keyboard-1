package com.switchtype.live;

import com.switchtype.db.Database;
import com.switchtype.exception.ProfileStoreException;
import com.switchtype.model.LayoutType;
import com.switchtype.model.PredictionMode;
import com.switchtype.model.ScanStyle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a measured reaction time into recommended settings, using the results of
 * experiment 2 (best setting per simulated user type). With MySQL it calls
 * sp_recommend_settings; offline it uses the same table bundled as a CSV file.
 * The match is to a SIMULATED user type, so the result is a starting point, not a diagnosis.
 */
public final class Recommender {

    public static final String TABLE_RESOURCE = "/recommended_settings.csv";

    /** One row of the recommendation table. */
    public static final class Recommendation {
        public final String profile;
        public final String label;
        public final int reactionMean;
        public final int reactionSd;
        public final ScanStyle style;
        public final LayoutType layout;
        public final PredictionMode prediction;
        public final int intervalMs;
        public final double cpm;
        public final double totalError;

        public Recommendation(String profile, String label, int reactionMean, int reactionSd, ScanStyle style,
                              LayoutType layout, PredictionMode prediction, int intervalMs, double cpm,
                              double totalError) {
            this.profile = profile;
            this.label = label;
            this.reactionMean = reactionMean;
            this.reactionSd = reactionSd;
            this.style = style;
            this.layout = layout;
            this.prediction = prediction;
            this.intervalMs = intervalMs;
            this.cpm = cpm;
            this.totalError = totalError;
        }

        /** The CSV line format used by the bundled table and the Experiments tool. */
        public String[] toCsv() {
            return new String[]{profile, label, String.valueOf(reactionMean), String.valueOf(reactionSd),
                style.name(), layout.name(), prediction.name(), String.valueOf(intervalMs),
                com.switchtype.io.CsvWriter.num(cpm, 2), com.switchtype.io.CsvWriter.num(totalError, 4)};
        }
    }

    public static final String[] CSV_HEADER = {"profile", "label", "reaction_mean", "reaction_sd", "scan_style",
        "layout", "prediction", "interval_ms", "cpm", "total_err"};

    private Recommender() {
    }

    /** The bundled table (copied from experiments/recommended_settings.csv). */
    public static List<Recommendation> bundledTable() throws ProfileStoreException {
        List<Recommendation> list = new ArrayList<Recommendation>();
        InputStream in = Recommender.class.getResourceAsStream(TABLE_RESOURCE);
        if (in == null) {
            throw new ProfileStoreException("The recommendation table is missing; run the experiments first");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line = reader.readLine();                     // header
            while ((line = reader.readLine()) != null) {
                String[] f = line.split(",");
                if (f.length < 10) {
                    continue;
                }
                list.add(new Recommendation(f[0], f[1], Integer.parseInt(f[2]), Integer.parseInt(f[3]),
                        ScanStyle.valueOf(f[4]), LayoutType.valueOf(f[5]), PredictionMode.valueOf(f[6]),
                        Integer.parseInt(f[7]), Double.parseDouble(f[8]), Double.parseDouble(f[9])));
            }
        } catch (IOException | RuntimeException e) {
            throw new ProfileStoreException("Could not read the recommendation table: " + e.getMessage(), e);
        }
        return list;
    }

    /** Closest row by reaction mean (scale 100 ms) and spread (scale 50 ms); same rule as the procedure. */
    public static Recommendation closest(List<Recommendation> table, double mean, double sd) {
        Recommendation best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Recommendation r : table) {
            double dm = (r.reactionMean - mean) / 100.0;
            double ds = (r.reactionSd - sd) / 50.0;
            double d = dm * dm + ds * ds;
            if (d < bestDistance) {
                bestDistance = d;
                best = r;
            }
        }
        return best;
    }

    /** Asks MySQL (sp_recommend_settings). Returns null when the table is empty. */
    public static Recommendation fromDatabase(int mean, int sd) throws ProfileStoreException {
        try (Connection con = Database.getConnection();
             CallableStatement cs = con.prepareCall("{call sp_recommend_settings(?, ?)}")) {
            cs.setInt(1, mean);
            cs.setInt(2, sd);
            try (ResultSet rs = cs.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new Recommendation(rs.getString("profile"), rs.getString("label"), mean, sd,
                        ScanStyle.valueOf(rs.getString("scan_style")), LayoutType.valueOf(rs.getString("layout")),
                        PredictionMode.valueOf(rs.getString("prediction")), rs.getInt("interval_ms"),
                        rs.getDouble("cpm"), rs.getDouble("total_err"));
            }
        } catch (SQLException e) {
            throw new ProfileStoreException("sp_recommend_settings failed: " + e.getMessage(), e);
        }
    }
}
