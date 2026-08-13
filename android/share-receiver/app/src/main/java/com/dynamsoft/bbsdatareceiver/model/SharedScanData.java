package com.dynamsoft.bbsdatareceiver.model;

import android.net.Uri;

import java.util.List;

/**
 * Data model representing the result of receiving a share intent.
 *
 * <p>When another app shares files with this app, the received data is
 * parsed and packed into this model. MainActivity only needs to read
 * this model to render the UI — it doesn't need to know about intent
 * parsing at all.</p>
 */
public class SharedScanData {

    // ── All received URIs and their display names ──────────────────────────

    /**
     * All file URIs that were shared (regardless of format validity).
     */
    public final List<Uri> allUris;

    /**
     * Display names of all shared files, in the same order as allUris.
     */
    public final List<String> fileNames;

    // ── Format validation flag ─────────────────────────────────────────────

    /**
     * True when the three expected files were found:
     * • one *_AnnotatedImage.jpg
     * • one *_OriginalImage.jpg
     * • one matching *.csv
     * and all three share the same base name prefix.
     */
    public final boolean isValidFormat;

    // ── Fields populated only when isValidFormat == true ──────────────────

    /**
     * URI of the annotated image (suffix = "AnnotatedImage").
     */
    public final Uri annotatedImageUri;

    /**
     * URI of the original image (suffix = "OriginalImage").
     */
    public final Uri originalImageUri;

    /**
     * URI of the CSV file.
     */
    public final Uri csvUri;

    /**
     * Parsed CSV rows.  The first element is the header row.
     * Each element is a String[] of column values for that row.
     */
    public final List<String[]> csvData;

    // ── Constructor ────────────────────────────────────────────────────────

    /**
     * Package-private constructor — built exclusively by ShareReceiver.
     */
    public SharedScanData(List<Uri> allUris,
                          List<String> fileNames,
                          boolean isValidFormat,
                          Uri annotatedImageUri,
                          Uri originalImageUri,
                          Uri csvUri,
                          List<String[]> csvData) {
        this.allUris = allUris;
        this.fileNames = fileNames;
        this.isValidFormat = isValidFormat;
        this.annotatedImageUri = annotatedImageUri;
        this.originalImageUri = originalImageUri;
        this.csvUri = csvUri;
        this.csvData = csvData;
    }

    // ── Display-name derivation ────────────────────────────────────────────
    // Kept here (rather than in the UI layer) because it is pure
    // data-interpretation logic tied to the sharing app's naming
    // convention (see ShareReceiver's class doc) — any UI that renders a
    // SharedScanData can reuse it without re-implementing the convention.

    /**
     * Joins a list of file names into a single comma-separated display
     * string. Used both by {@link #getDisplayName()} and directly by the UI
     * layer to render the file names of an invalid/unrecognized share (see
     * {@code ScanResultView#showFormatError}).
     */
    public static String joinFileNames(List<String> names) {
        if (names == null || names.isEmpty()) return "(no file names)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(names.get(i));
        }
        return sb.toString();
    }

    /**
     * Human-readable display name for this share: the shared base file-name
     * prefix (e.g. "Scan_2026_06_03...") derived by stripping the
     * "_AnnotatedImage"/"_OriginalImage" suffix from whichever file name
     * matches, or {@link #joinFileNames(List)} of all file names if none
     * match (e.g. the bundled sample data, or a share with unrelated names).
     */
    public String getDisplayName() {
        for (String name : fileNames) {
            int idx = name.indexOf("_AnnotatedImage");
            if (idx >= 0) return name.substring(0, idx);
        }
        for (String name : fileNames) {
            int idx = name.indexOf("_OriginalImage");
            if (idx >= 0) return name.substring(0, idx);
        }
        return joinFileNames(fileNames);
    }
}

