# Checklist Play Console - LePraMim

## Arquivo para upload

Use este arquivo na release de teste fechado:

`dist/LePraMim-0.2.0-release.aab`

## Assets

- Icone 512x512: `play-store/icon-512.png`
- Feature graphic 1024x500: `play-store/feature-graphic-1024x500.png`
- Screenshot principal: `play-store/screenshot-home.png`
- Screenshot 1: `play-store/screenshot-01-assinatura.png`
- Screenshot 2: `play-store/screenshot-02-acoes.png`

## Textos

- Ficha da loja: `play-store/store-listing.md`
- Plano ASO/SEO orgânico: `play-store/aso-seo-organic-plan.md`
- Copy HTML para página externa/Google: `play-store/landing-page-seo-copy.html`
- Configuração da assinatura: `play-store/billing-product-setup.md`
- Notas da versao: `play-store/release-notes-closed-test.md`
- Declaracao de acessibilidade: `play-store/accessibility-declaration.md`
- Data Safety: `play-store/data-safety-notes.md`
- Politica de privacidade: `play-store/privacy-policy.md`
- Politica de privacidade em HTML: `play-store/privacy-policy.html`

## Passos na Play Console

1. Criar app: LePraMim.
2. Escolher app gratuito com compras no app/assinatura.
3. Completar ficha da loja com descricao, icone, screenshot e feature graphic.
4. Informar URL publica da politica de privacidade.
5. Preencher App Content, incluindo Data Safety.
6. Declarar uso de AccessibilityService como ferramenta de acessibilidade.
7. Criar assinatura `lepramim_plus_monthly` com plano mensal.
8. Criar trilha de teste fechado.
9. Criar lista de testadores por email.
10. Criar nova release e enviar `dist/LePraMim-0.2.0-release.aab`.
11. Colar as notas da versao.
12. Enviar para revisao.

## Observacao sobre testadores

Se a conta Play Console for pessoal nova, a Google pode exigir pelo menos 12 testadores optados por 14 dias continuos antes de liberar producao.

## Backup essencial

Faca backup destes arquivos:

- `keystore/lepramim-upload-key.jks`
- `keystore.properties`

Sem eles, futuras atualizacoes podem ficar mais dificeis.
