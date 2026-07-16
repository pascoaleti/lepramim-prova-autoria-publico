package com.lepramim.app;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConversationMessageFormatterTest {
    @Test
    public void summarizesMultipleMessagesFromSamePerson() {
        String speech = ConversationMessageFormatter.format("WhatsApp", "Maria", Arrays.asList(
                new ConversationMessageFormatter.Message("", "Oi"),
                new ConversationMessageFormatter.Message("", "Chego daqui a pouco"),
                new ConversationMessageFormatter.Message("", "Me espera")
        ), "");

        assertTrue(speech.contains("WhatsApp"));
        assertTrue(speech.contains("Maria mandou 3 mensagens"));
        assertTrue(speech.contains("Primeira mensagem: Oi"));
        assertTrue(speech.contains("\u00daltima mensagem: Me espera"));
    }

    @Test
    public void organizesMessagesFromDifferentPeople() {
        String speech = ConversationMessageFormatter.format("Telegram", "", Arrays.asList(
                new ConversationMessageFormatter.Message("Ana", "Estou chegando"),
                new ConversationMessageFormatter.Message("Joao", "Pode abrir o portao")
        ), "");

        assertTrue(speech.contains("Telegram"));
        assertTrue(speech.contains("Mensagens de Ana e Joao"));
        assertTrue(speech.contains("Ana: Estou chegando"));
        assertTrue(speech.contains("Joao: Pode abrir o portao"));
    }

    @Test
    public void ignoresIconOnlyMessages() {
        String speech = ConversationMessageFormatter.format("WhatsApp", "Maria", Arrays.asList(
                new ConversationMessageFormatter.Message("", "camera"),
                new ConversationMessageFormatter.Message("", "Pode vir")
        ), "");

        assertTrue(speech.contains("Pode vir"));
        assertFalse(speech.toLowerCase().contains("camera"));
    }

    @Test
    public void notificationMentionsAppAndSender() {
        String speech = ConversationMessageFormatter.format("Notificacao do WhatsApp", "Maria", Arrays.asList(
                new ConversationMessageFormatter.Message("", "Cheguei")
        ), "");

        assertTrue(speech.contains("Notificacao do WhatsApp"));
        assertTrue(speech.contains("Maria mandou: Cheguei"));
    }

    @Test
    public void parsesNotificationLineWithSender() {
        ConversationMessageFormatter.Message message =
                ConversationMessageFormatter.parseMessage("Mensagem de Nanda: Vou chegar mais tarde", "");

        assertTrue(message.sender.equals("Nanda"));
        assertTrue(message.text.equals("Vou chegar mais tarde"));
    }
}
