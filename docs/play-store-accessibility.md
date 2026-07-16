# Declaração de acessibilidade para Play Console

## Por que o app usa AccessibilityService

O LePraMim usa o Serviço de Acessibilidade para identificar textos visíveis na tela e lê-los em voz alta quando o usuário solicita. O objetivo é ajudar pessoas com baixa alfabetização, idosos ou pessoas com dificuldade de leitura a compreender mensagens, avisos, telas de aplicativos e textos do dia a dia.

## Como a leitura acontece

- A leitura principal acontece por ação clara do usuário ao tocar no botão flutuante OUVIR.
- A leitura automática fica desligada por padrão e só deve ser ativada explicitamente nas configurações.
- O app não deve controlar o aparelho, fazer compras, enviar mensagens ou alterar configurações sem consentimento.

## Segurança

- O Modo Seguro fica ligado por padrão.
- O app evita ler em voz alta senhas, códigos bancários, tokens, PIX e apps sensíveis.
- O usuário/cuidador pode desativar permissões no Android a qualquer momento.

## Texto sugerido para Play Console

O LePraMim usa o Serviço de Acessibilidade para identificar textos visíveis na tela e lê-los em voz alta quando o usuário solicita. O objetivo é ajudar pessoas com baixa alfabetização, idosos ou pessoas com dificuldade de leitura a compreender mensagens, avisos, telas de aplicativos e textos do dia a dia. O app não usa o serviço para controlar o dispositivo sem consentimento, não coleta senhas intencionalmente e oferece Modo Seguro para evitar leitura de códigos ou informações sensíveis.
