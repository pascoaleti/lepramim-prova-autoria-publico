package com.lepramim.app;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VisibleScreenFormatterTest {
    @Test
    public void filtersCommonControlsAndKeepsReadableContent() {
        String speech = VisibleScreenFormatter.format(Arrays.asList(
                candidate("10:30", 20, 10, 90, 34),
                candidate("Voltar", 20, 80, 120, 120),
                candidate("Consulta marcada para sexta as 14 horas", 24, 240, 700, 310),
                candidate("Enviar", 580, 1160, 700, 1210),
                candidate("Digite aqui", 40, 1120, 540, 1180, "android.widget.EditText", true)
        ), 720, 1280);

        assertTrue(speech.contains("Consulta marcada"));
        assertFalse(speech.contains("Voltar"));
        assertFalse(speech.contains("Enviar"));
        assertFalse(speech.contains("Digite aqui"));
        assertFalse(speech.contains("10:30"));
    }

    @Test
    public void removesContainedNearbyText() {
        String speech = VisibleScreenFormatter.format(Arrays.asList(
                candidate("R$ 89,90", 40, 300, 220, 340),
                candidate("Boleto vencimento 10/07/2026 valor R$ 89,90", 40, 292, 680, 380)
        ), 720, 1280);

        assertTrue(speech.contains("Boleto vencimento"));
    }

    @Test
    public void ignoresIconContentDescriptions() {
        String speech = VisibleScreenFormatter.format(Arrays.asList(
                new VisibleScreenFormatter.Candidate("camera", 560, 600, 620, 650,
                        "android.widget.ImageButton", false, true, true),
                candidate("Pedido entregue hoje", 40, 300, 680, 360)
        ), 720, 1280);

        assertTrue(speech.contains("Pedido entregue hoje"));
        assertFalse(speech.toLowerCase().contains("camera"));
    }

    private static VisibleScreenFormatter.Candidate candidate(String text, int left, int top, int right, int bottom) {
        return candidate(text, left, top, right, bottom, "android.widget.TextView", false);
    }

    private static VisibleScreenFormatter.Candidate candidate(String text, int left, int top, int right, int bottom,
                                                             String className, boolean editable) {
        return new VisibleScreenFormatter.Candidate(text, left, top, right, bottom, className, editable, false);
    }
}
