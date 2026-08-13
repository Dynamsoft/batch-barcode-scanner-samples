package com.dynamsoft.bbsdatareceiver.share;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.dynamsoft.bbsdatareceiver.model.SharedScanData;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================
 * ShareReceiver  —  Core Share-Reception Logic
 * ============================================================
 * <p>
 * Responsibility
 * --------------
 * This class owns ALL logic related to receiving and interpreting
 * an incoming Android share intent.  It deliberately knows nothing
 * about Views, Fragments, or Activities — it just returns a clean
 * {@link SharedScanData} object that the UI layer can consume.
 * <p>
 * How Android sharing works (quick primer)
 * -----------------------------------------
 * When another app calls ShareCompat or a plain Intent with
 * ACTION_SEND / ACTION_SEND_MULTIPLE, Android shows the share
 * sheet and the user picks this app.  Android then starts (or
 * resumes) MainActivity with the original intent.  The shared
 * file URIs are passed as Intent extras under the key
 * {@link Intent#EXTRA_STREAM}.
 * <p>
 * Expected file format
 * --------------------
 * The sending app always shares exactly three files together:
 * <p>
 * {Type}_{timestamp}_{version}_AnnotatedImage.jpg
 * {Type}_{timestamp}_{version}_OriginalImage.jpg
 * {Type}_{timestamp}_{version}.csv
 * <p>
 * where {Type} is "Scan" or "History".
 * Example:
 * sample.jpg
 * sample.csv
 * <p>
 * ============================================================
 */
public class ShareReceiver {

    // ── File classification helpers ────────────────────────────────────────

    /**
     * Common image file extensions.
     */
    private static final List<String> IMAGE_EXTENSIONS =
            java.util.Arrays.asList(".jpg", ".jpeg", ".png", ".webp", ".bmp", ".gif", ".heic", ".heif");
    /**
     * Asset file names bundled for the "Show Sample" preview switch.
     */
    private static final String SAMPLE_IMAGE_ASSET = "sample.jpg";
    private static final String SAMPLE_CSV_ASSET = "sample.csv";
    /**
     * Cached result of the first {@link #loadSample} call. The sample
     * assets never change at runtime, so loading/parsing them once and
     * reusing the result avoids re-copying the asset file on every toggle
     * of the "Show Sample" switch — callers (the UI layer) don't need to
     * do their own caching.
     */
    private static SharedScanData cachedSample;

    /**
     * Returns true if the file name has a recognized image extension.
     */
    private static boolean hasImageExtension(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        for (String ext : IMAGE_EXTENSIONS) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }

    // ── Public entry point ─────────────────────────────────────────────────

    /**
     * Loose check: any image file whose name contains "AnnotatedImage".
     * We intentionally avoid a strict full-name pattern so that minor
     * naming variations in future app versions still work.
     */
    private static boolean isAnnotatedImage(String name) {
        return hasImageExtension(name) && name.contains("AnnotatedImage");
    }

    // ── Step 2 helper: extract URIs ────────────────────────────────────────

    /**
     * Loose check: any image file whose name contains "OriginalImage".
     */
    private static boolean isOriginalImage(String name) {
        return hasImageExtension(name) && name.contains("OriginalImage");
    }

    // ── Step 3 helper: classify & assemble ─────────────────────────────────

    /**
     * Loose check: any file whose name ends with ".csv".
     */
    private static boolean isCsvFile(String name) {
        return name != null && name.toLowerCase().endsWith(".csv");
    }

    // ── Sample/demo data (assets/sample.jpg + assets/sample.csv) ───────────

    /**
     * Attempt to parse a share intent into a {@link SharedScanData}.
     *
     * <p>Call this from {@code Activity.onCreate()} and
     * {@code Activity.onNewIntent()} with the received {@link Intent}.</p>
     *
     * @param context Android context (used for ContentResolver queries).
     * @param intent  The intent delivered to the Activity.
     * @return A {@link SharedScanData} if the intent is a share intent;
     * {@code null} if the intent is not a share intent at all.
     */
    public static SharedScanData receive(Context context, Intent intent) {

        // ── Step 1: Confirm this is a share intent ─────────────────────────
        if (intent == null) return null;
        String action = intent.getAction();
        boolean isSendMultiple = Intent.ACTION_SEND_MULTIPLE.equals(action);
        boolean isSend = Intent.ACTION_SEND.equals(action);
        if (!isSendMultiple && !isSend) return null;

        // ── Step 2: Extract the list of shared file URIs from the intent ───
        List<Uri> uris = extractUrisFromIntent(intent);
        if (uris == null || uris.isEmpty()) return null;

        // ── Step 3: Resolve display names and classify each URI ───────────
        return classifyAndBuild(context, uris);
    }

    /**
     * Extracts all shared {@link Uri}s from the intent extras.
     * ACTION_SEND_MULTIPLE stores a list; ACTION_SEND stores a single URI.
     */
    private static List<Uri> extractUrisFromIntent(Intent intent) {
        if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            // Multiple files: URIs stored as an ArrayList<Parcelable>
            return intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        } else {
            // Single file: URI stored as a single Parcelable
            Uri single = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (single != null) {
                List<Uri> list = new ArrayList<>();
                list.add(single);
                return list;
            }
        }
        return null;
    }

    /**
     * Resolves file names, validates the naming pattern,
     * and constructs a {@link SharedScanData}.
     */
    private static SharedScanData classifyAndBuild(Context context, List<Uri> uris) {
        List<String> fileNames = new ArrayList<>();
        Uri annotatedUri = null;
        Uri originalUri = null;
        Uri csvUri = null;

        for (Uri uri : uris) {
            // Resolve the human-readable file name for this URI
            String name = resolveFileName(context, uri);
            fileNames.add(name != null ? name : uri.toString());

            if (name == null) continue;

            // Classify by checking whether the file name contains the
            // expected keywords — no strict full-pattern match required.
            if (isAnnotatedImage(name)) {
                annotatedUri = uri;
            } else if (isOriginalImage(name)) {
                originalUri = uri;
            } else if (isCsvFile(name)) {
                csvUri = uri;
            }
        }

        // Valid only when all three files are present
        boolean valid = (annotatedUri != null && originalUri != null && csvUri != null);

        // Parse the CSV when the data is valid
        List<String[]> csvData = valid ? parseCsv(context, csvUri) : null;

        return new SharedScanData(uris, fileNames, valid,
                annotatedUri, originalUri, csvUri, csvData);
    }

    /**
     * Builds a demo {@link SharedScanData} from the bundled sample assets
     * ({@code assets/sample.jpg} + {@code assets/sample.csv}), so the UI can
     * be previewed exactly as if a real scan had been shared — used by the
     * "Show Sample" switch in {@code ScanResultView}. Cached after the first
     * successful load (see {@link #cachedSample}).
     *
     * <p>Only one sample image is bundled, so it is reused for both the
     * annotated and the original image slots.</p>
     *
     * @param context Android context (used to copy the asset into the cache
     *                dir and to read the CSV asset).
     * @return a valid-format {@link SharedScanData}, or {@code null} if the
     * sample assets could not be loaded.
     */
    public static SharedScanData loadSample(Context context) {
        if (cachedSample != null) return cachedSample;

        Uri sampleImageUri = copyAssetToCache(context, SAMPLE_IMAGE_ASSET);
        List<String[]> csvData = parseCsvAsset(context, SAMPLE_CSV_ASSET);
        if (sampleImageUri == null || csvData == null || csvData.isEmpty()) return null;

        List<Uri> uris = new ArrayList<>();
        uris.add(sampleImageUri);
        List<String> fileNames = new ArrayList<>();
        fileNames.add("Sample");

        cachedSample = new SharedScanData(uris, fileNames, true,
                sampleImageUri, sampleImageUri, null, csvData);
        return cachedSample;
    }

    /**
     * Copies an asset file into the app's cache directory (once) and returns
     * a {@code file://} {@link Uri} pointing at the copy — required because
     * {@link android.widget.ImageView#setImageURI} cannot read
     * {@code file:///android_asset/} paths directly.
     */
    private static Uri copyAssetToCache(Context context, String assetName) {
        File outFile = new File(context.getCacheDir(), assetName);
        try {
            AssetManager assets = context.getAssets();
            try (InputStream is = assets.open(assetName);
                 FileOutputStream os = new FileOutputStream(outFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
            }
            return Uri.fromFile(outFile);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Parses a CSV asset the same way {@link #parseCsv} parses a shared CSV
     * {@link Uri}, just reading from {@code assets/} instead of a
     * {@link android.content.ContentResolver} stream.
     */
    private static List<String[]> parseCsvAsset(Context context, String assetName) {
        try (InputStream is = context.getAssets().open(assetName)) {
            return parseCsvStream(is);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ── File-name resolution ───────────────────────────────────────────────

    /**
     * Queries the {@link android.content.ContentResolver} to get the
     * display name for a {@code content://} URI (i.e. the original file
     * name the sending app provided).  Falls back to the last path segment
     * for {@code file://} URIs or unknown schemes.
     */
    private static String resolveFileName(Context context, Uri uri) {
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver()
                    .query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (col >= 0) return cursor.getString(col);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // Fallback for file:// URIs
        return uri.getLastPathSegment();
    }

    // ── CSV parsing ────────────────────────────────────────────────────────

    /**
     * Opens the CSV file via {@link android.content.ContentResolver}
     * and parses it line by line.
     *
     * <p>The first row of the returned list is the header row.
     * Each element is a {@code String[]} of column values.</p>
     */
    private static List<String[]> parseCsv(Context context, Uri csvUri) {
        try (InputStream is = context.getContentResolver().openInputStream(csvUri)) {
            return parseCsvStream(is);
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Shared line-by-line CSV parsing logic used by both {@link #parseCsv}
     * (real shared CSV files) and {@link #parseCsvAsset} (the bundled sample
     * CSV asset).
     */
    private static List<String[]> parseCsvStream(InputStream is) throws Exception {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    rows.add(parseCsvLine(line));
                }
            }
        }
        return rows;
    }

    /**
     * Parses a single CSV line, correctly handling double-quoted fields
     * (including fields that contain commas inside quotes).
     */
    private static String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                // Toggle quoted-field mode; don't include the quote char
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                // Unquoted comma → field separator
                fields.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        fields.add(sb.toString()); // last field
        return fields.toArray(new String[0]);
    }
}

