package com.switchtype.ui;

import com.switchtype.db.DbConfig;
import com.switchtype.io.ModelStore;
import com.switchtype.live.Recommender;
import com.switchtype.live.SessionController;
import com.switchtype.model.Settings;
import com.switchtype.model.SwitchChoice;
import com.switchtype.model.UserProfile;
import com.switchtype.predict.LanguageModel;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import javax.swing.WindowConstants;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.List;

/**
 * The main window: five tabs (Unit IV: JTabbedPane). It owns the switch: a
 * KeyEventDispatcher sees every key event of the window first, so the Space bar works as
 * the switch whatever component has focus, and auto-repeat is filtered in the controller.
 */
public class MainFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private final AppContext app;
    private final JTabbedPane tabs = new JTabbedPane();
    private final KeyboardPanel keyboard;
    private final CalibrationPanel calibration;
    private final HistoryPanel history;
    private final ProfilesPanel profilesPanel;
    private final DataPanel data;
    private UserProfile current;
    private boolean calibrationHeld;

    public MainFrame(AppContext app) {
        super("SwitchType - single-switch keyboard");
        this.app = app;
        keyboard = new KeyboardPanel(app, this);
        calibration = new CalibrationPanel(this);
        history = new HistoryPanel(this);
        profilesPanel = new ProfilesPanel(this);
        data = new DataPanel(this);
        tabs.addTab("Keyboard", keyboard);
        tabs.addTab("Calibrate", calibration);
        tabs.addTab("History", history);
        tabs.addTab("Profiles", profilesPanel);
        tabs.addTab("Data & privacy", data);
        tabs.setFocusable(false);
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedComponent() != calibration) {
                calibration.leave();
            }
            if (tabs.getSelectedComponent() == history) {
                history.reload();
            }
        });
        setContentPane(tabs);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exit();
            }
        });
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this::dispatchSwitch);
        setSize(1280, 860);
        setLocationRelativeTo(null);
        java.awt.Rectangle screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        if (screen.height < 900 || screen.width < 1300) {
            setExtendedState(MAXIMIZED_BOTH);            // small laptop screens: use all of it
        }
    }

    /** Routes the switch key to the keyboard or the calibration tab (Unit IV: keyboard events). */
    private boolean dispatchSwitch(KeyEvent e) {
        if (!isActive() || (e.getID() != KeyEvent.KEY_PRESSED && e.getID() != KeyEvent.KEY_RELEASED)) {
            return false;
        }
        boolean down = e.getID() == KeyEvent.KEY_PRESSED;
        SessionController c = keyboard.getController();
        if (tabs.getSelectedComponent() == keyboard && c != null) {
            SwitchChoice choice = c.getSettings().getSwitchChoice();
            boolean isSwitch = (choice == SwitchChoice.SPACE && e.getKeyCode() == KeyEvent.VK_SPACE)
                    || (choice == SwitchChoice.ENTER && e.getKeyCode() == KeyEvent.VK_ENTER);
            if (isSwitch) {
                if (down) {
                    c.switchDown();
                } else {
                    c.switchUp();
                }
                return true;
            }
        } else if (tabs.getSelectedComponent() == calibration
                && (e.getKeyCode() == KeyEvent.VK_SPACE || e.getKeyCode() == KeyEvent.VK_ENTER)) {
            if (down && !calibrationHeld) {
                calibration.switchDown();
            }
            calibrationHeld = down;
            return true;
        }
        return false;
    }

    /** Loads profiles (MySQL if db.properties exists, otherwise offline) and starts typing. */
    public void startUp() {
        final boolean tryMySql = new File(DbConfig.FILE_NAME).exists();
        Async.run(app.getWorker(), this, () -> {
            app.loadUserModel();
            if (tryMySql) {
                try {
                    app.tryMySql();
                } catch (Exception ex) {
                    System.err.println("MySQL not available, working offline: " + ex.getMessage());
                    app.useOffline();
                }
            }
            return loadOrCreateProfiles();
        }, this::profilesLoaded);
    }

    /** Runs on the worker: reads the profiles and creates a default one if there are none. */
    private List<UserProfile> loadOrCreateProfiles() throws Exception {
        List<UserProfile> list = app.getProfiles().findAll();
        if (list.isEmpty()) {
            app.getProfiles().save(new UserProfile(0, "Default", new Settings()));
            list = app.getProfiles().findAll();
        }
        return list;
    }

    private void profilesLoaded(List<UserProfile> list) {
        profilesPanel.setProfiles(list);
        UserProfile chosen = list.get(0);
        if (current != null) {
            for (UserProfile p : list) {
                if (p.getId() == current.getId()) {
                    chosen = p;
                }
            }
        }
        useProfile(chosen);
    }

    public void useProfile(UserProfile p) {
        current = p;
        keyboard.start(p);
        data.refresh(p.getSettings().isEventLogging());
        setTitle("SwitchType - " + p.getName());
    }

    public void saveProfile(final UserProfile p) {
        Async.run(app.getWorker(), this, () -> {
            app.getProfiles().save(p);
            return app.getProfiles().findAll();
        }, list -> profilesPanel.setProfiles(list));
    }

    public void deleteProfile(final UserProfile p) {
        if (current != null && p.getId() == current.getId()) {
            keyboard.stop();
        }
        Async.run(app.getWorker(), this, () -> {
            app.getProfiles().deleteUserData(p.getId());
            return loadOrCreateProfiles();
        }, list -> {
            if (current != null && current.getId() == p.getId()) {
                current = null;
            }
            profilesLoaded(list);
        });
    }

    public void deleteCurrentProfile() {
        if (current == null) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this, "Delete profile '" + current.getName()
                + "' with all its sessions and events from every table? This cannot be undone.",
                "Delete all my data", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer == JOptionPane.YES_OPTION) {
            deleteProfile(current);
        }
    }

    public void connectToDatabase(boolean showResult) {
        keyboard.stop();
        Async.run(app.getWorker(), this, () -> {
            app.tryMySql();
            return loadOrCreateProfiles();
        }, list -> {
            current = null;
            profilesLoaded(list);
            if (showResult) {
                JOptionPane.showMessageDialog(this, "Connected to " + app.getProfiles().describe());
            }
        });
        // Queued after the job above: if the connection failed (Async showed the error and no
        // profile was started), typing restarts with the offline store that is still active.
        app.getWorker().submit(() -> javax.swing.SwingUtilities.invokeLater(() -> {
            if (keyboard.getController() == null && current != null) {
                keyboard.start(current);
            }
        }));
    }

    public void useOffline() {
        keyboard.stop();
        app.useOffline();
        current = null;
        Async.run(app.getWorker(), this, this::loadOrCreateProfiles, this::profilesLoaded);
    }

    public void applyRecommendation(Recommender.Recommendation r) {
        if (r == null || keyboard.getController() == null) {
            return;
        }
        Settings s = keyboard.getController().getSettings();
        s.setStyle(r.style);
        s.setLayout(r.layout);
        s.setPrediction(r.prediction);
        s.setIntervalMs(r.intervalMs);
        keyboard.getController().applySettings(s);
        keyboard.settingsChanged(s);
        tabs.setSelectedComponent(keyboard);
    }

    public void setEventLogging(boolean on) {
        if (keyboard.getController() == null) {
            return;
        }
        Settings s = keyboard.getController().getSettings();
        s.setEventLogging(on);
        keyboard.getController().applySettings(s);
        keyboard.settingsChanged(s);
    }

    public void setModel(LanguageModel model) {
        app.setModel(model);
        if (keyboard.getController() != null) {
            keyboard.getController().setModel(model);
        }
    }

    public void sessionSaved() {
        if (tabs.getSelectedComponent() == history) {
            history.reload();
        }
    }

    public int currentProfileId() {
        return current == null ? 0 : current.getId();
    }

    public AppContext getApp() {
        return app;
    }

    public JTabbedPane getTabs() {
        return tabs;
    }

    public KeyboardPanel getKeyboard() {
        return keyboard;
    }

    public DataPanel getData() {
        return data;
    }

    public CalibrationPanel getCalibration() {
        return calibration;
    }

    /** Saves the summary and the learned words (on the worker), stops the threads and exits. */
    private void exit() {
        keyboard.stop();
        final LanguageModel snapshot = app.getModel().copy();      // copied on the EDT, written by the worker
        app.getWorker().submit(() -> {
            try {
                ModelStore.save(snapshot, AppContext.USER_MODEL);
            } catch (Exception ex) {
                System.err.println(ex.getMessage());
            }
        });
        try {
            app.getWorker().shutdown();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        dispose();
        System.exit(0);
    }
}
