package com.lepramim.app;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class VisibleScreenFormatter {
    private static final Locale LOCALE = new Locale("pt", "BR");
    private static final int MAX_ITEMS = 10;
    private static final int MAX_CHARS = 950;

    private VisibleScreenFormatter() {
    }

    static String format(List<Candidate> rawCandidates, int screenWidth, int screenHeight) {
        if (rawCandidates == null || rawCandidates.isEmpty() || screenWidth <= 0 || screenHeight <= 0) {
            return "";
        }

        int topCutoff = Math.max(28, Math.round(screenHeight * 0.035f));
        int bottomCutoff = screenHeight - Math.max(48, Math.round(screenHeight * 0.06f));

        List<Candidate> candidates = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Candidate raw : rawCandidates) {
            if (raw == null || raw.isEmpty()) {
                continue;
            }
            String text = cleanup(raw.text);
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

        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (Candidate candidate : candidates) {
            if (count >= MAX_ITEMS || builder.length() >= MAX_CHARS) {
                break;
            }
            if (count > 0) {
                builder.append(". ");
            }
            builder.append(candidate.text);
            count++;
        }
        return limit(cleanup(builder.toString()));
    }

    private static List<Candidate> removeContained(List<Candidate> candidates) {
        List<Candidate> filtered = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            Candidate current = candidates.get(i);
            String currentText = normalize(current.text);
            boolean contained = false;
            for (int j = 0; j < candidates.size(); j++) {
                if (i == j) {
                    continue;
                }
                Candidate other = candidates.get(j);
                String otherText = normalize(other.text);
                boolean nearby = Math.abs(current.centerY() - other.centerY()) < 54;
                if (nearby && otherText.length() > currentText.length() + 8 && otherText.contains(currentText)) {
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

    private static boolean shouldIgnore(String text) {
        String normalized = normalize(text);
        return normalized.length() <= 1
                || normalized.matches("\\d{1,2}:\\d{2}\\s*(am|pm)?")
                || normalized.matches("\\d{1,3}%")
                || normalized.matches("\\d+")
                || normalized.equals("voltar")
                || normalized.equals("mais opcoes")
                || normalized.equals("more options")
                || normalized.equals("menu")
                || normalized.equals("inicio")
                || normalized.equals("home")
                || normalized.equals("abrir")
                || normalized.equals("fechar")
                || normalized.equals("close")
                || normalized.equals("ok")
                || normalized.equals("cancelar")
                || normalized.equals("enviar")
                || normalized.equals("pesquisar")
                || normalized.equals("buscar")
                || normalized.equals("compartilhar")
                || normalized.equals("curtir")
                || normalized.equals("seguir")
                || normalized.equals("proximo")
                || normalized.equals("anterior")
                || normalized.equals("foto")
                || normalized.equals("imagem")
                || normalized.equals("audio")
                || normalized.equals("video")
                || normalized.equals("emoji")
                || normalized.equals("sticker")
                || normalized.equals("figurinha")
                || normalized.equals("gif")
                || normalized.equals("camera")
                || normalized.equals("microfone")
                || normalized.equals("anexo")
                || normalized.equals("arquivo")
                || normalized.equals("play")
                || normalized.equals("pausar")
                || normalized.equals("baixar")
                || normalized.equals("reagir")
                || normalized.equals("responder")
                || normalized.equals("encaminhar")
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
        boolean knownIcon = normalized.matches("(foto|imagem|audio|video|emoji|sticker|figurinha|gif|camera|microfone|anexo|arquivo|play|pausar|baixar|reagir|responder|encaminhar|menu|voltar|enviar)");
        return knownIcon
                || (candidate.fromContentDescription && candidate.isImageLike())
                || (candidate.fromContentDescription && iconClass && shortText);
    }

    private static boolean isTinyFragment(String text, Candidate candidate) {
        return text.length() <= 3 && (candidate.width() < 42 || candidate.height() < 18);
    }

    private static String limit(String text) {
        if (text.length() <= MAX_CHARS) {
            return text;
        }
        return text.substring(0, MAX_CHARS).trim();
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

        Candidate(String text, int left, int top, int right, int bottom, String className, boolean editable, boolean clickable) {
            this(text, left, top, right, bottom, className, editable, clickable, false);
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
