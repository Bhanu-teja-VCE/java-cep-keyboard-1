package com.switchtype.db;

import com.switchtype.exception.ProfileStoreException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/** Creates the tables, procedures and sample data from the script bundled in the jar. */
public final class SchemaInstaller {

    public static final String SCRIPT = "/sql/switchtype.sql";

    private SchemaInstaller() {
    }

    /** True if the "user_profile" table exists in the connected database. */
    public static boolean isInstalled(Connection con) throws SQLException {
        DatabaseMetaData meta = con.getMetaData();
        try (ResultSet rs = meta.getTables(con.getCatalog(), null, "user_profile", new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    /**
     * Runs the script. "CREATE DATABASE" and "USE" are skipped because the connection
     * already points at the configured database.
     *
     * @return number of statements executed
     */
    public static int install(Connection con) throws ProfileStoreException {
        List<String> statements;
        try (InputStream in = SchemaInstaller.class.getResourceAsStream(SCRIPT)) {
            if (in == null) {
                throw new ProfileStoreException("The SQL script " + SCRIPT + " is missing from the program");
            }
            statements = SqlScript.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ProfileStoreException("Could not read the SQL script", e);
        }
        int count = 0;
        try (Statement st = con.createStatement()) {
            for (String sql : statements) {
                String upper = sql.toUpperCase();
                if (upper.startsWith("CREATE DATABASE") || upper.startsWith("USE ")) {
                    continue;
                }
                st.execute(sql);
                count++;
            }
        } catch (SQLException e) {
            throw new ProfileStoreException("Setting up the database failed at statement " + (count + 1), e);
        }
        return count;
    }
}
