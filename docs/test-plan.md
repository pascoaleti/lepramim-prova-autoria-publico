# Plano de testes

## Builds

- `:app:assembleDebug`
- `:app:testDebugUnitTest`
- `:app:assembleRelease`
- `:app:bundleRelease`

## Aparelhos e Android

- Android 8, 9, 10, 11, 12, 13, 14 e 15.
- Samsung, Motorola e Xiaomi.
- Fonte normal, grande e gigante.
- Celular sem internet.
- Celular com TTS ausente ou desativado.

## Fluxos principais

1. Primeira abertura mostra onboarding.
2. Escolher "Sou cuidador ou familiar".
3. Abrir acessibilidade pelo app.
4. Ativar LePraMim nas configurações.
5. Voltar e confirmar botão amarelo.
6. Escolher "Vou usar para ouvir".
7. Testar voz.
8. Reabrir guia nas configurações.

## Botão flutuante

- Aparece fora do app.
- Não aparece por cima do próprio LePraMim.
- Toque simples lê a tela.
- Toque duplo repete a última leitura.
- Segurar pressionado para a leitura.
- Arrastar muda a posição.
- Posição persiste ao trocar de app.
- Tamanho pequeno, médio e grande.
- Modo discreto.

## Apps para teste

- WhatsApp.
- WhatsApp Business.
- SMS.
- Gmail.
- Navegador.
- Tela de banco.
- Tela com senha/código.
- Tela com texto pequeno.
- Tela com texto grande.

## WhatsApp

- Conversa com mensagens recebidas e enviadas.
- Confirmar que prioriza mensagens recebidas.
- Não repetir horários, botões e "digite uma mensagem".
- Testar quando só houver mensagens enviadas.

## OCR/câmera/imagem

- Tirar foto de papel.
- Escolher print da galeria.
- Boleto com valor e vencimento.
- Receita/consulta.
- Foto escura.
- Foto cortada.
- Permissão/câmera indisponível.

## Segurança

- Código de verificação.
- Senha.
- PIX.
- Link suspeito.
- Pedido de dinheiro.
- App de banco bloqueado.
- Apagar última leitura.
- Modo seguro ligado/desligado.

## Assinatura

- Produto mensal disponível.
- Produto anual disponível.
- Compra aprovada.
- Compra pendente.
- Compra cancelada.
- Assinatura expirada.
- Restaurar compra.
- Erro de conexão.
- Play Store sem produto liberado.

## Critérios de aceite manual

- App abre sem crash.
- TTS fala em português.
- Leitura básica funciona antes da assinatura.
- Plus não é liberado apenas por botão local.
- Modo seguro evita leitura de dados sensíveis.
- Usuário analfabeto consegue usar com o botão amarelo.
