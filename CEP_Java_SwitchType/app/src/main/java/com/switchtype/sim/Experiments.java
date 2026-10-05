package com.switchtype.sim;

import com.switchtype.db.Database;
import com.switchtype.db.ExperimentDao;
import com.switchtype.db.SchemaInstaller;
import com.switchtype.io.CsvWriter;
import com.switchtype.live.Recommender;
import com.switchtype.live.ScanListener;
import com.switchtype.live.ScannerThread;
import com.switchtype.live.TimingStats;
import com.switchtype.model.Key;
import com.switchtype.model.LayoutType;
import com.switchtype.model.Outcome;
import com.switchtype.model.PredictionMode;
import com.switchtype.model.ScanStyle;
import com.switchtype.model.Settings;
import com.switchtype.predict.WordPredictor;
import com.switchtype.scan.KeyboardLayout;
import com.switchtype.scan.ScanEngine;
import com.switchtype.scan.ScanFactory;
import com.switchtype.scan.ScanStrategy;

import java.io.File;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Console tool that runs experiments 1 to 9 and writes CSV files.
 * <pre>
 *   java -cp SwitchType.jar com.switchtype.sim.Experiments [outDir] [all|1..9 ...] [--mysql] [--seconds=60]
 * </pre>
 * Every simulated measurement uses the virtual-time SimulationDriver, i.e. the same
 * ScanEngine as the live app. All user parameters are ASSUMPTIONS (profiles.properties).
 */
public final class Experiments {

    static final int[] INTERVALS = range(200, 2000, 100);
    static final int[] COARSE_INTERVALS = range(200, 2000, 200);
    static final double MAX_ERROR = 0.05;
    /**
     * Order in which layouts are tried when picking the best setting. Group-binary scanning
     * builds its tree from frequencies, so all layouts give identical results; on a tie the
     * first one listed (frequency) is kept.
     */
    static final LayoutType[] LAYOUT_ORDER = {LayoutType.FREQUENCY, LayoutType.ALPHABETICAL, LayoutType.QWERTY};

    private final ExperimentRunner runner;
    private final List<UserParams> profiles;
    private final File out;
    private final boolean mysql;
    private final int realTimeSeconds;
    private final ExecutorService pool;

    /** Best setting per profile found in experiment 2 (used by 3 to 7). */
    private final Map<String, Best> best = new HashMap<String, Best>();
    private final Map<String, PredictionMode> bestMode = new HashMap<String, PredictionMode>();
    /** Mean results of experiment 1, keyed by profile|style|layout|interval. */
    private final Map<String, ExperimentRunner.Summary> sweep = new HashMap<String, ExperimentRunner.Summary>();

    /** A configuration and its mean result. */
    static final class Best {
        ScanStyle style;
        LayoutType layout;
        int interval;
        double cpm;
        double err;
    }

    private Experiments(File out, boolean mysql, int realTimeSeconds) throws Exception {
        this.runner = new ExperimentRunner();
        this.profiles = UserParams.loadAll(new File(out, "profiles.properties"));
        this.out = out;
        this.mysql = mysql;
        this.realTimeSeconds = realTimeSeconds;
        // java.util.concurrent is used here only to run independent, seeded simulations on all
        // CPU cores. Each task has its own driver and random generator, so results do not depend
        // on the number of threads; the shared model and phrases are only read.
        this.pool = Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors()));
    }

    public static void main(String[] args) throws Exception {
        File out = new File("experiments");
        List<String> which = new ArrayList<String>();
        boolean mysql = false;
        int seconds = 60;
        for (String a : args) {
            if (a.equals("--mysql")) {
                mysql = true;
            } else if (a.startsWith("--seconds=")) {
                seconds = Integer.parseInt(a.substring("--seconds=".length()));
            } else if (a.matches("all|[1-9]")) {
                which.add(a);
            } else {
                out = new File(a);
            }
        }
        if (which.isEmpty() || which.contains("all")) {
            which = Arrays.asList("8", "1", "2", "3", "4", "5", "6", "7", "9");
        }
        if (!out.exists() && !out.mkdirs()) {
            throw new IllegalStateException("Cannot create " + out);
        }
        if (mysql) {
            installSchema();
        }
        Experiments e = new Experiments(out, mysql, seconds);
        try {
            e.run(which);
        } finally {
            e.pool.shutdown();
        }
    }

    private void run(List<String> which) throws Exception {
        boolean needSweep = false;
        for (String w : which) {
            needSweep |= !w.equals("8") && !w.equals("9");
        }
        for (String w : which) {
            long start = System.currentTimeMillis();
            if (w.equals("1") || (needSweep && sweep.isEmpty() && !w.equals("8") && !w.equals("9"))) {
                exp1IntervalSweep();
                exp2BestWithConstraint();
            }
            if (w.equals("2") && best.isEmpty()) {
                exp2BestWithConstraint();
            }
            if (w.equals("3")) {
                exp3StylesAndLayouts();
            } else if (w.equals("4")) {
                exp4Prediction();
            } else if (w.equals("5")) {
                exp5Adaptive();
            } else if (w.equals("6")) {
                if (bestMode.isEmpty()) {
                    exp4Prediction();
                }
                exp6Tiredness();
            } else if (w.equals("7")) {
                exp7Sensitivity();
            } else if (w.equals("8")) {
                exp8Validation();
            } else if (w.equals("9")) {
                exp9RealTime();
            }
            System.out.printf("experiment %s done in %.1f s%n", w, (System.currentTimeMillis() - start) / 1000.0);
        }
    }

    // ------------------------------------------------------------------ experiment 1

    private void exp1IntervalSweep() throws Exception {
        List<Callable<String[]>> tasks = new ArrayList<Callable<String[]>>();
        final List<String[]> keys = new ArrayList<String[]>();
        for (final UserParams p0 : profiles) {
            final UserParams p = p0.withoutFatigue();
            for (final ScanStyle style : ScanStyle.values()) {
                for (final LayoutType layout : LayoutType.values()) {
                    for (final int interval : INTERVALS) {
                        for (final long seed : ExperimentRunner.SEEDS) {
                            keys.add(new String[]{p.getKey(), style.name(), layout.name(), String.valueOf(interval)});
                            tasks.add(new Callable<String[]>() {
                                @Override
                                public String[] call() {
                                    RunResult r = runner.run(p, new Settings(style, layout, PredictionMode.OFF, interval), seed);
                                    return row(p.getKey(), style, layout, PredictionMode.OFF, interval, seed, r);
                                }
                            });
                        }
                    }
                }
            }
        }
        List<String[]> rows = runAll(tasks);
        CsvWriter.write(new File(out, "exp1_sweep_raw.csv"), RAW_HEADER, rows);

        // group the raw rows into per-configuration summaries (same order as the tasks)
        List<String[]> summaryRows = new ArrayList<String[]>();
        for (int i = 0; i < rows.size(); i += ExperimentRunner.SEEDS.length) {
            String[] k = keys.get(i);
            ExperimentRunner.Summary s = new ExperimentRunner.Summary();
            for (int j = 0; j < ExperimentRunner.SEEDS.length; j++) {
                s.add(fromRow(rows.get(i + j)));
            }
            sweep.put(k[0] + "|" + k[1] + "|" + k[2] + "|" + k[3], s);
            summaryRows.add(summaryRow(k[0], k[1], k[2], "OFF", k[3], s));
        }
        CsvWriter.write(new File(out, "exp1_sweep.csv"), SUMMARY_HEADER, summaryRows);
        if (mysql) {
            int id = ExperimentDao.saveRun("exp1 interval sweep", "1-5", mysqlRows(rows));
            System.out.println("  saved " + rows.size() + " rows to MySQL as experiment_run " + id);
        }
    }

    // ------------------------------------------------------------------ experiment 2

    private void exp2BestWithConstraint() throws Exception {
        List<String[]> bestRows = new ArrayList<String[]>();
        List<String[]> pareto = new ArrayList<String[]>();
        for (UserParams p : profiles) {
            Best b = null;
            List<double[]> points = new ArrayList<double[]>();
            List<String> labels = new ArrayList<String>();
            for (ScanStyle style : ScanStyle.values()) {
                for (LayoutType layout : LAYOUT_ORDER) {
                    for (int interval : INTERVALS) {
                        ExperimentRunner.Summary s = sweep.get(p.getKey() + "|" + style.name() + "|" + layout.name() + "|" + interval);
                        points.add(new double[]{s.cpm(), s.totalErrorRate()});
                        labels.add(style.name() + "," + layout.name() + "," + interval);
                        if (s.totalErrorRate() <= MAX_ERROR && (b == null || s.cpm() > b.cpm)) {
                            b = new Best();
                            b.style = style;
                            b.layout = layout;
                            b.interval = interval;
                            b.cpm = s.cpm();
                            b.err = s.totalErrorRate();
                        }
                    }
                }
            }
            if (b == null) {
                throw new IllegalStateException("No setting meets the error limit for " + p);
            }
            best.put(p.getKey(), b);
            bestRows.add(new String[]{p.getKey(), b.style.name(), b.layout.name(), String.valueOf(b.interval),
                CsvWriter.num(b.cpm, 2), CsvWriter.num(b.cpm / 5, 2), CsvWriter.num(b.err, 4)});
            for (int i = 0; i < points.size(); i++) {
                boolean dominated = false;
                for (double[] q : points) {
                    if (q[0] >= points.get(i)[0] && q[1] <= points.get(i)[1]
                            && (q[0] > points.get(i)[0] || q[1] < points.get(i)[1])) {
                        dominated = true;
                        break;
                    }
                }
                String[] l = labels.get(i).split(",");
                pareto.add(new String[]{p.getKey(), l[0], l[1], l[2], CsvWriter.num(points.get(i)[0], 2),
                    CsvWriter.num(points.get(i)[1], 4), dominated ? "0" : "1"});
            }
        }
        CsvWriter.write(new File(out, "exp2_best.csv"),
                new String[]{"profile", "style", "layout", "interval_ms", "cpm", "wpm", "total_err"}, bestRows);
        CsvWriter.write(new File(out, "exp2_pareto.csv"),
                new String[]{"profile", "style", "layout", "interval_ms", "cpm", "total_err", "pareto"}, pareto);
    }

    // ------------------------------------------------------------------ experiment 3

    private void exp3StylesAndLayouts() throws Exception {
        List<String[]> rows = new ArrayList<String[]>();
        for (UserParams p : profiles) {
            Best b = best.get(p.getKey());
            for (ScanStyle style : ScanStyle.values()) {
                for (LayoutType layout : LayoutType.values()) {
                    ExperimentRunner.Summary s = sweep.get(p.getKey() + "|" + style.name() + "|" + layout.name() + "|" + b.interval);
                    // also the best interval of this combination, for a fairer comparison
                    int ownBest = -1;
                    double ownCpm = -1;
                    for (int interval : INTERVALS) {
                        ExperimentRunner.Summary t = sweep.get(p.getKey() + "|" + style.name() + "|" + layout.name() + "|" + interval);
                        if (t.totalErrorRate() <= MAX_ERROR && t.cpm() > ownCpm) {
                            ownCpm = t.cpm();
                            ownBest = interval;
                        }
                    }
                    rows.add(new String[]{p.getKey(), style.name(), layout.name(), String.valueOf(b.interval),
                        CsvWriter.num(s.cpm(), 2), CsvWriter.num(s.cpmSd(), 2), CsvWriter.num(s.totalErrorRate(), 4),
                        CsvWriter.num(s.pressesPerChar(), 3), CsvWriter.num(s.stepsPerChar(), 2),
                        String.valueOf(ownBest), CsvWriter.num(Math.max(0, ownCpm), 2)});
                }
            }
        }
        CsvWriter.write(new File(out, "exp3_styles_layouts.csv"), new String[]{"profile", "style", "layout",
            "interval_ms", "cpm", "cpm_sd", "total_err", "presses_per_char", "steps_per_char",
            "own_best_interval", "own_best_cpm"}, rows);
    }

    // ------------------------------------------------------------------ experiment 4

    private void exp4Prediction() throws Exception {
        List<Callable<String[]>> tasks = new ArrayList<Callable<String[]>>();
        for (final UserParams p0 : profiles) {
            final UserParams p = p0.withoutFatigue();
            final Best b = best.get(p.getKey());
            for (final ScanStyle style : ScanStyle.values()) {
                for (final PredictionMode mode : PredictionMode.values()) {
                    for (final long seed : ExperimentRunner.SEEDS) {
                        tasks.add(new Callable<String[]>() {
                            @Override
                            public String[] call() {
                                RunResult r = runner.run(p, new Settings(style, b.layout, mode, b.interval), seed);
                                return row(p.getKey(), style, b.layout, mode, b.interval, seed, r);
                            }
                        });
                    }
                }
            }
        }
        List<String[]> raw = runAll(tasks);
        CsvWriter.write(new File(out, "exp4_prediction_raw.csv"), RAW_HEADER, raw);
        List<String[]> rows = new ArrayList<String[]>();
        for (int i = 0; i < raw.size(); i += ExperimentRunner.SEEDS.length) {
            ExperimentRunner.Summary s = new ExperimentRunner.Summary();
            for (int j = 0; j < ExperimentRunner.SEEDS.length; j++) {
                s.add(fromRow(raw.get(i + j)));
            }
            String[] r = raw.get(i);
            rows.add(summaryRow(r[0], r[1], r[2], r[3], r[4], s));
        }
        CsvWriter.write(new File(out, "exp4_prediction.csv"), SUMMARY_HEADER, rows);

        // best mode per profile at its best style (feeds the recommendation table)
        List<Recommender.Recommendation> recs = new ArrayList<Recommender.Recommendation>();
        for (UserParams p : profiles) {
            Best b = best.get(p.getKey());
            PredictionMode chosen = PredictionMode.OFF;
            double chosenCpm = -1;
            double chosenErr = 0;
            for (String[] r : rows) {
                if (r[0].equals(p.getKey()) && r[1].equals(b.style.name())) {
                    double cpm = Double.parseDouble(r[5]);
                    double err = Double.parseDouble(r[7]);
                    if (err <= MAX_ERROR && cpm > chosenCpm) {
                        chosenCpm = cpm;
                        chosenErr = err;
                        chosen = PredictionMode.valueOf(r[3]);
                    }
                }
            }
            bestMode.put(p.getKey(), chosen);
            recs.add(new Recommender.Recommendation(p.getKey(), p.getLabel(), (int) p.get("reactionMean"),
                    (int) p.get("reactionSd"), b.style, b.layout, chosen, b.interval, chosenCpm, chosenErr));
        }
        List<String[]> recRows = new ArrayList<String[]>();
        for (Recommender.Recommendation r : recs) {
            recRows.add(r.toCsv());
        }
        CsvWriter.write(new File(out, "recommended_settings.csv"), Recommender.CSV_HEADER, recRows);
        if (mysql) {
            ExperimentDao.saveRecommendations(recs);
            ExperimentDao.saveRun("exp4 prediction", "1-5", mysqlRows(raw));
        }

        // keystroke savings with a perfect user, and hit rates on the held-out phrases
        List<String[]> savings = new ArrayList<String[]>();
        UserParams perfect = profiles.get(0).perfect();
        for (PredictionMode mode : PredictionMode.values()) {
            RunResult r = runner.run(perfect, new Settings(ScanStyle.ROW_COLUMN, LayoutType.FREQUENCY, mode, 1000), 1);
            int[] hits = hitRates(mode);
            savings.add(new String[]{mode.name(), String.valueOf(r.getTargetChars()), String.valueOf(r.getSelections()),
                CsvWriter.num(1.0 - (double) r.getSelections() / r.getTargetChars(), 4),
                CsvWriter.num(r.pressesPerChar(), 3), String.valueOf(hits[0]), String.valueOf(hits[1]),
                String.valueOf(hits[2]), String.valueOf(hits[3]), String.valueOf(hits[4]), String.valueOf(hits[5]),
                String.valueOf(hits[6])});
        }
        CsvWriter.write(new File(out, "exp4_keystroke_savings.csv"), new String[]{"mode", "target_chars",
            "selections", "keystroke_savings", "presses_per_char", "words", "top1_start", "top3_start",
            "top5_start", "top1_after1", "top3_after1", "top5_after1"}, savings);
    }

    /**
     * Hit rates of the predictor on the TEST phrases (never used for training): how often the
     * word is in the top 1/3/5 at the start of the word and after its first letter.
     * Unit III: StringTokenizer splits the phrases.
     */
    private int[] hitRates(PredictionMode mode) {
        WordPredictor predictor = runner.predictor(mode);
        int[] h = new int[7];
        for (String phrase : runner.getPhrases().test()) {
            String previous = WordPredictor.START;
            StringTokenizer tokens = new StringTokenizer(phrase, " ,.?");
            while (tokens.hasMoreTokens()) {
                String word = tokens.nextToken();
                h[0]++;
                List<String> start = predictor.predict(previous, "", 5);
                List<String> after1 = predictor.predict(previous, word.substring(0, 1), 5);
                int[] ks = {1, 3, 5};
                for (int i = 0; i < 3; i++) {
                    if (start.subList(0, Math.min(ks[i], start.size())).contains(word)) {
                        h[1 + i]++;
                    }
                    if (after1.subList(0, Math.min(ks[i], after1.size())).contains(word)) {
                        h[4 + i]++;
                    }
                }
                previous = word;
            }
        }
        return h;
    }

    // ------------------------------------------------------------------ experiment 5

    private void exp5Adaptive() throws Exception {
        final List<String> twice = new ArrayList<String>(runner.getPhrases().test());
        twice.addAll(runner.getPhrases().test());
        List<Callable<String[]>> tasks = new ArrayList<Callable<String[]>>();
        for (final UserParams p0 : profiles) {
            final UserParams p = p0.withoutFatigue();
            final Best b = best.get(p.getKey());
            final int fast = Math.max(200, b.interval / 2);
            final int slow = Math.min(2000, b.interval * 2);
            final Object[][] conditions = {
                {"fixed_best", b.interval, false}, {"fixed_too_fast", fast, false}, {"fixed_too_slow", slow, false},
                {"adaptive_from_1000", 1000, true}, {"adaptive_from_fast", fast, true}, {"adaptive_from_slow", slow, true}};
            for (final Object[] c : conditions) {
                for (final long seed : ExperimentRunner.SEEDS) {
                    tasks.add(new Callable<String[]>() {
                        @Override
                        public String[] call() {
                            Settings s = new Settings(b.style, b.layout, PredictionMode.OFF, (Integer) c[1]);
                            s.setAdaptive((Boolean) c[2]);
                            SimulationDriver d = new SimulationDriver(s, runner.getFrequency(),
                                    runner.predictor(PredictionMode.OFF), new SimulatedUser(p, seed));
                            RunResult r = d.typePhrases(twice);
                            return new String[]{p.getKey(), (String) c[0], String.valueOf(c[1]), String.valueOf(seed),
                                String.valueOf(b.interval), CsvWriter.num(r.cpm(), 2), CsvWriter.num(r.totalErrorRate(), 4),
                                String.valueOf(medianSecondHalf(r)), CsvWriter.num(firstWithinMinutes(r, b.interval), 2),
                                CsvWriter.num(shareWithin(r, b.interval), 3), CsvWriter.num(r.getTimeMs() / 60000.0, 1),
                                traceString(r)};
                        }
                    });
                }
            }
        }
        List<String[]> rows = runAll(tasks);
        List<String[]> table = new ArrayList<String[]>();
        List<String[]> traces = new ArrayList<String[]>();
        for (String[] r : rows) {
            table.add(Arrays.copyOf(r, 11));
            if (r[3].equals("1") && r[1].startsWith("adaptive")) {
                for (String point : r[11].split(";")) {
                    String[] tv = point.split(":");
                    traces.add(new String[]{r[0], r[1], tv[0], tv[1]});
                }
            }
        }
        CsvWriter.write(new File(out, "exp5_adaptive.csv"), new String[]{"profile", "condition", "start_ms", "seed",
            "best_fixed_ms", "cpm", "total_err", "median_interval_2nd_half", "first_within_20pct_min",
            "share_time_within_20pct", "session_min"}, table);
        CsvWriter.write(new File(out, "exp5_trace.csv"),
                new String[]{"profile", "condition", "minute", "interval_ms"}, traces);
    }

    /** Median interval during the second half of the session (time-weighted by change points). */
    private static int medianSecondHalf(RunResult r) {
        long half = r.getTimeMs() / 2;
        List<long[]> trace = r.getIntervalTrace();
        List<Integer> samples = new ArrayList<Integer>();
        // sample the interval every 10 s of virtual time in the second half
        for (long t = half; t <= r.getTimeMs(); t += 10000) {
            samples.add(intervalAt(trace, t));
        }
        java.util.Collections.sort(samples);
        return samples.isEmpty() ? intervalAt(trace, 0) : samples.get(samples.size() / 2);
    }

    private static int intervalAt(List<long[]> trace, long t) {
        int value = (int) trace.get(0)[1];
        for (long[] point : trace) {
            if (point[0] <= t) {
                value = (int) point[1];
            }
        }
        return value;
    }

    /** First minute at which the interval is within 20 % of the best fixed one; -1 if never. */
    private static double firstWithinMinutes(RunResult r, int target) {
        for (long[] point : r.getIntervalTrace()) {
            if (Math.abs(point[1] - target) <= 0.2 * target) {
                return point[0] / 60000.0;
            }
        }
        return -1;
    }

    /** Share of the session time during which the interval was within 20 % of the best fixed one. */
    private static double shareWithin(RunResult r, int target) {
        List<long[]> trace = r.getIntervalTrace();
        long inside = 0;
        for (int i = 0; i < trace.size(); i++) {
            long end = (i + 1 < trace.size()) ? trace.get(i + 1)[0] : r.getTimeMs();
            if (Math.abs(trace.get(i)[1] - target) <= 0.2 * target) {
                inside += end - trace.get(i)[0];
            }
        }
        return r.getTimeMs() == 0 ? 0 : (double) inside / r.getTimeMs();
    }

    private static String traceString(RunResult r) {
        StringBuilder sb = new StringBuilder();
        for (long[] point : r.getIntervalTrace()) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(CsvWriter.num(point[0] / 60000.0, 3)).append(':').append(point[1]);
        }
        sb.append(';').append(CsvWriter.num(r.getTimeMs() / 60000.0, 3)).append(':')
                .append(r.getIntervalTrace().get(r.getIntervalTrace().size() - 1)[1]);
        return sb.toString();
    }

    // ------------------------------------------------------------------ experiment 6

    private void exp6Tiredness() throws Exception {
        final long session = 30 * 60000L;
        List<Callable<String[]>> tasks = new ArrayList<Callable<String[]>>();
        for (final UserParams p : profiles) {                // fatigue ON here
            final Best b = best.get(p.getKey());
            final PredictionMode mode = bestMode.get(p.getKey());
            final Object[][] conditions = {{"no_rest", false, 0L}, {"rest_2min_every_10", false, 10 * 60000L},
                {"adaptive_no_rest", true, 0L}};
            for (final Object[] c : conditions) {
                for (final long seed : ExperimentRunner.SEEDS) {
                    tasks.add(new Callable<String[]>() {
                        @Override
                        public String[] call() {
                            Settings s = new Settings(b.style, b.layout, mode, b.interval);
                            s.setAdaptive((Boolean) c[1]);
                            SimulationDriver d = new SimulationDriver(s, runner.getFrequency(), runner.predictor(mode),
                                    new SimulatedUser(p, seed));
                            RunResult r = d.typeForDuration(runner.getPhrases().test(), session, (Long) c[2], 2 * 60000L);
                            double[] bins = new double[10];             // 3-minute bins
                            double total = 0;
                            for (long[] ph : r.getPhraseLog()) {
                                if (ph[0] <= session) {
                                    bins[(int) Math.min(9, (ph[0] - 1) / (3 * 60000L))] += ph[1];
                                    total += ph[1];
                                }
                            }
                            StringBuilder timeline = new StringBuilder();
                            for (double bin : bins) {
                                timeline.append(timeline.length() == 0 ? "" : ";").append(CsvWriter.num(bin / 3.0, 2));
                            }
                            double first = quarter(r, 0, session / 4);
                            double last = quarter(r, 3 * session / 4, session);
                            return new String[]{p.getKey(), (String) c[0], String.valueOf(seed), CsvWriter.num(total, 0),
                                CsvWriter.num(total / 30.0, 2), CsvWriter.num(first, 2), CsvWriter.num(last, 2),
                                CsvWriter.num(first > 0 ? last / first : 0, 3), timeline.toString()};
                        }
                    });
                }
            }
        }
        List<String[]> rows = runAll(tasks);
        List<String[]> summary = new ArrayList<String[]>();
        List<String[]> timeline = new ArrayList<String[]>();
        for (String[] r : rows) {
            summary.add(Arrays.copyOf(r, 8));
            String[] bins = r[8].split(";");
            for (int i = 0; i < bins.length; i++) {
                timeline.add(new String[]{r[0], r[1], r[2], String.valueOf(i * 3 + 1.5), bins[i]});
            }
        }
        CsvWriter.write(new File(out, "exp6_fatigue.csv"), new String[]{"profile", "condition", "seed",
            "correct_chars_30min", "cpm_30min", "cpm_first_quarter", "cpm_last_quarter", "last_over_first"}, summary);
        CsvWriter.write(new File(out, "exp6_timeline.csv"),
                new String[]{"profile", "condition", "seed", "minute", "cpm"}, timeline);
    }

    /** CPM of the phrases that ended within [from, to). */
    private static double quarter(RunResult r, long from, long to) {
        double chars = 0;
        for (long[] ph : r.getPhraseLog()) {
            if (ph[0] >= from && ph[0] < to) {
                chars += ph[1];
            }
        }
        return chars / ((to - from) / 60000.0);
    }

    // ------------------------------------------------------------------ experiment 7

    private void exp7Sensitivity() throws Exception {
        List<String[]> rows = new ArrayList<String[]>();
        for (UserParams p0 : profiles) {
            UserParams base = p0.withoutFatigue();
            List<Object[]> variants = new ArrayList<Object[]>();
            variants.add(new Object[]{"base", base});
            for (String param : UserParams.PARAMETERS) {
                if (!param.equals("fatiguePct")) {
                    variants.add(new Object[]{param + "x0.8", base.scaled(param, 0.8)});
                    variants.add(new Object[]{param + "x1.2", base.scaled(param, 1.2)});
                }
            }
            variants.add(new Object[]{"all_x0.8", base.scaledAll(0.8)});
            variants.add(new Object[]{"all_x1.2", base.scaledAll(1.2)});
            for (Object[] v : variants) {
                rows.add(sensitivityRow(p0.getKey(), (String) v[0], (UserParams) v[1]));
            }
            System.out.println("  sensitivity done for " + p0.getLabel());
        }
        CsvWriter.write(new File(out, "exp7_sensitivity.csv"), new String[]{"profile", "variant", "best_style",
            "best_interval", "best_cpm", "best_err", "freq_cpm", "alpha_cpm", "qwerty_cpm", "freq_layout_best",
            "bigram_gain_pct", "adaptive_gap_pct"}, rows);
    }

    /**
     * Re-derives the main conclusions for one variant of the user model: best style and
     * interval (coarse grid, frequency layout), whether the frequency layout still wins,
     * the bigram gain and the gap between adaptive and the best fixed interval.
     */
    private String[] sensitivityRow(final String profile, String variant, final UserParams p) throws Exception {
        List<Callable<double[]>> tasks = new ArrayList<Callable<double[]>>();
        for (final ScanStyle style : ScanStyle.values()) {
            for (final int interval : COARSE_INTERVALS) {
                tasks.add(new Callable<double[]>() {
                    @Override
                    public double[] call() {
                        ExperimentRunner.Summary s = runner.runSeeds(p,
                                new Settings(style, LayoutType.FREQUENCY, PredictionMode.OFF, interval));
                        return new double[]{style.ordinal(), interval, s.cpm(), s.totalErrorRate()};
                    }
                });
            }
        }
        double[] top = null;
        for (double[] r : runAllPlain(tasks)) {
            if (r[3] <= MAX_ERROR && (top == null || r[2] > top[2])) {
                top = r;
            }
        }
        if (top == null) {
            return new String[]{profile, variant, "none", "", "", "", "", "", "", "", "", ""};
        }
        final ScanStyle style = ScanStyle.values()[(int) top[0]];
        final int interval = (int) top[1];
        double alpha = runner.runSeeds(p, new Settings(style, LayoutType.ALPHABETICAL, PredictionMode.OFF, interval)).cpm();
        double qwerty = runner.runSeeds(p, new Settings(style, LayoutType.QWERTY, PredictionMode.OFF, interval)).cpm();
        double bigram = runner.runSeeds(p, new Settings(style, LayoutType.FREQUENCY, PredictionMode.BIGRAM, interval)).cpm();
        Settings adaptive = new Settings(style, LayoutType.FREQUENCY, PredictionMode.OFF, 1000);
        adaptive.setAdaptive(true);
        double adaptiveCpm = runner.runSeeds(p, adaptive).cpm();
        return new String[]{profile, variant, style.name(), String.valueOf(interval), CsvWriter.num(top[2], 2),
            CsvWriter.num(top[3], 4), CsvWriter.num(top[2], 2), CsvWriter.num(alpha, 2), CsvWriter.num(qwerty, 2),
            (top[2] >= alpha && top[2] >= qwerty) ? "1" : "0",
            CsvWriter.num(100 * (bigram - top[2]) / top[2], 1), CsvWriter.num(100 * (top[2] - adaptiveCpm) / top[2], 1)};
    }

    // ------------------------------------------------------------------ experiment 8

    private void exp8Validation() throws Exception {
        List<String[]> rows = new ArrayList<String[]>();
        UserParams typical = profiles.get(1).withoutFatigue();
        UserParams perfect = typical.perfect();
        for (ScanStyle style : ScanStyle.values()) {
            for (LayoutType layout : LayoutType.values()) {
                Settings s = new Settings(style, layout, PredictionMode.OFF, 600);
                ScanStrategy strategy = ScanFactory.strategy(style, ScanFactory.grid(s, runner.getFrequency()),
                        s.getRowCycles(), runner.getFrequency());
                long steps = 0;
                long presses = 0;
                for (String phrase : runner.getPhrases().test()) {
                    for (char c : phrase.toCharArray()) {
                        Key k = KeyboardLayout.findChar(strategy.getGrid(), c);
                        steps += strategy.analyticSteps(k);
                        presses += strategy.analyticPresses(k);
                    }
                }
                RunResult r = runner.run(perfect, s, 1);
                rows.add(new String[]{"zero_noise", style.name(), layout.name(), String.valueOf(steps),
                    String.valueOf(r.getSteps()), CsvWriter.num(100.0 * (r.getSteps() - steps) / steps, 3),
                    String.valueOf(presses), String.valueOf(r.getPresses())});
            }
        }
        UserParams calm = typical.copyWith("miss", 0).copyWith("spurious", 0);
        for (int interval : new int[]{200, 300, 2000}) {
            for (UserParams u : new UserParams[]{calm, typical.copyWith("compensation", 0)}) {
                ExperimentRunner.Summary s = runner.runSeeds(u,
                        new Settings(ScanStyle.ROW_COLUMN, LayoutType.FREQUENCY, PredictionMode.OFF, interval));
                rows.add(new String[]{"sanity_comp" + CsvWriter.num(u.get("compensation"), 1), "ROW_COLUMN", "FREQUENCY",
                    String.valueOf(interval), CsvWriter.num(s.totalErrorRate(), 4), CsvWriter.num(s.cpm(), 2), "", ""});
            }
        }
        Settings s = new Settings(ScanStyle.ROW_COLUMN, LayoutType.FREQUENCY, PredictionMode.BIGRAM, 700);
        RunResult a = runner.run(typical, s, 42);
        RunResult b = runner.run(typical, s, 42);
        RunResult c = runner.run(typical, s, 43);
        rows.add(new String[]{"seed_42_twice", "", "", CsvWriter.num(a.cpm(), 4), CsvWriter.num(b.cpm(), 4),
            a.getTimeMs() == b.getTimeMs() ? "identical" : "DIFFERENT", "", ""});
        rows.add(new String[]{"seed_42_vs_43", "", "", CsvWriter.num(a.cpm(), 4), CsvWriter.num(c.cpm(), 4),
            CsvWriter.num(100 * (c.cpm() - a.cpm()) / a.cpm(), 2), "", ""});
        CsvWriter.write(new File(out, "exp8_validation.csv"),
                new String[]{"check", "style", "layout", "a", "b", "c", "d", "e"}, rows);
    }

    // ------------------------------------------------------------------ experiment 9

    /**
     * Runs the real ScannerThread (no Swing) and measures how late each step is compared
     * with its ideal time, idle and with busy threads, at three priorities.
     */
    private void exp9RealTime() throws Exception {
        List<String[]> rows = new ArrayList<String[]>();
        int cores = Runtime.getRuntime().availableProcessors();
        for (int load : new int[]{0, 2 * cores}) {
            for (int priority : new int[]{Thread.MIN_PRIORITY, Thread.NORM_PRIORITY, ScannerThread.PRIORITY, Thread.MAX_PRIORITY}) {
                for (int interval : new int[]{200, 500, 1000}) {
                    TimingStats t = measureScanner(interval, priority, load, realTimeSeconds);
                    rows.add(new String[]{String.valueOf(interval), String.valueOf(load), String.valueOf(priority),
                        String.valueOf(t.count()), CsvWriter.num(t.meanMs(), 3), CsvWriter.num(t.percentileMs(99), 3),
                        CsvWriter.num(t.maxMs(), 3)});
                    System.out.printf("  interval %d load %d priority %d: mean %.3f p99 %.3f max %.3f ms%n",
                            interval, load, priority, t.meanMs(), t.percentileMs(99), t.maxMs());
                }
            }
        }
        CsvWriter.write(new File(out, "exp9_timing.csv"), new String[]{"interval_ms", "busy_threads", "priority",
            "steps", "mean_late_ms", "p99_late_ms", "max_late_ms"}, rows);
    }

    /** One timing measurement. Busy threads compute until the stop flag (guarded by lock) is set. */
    static TimingStats measureScanner(int interval, int priority, int busyThreads, int seconds) throws Exception {
        final boolean[] stop = new boolean[1];
        final Object lock = new Object();
        List<Thread> busy = new ArrayList<Thread>();
        for (int i = 0; i < busyThreads; i++) {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    double x = 0;
                    while (true) {
                        for (int k = 0; k < 100000; k++) {
                            x += Math.sqrt(k);
                        }
                        synchronized (lock) {
                            if (stop[0]) {
                                break;
                            }
                        }
                    }
                    if (x < 0) {
                        System.out.println(x);             // keeps the loop from being optimised away
                    }
                }
            }, "busy-" + i);
            t.setDaemon(true);
            busy.add(t);
            t.start();
        }
        Settings s = new Settings(ScanStyle.ROW_COLUMN, LayoutType.ALPHABETICAL, PredictionMode.OFF, interval);
        ScanEngine engine = ScanFactory.engine(s, null);
        ScannerThread scanner = new ScannerThread(engine, new ScanListener() {
            @Override
            public void highlightChanged(List<Key> keys) {
            }

            @Override
            public void pressed(Outcome outcome) {
            }

            @Override
            public void gaveUp() {
            }
        }, false);
        scanner.setPriority(priority);
        scanner.start();
        scanner.join(seconds * 1000L);                    // the scanner never ends by itself: this waits `seconds`
        scanner.shutdown();
        synchronized (lock) {
            stop[0] = true;
        }
        for (Thread t : busy) {
            t.join();
        }
        return scanner.getTimingStats();
    }

    // ------------------------------------------------------------------ helpers

    static final String[] RAW_HEADER = {"profile", "style", "layout", "prediction", "interval_ms", "seed", "cpm",
        "total_err", "uncorrected_err", "presses_per_char", "steps_per_char", "unfinished", "selections_per_char"};
    static final String[] SUMMARY_HEADER = {"profile", "style", "layout", "prediction", "interval_ms", "cpm",
        "cpm_sd", "total_err", "uncorrected_err", "presses_per_char", "steps_per_char", "unfinished"};

    static String[] row(String profile, ScanStyle style, LayoutType layout, PredictionMode mode, int interval,
                        long seed, RunResult r) {
        return new String[]{profile, style.name(), layout.name(), mode.name(), String.valueOf(interval),
            String.valueOf(seed), CsvWriter.num(r.cpm(), 4), CsvWriter.num(r.totalErrorRate(), 5),
            CsvWriter.num(r.uncorrectedErrorRate(), 5), CsvWriter.num(r.pressesPerChar(), 4),
            CsvWriter.num(r.stepsPerChar(), 4), String.valueOf(r.getUnfinished()),
            CsvWriter.num(r.selectionsPerChar(), 4)};
    }

    /** The metrics of a raw row, in the order ExperimentRunner.Summary expects. */
    static double[] fromRow(String[] r) {
        return new double[]{Double.parseDouble(r[6]), Double.parseDouble(r[7]), Double.parseDouble(r[8]),
            Double.parseDouble(r[9]), Double.parseDouble(r[10]), Double.parseDouble(r[11])};
    }

    static String[] summaryRow(String profile, String style, String layout, String mode, String interval,
                               ExperimentRunner.Summary s) {
        return new String[]{profile, style, layout, mode, interval, CsvWriter.num(s.cpm(), 3),
            CsvWriter.num(s.cpmSd(), 3), CsvWriter.num(s.totalErrorRate(), 5), CsvWriter.num(s.uncorrectedErrorRate(), 5),
            CsvWriter.num(s.pressesPerChar(), 4), CsvWriter.num(s.stepsPerChar(), 4), String.valueOf(s.unfinished())};
    }

    private static List<String[]> mysqlRows(List<String[]> raw) {
        List<String[]> rows = new ArrayList<String[]>();
        for (String[] r : raw) {
            rows.add(new String[]{r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7], r[8], r[9]});
        }
        return rows;
    }

    private List<String[]> runAll(List<Callable<String[]>> tasks) throws Exception {
        List<String[]> rows = new ArrayList<String[]>();
        for (Future<String[]> f : pool.invokeAll(tasks)) {
            rows.add(f.get());
        }
        return rows;
    }

    private List<double[]> runAllPlain(List<Callable<double[]>> tasks) throws Exception {
        List<double[]> rows = new ArrayList<double[]>();
        for (Future<double[]> f : pool.invokeAll(tasks)) {
            rows.add(f.get());
        }
        return rows;
    }

    private static int[] range(int from, int to, int step) {
        int[] r = new int[(to - from) / step + 1];
        for (int i = 0; i < r.length; i++) {
            r[i] = from + i * step;
        }
        return r;
    }

    /** Makes sure the MySQL schema exists before results are written (used with --mysql). */
    static void installSchema() throws Exception {
        try (Connection con = Database.getConnection()) {
            SchemaInstaller.install(con);
        }
    }
}
