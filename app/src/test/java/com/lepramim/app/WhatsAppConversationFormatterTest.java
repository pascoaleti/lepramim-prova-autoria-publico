package com.lepramim.app;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WhatsAppConversationFormatterTest {
    @Test
    public void readsReceivedMessagesAndSkipsUserMessages() {
        String speech = WhatsAppConversationFormatter.format(Arrays.asList(
                candidate("Oi, tudo bem? 12:10", 24, 260, 440, 320),
                candidate("Eu mandei isso 12:11", 360, 330, 700, 390),
                candidate("Passa aqui quando puder 12:12", 24, 430, 520, 490)
        ), 720, 1280);

        assertTrue(speech.contains("WhatsApp"));
        assertTrue(speech.contains("2 mensagens"));
        assertTrue(speech.contains("Oi, tudo bem?"));
        assertTrue(speech.contains("Passa aqui quando puder"));
        assertFalse(speech.contains("Eu mandei isso"));
        assertFalse(speech.contains("12:"));
    }

    @Test
    public void explainsWhenOnlyUserMessagesAreVisible() {
        String speech = WhatsAppConversationFormatter.format(Collections.singletonList(
                candidate("Mensagem que eu escrevi 16:30", 370, 500, 690, 560)
        ), 720, 1280);

        assertTrue(speech.contains("Não encontrei mensagem recebida"));
        assertFalse(speech.contains("Mensagem que eu escrevi"));
    }

    @Test
    public void ignoresWhatsAppControlsAndLooseTimestamps() {
        String speech = WhatsAppConversationFormatter.format(Arrays.asList(
                candidate("WhatsApp", 20, 60, 180, 100),
                candidate("Hoje", 300, 200, 410, 230),
                candidate("14:33", 320, 300, 380, 330),
                candidate("Digite uma mensagem", 100, 1120, 520, 1160),
                candidate("Cheguei em casa", 24, 520, 420, 580)
        ), 720, 1280);

        assertTrue(speech.contains("Cheguei em casa"));
        assertFalse(speech.contains("Digite uma mensagem"));
        assertFalse(speech.contains("14:33"));
    }

    @Test
    public void warnsWhenReceivedMessageLooksLikeScam() {
        String speech = WhatsAppConversationFormatter.format(Collections.singletonList(
                candidate("Urgente, sua conta foi bloqueada. Clique em bit.ly/banco", 24, 520, 600, 600)
        ), 720, 1280);

        assertTrue(speech.contains("Atenção"));
        assertTrue(speech.toLowerCase().contains("pode"));
    }

    @Test
    public void skipsLongUserMessagesWhenAppWidthIsSmallerThanDeviceWidth() {
        String speech = WhatsAppConversationFormatter.format(Arrays.asList(
                candidate("Mensagem importante que chegou da outra pessoa", 24, 380, 520, 460),
                candidate("Essa foi uma frase longa que eu mesmo escrevi no WhatsApp", 190, 500, 700, 610),
                candidate("Outra resposta minha que nao deve ser falada", 220, 630, 700, 720)
        ), 1080, 1280);

        assertTrue(speech.contains("Mensagem importante que chegou"));
        assertFalse(speech.contains("eu mesmo escrevi"));
        assertFalse(speech.contains("Outra resposta minha"));
    }

    @Test
    public void ignoresTextBeingTypedInMessageBox() {
        WhatsAppConversationFormatter.Candidate typedText =
                new WhatsAppConversationFormatter.Candidate(
                        "texto que eu estou digitando",
                        80,
                        1120,
                        640,
                        1180,
                        "android.widget.EditText",
                        true
                );

        assertTrue(WhatsAppConversationFormatter.hasConversationMarker(Collections.singletonList(typedText)));
        String speech = WhatsAppConversationFormatter.format(Arrays.asList(
                candidate("Mensagem recebida de verdade", 24, 520, 520, 590),
                typedText
        ), 720, 1280);

        assertTrue(speech.contains("Mensagem recebida de verdade"));
        assertFalse(speech.contains("digitando"));
    }

    @Test
    public void summarizesMessagesFromConversationName() {
        String speech = WhatsAppConversationFormatter.format(Arrays.asList(
                candidate("Maria", 120, 90, 320, 130),
                candidate("Oi", 24, 420, 160, 470),
                candidate("Leva o remedio quando vier", 24, 500, 520, 560)
        ), 720, 1280);

        assertTrue(speech.contains("Maria mandou 2 mensagens"));
        assertTrue(speech.contains("Primeira mensagem: Oi"));
        assertTrue(speech.contains("Leva o remedio quando vier"));
    }

    @Test
    public void ignoresIconDescriptionsInsideConversation() {
        WhatsAppConversationFormatter.Candidate cameraIcon =
                new WhatsAppConversationFormatter.Candidate(
                        "camera",
                        560,
                        600,
                        620,
                        650,
                        "android.widget.ImageButton",
                        false,
                        true,
                        true
                );

        String speech = WhatsAppConversationFormatter.format(Arrays.asList(
                candidate("Pode vir agora", 24, 520, 420, 580),
                cameraIcon
        ), 720, 1280);

        assertTrue(speech.contains("Pode vir agora"));
        assertFalse(speech.toLowerCase().contains("camera"));
    }

    @Test
    public void ignoresImageAndVideoDescriptionsInsideConversation() {
        WhatsAppConversationFormatter.Candidate videoPreview =
                new WhatsAppConversationFormatter.Candidate(
                        "Changeman momentos de minha infancia",
                        260,
                        620,
                        690,
                        980,
                        "android.widget.ImageView",
                        false,
                        true,
                        true
                );

        String speech = WhatsAppConversationFormatter.format(Arrays.asList(
                candidate("Nanda", 120, 90, 320, 130),
                videoPreview,
                candidate("Texto recebido importante 18:45", 24, 1020, 560, 1090)
        ), 720, 1280);

        assertTrue(speech.contains("Texto recebido importante"));
        assertFalse(speech.toLowerCase().contains("changeman"));
    }

    @Test
    public void prioritizesLatestTextAfterTodayAndIgnoresMediaAndEmoji() {
        String speech = WhatsAppConversationFormatter.format(Arrays.asList(
                candidate("Nanda", 210, 140, 410, 190),
                candidate("18 de maio de 2026", 370, 250, 650, 290),
                candidate("Primo, feliz aniversario atrasado! Felicidades e muita saude! Sucesso sempre! 😘❤️ 13:20",
                        40, 340, 920, 590),
                candidate("Valeu 👌😀😀😀 14:02", 470, 620, 1040, 720),
                candidate("Hoje", 500, 835, 610, 900),
                candidate("Changeman", 560, 980, 840, 1040),
                candidate("1:13", 370, 1840, 450, 1890),
                candidate("momentos de minha infancia 14:04", 560, 1780, 980, 1930),
                candidate("Sou completamente apaixonada pelos Changeman. Jaspion tb amava. Flashman em terceiro. 15:48",
                        40, 1990, 930, 2160),
                new WhatsAppConversationFormatter.Candidate(
                        "Mensagem",
                        130,
                        2200,
                        720,
                        2280,
                        "android.widget.EditText",
                        true
                )
        ), 1080, 2400);

        assertTrue(speech.contains("Nanda mandou: Sou completamente apaixonada pelos Changeman"));
        assertFalse(speech.contains("feliz aniversario"));
        assertFalse(speech.contains("Valeu"));
        assertFalse(speech.contains("Changeman. Jaspion tb amava. Flashman em terceiro. 15:48"));
        assertFalse(speech.contains("😘"));
        assertFalse(speech.contains("❤️"));
        assertFalse(speech.contains("1:13"));
        assertFalse(speech.toLowerCase().contains("momentos de minha infancia"));
    }

    @Test
    public void stripsEmojiFromReceivedTextInsteadOfSpeakingIt() {
        String speech = WhatsAppConversationFormatter.format(Arrays.asList(
                candidate("Nanda", 210, 140, 410, 190),
                candidate("Hoje", 500, 340, 610, 390),
                candidate("Oi, tudo bem? ❤️😊 18:43", 40, 520, 650, 600),
                new WhatsAppConversationFormatter.Candidate(
                        "Mensagem",
                        130,
                        1120,
                        620,
                        1180,
                        "android.widget.EditText",
                        true
                )
        ), 720, 1280);

        assertTrue(speech.contains("Oi, tudo bem?"));
        assertFalse(speech.contains("❤️"));
        assertFalse(speech.contains("😊"));
    }

    private static WhatsAppConversationFormatter.Candidate candidate(String text, int left, int top, int right, int bottom) {
        return new WhatsAppConversationFormatter.Candidate(text, left, top, right, bottom);
    }
}
