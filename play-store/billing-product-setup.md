# Configuração da assinatura - Play Console

## Produtos esperados pelo app

IDs dos produtos:

`lepramim_plus_monthly`

`lepramim_plus_annual`

Esses IDs precisam ser exatamente iguais. Se mudar uma letra, o app não encontra a assinatura.

## Tipo

Assinatura.

## Nomes sugeridos

LePraMim Plus Mensal

LePraMim Plus Anual

## Plano base

Mensal:

- Tipo: renovação automática.
- Período: mensal.
- Preço sugerido no Brasil: R$ 9,90.
- Status: ativo.

Anual:

- Tipo: renovação automática.
- Período: anual.
- Preço sugerido no Brasil: R$ 69,90.
- Status: ativo.

## Benefício exibido ao usuário

Libera o leitor por voz para mensagens, notificações e textos visíveis na tela.

## Observação para teste fechado

Antes de testar compra real, adicione os e-mails como testadores da licença/app na Play Console. Em teste, a Google pode mostrar cartões de teste e compras sem cobrança real para contas configuradas como testadoras.

## Comportamento no app

- Debug/emulador: liberado para facilitar validação.
- Release/Play Store: exige assinatura ativa.
- Botão "Plus mensal": abre o fluxo de compra mensal da Play Store.
- Botão "Plus anual": abre o fluxo de compra anual da Play Store.
- Botão "Restaurar": consulta compras ativas na conta Google.
- Serviço de acessibilidade: não lê mensagens se o release não tiver assinatura ativa.
