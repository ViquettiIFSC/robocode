# ArenaSurvivorV2

Robô `AdvancedRobot` para Robocode que combina movimento variável, radar adaptado
ao número de adversários e mira antecipada simples. A proposta é sobreviver sem
ficar parado e atacar um alvo recente com baixo custo de cálculo.

## Como ele luta

- O corpo recebe comandos de giro entre 4° e 9° por turno e de deslocamento
  entre 120 e 260 pixels. A cada 18 a 42 turnos, ou quando faltam menos de 20
  pixels no trecho, sorteia novos valores e tem 50% de chance de inverter a
  marcha. O giro efetivo depende do limite do Robocode para a velocidade atual.
- Ao receber uma bala, inverte a marcha e escolhe outro trecho, respeitando um
  intervalo mínimo de oito turnos entre essas reações. Ao tocar uma parede ou
  causar uma colisão, também inverte e escolhe outro trecho.
- Radar, canhão e corpo operam de forma independente. Contra vários robôs, o
  radar gira sem parar. Em duelos 1×1, varre uma faixa ao redor do alvo; se não
  houver alvo ou a última leitura tiver mais de dois turnos, retoma a busca
  completa até localizar o adversário novamente.
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
| Sobrevivência contra robôs avançados | Baixa a média | O movimento varia e reage a acertos, mas ainda não prevê balas nem calcula rotas seguras; o ganho depende dos adversários e precisa ser medido. |

## Pontos fortes

- Usa `AdvancedRobot` e `execute()`, permitindo mover, rastrear e mirar no mesmo turno.
- Varia a trajetória e reage a tiros recebidos, com intervalo para evitar inversões excessivas.
- Mantém o radar próximo do alvo em duelos e recupera a busca completa quando perde a leitura.
- Evita desperdiçar energia quando não há leitura recente ou quando a reserva está baixa.
- Não aponta para fora da arena ao prever a posição do inimigo.
- O código é curto e adequado como base para estudar radar, alvo único e mira preditiva.

## Limitações e próximos passos

- O movimento ainda não considera a posição dos inimigos nem evita paredes antecipadamente; a reação a tiros só ocorre depois do acerto, sem prever balas em voo.
- A previsão assume que o adversário continuará em linha reta; ela perde precisão quando ele curva, para ou bate na parede.
- A potência máxima de 2,0 preserva energia, mas reduz o dano e o poder de finalização em duelos.
- Em batalhas com vários robôs, guardar uma lista de inimigos e escolher o alvo por distância, energia e risco melhoraria a decisão.

## Validação

O fonte compila para Java 8 com a API do Robocode 1.11.1. A versão atual também
concluiu testes sem interface de duas rodadas contra `sample.Walls` e duas contra
`sample.Walls` e `sample.Fire`, sem erros de execução.

Esses testes verificam a execução, mas não comprovam melhoria de desempenho.
A avaliação acima é derivada do comportamento implementado; antes de comparar
pontuações, execute várias rodadas contra os mesmos adversários e use a média
dos resultados.
