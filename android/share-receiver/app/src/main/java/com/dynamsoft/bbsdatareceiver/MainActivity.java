package com.dynamsoft.bbsdatareceiver;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.dynamsoft.bbsdatareceiver.model.SharedScanData;
import com.dynamsoft.bbsdatareceiver.share.ShareReceiver;
import com.dynamsoft.bbsdatareceiver.widget.ScanResultView;

/**
 * MainActivity — thin glue between data and UI.
 *
 * <p>This sample is meant to teach how to <b>receive</b> shared scan data
 * from another app, so the two concerns are kept strictly separate:</p>
 * <ul>
 *   <li><b>Data logic</b> — {@link ShareReceiver} parses the incoming share
 *       intent (file URIs, naming convention, CSV contents) into a plain
 *       {@link SharedScanData} model. It knows nothing about Views.</li>
 *   <li><b>UI logic</b> — {@link ScanResultView} is a single reusable custom
 *       view that renders a {@link SharedScanData} (or its absence). It
 *       knows nothing about intents or content resolvers.</li>
 * </ul>
 * <p>MainActivity's only job is to receive the Android lifecycle callbacks,
 * ask {@link ShareReceiver} to parse the intent, and hand the result to
 * {@link ScanResultView}.</p>
 */
public class MainActivity extends AppCompatActivity {

    private ScanResultView scanResultView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        EdgeToEdge.enable(this);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        scanResultView = findViewById(R.id.scan_result_view);

        // ── SHARE RECEPTION ────────────────────────────────────────────────
        // Delegate intent parsing to ShareReceiver.
        // The result is a SharedScanData (or null when launched normally).
        renderUi(ShareReceiver.receive(this, getIntent()));
        // ──────────────────────────────────────────────────────────────────
    }

    /**
     * Called when the Activity is already running and a new share intent arrives.
     * Android delivers the new intent here instead of restarting the Activity.
     */
    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        // ── SHARE RECEPTION ────────────────────────────────────────────────
        renderUi(ShareReceiver.receive(this, intent));
        // ──────────────────────────────────────────────────────────────────
    }

    /**
     * Forwards the parsed share data to {@link ScanResultView}, picking the
     * matching render state. All the actual UI work happens inside the view.
     */
    private void renderUi(SharedScanData data) {
        if (data == null) {
            scanResultView.showNoShare();
        } else if (data.isValidFormat) {
            scanResultView.showValidShare(data);
        } else {
            scanResultView.showFormatError(data.fileNames);
        }
    }
}
