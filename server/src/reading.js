export async function summarizeReading({ text, context = "tela", mode = "simple", geminiApiKey = "" }) {
  const cleanText = cleanup(text);
  if (!cleanText) {
    return {
      ok: false,
      speech: "Nao encontrei texto para resumir.",
      provider: "local"
    };
  }

  if (geminiApiKey) {
    try {
      return {
        ok: true,
        speech: await summarizeWithGemini(cleanText, context, mode, geminiApiKey),
        provider: "gemini"
      };
    } catch (error) {
      console.warn("Gemini summarize failed", error?.message || error);
    }
  }

  return {
    ok: true,
    speech: localReadingSummary(cleanText, context),
    provider: "local"
  };
}

async function summarizeWithGemini(text, context, mode, geminiApiKey) {
  const prompt = [
    "Voce e o LePraMim, um app para pessoas com baixa alfabetizacao.",
    "Explique em portugues do Brasil, com frases curtas, sem termos tecnicos.",
    "Nao leia emojis, botoes, horarios soltos, controles de imagem ou video.",
    "Se for conversa, diga o aplicativo, quem mandou quando aparecer, e resuma as ultimas mensagens.",
    "Se tiver senha, token, codigo de banco ou PIX, avise com cuidado e nao repita o codigo inteiro.",
    "",
    `Contexto: ${context}`,
    `Modo: ${mode}`,
    `Texto visivel: ${text}`
  ].join("\n");

  const result = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-lite:generateContent?key=${geminiApiKey}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        contents: [{ parts: [{ text: prompt }] }],
        generationConfig: {
          temperature: 0.2,
          maxOutputTokens: 180
        }
      })
    }
  );

  if (!result.ok) {
    throw new Error(`Gemini HTTP ${result.status}`);
  }
  const data = await result.json();
  return cleanup(data?.candidates?.[0]?.content?.parts?.[0]?.text || localReadingSummary(text, context));
}

function localReadingSummary(text, context) {
  const lines = cleanup(text)
    .split(/[.\n]+/)
    .map((item) => cleanup(item))
    .filter(Boolean)
    .slice(-3);

  const joined = lines.join(". ");
  if (!joined) {
    return "Nao encontrei texto importante para ler.";
  }

  if (/(senha|token|codigo|c[oó]digo|pix|banco)/i.test(joined)) {
    return `Modo seguro. Esse texto parece ter codigo, senha, banco ou PIX. Confira com cuidado. ${joined}`;
  }
  if (/(whatsapp|telegram|mensagem|notificacao|notifica[cç][aã]o)/i.test(context)) {
    return `Mensagem. ${joined}`;
  }
  return joined;
}

export function cleanup(value) {
  return String(value || "")
    .replace(/\p{Extended_Pictographic}/gu, "")
    .replace(/\s+/g, " ")
    .trim();
}
