package com.dynamsoft.bbsdatareceiver.widget;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;

import com.dynamsoft.bbsdatareceiver.R;
import com.dynamsoft.bbsdatareceiver.model.SharedScanData;
import com.dynamsoft.bbsdatareceiver.share.ShareReceiver;

import java.util.Collections;
import java.util.List;

/**
 * ============================================================
 * ScanResultView  —  Self-contained "scan result" UI widget
 * ============================================================
 *
 * <p>This is the ONE reusable custom view that owns every bit of
 * presentation logic for displaying a {@link SharedScanData}: the
 * file-name header with its "Hide Marks" switch, the image preview,
 * and the CSV table (with its sticky header + independently
 * scrollable body).</p>
 *
 * <p>Keeping all of this inside a single {@link LinearLayout} subclass
 * — rather than spread across the hosting Activity — means:</p>
 * <ul>
 *   <li>The Activity (see {@code MainActivity}) only has to call
 *       {@link #showNoShare()}, {@link #showFormatError(List)} or
 *       {@link #showValidShare(SharedScanData)}.</li>
 *   <li>The view can be dropped into any layout/Activity/Fragment
 *       that needs to show a received scan result — it doesn't depend
 *       on anything Activity-specific.</li>
 *   <li>Data logic (parsing the share intent, reading the CSV) stays
 *       entirely in {@code ShareReceiver} / {@link SharedScanData} and
 *       never leaks into this class — this class only ever renders
 *       the data it is given.</li>
 * </ul>
 */
public class ScanResultView extends LinearLayout {

    /**
     * Max cell width in dp — prevents very long fields (e.g. LOCATION) from being too wide.
     */
    private static final int CELL_MAX_WIDTH_DP = 220;
    /**
     * Min cell width in dp — keeps short columns (e.g. "Index") from being squeezed too tight.
     */
    private static final int CELL_MIN_WIDTH_DP = 60;
    /**
     * Placeholder column names shown in the sticky header while no CSV data
     * has been loaded yet (see {@link #showDefaultTableHeader()}). Built
     * with the exact same {@link #makeCellView} styling as the real header,
     * so the empty-state header always looks identical to the real one.
     */
    private static final String[] DEFAULT_TABLE_COLUMNS = {"INDEX", "BARCODE_TEXT", "BARCODE_FORMAT", "STATUS", "LOCATION"};
    // ── View references ────────────────────────────────────────────────────
    private TextView tvFileNames;
    private TextView tvFormatError;
    private SwitchCompat switchHideMarks;
    private TextView tvSwitchLabel;
    private ImageView ivDisplay;
    private View imageEmptyState;
    private View tableEmptyState;
    /**
     * The scrollable body of the real CSV table (header lives in headerHscroll/tableHeader, shared with the empty state).
     */
    private View bodyVscroll;
    private HorizontalScrollView headerHscroll;
    private HorizontalScrollView bodyHscroll;
    private TableLayout tableHeader;
    private TableLayout tableCsv;
    /**
     * The currently displayed share data; null when nothing valid has been received yet.
     */
    private SharedScanData currentData;
    /**
     * True once a real, validly-formatted share has been received. While
     * false, the shared switch stays in "Show Sample" mode (freely
     * togglable — ON shows the bundled sample, OFF shows the normal empty
     * state). Once true, the switch permanently becomes a "Hide Marks"
     * toggle for the real data — real data always takes priority over the
     * sample preview.
     */
    private boolean realDataReceived = false;

    // ── Constructors ───────────────────────────────────────────────────────
    /**
     * The header text to restore when "Show Sample" is switched back off — set by showNoShare()/showFormatError().
     */
    private String baseHeaderText = "No files";
    /**
     * Whether the format-error banner should be shown when "Show Sample" is off — set by showNoShare()/showFormatError().
     */
    private boolean baseFormatErrorVisible = false;
    /**
     * Kept as a field so {@link #setSwitchCheckedSilently} can temporarily
     * detach and reattach it (to change the switch's checked state
     * programmatically without re-firing the listener).
     */
    private CompoundButton.OnCheckedChangeListener switchListener;

    public ScanResultView(Context context) {
        super(context);
        init(context);
    }

    // ── View binding ───────────────────────────────────────────────────────

    public ScanResultView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    // ── Public API ─────────────────────────────────────────────────────────

    public ScanResultView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setOrientation(VERTICAL);
        int paddingPx = (int) (16 * context.getResources().getDisplayMetrics().density);
        setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

        LayoutInflater.from(context).inflate(R.layout.view_scan_result, this, true);
        bindViews();
    }

    private void bindViews() {
        tvFileNames = findViewById(R.id.tv_file_names);
        // Required for the marquee (ellipsize="marquee") to actually animate —
        // a single-line TextView only scrolls its text while "selected".
        tvFileNames.setSelected(true);
        tvFormatError = findViewById(R.id.tv_format_error);
        switchHideMarks = findViewById(R.id.switch_hide_marks);
        tvSwitchLabel = findViewById(R.id.tv_switch_label);
        ivDisplay = findViewById(R.id.iv_display);
        imageEmptyState = findViewById(R.id.image_empty_state);
        tableEmptyState = findViewById(R.id.table_empty_state);
        bodyVscroll = findViewById(R.id.body_vscroll);
        headerHscroll = findViewById(R.id.header_hscroll);
        bodyHscroll = findViewById(R.id.body_hscroll);
        tableHeader = findViewById(R.id.table_header);
        tableCsv = findViewById(R.id.table_csv);

        // ── Shared switch: "Show Sample" toggle ↔ "Hide Marks" toggle ──────
        // Before any real data is received, the switch stays labeled "Show
        // Sample" and can be freely turned on/off: ON renders the bundled
        // sample data, OFF reverts to the normal empty state (see
        // refreshEmptyModeDisplay()). Only once real, validly-formatted
        // data arrives (showValidShare) does the switch permanently become
        // a "Hide Marks" toggle: ON shows the Original Image (marks
        // removed), OFF shows the Annotated Image (marks visible).
        switchListener = (button, isChecked) -> {
            if (realDataReceived) {
                updateDisplayedImage();
            } else {
                refreshEmptyModeDisplay();
            }
        };
        switchHideMarks.setOnCheckedChangeListener(switchListener);

        // ── Sticky header ↔ scrollable body sync ───────────────────────────
        // The header row lives in its own HorizontalScrollView so it can stay
        // pinned to the top of the table while the body scrolls vertically.
        // It never receives touch input directly (swallow all touches below);
        // instead its horizontal scroll position always mirrors the body's,
        // so columns stay aligned while giving the illusion of a sticky header.
        headerHscroll.setOnTouchListener((v, event) -> true);
        bodyHscroll.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) ->
                headerHscroll.scrollTo(scrollX, 0));
    }

    /**
     * STATE A: no share intent has been received (app launched normally).
     * Header shows "No files"; image/table areas show empty-state placeholders.
     */
    public void showNoShare() {
        enterEmptyState("No files", false);
    }

    /**
     * STATE B: files were received but they don't match the expected naming
     * convention (see {@code ShareReceiver}'s class doc for the expected pattern).
     *
     * @param fileNames display names of the files that were received.
     */
    public void showFormatError(List<String> fileNames) {
        enterEmptyState(SharedScanData.joinFileNames(fileNames), true);
    }

    /**
     * STATE C: all three expected files were present and valid.
     * Shows the shared file-name prefix, the image preview (respecting the
     * current Hide Marks switch state) and the CSV table.
     *
     * <p>Real data always takes priority over the sample preview: once this
     * is called, the shared switch is locked into "Hide Marks" mode for the
     * rest of this view's lifetime.</p>
     *
     * @param data the parsed, valid scan result to display.
     */
    public void showValidShare(@NonNull SharedScanData data) {
        realDataReceived = true;
        tvSwitchLabel.setText(R.string.switch_label_hide_marks);
        // Reset to OFF (Annotated Image shown by default) now that the
        // switch's role has permanently changed to Hide Marks.
        setSwitchCheckedSilently(false);
        renderShare(data);
    }

    /**
     * Common setup shared by {@link #showNoShare()} and
     * {@link #showFormatError(List)}: both put the view into "no real data
     * yet" mode, just with a different header message.
     *
     * @param headerText         text to show in the header while "Show
     *                           Sample" is OFF.
     * @param formatErrorVisible whether the format-error banner should be
     *                           shown while "Show Sample" is OFF.
     */
    private void enterEmptyState(String headerText, boolean formatErrorVisible) {
        realDataReceived = false;
        baseHeaderText = headerText;
        baseFormatErrorVisible = formatErrorVisible;
        tvSwitchLabel.setText(R.string.switch_label_show_sample);
        refreshEmptyModeDisplay();
    }

    // ── Image preview ──────────────────────────────────────────────────────

    /**
     * Re-renders whichever state is currently appropriate while no real
     * data has been received: the bundled sample (if the switch is ON) or
     * the normal "no files"/format-error empty state (if OFF). Called
     * whenever the switch is toggled, and whenever showNoShare()/
     * showFormatError() run.
     */
    private void refreshEmptyModeDisplay() {
        if (switchHideMarks.isChecked()) {
            SharedScanData sample = ShareReceiver.loadSample(getContext());
            if (sample != null) {
                renderShare(sample);
                return;
            }
            // Sample failed to load — silently fall back to OFF/empty state.
            setSwitchCheckedSilently(false);
        }

        currentData = null;
        tvFileNames.setText(baseHeaderText);
        tvFormatError.setVisibility(baseFormatErrorVisible ? View.VISIBLE : View.GONE);
        showImageEmptyState();
        showTableEmptyState();
    }

    /**
     * Updates the switch's checked state without re-firing its listener.
     */
    private void setSwitchCheckedSilently(boolean checked) {
        if (switchHideMarks.isChecked() == checked) return;
        CompoundButton.OnCheckedChangeListener listener = switchListener;
        switchHideMarks.setOnCheckedChangeListener(null);
        switchHideMarks.setChecked(checked);
        switchHideMarks.setOnCheckedChangeListener(listener);
    }

    /**
     * Shared rendering logic for both real shares ({@link #showValidShare})
     * and the sample preview ({@link #refreshEmptyModeDisplay}).
     */
    private void renderShare(@NonNull SharedScanData data) {
        currentData = data;
        tvFileNames.setText(data.getDisplayName());
        tvFormatError.setVisibility(View.GONE);

        // Image preview — respects the current Hide Marks switch state.
        imageEmptyState.setVisibility(View.GONE);
        ivDisplay.setVisibility(View.VISIBLE);
        updateDisplayedImage();

        // CSV table
        buildCsvTable(data.csvData);
        tableEmptyState.setVisibility(View.GONE);
        bodyVscroll.setVisibility(View.VISIBLE);
    }

    // ── CSV table builder ──────────────────────────────────────────────────

    private void showImageEmptyState() {
        ivDisplay.setVisibility(View.GONE);
        imageEmptyState.setVisibility(View.VISIBLE);
    }

    private void showTableEmptyState() {
        bodyVscroll.setVisibility(View.GONE);
        tableEmptyState.setVisibility(View.VISIBLE);
        showDefaultTableHeader();
    }

    /**
     * Shows the Original Image when Hide Marks is ON, otherwise the
     * Annotated Image. No-op when there is no valid share data yet.
     */
    private void updateDisplayedImage() {
        if (currentData == null) return;
        Uri uri = switchHideMarks.isChecked()
                ? currentData.originalImageUri
                : currentData.annotatedImageUri;
        ivDisplay.setImageURI(uri);
    }

    /**
     * Builds the sticky header {@link TableLayout} (from row 0 of
     * {@code csvData}) and the scrollable body {@link TableLayout} (from the
     * remaining rows) as two separate tables sharing identical, explicitly
     * computed column widths — so their columns always stay aligned even
     * though they scroll horizontally in sync rather than as a single view.
     */
    private void buildCsvTable(List<String[]> csvData) {
        tableHeader.removeAllViews();
        tableCsv.removeAllViews();
        headerHscroll.scrollTo(0, 0);
        bodyHscroll.scrollTo(0, 0);
        if (csvData == null || csvData.isEmpty()) return;

        int[] columnWidths = computeColumnWidths(csvData);

        // Row 0 → sticky header table
        TableRow headerRow = new TableRow(getContext());
        String[] headerCols = csvData.get(0);
        for (int col = 0; col < headerCols.length; col++) {
            headerRow.addView(makeCellView(headerCols[col], true, columnWidths[col]));
        }
        tableHeader.addView(headerRow);

        // Remaining rows → scrollable body table
        for (int rowIndex = 1; rowIndex < csvData.size(); rowIndex++) {
            String[] cols = csvData.get(rowIndex);
            TableRow row = new TableRow(getContext());
            for (int col = 0; col < cols.length; col++) {
                int width = col < columnWidths.length ? columnWidths[col] : 0;
                row.addView(makeCellView(cols[col], false, width));
            }
            tableCsv.addView(row);
        }
    }

    /**
     * Builds the sticky header with placeholder column names, using the
     * exact same code path as {@link #buildCsvTable} (just with no data
     * rows) — this is what keeps the empty-state header visually identical
     * to the real one, instead of a separately hand-styled duplicate.
     */
    private void showDefaultTableHeader() {
        buildCsvTable(Collections.singletonList(DEFAULT_TABLE_COLUMNS));
    }

    /**
     * Computes a fixed pixel width for each column, wide enough to fit that
     * column's longest text (header or data), clamped between
     * {@link #CELL_MIN_WIDTH_DP} and {@link #CELL_MAX_WIDTH_DP}.
     *
     * <p>Explicit shared widths are required because the header and body are
     * two independent {@link TableLayout}s (to support the sticky-header
     * effect) — without this their columns could otherwise drift out of
     * alignment.</p>
     */
    private int[] computeColumnWidths(List<String[]> csvData) {
        float density = getResources().getDisplayMetrics().density;
        int paddingH = (int) (16 * density) * 2; // left + right cell padding
        int minWidthPx = (int) (CELL_MIN_WIDTH_DP * density);
        int maxWidthPx = (int) (CELL_MAX_WIDTH_DP * density);

        TextPaint headerPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        headerPaint.setTextSize(spToPx(13));
        headerPaint.setTypeface(Typeface.DEFAULT_BOLD);

        TextPaint dataPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        dataPaint.setTextSize(spToPx(12));
        dataPaint.setTypeface(Typeface.DEFAULT);

        int columnCount = csvData.get(0).length;
        int[] widths = new int[columnCount];
        for (int col = 0; col < columnCount; col++) {
            float maxTextWidth = 0f;
            for (int row = 0; row < csvData.size(); row++) {
                String[] cols = csvData.get(row);
                if (col >= cols.length) continue;
                TextPaint paint = (row == 0) ? headerPaint : dataPaint;
                float w = paint.measureText(cols[col]);
                if (w > maxTextWidth) maxTextWidth = w;
            }
            int width = (int) maxTextWidth + paddingH;
            widths[col] = Math.max(minWidthPx, Math.min(maxWidthPx, width));
        }
        return widths;
    }

    private float spToPx(float sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp,
                getResources().getDisplayMetrics());
    }

    /**
     * Creates a single table cell TextView with an explicit, pre-computed
     * column width (see {@link #computeColumnWidths}).
     *
     * <p>The header row reuses {@code R.drawable.bg_header_row} — the same
     * light-grey background + bottom divider used by the table's empty-state
     * placeholder — so the header looks identical whether or not data has
     * been loaded.</p>
     */
    private TextView makeCellView(String text, boolean isHeader, int widthPx) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        float density = getResources().getDisplayMetrics().density;
        int paddingH = (int) (16 * density);
        int paddingV = (int) ((isHeader ? 12 : 10) * density);
        tv.setPadding(paddingH, paddingV, paddingH, paddingV);
        tv.setTextSize(isHeader ? 13f : 12f);
        tv.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        if (isHeader) {
            tv.setBackgroundResource(R.drawable.bg_header_row);
        } else {
            tv.setBackgroundColor(Color.WHITE);   // data rows: plain white, matches table_row_background
        }

        // Single-line for header so all titles are always fully visible;
        // data cells wrap if they exceed the column width.
        if (isHeader) {
            tv.setTypeface(null, Typeface.BOLD);
            tv.setTextColor(Color.parseColor("#212121"));
            tv.setSingleLine(true);
        } else {
            tv.setTextColor(Color.parseColor("#212121"));
        }

        // Fixed width (rather than WRAP_CONTENT) so the header table and body
        // table — two independent TableLayouts — always stay column-aligned.
        // MATCH_PARENT height so every cell stretches to the tallest cell in
        // the row; without this, short cells leave a gap that shows the
        // TableLayout's divider background, making it look like a second cell.
        //
        // Left margin intentionally 0: no vertical divider line is shown
        // between columns (only the outer table border remains, via the
        // TableLayout's own padding). Top margin still exposes the
        // TableLayout's background as a horizontal row divider.
        TableRow.LayoutParams params = new TableRow.LayoutParams(
                widthPx,
                TableRow.LayoutParams.MATCH_PARENT);
        params.setMargins(0, 2, 0, 0);
        tv.setLayoutParams(params);
        return tv;
    }
}
