package com.switchtype.io;

import com.switchtype.exception.ProfileStoreException;
import com.switchtype.predict.LanguageModel;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * Saves the language model that has learned from this user (Unit III: serialization).
 * The file stays on this device; "Reset vocabulary" deletes it.
 */
public final class ModelStore {

    private static final String[] ALLOWED = {
        LanguageModel.class.getName(), "java.util.TreeMap", "java.util.HashMap",
        "java.lang.Integer", "java.lang.Number", "java.lang.String"};

    private ModelStore() {
    }

    public static void save(LanguageModel model, File file) throws ProfileStoreException {
        File dir = file.getAbsoluteFile().getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            throw new ProfileStoreException("Cannot create folder " + dir);
        }
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file))) {
            out.writeObject(model);
        } catch (IOException e) {
            throw new ProfileStoreException("Could not save the vocabulary: " + e.getMessage(), e);
        }
    }

    public static LanguageModel load(File file) throws ProfileStoreException {
        try (ObjectInputStream in = new SafeObjectInput(new FileInputStream(file), ALLOWED)) {
            Object obj = in.readObject();
            if (!(obj instanceof LanguageModel)) {
                throw new ProfileStoreException(file.getName() + " is not a SwitchType vocabulary");
            }
            return (LanguageModel) obj;
        } catch (IOException | ClassNotFoundException e) {
            throw new ProfileStoreException("Could not read " + file.getName() + ": " + e.getMessage(), e);
        }
    }
}
