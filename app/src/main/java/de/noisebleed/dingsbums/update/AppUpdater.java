package de.noisebleed.dingsbums.update;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.noisebleed.dingsbums.BuildConfig;
import de.noisebleed.dingsbums.R;

/**
 * Selbst-Update nach dem Vorbild der EP-App, nur ohne eigenes Backend: das neueste Release kommt
 * aus der GitHub-API des öffentlichen Repos, die CI hängt an jedes Release neben das APK eine
 * {@code update.json} mit versionCode und SHA-256. Eine neuere Version wird im Hintergrund geladen,
 * geprüft und dann über eine PackageInstaller-Session installiert.
 *
 * Statistik und Spielstand überstehen das: ein Update mit demselben Signaturschlüssel ersetzt nur
 * den Code, die SharedPreferences der App bleiben unberührt.
 */
public final class AppUpdater {

    public interface Listener {
        /** Auf dem Main-Thread. available != null: Update liegt geprüft bereit. */
        void onResult(@Nullable String available, @Nullable String error);
    }

    private static final long CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L;
    private static final int TIMEOUT_MS = 15_000;

    private static final String PREFS = "update_state";
    private static final String KEY_LAST_CHECK = "last_check";
    private static final String KEY_READY_CODE = "ready_version_code";
    private static final String KEY_READY_NAME = "ready_version_name";

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());

    private AppUpdater() {
    }

    /**
     * Darf dieses Profil überhaupt Apps installieren? Im Amazon-Kids-Profil des Fire-Tablets nicht —
     * dort wird gar nicht erst gesucht; das Update aus dem Eltern-Profil gilt für alle Profile.
     */
    public static boolean isAllowed(Context context) {
        UserManager users = context.getSystemService(UserManager.class);
        if (users == null) {
            return true;
        }
        if (users.hasUserRestriction(UserManager.DISALLOW_INSTALL_APPS)
                || users.hasUserRestriction(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)) {
            return false;
        }
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                || !users.hasUserRestriction(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY);
    }

    /** Beim App-Start: höchstens alle paar Stunden wirklich nachfragen. */
    public static void checkIfDue(Context context, Listener listener) {
        long last = prefs(context).getLong(KEY_LAST_CHECK, 0);
        if (isAllowed(context) && System.currentTimeMillis() - last >= CHECK_INTERVAL_MS) {
            check(context, listener);
        }
    }

    public static void check(Context context, Listener listener) {
        Context app = context.getApplicationContext();
        // Auch bei Fehlern (offline) erst im nächsten Intervall wieder fragen.
        prefs(app).edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();
        executor.execute(() -> {
            try {
                Release release = fetchLatest();
                SharedPreferences p = prefs(app);
                if (release == null || release.versionCode <= BuildConfig.VERSION_CODE) {
                    p.edit().remove(KEY_READY_CODE).remove(KEY_READY_NAME).apply();
                    deliver(listener, null, null);
                    return;
                }
                download(app, release);
                p.edit()
                        .putInt(KEY_READY_CODE, release.versionCode)
                        .putString(KEY_READY_NAME, release.versionName)
                        .apply();
                deliver(listener, release.versionName, null);
            } catch (IOException | JSONException e) {
                deliver(listener, null, e.getMessage());
            }
        });
    }

    /** Fertig heruntergeladene, geprüfte Version, die neuer ist als die laufende — sonst null. */
    @Nullable
    public static String readyVersionName(Context context) {
        SharedPreferences p = prefs(context);
        int code = p.getInt(KEY_READY_CODE, 0);
        if (code <= BuildConfig.VERSION_CODE || !apkFile(context, code).exists()) {
            return null;
        }
        return p.getString(KEY_READY_NAME, String.valueOf(code));
    }

    /**
     * Startet die Installation der bereitliegenden Version. Fehlt noch die einmalige Freigabe
     * "Apps aus dieser Quelle installieren", öffnet sich zuerst die Systemeinstellung — gibt dann
     * false zurück, der Aufrufer versucht es nach der Rückkehr erneut.
     */
    public static boolean install(Activity activity) {
        int code = prefs(activity).getInt(KEY_READY_CODE, 0);
        File apk = apkFile(activity, code);
        if (code <= BuildConfig.VERSION_CODE || !apk.exists()) {
            return true;
        }
        if (!activity.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(activity, R.string.update_allow_source, Toast.LENGTH_LONG).show();
            activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName())));
            return false;
        }
        Context app = activity.getApplicationContext();
        executor.execute(() -> {
            try {
                commitSession(app, apk);
            } catch (IOException e) {
                main.post(() -> Toast.makeText(app,
                        app.getString(R.string.update_install_failed, e.getMessage()),
                        Toast.LENGTH_LONG).show());
            }
        });
        return true;
    }

    private static void commitSession(Context context, File apk) throws IOException {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Ein Selbst-Update darf ab Android 12 ohne Rückfrage durchlaufen, wenn das System es
            // zulässt — sonst kommt der normale Bestätigungsdialog.
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        }
        int sessionId = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(sessionId)) {
            try (InputStream in = new FileInputStream(apk);
                 OutputStream out = session.openWrite("dingsbums.apk", 0, apk.length())) {
                copy(in, out);
                session.fsync(out);
            }
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Mutable, weil das System Status und Bestätigungs-Intent einträgt.
                flags |= PendingIntent.FLAG_MUTABLE;
            }
            PendingIntent result = PendingIntent.getBroadcast(context, sessionId,
                    new Intent(context, InstallResultReceiver.class), flags);
            session.commit(result.getIntentSender());
        }
    }

    // ---------------------------------------------------------------- Abfrage und Download

    private static final class Release {
        int versionCode;
        String versionName;
        String sha256;
        String apk;
    }

    /** Neuestes Release laut GitHub, mit den Angaben aus dessen update.json; null wenn keins. */
    @Nullable
    private static Release fetchLatest() throws IOException, JSONException {
        // "latest" ist das neueste veröffentlichte Release ohne Entwürfe und Vorabversionen.
        JSONObject latest;
        try {
            latest = new JSONObject(readText(BuildConfig.UPDATE_API + "/releases/latest"));
        } catch (NotFoundException e) {
            return null;
        }
        JSONArray assets = latest.optJSONArray("assets");
        String infoUrl = assetUrl(assets, "update.json");
        if (infoUrl == null) {
            // Ein Release ohne update.json (von Hand angelegt) wird nicht angeboten.
            return null;
        }
        JSONObject info = new JSONObject(readText(infoUrl));
        Release release = new Release();
        release.versionCode = info.getInt("version_code");
        release.versionName = info.getString("version_name");
        release.sha256 = info.getString("sha256");
        release.apk = assetUrl(assets, info.getString("apk"));
        return release.apk != null ? release : null;
    }

    /** Download-Adresse des Release-Anhangs mit diesem Namen, oder null. */
    @Nullable
    private static String assetUrl(@Nullable JSONArray assets, String name) throws JSONException {
        if (assets == null) {
            return null;
        }
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            if (name.equals(asset.optString("name"))) {
                return asset.getString("browser_download_url");
            }
        }
        return null;
    }

    private static void download(Context context, Release release) throws IOException {
        File target = apkFile(context, release.versionCode);
        File dir = target.getParentFile();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cache-Verzeichnis nicht anlegbar");
        }
        // Ältere Downloads wegräumen, nur die aktuelle Version behalten.
        File[] old = dir.listFiles();
        if (old != null) {
            for (File f : old) {
                if (!f.equals(target)) {
                    f.delete();
                }
            }
        }
        if (target.exists() && release.sha256.equalsIgnoreCase(sha256(target))) {
            return;
        }
        File part = new File(dir, target.getName() + ".part");
        HttpURLConnection connection = open(release.apk);
        try (InputStream in = connection.getInputStream(); OutputStream out = new FileOutputStream(part)) {
            copy(in, out);
        } finally {
            connection.disconnect();
        }
        if (!release.sha256.equalsIgnoreCase(sha256(part))) {
            part.delete();
            throw new IOException("Prüfsumme stimmt nicht");
        }
        if (!part.renameTo(target)) {
            throw new IOException("Download konnte nicht abgelegt werden");
        }
    }

    /** Eigene Ausnahme, damit ein fehlendes update.json von echten Netzfehlern unterscheidbar ist. */
    private static final class NotFoundException extends IOException {
        NotFoundException(String url) {
            super("Nicht gefunden: " + url);
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        // Die GitHub-API lehnt Anfragen ohne User-Agent ab. Release-Anhänge leiten auf einen
        // anderen https-Host um; dem folgt HttpURLConnection von selbst.
        connection.setRequestProperty("User-Agent", "Dingsbums/" + BuildConfig.VERSION_NAME);
        connection.setRequestProperty("Accept", url.startsWith(BuildConfig.UPDATE_API)
                ? "application/vnd.github+json" : "*/*");
        int code = connection.getResponseCode();
        if (code == HttpURLConnection.HTTP_NOT_FOUND) {
            connection.disconnect();
            throw new NotFoundException(url);
        }
        if (code != HttpURLConnection.HTTP_OK) {
            connection.disconnect();
            throw new IOException("HTTP " + code);
        }
        return connection;
    }

    private static String readText(String url) throws IOException {
        HttpURLConnection connection = open(url);
        try (InputStream in = connection.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            copy(in, out);
            return out.toString(StandardCharsets.UTF_8.name());
        } finally {
            connection.disconnect();
        }
    }

    private static void deliver(@Nullable Listener listener, @Nullable String version, @Nullable String error) {
        if (listener != null) {
            main.post(() -> listener.onResult(version, error));
        }
    }

    private static File apkFile(Context context, int versionCode) {
        return new File(new File(context.getCacheDir(), "updates"), "dingsbums-" + versionCode + ".apk");
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
    }

    private static String sha256(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) != -1) {
                digest.update(buffer, 0, n);
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
