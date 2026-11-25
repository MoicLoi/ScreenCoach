package com.moiclathy.screencoach;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.moiclathy.screencoach.data.AnswerCache;
import com.moiclathy.screencoach.data.AppSettings;
import com.moiclathy.screencoach.data.CachedAnswer;
import com.moiclathy.screencoach.data.CoachRuntime;
import com.moiclathy.screencoach.data.ImportParser;
import com.moiclathy.screencoach.data.KnowledgeDb;
import com.moiclathy.screencoach.data.TranslationLanguage;
import com.moiclathy.screencoach.net.DeepSeekClient;
import com.moiclathy.screencoach.service.ImportFileProvider;
import com.moiclathy.screencoach.service.AccessibilityBridge;
import com.moiclathy.screencoach.service.CoachService;
import com.moiclathy.screencoach.service.CompactGeometry;
import com.moiclathy.screencoach.service.CompactPositionPreview;
import com.moiclathy.screencoach.service.NotificationHelper;
import com.moiclathy.screencoach.service.WatchdogScheduler;
import com.moiclathy.screencoach.util.BitmapUtils;
import com.moiclathy.screencoach.util.UiUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.util.Base64;
import android.util.Log;
import android.net.Uri;

public class MainActivity extends Activity {
    private static final int REQUEST_PROJECTION = 2001;
    private static final int REQUEST_NOTIFICATIONS = 2002;
    private static final int REQUEST_IMPORT_CAMERA = 3001;
    private static final int REQUEST_IMPORT_GALLERY = 3002;
    private static final int REQUEST_IMPORT_QUESTION_FILE = 3003;
    private static final int REQUEST_IMPORT_GLOSSARY_FILE = 3004;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService probeExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService importExecutor = Executors.newSingleThreadExecutor();

    private TextView tvStatusOverlay;
    private TextView tvStatusAccessibility;
    private TextView tvStatusProjection;
    private TextView tvServiceDetail;
    private TextView tvCacheStats;
    private TextView tvKeepalive;
    private TextView tvConfigHint;
    private View dotOverlay;
    private View dotAccessibility;
    private View dotProjection;
    private EditText etApiKey;
    private EditText etApiInterval;
    private CheckBox cbClearOnStart;
    private CheckBox cbUserSearch;
    private TextView tvCompactPosition;
    private Button btnPreviewPosition;
    private Switch swCompactOverStatusBar;
    private CompactPositionPreview positionPreview;
    private Switch swFlagSecure;
    private RadioGroup rgDisplayMode;
    private RadioButton rbLarge;
    private RadioButton rbCompact;
    private RadioButton rbNotification;
    private RadioGroup rgThinking;
    private RadioButton rbThinkOff;
    private RadioButton rbThinkLow;
    private RadioButton rbThinkHigh;
    private RadioGroup rgMode;
    private RadioButton rbQaMode;
    private RadioButton rbTranslationMode;
    private Button btnTranslationLanguage;
    private Switch swUseQuestionBank;
    private Switch swUseGlossary;
    private File pendingCameraFile;
    private AlertDialog progressDialog;
    private int translationLanguage = TranslationLanguage.ZH_EN_BIDIRECTIONAL;
    private Button btnStart;
    private Button btnStartA11y;
    private Button btnStop;
    private Button btnTestDeepSeek;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        applyFlagSecure();
        bindViews();
        loadSettings();
        bindActions();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void bindViews() {
        tvStatusOverlay = findViewById(R.id.tvStatusOverlay);
        tvStatusAccessibility = findViewById(R.id.tvStatusAccessibility);
        tvStatusProjection = findViewById(R.id.tvStatusProjection);
        tvServiceDetail = findViewById(R.id.tvServiceDetail);
        tvCacheStats = findViewById(R.id.tvCacheStats);
        tvKeepalive = findViewById(R.id.tvKeepalive);
        tvConfigHint = findViewById(R.id.tvConfigHint);
        dotOverlay = findViewById(R.id.dotOverlay);
        dotAccessibility = findViewById(R.id.dotAccessibility);
        dotProjection = findViewById(R.id.dotProjection);
        etApiKey = findViewById(R.id.etApiKey);
        etApiInterval = findViewById(R.id.etApiInterval);
        cbClearOnStart = findViewById(R.id.cbClearOnStart);
        cbUserSearch = findViewById(R.id.cbUserSearch);
        tvCompactPosition = findViewById(R.id.tvCompactPosition);
        btnPreviewPosition = findViewById(R.id.btnPreviewPosition);
        swCompactOverStatusBar = findViewById(R.id.swCompactOverStatusBar);
        swFlagSecure = findViewById(R.id.swFlagSecure);
        rgDisplayMode = findViewById(R.id.rgDisplayMode);
        rbLarge = findViewById(R.id.rbLarge);
        rbCompact = findViewById(R.id.rbCompact);
        rbNotification = findViewById(R.id.rbNotification);
        rgThinking = findViewById(R.id.rgThinking);
        rbThinkOff = findViewById(R.id.rbThinkOff);
        rbThinkLow = findViewById(R.id.rbThinkLow);
        rbThinkHigh = findViewById(R.id.rbThinkHigh);
        rgMode = findViewById(R.id.rgMode);
        rbQaMode = findViewById(R.id.rbQaMode);
        rbTranslationMode = findViewById(R.id.rbTranslationMode);
        btnTranslationLanguage = findViewById(R.id.btnTranslationLanguage);
        swUseQuestionBank = findViewById(R.id.swUseQuestionBank);
        swUseGlossary = findViewById(R.id.swUseGlossary);
        btnStart = findViewById(R.id.btnStart);
        btnStartA11y = findViewById(R.id.btnStartA11y);
        btnStop = findViewById(R.id.btnStop);
        btnTestDeepSeek = findViewById(R.id.btnTestDeepSeek);
    }

    private void loadSettings() {
        AppSettings settings = AppSettings.load(this);
        etApiKey.setText(settings.apiKey);
        etApiInterval.setText(String.valueOf(settings.apiIntervalSeconds));
        cbClearOnStart.setChecked(settings.clearCacheOnStart);
        cbUserSearch.setChecked(settings.compactUserSearchMode);
        if (settings.thinkingLevel == AppSettings.THINKING_LOW) {
            rbThinkLow.setChecked(true);
        } else if (settings.thinkingLevel == AppSettings.THINKING_HIGH) {
            rbThinkHigh.setChecked(true);
        } else {
            rbThinkOff.setChecked(true);
        }
        translationLanguage = settings.translationLanguage;
        if (settings.recognitionMode == AppSettings.MODE_TRANSLATION) {
            rbTranslationMode.setChecked(true);
        } else {
            rbQaMode.setChecked(true);
        }
        swUseQuestionBank.setChecked(settings.useQuestionBank);
        swUseGlossary.setChecked(settings.useGlossary);
        updateModeUi();
        updateCompactPositionLabel();
        swFlagSecure.setChecked(settings.flagSecureEnabled);
        swCompactOverStatusBar.setChecked(settings.compactOverStatusBar);
        if (settings.displayMode == AppSettings.DISPLAY_COMPACT) {
            rbCompact.setChecked(true);
        } else if (settings.displayMode == AppSettings.DISPLAY_NOTIFICATION) {
            rbNotification.setChecked(true);
        } else {
            rbLarge.setChecked(true);
        }
    }

    private void bindActions() {
        btnStart.setOnClickListener(v -> beginMediaSession());
        btnStartA11y.setOnClickListener(v -> beginAccessibilitySession());
        btnStop.setOnClickListener(v -> confirmStop());
        findViewById(R.id.btnOverlay).setOnClickListener(v -> openOverlaySettings());
        findViewById(R.id.btnAccessibility).setOnClickListener(v -> openAccessibilitySettings());
        findViewById(R.id.btnBattery).setOnClickListener(v -> openBatterySettings());
        findViewById(R.id.btnSave).setOnClickListener(v -> {
            if (saveSettings()) toast(R.string.toast_saved);
        });
        btnTestDeepSeek.setOnClickListener(v -> testDeepSeek());
        findViewById(R.id.btnClearCache).setOnClickListener(v -> confirmClearCache());
        cbUserSearch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppSettings.setCompactUserSearchMode(this, isChecked);
            if (CoachRuntime.running) toast(R.string.toast_display_next_start);
        });
        swCompactOverStatusBar.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppSettings.setCompactOverStatusBar(this, isChecked);
            updateCompactPositionLabel();
            if (positionPreview != null) {
                positionPreview.setAllowOverStatusBar(isChecked);
            }
            if (CoachRuntime.running) toast(R.string.toast_display_next_start);
        });
        swFlagSecure.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppSettings.setFlagSecureEnabled(this, isChecked);
            applyFlagSecure();
            if (positionPreview != null) positionPreview.setSecure(isChecked);
            refreshServiceSecurity();
        });
        rgDisplayMode.setOnCheckedChangeListener((group, checkedId) -> {
            AppSettings.setDisplayMode(this, selectedDisplayMode());
            if (CoachRuntime.running) toast(R.string.toast_display_next_start);
        });
        rgThinking.setOnCheckedChangeListener((group, checkedId) -> {
            AppSettings.setThinkingLevel(this, selectedThinkingLevel());
            if (CoachRuntime.running) toast(R.string.toast_display_next_start);
        });
        rgMode.setOnCheckedChangeListener((group, checkedId) -> {
            AppSettings.setRecognitionMode(this, selectedRecognitionMode());
            updateModeUi();
            if (CoachRuntime.running) toast(R.string.toast_display_next_start);
        });
        btnTranslationLanguage.setOnClickListener(v -> showTranslationLanguageDialog());
        swUseQuestionBank.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppSettings.setUseQuestionBank(this, isChecked);
            if (CoachRuntime.running) toast(R.string.toast_display_next_start);
        });
        swUseGlossary.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppSettings.setUseGlossary(this, isChecked);
            if (CoachRuntime.running) toast(R.string.toast_display_next_start);
        });
        findViewById(R.id.btnKnowledgeImport).setOnClickListener(v -> showKnowledgeImportDialog());
        findViewById(R.id.btnMoveLeft).setOnClickListener(v -> moveCompact(-1, 0));
        findViewById(R.id.btnMoveUp).setOnClickListener(v -> moveCompact(0, -1));
        findViewById(R.id.btnMoveDown).setOnClickListener(v -> moveCompact(0, 1));
        findViewById(R.id.btnMoveRight).setOnClickListener(v -> moveCompact(1, 0));
        findViewById(R.id.btnResetPosition).setOnClickListener(v -> resetCompactPosition());
        btnPreviewPosition.setOnClickListener(v -> togglePositionPreview());
    }

    private boolean saveSettings() {
        int interval;
        try {
            interval = Integer.parseInt(etApiInterval.getText().toString().trim());
        } catch (NumberFormatException e) {
            interval = -1;
        }
        if (interval < 1 || interval > 300) {
            toast(R.string.toast_invalid_interval);
            etApiInterval.requestFocus();
            return false;
        }
        AppSettings settings = new AppSettings();
        settings.apiKey = etApiKey.getText().toString().trim();
        settings.clearCacheOnStart = cbClearOnStart.isChecked();
        settings.displayMode = selectedDisplayMode();
        settings.flagSecureEnabled = swFlagSecure.isChecked();
        settings.apiIntervalSeconds = interval;
        settings.compactUserSearchMode = cbUserSearch.isChecked();
        settings.compactOverStatusBar = swCompactOverStatusBar.isChecked();
        settings.thinkingLevel = selectedThinkingLevel();
        settings.recognitionMode = selectedRecognitionMode();
        settings.translationLanguage = translationLanguage;
        settings.useQuestionBank = swUseQuestionBank.isChecked();
        settings.useGlossary = swUseGlossary.isChecked();
        if (!settings.save(this)) {
            toast(R.string.toast_key_save_failed);
            return false;
        }
        return true;
    }

    private boolean ensureApiKey() {
        if (etApiKey.getText().toString().trim().isEmpty()) {
            toast(R.string.toast_need_api_key);
            etApiKey.requestFocus();
            return false;
        }
        return true;
    }

    private void beginMediaSession() {
        if (!saveSettings() || !ensureApiKey()) return;
        if (!ensureOutputPermission()) return;
        hidePositionPreview();
        if (!NotificationHelper.isNotificationPermissionGranted(this)) {
            toast(R.string.toast_notification_hint);
        }
        MediaProjectionManager manager = (MediaProjectionManager)
                getSystemService(MEDIA_PROJECTION_SERVICE);
        if (manager == null) {
            toast(R.string.toast_projection_failed);
            return;
        }
        try {
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_PROJECTION);
        } catch (Throwable t) {
            toast(R.string.toast_projection_failed);
        }
    }

    private void beginAccessibilitySession() {
        if (!saveSettings() || !ensureApiKey()) return;
        if (!ensureOutputPermission()) return;
        hidePositionPreview();
        if (!AccessibilityBridge.isServiceEnabled(this)) {
            toast(R.string.toast_need_accessibility);
            openAccessibilitySettings();
            return;
        }
        startCoachService(new Intent(this, CoachService.class)
                .setAction(CoachService.ACTION_START_A11Y), false);
    }

    private void startCoachService(Intent service, boolean media) {
        if (cbClearOnStart.isChecked()) {
            try {
                int cleared = AnswerCache.get(this).clear();
                updateCacheStats();
                Log.i("MainActivity", "Cleared " + cleared + " cached answers before start");
                if (cleared > 0) {
                    toast(getString(R.string.toast_auto_cleared, cleared));
                }
            } catch (Throwable t) {
                toast("清空答案库失败：" + t.getClass().getSimpleName());
            }
        }
        try {
            startForegroundService(service);
            toast(R.string.toast_starting);
            mainHandler.postDelayed(this::updateStatus, 600);
        } catch (Throwable t) {
            toast((media ? getString(R.string.toast_projection_failed)
                    : "无法启动识别服务") + "：" + t.getClass().getSimpleName());
        }
    }

    private void confirmStop() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_stop_title)
                .setMessage(R.string.dialog_stop_message)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.dialog_ok, (d, which) -> {
                    stopService(new Intent(this, CoachService.class));
                    CoachRuntime.reset();
                    mainHandler.postDelayed(this::updateStatus, 300);
                })
                .create();
        dialog.show();
        if (dialog.getWindow() != null) {
            if (AppSettings.isFlagSecureEnabled(this)) {
                dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            } else {
                dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            }
        }
    }

    private void confirmClearCache() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.btn_clear)
                .setMessage(R.string.cache_title)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.dialog_ok, (d, which) -> {
                    try {
                        AnswerCache.get(this).clear();
                        toast(R.string.toast_cache_cleared);
                        updateCacheStats();
                    } catch (Throwable t) {
                        toast("清空失败：" + t.getClass().getSimpleName());
                    }
                })
                .create();
        dialog.show();
        if (dialog.getWindow() != null) {
            if (AppSettings.isFlagSecureEnabled(this)) {
                dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            } else {
                dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            }
        }
    }

    private void testDeepSeek() {
        if (!saveSettings() || !ensureApiKey()) return;
        final String key = etApiKey.getText().toString().trim();
        btnTestDeepSeek.setEnabled(false);
        tvConfigHint.setText("正在连接 DeepSeek…");
        probeExecutor.execute(() -> {
            try {
                List<String> models = new DeepSeekClient(key).listModels();
                boolean hasVision = models.contains(DeepSeekClient.VISION_MODEL);
                String names = models.isEmpty() ? "（无）" : String.join("、", models);
                mainHandler.post(() -> {
                    btnTestDeepSeek.setEnabled(true);
                    tvConfigHint.setText(hasVision
                            ? getString(R.string.test_deepseek_success)
                            : getString(R.string.test_deepseek_no_vision, names));
                });
            } catch (Throwable t) {
                String message = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                if (message.length() > 240) message = message.substring(0, 240) + "…";
                final String safe = message;
                mainHandler.post(() -> {
                    btnTestDeepSeek.setEnabled(true);
                    tvConfigHint.setText(getString(R.string.test_failed, safe));
                });
            }
        });
    }

    private void moveCompact(int dx, int dy) {
        int x = AppSettings.compactX(this, CompactGeometry.defaultX(this)) + dx;
        int y = AppSettings.compactY(this, CompactGeometry.defaultY(this)) + dy;
        x = CompactGeometry.clampX(this, x);
        y = CompactGeometry.clampY(this, y);
        AppSettings.saveCompactPosition(this, x, y);
        updateCompactPositionLabel();
        if (positionPreview != null && positionPreview.isShowing()) {
            positionPreview.updatePosition();
        }
    }

    private void resetCompactPosition() {
        AppSettings.saveCompactPosition(this, CompactGeometry.defaultX(this),
                CompactGeometry.defaultY(this));
        updateCompactPositionLabel();
        if (positionPreview != null && positionPreview.isShowing()) {
            positionPreview.updatePosition();
        }
    }

    private void updateCompactPositionLabel() {
        if (tvCompactPosition == null) return;
        int x = CompactGeometry.clampX(this,
                AppSettings.compactX(this, CompactGeometry.defaultX(this)));
        int y = CompactGeometry.clampY(this,
                AppSettings.compactY(this, CompactGeometry.defaultY(this)));
        tvCompactPosition.setText(getString(R.string.position_value, x, y));
    }

    private void togglePositionPreview() {
        if (positionPreview != null && positionPreview.isShowing()) {
            hidePositionPreview();
            return;
        }
        if (CoachRuntime.running) {
            toast(R.string.toast_preview_running);
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            toast(R.string.toast_need_overlay);
            openOverlaySettings();
            return;
        }
        try {
            positionPreview = new CompactPositionPreview(this);
            positionPreview.show();
            btnPreviewPosition.setText(R.string.btn_close_preview);
        } catch (Throwable t) {
            positionPreview = null;
            toast(getString(R.string.toast_preview_failed,
                    t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
        }
    }

    private void hidePositionPreview() {
        if (positionPreview != null) {
            positionPreview.hide();
            positionPreview = null;
        }
        if (btnPreviewPosition != null) {
            btnPreviewPosition.setText(R.string.btn_preview_position);
        }
    }

    private void secureDialog(AlertDialog dialog) {
        if (dialog.getWindow() == null) return;
        if (AppSettings.isFlagSecureEnabled(this)) {
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    private void showKnowledgeImportDialog() {
        View content = getLayoutInflater().inflate(R.layout.dialog_import, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.import_dialog_title)
                .setView(content)
                .setNegativeButton(R.string.close, null)
                .create();
        refreshImportCounts(content);
        content.findViewById(R.id.btnImportCamera).setOnClickListener(v -> {
            dialog.dismiss();
            captureQuestionPhoto();
        });
        content.findViewById(R.id.btnImportGallery).setOnClickListener(v -> {
            dialog.dismiss();
            pickQuestionImage();
        });
        content.findViewById(R.id.btnImportQuestionFile).setOnClickListener(v -> {
            dialog.dismiss();
            pickKnowledgeFile(true);
        });
        content.findViewById(R.id.btnImportGlossaryFile).setOnClickListener(v -> {
            dialog.dismiss();
            pickKnowledgeFile(false);
        });
        content.findViewById(R.id.btnClearQuestionBank).setOnClickListener(v ->
                confirmClearKnowledge(true, content));
        content.findViewById(R.id.btnClearGlossary).setOnClickListener(v ->
                confirmClearKnowledge(false, content));
        dialog.show();
        secureDialog(dialog);
    }

    private void refreshImportCounts(View content) {
        if (content == null) return;
        TextView counts = content.findViewById(R.id.tvImportCounts);
        if (counts == null) return;
        try {
            KnowledgeDb db = KnowledgeDb.get(this);
            counts.setText(getString(R.string.import_counts,
                    db.questionCount(), db.glossaryCount()));
        } catch (Throwable t) {
            counts.setText("题库读取失败");
        }
    }

    private void confirmClearKnowledge(boolean questions, View content) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage(questions ? R.string.import_confirm_clear_questions
                        : R.string.import_confirm_clear_glossary)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.dialog_ok, (d, which) -> {
                    try {
                        if (questions) KnowledgeDb.get(this).clearQuestions();
                        else KnowledgeDb.get(this).clearGlossary();
                        refreshImportCounts(content);
                    } catch (Throwable t) {
                        toast("清空失败");
                    }
                })
                .create();
        dialog.show();
        secureDialog(dialog);
    }

    private void captureQuestionPhoto() {
        try {
            File dir = new File(getCacheDir(), "imports");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("无法创建缓存目录");
            pendingCameraFile = new File(dir, "question_" + System.currentTimeMillis() + ".jpg");
            Uri output = new Uri.Builder().scheme("content")
                    .authority(ImportFileProvider.AUTHORITY)
                    .appendPath(pendingCameraFile.getName()).build();
            Intent capture = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            capture.putExtra(MediaStore.EXTRA_OUTPUT, output);
            capture.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(capture, REQUEST_IMPORT_CAMERA);
        } catch (android.content.ActivityNotFoundException e) {
            toast(R.string.import_camera_unavailable);
        } catch (Throwable t) {
            toast("相机启动失败：" + t.getClass().getSimpleName());
        }
    }

    private void pickQuestionImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, REQUEST_IMPORT_GALLERY);
        } catch (Throwable t) {
            toast("无法打开相册");
        }
    }

    private void pickKnowledgeFile(boolean questionBank) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, questionBank
                    ? REQUEST_IMPORT_QUESTION_FILE : REQUEST_IMPORT_GLOSSARY_FILE);
        } catch (Throwable t) {
            toast("无法打开文件选择器");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PROJECTION) {
            if (resultCode != RESULT_OK || data == null) {
                toast(R.string.toast_projection_denied);
                return;
            }
            Intent service = new Intent(this, CoachService.class)
                    .setAction(CoachService.ACTION_START_MEDIA)
                    .putExtra(CoachService.EXTRA_RESULT_CODE, resultCode)
                    .putExtra(CoachService.EXTRA_RESULT_DATA, data);
            startCoachService(service, true);
            return;
        }
        if (requestCode == REQUEST_IMPORT_CAMERA) {
            File file = pendingCameraFile;
            pendingCameraFile = null;
            if (resultCode == RESULT_OK && file != null && file.length() > 0) {
                importQuestionImage(file, null);
            } else {
                if (file != null) file.delete();
                toast("拍照已取消或没有图片");
            }
            return;
        }
        if (requestCode == REQUEST_IMPORT_GALLERY) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                importQuestionImage(null, data.getData());
            }
            return;
        }
        if (requestCode == REQUEST_IMPORT_QUESTION_FILE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                importKnowledgeFile(data.getData(), true);
            }
            return;
        }
        if (requestCode == REQUEST_IMPORT_GLOSSARY_FILE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                importKnowledgeFile(data.getData(), false);
            }
        }
    }

    private void importQuestionImage(File file, Uri uri) {
        String key = etApiKey.getText().toString().trim();
        if (key.isEmpty()) {
            toast(R.string.toast_need_glossary_api);
            if (file != null) file.delete();
            return;
        }
        showImportProgress(R.string.import_photo_reading);
        importExecutor.execute(() -> {
            Bitmap bitmap = null;
            try {
                byte[] bytes = file != null ? readFileBytes(file, 24 * 1024 * 1024)
                        : readUriBytes(uri, 24 * 1024 * 1024);
                bitmap = decodeSampled(bytes, 1600);
                if (bitmap == null) throw new IOException("图片解码失败");
                ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 82, jpeg);
                String base64 = Base64.encodeToString(jpeg.toByteArray(), Base64.NO_WRAP);
                List<KnowledgeDb.Question> questions = new DeepSeekClient(key,
                        AppSettings.THINKING_OFF)
                        .extractQuestionsFromImage(base64, "image/jpeg");
                if (questions.isEmpty()) throw new IOException("没有解析到题目");
                int count = KnowledgeDb.get(this).addQuestions(questions);
                runOnUiThread(() -> {
                    hideImportProgress();
                    toast(getString(R.string.import_success, count));
                });
            } catch (Throwable t) {
                String message = t.getMessage() == null
                        ? t.getClass().getSimpleName() : t.getMessage();
                runOnUiThread(() -> {
                    hideImportProgress();
                    toast(getString(R.string.import_failed, shorten(message, 160)));
                });
            } finally {
                if (bitmap != null) bitmap.recycle();
                if (file != null) file.delete();
            }
        });
    }

    private void importKnowledgeFile(Uri uri, boolean questionBank) {
        showImportProgress(R.string.import_reading);
        importExecutor.execute(() -> {
            try {
                String name = displayName(uri);
                String content = new String(readUriBytes(uri, 8 * 1024 * 1024),
                        StandardCharsets.UTF_8);
                int count;
                if (questionBank) {
                    List<KnowledgeDb.Question> questions = ImportParser.parseQuestions(content, name);
                    if (questions.isEmpty()) throw new IOException("没有解析到有效条目");
                    count = KnowledgeDb.get(this).addQuestions(questions);
                } else {
                    List<KnowledgeDb.Glossary> glossary = ImportParser.parseGlossary(content, name);
                    if (glossary.isEmpty()) throw new IOException("没有解析到有效条目");
                    count = KnowledgeDb.get(this).addGlossary(glossary);
                }
                runOnUiThread(() -> {
                    hideImportProgress();
                    toast(getString(R.string.import_success, count));
                });
            } catch (Throwable t) {
                String message = t.getMessage() == null
                        ? t.getClass().getSimpleName() : t.getMessage();
                runOnUiThread(() -> {
                    hideImportProgress();
                    toast(getString(R.string.import_failed, shorten(message, 160)));
                });
            }
        });
    }

    private void showImportProgress(int messageRes) {
        hideImportProgress();
        progressDialog = new AlertDialog.Builder(this)
                .setMessage(messageRes)
                .setCancelable(false)
                .create();
        progressDialog.show();
        secureDialog(progressDialog);
    }

    private void hideImportProgress() {
        if (progressDialog != null) {
            try { progressDialog.dismiss(); } catch (Throwable ignored) { }
            progressDialog = null;
        }
    }

    private String displayName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME},
                    null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.isEmpty()) return name;
            }
        } catch (Throwable ignored) { }
        finally { if (cursor != null) cursor.close(); }
        return "upload";
    }

    private byte[] readUriBytes(Uri uri, int max) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("无法读取文件");
            return readLimited(in, max);
        }
    }

    private byte[] readFileBytes(File file, int max) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return readLimited(in, max);
        }
    }

    private byte[] readLimited(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > max) throw new IOException("文件过大");
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private Bitmap decodeSampled(byte[] bytes, int maxDimension) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        int sample = 1;
        while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > maxDimension * 2) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        return bitmap == null ? null : BitmapUtils.maxDimension(bitmap, maxDimension);
    }


    private int selectedThinkingLevel() {
        if (rbThinkLow != null && rbThinkLow.isChecked()) return AppSettings.THINKING_LOW;
        if (rbThinkHigh != null && rbThinkHigh.isChecked()) return AppSettings.THINKING_HIGH;
        return AppSettings.THINKING_OFF;
    }

    private int selectedRecognitionMode() {
        return rbTranslationMode != null && rbTranslationMode.isChecked()
                ? AppSettings.MODE_TRANSLATION : AppSettings.MODE_QA;
    }

    private void updateModeUi() {
        boolean translation = selectedRecognitionMode() == AppSettings.MODE_TRANSLATION;
        btnTranslationLanguage.setVisibility(translation ? View.VISIBLE : View.GONE);
        btnTranslationLanguage.setText("翻译语言：" + TranslationLanguage.label(translationLanguage));
    }

    private void showTranslationLanguageDialog() {
        String[] labels = new String[TranslationLanguage.count()];
        for (int i = 0; i < labels.length; i++) labels[i] = TranslationLanguage.label(i);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.translation_language_title)
                .setSingleChoiceItems(labels, translationLanguage, (d, which) -> {
                    translationLanguage = which;
                    AppSettings.setTranslationLanguage(this, which);
                    updateModeUi();
                    if (CoachRuntime.running) toast(R.string.toast_display_next_start);
                    d.dismiss();
                })
                .setNegativeButton(R.string.close, null)
                .create();
        dialog.show();
        secureDialog(dialog);
    }

    private int selectedDisplayMode() {
        if (rbCompact != null && rbCompact.isChecked()) return AppSettings.DISPLAY_COMPACT;
        if (rbNotification != null && rbNotification.isChecked()) return AppSettings.DISPLAY_NOTIFICATION;
        return AppSettings.DISPLAY_LARGE;
    }

    private boolean ensureOutputPermission() {
        if (selectedDisplayMode() == AppSettings.DISPLAY_NOTIFICATION) {
            if (NotificationHelper.isNotificationPermissionGranted(this)
                    && NotificationHelper.notificationsEnabled(this)) return true;
            toast(R.string.toast_notification_needed);
            try {
                startActivity(NotificationHelper.notificationSettingsIntent(this));
            } catch (Throwable ignored) { }
            return false;
        }
        if (Settings.canDrawOverlays(this)) return true;
        toast(R.string.toast_need_overlay);
        openOverlaySettings();
        return false;
    }

    private void applyFlagSecure() {
        if (AppSettings.isFlagSecureEnabled(this)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    private void refreshServiceSecurity() {
        if (!CoachRuntime.running) return;
        try {
            startService(new Intent(this, CoachService.class)
                    .setAction(CoachService.ACTION_REFRESH_SECURITY));
        } catch (Throwable ignored) { }
    }

    private void openOverlaySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (Throwable t) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
        }
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Throwable t) {
            toast("无法打开无障碍设置");
        }
    }

    private void openBatterySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        } catch (Throwable t) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Throwable ignored) { }
        }
    }

    private void updateStatus() {
        boolean overlayAllowed = Settings.canDrawOverlays(this);
        tvStatusOverlay.setText(overlayAllowed ? R.string.status_ready : R.string.status_unknown);
        setDot(dotOverlay, overlayAllowed ? 0xFF22C55E : 0xFF64748B);

        boolean a11yEnabled = AccessibilityBridge.isServiceEnabled(this);
        boolean a11yConnected = AccessibilityBridge.isConnected();
        if (a11yEnabled && a11yConnected) {
            tvStatusAccessibility.setText("已连接");
            setDot(dotAccessibility, 0xFF22C55E);
        } else if (a11yEnabled) {
            tvStatusAccessibility.setText("已启用 / 未连接");
            setDot(dotAccessibility, 0xFFFBBF24);
        } else {
            tvStatusAccessibility.setText(R.string.status_disabled);
            setDot(dotAccessibility, 0xFF64748B);
        }

        boolean running = CoachRuntime.running;
        if (running) {
            boolean media = "media".equals(CoachRuntime.mode);
            tvStatusProjection.setText(media ? R.string.status_media : R.string.status_a11y);
            setDot(dotProjection, media ? 0xFF22C55E : 0xFFFBBF24);
            if (CoachRuntime.questionNo <= 0) {
                tvServiceDetail.setText(getString(R.string.status_detail_translation,
                        media ? "MediaProjection" : "无障碍截图",
                        CoachRuntime.intervalSeconds));
            } else {
                tvServiceDetail.setText(getString(R.string.status_detail_running,
                        media ? "MediaProjection" : "无障碍截图", CoachRuntime.questionNo,
                        CoachRuntime.intervalSeconds));
            }
            setDot(dotProjection, CoachRuntime.state == CoachRuntime.STATE_ERROR
                    ? 0xFFEF4444 : 0xFF22C55E);
        } else {
            tvStatusProjection.setText(R.string.status_stopped);
            setDot(dotProjection, 0xFF64748B);
            tvServiceDetail.setText(R.string.status_detail_idle);
        }
        btnStart.setVisibility(running ? View.GONE : View.VISIBLE);
        btnStartA11y.setVisibility(running ? View.GONE : View.VISIBLE);
        btnStop.setVisibility(running ? View.VISIBLE : View.GONE);

        long heartbeat = AccessibilityBridge.lastHeartbeat();
        if (a11yConnected && heartbeat > 0) {
            String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    .format(new Date(heartbeat));
            if (System.currentTimeMillis() - heartbeat > 90_000L) {
                tvKeepalive.setText("已连接，但心跳停留在 " + time + "，可能被系统回收。打开设置可恢复。");
            } else {
                tvKeepalive.setText(getString(R.string.keepalive_ok, time));
            }
        } else {
            tvKeepalive.setText(R.string.keepalive_off);
        }
        if (a11yEnabled) WatchdogScheduler.schedule(this);
        updateCacheStats();
    }

    private void updateCacheStats() {
        try {
            AnswerCache cache = AnswerCache.get(this);
            int count = cache.count();
            if (count == 0) {
                tvCacheStats.setText(R.string.cache_empty);
                return;
            }
            CachedAnswer latest = cache.latest();
            String stats = getString(R.string.cache_stats, count);
            if (latest != null) {
                stats += "\n" + (latest.questionNo > 0
                        ? getString(R.string.cache_latest, latest.questionNo,
                                shorten(latest.answer, 28))
                        : getString(R.string.cache_latest_translation,
                                shorten(latest.answer, 28)));
            }
            tvCacheStats.setText(stats);
        } catch (Throwable t) {
            tvCacheStats.setText("缓存读取失败：" + t.getClass().getSimpleName());
        }
    }

    private void setDot(View view, int color) {
        if (view != null) view.setBackground(UiUtils.circle(this, color));
    }

    private void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private static String shorten(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    @Override
    protected void onPause() {
        hidePositionPreview();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        hidePositionPreview();
        hideImportProgress();
        probeExecutor.shutdownNow();
        importExecutor.shutdownNow();
        super.onDestroy();
    }
}
