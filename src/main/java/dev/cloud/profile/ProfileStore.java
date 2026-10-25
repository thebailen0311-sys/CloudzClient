package dev.cloud.profile;

import dev.cloud.storage.CloudPaths;
import dev.cloud.storage.SafeJson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Local Cloude profiles (profile.json) and saved account entries (accounts.json).
 * A local profile is only a name. No passwords are stored, and no online sign-in is performed.
 */
public final class ProfileStore {
    private ProfileStore() {}

    public static final class Profile {
        public String id, username, displayName;
        public long createdAt;
    }

    public static final class ProfileFile {
        public int version = 1;
        public boolean setupComplete;
        public String selectedId;
        public List<Profile> profiles = new ArrayList<>();
    }

    public static final class Account {
        public String id, type, label, username, authStatus;
    }

    public static final class AccountFile {
        public int version = 1;
        public String selectedId;
        public List<Account> accounts = new ArrayList<>();
    }

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static ProfileFile profileFile = new ProfileFile();
    private static AccountFile accountFile = new AccountFile();

    /** Microsoft / Minecraft sign-in needs a registered Azure client ID, which this build does not have. */
    public static boolean microsoftConfigured() {
        return false;
    }

    public static synchronized void load() {
        profileFile = SafeJson.load(CloudPaths.ROOT.resolve("profile.json"), ProfileFile.class, ProfileFile::new);
        accountFile = SafeJson.load(CloudPaths.ROOT.resolve("accounts.json"), AccountFile.class, AccountFile::new);

        if (profileFile.profiles == null) profileFile.profiles = new ArrayList<>();
        profileFile.profiles.removeIf(p -> p == null || p.id == null || p.username == null);
        for (Profile p : profileFile.profiles) if (p.displayName == null) p.displayName = p.username;
        if (findProfile(profileFile.selectedId) == null)
            profileFile.selectedId = profileFile.profiles.isEmpty() ? null : profileFile.profiles.get(0).id;

        if (accountFile.accounts == null) accountFile.accounts = new ArrayList<>();
        accountFile.accounts.removeIf(a -> a == null || a.id == null || a.type == null);
        if (findAccount(accountFile.selectedId) == null)
            accountFile.selectedId = accountFile.accounts.isEmpty() ? null : accountFile.accounts.get(0).id;

        persist(); // creates the files when they are missing
    }

    public static synchronized boolean needsSetup() {
        return !profileFile.setupComplete || profileFile.profiles.isEmpty();
    }

    public static synchronized void completeSetup() {
        profileFile.setupComplete = true;
        persist();
    }

    public static synchronized List<Profile> profiles() {
        return List.copyOf(profileFile.profiles);
    }

    public static synchronized Profile selectedProfile() {
        return findProfile(profileFile.selectedId);
    }

    public static synchronized Profile addProfile(String username, String displayName) {
        String u = checkUsername(username, null);
        Profile p = new Profile();
        p.id = UUID.randomUUID().toString();
        p.username = u;
        p.displayName = checkDisplay(displayName, u);
        p.createdAt = System.currentTimeMillis();
        profileFile.profiles.add(p);
        if (profileFile.selectedId == null) profileFile.selectedId = p.id;
        persist();
        return p;
    }

    public static synchronized void editProfile(String id, String username, String displayName) {
        Profile p = requireProfile(id);
        p.username = checkUsername(username, id);
        p.displayName = checkDisplay(displayName, p.username);
        persist();
    }

    public static synchronized void removeProfile(String id) {
        Profile p = requireProfile(id);
        if (profileFile.profiles.size() <= 1) throw new IllegalArgumentException("You need at least one profile.");
        profileFile.profiles.remove(p);
        if (id.equals(profileFile.selectedId)) profileFile.selectedId = profileFile.profiles.get(0).id;
        persist();
    }

    public static synchronized void selectProfile(String id) {
        requireProfile(id);
        profileFile.selectedId = id;
        persist();
    }

    public static synchronized List<Account> accounts() {
        return List.copyOf(accountFile.accounts);
    }

    public static synchronized Account selectedAccount() {
        return findAccount(accountFile.selectedId);
    }

    /** Saves a name-only account entry. Its status is always "Not authenticated". */
    public static synchronized Account addLocalAccount(String label, String username) {
        String u = checkUsername(username, null);
        Account a = new Account();
        a.id = UUID.randomUUID().toString();
        a.type = "local";
        a.label = checkDisplay(label, u);
        a.username = u;
        a.authStatus = "Not authenticated";
        accountFile.accounts.add(a);
        if (accountFile.selectedId == null) accountFile.selectedId = a.id;
        persist();
        return a;
    }

    public static synchronized void removeAccount(String id) {
        Account a = findAccount(id);
        if (a == null) throw new IllegalArgumentException("Account not found.");
        accountFile.accounts.remove(a);
        if (id.equals(accountFile.selectedId))
            accountFile.selectedId = accountFile.accounts.isEmpty() ? null : accountFile.accounts.get(0).id;
        persist();
    }

    public static synchronized void selectAccount(String id) {
        if (findAccount(id) == null) throw new IllegalArgumentException("Account not found.");
        accountFile.selectedId = id;
        persist();
    }

    private static String checkUsername(String raw, String selfId) {
        String u = raw == null ? "" : raw.trim();
        if (!USERNAME.matcher(u).matches())
            throw new IllegalArgumentException("Username must be 3-16 characters: letters, numbers or underscore.");
        for (Profile p : profileFile.profiles)
            if (!p.id.equals(selfId) && p.username.equalsIgnoreCase(u))
                throw new IllegalArgumentException("That username is already used by another profile.");
        return u;
    }

    private static String checkDisplay(String raw, String fallback) {
        String d = raw == null ? "" : raw.trim();
        if (d.isEmpty()) return fallback;
        if (d.length() > 32) throw new IllegalArgumentException("Display name must be 32 characters or fewer.");
        for (int i = 0; i < d.length(); i++)
            if (Character.isISOControl(d.charAt(i)))
                throw new IllegalArgumentException("Display name contains invalid characters.");
        return d;
    }

    private static Profile findProfile(String id) {
        if (id == null) return null;
        for (Profile p : profileFile.profiles) if (id.equals(p.id)) return p;
        return null;
    }

    private static Profile requireProfile(String id) {
        Profile p = findProfile(id);
        if (p == null) throw new IllegalArgumentException("Profile not found.");
        return p;
    }

    private static Account findAccount(String id) {
        if (id == null) return null;
        for (Account a : accountFile.accounts) if (id.equals(a.id)) return a;
        return null;
    }

    private static void persist() {
        try {
            SafeJson.save(CloudPaths.ROOT.resolve("profile.json"), profileFile);
            SafeJson.save(CloudPaths.ROOT.resolve("accounts.json"), accountFile);
        } catch (IOException e) {
            System.err.println("[CloudClient] Failed to save profiles: " + e);
        }
    }
}
