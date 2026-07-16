package com.lepramim.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class ConversationMessageFormatter {
    private static final Locale LOCALE = new Locale("pt", "BR");
    private static final int MAX_SPOKEN_MESSAGES = 3;

    private ConversationMessageFormatter() {
    }

    static String format(String appName, String fallbackSender, List<Message> rawMessages, String emptyFallback) {
        String cleanAppName = cleanup(appName);
        if (cleanAppName.isEmpty()) {
            cleanAppName = "Aplicativo";
        }

        List<Message> messages = cleanMessages(rawMessages, fallbackSender);
        if (messages.isEmpty()) {
            return cleanup(emptyFallback);
        }

        if (messages.size() > MAX_SPOKEN_MESSAGES) {
            messages = messages.subList(messages.size() - MAX_SPOKEN_MESSAGES, messages.size());
        }

        Set<String> senders = nonEmptySenders(messages);
        if (senders.size() == 1) {
            String sender = senders.iterator().next();
            if (messages.size() == 1) {
                return cleanAppName + ". " + sender + " mandou: " + messages.get(0).text;
            }
            return cleanAppName + ". " + sender + " mandou " + messages.size()
                    + " mensagens. " + describeSequence(messages);
        }

        if (senders.size() > 1) {
            return cleanAppName + ". Mensagens de " + joinNames(new ArrayList<>(senders))
                    + ". " + describeBySender(messages);
        }

        if (messages.size() == 1) {
            return cleanAppName + ". Mensagem recebida: " + messages.get(0).text;
        }
        return cleanAppName + ". Encontrei " + messages.size()
                + " mensagens recebidas. " + describeSequence(messages);
    }

    static Message parseMessage(String rawText, String fallbackSender) {
        String text = cleanup(rawText);
        if (text.isEmpty()) {
            return new Message("", "");
        }

        String sender = "";
        String body = text;
        String normalized = normalize(text);
        int saidIndex = normalized.indexOf(" disse: ");
        if (saidIndex > 0) {
            int originalIndex = indexOfIgnoreCase(text, " disse: ");
            sender = cleanup(text.substring(0, originalIndex));
            body = cleanup(text.substring(originalIndex + " disse: ".length()));
        } else if (normalized.startsWith("mensagem de ")) {
            int separator = findFirstSeparator(text);
            if (separator > 0 && separator + 1 < text.length()) {
                String maybeSender = cleanup(text.substring("mensagem de ".length(), separator));
                String maybeBody = cleanup(text.substring(separator + 1));
                if (looksLikeSender(maybeSender) && !maybeBody.isEmpty()) {
                    sender = maybeSender;
                    body = maybeBody;
                }
            }
        } else {
            int colon = text.indexOf(':');
            if (colon > 0 && colon < 42 && colon + 1 < text.length()) {
                String maybeSender = cleanup(text.substring(0, colon));
                String maybeBody = cleanup(text.substring(colon + 1));
                if (looksLikeSender(maybeSender) && !maybeBody.isEmpty()) {
                    sender = maybeSender;
                    body = maybeBody;
                }
            }
        }

        if (sender.isEmpty()) {
            sender = cleanup(fallbackSender);
        }
        if (normalize(sender).equals("voce") || normalize(sender).equals("you")) {
            sender = "";
        }
        return new Message(sender, body);
    }

    private static List<Message> cleanMessages(List<Message> rawMessages, String fallbackSender) {
        List<Message> messages = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (rawMessages == null) {
            return messages;
        }
        for (Message raw : rawMessages) {
            if (raw == null) {
                continue;
            }
            Message parsed = parseMessage(raw.sender.isEmpty() ? raw.text : raw.sender + ": " + raw.text, fallbackSender);
            if (parsed.text.isEmpty() || isLikelyIconOnly(parsed.text)) {
                continue;
            }
            String key = normalize(parsed.sender + "|" + parsed.text);
            if (seen.add(key)) {
                messages.add(parsed);
            }
        }
        return messages;
    }

    private static Set<String> nonEmptySenders(List<Message> messages) {
        Set<String> senders = new LinkedHashSet<>();
        for (Message message : messages) {
            if (!message.sender.isEmpty()) {
                senders.add(message.sender);
            }
        }
        return senders;
    }

    private static String describeSequence(List<Message> messages) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            if (i > 0) {
                builder.append(". ");
            }
            if (i == 0) {
                builder.append("Primeira mensagem: ");
            } else if (i == messages.size() - 1) {
                builder.append("\u00daltima mensagem: ");
            } else {
                builder.append("Depois: ");
            }
            builder.append(messages.get(i).text);
        }
        return builder.toString();
    }

    private static String describeBySender(List<Message> messages) {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (Message message : messages) {
            String sender = message.sender.isEmpty() ? "Mensagem" : message.sender;
            List<String> texts = grouped.get(sender);
            if (texts == null) {
                texts = new ArrayList<>();
                grouped.put(sender, texts);
            }
            texts.add(message.text);
        }

        StringBuilder builder = new StringBuilder();
        int index = 0;
        for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
            if (index > 0) {
                builder.append(". ");
            }
            builder.append(entry.getKey()).append(": ");
            List<String> texts = entry.getValue();
            if (texts.size() == 1) {
                builder.append(texts.get(0));
            } else {
                builder.append(texts.size()).append(" mensagens. ");
                builder.append("\u00daltima: ").append(texts.get(texts.size() - 1));
            }
            index++;
        }
        return builder.toString();
    }

    private static String joinNames(List<String> names) {
        if (names.isEmpty()) {
            return "";
        }
        if (names.size() == 1) {
            return names.get(0);
        }
        if (names.size() == 2) {
            return names.get(0) + " e " + names.get(1);
        }
        return names.get(0) + ", " + names.get(1) + " e mais " + (names.size() - 2);
    }

    private static boolean looksLikeSender(String text) {
        String normalized = normalize(text);
        return text.length() >= 2
                && text.length() <= 42
                && normalized.matches("[a-z0-9 ._+\\-]+")
                && !normalized.startsWith("http")
                && !normalized.contains("www.");
    }

    private static boolean isLikelyIconOnly(String text) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return true;
        }
        if (!normalized.matches(".*[a-z0-9].*")) {
            return true;
        }
        return SpeechTextCleaner.isMediaOrControlLabel(text);
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

    private static int indexOfIgnoreCase(String text, String needle) {
        return text.toLowerCase(LOCALE).indexOf(needle.toLowerCase(LOCALE));
    }

    private static String normalize(String text) {
        return SpeechTextCleaner.normalize(text);
    }

    private static String cleanup(String text) {
        return SpeechTextCleaner.cleanup(text);
    }

    static final class Message {
        final String sender;
        final String text;

        Message(String sender, String text) {
            this.sender = cleanup(sender);
            this.text = cleanup(text);
        }
    }
}
