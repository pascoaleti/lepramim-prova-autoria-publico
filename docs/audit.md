# Auditoria técnica inicial

## Estrutura

- Linguagem: Java, Android nativo.
- Package name: `com.lepramim.app`.
- Activity principal: `app/src/main/java/com/lepramim/app/MainActivity.java`.
- Services: `app/src/main/java/com/lepramim/app/LePraMimAccessibilityService.java`.
- AccessibilityService: declarado no `AndroidManifest.xml` com `BIND_ACCESSIBILITY_SERVICE` e configuração em `app/src/main/res/xml/lepramim_accessibility_service.xml`.
- Botão flutuante: criado dentro de `LePraMimAccessibilityService`, usando `TYPE_ACCESSIBILITY_OVERLAY`.
- OCR: ML Kit Text Recognition em `MainActivity`, métodos `recognizeImageUri`, `recognizeBitmap` e `recognizeTextFromImage`.
- TTS: `TextToSpeech` em `MainActivity` e `LePraMimAccessibilityService`; preferências aplicadas por `TtsVoiceController`.
- Billing: `BillingManager` com Google Play Billing, exposto por `BillingRepository`.
- Entitlement/Plus: `EntitlementStore` e `EntitlementManager`.

## Permissões e recursos

- `android.permission.RECORD_AUDIO`: comando por voz.
- `com.android.vending.BILLING`: assinaturas Google Play.
- Feature opcional `android.hardware.camera.any`.
- Queries para WhatsApp, WhatsApp Business, câmera e imagens.

## Arquivos sensíveis ou gerados encontrados

- `keystore.properties`: sensível, ignorado no `.gitignore`.
- `keystore/lepramim-upload-key.jks`: sensível, ignorado no `.gitignore`.
- `app/build`, `build`, `.gradle`: gerados.
- `dist/*.aab`, `dist/*.apk`: gerados para distribuição.
- `test-artifacts/*` e screenshots locais: artefatos de teste.

## Pode alterar com baixo risco

- Textos de interface.
- Telas programáticas em `MainActivity`.
- Heurísticas locais de leitura inteligente.
- Preferências salvas em `SharedPreferences`.
- Documentação e copy da Play Store.

## Precisa de cuidado

- `applicationId`/package name.
- Serviço de acessibilidade e declaração para Play Console.
- Billing e IDs de produto: `lepramim_plus_monthly` e `lepramim_plus_annual`.
- Keystore e assinatura.
- Leitura em apps sensíveis, banco, senha e códigos.
- Otimização R8/minify em release.
