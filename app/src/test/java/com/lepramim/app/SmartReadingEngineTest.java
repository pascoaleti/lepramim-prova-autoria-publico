package com.lepramim.app;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SmartReadingEngineTest {
    private final SmartReadingEngine engine = new SmartReadingEngine();

    @Test
    public void explainsBoletoWithValueAndDate() {
        SmartReadingEngine.Result result = engine.analyze(
                "Boleto vencimento 10/07/2026 valor R$ 89,90",
                ReadingMode.EXPLAIN,
                false
        );

        assertTrue(result.speech.contains("boleto"));
        assertTrue(result.speech.contains("R$ 89,90"));
        assertTrue(result.speech.contains("10/07/2026"));
        assertTrue(result.tags.contains("boleto"));
        assertTrue(result.tags.contains("valor"));
        assertTrue(result.tags.contains("data"));
    }

    @Test
    public void detectsVerificationCodeInSafeMode() {
        SmartReadingEngine.Result result = engine.analyze(
                "Seu código de segurança é 482913. Não compartilhe.",
                ReadingMode.EXPLAIN,
                true
        );

        assertTrue(result.sensitive);
        assertTrue(result.speech.contains("Modo seguro"));
        assertTrue(result.tags.contains("codigo"));
    }

    @Test
    public void warnsAboutSuspiciousMoneyAndLinkMessage() {
        SmartReadingEngine.Result result = engine.analyze(
                "Urgente, sua conta será bloqueada. Clique em bit.ly/banco e regularize agora.",
                ReadingMode.RISK_ALERT,
                false
        );

        assertTrue(result.risk);
        assertTrue(result.speech.contains("Atenção"));
        assertTrue(result.tags.contains("golpe"));
        assertTrue(result.tags.contains("link suspeito"));
    }

    @Test
    public void identifiesAppointmentAndTime() {
        SmartReadingEngine.Result result = engine.analyze(
                "Consulta médica marcada para 12/08/2026 às 14:30",
                ReadingMode.IMPORTANT_ONLY,
                false
        );

        assertTrue(result.speech.contains("consulta"));
        assertTrue(result.speech.contains("12/08/2026"));
        assertTrue(result.speech.contains("14:30"));
        assertTrue(result.tags.contains("consulta"));
        assertTrue(result.tags.contains("horario"));
    }

    @Test
    public void readAllDoesNotFlagSimpleMessageAsRisk() {
        SmartReadingEngine.Result result = engine.analyze(
                "Bom dia, chego depois do almoço.",
                ReadingMode.READ_ALL,
                true
        );

        assertFalse(result.risk);
        assertFalse(result.sensitive);
        assertTrue(result.speech.contains("Bom dia"));
    }

    @Test
    public void importantModeReadsSimpleTextWithoutArtificialIntro() {
        SmartReadingEngine.Result result = engine.analyze(
                "Bom dia, chego depois do almoco.",
                ReadingMode.IMPORTANT_ONLY,
                true
        );

        assertTrue(result.speech.startsWith("Bom dia"));
        assertFalse(result.speech.contains("O principal"));
        assertFalse(result.speech.contains("Eu li isto"));
    }
}
