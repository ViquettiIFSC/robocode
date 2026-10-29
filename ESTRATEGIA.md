# ArenaSurvivorV2: estratégia para Battle Royale

## Ideia principal

**Procurar espaço seguro, continuar em movimento e atacar oportunidades boas.**

O robô foi feito para o Robocode clássico, em batalhas com vários adversários,
cada um por si. Sobreviver é importante, mas acertar tiros também rende pontos e
recupera energia. Por isso, ele combina defesa e ataque durante toda a rodada.

O código tem **189 linhas**, excluindo comentários e linhas em branco. Usa uma
lista de inimigos, comparação de destinos e previsão de movimento. Não precisa
de aprendizado de máquina nem de um histórico grande de partidas.

## 1. Radar e memória dos inimigos

Enquanto existem vários adversários, o radar gira continuamente para atualizar
a situação da arena. Um `HashMap` guarda os inimigos pelo nome. Cada registro
`Enemy` contém posição, direção, velocidade, energia e turno da última leitura.

Quando resta apenas um inimigo, o radar passa a acompanhar esse adversário,
ultrapassando um pouco sua direção para continuar encontrando-o. Se perder a
leitura, volta a procurar pela arena.

Leituras antigas perdem utilidade: um inimigo sem atualização há mais de 16
turnos não é escolhido como alvo; após 40 turnos, sua posição também deixa de
participar da avaliação de risco. Inimigos mortos são removidos imediatamente.

## 2. Movimento: escolher o destino de menor risco

O robô compara **32 pontos ao seu redor**. O raio varia entre 160 e 240 pixels,
limitado a um terço do menor lado da arena. A orientação dos pontos também varia,
para não repetir sempre a mesma trajetória.

Ele descarta destinos a menos de 50 pixels das paredes e calcula uma nota de
perigo para os demais. **Quanto menor a nota, melhor o destino.**

| Fator | Como influencia a escolha |
| --- | --- |
| Paredes | Quanto mais perto, maior a penalidade. |
| Todos os inimigos conhecidos | Inimigos próximos aumentam bastante o risco. |
| Energia dos inimigos | Um inimigo forte em relação ao nosso robô pesa mais, com um limite. |
| Direção do deslocamento | Mover de lado em relação ao inimigo recebe menos penalidade que ir em sua direção ou fugir em linha reta. |
| Posição anterior | Voltar ao ponto da última escolha recebe uma penalidade. |
| Duelo final | Acrescenta uma preferência por distância intermediária, usando 320 pixels como referência. |

A parte principal do risco dos inimigos é proporcional a:

```text
força do inimigo × exposição do movimento / distância²
```

Isso significa que, mantendo os outros fatores iguais, dobrar a distância divide
essa parte do perigo por quatro. O risco de **todos** os inimigos é somado: o
robô tenta evitar ficar perto de vários adversários ao mesmo tempo.

O cosseno em `riskAt()` só serve para comparar a direção do movimento com a
direção do inimigo. Um movimento lateral tende a ser mais difícil de acertar
com tiros apontados diretamente para a posição atual.

O destino é reavaliado a cada 24 turnos, quando faltam menos de 35 pixels para
chegar, após uma colisão ou quando um inimigo morre. Receber uma bala também
antecipa a escolha, com intervalo mínimo de 12 turnos entre essas reações.

Para chegar ao destino, o robô escolhe avançar ou andar de ré, conforme exigir
menos giro. Reduz a velocidade em curvas mais fechadas. Se nenhum ponto for
válido, tenta seguir para o centro da arena.

## 3. Escolha do alvo

Para cada inimigo com leitura recente, o robô calcula:

```text
nota = distância × (1 + energia do inimigo / 100) × (1 + idade da leitura × 0,03)
```

Escolhe a menor nota. Isso favorece adversários próximos, com pouca energia e
posição conhecida recentemente. O alvo atual recebe um desconto de 20% na nota,
evitando trocar de inimigo por diferenças pequenas.

Exemplo, com leituras da mesma idade e sem alvo anterior:

- Inimigo A: distância 200, energia 100 → nota inicial 400.
- Inimigo B: distância 300, energia 20 → nota inicial 360.

Nesse caso, o robô prefere B, apesar de estar um pouco mais longe. Se A já fosse
o alvo, seu desconto reduziria a nota para 320 e o robô continuaria mirando nele.

## 4. Mira e controle da energia

A mira usa **previsão linear**: supõe que o inimigo continuará com a mesma
direção e velocidade observadas. Simula sua posição turno a turno, incluindo o
tempo desde a última leitura e o tempo de viagem da bala. A previsão para quando
o alvo alcançaria uma parede, em vez de continuar para fora da arena.

No Robocode, zero grau aponta para cima. Por isso, o avanço em X usa seno e o
avanço em Y usa cosseno. `normalRelativeAngle()` transforma diferenças de ângulo
no menor giro necessário.

A potência base é `450 / distância`, limitada entre 0,7 e 2,5. Depois, o robô
reduz esse valor conforme a energia do alvo e sua própria energia. Contra um
alvo quase sem energia, um tiro fraco pode bastar. Com pouca energia própria,
ainda permite tiros mínimos de 0,1, desde que sobrem pelo menos 0,2 de energia.

Ele só dispara quando:

- A leitura do alvo tem no máximo 12 turnos.
- Há energia para um tiro válido.
- O canhão está frio.
- A direção **atual** do canhão está suficientemente próxima da mira prevista.

A tolerância de mira diminui com a distância. Mandar o canhão girar não significa
que ele já terminou o giro; por isso, o robô verifica o alinhamento antes de
disparar.

## 5. Organização do código

| Método | Responsabilidade |
| --- | --- |
| `run()` | Coordena radar, movimento e ataque a cada turno. |
| `onScannedRobot()` | Atualiza os dados de um inimigo. |
| `selectTarget()` | Escolhe em quem mirar. |
| `updateRadar()` | Alterna entre busca geral e acompanhamento no duelo. |
| `chooseDestination()` | Compara os 32 destinos possíveis. |
| `riskAt()` | Dá uma nota de perigo para cada destino. |
| `moveToDestination()` | Escolhe giro, velocidade e avanço ou ré. |
| `aimAndFire()` | Prevê a posição do alvo e decide se pode atirar. |
| Eventos de acerto e colisão | Pedem uma nova avaliação do movimento. |

Ele herda de `AdvancedRobot` para usar comandos `set...`, que são executados
em conjunto por `execute()`. Assim, corpo, canhão e radar podem trabalhar no
mesmo turno. Os três giros são configurados para operar independentemente.

## Como explicar em uma apresentação

> Meu robô usa o radar para guardar informações dos adversários. Ele compara
> 32 destinos e escolhe o que oferece menos perigo, considerando inimigos,
> paredes e direção do movimento. Ao mesmo tempo, procura um alvo próximo e
> vulnerável, prevê onde ele estará quando a bala chegar e só atira se houver
> alinhamento e energia. No duelo final, concentra o radar no último inimigo.

## Limitações

- A mira perde precisão quando o inimigo curva, acelera ou muda de direção.
- O movimento avalia principalmente os destinos; não simula toda a trajetória
  do corpo nem acompanha balas em voo. Uma rota ainda pode cruzar um inimigo.
- Reagir após receber uma bala não garante escapar das próximas.
- A margem das paredes reduz colisões, mas não garante evitá-las em toda curva.
- Os pesos são heurísticas. O desempenho depende dos adversários e da arena;
  nenhum resultado contra os exemplos garante vencer robôs de competição.

## Validação realizada

Testado em **29/10/2026**, no Robocode **1.11.1**, sem interface gráfica, com
classes compiladas para Java 8. A versão final concluiu 42 rodadas:

| Cenário | Rodadas | Vitórias do ArenaSurvivorV2 |
| --- | --- | --- |
| Oito robôs, arena 800 × 600 | 15 | 15 |
| Oito robôs, arena 1200 × 900 | 15 | 14 |
| Duelo contra `sample.Walls` | 5 | 5 |
| Duelo contra `sample.SpinBot` | 5 | 5 |
| Partida iniciando no canto, arena 400 × 400, contra `sample.SittingDuck` | 2 | 2 |

Nas duas baterias com oito robôs, os adversários foram `Walls`, `SpinBot`,
`Crazy`, `Fire`, `RamFire`, `TrackFire` e `Corners`, todos do pacote `sample`.
O ArenaSurvivorV2 terminou em primeiro na pontuação acumulada das duas baterias.

Não houve erros de execução nem turnos pulados reportados. Uma verificação
separada confirmou que, com 0,5 de energia, ele ainda pode disparar uma bala de
potência 0,1 para finalizar um alvo fraco; com 0,25, preserva a reserva e não atira.

O movimento usa aleatoriedade e as posições iniciais das baterias maiores
também variam. Esses resultados descrevem esta execução contra robôs de
exemplo, sem garantir a mesma taxa de vitórias em outras partidas.

## Compilação e execução

No projeto, o fonte fica em `robozinho/ArenaSurvivorV2.java`. A pasta corresponde
ao pacote `robozinho`, como o Java exige. As configurações `.vscode/settings.json`
definem a pasta dos fontes e a biblioteca `C:/robocode/libs/robocode.jar`, tanto
ao abrir `RObo` quanto ao abrir diretamente a pasta `robocode` no VS Code.

O pacote continua sendo `robozinho`. Para usá-lo no Robocode, coloque o fonte
em `C:\robocode\robots\robozinho\ArenaSurvivorV2.java`, compile pelo editor e
selecione `robozinho.ArenaSurvivorV2` na batalha.

Para compilar pelo terminal com um JDK recente e gerar classes para Java 8:

```powershell
javac --release 8 -encoding UTF-8 -cp C:\robocode\libs\robocode.jar -d C:\robocode\robots ArenaSurvivorV2.java
```

Execute o comando na pasta do arquivo. Ajuste o caminho caso sua instalação
do Robocode esteja em outro lugar. O comando acima é uma instrução de instalação;
os testes deste projeto usaram uma cópia isolada do motor.

## Referências da API

- [AdvancedRobot: comandos e execução](https://robocode.sourceforge.io/docs/robocode/robocode/AdvancedRobot.html).
- [Robot: configuração dos giros independentes](https://robocode.sourceforge.io/docs/robocode/robocode/Robot.html).
- [Rules: potência, velocidade das balas e limites](https://robocode.sourceforge.io/docs/robocode/robocode/Rules.html).
