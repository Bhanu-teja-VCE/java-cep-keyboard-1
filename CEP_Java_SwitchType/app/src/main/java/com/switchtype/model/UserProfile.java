package com.switchtype.model;

import java.io.Serializable;

/** A named user of this device with their own settings. */
public class UserProfile implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private String name;
    private Settings settings;

    public UserProfile(int id, String name, Settings settings) {
        this.id = id;
        this.name = name;
        this.settings = settings;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Settings getSettings() {
        return settings;
    }

    public void setSettings(Settings settings) {
        this.settings = settings;
    }

    @Override
    public String toString() {
        return name;
    }
}
