package com.switchtype.db;

import com.switchtype.exception.ProfileStoreException;
import com.switchtype.model.UserProfile;

import java.util.List;

/** Stores user profiles. MySQL or a local file, chosen at start-up. */
public interface ProfileRepository {

    List<UserProfile> findAll() throws ProfileStoreException;

    /** Inserts (id 0) or updates the profile; returns it with its id set. */
    UserProfile save(UserProfile profile) throws ProfileStoreException;

    /** Deletes the profile and every session and event of it. */
    void deleteUserData(int profileId) throws ProfileStoreException;

    /** A short description for the status bar. */
    String describe();
}
