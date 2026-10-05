package com.switchtype.db;

import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlScriptTest {

    @Test
    void delimiterBlocksStayInOneStatement() throws Exception {
        String script = "CREATE TABLE a (x INT);\n-- comment\nDELIMITER //\nCREATE PROCEDURE p()\nBEGIN\n"
                + "  SELECT 1;\n  SELECT 2;\nEND //\nDELIMITER ;\nSELECT 3;\n";
        List<String> statements = SqlScript.parse(new StringReader(script));
        assertEquals(3, statements.size());
        assertTrue(statements.get(1).contains("SELECT 2;"), "the body keeps its own semicolons");
    }

    @Test
    void bundledScriptHasTheTablesAndProcedures() throws Exception {
        List<String> statements = SqlScript.parse(new InputStreamReader(
                SqlScriptTest.class.getResourceAsStream(SchemaInstaller.SCRIPT), StandardCharsets.UTF_8));
        int tables = 0;
        int procedures = 0;
        for (String s : statements) {
            if (s.startsWith("CREATE TABLE")) {
                tables++;
            }
            if (s.startsWith("CREATE PROCEDURE")) {
                procedures++;
            }
        }
        assertEquals(6, tables);
        assertEquals(5, procedures);
    }
}
