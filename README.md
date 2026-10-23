# ArenaSurvivorV2

Robô `AdvancedRobot` para Robocode que combina movimento contínuo, radar livre e
mira antecipada simples. A proposta é sobreviver sem ficar parado e atacar um
alvo recente com baixo custo de cálculo.

## Como ele luta

- O corpo descreve arcos de 6° enquanto avança 160 pixels. Ao tocar uma parede,
  ou causar uma colisão, inverte o sentido.
- Radar, canhão e corpo operam de forma independente. O radar gira sem parar,
  mantendo a busca por robôs mesmo durante o deslocamento e a mira.
- O robô mantém o alvo atual para evitar trocas constantes; só aceita outro se
  estiver mais de 20% mais perto ou se a leitura atual tiver mais de 10 turnos.
- A mira prevê a posição futura usando rumo, velocidade, idade da leitura e
  velocidade da bala. A posição prevista é limitada às bordas da arena.
- A potência do tiro varia aproximadamente entre 0,5 e 2,0 conforme a distância
  e é reduzida quando a energia restante é baixa. O disparo ocorre apenas com o
  canhão frio e apontado para perto do alvo.

## Avaliação de desempenho esperada

| Situação | Tendência | Motivo |
| --- | --- | --- |
| Alvos parados ou lentos | Boa | O radar encontra o alvo continuamente e a mira linear acerta com facilidade. |
| Robôs com movimento regular | Média | A previsão linear acompanha velocidade e rumo, mas não considera mudanças de direção. |
| Robôs que desviam de balas | Baixa a média | O tiro não usa histórico de esquiva, nem ajusta a mira após errar. |
| Luta contra vários robôs | Média | Há uma regra simples de troca de alvo, porém o robô só guarda os dados de um inimigo por vez. |
| Sobrevivência contra robôs avançados | Baixa a média | O arco constante é previsível e não há reação a balas recebidas nem cálculo de rota segura. |

## Pontos fortes

- Usa `AdvancedRobot` e `execute()`, permitindo mover, rastrear e mirar no mesmo turno.
- Evita desperdiçar energia quando não há leitura recente ou quando a reserva está baixa.
- Não aponta para fora da arena ao prever a posição do inimigo.
- O código é curto e adequado como base para estudar radar, alvo único e mira preditiva.

## Limitações e próximos passos

- O movimento não considera a posição dos inimigos, paredes próximas ou balas recebidas.
- A previsão assume que o adversário continuará em linha reta; ela perde precisão quando ele curva, para ou bate na parede.
- A potência máxima de 2,0 preserva energia, mas reduz o dano e o poder de finalização em duelos.
- Em batalhas com vários robôs, guardar uma lista de inimigos e escolher o alvo por distância, energia e risco melhoraria a decisão.

## Validação

O fonte compila para Java 8 com a API do Robocode 1.11.1. A avaliação acima é
derivada do comportamento implementado; antes de comparar pontuações, execute
várias rodadas contra os mesmos adversários e use a média dos resultados.
