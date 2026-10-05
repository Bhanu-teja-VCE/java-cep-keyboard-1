package com.switchtype.io;

import com.switchtype.db.FileProfileRepository;
import com.switchtype.db.LocalSessionRepository;
import com.switchtype.db.SessionSummary;
import com.switchtype.exception.InvalidSettingsException;
import com.switchtype.exception.ProfileStoreException;
import com.switchtype.model.ScanStyle;
import com.switchtype.model.Settings;
import com.switchtype.model.UserProfile;
import com.switchtype.predict.LanguageModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageTest {

    @TempDir
    File dir;

    @Test
    void languageModelSurvivesSerialization() throws Exception {
        LanguageModel m = new LanguageModel();
        m.add("<s>", "hello");
        m.add("hello", "there");
        File f = new File(dir, "model.ser");
        ModelStore.save(m, f);
        LanguageModel back = ModelStore.load(f);
        assertEquals(1, back.bigramCount("hello", "there"));
        assertEquals(2, back.vocabularySize());
    }

    @Test
    void unexpectedClassesInAFileAreRefused() throws Exception {
        File f = new File(dir, "evil.ser");
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(f))) {
            out.writeObject(new Date());              // not on the allow-list
        }
        assertThrows(ProfileStoreException.class, () -> ModelStore.load(f));
    }

    @Test
    void offlineProfilesAndSessionsWorkWithoutMySql() throws Exception {
        LocalSessionRepository sessions = new LocalSessionRepository(dir);
        FileProfileRepository profiles = new FileProfileRepository(new File(dir, "profiles.ser"), sessions);
        Settings s = new Settings();
        s.setStyle(ScanStyle.GROUP_BINARY);
        UserProfile p = profiles.save(new UserProfile(0, "Asha", s));
        assertTrue(p.getId() > 0);
        assertEquals(ScanStyle.GROUP_BINARY, profiles.findAll().get(0).getSettings().getStyle());

        SessionSummary summary = new SessionSummary(p.getId(), "PRACTICE", System.currentTimeMillis());
        summary.setCounts(30, 28, 70, 35, 2, 60000);
        sessions.save(summary, Collections.singletonList(new String[]{"1200", "e"}));
        List<SessionSummary> history = sessions.history(p.getId());
        assertEquals(1, history.size());
        assertEquals(28.0, history.get(0).cpm(), 1e-9);
        assertEquals(0, sessions.purgeEvents(1), "the event is younger than one day");

        profiles.deleteUserData(p.getId());
        assertTrue(profiles.findAll().isEmpty());
        assertTrue(sessions.history(p.getId()).isEmpty(), "delete-all-my-data removes the sessions too");
    }

    @Test
    void csvValuesWithCommasAreQuoted() {
        assertEquals("a,\"b,c\",\"say \"\"hi\"\"\"\n", CsvWriter.line(new String[]{"a", "b,c", "say \"hi\""}));
    }

    @Test
    void settingsOutsideTheirRangeAreRejected() {
        Settings s = new Settings();
        s.setIntervalMs(150);
        assertThrows(InvalidSettingsException.class, s::validate);
        s.setIntervalMs(500);
        s.setKeySize(30);                             // below the 44 px target size
        assertThrows(InvalidSettingsException.class, s::validate);
    }
}
