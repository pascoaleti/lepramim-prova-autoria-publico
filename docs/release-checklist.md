# Checklist de release

## Antes de gerar AAB

- Confirmar `versionCode` e `versionName`.
- Rodar `:app:testDebugUnitTest`.
- Rodar `:app:assembleDebug`.
- Rodar `:app:assembleRelease`.
- Rodar `:app:bundleRelease`.
- Confirmar que `keystore.properties` e `.jks` não serão enviados ao Git.
- Confirmar que `dist`, `build` e `test-artifacts` estão ignorados.
- Testar onboarding após limpar dados do app.
- Testar acessibilidade ligada/desligada.
- Testar botão flutuante em outro app.
- Testar OCR com foto e print.
- Testar Modo Seguro.
- Testar mensal e anual no ambiente de teste da Play Store.

## Play Console

- Declaração de acessibilidade preenchida.
- Política de privacidade publicada.
- Termos publicados.
- Produtos de assinatura ativos:
  - `lepramim_plus_monthly`
  - `lepramim_plus_annual`
- Teste fechado configurado quando exigido.
- Países liberados conforme estratégia.
- Conteúdo do app e segurança de dados atualizados.
- Screenshots revisados.
- Notas da versão em pt-BR.

## Não fazer

- Não burlar botão desativado da Play Console.
- Não declarar teste inexistente.
- Não inserir credenciais no código.
- Não publicar leitura automática silenciosa sem consentimento.
