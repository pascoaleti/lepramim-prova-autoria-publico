package com.lepramim.app;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener, BillingManager.Listener {
    private static final int REQUEST_IMAGE_CAPTURE = 1001;
    private static final int REQUEST_RECORD_AUDIO = 1002;
    private static final int REQUEST_IMAGE_PICK = 1003;

    private final Locale portugueseBrazil = new Locale("pt", "BR");
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextToSpeech textToSpeech;
    private SpeechRecognizer speechRecognizer;
    private TextRecognizer imageTextRecognizer;
    private BillingRepository billingRepository;
    private EntitlementManager entitlementManager;
    private final SmartReadingEngine smartReadingEngine = new SmartReadingEngine();
    private TextView instructionView;
    private TextView statusView;
    private TextView planView;
    private ImageView photoPreview;
    private String lastSpokenText = "";
    private String lastRecognizedImageText = "";
    private boolean announcedReaderEnabled = false;
    private boolean showingLegalScreen = false;
    private boolean showingSettingsScreen = false;
    private boolean showingPlusScreen = false;
    private boolean showingOnboardingScreen = false;
    private int caregiverOnboardingStep = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureSystemBars();
        entitlementManager = new EntitlementManager(this);
        setContentView(createSplashView());
        handler.postDelayed(this::showInitialScreen, 650);

        billingRepository = new BillingRepository(this, this);
        billingRepository.start();

        imageTextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        textToSpeech = new TextToSpeech(this, this);
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(createRecognitionListener());
        }

        handler.postDelayed(() -> handleIncomingImageIntent(getIntent()), 900);
    }

    private void showInitialScreen() {
        if (isFinishing()) {
            return;
        }
        if (LePraMimSettings.isOnboardingDone(this)) {
            setContentView(createPremiumContentView());
        } else {
            setContentView(createOnboardingChoiceView());
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            TtsVoiceController.apply(this, textToSpeech);
            speak(getString(R.string.voice_intro));
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingImageIntent(intent);
    }

    private View createPremiumContentView() {
        getWindow().setStatusBarColor(getColor(R.color.lepramim_soft));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        );

        showingLegalScreen = false;
        showingSettingsScreen = false;
        showingPlusScreen = false;
        showingOnboardingScreen = false;

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(getColor(R.color.lepramim_soft));
        scrollView.setClipToPadding(true);
        scrollView.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(12), dp(8), dp(12), dp(14));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        root.addView(createPremiumHeader(), blockLayout(0, 0, 0, dp(10)));
        root.addView(createDocumentAudioCard(), blockLayout(0, 0, 0, dp(10)));
        root.addView(createPrimaryPlayCard(), blockLayout(0, 0, 0, dp(10)));
        root.addView(createAssistCard(), blockLayout(0, 0, 0, dp(10)));
        root.addView(createBottomNavigationBar(), blockLayout(0, 0, 0, dp(10)));
        root.addView(createQuickActionsPanel(), blockLayout(0, 0, 0, 0));

        photoPreview = new ImageView(this);
        photoPreview.setAdjustViewBounds(true);
        photoPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photoPreview.setVisibility(View.GONE);
        photoPreview.setContentDescription("Imagem com texto");

        updateReaderState();
        return scrollView;
    }

    private View createSplashView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(createSplashBackground());

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(24), dp(24), dp(24), dp(24));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo_lepramim);
        logo.setContentDescription("Logo LePraMim");
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        content.addView(logo, new LinearLayout.LayoutParams(dp(132), dp(132)));

        TextView title = new TextView(this);
        title.setText("LePraMim");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 36);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setIncludeFontPadding(false);
        content.addView(title, withMargins(
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                0, dp(18), 0, 0
        ));

        TextView subtitle = new TextView(this);
        subtitle.setText("Leitura em voz alta");
        subtitle.setTextColor(Color.WHITE);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        subtitle.setTypeface(Typeface.DEFAULT_BOLD);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setAlpha(0.92f);
        content.addView(subtitle, withMargins(
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                0, dp(8), 0, 0
        ));

        root.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        return root;
    }

    private Drawable createSplashBackground() {
        return new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        getColor(R.color.lepramim_blue),
                        getColor(R.color.lepramim_blue_dark)
                }
        );
    }

    private View createPremiumHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18), dp(14), dp(18), dp(14));
        header.setMinimumHeight(dp(118));
        header.setBackground(createHeroBackground());
        header.setElevation(dp(5));

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo_lepramim);
        logo.setContentDescription("LePraMim");
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        topRow.addView(logo, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout titleColumn = new LinearLayout(this);
        titleColumn.setOrientation(LinearLayout.VERTICAL);
        titleColumn.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("LePraMim");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setIncludeFontPadding(false);
        titleColumn.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Leitura em voz alta");
        subtitle.setTextColor(Color.WHITE);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        subtitle.setTypeface(Typeface.DEFAULT_BOLD);
        subtitle.setAlpha(0.92f);
        titleColumn.addView(subtitle, withMargins(
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                0, dp(4), 0, 0
        ));

        topRow.addView(titleColumn, withMargins(
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                dp(12), 0, 0, 0
        ));

        FrameLayout menuButton = new FrameLayout(this);
        menuButton.setClickable(true);
        menuButton.setFocusable(true);
        menuButton.setContentDescription("Abrir configuracoes");
        menuButton.setBackground(createCircleBackground(0x22FFFFFF, 0x33FFFFFF, dp(1)));
        menuButton.setOnClickListener(view -> openSettingsScreen());

        ImageView menuIcon = new ImageView(this);
        setTintedIcon(menuIcon, R.drawable.ic_menu, Color.WHITE);
        menuIcon.setContentDescription("Menu");
        menuButton.addView(menuIcon, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        topRow.addView(menuButton, new LinearLayout.LayoutParams(dp(46), dp(46)));

        header.addView(topRow);

        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setGravity(Gravity.CENTER_VERTICAL);

        statusView = createHeaderChip("OUVIR desligado", getColor(R.color.lepramim_yellow), getColor(R.color.lepramim_ink));
        chipRow.addView(statusView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        planView = createHeaderChip(EntitlementStore.getPlanLabel(this), 0x22FFFFFF, Color.WHITE);
        chipRow.addView(planView, withMargins(
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                dp(8), 0, 0, 0
        ));

        header.addView(chipRow, withMargins(
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                0, dp(12), 0, 0
        ));
        return header;
    }

    private TextView createHeaderChip(String text, int backgroundColor, int textColor) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextColor(textColor);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(12), dp(8), dp(12), dp(8));
        chip.setSingleLine(false);
        chip.setBackground(createRoundedBackground(backgroundColor, Color.TRANSPARENT, 0));
        return chip;
    }

    private View createDocumentAudioCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setMinimumHeight(dp(122));
        card.setBackground(createRoundedBackground(Color.WHITE, getColor(R.color.lepramim_blue_soft), dp(2)));
        card.setElevation(dp(2));
        card.setContentDescription("Texto sendo convertido em audio");

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_document_audio);
        icon.setContentDescription("Documento com seta para alto falante");
        icon.setAdjustViewBounds(true);
        card.addView(icon, new LinearLayout.LayoutParams(dp(210), dp(78)));

        TextView label = new TextView(this);
        label.setText("Texto sendo convertido em \u00e1udio");
        label.setTextColor(getColor(R.color.lepramim_blue_dark));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setGravity(Gravity.CENTER);
        card.addView(label, withMargins(
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                0, dp(8), 0, 0
        ));
        return card;
    }

    private View createPrimaryPlayCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(16), dp(16), dp(16), dp(14));
        card.setMinimumHeight(dp(204));
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription("Tocar. Ativar ou usar leitura em voz alta.");
        card.setBackground(createRoundedBackground(Color.WHITE, 0x5537A4FF, dp(2)));
        card.setElevation(dp(3));
        card.setOnClickListener(view -> handlePrimaryPlay());

        ImageView play = new ImageView(this);
        play.setImageResource(R.drawable.ic_play_circle);
        play.setContentDescription("Botao tocar");
        card.addView(play, new LinearLayout.LayoutParams(dp(112), dp(112)));

        TextView title = new TextView(this);
        title.setText("Tocar");
        title.setTextColor(getColor(R.color.lepramim_blue_dark));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setIncludeFontPadding(false);
        card.addView(title, withMargins(
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                0, dp(10), 0, 0
        ));

        instructionView = new TextView(this);
        instructionView.setText("Toque para ativar");
        instructionView.setTextColor(getColor(R.color.lepramim_muted));
        instructionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        instructionView.setTypeface(Typeface.DEFAULT_BOLD);
        instructionView.setGravity(Gravity.CENTER);
        instructionView.setLineSpacing(dp(3), 1.0f);
        card.addView(instructionView, withMargins(
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                0, dp(8), 0, 0
        ));
        return card;
    }

    private View createAssistCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.setMinimumHeight(dp(92));
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription("Leitura assistida. Ouvir teste de voz.");
        card.setBackground(createRoundedBackground(getColor(R.color.lepramim_green_soft), Color.TRANSPARENT, 0));
        card.setElevation(dp(2));
        card.setOnClickListener(view -> testVoice());

        FrameLayout iconWrap = new FrameLayout(this);
        iconWrap.setBackground(createCircleBackground(Color.WHITE, getColor(R.color.lepramim_green), dp(2)));
        ImageView mic = new ImageView(this);
        mic.setImageResource(R.drawable.ic_microphone);
        mic.setContentDescription("Microfone");
        iconWrap.addView(mic, new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER));
        card.addView(iconWrap, new LinearLayout.LayoutParams(dp(62), dp(62)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("Leitura assistida");
        title.setTextColor(getColor(R.color.lepramim_green_dark));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        copy.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Ou\u00e7a os textos em voz alta com facilidade.");
        subtitle.setTextColor(getColor(R.color.lepramim_ink));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        subtitle.setLineSpacing(dp(3), 1.0f);
        copy.addView(subtitle, withMargins(
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                0, dp(4), 0, 0
        ));

        card.addView(copy, withMargins(
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                dp(16), 0, 0, 0
        ));
        return card;
    }

    private View createQuickActionsPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(14), dp(14), dp(14));
        panel.setBackground(createRoundedBackground(Color.WHITE, getColor(R.color.lepramim_blue_soft), dp(2)));
        panel.setElevation(dp(2));

        LinearLayout firstRow = new LinearLayout(this);
        firstRow.setOrientation(LinearLayout.HORIZONTAL);
        firstRow.setGravity(Gravity.CENTER);
        firstRow.addView(createCompactAction("Ativar", R.drawable.ic_volume, () -> openAccessibilitySettings()));
        firstRow.addView(createCompactAction("Papel", R.drawable.ic_camera, () -> openCamera()));
        firstRow.addView(createCompactAction("Print", R.drawable.ic_explain, () -> openImagePicker()));
        panel.addView(firstRow);

        LinearLayout secondRow = new LinearLayout(this);
        secondRow.setOrientation(LinearLayout.HORIZONTAL);
        secondRow.setGravity(Gravity.CENTER);
        secondRow.addView(createCompactAction("Plus", R.drawable.ic_payment, () -> openPlusScreen()));
        secondRow.addView(createCompactAction("Ajuda", R.drawable.ic_help, () -> openOnboardingAgain()));
        secondRow.addView(createCompactAction("Seguro", R.drawable.ic_terms, () -> openLegalScreen()));
        panel.addView(secondRow, withMargins(
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                0, dp(10), 0, 0
        ));
        return panel;
    }

    private View createCompactAction(String title, int iconRes, Runnable action) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(6), dp(8), dp(6), dp(8));
        item.setMinimumHeight(dp(82));
        item.setClickable(true);
        item.setFocusable(true);
        item.setContentDescription(title);
        item.setOnClickListener(view -> action.run());

        FrameLayout iconWrap = new FrameLayout(this);
        iconWrap.setBackground(createCircleBackground(getColor(R.color.lepramim_blue_soft), Color.TRANSPARENT, 0));
        ImageView icon = new ImageView(this);
        setTintedIcon(icon, iconRes, getColor(R.color.lepramim_blue));
        icon.setContentDescription(title);
        iconWrap.addView(icon, new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER));
        item.addView(iconWrap, new LinearLayout.LayoutParams(dp(52), dp(52)));

        TextView label = new TextView(this);
        label.setText(title);
        label.setTextColor(getColor(R.color.lepramim_ink));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setGravity(Gravity.CENTER);
        item.addView(label, withMargins(
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                0, dp(6), 0, 0
        ));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        item.setLayoutParams(params);
        return item;
    }

    private View createBottomNavigationBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(6), dp(8), dp(6), dp(8));
        bar.setMinimumHeight(dp(70));
        bar.setBackground(createRoundedBackground(Color.WHITE, 0x220057D9, dp(1)));
        bar.setElevation(dp(3));
        bar.addView(createBottomNavItem("In\u00edcio", R.drawable.ic_home, true, () -> speak("Tela inicial.")));
        bar.addView(createBottomNavItem("Biblioteca", R.drawable.ic_library, false, () -> openImagePicker()));
        bar.addView(createBottomNavItem("Hist\u00f3rico", R.drawable.ic_history_clock, false, () -> repeatLastSpeech()));
        bar.addView(createBottomNavItem("Configura\u00e7\u00f5es", R.drawable.ic_settings_gear, false, () -> openSettingsScreen()));
        return bar;
    }

    private View createBottomNavItem(String title, int iconRes, boolean active, Runnable action) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(2), dp(2), dp(2), dp(2));
        item.setClickable(true);
        item.setFocusable(true);
        item.setContentDescription(title);
        item.setOnClickListener(view -> action.run());

        ImageView icon = new ImageView(this);
        setTintedIcon(icon, iconRes, active ? getColor(R.color.lepramim_blue) : 0xFF7A8492);
        icon.setContentDescription(title);
        item.addView(icon, new LinearLayout.LayoutParams(dp(30), dp(30)));

        TextView label = new TextView(this);
        label.setText(title);
        label.setTextColor(active ? getColor(R.color.lepramim_blue) : 0xFF4B5563);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(false);
        item.addView(label, withMargins(
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                0, dp(4), 0, 0
        ));

        item.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return item;
    }

    private void handlePrimaryPlay() {
        if (isReaderEnabled()) {
            speak(getString(R.string.voice_screen_reader));
        } else {
            openAccessibilitySettings();
        }
    }

    private void setTintedIcon(ImageView view, int iconRes, int color) {
        Drawable icon = getDrawable(iconRes);
        if (icon != null) {
            icon = icon.mutate();
            icon.setTint(color);
            view.setImageDrawable(icon);
        } else {
            view.setImageResource(iconRes);
        }
    }

    private GradientDrawable createCircleBackground(int color, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        if (strokeWidth > 0) {
            drawable.setStroke(strokeWidth, strokeColor);
        }
        return drawable;
    }

    private View createContentView() {
        showingLegalScreen = false;
        showingSettingsScreen = false;
        showingPlusScreen = false;
        showingOnboardingScreen = false;

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(getColor(R.color.lepramim_soft));
        scrollView.setClipToPadding(true);
        scrollView.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(18));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        root.addView(createHero(), blockLayout(0, 0, 0, dp(12)));

        photoPreview = new ImageView(this);
        photoPreview.setAdjustViewBounds(true);
        photoPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        photoPreview.setVisibility(View.GONE);
        photoPreview.setContentDescription("Imagem com texto");
        root.addView(photoPreview, blockLayout(0, 0, 0, dp(10), dp(170)));

        View activateReaderButton = createActionTile(
                "Ativar no celular",
                "WhatsApp, banco e mensagens",
                R.drawable.ic_volume,
                getColor(R.color.lepramim_orange),
                getColor(R.color.lepramim_ink),
                true
        );
        activateReaderButton.setOnClickListener(view -> openAccessibilitySettings());
        root.addView(activateReaderButton, buttonLayout());

        View cameraButton = createActionTile(
                "Ler papel",
                "Boleto, receita ou carta",
                R.drawable.ic_camera,
                getColor(R.color.lepramim_blue),
                Color.WHITE,
                false
        );
        cameraButton.setOnClickListener(view -> openCamera());
        root.addView(cameraButton, buttonLayout());

        View galleryButton = createActionTile(
                "Ler print",
                "Foto recebida no celular",
                R.drawable.ic_explain,
                getColor(R.color.lepramim_teal),
                Color.WHITE,
                false
        );
        galleryButton.setOnClickListener(view -> openImagePicker());
        root.addView(galleryButton, buttonLayout());

        View repeatButton = createActionTile(
                "Repetir leitura",
                "Repete a fala",
                R.drawable.ic_repeat,
                Color.WHITE,
                getColor(R.color.lepramim_blue_dark),
                false
        );
        repeatButton.setBackground(createRoundedBackground(Color.WHITE, getColor(R.color.lepramim_blue), dp(2)));
        repeatButton.setOnClickListener(view -> repeatLastSpeech());
        root.addView(repeatButton, buttonLayout());

        View voiceButton = createActionTile(
                "Falar comando",
                "Fale com o app",
                R.drawable.ic_mic,
                Color.WHITE,
                getColor(R.color.lepramim_blue_dark),
                false
        );
        voiceButton.setBackground(createRoundedBackground(Color.WHITE, getColor(R.color.lepramim_blue), dp(2)));
        voiceButton.setOnClickListener(view -> requestVoiceCommand());
        root.addView(voiceButton, buttonLayout());

        View settingsButton = createActionTile(
                "Configurar ajuda",
                "Voz, segurança e botão",
                R.drawable.ic_help,
                Color.WHITE,
                getColor(R.color.lepramim_blue_dark),
                false
        );
        settingsButton.setBackground(createRoundedBackground(Color.WHITE, getColor(R.color.lepramim_blue), dp(2)));
        settingsButton.setOnClickListener(view -> openSettingsScreen());
        root.addView(settingsButton, buttonLayout());

        View plusButton = createActionTile(
                "LePraMim Plus",
                "Opcional, uso sem limite",
                R.drawable.ic_payment,
                getColor(R.color.lepramim_orange),
                getColor(R.color.lepramim_ink),
                false
        );
        plusButton.setOnClickListener(view -> openPlusScreen());
        root.addView(plusButton, buttonLayout());

        View helpButton = createActionTile(
                "Como usar",
                "Guia para família",
                R.drawable.ic_help,
                Color.WHITE,
                getColor(R.color.lepramim_blue_dark),
                false
        );
        helpButton.setBackground(createRoundedBackground(Color.WHITE, getColor(R.color.lepramim_blue), dp(2)));
        helpButton.setOnClickListener(view -> openOnboardingAgain());
        root.addView(helpButton, buttonLayout());

        View legalButton = createActionTile(
                "Segurança",
                "Dados protegidos",
                R.drawable.ic_terms,
                Color.WHITE,
                getColor(R.color.lepramim_blue_dark),
                false
        );
        legalButton.setBackground(createRoundedBackground(Color.WHITE, getColor(R.color.lepramim_blue), dp(2)));
        legalButton.setOnClickListener(view -> openLegalScreen());
        root.addView(legalButton, buttonLayout());

        updateReaderState();
        return scrollView;
    }

    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(getColor(R.color.lepramim_soft));
        window.setNavigationBarColor(Color.WHITE);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        );
    }

    private View createHero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        hero.setBackground(createHeroBackground());
        hero.setElevation(dp(6));

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout markBackground = new FrameLayout(this);
        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.ic_lepramim_foreground);
        mark.setContentDescription("LePraMim");
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        markBackground.setBackground(createRoundedBackground(0x1FFFFFFF, Color.TRANSPARENT, 0));
        markBackground.addView(mark, new FrameLayout.LayoutParams(dp(94), dp(94), Gravity.CENTER));
        brandRow.addView(markBackground, new LinearLayout.LayoutParams(dp(96), dp(96)));

        LinearLayout titleColumn = new LinearLayout(this);
        titleColumn.setOrientation(LinearLayout.VERTICAL);
        titleColumn.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("LePraMim");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 31);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setIncludeFontPadding(false);
        titleColumn.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("O celular fala com você");
        subtitle.setTextColor(Color.WHITE);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        subtitle.setTypeface(Typeface.DEFAULT_BOLD);
        subtitle.setAlpha(0.9f);
        subtitle.setIncludeFontPadding(false);
        titleColumn.addView(subtitle, withMargins(
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                0, dp(6), 0, 0
        ));

        brandRow.addView(titleColumn, withMargins(
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                dp(14), 0, 0, 0
        ));
        hero.addView(brandRow);

        TextView promise = new TextView(this);
        promise.setText("Ouça antes. Pague só se servir.");
        promise.setTextColor(Color.WHITE);
        promise.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        promise.setTypeface(Typeface.DEFAULT_BOLD);
        promise.setGravity(Gravity.CENTER);
        promise.setPadding(dp(12), dp(8), dp(12), dp(8));
        promise.setBackground(createRoundedBackground(0x26FFFFFF, 0x22FFFFFF, dp(1)));
        hero.addView(promise, withMargins(
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                0, dp(18), 0, dp(10)
        ));

        LinearLayout infoRow = new LinearLayout(this);
        infoRow.setOrientation(LinearLayout.HORIZONTAL);
        infoRow.setGravity(Gravity.CENTER_VERTICAL);

        statusView = new TextView(this);
        statusView.setText("OUVIR desligado");
        statusView.setTextColor(getColor(R.color.lepramim_ink));
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        statusView.setTypeface(Typeface.DEFAULT_BOLD);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(dp(13), dp(9), dp(13), dp(9));
        statusView.setBackground(createRoundedBackground(getColor(R.color.lepramim_yellow), Color.TRANSPARENT, 0));
        infoRow.addView(statusView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        planView = new TextView(this);
        planView.setText(EntitlementStore.getPlanLabel(this));
        planView.setTextColor(Color.WHITE);
        planView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        planView.setTypeface(Typeface.DEFAULT_BOLD);
        planView.setGravity(Gravity.CENTER);
        planView.setPadding(dp(11), dp(8), dp(11), dp(8));
        planView.setMaxLines(2);
        planView.setBackground(createRoundedBackground(0x24FFFFFF, Color.TRANSPARENT, 0));
        infoRow.addView(planView, withMargins(
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                dp(8), 0, 0, 0
        ));

        hero.addView(infoRow, withMargins(
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                0, 0, 0, dp(18)
        ));

        instructionView = new TextView(this);
        instructionView.setText("Arraste at\u00e9 o texto.");
        instructionView.setTextColor(Color.WHITE);
        instructionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 27);
        instructionView.setTypeface(Typeface.DEFAULT_BOLD);
        instructionView.setLineSpacing(dp(3), 1.0f);
        instructionView.setIncludeFontPadding(false);
        instructionView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        hero.addView(instructionView);

        return hero;
    }

    private View createOnboardingChoiceView() {
        showingOnboardingScreen = true;
        ScrollView scrollView = createBaseScrollView();
        LinearLayout root = createRoot(scrollView);

        root.addView(createTitleHero(
                "LePraMim",
                "O celular lê textos em voz alta para quem precisa ouvir."
        ), blockLayout(0, 0, 0, dp(14)));

        View caregiverButton = createActionTile(
                "Sou cuidador ou familiar",
                "Configurar com calma",
                R.drawable.ic_help,
                getColor(R.color.lepramim_blue),
                Color.WHITE,
                true
        );
        caregiverButton.setOnClickListener(view -> {
            caregiverOnboardingStep = 0;
            setContentView(createCaregiverOnboardingView());
        });
        root.addView(caregiverButton, buttonLayout());

        View userButton = createActionTile(
                "Vou usar para ouvir",
                "Tela simples",
                R.drawable.ic_volume,
                getColor(R.color.lepramim_orange),
                getColor(R.color.lepramim_ink),
                true
        );
        userButton.setOnClickListener(view -> setContentView(createUserOnboardingView()));
        root.addView(userButton, buttonLayout());

        View skipButton = createActionTile(
                "Pular por enquanto",
                "Ir para o app",
                R.drawable.ic_back,
                Color.WHITE,
                getColor(R.color.lepramim_blue_dark),
                false
        );
        skipButton.setBackground(createRoundedBackground(Color.WHITE, getColor(R.color.lepramim_blue), dp(2)));
        skipButton.setOnClickListener(view -> finishOnboarding());
        root.addView(skipButton, buttonLayout());

        return scrollView;
    }

    private View createCaregiverOnboardingView() {
        showingOnboardingScreen = true;
        ScrollView scrollView = createBaseScrollView();
        LinearLayout root = createRoot(scrollView);

        String[] titles = {
                "Configurar para alguém",
                "Permissão de leitura",
                "Teste de leitura",
                "Escolha a voz",
                "Plano Plus",
                "Pronto para entregar"
        };
        String[] bodies = {
                "O app lê mensagens, telas, prints e papéis em voz alta.",
                "A acessibilidade permite ler textos visíveis em outros apps quando a pessoa toca em OUVIR.",
                "Vamos falar uma frase de teste para confirmar que o som está bom.",
                "Escolha uma velocidade confortável para idosos ou pessoas com dificuldade.",
                "A pessoa testa o básico grátis. O Plus libera uso sem limite.",
                "Depois de ativar o botão amarelo, entregue o celular pronto para uso."
        };

        int step = Math.max(0, Math.min(caregiverOnboardingStep, titles.length - 1));
        root.addView(createTitleHero(titles[step], bodies[step]), blockLayout(0, 0, 0, dp(14)));

        if (step == 1) {
            View permissionButton = createActionTile(
                    "Abrir acessibilidade",
                    "Ativar LePraMim",
                    R.drawable.ic_volume,
                    getColor(R.color.lepramim_orange),
                    getColor(R.color.lepramim_ink),
                    true
            );
            permissionButton.setOnClickListener(view -> openAccessibilitySettings());
            root.addView(permissionButton, buttonLayout());
        } else if (step == 2) {
            View testButton = createActionTile(
                    "Testar leitura",
                    "Ouvir frase",
                    R.drawable.ic_volume,
                    getColor(R.color.lepramim_orange),
                    getColor(R.color.lepramim_ink),
                    true
            );
            testButton.setOnClickListener(view -> testVoice());
            root.addView(testButton, buttonLayout());
        } else if (step == 3) {
            root.addView(createSpeedButton("Muito devagar", "Para ouvir com calma", "muito_devagar"), buttonLayout());
            root.addView(createSpeedButton("Devagar", "Bom para idosos", "devagar"), buttonLayout());
            root.addView(createSpeedButton("Normal", "Equilibrado", "normal"), buttonLayout());
        } else if (step == 4) {
            View plusButton = createActionTile(
                    "Ver Plus",
                    "Mensal ou anual",
                    R.drawable.ic_payment,
                    getColor(R.color.lepramim_orange),
                    getColor(R.color.lepramim_ink),
                    true
            );
            plusButton.setOnClickListener(view -> openPlusScreen());
            root.addView(plusButton, buttonLayout());
        }

        View nextButton = createActionTile(
                step == titles.length - 1 ? "Entregar celular pronto" : "Continuar",
                step == titles.length - 1 ? "Finalizar guia" : "Próximo passo",
                R.drawable.ic_back,
                getColor(R.color.lepramim_blue),
                Color.WHITE,
                true
        );
        nextButton.setOnClickListener(view -> {
            if (caregiverOnboardingStep >= titles.length - 1) {
                finishOnboarding();
            } else {
                caregiverOnboardingStep++;
                setContentView(createCaregiverOnboardingView());
            }
        });
        root.addView(nextButton, buttonLayout());

        View skipButton = createActionTile(
                "Pular guia",
                "Ir para o app",
                R.drawable.ic_back,
                Color.WHITE,
                getColor(R.color.lepramim_blue_dark),
                false
        );
        skipButton.setBackground(createRoundedBackground(Color.WHITE, getColor(R.color.lepramim_blue), dp(2)));
        skipButton.setOnClickListener(view -> finishOnboarding());
        root.addView(skipButton, buttonLayout());

        return scrollView;
    }

    private View createUserOnboardingView() {
        showingOnboardingScreen = true;
        ScrollView scrollView = createBaseScrollView();
        LinearLayout root = createRoot(scrollView);

        root.addView(createTitleHero(
                "Arraste at\u00e9 o texto",
                "Solte o bot\u00e3o amarelo em cima das palavras para ouvir."
        ), blockLayout(0, 0, 0, dp(14)));

        View testButton = createActionTile(
                "Testar agora",
                "Ouvir exemplo",
                R.drawable.ic_volume,
                getColor(R.color.lepramim_orange),
                getColor(R.color.lepramim_ink),
                true
        );
        testButton.setOnClickListener(view -> testVoice());
        root.addView(testButton, buttonLayout());

        View helpButton = createActionTile(
                "Pedir ajuda para configurar",
                "Modo familiar",
                R.drawable.ic_help,
                getColor(R.color.lepramim_blue),
                Color.WHITE,
                true
        );
        helpButton.setOnClickListener(view -> {
            caregiverOnboardingStep = 0;
            setContentView(createCaregiverOnboardingView());
        });
        root.addView(helpButton, buttonLayout());

        View finishButton = createActionTile(
                "Ir para o app",
                "Começar",
                R.drawable.ic_back,
                Color.WHITE,
                getColor(R.color.lepramim_blue_dark),
                false
        );
        finishButton.setBackground(createRoundedBackground(Color.WHITE, getColor(R.color.lepramim_blue), dp(2)));
        finishButton.setOnClickListener(view -> finishOnboarding());
        root.addView(finishButton, buttonLayout());

        return scrollView;
    }

    private View createSettingsView() {
        showingSettingsScreen = true;
        ScrollView scrollView = createBaseScrollView();
        LinearLayout root = createRoot(scrollView);

        root.addView(createTitleHero(
                "Configurar ajuda",
                "Ajustes para deixar o celular fácil de ouvir."
        ), blockLayout(0, 0, 0, dp(12)));

        root.addView(createBackToMainButton(), buttonLayout());

        root.addView(createSectionTitle("Leitura"), blockLayout(0, dp(8), 0, dp(4)));
        root.addView(createSpeedButton("Muito devagar", "Voz bem calma", "muito_devagar"), buttonLayout());
        root.addView(createSpeedButton("Devagar", "Mais fácil de acompanhar", "devagar"), buttonLayout());
        root.addView(createSpeedButton("Normal", "Velocidade equilibrada", "normal"), buttonLayout());
        root.addView(createSpeedButton("Rápido", "Para quem já acompanha bem", "rapido"), buttonLayout());
        root.addView(createSettingButton("Tom mais baixo", "Voz mais grave", () -> {
            LePraMimSettings.setSpeechPitch(this, 0.88f);
            testVoice();
        }), buttonLayout());
        root.addView(createSettingButton("Tom normal", "Voz equilibrada", () -> {
            LePraMimSettings.setSpeechPitch(this, 1.0f);
            testVoice();
        }), buttonLayout());
        root.addView(createSettingButton("Tom mais alto", "Voz mais clara", () -> {
            LePraMimSettings.setSpeechPitch(this, 1.12f);
            testVoice();
        }), buttonLayout());
        root.addView(createSettingButton("Testar voz", "Ouvir exemplo", this::testVoice), buttonLayout());
        root.addView(createSettingButton("Repetir última leitura", "Ouvir de novo", this::repeatLastSpeech), buttonLayout());
        root.addView(createSettingButton("Ler tudo", "Fala o texto completo", () -> setReadingModeAndSpeak(ReadingMode.READ_ALL)), buttonLayout());
        root.addView(createSettingButton("Falar só o importante", "Valor, data, horário e aviso", () -> setReadingModeAndSpeak(ReadingMode.IMPORTANT_ONLY)), buttonLayout());
        root.addView(createSettingButton("Ler e explicar", "Palavras simples", () -> setReadingModeAndSpeak(ReadingMode.EXPLAIN)), buttonLayout());
        root.addView(createSettingButton("Detectar risco", "Golpe, senha ou link estranho", () -> setReadingModeAndSpeak(ReadingMode.RISK_ALERT)), buttonLayout());

        root.addView(createSectionTitle("Botão flutuante"), blockLayout(0, dp(8), 0, dp(4)));
        root.addView(createSwitchRow(
                "Mostrar botão amarelo",
                "Botão OUVIR por cima dos apps",
                LePraMimSettings.isOverlayEnabled(this),
                (button, checked) -> {
                    LePraMimSettings.setOverlayEnabled(this, checked);
                    speak(checked ? "Botão amarelo ligado." : "Botão amarelo desligado.");
                }
        ), buttonLayout());
        root.addView(createSettingButton("Tamanho pequeno", "Menos espaço na tela", () -> setOverlaySizeAndSpeak(LePraMimSettings.OVERLAY_SMALL)), buttonLayout());
        root.addView(createSettingButton("Tamanho médio", "Recomendado", () -> setOverlaySizeAndSpeak(LePraMimSettings.OVERLAY_MEDIUM)), buttonLayout());
        root.addView(createSettingButton("Tamanho grande", "Mais fácil de tocar", () -> setOverlaySizeAndSpeak(LePraMimSettings.OVERLAY_LARGE)), buttonLayout());
        root.addView(createSwitchRow(
                "Modo discreto",
                "Botão mais transparente",
                LePraMimSettings.isOverlayDiscreet(this),
                (button, checked) -> {
                    LePraMimSettings.setOverlayDiscreet(this, checked);
                    speak(checked ? "Modo discreto ligado." : "Modo discreto desligado.");
                }
        ), buttonLayout());
        root.addView(createSettingButton("Posição inicial", "Voltar para canto inferior", () -> {
            LePraMimSettings.setOverlayPosition(this, dp(14), dp(86));
            speak("Posição do botão reiniciada.");
        }), buttonLayout());

        root.addView(createSectionTitle("Segurança"), blockLayout(0, dp(8), 0, dp(4)));
        root.addView(createSwitchRow(
                "Modo seguro",
                "Evita ler senha, código e banco em voz alta",
                LePraMimSettings.isSafeModeEnabled(this),
                (button, checked) -> {
                    LePraMimSettings.setSafeModeEnabled(this, checked);
                    speak(checked ? "Modo seguro ligado." : "Modo seguro desligado.");
                }
        ), buttonLayout());
        root.addView(createSwitchRow(
                "Não ler em apps sensíveis",
                "Banco, senha e autenticação",
                !LePraMimSettings.getBlockedPackages(this).isEmpty(),
                (button, checked) -> {
                    LePraMimSettings.setDefaultSensitivePackagesBlocked(this, checked);
                    speak(checked ? "Apps sensíveis bloqueados." : "Bloqueio de apps sensíveis desligado.");
                }
        ), buttonLayout());
        root.addView(createSwitchRow(
                "Ler automático",
                "Somente para quem quiser",
                LePraMimSettings.isAutoReadEnabled(this),
                (button, checked) -> {
                    LePraMimSettings.setAutoReadEnabled(this, checked);
                    speak(checked ? "Leitura automática ligada." : "Leitura automática desligada.");
                }
        ), buttonLayout());
        root.addView(createSettingButton("Apagar última leitura", "Não guardar na tela", () -> {
            lastSpokenText = "";
            lastRecognizedImageText = "";
            speak("Última leitura apagada.");
        }), buttonLayout());

        root.addView(createSectionTitle("Cuidador"), blockLayout(0, dp(8), 0, dp(4)));
        root.addView(createSettingButton("Reabrir guia", "Configurar de novo", this::openOnboardingAgain), buttonLayout());
        root.addView(createSettingButton("Ativar permissões", "Abrir acessibilidade", this::openAccessibilitySettings), buttonLayout());
        root.addView(createSettingButton("Testar permissões", "Ver se está pronto", () -> {
            speak(isReaderEnabled() ? "LePraMim está pronto para ouvir outras telas." : "O leitor ainda está desligado. Ative nas configurações de acessibilidade.");
        }), buttonLayout());

        root.addView(createSectionTitle("Plus"), blockLayout(0, dp(8), 0, dp(4)));
        root.addView(createLegalSection("Plano atual", EntitlementStore.getPlanLabel(this)), buttonLayout());
        root.addView(createSettingButton("Comprar assinatura", "Mensal ou anual", this::openPlusScreen), buttonLayout());
        root.addView(createSettingButton("Restaurar compra", "Já assinei", this::restoreSubscription), buttonLayout());

        return scrollView;
    }

    private View createPlusView() {
        showingPlusScreen = true;
        ScrollView scrollView = createBaseScrollView();
        LinearLayout root = createRoot(scrollView);

        root.addView(createTitleHero(
                "LePraMim Plus",
                "Mais autonomia para ouvir mensagens, papéis e textos do dia a dia."
        ), blockLayout(0, 0, 0, dp(12)));

        root.addView(createBackToMainButton(), buttonLayout());

        root.addView(createLegalSection("Uso sem limite", "Leia telas, mensagens e imagens sem ficar contando leituras."), buttonLayout());
        root.addView(createLegalSection("Lê mensagens e telas", "Use o botão amarelo em qualquer app suportado."), buttonLayout());
        root.addView(createLegalSection("Lê papéis pela câmera", "Fotografe boleto, receita, carta ou aviso."), buttonLayout());
        root.addView(createLegalSection("Explica com palavras simples", "Ajuda a entender valor, data, consulta, entrega e avisos."), buttonLayout());
        root.addView(createLegalSection("Mais segurança contra golpes", "Avisa quando encontrar senha, PIX, link estranho ou pedido de dinheiro."), buttonLayout());
        root.addView(createLegalSection("Feito para família", "Configuração pensada para cuidador deixar o celular pronto."), buttonLayout());

        if (EntitlementStore.isSubscriptionActive(this)) {
            root.addView(createLegalSection("Plus ativo", "Sua assinatura está ativa nesta conta da Play Store."), buttonLayout());
        } else {
            View monthlyButton = createActionTile(
                    "Assinar mensal",
                    EntitlementStore.getMonthlyPriceLabel(this),
                    R.drawable.ic_payment,
                    getColor(R.color.lepramim_blue),
                    Color.WHITE,
                    true
            );
            monthlyButton.setOnClickListener(view -> startSubscription(BillingRepository.PLAN_MONTHLY));
            root.addView(monthlyButton, buttonLayout());

            View annualButton = createActionTile(
                    "Assinar anual",
                    EntitlementStore.getAnnualPriceLabel(this) + " melhor valor",
                    R.drawable.ic_payment,
                    getColor(R.color.lepramim_orange),
                    getColor(R.color.lepramim_ink),
                    true
            );
            annualButton.setOnClickListener(view -> startSubscription(BillingRepository.PLAN_ANNUAL));
            root.addView(annualButton, buttonLayout());
        }

        View restoreButton = createActionTile(
                "Restaurar compra",
                "Já assinei",
                R.drawable.ic_repeat,
                Color.WHITE,
                getColor(R.color.lepramim_blue_dark),
                false
        );
        restoreButton.setBackground(createRoundedBackground(Color.WHITE, getColor(R.color.lepramim_blue), dp(2)));
        restoreButton.setOnClickListener(view -> restoreSubscription());
        root.addView(restoreButton, buttonLayout());

        return scrollView;
    }

    private ScrollView createBaseScrollView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(getColor(R.color.lepramim_soft));
        scrollView.setClipToPadding(true);
        scrollView.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });
        return scrollView;
    }

    private LinearLayout createRoot(ScrollView scrollView) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(18));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return root;
    }

    private View createTitleHero(String titleText, String subtitleText) {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(20), dp(20), dp(20));
        hero.setBackground(createHeroBackground());
        hero.setElevation(dp(5));

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 31);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLineSpacing(dp(2), 1.0f);
        hero.addView(title);

        instructionView = new TextView(this);
        instructionView.setText(subtitleText);
        instructionView.setTextColor(Color.WHITE);
        instructionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        instructionView.setTypeface(Typeface.DEFAULT_BOLD);
        instructionView.setLineSpacing(dp(4), 1.0f);
        hero.addView(instructionView, withMargins(
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                0, dp(10), 0, 0
        ));

        return hero;
    }

    private View createBackToMainButton() {
        View backButton = createActionTile(
                "Voltar",
                "Tela principal",
                R.drawable.ic_back,
                Color.WHITE,
                getColor(R.color.lepramim_blue_dark),
                false
        );
        backButton.setBackground(createRoundedBackground(Color.WHITE, getColor(R.color.lepramim_blue), dp(2)));
        backButton.setOnClickListener(view -> returnToMainScreen());
        return backButton;
    }

    private View createSectionTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(getColor(R.color.lepramim_blue_dark));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.START);
        return title;
    }

    private View createSpeedButton(String title, String subtitle, String preset) {
        return createSettingButton(title, subtitle, () -> {
            LePraMimSettings.setSpeechPreset(this, preset);
            testVoice();
        });
    }

    private View createSettingButton(String title, String subtitle, Runnable action) {
        View button = createActionTile(
                title,
                subtitle,
                R.drawable.ic_volume,
                Color.WHITE,
                getColor(R.color.lepramim_blue_dark),
                false
        );
        button.setBackground(createRoundedBackground(Color.WHITE, getColor(R.color.lepramim_blue), dp(2)));
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private View createSwitchRow(String title, String subtitle, boolean checked, CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(14), dp(16), dp(14));
        row.setMinimumHeight(dp(88));
        row.setBackground(createRoundedBackground(Color.WHITE, getColor(R.color.lepramim_blue), dp(2)));
        row.setElevation(dp(2));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(getColor(R.color.lepramim_blue_dark));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        copy.addView(titleView);

        TextView subtitleView = new TextView(this);
        subtitleView.setText(subtitle);
        subtitleView.setTextColor(getColor(R.color.lepramim_muted));
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        subtitleView.setTypeface(Typeface.DEFAULT_BOLD);
        copy.addView(subtitleView);

        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch toggle = new Switch(this);
        toggle.setChecked(checked);
        toggle.setOnCheckedChangeListener(listener);
        row.setOnClickListener(view -> toggle.setChecked(!toggle.isChecked()));
        row.addView(toggle);
        return row;
    }

    private void openOnboardingAgain() {
        showingLegalScreen = false;
        showingSettingsScreen = false;
        showingPlusScreen = false;
        showingOnboardingScreen = true;
        caregiverOnboardingStep = 0;
        setContentView(createOnboardingChoiceView());
        speak("Guia de configuração aberto.");
    }

    private void finishOnboarding() {
        LePraMimSettings.setOnboardingDone(this, true);
        showingOnboardingScreen = false;
        setContentView(createPremiumContentView());
        speak("Pronto. Abra outro app. Toque no bot\u00e3o amarelo para ler a tela, ou arraste at\u00e9 um texto e solte.");
    }

    private void openSettingsScreen() {
        showingLegalScreen = false;
        showingPlusScreen = false;
        showingOnboardingScreen = false;
        setContentView(createSettingsView());
        speak("Configurações abertas.");
    }

    private void openPlusScreen() {
        showingLegalScreen = false;
        showingSettingsScreen = false;
        showingOnboardingScreen = false;
        setContentView(createPlusView());
        speak(getString(R.string.voice_subscription_intro));
    }

    private void setReadingModeAndSpeak(ReadingMode mode) {
        LePraMimSettings.setReadingMode(this, mode);
        if (mode == ReadingMode.IMPORTANT_ONLY) {
            speak("Modo importante ligado. Vou falar só o principal.");
        } else if (mode == ReadingMode.EXPLAIN) {
            speak("Modo explicar ligado. Vou usar palavras simples.");
        } else if (mode == ReadingMode.RISK_ALERT) {
            speak("Modo alerta ligado. Vou avisar sobre golpe, senha e link estranho.");
        } else {
            speak("Modo ler tudo ligado.");
        }
    }

    private void setOverlaySizeAndSpeak(String size) {
        LePraMimSettings.setOverlaySize(this, size);
        speak("Tamanho do botão salvo. O botão atualiza ao abrir outro aplicativo.");
    }

    private void testVoice() {
        TtsVoiceController.apply(this, textToSpeech);
        speak("Olá, eu sou o LePraMim. Vou ler os textos em voz alta para ajudar você.");
    }

    private View createLegalView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(getColor(R.color.lepramim_soft));
        scrollView.setClipToPadding(true);
        scrollView.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(18));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(20), dp(20), dp(20));
        hero.setBackground(createHeroBackground());
        hero.setElevation(dp(4));

        TextView title = new TextView(this);
        title.setText("Termos e privacidade");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLineSpacing(dp(2), 1.0f);
        hero.addView(title);

        instructionView = new TextView(this);
        instructionView.setText("Tudo explicado dentro do app, sem abrir link externo.");
        instructionView.setTextColor(Color.WHITE);
        instructionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        instructionView.setTypeface(Typeface.DEFAULT_BOLD);
        instructionView.setLineSpacing(dp(4), 1.0f);
        hero.addView(instructionView, withMargins(
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                0, dp(10), 0, 0
        ));
        root.addView(hero, blockLayout(0, 0, 0, dp(12)));

        View backButton = createActionTile(
                "Voltar",
                "Tela principal",
                R.drawable.ic_back,
                getColor(R.color.lepramim_orange),
                getColor(R.color.lepramim_ink),
                false
        );
        backButton.setOnClickListener(view -> returnToMainScreen());
        root.addView(backButton, buttonLayout());

        root.addView(createLegalSection(
                "O que o app faz",
                "O LePraMim ajuda pessoas que n\u00e3o sabem ler, idosos e pessoas com dificuldade de interpretar texto. Ele l\u00ea em voz alta textos do celular, fotos, prints, boletos, placas e mensagens vis\u00edveis na tela."
        ), buttonLayout());

        root.addView(createLegalSection(
                "Privacidade",
                "Os textos lidos s\u00e3o usados para falar em voz alta e facilitar o entendimento. O app n\u00e3o vende dados pessoais e n\u00e3o usa servidor pr\u00f3prio para guardar mensagens. A voz e o reconhecimento podem depender dos servi\u00e7os configurados no Android do aparelho. Quando houver pagamento, a compra \u00e9 processada pela Google Play."
        ), buttonLayout());

        root.addView(createLegalSection(
                "Permiss\u00e3o de acessibilidade",
                "A permiss\u00e3o de acessibilidade serve para encontrar textos vis\u00edveis em outros aplicativos quando a pessoa toca no bot\u00e3o OUVIR. O LePraMim n\u00e3o deve fazer compras, mandar mensagens ou mudar configura\u00e7\u00f5es sozinho."
        ), buttonLayout());

        root.addView(createLegalSection(
                "C\u00e2mera e fotos",
                "Quando a pessoa fotografa ou escolhe uma imagem, o app tenta reconhecer o texto para ler em voz alta. Evite usar com senhas, c\u00f3digos banc\u00e1rios ou documentos sigilosos perto de outras pessoas."
        ), buttonLayout());

        root.addView(createLegalSection(
                "Pagamentos",
                "A leitura b\u00e1sica \u00e9 gr\u00e1tis. O plano Plus \u00e9 opcional e pode liberar recursos extras. Assinaturas, cancelamentos e cobran\u00e7as seguem as regras da Google Play."
        ), buttonLayout());

        root.addView(createLegalSection(
                "Contato",
                "Suporte: devs@pascoal.eti.br"
        ), buttonLayout());

        return scrollView;
    }

    private View createLegalSection(String title, String body) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(18), dp(16), dp(18), dp(16));
        section.setBackground(createRoundedBackground(Color.WHITE, getColor(R.color.lepramim_blue), dp(2)));
        section.setElevation(dp(2));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(getColor(R.color.lepramim_blue_dark));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 23);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setLineSpacing(dp(2), 1.0f);
        section.addView(titleView);

        TextView bodyView = new TextView(this);
        bodyView.setText(body);
        bodyView.setTextColor(getColor(R.color.lepramim_ink));
        bodyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        bodyView.setLineSpacing(dp(5), 1.0f);
        section.addView(bodyView, withMargins(
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                0, dp(8), 0, 0
        ));

        return section;
    }

    private View createActionTile(String title, String subtitle, int iconRes, int backgroundColor, int textColor, boolean primary) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.HORIZONTAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(dp(18), dp(15), dp(18), dp(15));
        tile.setMinimumHeight(dp(primary ? 104 : 88));
        tile.setClickable(true);
        tile.setFocusable(true);
        tile.setContentDescription(title + ". " + subtitle);
        tile.setBackground(createRoundedBackground(backgroundColor, Color.TRANSPARENT, 0));
        tile.setElevation(dp(primary ? 5 : 3));

        FrameLayout iconWrap = new FrameLayout(this);
        int iconBackground = textColor == Color.WHITE ? 0x24FFFFFF : 0x1A083B75;
        iconWrap.setBackground(createRoundedBackground(iconBackground, Color.TRANSPARENT, 0));
        ImageView iconView = new ImageView(this);
        Drawable icon = getDrawable(iconRes);
        if (icon != null) {
            icon = icon.mutate();
            icon.setTint(textColor);
            iconView.setImageDrawable(icon);
        }
        iconWrap.addView(iconView, new FrameLayout.LayoutParams(dp(primary ? 42 : 36), dp(primary ? 42 : 36), Gravity.CENTER));
        tile.addView(iconWrap, new LinearLayout.LayoutParams(dp(primary ? 64 : 58), dp(primary ? 64 : 58)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(textColor);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, primary ? 28 : 23);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setSingleLine(false);
        copy.addView(titleView);

        TextView subtitleView = new TextView(this);
        subtitleView.setText(subtitle);
        subtitleView.setTextColor(textColor);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        subtitleView.setTypeface(Typeface.DEFAULT_BOLD);
        subtitleView.setAlpha(textColor == Color.WHITE ? 0.9f : 0.76f);
        copy.addView(subtitleView);

        tile.addView(copy, withMargins(
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                dp(16), 0, 0, 0
        ));

        return tile;
    }

    private Drawable createHeroBackground() {
        GradientDrawable base = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        getColor(R.color.lepramim_blue),
                        0xFF0E5FA9,
                        getColor(R.color.lepramim_blue_dark)
                }
        );
        base.setCornerRadius(dp(24));

        GradientDrawable shine = new GradientDrawable(
                GradientDrawable.Orientation.BL_TR,
                new int[]{
                        0x00FFFFFF,
                        0x18FFFFFF,
                        0x00FFFFFF
                }
        );
        shine.setCornerRadius(dp(24));

        GradientDrawable depth = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        0x00000000,
                        0x14000000
                }
        );
        depth.setCornerRadius(dp(24));

        return new LayerDrawable(new Drawable[]{base, shine, depth});
    }

    private GradientDrawable createRoundedBackground(int color, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(24));
        if (strokeWidth > 0) {
            drawable.setStroke(strokeWidth, strokeColor);
        }
        return drawable;
    }

    private LinearLayout.LayoutParams buttonLayout() {
        return blockLayout(0, dp(6), 0, dp(6));
    }

    private LinearLayout.LayoutParams blockLayout(int left, int top, int right, int bottom) {
        return withMargins(
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ),
                left, top, right, bottom
        );
    }

    private LinearLayout.LayoutParams blockLayout(int left, int top, int right, int bottom, int height) {
        return withMargins(
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        height
                ),
                left, top, right, bottom
        );
    }

    private LinearLayout.LayoutParams withMargins(LinearLayout.LayoutParams params, int left, int top, int right, int bottom) {
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private void startSubscription() {
        startSubscription(BillingRepository.PLAN_MONTHLY);
    }

    private void startSubscription(String plan) {
        if (EntitlementStore.isSubscriptionActive(this)) {
            speak(getString(R.string.voice_subscription_active));
            return;
        }
        if (BillingRepository.PLAN_ANNUAL.equals(plan)) {
            speak(getString(R.string.voice_subscription_start_annual));
        } else {
            speak(getString(R.string.voice_subscription_start_monthly));
        }
        if (billingRepository != null) {
            billingRepository.buy(this, plan);
        }
    }

    private void restoreSubscription() {
        speak(getString(R.string.voice_subscription_restoring));
        if (billingRepository != null) {
            billingRepository.restore();
        }
    }

    private void openCamera() {
        speak(getString(R.string.voice_camera));
        handler.postDelayed(() -> {
            try {
                Intent intent = new Intent(this, CameraCaptureActivity.class);
                startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
            } catch (ActivityNotFoundException exception) {
                openLegacyCamera();
            }
        }, 700);
    }

    private void openLegacyCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
        } catch (ActivityNotFoundException exception) {
            speak(getString(R.string.voice_no_camera));
        }
    }

    private void openImagePicker() {
        speak(getString(R.string.voice_gallery));
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "Escolher imagem"), REQUEST_IMAGE_PICK);
        } catch (ActivityNotFoundException exception) {
            speak(getString(R.string.voice_ocr_error));
        }
    }

    private void openAccessibilitySettings() {
        speak(getString(R.string.voice_accessibility));
        handler.postDelayed(() -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        }, 900);
    }

    private void openLegalScreen() {
        showingLegalScreen = true;
        showingSettingsScreen = false;
        showingPlusScreen = false;
        showingOnboardingScreen = false;
        setContentView(createLegalView());
        speak("Termos e pol\u00edtica de privacidade abertos no app.");
    }

    private void returnToMainScreen() {
        showingLegalScreen = false;
        showingSettingsScreen = false;
        showingPlusScreen = false;
        showingOnboardingScreen = false;
        setContentView(createPremiumContentView());
        speak("Voltei para a tela principal.");
    }

    private void repeatLastSpeech() {
        if (!lastRecognizedImageText.isEmpty()) {
            speakTextWithExplanation(lastRecognizedImageText);
        } else if (lastSpokenText.isEmpty()) {
            speak(getString(R.string.voice_intro));
        } else {
            speak(lastSpokenText);
        }
    }

    private void requestVoiceCommand() {
        if (speechRecognizer == null) {
            speak(getString(R.string.voice_no_speech));
            return;
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            return;
        }

        speak(getString(R.string.voice_listening));
        handler.postDelayed(this::startVoiceRecognition, 1300);
    }

    private void startVoiceRecognition() {
        if (speechRecognizer == null) {
            speak(getString(R.string.voice_no_speech));
            return;
        }

        if (textToSpeech != null) {
            textToSpeech.stop();
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.app_name));
        speechRecognizer.startListening(intent);
    }

    private RecognitionListener createRecognitionListener() {
        return new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                instructionView.setText(R.string.voice_listening);
            }

            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { }

            @Override
            public void onError(int error) {
                speak(getString(R.string.voice_not_understood));
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches == null || matches.isEmpty()) {
                    speak(getString(R.string.voice_not_understood));
                    return;
                }
                handleVoiceCommand(matches.get(0));
            }

            @Override public void onPartialResults(Bundle partialResults) { }
            @Override public void onEvent(int eventType, Bundle params) { }
        };
    }

    private void handleVoiceCommand(String command) {
        String normalized = command.toLowerCase(portugueseBrazil);

        if ((normalized.contains("assin") || normalized.contains("plus"))
                && (normalized.contains("anual") || normalized.contains("ano"))) {
            startSubscription(BillingRepository.PLAN_ANNUAL);
        } else if (normalized.contains("assin") || normalized.contains("plus")) {
            startSubscription();
        } else if (normalized.contains("restaur")) {
            restoreSubscription();
        } else if (normalized.contains("foto") || normalized.contains("fotograf") || normalized.contains("camera") || normalized.contains("câmera")) {
            openCamera();
        } else if (normalized.contains("imagem") || normalized.contains("print") || normalized.contains("galeria")) {
            openImagePicker();
        } else if (normalized.contains("ativar") || normalized.contains("leitor")) {
            openAccessibilitySettings();
        } else if (normalized.contains("termo") || normalized.contains("privacidade")) {
            openLegalScreen();
        } else if (normalized.contains("whatsapp") || normalized.contains("zap") || normalized.contains("mensagem") || normalized.contains("aplicativo")) {
            speak(getString(R.string.voice_screen_reader));
        } else if (normalized.contains("repet") || normalized.contains("ouvir") || normalized.contains("de novo")) {
            repeatLastSpeech();
        } else if (normalized.contains("ajuda") || normalized.contains("socorro")) {
            speak(getString(R.string.voice_help));
        } else {
            speak(getString(R.string.voice_not_understood));
        }
    }

    private void handleIncomingImageIntent(Intent intent) {
        if (intent == null) {
            return;
        }

        Uri imageUri = null;
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action) && intent.getType() != null && intent.getType().startsWith("image/")) {
            Object extra = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (extra instanceof Uri) {
                imageUri = (Uri) extra;
            }
        } else if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            imageUri = intent.getData();
        }

        if (imageUri != null) {
            recognizeImageUri(imageUri);
        }
    }

    private void recognizeImageUri(Uri imageUri) {
        if (imageUri == null) {
            speak(getString(R.string.voice_ocr_error));
            return;
        }

        if (photoPreview != null) {
            photoPreview.setImageURI(imageUri);
            photoPreview.setVisibility(View.VISIBLE);
        }

        try {
            InputImage image = InputImage.fromFilePath(this, imageUri);
            recognizeTextFromImage(image);
        } catch (IOException exception) {
            speak(getString(R.string.voice_ocr_error));
        }
    }

    private void recognizeBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            speak(getString(R.string.voice_ocr_error));
            return;
        }
        if (photoPreview != null) {
            photoPreview.setImageBitmap(bitmap);
            photoPreview.setVisibility(View.VISIBLE);
        }
        recognizeTextFromImage(InputImage.fromBitmap(bitmap, 0));
    }

    private void recognizeTextFromImage(InputImage image) {
        if (!EntitlementStore.tryConsumeImageRead(this)) {
            speak(EntitlementStore.getImageLimitReachedMessage(this));
            return;
        }

        if (imageTextRecognizer == null) {
            imageTextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        }

        String remainingMessage = EntitlementStore.getImageRemainingMessage(this);
        if (remainingMessage.isEmpty()) {
            speak(getString(R.string.voice_ocr_processing));
        } else {
            speak(getString(R.string.voice_ocr_processing) + " " + remainingMessage);
        }
        imageTextRecognizer.process(image)
                .addOnSuccessListener(this::handleRecognizedText)
                .addOnFailureListener(exception -> speak(getString(R.string.voice_ocr_error)));
    }

    private void handleRecognizedText(Text visionText) {
        String recognized = cleanup(visionText == null ? "" : visionText.getText());
        if (recognized.isEmpty()) {
            lastRecognizedImageText = "";
            speak(getString(R.string.voice_ocr_empty));
            return;
        }

        lastRecognizedImageText = recognized;
        speakTextWithExplanation(recognized);
    }

    private void speakTextWithExplanation(String text) {
        String trimmed = cleanup(text);
        if (trimmed.isEmpty()) {
            speak(getString(R.string.voice_ocr_empty));
            return;
        }

        SmartReadingEngine.Result result = smartReadingEngine.analyze(
                trimmed,
                LePraMimSettings.getReadingMode(this),
                LePraMimSettings.isSafeModeEnabled(this)
        );
        speak(result.speech);
    }

    private String simpleExplanation(String text) {
        String normalized = text.toLowerCase(portugueseBrazil);
        if (normalized.contains("vence") || normalized.contains("vencimento") || normalized.contains("boleto")) {
            return "Parece ser uma cobrança ou boleto. Preste atenção no valor e na data de vencimento.";
        }
        if (normalized.contains("consulta") || normalized.contains("médico") || normalized.contains("medico")) {
            return "Parece ser informação de consulta. Preste atenção no dia e no horário.";
        }
        if (normalized.contains("entrega") || normalized.contains("pedido")) {
            return "Parece ser informação de compra ou entrega.";
        }
        if (normalized.contains("senha") || normalized.contains("código") || normalized.contains("codigo")) {
            return "Parece ter uma senha ou código. Não compartilhe com desconhecidos.";
        }
        return "";
    }

    private String limitForSpeech(String text) {
        if (text.length() <= 1200) {
            return text;
        }
        return text.substring(0, 1200);
    }

    @Override
    protected void onResume() {
        super.onResume();
        sendReaderVisibility(true);
        if (billingRepository != null) {
            billingRepository.refresh();
        }
        if (!showingLegalScreen && !showingSettingsScreen && !showingPlusScreen && !showingOnboardingScreen) {
            updateReaderState();
        }
        EntitlementStore.refreshRemoteUsageAsync(this, () -> {
            if (planView != null) {
                planView.setText(EntitlementStore.getPlanLabel(this));
            }
        });
    }

    @Override
    protected void onPause() {
        sendReaderVisibility(false);
        super.onPause();
    }

    private void updateReaderState() {
        boolean subscriptionActive = EntitlementStore.isSubscriptionActive(this);
        boolean enabled = isReaderEnabled();

        if (planView != null) {
            planView.setText(EntitlementStore.getPlanLabel(this));
        }

        if (statusView != null) {
            statusView.setText(enabled ? "OUVIR pronto" : "OUVIR desligado");
            statusView.setBackground(createRoundedBackground(
                    enabled ? getColor(R.color.lepramim_teal_soft) : getColor(R.color.lepramim_yellow),
                    Color.TRANSPARENT,
                    0
            ));
            statusView.setTextColor(enabled ? getColor(R.color.lepramim_blue_dark) : getColor(R.color.lepramim_ink));
        }

        if (instructionView == null) {
            return;
        }

        if (enabled) {
            instructionView.setText(subscriptionActive ? "Arraste at\u00e9 o texto." : "Abra outro app.");
            if (!announcedReaderEnabled && textToSpeech != null) {
                announcedReaderEnabled = true;
                speak(getString(R.string.voice_reader_enabled));
            }
        } else {
            instructionView.setText("Ative o bot\u00e3o amarelo.");
        }
    }

    private boolean isReaderEnabled() {
        String enabledServices = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (enabledServices == null) {
            return false;
        }
        ComponentName serviceName = new ComponentName(this, LePraMimAccessibilityService.class);
        return enabledServices.toLowerCase(portugueseBrazil)
                .contains(serviceName.flattenToString().toLowerCase(portugueseBrazil));
    }

    private void sendReaderVisibility(boolean inForeground) {
        Intent intent = new Intent(LePraMimAccessibilityService.ACTION_APP_VISIBILITY);
        intent.setPackage(getPackageName());
        intent.putExtra(LePraMimAccessibilityService.EXTRA_IN_FOREGROUND, inForeground);
        sendBroadcast(intent);
    }

    private void speak(String text) {
        lastSpokenText = text;
        if (instructionView != null
                && !showingOnboardingScreen
                && !showingSettingsScreen
                && !showingPlusScreen
                && !showingLegalScreen) {
            instructionView.setText(shortVisualMessage(text));
        }
        if (textToSpeech != null) {
            TtsVoiceController.apply(this, textToSpeech);
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "lepramim-voice");
        }
    }

    private String shortVisualMessage(String spokenText) {
        String text = cleanup(spokenText).toLowerCase(portugueseBrazil);
        if (text.contains("configura")) {
            return "Ative nas configurações.";
        }
        if (text.contains("procurando texto")) {
            return "Procurando texto...";
        }
        if (text.contains("não consegui")) {
            return "Tente outra vez.";
        }
        if (text.contains("assinatura") || text.contains("plus") || text.contains("play store")) {
            return "Plus sem limite.";
        }
        if (text.contains("ouvindo")) {
            return "Pode falar.";
        }
        if (text.contains("leitor ativado")) {
            return "OUVIR está pronto.";
        }
        if (text.length() > 72) {
            return "Arraste at\u00e9 o texto.";
        }
        return spokenText;
    }

    private String cleanup(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    @Override
    public void onSubscriptionPriceChanged(String priceLabel) {
        updateReaderState();
    }

    @Override
    public void onSubscriptionStatusChanged(boolean active) {
        updateReaderState();
    }

    @Override
    public void onBillingMessage(String message) {
        speak(message);
        updateReaderState();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            return;
        }

        if (requestCode == REQUEST_IMAGE_CAPTURE) {
            if (data != null && data.getStringExtra(CameraCaptureActivity.EXTRA_IMAGE_URI) != null) {
                recognizeImageUri(Uri.parse(data.getStringExtra(CameraCaptureActivity.EXTRA_IMAGE_URI)));
                return;
            }
            if (data != null && data.getExtras() != null) {
                Object image = data.getExtras().get("data");
                if (image instanceof Bitmap) {
                    recognizeBitmap((Bitmap) image);
                    return;
                }
            }
            speak(getString(R.string.voice_ocr_error));
        } else if (requestCode == REQUEST_IMAGE_PICK) {
            Uri imageUri = data == null ? null : data.getData();
            recognizeImageUri(imageUri);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestVoiceCommand();
            } else {
                speak(getString(R.string.voice_mic_denied));
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (showingLegalScreen || showingSettingsScreen || showingPlusScreen || showingOnboardingScreen) {
            returnToMainScreen();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (billingRepository != null) {
            billingRepository.release();
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (imageTextRecognizer != null) {
            imageTextRecognizer.close();
        }
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }
}
