# Auditoria de producao - LePraMim 0.4.9

Data: 2026-07-15

## Estrutura

- App Android nativo em Java.
- Package name preservado: `com.lepramim.app`.
- Interface principal em `MainActivity`, com Views programaticas.
- Servico de acessibilidade em `LePraMimAccessibilityService`.
- Botao flutuante no proprio `LePraMimAccessibilityService`, usando overlay de acessibilidade.
- OCR com ML Kit em `MainActivity` e `CameraCaptureActivity`.
- TTS em `MainActivity`, `LePraMimAccessibilityService` e `TtsVoiceController`.
- Billing em `BillingManager`, `BillingRepository`, `EntitlementManager`, `EntitlementStore` e `RemoteEntitlementValidator`.

## Correcoes feitas

- Versao aumentada para `versionCode 24` e `versionName 0.4.9`.
- Icone do app atualizado com Adaptive Icon e imagem 512x512 alinhada.
- Plano gratis ajustado para teste real antes de assinar: 12 leituras de tela e 3 leituras por imagem ao dia.
- Manifest endurecido com `allowBackup=false` e `usesCleartextTraffic=false`.
- WhatsApp e leitura geral agora ignoram descricoes de imagem/video quando vierem de `contentDescription`.
- Textos de notificacao e conversa foram polidos para falar app, remetente e mensagem de forma mais clara.

## Verificacoes executadas

- `:app:testDebugUnitTest`: passou.
- `:app:assembleDebug`: passou.
- `:app:bundleRelease`: passou.
- APK debug instalado em emulador Android API 35.
- App abriu na `MainActivity` sem crash.
- Onboarding e tela principal conferidos por screenshot.
- Menu/configuracoes conferidos por screenshot.
- AAB gerado em `dist/LePraMim-0.4.9-release.aab`.

## Pontos de cuidado

- A voz humanizada depende das vozes instaladas no Android. Sem backend de voz neural, o app ainda usa TTS do aparelho.
- A validacao de assinatura por servidor esta preparada, mas `ENTITLEMENT_BASE_URL` ainda esta vazio.
- `BuildConfig.DEBUG` libera Plus apenas em build debug. Release depende da Play Store.
- R8/minify segue desativado porque uma versao anterior quebrou ML Kit. Reativar somente depois de teste em aparelho real.
- Testes reais de acessibilidade em WhatsApp, SMS e Gmail ainda precisam ser repetidos no celular do usuario/testadores.

## Conclusao

Esta versao esta apta para envio ao teste fechado como build 0.4.9. Ela corrige pontos de seguranca, icone, limites do gratis e leitura de midia em conversa, mas a qualidade final de venda ainda deve ser validada em uso real com WhatsApp e notificacoes.
