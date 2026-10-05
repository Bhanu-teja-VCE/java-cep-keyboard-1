package com.switchtype.ui;

import com.switchtype.db.Database;
import com.switchtype.db.FileProfileRepository;
import com.switchtype.db.LocalSessionRepository;
import com.switchtype.db.MySqlProfileRepository;
import com.switchtype.db.MySqlSessionRepository;
import com.switchtype.db.ProfileRepository;
import com.switchtype.db.SchemaInstaller;
import com.switchtype.db.SessionRepository;
import com.switchtype.exception.SwitchTypeException;
import com.switchtype.io.ModelStore;
import com.switchtype.live.DbWorker;
import com.switchtype.predict.CharFrequency;
import com.switchtype.predict.CorpusLoader;
import com.switchtype.predict.LanguageModel;
import com.switchtype.sim.PhraseBank;

import java.io.File;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything the screens share: the stores (MySQL or offline files), the background
 * worker, the language model and the phrases. Created once by Main.
 */
public class AppContext {

    public static final File DATA_DIR = new File("data");
    public static final File USER_MODEL = new File(DATA_DIR, "user-model.ser");

    private final DbWorker worker = new DbWorker();
    private final PhraseBank phrases;
    private final CharFrequency frequency;
    private volatile LanguageModel model;          // replaced on reset/import (EDT), read by the EDT
    private volatile ProfileRepository profiles;   // swapped by the worker when MySQL connects
    private volatile SessionRepository sessions;
    private volatile boolean mysql;

    public AppContext() throws SwitchTypeException {
        phrases = new PhraseBank();
        List<String> training = trainingText();
        frequency = CorpusLoader.frequencies(training);
        model = baseModel(training);
        useOffline();
        worker.start();
    }

    /** Corpus plus TRAIN phrases. Practice uses the TEST phrases, so prediction is never trained on them. */
    private List<String> trainingText() throws SwitchTypeException {
        List<String> training = new ArrayList<String>(CorpusLoader.readResourceLines(CorpusLoader.CORPUS_RESOURCE));
        training.addAll(phrases.train());
        return training;
    }

    private static LanguageModel baseModel(List<String> training) {
        LanguageModel m = new LanguageModel();
        CorpusLoader.train(m, training);
        return m;
    }

    /** Loads the vocabulary learned on this device, if any. Call on the worker thread. */
    public void loadUserModel() {
        if (USER_MODEL.exists()) {
            try {
                model = ModelStore.load(USER_MODEL);
            } catch (SwitchTypeException e) {
                System.err.println(e.getMessage() + " - using the built-in vocabulary");
            }
        }
    }

    /** Forgets everything learned on this device. */
    public void resetUserModel() throws SwitchTypeException {
        if (USER_MODEL.exists() && !USER_MODEL.delete()) {
            throw new com.switchtype.exception.ProfileStoreException("Could not delete " + USER_MODEL);
        }
        model = baseModel(trainingText());
    }

    public final void useOffline() {
        LocalSessionRepository local = new LocalSessionRepository(DATA_DIR);
        sessions = local;
        profiles = new FileProfileRepository(new File(DATA_DIR, "profiles.ser"), local);
        mysql = false;
    }

    /**
     * Tries MySQL with the current db.properties and installs the schema if needed.
     * Must run on the worker thread (network I/O).
     */
    public void tryMySql() throws SwitchTypeException {
        try (Connection con = Database.getConnection()) {
            if (!SchemaInstaller.isInstalled(con)) {
                SchemaInstaller.install(con);
            }
        } catch (java.sql.SQLException e) {
            throw new com.switchtype.exception.ProfileStoreException("MySQL check failed: " + e.getMessage(), e);
        }
        profiles = new MySqlProfileRepository();
        sessions = new MySqlSessionRepository();
        mysql = true;
    }

    public DbWorker getWorker() {
        return worker;
    }

    public PhraseBank getPhrases() {
        return phrases;
    }

    public CharFrequency getFrequency() {
        return frequency;
    }

    public LanguageModel getModel() {
        return model;
    }

    public void setModel(LanguageModel model) {
        this.model = model;
    }

    public ProfileRepository getProfiles() {
        return profiles;
    }

    public SessionRepository getSessions() {
        return sessions;
    }

    public boolean isMySql() {
        return mysql;
    }
}
