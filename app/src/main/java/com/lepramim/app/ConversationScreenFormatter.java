package com.lepramim.app;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class ConversationScreenFormatter {
    private static final Locale LOCALE = new Locale("pt", "BR");
    private static final int MAX_RECEIVED_MESSAGES = 4;

    private ConversationScreenFormatter() {
    }

    static String format(String appName, List<VisibleScreenFormatter.Candidate> rawCandidates,
                         int screenWidth, int screenHeight) {
        if (rawCandidates == null || rawCandidates.isEmpty() || screenWidth <= 0 || screenHeight <= 0) {
            return "";
        }

        int topCutoff = Math.max(88, Math.round(screenHeight * 0.13f));
        int bottomCutoff = Math.max(topCutoff + 100, screenHeight - Math.max(110, Math.round(screenHeight * 0.15f)));
        String conversationName = detectConversationName(rawCandidates, topCutoff);

        List<VisibleScreenFormatter.Candidate> candidates = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (VisibleScreenFormatter.Candidate raw : rawCandidates) {
            if (raw == null || raw.isEmpty()) {
                continue;
            }
            String text = sanitize(raw.text);
            String key = normalize(text);
            if (text.isEmpty()
                    || raw.top < topCutoff
                    || raw.bottom > bottomCutoff
                    || raw.isEditableInput()
                    || isTinyFragment(text, raw)
                    || isLikelyIconOrControl(text, raw)
                    || shouldIgnore(text)
                    || seen.contains(key)) {
                continue;
            }
            seen.add(key);
            candidates.add(raw);
        }

        if (candidates.isEmpty()) {
            return "";
        }

        candidates.sort((left, right) -> {
            int top = Integer.compare(left.top, right.top);
            if (top != 0) {
                return top;
            }
            return Integer.compare(left.left, right.left);
        });

        List<ConversationMessageFormatter.Message> received = new ArrayList<>();
        for (VisibleScreenFormatter.Candidate candidate : candidates) {
            if (!isSentByUser(candidate, screenWidth)) {
                received.add(ConversationMessageFormatter.parseMessage(candidate.text, conversationName));
            }
        }

        if (received.isEmpty()) {
            return "";
        }

        int start = Math.max(0, received.size() - MAX_RECEIVED_MESSAGES);
        return ConversationMessageFormatter.format(
                appName,
                conversationName,
                received.subList(start, received.size()),
                ""
        );
    }

    private static String detectConversationName(List<VisibleScreenFormatter.Candidate> rawCandidates, int topCutoff) {
        for (VisibleScreenFormatter.Candidate candidate : rawCandidates) {
            if (candidate == null || candidate.isEmpty()) {
                continue;
            }
            if (candidate.top < 42 || candidate.top > topCutoff) {
                continue;
            }
            String text = sanitize(candidate.text);
            if (text.isEmpty()
                    || candidate.isEditableInput()
                    || isLikelyIconOrControl(text, candidate)
                    || shouldIgnore(text)) {
                continue;
            }
            if (text.length() >= 2 && text.length() <= 42 && !text.matches(".*\\d{2}:\\d{2}.*")) {
                return text;
            }
        }
        return "";
    }

    private static boolean isSentByUser(VisibleScreenFormatter.Candidate candidate, int screenWidth) {
        if (candidate == null || screenWidth <= 0) {
            return false;
        }
        boolean startsOnRightSide = candidate.left > Math.round(screenWidth * 0.25f);
        boolean centeredOnRightSide = candidate.centerX() > Math.round(screenWidth * 0.54f);
        boolean stronglyRightAligned = candidate.right > Math.round(screenWidth * 0.78f)
                && candidate.left > Math.round(screenWidth * 0.12f);
        boolean startsOnLeftEdge = candidate.left < Math.round(screenWidth * 0.12f);
        return !startsOnLeftEdge && (startsOnRightSide || centeredOnRightSide || stronglyRightAligned);
    }

    private static String sanitize(String text) {
        return cleanup(text)
                .replaceAll("[\\u2713\\u2714]+", "")
                .replaceAll("(?i)\\s*\\b\\d{1,2}:\\d{2}\\b\\s*(lida|enviada|entregue|read|sent|delivered)?\\s*$", "")
                .replaceAll("(?i)\\s*\\b\\d{1,2}:\\d{2}\\s*(am|pm)\\b\\s*$", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private static boolean shouldIgnore(String text) {
        String normalized = normalize(text);
        return normalized.length() <= 1
                || normalized.matches("\\d{1,2}:\\d{2}\\s*(am|pm)?")
                || normalized.matches("\\d{1,2}/\\d{1,2}/\\d{2,4}")
                || normalized.matches("(hoje|ontem|today|yesterday)")
                || normalized.matches("(domingo|segunda-feira|terca-feira|quarta-feira|quinta-feira|sexta-feira|sabado).*")
                || normalized.matches("(voltar|mais opcoes|more options|pesquisar|buscar|menu|emoji|anexar|enviar|camera|microfone|audio|video|foto|imagem|gif|sticker|figurinha|play|pausar|baixar)")
                || normalized.contains("digite uma mensagem")
                || normalized.contains("type a message")
                || normalized.contains("toque duas vezes")
                || normalized.contains("double tap");
    }

    private static boolean isLikelyIconOrControl(String text, VisibleScreenFormatter.Candidate candidate) {
        String normalized = normalize(text);
        if (!normalized.matches(".*[a-z0-9].*")) {
            return true;
        }
        boolean iconClass = candidate.isIconLike();
        boolean shortText = normalized.length() <= 28;
        boolean knownIcon = normalized.matches("(foto|imagem|audio|video|emoji|sticker|figurinha|gif|camera|microfone|anexo|arquivo|play|pausar|baixar|reagir|responder|encaminhar|menu|voltar|enviar)");
        return knownIcon || (candidate.fromContentDescription && iconClass && shortText);
    }

    private static boolean isTinyFragment(String text, VisibleScreenFormatter.Candidate candidate) {
        return text.length() <= 3 && (candidate.width() < 42 || candidate.height() < 18);
    }

    private static String normalize(String text) {
        String cleaned = cleanup(text).toLowerCase(LOCALE);
        String normalized = Normalizer.normalize(cleaned, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}+", "");
    }

    private static String cleanup(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }
}
