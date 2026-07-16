package com.lepramim.app;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;

import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class LePraMimAccessibilityService extends AccessibilityService implements TextToSpeech.OnInitListener {
    static final String ACTION_APP_VISIBILITY = "com.lepramim.app.APP_VISIBILITY";
    static final String EXTRA_IN_FOREGROUND = "in_foreground";

    private static final int MAX_SPOKEN_CHARS = 1200;
    private static final int RECENT_LIMIT = 16;
    private static final int MAX_WHATSAPP_MESSAGES = 5;
    private static final int MAX_WHATSAPP_RECEIVED_MESSAGES = 5;
    private static final long MIN_READ_INTERVAL_MS = 1200;
    private static final long AUTO_READ_INTERVAL_MS = 5000;
    private static final long DOUBLE_TAP_MS = 360;
    private static final long LONG_PRESS_MS = 620;
    private static final int POINT_READ_RADIUS_DP = 92;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Locale portugueseBrazil = new Locale("pt", "BR");
    private final Runnable readScreenRunnable = () -> readVisibleScreenText("Na tela. ", false);
    private final Runnable showOverlayRunnable = this::showOverlayButton;
    private final ArrayDeque<String> recentMessages = new ArrayDeque<>();
    private final SmartReadingEngine smartReadingEngine = new SmartReadingEngine();

    private TextToSpeech textToSpeech;
    private WindowManager windowManager;
    private View overlayButton;
    private WindowManager.LayoutParams overlayParams;
    private BroadcastReceiver appVisibilityReceiver;
    private String lastPackageName = "";
    private String lastSpokenText = "";
    private long lastReadAt = 0L;
    private long lastAutoReadAt = 0L;
    private long lastOverlayTapAt = 0L;
    private float overlayDownRawX = 0f;
    private float overlayDownRawY = 0f;
    private int overlayStartX = 0;
    private int overlayStartY = 0;
    private boolean overlayMoved = false;
    private boolean overlayLongPressed = false;
    private Runnable overlayLongPressRunnable;
    private boolean appInForeground = false;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        textToSpeech = new TextToSpeech(this, this);
        registerAppVisibilityReceiver();
        showOverlayButton();
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            TtsVoiceController.apply(this, textToSpeech);
            speak(getString(R.string.voice_reader_enabled));
        }
    }

    private void selectHumanizedPortugueseVoice() {
        if (textToSpeech == null) {
            return;
        }

        Set<Voice> voices = textToSpeech.getVoices();
        if (voices == null || voices.isEmpty()) {
            return;
        }

        Voice bestVoice = null;
        int bestScore = Integer.MIN_VALUE;
        for (Voice voice : voices) {
            if (voice == null || voice.getLocale() == null) {
                continue;
            }
            Locale locale = voice.getLocale();
            if (!"pt".equalsIgnoreCase(locale.getLanguage())) {
                continue;
            }

            int score = 0;
            if ("BR".equalsIgnoreCase(locale.getCountry())) {
                score += 80;
            }
            if (voice.isNetworkConnectionRequired()) {
                score += 55;
            } else {
                score += 12;
            }
            score += voice.getQuality() * 24;
            if (voice.getQuality() >= Voice.QUALITY_HIGH) {
                score += 45;
            }
            if (voice.getQuality() >= Voice.QUALITY_VERY_HIGH) {
                score += 70;
            }
            score -= voice.getLatency();

            String name = voice.getName() == null ? "" : voice.getName().toLowerCase(Locale.US);
            if (name.contains("female") || name.contains("feminina") || name.contains("brasil")) {
                score += 18;
            }
            if (name.contains("network") || name.contains("online") || name.contains("neural")
                    || name.contains("wavenet") || name.contains("enhanced")) {
                score += 35;
            }

            if (score > bestScore) {
                bestScore = score;
                bestVoice = voice;
            }
        }

        if (bestVoice != null) {
            textToSpeech.setVoice(bestVoice);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }

        String packageName = event.getPackageName() == null ? "" : event.getPackageName().toString();
        if (packageName.equals(getPackageName())) {
            if (isOwnAppActiveWindow()) {
                appInForeground = true;
                lastPackageName = packageName;
                handler.removeCallbacks(showOverlayRunnable);
                hideOverlayButton();
            }
            return;
        }
        appInForeground = false;
        showOverlayButton();
        lastPackageName = packageName;

        boolean plusActive = EntitlementStore.hasUnlimitedAccess(this);

        if (event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            readNotification(event, packageName, plusActive);
            return;
        }

        if (plusActive
                && LePraMimSettings.isAutoReadEnabled(this)
                && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            scheduleAutoReadForNewScreen(packageName);
        }
    }

    private boolean isOwnAppActiveWindow() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return false;
        }
        CharSequence packageName = root.getPackageName();
        boolean ownApp = packageName != null && getPackageName().contentEquals(packageName);
        root.recycle();
        return ownApp;
    }

    @Override
    public void onInterrupt() {
        if (textToSpeech != null) {
            textToSpeech.stop();
        }
    }

    private void showOverlayButton() {
        if (!LePraMimSettings.isOverlayEnabled(this)) {
            hideOverlayButton();
            return;
        }
        if (appInForeground || getPackageName().equals(lastPackageName)) {
            return;
        }
        if (overlayButton != null) {
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) {
            return;
        }

        ImageView button = new ImageView(this);
        button.setImageResource(R.drawable.ic_pointer_listen);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int sizeDp = LePraMimSettings.getOverlaySizeDp(this);
        int padding = Math.max(4, sizeDp / 12);
        button.setPadding(dp(padding), dp(padding), dp(padding), dp(padding));
        button.setContentDescription("Arraste a mira amarela para cima do texto e solte para ouvir");
        button.setBackground(overlayBackground(Color.rgb(255, 209, 102), getColorCompat(R.color.lepramim_blue)));
        button.setAlpha(LePraMimSettings.isOverlayDiscreet(this) ? 0.62f : 1.0f);
        button.setElevation(dp(14));
        button.setOnTouchListener((view, event) -> handleOverlayTouch(event));

        overlayParams = new WindowManager.LayoutParams(
                dp(sizeDp),
                dp(sizeDp),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        overlayParams.gravity = Gravity.BOTTOM | Gravity.END;
        overlayParams.x = LePraMimSettings.getOverlayX(this, dp(14));
        overlayParams.y = LePraMimSettings.getOverlayY(this, dp(86));

        try {
            windowManager.addView(button, overlayParams);
            overlayButton = button;
        } catch (RuntimeException ignored) {
            overlayButton = null;
            overlayParams = null;
        }
    }

    private boolean handleOverlayTouch(MotionEvent event) {
        if (overlayButton == null || overlayParams == null || windowManager == null) {
            return false;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                setOverlayPressed(true);
                overlayDownRawX = event.getRawX();
                overlayDownRawY = event.getRawY();
                overlayStartX = overlayParams.x;
                overlayStartY = overlayParams.y;
                overlayMoved = false;
                overlayLongPressed = false;
                overlayLongPressRunnable = () -> {
                    overlayLongPressed = true;
                    if (textToSpeech != null) {
                        textToSpeech.stop();
                    }
                    setOverlayColor(getColorCompat(R.color.lepramim_blue));
                    speak("Leitura parada.");
                };
                handler.postDelayed(overlayLongPressRunnable, LONG_PRESS_MS);
                return true;

            case MotionEvent.ACTION_MOVE:
                int dx = Math.round(event.getRawX() - overlayDownRawX);
                int dy = Math.round(event.getRawY() - overlayDownRawY);
                if (Math.abs(dx) > dp(5) || Math.abs(dy) > dp(5)) {
                    overlayMoved = true;
                    handler.removeCallbacks(overlayLongPressRunnable);
                    overlayParams.x = Math.max(0, overlayStartX - dx);
                    overlayParams.y = Math.max(0, overlayStartY - dy);
                    try {
                        windowManager.updateViewLayout(overlayButton, overlayParams);
                    } catch (RuntimeException ignored) {
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                setOverlayPressed(false);
                handler.removeCallbacks(overlayLongPressRunnable);
                if (overlayMoved) {
                    LePraMimSettings.setOverlayPosition(this, overlayParams.x, overlayParams.y);
                    int[] target = overlayTargetPoint();
                    readPointedText(target[0], target[1], true);
                    return true;
                }
                if (overlayLongPressed) {
                    return true;
                }
                long now = System.currentTimeMillis();
                if (now - lastOverlayTapAt <= DOUBLE_TAP_MS) {
                    repeatLastReading();
                    lastOverlayTapAt = 0L;
                } else {
                    lastOverlayTapAt = now;
                    readVisibleScreenText("Na tela. ", true);
                }
                return true;

            default:
                return true;
        }
    }

    private void repeatLastReading() {
        if (lastSpokenText == null || lastSpokenText.isEmpty()) {
            speakOnce("Ainda não tenho uma leitura para repetir.", true);
            return;
        }
        speak(lastSpokenText);
    }

    private void registerAppVisibilityReceiver() {
        if (appVisibilityReceiver != null) {
            return;
        }

        appVisibilityReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null && ACTION_APP_VISIBILITY.equals(intent.getAction())) {
                    if (intent.getBooleanExtra(EXTRA_IN_FOREGROUND, false)) {
                        appInForeground = true;
                        lastPackageName = getPackageName();
                        handler.removeCallbacks(showOverlayRunnable);
                        hideOverlayButton();
                    } else {
                        appInForeground = false;
                        if (getPackageName().equals(lastPackageName)) {
                            lastPackageName = "";
                        }
                        handler.postDelayed(showOverlayRunnable, 250);
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter(ACTION_APP_VISIBILITY);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(appVisibilityReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(appVisibilityReceiver, filter);
        }
    }

    private void hideOverlayButton() {
        if (overlayButton == null || windowManager == null) {
            overlayButton = null;
            return;
        }

        try {
            windowManager.removeView(overlayButton);
        } catch (RuntimeException ignored) {
        }
        overlayButton = null;
        overlayParams = null;
    }

    private GradientDrawable circle(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    private GradientDrawable overlayBackground(int color, int strokeColor) {
        GradientDrawable drawable = circle(color);
        drawable.setStroke(dp(3), strokeColor);
        return drawable;
    }

    private void setOverlayColor(int color) {
        if (overlayButton == null) {
            return;
        }
        overlayButton.setBackground(overlayBackground(color, getColorCompat(R.color.lepramim_blue_dark)));
    }

    private void setOverlayPressed(boolean pressed) {
        if (overlayButton == null) {
            return;
        }
        overlayButton.setScaleX(pressed ? 1.12f : 1.0f);
        overlayButton.setScaleY(pressed ? 1.12f : 1.0f);
        overlayButton.setAlpha(pressed || !LePraMimSettings.isOverlayDiscreet(this) ? 1.0f : 0.62f);
        if (pressed) {
            overlayButton.setBackground(overlayBackground(0xFFFFE08A, getColorCompat(R.color.lepramim_green)));
        } else {
            overlayButton.setBackground(overlayBackground(Color.rgb(255, 209, 102), getColorCompat(R.color.lepramim_blue)));
        }
    }

    private int[] overlayTargetPoint() {
        if (overlayButton == null) {
            return new int[]{0, 0};
        }
        int[] location = new int[2];
        overlayButton.getLocationOnScreen(location);
        return new int[]{
                location[0] + overlayButton.getWidth() / 2,
                location[1] + overlayButton.getHeight() / 2
        };
    }

    private int getColorCompat(int colorRes) {
        return getColor(colorRes);
    }

    private void scheduleAutoReadForNewScreen(String packageName) {
        long now = System.currentTimeMillis();
        if (packageName.equals(lastPackageName) && now - lastAutoReadAt < AUTO_READ_INTERVAL_MS) {
            return;
        }
        lastPackageName = packageName;
        lastAutoReadAt = now;
        handler.removeCallbacks(readScreenRunnable);
        handler.postDelayed(readScreenRunnable, 900);
    }

    private void readNotification(AccessibilityEvent event, String packageName, boolean plusActive) {
        String title = "";
        String text = "";
        String bigText = "";
        List<String> notificationLines = new ArrayList<>();

        if (event.getParcelableData() instanceof Notification) {
            Notification notification = (Notification) event.getParcelableData();
            Bundle extras = notification.extras;
            if (extras != null) {
                title = charSequenceToString(extras.getCharSequence(Notification.EXTRA_TITLE));
                text = charSequenceToString(extras.getCharSequence(Notification.EXTRA_TEXT));
                bigText = charSequenceToString(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
                CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
                if (TextUtils.isEmpty(bigText) && lines != null && lines.length > 0) {
                    bigText = charSequenceToString(lines[lines.length - 1]);
                }
                if (lines != null) {
                    for (CharSequence line : lines) {
                        String cleanLine = cleanup(charSequenceToString(line));
                        if (!cleanLine.isEmpty()) {
                            notificationLines.add(cleanLine);
                        }
                    }
                }
            }
        }

        if (TextUtils.isEmpty(text) && !event.getText().isEmpty()) {
            text = charSequenceToString(event.getText().get(event.getText().size() - 1));
        }

        addBigTextLines(notificationLines, bigText);
        String message = cleanup(TextUtils.isEmpty(bigText) ? text : bigText);
        String sender = cleanup(title);
        String appName = friendlyAppName(packageName);
        String notificationAppName = "Notifica\u00e7\u00e3o do " + appName;
        if (sameMeaning(sender, appName)) {
            sender = "";
        }
        String messageForAnalysis = buildNotificationAnalysisText(message, notificationLines);

        if (messageForAnalysis.isEmpty() || shouldIgnoreNotification(messageForAnalysis)) {
            return;
        }
        SmartReadingEngine.Result analysis = smartReadingEngine.analyze(
                messageForAnalysis,
                LePraMimSettings.getReadingMode(this),
                LePraMimSettings.isSafeModeEnabled(this)
        );
        if (analysis.sensitive && LePraMimSettings.isSafeModeEnabled(this)) {
            speakOnce(analysis.speech);
            return;
        }
        if (!plusActive && !EntitlementStore.tryConsumeScreenRead(this)) {
            speakOnce(EntitlementStore.getScreenLimitReachedMessage(this));
            return;
        }

        String spoken;
        if (isMessagingPackage(packageName) || !notificationLines.isEmpty()) {
            spoken = buildConversationNotificationSpeech(notificationAppName, sender, message, notificationLines);
        } else {
            if (!sender.isEmpty() && !startsWithSameMeaning(message, sender)) {
                spoken = notificationAppName + ". " + sender + ": " + message;
            } else {
                spoken = notificationAppName + ". " + message;
            }
            if (LePraMimSettings.getReadingMode(this) != ReadingMode.READ_ALL) {
                spoken = notificationAppName + ". " + analysis.speech;
            }
        }
        if (analysis.risk) {
            spoken = "Atenção. Esta mensagem pode ser perigosa. " + spoken;
        }
        speakOnce(spoken);
    }

    private void addBigTextLines(List<String> notificationLines, String bigText) {
        if (TextUtils.isEmpty(bigText) || !bigText.contains("\n")) {
            return;
        }
        String[] parts = bigText.split("\\n");
        for (String part : parts) {
            String cleanLine = cleanup(part);
            if (!cleanLine.isEmpty() && !notificationLines.contains(cleanLine)) {
                notificationLines.add(cleanLine);
            }
        }
    }

    private String buildNotificationAnalysisText(String message, List<String> notificationLines) {
        StringBuilder builder = new StringBuilder();
        if (notificationLines != null && !notificationLines.isEmpty()) {
            for (String line : notificationLines) {
                appendReadableText(builder, line);
            }
        }
        if (builder.length() == 0) {
            appendReadableText(builder, message);
        }
        return cleanup(builder.toString());
    }

    private String buildConversationNotificationSpeech(String appName, String fallbackSender, String message,
                                                       List<String> notificationLines) {
        List<ConversationMessageFormatter.Message> messages = new ArrayList<>();
        if (notificationLines != null && !notificationLines.isEmpty()) {
            for (String line : notificationLines) {
                ConversationMessageFormatter.Message parsed =
                        ConversationMessageFormatter.parseMessage(cleanup(line), fallbackSender);
                if (!parsed.text.isEmpty()) {
                    messages.add(parsed);
                }
            }
        }
        if (messages.isEmpty() && !TextUtils.isEmpty(message)) {
            messages.add(ConversationMessageFormatter.parseMessage(message, fallbackSender));
        }
        return ConversationMessageFormatter.format(
                appName,
                fallbackSender,
                messages,
                appName + ". Não encontrei mensagem legível nesta notificação."
        );
    }

    private void readVisibleScreenText(String prefix, boolean manualRead) {
        setOverlayColor(getColorCompat(R.color.lepramim_blue));
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            setOverlayColor(0xFFE95B5B);
            speakOnce("Não encontrei texto legível nesta tela.");
            return;
        }

        String packageName = root.getPackageName() == null ? lastPackageName : root.getPackageName().toString();
        if (LePraMimSettings.isPackageBlocked(this, packageName)) {
            root.recycle();
            if (manualRead) {
                setOverlayColor(0xFFE95B5B);
                speakOnce("Modo seguro. Este aplicativo está bloqueado para leitura em voz alta.");
            }
            return;
        }

        String spoken;
        boolean isWhatsAppConversation = false;
        List<WhatsAppConversationFormatter.Candidate> whatsAppCandidates = null;
        if (isWhatsAppPackage(packageName)) {
            whatsAppCandidates = collectWhatsAppFormatterCandidates(root);
            isWhatsAppConversation = WhatsAppConversationFormatter.hasConversationMarker(whatsAppCandidates);
        }
        if (isWhatsAppConversation) {
            spoken = buildWhatsAppConversationText(whatsAppCandidates);
        } else {
            List<VisibleScreenFormatter.Candidate> screenCandidates = new ArrayList<>();
            collectVisibleScreenCandidates(root, screenCandidates, new HashSet<>());
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int screenHeight = getResources().getDisplayMetrics().heightPixels;
            if (isMessagingPackage(packageName)) {
                spoken = ConversationScreenFormatter.format(friendlyAppName(packageName), screenCandidates, screenWidth, screenHeight);
            } else {
                spoken = "";
            }
            if (spoken.isEmpty()) {
                spoken = VisibleScreenFormatter.format(screenCandidates, screenWidth, screenHeight);
            }
            if (spoken.isEmpty()) {
                StringBuilder builder = new StringBuilder();
                Set<String> seen = new HashSet<>();
                collectText(root, builder, seen);
                spoken = cleanup(builder.toString());
                if (!spoken.isEmpty()) {
                    spoken = prefix + spoken;
                }
            }
        }
        root.recycle();

        if (spoken.isEmpty()) {
            setOverlayColor(0xFFE95B5B);
            speakOnce("Não encontrei texto legível nesta tela.");
            return;
        }
        if (manualRead && !EntitlementStore.tryConsumeScreenRead(this)) {
            setOverlayColor(0xFFE95B5B);
            speakOnce(EntitlementStore.getScreenLimitReachedMessage(this));
            return;
        }
        if (isWhatsAppConversation) {
            speakOnce(spoken, false);
            return;
        }
        SmartReadingEngine.Result analysis = smartReadingEngine.analyze(
                spoken,
                LePraMimSettings.getReadingMode(this),
                LePraMimSettings.isSafeModeEnabled(this)
        );
        spoken = analysis.speech;
        speakOnce(spoken, manualRead);
    }

    private void readPointedText(int rawX, int rawY, boolean manualRead) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            setOverlayColor(0xFFE95B5B);
            speakOnce("Coloque o bot\u00e3o amarelo em cima do texto e solte para ouvir.", true);
            return;
        }

        String packageName = root.getPackageName() == null ? lastPackageName : root.getPackageName().toString();
        if (LePraMimSettings.isPackageBlocked(this, packageName)) {
            root.recycle();
            setOverlayColor(0xFFE95B5B);
            speakOnce("Modo seguro. Este aplicativo est\u00e1 bloqueado para leitura em voz alta.", true);
            return;
        }

        TextCandidate candidate = findBestReadableCandidateNearPoint(root, rawX, rawY);
        root.recycle();

        if (candidate == null || cleanup(candidate.text).isEmpty()) {
            setOverlayColor(0xFFE95B5B);
            speakOnce("N\u00e3o encontrei texto nesse ponto. Coloque o bot\u00e3o amarelo em cima das palavras e solte.", true);
            return;
        }

        if (manualRead && !EntitlementStore.tryConsumeScreenRead(this)) {
            setOverlayColor(0xFFE95B5B);
            speakOnce(EntitlementStore.getScreenLimitReachedMessage(this), true);
            return;
        }

        setOverlayColor(getColorCompat(R.color.lepramim_green));
        SmartReadingEngine.Result analysis = smartReadingEngine.analyze(
                candidate.text,
                LePraMimSettings.getReadingMode(this),
                LePraMimSettings.isSafeModeEnabled(this)
        );
        String speech = analysis.speech;
        if (!speech.startsWith("Modo seguro") && !speech.startsWith("Aten")) {
            speech = "Aqui diz. " + speech;
        }
        speakOnce(speech, true);
    }

    private TextCandidate findBestReadableCandidateNearPoint(AccessibilityNodeInfo root, int rawX, int rawY) {
        List<TextCandidate> candidates = new ArrayList<>();
        collectReadablePointCandidates(root, candidates, new HashSet<>());
        TextCandidate best = null;
        int bestScore = Integer.MAX_VALUE;
        int maxDistance = dp(POINT_READ_RADIUS_DP);

        for (TextCandidate candidate : candidates) {
            if (candidate == null || candidate.bounds == null || candidate.bounds.isEmpty()) {
                continue;
            }
            int distance = distanceToRect(candidate.bounds, rawX, rawY);
            Rect expanded = new Rect(candidate.bounds);
            expanded.inset(-dp(18), -dp(14));
            boolean inside = expanded.contains(rawX, rawY);
            if (!inside && distance > maxDistance) {
                continue;
            }

            int area = Math.max(1, candidate.bounds.width() * candidate.bounds.height());
            int score = (inside ? 0 : distance * 22)
                    + Math.min(area / 220, 2600)
                    - Math.min(candidate.text.length() * 5, 520);
            if (candidate.fromContentDescription) {
                score += 420;
            }
            if (candidate.clickable) {
                score += 180;
            }
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private void collectReadablePointCandidates(AccessibilityNodeInfo node, List<TextCandidate> candidates,
                                                Set<String> seen) {
        if (node == null) {
            return;
        }

        if (node.isVisibleToUser()) {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            String className = node.getClassName() == null ? "" : node.getClassName().toString();
            appendPointCandidate(candidates, seen, node.getText(), bounds, className,
                    node.isEditable(), node.isClickable(), false);
            appendPointCandidate(candidates, seen, node.getContentDescription(), bounds, className,
                    node.isEditable(), node.isClickable(), true);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectReadablePointCandidates(child, candidates, seen);
                child.recycle();
            }
        }
    }

    private void appendPointCandidate(List<TextCandidate> candidates, Set<String> seen, CharSequence value,
                                      Rect bounds, String className, boolean editable, boolean clickable,
                                      boolean fromContentDescription) {
        String text = SpeechTextCleaner.stripTrailingTimeStatus(charSequenceToString(value));
        if (text.isEmpty()
                || bounds == null
                || bounds.isEmpty()
                || editable
                || shouldIgnoreScreenText(text)
                || SpeechTextCleaner.isMediaOrControlLabel(text)
                || !SpeechTextCleaner.hasReadableText(text)) {
            return;
        }

        String normalizedClass = className == null ? "" : className.toLowerCase(Locale.US);
        boolean imageLike = normalizedClass.contains("image") || normalizedClass.contains("imagebutton");
        if (fromContentDescription && imageLike) {
            return;
        }

        String key = SpeechTextCleaner.normalize(text) + "|" + bounds.flattenToString();
        if (seen.contains(key)) {
            return;
        }
        seen.add(key);
        candidates.add(new TextCandidate(text, new Rect(bounds), className, editable, clickable, fromContentDescription));
    }

    private int distanceToRect(Rect rect, int x, int y) {
        int dx = 0;
        if (x < rect.left) {
            dx = rect.left - x;
        } else if (x > rect.right) {
            dx = x - rect.right;
        }

        int dy = 0;
        if (y < rect.top) {
            dy = rect.top - y;
        } else if (y > rect.bottom) {
            dy = y - rect.bottom;
        }
        return (int) Math.sqrt((double) dx * dx + (double) dy * dy);
    }

    private void collectText(AccessibilityNodeInfo node, StringBuilder builder, Set<String> seen) {
        if (node == null || builder.length() >= MAX_SPOKEN_CHARS) {
            return;
        }

        if (node.isVisibleToUser()) {
            appendUnique(builder, seen, node.getText());
            appendUnique(builder, seen, node.getContentDescription());
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectText(child, builder, seen);
                child.recycle();
            }
        }
    }

    private void appendUnique(StringBuilder builder, Set<String> seen, CharSequence value) {
        String text = cleanup(value == null ? "" : value.toString());
        if (text.isEmpty() || seen.contains(text) || shouldIgnoreScreenText(text)) {
            return;
        }

        seen.add(text);
        appendReadableText(builder, text);
    }

    private void collectVisibleScreenCandidates(AccessibilityNodeInfo node, List<VisibleScreenFormatter.Candidate> candidates,
                                                Set<String> seen) {
        if (node == null) {
            return;
        }

        if (node.isVisibleToUser()) {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            String className = node.getClassName() == null ? "" : node.getClassName().toString();
            appendVisibleScreenCandidate(candidates, seen, node.getText(), bounds, className,
                    node.isEditable(), node.isClickable(), false);
            appendVisibleScreenCandidate(candidates, seen, node.getContentDescription(), bounds, className,
                    node.isEditable(), node.isClickable(), true);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectVisibleScreenCandidates(child, candidates, seen);
                child.recycle();
            }
        }
    }

    private void appendVisibleScreenCandidate(List<VisibleScreenFormatter.Candidate> candidates, Set<String> seen,
                                              CharSequence value, Rect bounds, String className,
                                              boolean editable, boolean clickable, boolean fromContentDescription) {
        String text = cleanup(value == null ? "" : value.toString());
        if (text.isEmpty() || bounds == null || bounds.isEmpty()) {
            return;
        }
        String key = text.toLowerCase(portugueseBrazil) + "|" + bounds.flattenToString();
        if (seen.contains(key)) {
            return;
        }
        seen.add(key);
        candidates.add(new VisibleScreenFormatter.Candidate(
                text,
                bounds.left,
                bounds.top,
                bounds.right,
                bounds.bottom,
                className,
                editable,
                clickable,
                fromContentDescription
        ));
    }

    private void appendReadableText(StringBuilder builder, CharSequence value) {
        if (value == null || TextUtils.isEmpty(value)) {
            return;
        }

        if (builder.length() > 0) {
            builder.append(". ");
        }
        builder.append(value);
    }

    private boolean shouldIgnoreNotification(String text) {
        String normalized = SpeechTextCleaner.normalize(text);
        return normalized.contains("mensagens novas")
                || normalized.contains("new messages")
                || normalized.contains("checking for new messages")
                || normalized.contains("backup")
                || normalized.contains("web whatsapp");
    }

    private boolean sameMeaning(String left, String right) {
        return normalizeForCompare(left).equals(normalizeForCompare(right));
    }

    private boolean startsWithSameMeaning(String text, String prefix) {
        String normalizedText = normalizeForCompare(text);
        String normalizedPrefix = normalizeForCompare(prefix);
        return !normalizedPrefix.isEmpty() && normalizedText.startsWith(normalizedPrefix);
    }

    private String normalizeForCompare(String text) {
        String cleaned = cleanup(text).toLowerCase(portugueseBrazil);
        String normalized = Normalizer.normalize(cleaned, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}+", "");
    }

    private boolean shouldIgnoreScreenText(String text) {
        String normalized = SpeechTextCleaner.normalize(text);
        return normalized.length() <= 1
                || normalized.matches("\\d{1,2}:\\d{2}\\s*(am|pm)?")
                || normalized.matches("[✓✔]+")
                || normalized.equals("voltar")
                || normalized.equals("mais opcoes")
                || normalized.equals("mais opções")
                || normalized.equals("more options")
                || normalized.equals("pesquisar")
                || normalized.equals("enviar")
                || normalized.equals("menu")
                || normalized.equals("início")
                || normalized.equals("inicio")
                || normalized.equals("home")
                || normalized.equals("abrir")
                || normalized.equals("fechar")
                || normalized.equals("close")
                || normalized.equals("ok")
                || normalized.equals("cancelar")
                || normalized.contains("toque duas vezes")
                || normalized.contains("double tap")
                || normalized.contains("sem notificacoes")
                || normalized.contains("sem notificações")
                || normalized.contains("no notifications");
    }

    private boolean isWhatsAppPackage(String packageName) {
        return "com.whatsapp".equals(packageName)
                || "com.whatsapp.w4b".equals(packageName);
    }

    private boolean isMessagingPackage(String packageName) {
        if (isWhatsAppPackage(packageName)) {
            return true;
        }
        return "org.telegram.messenger".equals(packageName)
                || "org.thunderdog.challegram".equals(packageName)
                || "com.facebook.orca".equals(packageName)
                || "com.instagram.android".equals(packageName)
                || "com.google.android.apps.messaging".equals(packageName)
                || "com.samsung.android.messaging".equals(packageName)
                || "com.discord".equals(packageName)
                || "com.viber.voip".equals(packageName)
                || "jp.naver.line.android".equals(packageName)
                || "com.skype.raider".equals(packageName);
    }

    private List<WhatsAppConversationFormatter.Candidate> collectWhatsAppFormatterCandidates(AccessibilityNodeInfo root) {
        List<TextCandidate> candidates = new ArrayList<>();
        collectWhatsAppCandidates(root, candidates, new HashSet<>());

        List<WhatsAppConversationFormatter.Candidate> formatterCandidates = new ArrayList<>();
        for (TextCandidate candidate : candidates) {
            if (candidate == null || candidate.bounds == null) {
                continue;
            }
            formatterCandidates.add(new WhatsAppConversationFormatter.Candidate(
                    candidate.text,
                    candidate.bounds.left,
                    candidate.bounds.top,
                    candidate.bounds.right,
                    candidate.bounds.bottom,
                    candidate.className,
                    candidate.editable,
                    candidate.clickable,
                    candidate.fromContentDescription
            ));
        }
        return formatterCandidates;
    }

    private String buildWhatsAppConversationText(List<WhatsAppConversationFormatter.Candidate> formatterCandidates) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        return WhatsAppConversationFormatter.format(formatterCandidates, screenWidth, screenHeight);
    }

    private boolean isWhatsAppMessageSentByUser(TextCandidate message, int screenWidth) {
        if (message == null || message.bounds == null || screenWidth <= 0) {
            return false;
        }
        return message.bounds.centerX() > (int) (screenWidth * 0.56f)
                && message.bounds.width() < (int) (screenWidth * 0.9f);
    }

    private void collectWhatsAppCandidates(AccessibilityNodeInfo node, List<TextCandidate> candidates, Set<String> seen) {
        if (node == null) {
            return;
        }

        if (node.isVisibleToUser()) {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            String className = node.getClassName() == null ? "" : node.getClassName().toString();
            boolean editable = node.isEditable();
            boolean clickable = node.isClickable();
            appendWhatsAppCandidate(candidates, seen, node.getText(), bounds, className, editable, clickable, false);
            appendWhatsAppCandidate(candidates, seen, node.getContentDescription(), bounds, className, editable, clickable, true);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectWhatsAppCandidates(child, candidates, seen);
                child.recycle();
            }
        }
    }

    private void appendWhatsAppCandidate(List<TextCandidate> candidates, Set<String> seen, CharSequence value, Rect bounds,
                                         String className, boolean editable, boolean clickable, boolean fromContentDescription) {
        String text = cleanup(value == null ? "" : value.toString());
        if (text.isEmpty() || bounds == null || bounds.isEmpty()) {
            return;
        }
        String key = text.toLowerCase(portugueseBrazil) + "|" + bounds.flattenToString();
        if (seen.contains(key)) {
            return;
        }
        seen.add(key);
        candidates.add(new TextCandidate(text, new Rect(bounds), className, editable, clickable, fromContentDescription));
    }

    private String sanitizeWhatsAppMessage(String text) {
        return cleanup(text)
                .replaceAll("[✓✔]+", "")
                .replaceAll("(?i)\\s*\\b\\d{1,2}:\\d{2}\\b\\s*(lida|enviada|entregue|read|sent|delivered)?\\s*$", "")
                .replaceAll("(?i)\\s*\\b\\d{1,2}:\\d{2}\\s*(am|pm)\\b\\s*$", "")
                .replaceAll("(?i)\\b(lida|enviada|entregue|read|sent|delivered)\\b", "")
                .replaceAll("(?i)^\\s*(mensagem de )?", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private boolean shouldIgnoreWhatsAppText(String text) {
        String normalized = text.toLowerCase(portugueseBrazil);
        return normalized.length() <= 1
                || normalized.matches("\\d{1,2}:\\d{2}\\s*(am|pm)?\\s*(✓✓?)?")
                || normalized.matches("\\d{1,2}/\\d{1,2}/\\d{2,4}")
                || normalized.matches("\\d+ mensagens? n(ova|ovas|ão lida|ão lidas).*")
                || normalized.matches(".*\\b(mensagem|mensagens) não lida(s)?\\b.*")
                || normalized.matches("(?i)(hoje|ontem|today|yesterday)")
                || normalized.matches("(?i)(domingo|segunda-feira|terça-feira|quarta-feira|quinta-feira|sexta-feira|sábado).*")
                || normalized.equals("whatsapp")
                || normalized.equals("voltar")
                || normalized.equals("mais opções")
                || normalized.equals("more options")
                || normalized.equals("pesquisar")
                || normalized.equals("chats")
                || normalized.equals("conversas")
                || normalized.equals("status")
                || normalized.equals("ligações")
                || normalized.equals("calls")
                || normalized.equals("comunidades")
                || normalized.equals("communities")
                || normalized.equals("mensagem")
                || normalized.equals("emoji")
                || normalized.equals("anexar")
                || normalized.equals("enviar")
                || normalized.equals("câmera")
                || normalized.equals("camera")
                || normalized.equals("microfone")
                || normalized.equals("online")
                || normalized.equals("digitando...")
                || normalized.equals("typing...")
                || normalized.contains("digite uma mensagem")
                || normalized.contains("type a message")
                || normalized.contains("chamada de voz")
                || normalized.contains("voice call")
                || normalized.contains("videochamada")
                || normalized.contains("video call")
                || normalized.contains("criptografia")
                || normalized.contains("mensagens e ligações")
                || normalized.contains("toque para mais informações")
                || normalized.contains("tap for more info")
                || normalized.contains("visto por último")
                || normalized.contains("last seen")
                || normalized.contains("silenciado")
                || normalized.contains("muted")
                || normalized.contains("grave uma mensagem")
                || normalized.contains("record voice message")
                || normalized.contains("toque duas vezes")
                || normalized.contains("double tap");
    }

    private boolean isTinyWhatsAppFragment(String text, Rect bounds) {
        return text.length() <= 3 && (bounds.width() < dp(36) || bounds.height() < dp(16));
    }

    private List<TextCandidate> removeContainedMessages(List<TextCandidate> messages) {
        List<TextCandidate> filtered = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            TextCandidate current = messages.get(i);
            String currentText = current.text.toLowerCase(portugueseBrazil);
            boolean containedInLongerNearbyText = false;
            for (int j = 0; j < messages.size(); j++) {
                if (i == j) {
                    continue;
                }
                TextCandidate other = messages.get(j);
                String otherText = other.text.toLowerCase(portugueseBrazil);
                boolean nearby = Math.abs(current.bounds.centerY() - other.bounds.centerY()) < dp(44);
                if (nearby && otherText.length() > currentText.length() + 6 && otherText.contains(currentText)) {
                    containedInLongerNearbyText = true;
                    break;
                }
            }
            if (!containedInLongerNearbyText) {
                filtered.add(current);
            }
        }
        return filtered;
    }

    private static final class TextCandidate {
        final String text;
        final Rect bounds;
        final String className;
        final boolean editable;
        final boolean clickable;
        final boolean fromContentDescription;

        TextCandidate(String text, Rect bounds, String className, boolean editable,
                      boolean clickable, boolean fromContentDescription) {
            this.text = text;
            this.bounds = bounds;
            this.className = className == null ? "" : className;
            this.editable = editable;
            this.clickable = clickable;
            this.fromContentDescription = fromContentDescription;
        }
    }

    private void speakOnce(String text) {
        speakOnce(text, false);
    }

    private void speakOnce(String text, boolean allowRepeat) {
        String normalized = cleanup(text);
        if (normalized.isEmpty() || (!allowRepeat && wasRecentlySpoken(normalized))) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastReadAt < MIN_READ_INTERVAL_MS) {
            return;
        }

        if (normalized.length() > MAX_SPOKEN_CHARS) {
            normalized = normalized.substring(0, MAX_SPOKEN_CHARS);
        }

        remember(normalized);
        lastReadAt = now;
        speak(normalized);
    }

    private boolean wasRecentlySpoken(String text) {
        String key = keyFor(text);
        return recentMessages.contains(key);
    }

    private void remember(String text) {
        recentMessages.addLast(keyFor(text));
        while (recentMessages.size() > RECENT_LIMIT) {
            recentMessages.removeFirst();
        }
    }

    private String keyFor(String text) {
        return cleanup(text).toLowerCase(portugueseBrazil);
    }

    private void speak(String text) {
        lastSpokenText = cleanup(text);
        if (textToSpeech != null) {
            TtsVoiceController.apply(this, textToSpeech);
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "lepramim-accessibility");
        }
        handler.postDelayed(() -> setOverlayColor(Color.rgb(255, 209, 102)), 1200);
    }

    private String friendlyAppName(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return "Aplicativo";
        }
        try {
            PackageManager packageManager = getPackageManager();
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            CharSequence label = packageManager.getApplicationLabel(appInfo);
            if (label != null && label.length() > 0) {
                return label.toString();
            }
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return "Aplicativo";
    }

    private String charSequenceToString(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    private String cleanup(String text) {
        return SpeechTextCleaner.cleanup(text);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        hideOverlayButton();
        if (appVisibilityReceiver != null) {
            try {
                unregisterReceiver(appVisibilityReceiver);
            } catch (RuntimeException ignored) {
            }
            appVisibilityReceiver = null;
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
