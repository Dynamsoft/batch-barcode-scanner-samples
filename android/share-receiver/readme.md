# BBSDataReceiver — Receive Scan Results via Android Share

This sample demonstrates how your Android app can receive scan results
shared from the [Dynamsoft Batch Barcode Scanner](https://www.dynamsoft.com/batch-barcode-scanning/overview/) app.

When a user scans barcodes in the Batch Barcode Scanner app, they can tap **Share**
to send the results to another app. This sample is a minimal Android app that
appears in the share sheet, receives the shared files, validates them, parses the
CSV result, and renders everything on screen — so you can see exactly how to do
the same in your own program.

## How Sharing Works

Android sharing is just an intent hand-off:

1. The user taps **Share** in the Batch Barcode Scanner app after a scan.
2. Android shows the system share sheet, listing apps that can accept the shared files.
3. Your app registers an `intent-filter` so it appears in that list.
4. The user picks your app; Android starts (or resumes) your `Activity` with an
   `ACTION_SEND_MULTIPLE` intent whose `EXTRA_STREAM` extra holds the shared file URIs.
5. Your app reads those URIs, classifies them, and parses the CSV.

The Batch Barcode Scanner app always shares **exactly three files together**:

| File | Contents |
|------|----------|
| `{Type}_{timestamp}_{version}_AnnotatedImage.jpg` | The scan image with barcode location marks drawn on it |
| `{Type}_{timestamp}_{version}_OriginalImage.jpg` | The original, unmarked scan image |
| `{Type}_{timestamp}_{version}.csv` | The recognition results (one row per barcode) |

where `{Type}` is `Scan` or `History`. All three files share the same base-name
prefix, for example:

```
Scan_2026_06_03_10_30_45_123_001_AnnotatedImage.jpg
Scan_2026_06_03_10_30_45_123_001_OriginalImage.jpg
Scan_2026_06_03_10_30_45_123_001.csv
```

## Prerequisites

- Android Studio (the sample uses AGP 8.9.1 / Gradle 8.11.1)
- A device or emulator running Android 7.0 (API 24) or later (see `minSdk` in `app/build.gradle`)
- The Dynamsoft Batch Barcode Scanner Android app installed on the same device

## Project Structure

```
share-receiver/
└── app/src/main/
    ├── AndroidManifest.xml                        # Registers the share intent-filter
    ├── assets/
    │   ├── sample.jpg                             # Bundled sample image (for the "Show Sample" preview)
    │   └── sample.csv                             # Bundled sample CSV (for the "Show Sample" preview)
    ├── java/com/dynamsoft/bbsdatareceiver/
    │   ├── MainActivity.java                      # Glue: forwards the received intent to ShareReceiver, hands the result to the UI
    │   ├── model/SharedScanData.java              # Data model holding everything received from a share
    │   ├── share/ShareReceiver.java               # Core logic: parses the share intent, validates, reads the CSV
    │   └── widget/ScanResultView.java             # Reusable custom view that renders a SharedScanData
    └── res/
        └── layout/
            ├── activity_main.xml                  # Minimal layout that only hosts ScanResultView
            └── view_scan_result.xml               # ScanResultView's layout (image preview + CSV table)
```

The sample deliberately keeps **data logic** and **UI logic** separate:

- `ShareReceiver` knows nothing about Views — it only turns an incoming
  `Intent` into a plain `SharedScanData`.
- `ScanResultView` knows nothing about intents or ContentResolvers — it only
  renders the `SharedScanData` it is given.
- `MainActivity` is the only place that connects the two.

## How to Receive Shared Data in Your Own App

The essential integration is only **two steps**. Everything else in this sample
is UI.

### Step 1 — Declare the share intent-filter

Register your `Activity` to receive the Android share action. Add an
`intent-filter` to your `Activity` in `AndroidManifest.xml`:

```xml
<activity
    android:name=".MainActivity"
    android:exported="true">

    <!-- Receive multiple files shared together (the normal case for BBS) -->
    <intent-filter>
        <action android:name="android.intent.action.SEND_MULTIPLE" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="*/*" />
    </intent-filter>

    <!-- Also accept a single-file share, e.g. a CSV-only share -->
    <intent-filter>
        <action android:name="android.intent.action.SEND" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="*/*" />
    </intent-filter>
</activity>
```

Notes:

- The Batch Barcode Scanner app sends the three files together, so
  `ACTION_SEND_MULTIPLE` is the one that matters. `ACTION_SEND` is handled too,
  so the sample stays robust if a future version shares a single file.
- `mimeType="*/*"` is used deliberately: the share includes **images and a CSV**
  in one action, so no single MIME type covers all the files. A narrower filter
  would make the app fail to appear in the share sheet for the full three-file share.

### Step 2 — Parse the intent

Copy `ShareReceiver.java` (and its dependency `SharedScanData.java`) into your
project, then call it from your `Activity` whenever a share intent arrives:

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    SharedScanData data = ShareReceiver.receive(this, getIntent());
    // ...render `data` however your app needs to...
}

@Override
protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent); // so a later getIntent() also sees the latest share

    SharedScanData data = ShareReceiver.receive(this, intent);
    // ...render `data`...
}
```

That's it. `ShareReceiver.receive(...)` handles the rest:

- Confirms the intent is a share intent (`ACTION_SEND` or `ACTION_SEND_MULTIPLE`),
  returning `null` otherwise.
- Extracts the shared file URIs from `Intent.EXTRA_STREAM` (a list for
  `SEND_MULTIPLE`, a single URI for `SEND`).
- Resolves each URI to its display name via the `ContentResolver`.
- Classifies each file by name against the expected naming convention:
  `*_AnnotatedImage.*` → annotated image, `*_OriginalImage.*` → original image,
  `*.csv` → result data. Matching is intentionally loose (name *contains* the
  keyword) so minor naming variations across app versions still work.
- Sets `SharedScanData.isValidFormat` to `true` only when all three files are present.
- When valid, opens the CSV through the `ContentResolver` and parses it line by
  line (handling quoted fields that contain commas) into
  `SharedScanData.csvData`, where element `0` is the header row.

### What you get back

`SharedScanData` bundles everything the receiving app needs:

| Field | Meaning |
|-------|---------|
| `allUris` / `fileNames` | Every received file URI and its display name, in order |
| `isValidFormat` | `true` when all three expected files were received |
| `annotatedImageUri` | URI of the image with barcode marks drawn on it |
| `originalImageUri` | URI of the original, unmarked image |
| `csvUri` | URI of the CSV result file |
| `csvData` | Parsed rows; row 0 is the header. Each row is a `String[]` of columns |
| `getDisplayName()` | Human-readable share name (the shared base file-name prefix) |

### About the CSV result

The CSV rows share the same column layout as the result file exported by the
Batch Barcode Scanner app. A typical row looks like:

```
INDEX,BARCODE_TEXT,BARCODE_FORMAT,STATUS,LOCATION
"1","Dynamsoft0073","DATAMATRIX","Recognized","[(205,226),(236,228),(235,258),(203,255)]"
```

- `INDEX` — 1-based position of the barcode in the scan.
- `BARCODE_TEXT` — the decoded barcode text.
- `BARCODE_FORMAT` — the barcode symbology (e.g. `DATAMATRIX`, `QRCODE`, `CODE_128`).
- `STATUS` — recognition status, e.g. `Recognized` or `Duplicated`.
- `LOCATION` — the four corner coordinates of the barcode in the image,
  `[(x1,y1),(x2,y2),(x3,y3),(x4,y4)]`, in the same coordinate space as the
  annotated image.

## Running the Sample

1. Open `android/share-receiver/` in Android Studio.
2. Let Gradle sync (Android Gradle Plugin 8.9.1, Gradle 8.11.1 — the wrapper is included).
3. Run the `app` on a device/emulator. The app opens with an empty placeholder state.
4. To preview the UI without a real share, toggle the **Show Sample** switch — it
   renders the bundled `assets/sample.jpg` + `assets/sample.csv` exactly as a real
   share would.
5. To test a real share: scan in the Dynamsoft Batch Barcode Scanner app, tap
   **Share**, pick **BBSDataReceiver** from the share sheet.

## What the Sample's UI Shows

`ScanResultView` renders a received `SharedScanData` in three states:

| State | When | What is shown |
|-------|------|---------------|
| No share | App launched normally | "No files" header, placeholder image/table |
| Format error | Files received but they don't match the naming convention | The received file names + an error banner |
| Valid share | All three files matched | The share name, an image preview, and the CSV as a table |

For a valid share, the image preview shows the **annotated** image (marks
visible) by default; the **Hide Marks** switch toggles to the **original** image.
The CSV is displayed in a table with a sticky header that stays aligned while the
body scrolls vertically and horizontally.

## Customizing It for Your App

The parts you will most likely change:

- **UI**: `ScanResultView` is a self-contained custom view — replace it with your
  own rendering of `SharedScanData`, or drop the view into any of your own
  layouts/Activities/Fragments as-is.
- **Naming convention**: `ShareReceiver` classifies files with `isAnnotatedImage`,
  `isOriginalImage`, and `isCsvFile`. Adjust these if you ever need to match a
  different file naming scheme.
- **CSV format**: `parseCsv`/`parseCsvStream` return raw rows; map them onto your
  own data model as needed.

## Requirements

- Minimum SDK: 24 (Android 7.0)
- Target/compile SDK: 36
- Java 11
- No barcode SDK is required in this app — it only *receives* results, it never scans.

## Support

If you have any questions, feel free to [contact Dynamsoft support](https://www.dynamsoft.com/company/contact?utm_source=sampleReadme).
