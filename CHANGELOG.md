# Changelog

## 0.5.2

- Atualiza o app para `versionCode 27` e `versionName 0.5.2`.
- Transforma o backend em servico Node modular com endpoints de saldo gratis, consumo, assinatura e resumo.
- Adiciona persistencia atomica em arquivo para leituras gratis por aparelho.
- Adiciona `GET /health`, `POST /v1/free-usage/status` e melhora `POST /v1/free-usage/consume`.
- Adiciona seguranca basica no backend com `helmet`, rate limit e limite de payload.
- Valida assinatura da Google Play sem salvar o token bruto, apenas hash SHA-256.
- Adiciona teste automatico do backend provando bloqueio depois do limite gratis.
- Permite configurar a URL do backend no build por `LEPRAMIM_BACKEND_BASE_URL` ou `-PlepramimBackendBaseUrl`.
- App passa a consultar e cachear o saldo remoto para nao depender apenas do contador local.
- Melhora o botao flutuante com seta maior, mira central visivel, borda azul e feedback ao arrastar.
- Leitura por arraste passa a usar a mira central do botao para facilitar posicionar sobre o texto.
- Evita bloquear assinante legitimo quando o backend estiver temporariamente sem validacao Google Play.

## 0.5.1

- Atualiza o app para `versionCode 26` e `versionName 0.5.1`.
- Troca o foreground do launcher por vetor menor e centralizado para evitar corte na mascara do Android.
- Regenera os PNGs legados e o icone 512 da Play Store com margem segura.
- Prepara controle remoto de leituras gratis por aparelho usando backend em HTTPS.
- Adiciona endpoint `/v1/free-usage/consume` no servidor para impedir reset do limite ao desinstalar e reinstalar.

## 0.5.0

- Atualiza o app para `versionCode 25` e `versionName 0.5.0`.
- Troca o botao flutuante para uma seta/ponteiro grande: arraste ate o texto e solte para ouvir so aquele ponto.
- Mantem toque simples para ler a tela, toque duplo para repetir e segurar para parar.
- Corrige o icone launcher com foreground em area segura para nao cortar no celular.
- Melhora a fala de conversa para limitar e organizar as ultimas mensagens recebidas.
- Adiciona limpeza comum de emoji, midia e controles antes do texto ir para o TTS.
- Prepara endpoint de resumo por IA no backend, com Gemini via `GEMINI_API_KEY` e fallback local sem chave.

## 0.4.9

- Atualiza o app para `versionCode 24` e `versionName 0.4.9`.
- Aplica o novo icone alinhado em 512x512 e Adaptive Icon com fundo azul.
- Reduz o plano gratis para 12 leituras de tela e 3 leituras de imagem por dia.
- Reforca a leitura de conversas para ignorar descricoes de imagem/video vindas de `contentDescription`.
- Melhora falas de notificacao, WhatsApp e mensagens com app/remetente mais claros.
- Desativa backup do Android e bloqueia trafego HTTP claro no manifesto.
- Valida build, testes unitarios e abertura no emulador Android API 35.

## 0.4.7

- Atualiza o icone instalado do app com a nova logo enviada.
- Usa a nova marca tambem na splash screen e no topo da tela principal.
- Atualiza assets da Play Store com telas verticais novas e feature graphic nova.
- Atualiza copy da Play Store sem prometer Pix, mantendo pagamento via Google Play.

## 0.4.6

- Nova identidade visual premium em azul, verde e branco.
- Nova tela inicial com header em degrade, card de texto para audio, botao central de tocar, card de leitura assistida e barra inferior.
- Novo icone vetorial do app com livro, balao de fala e alto-falante verde.
- Adaptive Icon atualizado para Android 8+.
- Splash screen com logo, nome LePraMim e slogan "Leitura em voz alta".
- Tema ajustado com status bar azul, navigation bar branca, cards grandes e cantos arredondados.

## 0.4.5

- WhatsApp agora prioriza mensagens recebidas depois do ultimo separador de conversa, como "Hoje" ou data.
- Leitura do WhatsApp considera a caixa de digitacao para nao cortar a ultima mensagem visivel no rodape.
- Limita a fala do WhatsApp as 3 ultimas mensagens recebidas de texto.
- Remove emojis e reacoes da fala em conversas e notificacoes.
- Ignora midias, video, imagem, audio, figurinha, camera, play e textos de controles visuais.
- Evita repetir a mesma leitura do WhatsApp quando o usuario toca novamente no botao flutuante.
- Adiciona testes unitarios baseados em conversa real com mensagem antiga, video enviado e ultima mensagem recebida.

## 0.4.4

- Melhora a fala de conversas para soar mais humana e menos robotica.
- WhatsApp agora resume mensagens recebidas por pessoa quando possivel.
- Notificacoes de conversa agora falam primeiro o nome do app e organizam mensagens por remetente.
- Filtra descricoes de icones, botoes, camera, audio, emoji, figurinha, play e controles visuais.
- Adiciona formatacao generica para outros apps de conversa, como Telegram, SMS, Messenger e Instagram.
- Adiciona testes unitarios para resumo de conversa, varios remetentes e filtro de icones.

## 0.4.3

- Melhora leitura do WhatsApp para reconhecer conversa, ignorar texto digitado e priorizar mensagens recebidas.
- Corrige classificacao de mensagens enviadas longas para nao falar como recebidas.
- Melhora leitura generica em outros apps filtrando botoes comuns, campos de digitacao, status e rodape.
- Aumenta teste gratis para 60 leituras de tela e 10 leituras de imagem por dia.
- Remove botoes duplicados de assinatura da tela principal; Plus fica opcional em tela propria.
- Ajusta modo padrao para fala util sem introducoes artificiais em textos comuns.

## 0.4.2

- Hotfix de release: corrige fechamento ao abrir causado pela minificacao do ML Kit.
- Minificacao e reducao de recursos foram desativadas temporariamente no release ate validacao completa das regras R8.

## 0.4.1

- Leitura do WhatsApp refeita para priorizar mensagens recebidas.
- Mensagens enviadas pelo dono do celular nao sao lidas como conteudo principal.
- Horarios, botoes e textos de interface do WhatsApp sao filtrados.
- Notificacoes do WhatsApp falam "Mensagem de..." sem passar pelo explicador generico.
- Voz padrao ajustada para ritmo mais natural.
- Testes unitarios para conversa do WhatsApp.

## 0.4.0

- Onboarding com modo cuidador/familiar e modo usuário.
- Tela de configurações premium para leitura, voz, botão flutuante, segurança, cuidador e Plus.
- Tela LePraMim Plus com mensal, anual, benefícios e restaurar compra.
- Botão flutuante arrastável com posição salva.
- Toque simples para ler, toque duplo para repetir e segurar para parar.
- Leitura inteligente local com detecção de boleto, valor, data, consulta, entrega, código, senha, PIX, banco, link suspeito e golpe.
- Modo Seguro ligado por padrão.
- Preferências de voz, velocidade e tom.
- Camada `BillingRepository` e `EntitlementManager`.
- Backend preparado para validação de assinatura com Google Play Developer API.
- Testes unitários para leitura inteligente e limite do plano grátis.
- Documentação de Play Store, privacidade, acessibilidade, testes e release.
