package com.switchtype.sim;

import com.switchtype.exception.CorpusFormatException;
import com.switchtype.model.PredictionMode;
import com.switchtype.model.Settings;
import com.switchtype.predict.BigramPredictor;
import com.switchtype.predict.CharFrequency;
import com.switchtype.predict.CorpusLoader;
import com.switchtype.predict.LanguageModel;
import com.switchtype.predict.NoPredictor;
import com.switchtype.predict.UnigramPredictor;
import com.switchtype.predict.WordPredictor;

import java.util.ArrayList;
import java.util.List;

/**
 * Loads the text resources once and runs simulated sessions. The language model is trained
 * on the corpus plus the TRAIN phrases; every measurement types the TEST phrases.
 */
public class ExperimentRunner {

    /** Seeds used for every configuration, so compared options see the same random users. */
    public static final long[] SEEDS = {1, 2, 3, 4, 5};

    private final PhraseBank phrases;
    private final LanguageModel model;
    private final CharFrequency frequency;

    public ExperimentRunner() throws CorpusFormatException {
        phrases = new PhraseBank();
        List<String> training = new ArrayList<String>(CorpusLoader.readResourceLines(CorpusLoader.CORPUS_RESOURCE));
        training.addAll(phrases.train());
        model = new LanguageModel();
        CorpusLoader.train(model, training);
        frequency = CorpusLoader.frequencies(training);
    }

    /** A predictor for the mode. Learning is off in experiments, so the model is shared read-only. */
    public WordPredictor predictor(PredictionMode mode) {
        switch (mode) {
            case UNIGRAM:
                return new UnigramPredictor(model);
            case BIGRAM:
                return new BigramPredictor(model);
            default:
                return new NoPredictor();
        }
    }

    /** One session: the simulated user types all TEST phrases once. */
    public RunResult run(UserParams user, Settings settings, long seed) {
        SimulationDriver driver = new SimulationDriver(settings, frequency,
                predictor(settings.getPrediction()), new SimulatedUser(user, seed));
        return driver.typePhrases(phrases.test());
    }

    /** The same configuration for every seed in SEEDS. */
    public Summary runSeeds(UserParams user, Settings settings) {
        Summary s = new Summary();
        for (long seed : SEEDS) {
            s.add(run(user, settings, seed));
        }
        return s;
    }

    public PhraseBank getPhrases() {
        return phrases;
    }

    public LanguageModel getModel() {
        return model;
    }

    public CharFrequency getFrequency() {
        return frequency;
    }

    /** Mean (and spread of CPM) over several runs of one configuration. */
    public static class Summary {
        private final List<RunResult> runs = new ArrayList<RunResult>();

        public void add(RunResult r) {
            runs.add(r);
        }

        public List<RunResult> getRuns() {
            return runs;
        }

        public double cpm() {
            double sum = 0;
            for (RunResult r : runs) {
                sum += r.cpm();
            }
            return sum / runs.size();
        }

        public double cpmSd() {
            double mean = cpm();
            double sum = 0;
            for (RunResult r : runs) {
                sum += (r.cpm() - mean) * (r.cpm() - mean);
            }
            return runs.size() < 2 ? 0 : Math.sqrt(sum / (runs.size() - 1));
        }

        public double totalErrorRate() {
            double sum = 0;
            for (RunResult r : runs) {
                sum += r.totalErrorRate();
            }
            return sum / runs.size();
        }

        public double uncorrectedErrorRate() {
            double sum = 0;
            for (RunResult r : runs) {
                sum += r.uncorrectedErrorRate();
            }
            return sum / runs.size();
        }

        public double pressesPerChar() {
            double sum = 0;
            for (RunResult r : runs) {
                sum += r.pressesPerChar();
            }
            return sum / runs.size();
        }

        public double stepsPerChar() {
            double sum = 0;
            for (RunResult r : runs) {
                sum += r.stepsPerChar();
            }
            return sum / runs.size();
        }

        public int unfinished() {
            int sum = 0;
            for (RunResult r : runs) {
                sum += r.getUnfinished();
            }
            return sum;
        }
    }
}
