package com.switchtype.sim;

import com.switchtype.exception.InvalidSettingsException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Parameters of one simulated user type. EVERY VALUE IS AN ASSUMPTION (see
 * profiles.properties); none is measured from real people.
 */
public class UserParams {

    /** Names of the parameters, in the order used by the sensitivity analysis. */
    public static final String[] PARAMETERS = {
        "reactionMean", "reactionSd", "jitter", "miss", "spurious", "compensation", "fatiguePct", "think", "detect"};

    private final String key;
    private final String label;
    private final double[] values = new double[PARAMETERS.length];

    public UserParams(String key, String label) {
        this.key = key;
        this.label = label;
    }

    /** Loads all profiles from the bundled file, or from an edited copy on disk if given. */
    public static List<UserParams> loadAll(File override) throws InvalidSettingsException {
        Properties props = new Properties();
        InputStream in = null;
        try {
            in = (override != null && override.exists())
                    ? new FileInputStream(override)
                    : UserParams.class.getResourceAsStream("/profiles.properties");
            if (in == null) {
                throw new InvalidSettingsException("profiles.properties is missing");
            }
            props.load(in);
        } catch (IOException e) {
            throw new InvalidSettingsException("Cannot read profiles: " + e.getMessage());
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // read-only stream
                }
            }
        }
        List<UserParams> result = new ArrayList<UserParams>();
        for (String name : props.getProperty("profiles", "").split(",")) {
            String k = name.trim();
            UserParams p = new UserParams(k, props.getProperty(k + ".label", k));
            for (int i = 0; i < PARAMETERS.length; i++) {
                String v = props.getProperty(k + "." + PARAMETERS[i]);
                if (v == null) {
                    throw new InvalidSettingsException("Profile " + k + " has no " + PARAMETERS[i]);
                }
                p.values[i] = Double.parseDouble(v.trim());
            }
            result.add(p);
        }
        return result;
    }

    /** A copy with one parameter multiplied by factor (sensitivity analysis). */
    public UserParams scaled(String parameter, double factor) {
        UserParams c = copy();
        int i = indexOf(parameter);
        c.values[i] = values[i] * factor;
        if ("compensation".equals(parameter) || "detect".equals(parameter)) {
            c.values[i] = Math.min(1.0, c.values[i]);  // these are probabilities/fractions
        }
        return c;
    }

    /** A copy with every parameter multiplied by factor. */
    public UserParams scaledAll(double factor) {
        UserParams c = this;
        for (String p : PARAMETERS) {
            c = c.scaled(p, factor);
        }
        return c;
    }

    /** The noise-free user of the validation test: no jitter, misses, slips, fatigue, think time. */
    public UserParams perfect() {
        UserParams c = copy();
        c.set("jitter", 0);
        c.set("miss", 0);
        c.set("spurious", 0);
        c.set("compensation", 1);
        c.set("fatiguePct", 0);
        c.set("think", 0);
        c.set("detect", 1);
        return c;
    }

    /** A copy without fatigue (experiments that study one effect at a time). */
    public UserParams withoutFatigue() {
        return copyWith("fatiguePct", 0);
    }

    /** A copy with one parameter set to a value. */
    public UserParams copyWith(String parameter, double value) {
        UserParams c = copy();
        c.set(parameter, value);
        return c;
    }

    private UserParams copy() {
        UserParams c = new UserParams(key, label);
        System.arraycopy(values, 0, c.values, 0, values.length);
        return c;
    }

    private static int indexOf(String parameter) {
        for (int i = 0; i < PARAMETERS.length; i++) {
            if (PARAMETERS[i].equals(parameter)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Unknown parameter " + parameter);
    }

    public double get(String parameter) {
        return values[indexOf(parameter)];
    }

    public void set(String parameter, double value) {
        values[indexOf(parameter)] = value;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
