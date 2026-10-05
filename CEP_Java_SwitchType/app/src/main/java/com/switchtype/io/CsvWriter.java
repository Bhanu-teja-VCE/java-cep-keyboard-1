package com.switchtype.io;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/** Writes simple CSV files (Unit III: FileWriter). Values containing commas or quotes are quoted. */
public final class CsvWriter {

    private CsvWriter() {
    }

    public static void write(File file, String[] header, List<String[]> rows) throws IOException {
        File dir = file.getAbsoluteFile().getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create folder " + dir);
        }
        try (FileWriter out = new FileWriter(file)) {
            out.write(line(header));
            for (String[] row : rows) {
                out.write(line(row));
            }
        }
    }

    /** Appends rows, writing the header first if the file is new. */
    public static void append(File file, String[] header, List<String[]> rows) throws IOException {
        boolean exists = file.exists();
        File dir = file.getAbsoluteFile().getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create folder " + dir);
        }
        try (FileWriter out = new FileWriter(file, true)) {
            if (!exists) {
                out.write(line(header));
            }
            for (String[] row : rows) {
                out.write(line(row));
            }
        }
    }

    static String line(String[] values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(values[i]));
        }
        return sb.append('\n').toString();
    }

    static String escape(String v) {
        if (v == null) {
            return "";
        }
        if (v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }

    /** Formats a number with a fixed number of decimals and a dot (independent of locale). */
    public static String num(double value, int decimals) {
        return String.format(java.util.Locale.ROOT, "%." + decimals + "f", value);
    }
}
