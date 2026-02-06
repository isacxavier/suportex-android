# Referência de input remoto (Android)

## Contexto
Encontramos um bug crítico onde o toque remoto estava desalinhado mesmo com a normalização correta na Web.
O problema não era a lógica 0..1 → pixel, e sim o **retângulo de referência** usado no Android.

O erro acontecia porque o cálculo de input estava usando um retângulo reduzido (downscaled),
que era adequado apenas para renderização/vídeo. Resultado: o Android “acertava” o cálculo
em cima de uma base errada, dando a impressão de que todo o pipeline estava quebrado.

## Regra de ouro (obrigatória)
**Renderização pode ser escalada. Input nunca.**

## O que deve ser sempre verdade
- A Web envia **coordenadas normalizadas (0..1)**.
- O Android converte usando **window bounds reais** (área real onde o gesto é injetado).
- **Scale/downscale é só para vídeo** (encoder/stream), jamais para o cálculo de input.

## Checklist rápido para evitar regressão
- [ ] O input usa bounds reais da janela (não `scaleResolutionDownBy`/`scaleDown`).
- [ ] O mapeamento 0..1 → pixel é feito **após** obter a área real.
- [ ] Mudança de resolução, FPS, UI size ou aspect ratio **não afeta o input**.

## Debug recomendado (opcional)
Se voltar a parecer “impossível de achar”, habilitar:
- logs de sanidade mostrando `bounds reais` + `coords normalizadas` + `px final`;
- overlay visual opcional desenhando o ponto remoto.
