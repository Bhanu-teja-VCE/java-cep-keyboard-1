package com.switchtype.db;

import com.switchtype.exception.ProfileStoreException;
import com.switchtype.io.SafeObjectInput;
import com.switchtype.model.UserProfile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Offline profile store: the whole list is serialized to one file. Reading uses an
 * allow-list of classes (see SafeObjectInput).
 */
public class FileProfileRepository implements ProfileRepository {

    private static final String[] ALLOWED = {
        "java.util.ArrayList", UserProfile.class.getName(), com.switchtype.model.Settings.class.getName(),
        com.switchtype.model.ScanStyle.class.getName(), com.switchtype.model.LayoutType.class.getName(),
        com.switchtype.model.PredictionMode.class.getName(), com.switchtype.model.SwitchChoice.class.getName(),
        "java.lang.Enum", "java.lang.String"};

    private final File file;
    private final SessionRepository sessions;

    public FileProfileRepository(File file, SessionRepository sessions) {
        this.file = file;
        this.sessions = sessions;
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized List<UserProfile> findAll() throws ProfileStoreException {
        if (!file.exists()) {
            return new ArrayList<UserProfile>();
        }
        try (ObjectInputStream in = new SafeObjectInput(new FileInputStream(file), ALLOWED)) {
            return (ArrayList<UserProfile>) in.readObject();
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            throw new ProfileStoreException("Could not read " + file.getName() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized UserProfile save(UserProfile profile) throws ProfileStoreException {
        List<UserProfile> all = findAll();
        if (profile.getId() == 0) {
            int max = 0;
            for (UserProfile p : all) {
                max = Math.max(max, p.getId());
            }
            profile.setId(max + 1);
            all.add(profile);
        } else {
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).getId() == profile.getId()) {
                    all.set(i, profile);
                }
            }
        }
        write(all);
        return profile;
    }

    @Override
    public synchronized void deleteUserData(int profileId) throws ProfileStoreException {
        List<UserProfile> all = findAll();
        Iterator<UserProfile> it = all.iterator();
        while (it.hasNext()) {
            if (it.next().getId() == profileId) {
                it.remove();
            }
        }
        write(all);
        sessions.deleteSessions(profileId);
    }

    private void write(List<UserProfile> all) throws ProfileStoreException {
        File dir = file.getAbsoluteFile().getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            throw new ProfileStoreException("Cannot create folder " + dir);
        }
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file))) {
            out.writeObject(new ArrayList<UserProfile>(all));
        } catch (IOException e) {
            throw new ProfileStoreException("Could not write " + file.getName() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public String describe() {
        return "offline (" + file.getPath() + ")";
    }
}
