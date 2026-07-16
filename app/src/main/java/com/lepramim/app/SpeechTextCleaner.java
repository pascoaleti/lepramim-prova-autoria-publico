package com.lepramim.app;

import java.text.Normalizer;
import java.util.Locale;

final class SpeechTextCleaner {
    private static final Locale PORTUGUESE_BRAZIL = new Locale("pt", "BR");

    private SpeechTextCleaner() {
    }

    static String cleanup(String text) {
        if (text == null) {
            return "";
        }
        return removeEmojiAndSymbols(text)
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    static String normalize(String text) {
        String cleaned = cleanup(text).toLowerCase(PORTUGUESE_BRAZIL);
        String normalized = Normalizer.normalize(cleaned, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}+", "");
    }

    static String stripTrailingTimeStatus(String text) {
        return cleanup(text)
                .replaceAll("[\\u2713\\u2714]+", "")
                .replaceAll("(?i)\\s*\\b\\d{1,2}:\\d{2}\\b\\s*(lida|enviada|entregue|read|sent|delivered)?\\s*$", "")
                .replaceAll("(?i)\\s*\\b\\d{1,2}:\\d{2}\\s*(am|pm)\\b\\s*$", "")
                .replaceAll("(?i)\\b(lida|enviada|entregue|read|sent|delivered)\\b", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    static boolean hasReadableText(String text) {
        return normalize(text).matches(".*[a-z0-9].*");
    }

    static boolean isMediaOrControlLabel(String text) {
        String normalized = normalize(text);
        return normalized.matches("(foto|imagem|audio|video|emoji|sticker|figurinha|gif|camera|microfone|anexo|arquivo|play|pausar|baixar|reagir|responder|encaminhar|menu|voltar|enviar|curtir|coracao)")
                || normalized.contains("reproduzir video")
                || normalized.contains("reproduzir audio")
                || normalized.contains("mensagem de video")
                || normalized.contains("mensagem de voz")
                || normalized.contains("toque duas vezes")
                || normalized.contains("double tap");
    }

    private static String removeEmojiAndSymbols(String text) {
        StringBuilder builder = new StringBuilder();
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (isEmojiOrReactionSymbol(codePoint)) {
                continue;
            }
            builder.appendCodePoint(codePoint);
        }
        return builder.toString();
    }

    private static boolean isEmojiOrReactionSymbol(int codePoint) {
        return (codePoint >= 0x1F000 && codePoint <= 0x1FAFF)
                || (codePoint >= 0x2600 && codePoint <= 0x27BF)
                || codePoint == 0xFE0F
                || codePoint == 0x200D;
    }
}
