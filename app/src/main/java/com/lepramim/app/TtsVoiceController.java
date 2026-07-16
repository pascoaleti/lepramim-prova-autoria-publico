package com.lepramim.app;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;

import java.util.Locale;
import java.util.Set;

final class TtsVoiceController {
    private static final Locale PORTUGUESE_BRAZIL = new Locale("pt", "BR");

    private TtsVoiceController() {
    }

    static void apply(Context context, TextToSpeech textToSpeech) {
        if (textToSpeech == null) {
            return;
        }

        int languageResult = textToSpeech.setLanguage(PORTUGUESE_BRAZIL);
        if (languageResult == TextToSpeech.LANG_MISSING_DATA
                || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            textToSpeech.setLanguage(new Locale("pt"));
        }

        Voice bestVoice = chooseBestPortugueseVoice(textToSpeech);
        if (bestVoice != null) {
            textToSpeech.setVoice(bestVoice);
        }
        textToSpeech.setSpeechRate(LePraMimSettings.getSpeechRate(context));
        textToSpeech.setPitch(LePraMimSettings.getSpeechPitch(context));
    }

    static Voice chooseBestPortugueseVoice(TextToSpeech textToSpeech) {
        if (textToSpeech == null) {
            return null;
        }

        Set<Voice> voices = textToSpeech.getVoices();
        if (voices == null || voices.isEmpty()) {
            return null;
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
        return bestVoice;
    }
}
