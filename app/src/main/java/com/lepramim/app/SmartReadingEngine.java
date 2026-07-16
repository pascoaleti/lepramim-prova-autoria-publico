package com.lepramim.app;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SmartReadingEngine {
    private static final int MAX_SPOKEN_CHARS = 1200;

    private static final Pattern MONEY_PATTERN = Pattern.compile(
            "(?i)(?:r\\$\\s*)?\\d{1,3}(?:\\.\\d{3})*,\\d{2}|(?:r\\$\\s*)\\d+\\.\\d{2}");
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "\\b(?:\\d{1,2}/\\d{1,2}/\\d{2,4}|\\d{1,2}\\s+de\\s+[a-zç]+\\s+de\\s+\\d{4})\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME_PATTERN = Pattern.compile("\\b\\d{1,2}:\\d{2}\\b");
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?i)(?:\\+55\\s*)?(?:\\(?\\d{2}\\)?\\s*)?9?\\d{4}[-\\s]?\\d{4}");
    private static final Pattern VERIFICATION_CODE_PATTERN = Pattern.compile(
            "(?i)(?:c[oó]digo|token|senha|otp|verifica[cç][aã]o|confirma[cç][aã]o)[^\\d]{0,24}(\\d{4,8})");
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)\\b(?:https?://|www\\.|bit\\.ly|tinyurl|wa\\.me|t\\.me|goo\\.gl|pix\\.)\\S+");

    Result analyze(String text, ReadingMode mode, boolean safeMode) {
        String cleaned = cleanup(text);
        if (cleaned.isEmpty()) {
            return Result.empty();
        }

        Signals signals = detectSignals(cleaned);
        boolean sensitive = signals.hasCode || signals.hasPassword || signals.hasBank || signals.hasPix;
        boolean dangerous = signals.hasScam || signals.hasSuspiciousLink || signals.hasMoneyRequest;

        if (safeMode && sensitive) {
            return new Result(
                    "Modo seguro. Este texto parece ter código, senha, banco ou PIX. Para sua segurança, não vou ler em voz alta agora.",
                    true,
                    dangerous,
                    signals.tags
            );
        }

        if (mode == ReadingMode.RISK_ALERT || dangerous) {
            if (dangerous) {
                return new Result(buildRiskSpeech(signals, cleaned), sensitive, true, signals.tags);
            }
        }

        if (mode == ReadingMode.IMPORTANT_ONLY) {
            return new Result(buildImportantSpeech(signals, cleaned), sensitive, dangerous, signals.tags);
        }

        if (mode == ReadingMode.EXPLAIN) {
            return new Result(buildExplanationSpeech(signals, cleaned), sensitive, dangerous, signals.tags);
        }

        return new Result(limit("Na tela. " + cleaned), sensitive, dangerous, signals.tags);
    }

    Signals detectSignals(String text) {
        String normalized = normalize(text);
        Signals signals = new Signals();

        signals.money = firstMatch(MONEY_PATTERN, text);
        signals.date = firstMatch(DATE_PATTERN, text);
        signals.time = firstMatch(TIME_PATTERN, text);
        signals.phone = firstMatch(PHONE_PATTERN, text);
        signals.code = firstGroupOrMatch(VERIFICATION_CODE_PATTERN, text);
        signals.link = firstMatch(URL_PATTERN, text);

        signals.hasBoleto = hasAny(normalized, "boleto", "vencimento", "linha digitavel", "codigo de barras", "fatura");
        signals.hasAppointment = hasAny(normalized, "consulta", "medico", "dentista", "exame", "agendamento", "marcado");
        signals.hasDelivery = hasAny(normalized, "entrega", "pedido", "rastreamento", "saiu para entrega", "chegara", "chegou");
        signals.hasCode = !signals.code.isEmpty() || hasAny(normalized, "codigo de seguranca", "codigo de verificacao", "token", "otp");
        signals.hasPassword = hasAny(normalized, "senha", "password", "pin", "nao compartilhe");
        signals.hasPix = hasAny(normalized, "pix", "chave pix", "copia e cola");
        signals.hasBank = hasAny(normalized, "banco", "itau", "bradesco", "caixa", "santander", "nubank", "mercado pago", "agibank");
        signals.hasAddress = hasAny(normalized, "rua ", "avenida", "av.", "numero", "bairro", "cep ");
        signals.hasMoneyRequest = hasAny(normalized, "me manda dinheiro", "empresta", "deposito", "transferencia", "pague agora", "regularizar");
        signals.hasSuspiciousLink = !signals.link.isEmpty()
                && hasAny(normalized, "clique", "acesse", "atualize", "desbloquear", "premio", "regularizar", "urgente");
        signals.hasScam = hasAny(normalized,
                "sua conta sera bloqueada",
                "seu cartao foi bloqueado",
                "premio",
                "ganhou",
                "urgente",
                "nao compartilhe com ninguem",
                "confirme seus dados",
                "regularize agora",
                "taxa de entrega",
                "falso motoboy");

        addTags(signals);
        return signals;
    }

    private String buildImportantSpeech(Signals signals, String originalText) {
        List<String> parts = new ArrayList<>();
        if (signals.hasBoleto) {
            parts.add("Isso parece um boleto ou cobrança.");
        }
        if (!signals.money.isEmpty()) {
            parts.add("Valor: " + signals.money + ".");
        }
        if (!signals.date.isEmpty()) {
            parts.add("Data: " + signals.date + ".");
        }
        if (!signals.time.isEmpty()) {
            parts.add("Horário: " + signals.time + ".");
        }
        if (signals.hasAppointment) {
            parts.add("Parece ser consulta ou agendamento.");
        }
        if (signals.hasDelivery) {
            parts.add("Parece ser entrega ou pedido.");
        }
        if (signals.hasPix) {
            parts.add("Fala de PIX.");
        }
        if (signals.hasCode || signals.hasPassword) {
            parts.add("Tem código ou senha. Não fale isso perto de outras pessoas.");
        }
        if (signals.hasSuspiciousLink || signals.hasScam || signals.hasMoneyRequest) {
            parts.add("Atenção: pode ser perigoso.");
        }

        if (parts.isEmpty()) {
            return limit(originalText);
        }
        return limit(join(parts));
    }

    private String buildExplanationSpeech(Signals signals, String originalText) {
        String important = buildImportantSpeech(signals, originalText);
        if (signals.hasBoleto && (!signals.money.isEmpty() || !signals.date.isEmpty())) {
            return limit("Em palavras simples. " + important);
        }
        if (signals.hasAppointment) {
            return limit("Em palavras simples. Isso parece uma consulta ou compromisso. " + important);
        }
        if (signals.hasDelivery) {
            return limit("Em palavras simples. Isso parece uma informação de entrega. " + important);
        }
        if (signals.hasCode || signals.hasPassword) {
            return limit("Em palavras simples. Isso parece uma mensagem de segurança. " + important);
        }
        return limit("Eu li isto. " + originalText + ". Em palavras simples. " + important);
    }

    private String buildRiskSpeech(Signals signals, String originalText) {
        if (signals.hasScam || signals.hasSuspiciousLink || signals.hasMoneyRequest) {
            return "Atenção. Esta mensagem pode ser perigosa porque fala de dinheiro, senha, banco ou link. Não clique e não envie dados antes de confirmar com alguém de confiança.";
        }
        return buildImportantSpeech(signals, originalText);
    }

    private void addTags(Signals signals) {
        if (signals.hasBoleto) signals.tags.add("boleto");
        if (!signals.money.isEmpty()) signals.tags.add("valor");
        if (!signals.date.isEmpty()) signals.tags.add("data");
        if (signals.hasAppointment) signals.tags.add("consulta");
        if (signals.hasDelivery) signals.tags.add("entrega");
        if (signals.hasCode) signals.tags.add("codigo");
        if (signals.hasPassword) signals.tags.add("senha");
        if (signals.hasPix) signals.tags.add("pix");
        if (signals.hasBank) signals.tags.add("banco");
        if (signals.hasSuspiciousLink) signals.tags.add("link suspeito");
        if (signals.hasScam) signals.tags.add("golpe");
        if (signals.hasMoneyRequest) signals.tags.add("pedido de dinheiro");
        if (signals.hasAddress) signals.tags.add("endereco");
        if (!signals.phone.isEmpty()) signals.tags.add("telefone");
        if (!signals.time.isEmpty()) signals.tags.add("horario");
    }

    private boolean hasAny(String normalized, String... needles) {
        for (String needle : needles) {
            if (normalized.contains(normalize(needle))) {
                return true;
            }
        }
        return false;
    }

    private String firstMatch(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group().trim() : "";
    }

    private String firstGroupOrMatch(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return "";
        }
        if (matcher.groupCount() >= 1 && matcher.group(1) != null) {
            return matcher.group(1).trim();
        }
        return matcher.group().trim();
    }

    private String join(List<String> parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(part);
        }
        return builder.toString();
    }

    private String cleanup(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\n', ' ')
                .replace('\t', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalize(String text) {
        String cleaned = cleanup(text).toLowerCase(Locale.US);
        String normalized = Normalizer.normalize(cleaned, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    private String limit(String text) {
        if (text.length() <= MAX_SPOKEN_CHARS) {
            return text;
        }
        return text.substring(0, MAX_SPOKEN_CHARS);
    }

    static final class Result {
        final String speech;
        final boolean sensitive;
        final boolean risk;
        final Set<String> tags;

        Result(String speech, boolean sensitive, boolean risk, Set<String> tags) {
            this.speech = speech;
            this.sensitive = sensitive;
            this.risk = risk;
            this.tags = new LinkedHashSet<>(tags);
        }

        static Result empty() {
            return new Result("", false, false, new LinkedHashSet<>());
        }
    }

    static final class Signals {
        String money = "";
        String date = "";
        String time = "";
        String phone = "";
        String code = "";
        String link = "";
        boolean hasBoleto;
        boolean hasAppointment;
        boolean hasDelivery;
        boolean hasCode;
        boolean hasPassword;
        boolean hasPix;
        boolean hasBank;
        boolean hasAddress;
        boolean hasMoneyRequest;
        boolean hasSuspiciousLink;
        boolean hasScam;
        final Set<String> tags = new LinkedHashSet<>();
    }
}
