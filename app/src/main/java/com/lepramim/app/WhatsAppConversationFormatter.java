package com.lepramim.app;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class WhatsAppConversationFormatter {
    private static final Locale LOCALE = new Locale("pt", "BR");
    private static final int MAX_RECEIVED_MESSAGES = 3;

    private WhatsAppConversationFormatter() {
    }

    static String format(List<Candidate> rawCandidates, int screenWidth, int screenHeight) {
        if (rawCandidates == null || rawCandidates.isEmpty() || screenWidth <= 0 || screenHeight <= 0) {
            return "";
        }

        int baseTopCutoff = Math.max(88, Math.round(screenHeight * 0.13f));
        int topCutoff = Math.max(baseTopCutoff, latestConversationSeparatorBottom(rawCandidates, baseTopCutoff));
        int inputTop = detectMessageInputTop(rawCandidates, screenHeight);
        int bottomCutoff = inputTop > 0
                ? inputTop - 8
                : screenHeight - Math.max(70, Math.round(screenHeight * 0.04f));
        bottomCutoff = Math.max(topCutoff + 100, bottomCutoff);
        String conversationName = detectConversationName(rawCandidates, baseTopCutoff);

        List<Candidate> candidates = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Candidate raw : rawCandidates) {
            if (raw == null || raw.isEmpty()) {
                continue;
            }
            String text = sanitize(raw.text);
            String key = keyFor(text);
            if (text.isEmpty()
                    || raw.top < topCutoff
                    || raw.bottom > bottomCutoff
                    || raw.isEditableInput()
                    || isTinyFragment(text, raw)
                    || isLikelyIconOrControl(text, raw)
                    || shouldIgnore(text)
                    || !hasReadableText(text)
                    || seen.contains(key)) {
                continue;
            }
            seen.add(key);
            candidates.add(new Candidate(text, raw.left, raw.top, raw.right, raw.bottom, raw.className,
                    raw.editable, raw.clickable, raw.fromContentDescription));
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
        candidates = removeContained(candidates);
        int effectiveWidth = effectiveScreenWidth(candidates, screenWidth);

        List<ConversationMessageFormatter.Message> received = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (!isSentByUser(candidate, effectiveWidth)) {
                received.add(ConversationMessageFormatter.parseMessage(candidate.text, conversationName));
            }
        }

        if (received.isEmpty()) {
            return "WhatsApp. Não encontrei mensagem recebida visível. Role a conversa até aparecer a mensagem que chegou e toque em ouvir de novo.";
        }

        int start = Math.max(0, received.size() - MAX_RECEIVED_MESSAGES);
        List<ConversationMessageFormatter.Message> spoken = received.subList(start, received.size());
        String speech = ConversationMessageFormatter.format(
                "WhatsApp",
                conversationName,
                spoken,
                "WhatsApp. Não encontrei mensagem recebida visível. Role a conversa até aparecer a mensagem que chegou e toque em ouvir de novo."
        );

        if (looksRisky(speech)) {
            return "Atenção. Essa conversa pode pedir dinheiro, senha ou clique em link. " + speech;
        }
        return speech;
    }

    static boolean hasConversationMarker(List<Candidate> rawCandidates) {
        if (rawCandidates == null) {
            return false;
        }
        for (Candidate candidate : rawCandidates) {
            if (candidate == null) {
                continue;
            }
            String normalized = normalize(candidate.text);
            if (candidate.isEditableInput()
                    || normalized.contains("digite uma mensagem")
                    || normalized.contains("type a message")
                    || normalized.equals("mensagem")
                    || normalized.contains("grave uma mensagem")
                    || normalized.contains("record voice message")) {
                return true;
            }
        }
        return false;
    }

    private static int latestConversationSeparatorBottom(List<Candidate> rawCandidates, int baseTopCutoff) {
        int latest = 0;
        for (Candidate candidate : rawCandidates) {
            if (candidate == null || candidate.isEmpty()) {
                continue;
            }
            String text = sanitize(candidate.text);
            if (!text.isEmpty() && candidate.top >= baseTopCutoff && isConversationSeparator(text)) {
                latest = Math.max(latest, candidate.bottom);
            }
        }
        return latest;
    }

    private static int detectMessageInputTop(List<Candidate> rawCandidates, int screenHeight) {
        int inputTop = 0;
        int lowerHalf = Math.round(screenHeight * 0.55f);
        for (Candidate candidate : rawCandidates) {
            if (candidate == null || candidate.isEmpty() || candidate.top < lowerHalf) {
                continue;
            }
            String normalized = normalize(candidate.text);
            if (candidate.isEditableInput()
                    || normalized.equals("mensagem")
                    || normalized.contains("digite uma mensagem")
                    || normalized.contains("type a message")
                    || normalized.contains("grave uma mensagem")
                    || normalized.contains("record voice message")) {
                if (inputTop == 0 || candidate.top < inputTop) {
                    inputTop = candidate.top;
                }
            }
        }
        return inputTop;
    }

    private static int effectiveScreenWidth(List<Candidate> candidates, int fallbackWidth) {
        int maxRight = 0;
        for (Candidate candidate : candidates) {
            if (candidate != null) {
                maxRight = Math.max(maxRight, candidate.right);
            }
        }
        if (maxRight > 0 && maxRight < Math.round(fallbackWidth * 0.86f)) {
            return maxRight;
        }
        return fallbackWidth;
    }

    private static String detectConversationName(List<Candidate> rawCandidates, int topCutoff) {
        for (Candidate candidate : rawCandidates) {
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
                    || shouldIgnore(text)
                    || normalize(text).equals("whatsapp")) {
                continue;
            }
            if (text.length() >= 2 && text.length() <= 42 && !text.matches(".*\\d{2}:\\d{2}.*")) {
                return text;
            }
        }
        return "";
    }

    private static boolean isSentByUser(Candidate candidate, int screenWidth) {
        String normalized = normalize(candidate.text);
        if (normalized.startsWith("voce:")
                || normalized.startsWith("you:")
                || normalized.contains(" voce enviou ")
                || normalized.contains(" mensagem enviada")
                || normalized.contains(" enviada ")
                || normalized.contains(" entregue ")
                || normalized.contains(" lida ")) {
            return true;
        }

        boolean startsOnRightSide = candidate.left > Math.round(screenWidth * 0.25f);
        boolean centeredOnRightSide = candidate.centerX() > Math.round(screenWidth * 0.54f);
        boolean stronglyRightAligned = candidate.right > Math.round(screenWidth * 0.78f)
                && candidate.left > Math.round(screenWidth * 0.12f);
        boolean startsOnLeftEdge = candidate.left < Math.round(screenWidth * 0.12f);
        return !startsOnLeftEdge && (startsOnRightSide || centeredOnRightSide || stronglyRightAligned);
    }

    private static List<Candidate> removeContained(List<Candidate> candidates) {
        List<Candidate> filtered = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            Candidate current = candidates.get(i);
            String currentText = keyFor(current.text);
            boolean contained = false;
            for (int j = 0; j < candidates.size(); j++) {
                if (i == j) {
                    continue;
                }
                Candidate other = candidates.get(j);
                String otherText = keyFor(other.text);
                boolean nearby = Math.abs(current.centerY() - other.centerY()) < 48;
                if (nearby && otherText.length() > currentText.length() + 6 && otherText.contains(currentText)) {
                    contained = true;
                    break;
                }
            }
            if (!contained) {
                filtered.add(current);
            }
        }
        return filtered;
    }

    private static String sanitize(String text) {
        String cleaned = removeEmojiAndMediaSymbols(cleanup(text))
                .replaceAll("[\\u2713\\u2714]+", "")
                .replaceAll("(?i)\\s*\\b\\d{1,2}:\\d{2}\\b\\s*(lida|enviada|entregue|read|sent|delivered)?\\s*$", "")
                .replaceAll("(?i)\\s*\\b\\d{1,2}:\\d{2}\\s*(am|pm)\\b\\s*$", "")
                .replaceAll("(?i)\\b(read|sent|delivered)\\b", "")
                .replaceAll("\\s{2,}", " ")
                .trim();

        String normalized = normalize(cleaned);
        if (normalized.startsWith("mensagem de ")) {
            int separator = findFirstSeparator(cleaned);
            if (separator > 0 && separator + 1 < cleaned.length()) {
                String sender = cleaned.substring("mensagem de ".length(), separator).trim();
                String message = cleaned.substring(separator + 1).trim();
                if (!sender.isEmpty() && !message.isEmpty()) {
                    cleaned = sender + " disse: " + message;
                }
            }
        }
        if (normalize(cleaned).startsWith("voce:")) {
            cleaned = cleaned.substring(cleaned.indexOf(':') + 1).trim();
        }
        return cleanup(cleaned);
    }

    private static int findFirstSeparator(String text) {
        int colon = text.indexOf(':');
        int comma = text.indexOf(',');
        if (colon < 0) {
            return comma;
        }
        if (comma < 0) {
            return colon;
        }
        return Math.min(colon, comma);
    }

    private static boolean shouldIgnore(String text) {
        String normalized = normalize(text);
        return normalized.length() <= 1
                || normalized.matches("\\d{1,2}:\\d{2}\\s*(am|pm)?")
                || normalized.matches("\\d{1,2}:\\d{2}:\\d{2}")
                || normalized.matches("\\d{1,2}/\\d{1,2}/\\d{2,4}")
                || isConversationSeparator(text)
                || normalized.matches("\\d+ mensagens? n(ova|ovas|ao lida|ao lidas).*")
                || normalized.matches(".*\\b(mensagem|mensagens) nao lida(s)?\\b.*")
                || normalized.equals("whatsapp")
                || normalized.equals("voltar")
                || normalized.equals("mais opcoes")
                || normalized.equals("more options")
                || normalized.equals("pesquisar")
                || normalized.equals("chats")
                || normalized.equals("conversas")
                || normalized.equals("status")
                || normalized.equals("ligacoes")
                || normalized.equals("calls")
                || normalized.equals("comunidades")
                || normalized.equals("communities")
                || normalized.equals("mensagem")
                || normalized.equals("emoji")
                || normalized.equals("anexar")
                || normalized.equals("enviar")
                || normalized.equals("camera")
                || normalized.equals("microfone")
                || normalized.equals("online")
                || normalized.equals("digitando...")
                || normalized.equals("typing...")
                || normalized.equals("foto")
                || normalized.equals("imagem")
                || normalized.equals("audio")
                || normalized.equals("video")
                || normalized.equals("sticker")
                || normalized.equals("figurinha")
                || normalized.equals("gif")
                || normalized.equals("reels")
                || normalized.equals("anexo")
                || normalized.equals("arquivo")
                || normalized.equals("play")
                || normalized.equals("pausar")
                || normalized.equals("baixar")
                || normalized.equals("reagir")
                || normalized.equals("responder")
                || normalized.equals("encaminhar")
                || normalized.contains("video de")
                || normalized.contains("mensagem de video")
                || normalized.contains("foto de")
                || normalized.contains("imagem de")
                || normalized.contains("audio de")
                || normalized.contains("mensagem de voz")
                || normalized.contains("reproduzir video")
                || normalized.contains("reproduzir audio")
                || normalized.contains("tiktok")
                || normalized.contains("digite uma mensagem")
                || normalized.contains("type a message")
                || normalized.contains("chamada de voz")
                || normalized.contains("voice call")
                || normalized.contains("videochamada")
                || normalized.contains("video call")
                || normalized.contains("criptografia")
                || normalized.contains("mensagens e ligacoes")
                || normalized.contains("toque para mais informacoes")
                || normalized.contains("tap for more info")
                || normalized.contains("visto por ultimo")
                || normalized.contains("last seen")
                || normalized.contains("silenciado")
                || normalized.contains("muted")
                || normalized.contains("grave uma mensagem")
                || normalized.contains("record voice message")
                || normalized.contains("toque duas vezes")
                || normalized.contains("double tap");
    }

    private static boolean isLikelyIconOrControl(String text, Candidate candidate) {
        String normalized = normalize(text);
        if (!normalized.matches(".*[a-z0-9].*")) {
            return true;
        }

        boolean iconClass = candidate.isIconLike();
        boolean shortText = normalized.length() <= 28;
        boolean knownIcon = normalized.matches("(foto|imagem|audio|video|emoji|sticker|figurinha|gif|camera|microfone|anexo|arquivo|play|pausar|baixar|reagir|responder|encaminhar|menu|voltar|enviar|coracao|curtir)");
        return knownIcon
                || (candidate.fromContentDescription && candidate.isImageLike())
                || (candidate.fromContentDescription && iconClass && shortText);
    }

    private static boolean isConversationSeparator(String text) {
        String normalized = normalize(text);
        return normalized.matches("(hoje|ontem|today|yesterday)")
                || normalized.matches("\\d{1,2} de [a-z]+ de \\d{4}")
                || normalized.matches("\\d{1,2} de [a-z]+")
                || normalized.matches("(domingo|segunda-feira|terca-feira|quarta-feira|quinta-feira|sexta-feira|sabado).*");
    }

    private static boolean hasReadableText(String text) {
        String normalized = normalize(text);
        return normalized.matches(".*[a-z0-9].*");
    }

    private static String removeEmojiAndMediaSymbols(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (isEmojiOrReactionSymbol(codePoint)) {
                continue;
            }
            builder.appendCodePoint(codePoint);
        }
        return cleanup(builder.toString());
    }

    private static boolean isEmojiOrReactionSymbol(int codePoint) {
        return (codePoint >= 0x1F000 && codePoint <= 0x1FAFF)
                || (codePoint >= 0x2600 && codePoint <= 0x27BF)
                || codePoint == 0xFE0F
                || codePoint == 0x200D;
    }

    private static boolean isTinyFragment(String text, Candidate candidate) {
        return text.length() <= 3 && (candidate.width() < 36 || candidate.height() < 16);
    }

    private static boolean looksRisky(String text) {
        String normalized = normalize(text);
        boolean hasLink = normalized.contains("http://")
                || normalized.contains("https://")
                || normalized.contains("bit.ly")
                || normalized.contains("wa.me/");
        boolean asksSensitive = normalized.contains("senha")
                || normalized.contains("codigo")
                || normalized.contains("token")
                || normalized.contains("pix")
                || normalized.contains("dinheiro")
                || normalized.contains("emprestado")
                || normalized.contains("urgente")
                || normalized.contains("bloqueada")
                || normalized.contains("bloqueado");
        return hasLink && asksSensitive;
    }

    private static String keyFor(String text) {
        return normalize(text);
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

    static final class Candidate {
        final String text;
        final int left;
        final int top;
        final int right;
        final int bottom;
        final String className;
        final boolean editable;
        final boolean clickable;
        final boolean fromContentDescription;

        Candidate(String text, int left, int top, int right, int bottom) {
            this(text, left, top, right, bottom, "", false, false, false);
        }

        Candidate(String text, int left, int top, int right, int bottom, String className, boolean editable) {
            this(text, left, top, right, bottom, className, editable, false, false);
        }

        Candidate(String text, int left, int top, int right, int bottom, String className, boolean editable,
                  boolean clickable, boolean fromContentDescription) {
            this.text = text == null ? "" : text;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.className = className == null ? "" : className;
            this.editable = editable;
            this.clickable = clickable;
            this.fromContentDescription = fromContentDescription;
        }

        boolean isEmpty() {
            return text.trim().isEmpty() || right <= left || bottom <= top;
        }

        int width() {
            return right - left;
        }

        int height() {
            return bottom - top;
        }

        int centerX() {
            return left + width() / 2;
        }

        int centerY() {
            return top + height() / 2;
        }

        boolean isEditableInput() {
            String normalizedClass = className.toLowerCase(Locale.US);
            return editable || normalizedClass.contains("edittext");
        }

        boolean isIconLike() {
            String normalizedClass = className.toLowerCase(Locale.US);
            return clickable
                    || normalizedClass.contains("image")
                    || normalizedClass.contains("button")
                    || normalizedClass.contains("imagebutton");
        }

        boolean isImageLike() {
            String normalizedClass = className.toLowerCase(Locale.US);
            return normalizedClass.contains("image")
                    || normalizedClass.contains("imagebutton");
        }
    }
}
