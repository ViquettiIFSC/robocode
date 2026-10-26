package robozinho;

import robocode.*;
import robocode.util.Utils;

public class StrafeSurvivor extends AdvancedRobot {
    private static final double LIMIAR_ENERGIA_RECUO = 30.0;
    private static final long TICKS_ALVO_DESATUALIZADO = 8;
    private String nomeAlvo;
    private double posicaoInimigoX, posicaoInimigoY;
    private double direcaoInimigo, velocidadeInimigo;
    private double energiaInimigo = -1;
    private long ultimoAvistamentoAlvo;
    
    // Variáveis que controlam a direção e a distância dos movimentos evasivos.
    private int direcaoMovimento = 1;
    private long ultimaMudancaDirecao = 0;
    private double distanciaMovimento = 180;

    @Override
    public void run() {
        // Desacopla todos os motores
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true);

        while (true) {
            // Se perder o alvo, gira o radar para procurar outro robô.
            if (nomeAlvo == null) {
                setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
            }
            execute();
        }
    }

    @Override
    public void onScannedRobot(ScannedRobotEvent evento) {
        boolean mesmoAlvo = evento.getName().equals(nomeAlvo);
        if (nomeAlvo != null && !nomeAlvo.equals(evento.getName())) {
            long idadeLeitura = getTime() - ultimoAvistamentoAlvo;
            if (idadeLeitura <= TICKS_ALVO_DESATUALIZADO) {
                double xPrevisto = limitar(posicaoInimigoX + Math.sin(direcaoInimigo)
                        * velocidadeInimigo * idadeLeitura, 18, getBattleFieldWidth() - 18);
                double yPrevisto = limitar(posicaoInimigoY + Math.cos(direcaoInimigo)
                        * velocidadeInimigo * idadeLeitura, 18, getBattleFieldHeight() - 18);
                double distanciaAlvoAtual = Math.hypot(xPrevisto - getX(), yPrevisto - getY());
                if (evento.getDistance() >= distanciaAlvoAtual) return;
            }
        }
        if (mesmoAlvo && energiaInimigo >= 0) {
            double quedaEnergia = energiaInimigo - evento.getEnergy();
            if (quedaEnergia >= 0.1 && quedaEnergia <= 3.0) {
                esquivarDisparo();
            }
        }
        nomeAlvo = evento.getName();
        energiaInimigo = evento.getEnergy();
        ultimoAvistamentoAlvo = getTime();

        // 1. Mantém o radar apontado para o alvo.
        double anguloAbsoluto = getHeadingRadians() + evento.getBearingRadians();
        double giroRadar = Utils.normalRelativeAngle(anguloAbsoluto - getRadarHeadingRadians());
        double ajusteRadar = Math.min(Math.atan(36.0 / evento.getDistance()), Math.PI / 4.0);
        setTurnRadarRightRadians(giroRadar + (giroRadar < 0 ? -ajusteRadar : ajusteRadar));

        // Atualiza a posição e o movimento estimados do inimigo.
        posicaoInimigoX = getX() + Math.sin(anguloAbsoluto) * evento.getDistance();
        posicaoInimigoY = getY() + Math.cos(anguloAbsoluto) * evento.getDistance();
        direcaoInimigo = evento.getHeadingRadians();
        velocidadeInimigo = evento.getVelocity();

        // 2. Recuar com pouca energia; caso contrário, mover em órbita.
        if (getEnergy() < LIMIAR_ENERGIA_RECUO) {
            recuarEvadindo(anguloAbsoluto);
        } else {
            moverEmOrbita(anguloAbsoluto, evento.getDistance());
        }

        // 3. Mira e dispara considerando o movimento previsto do alvo.
        mirarEAtirar(evento.getDistance());
    }

    private void moverEmOrbita(double anguloAbsoluto, double distancia) {
        talvezMudarDirecao();

        // Calcula uma trajetória perpendicular ao inimigo e ajusta a distância.
        double anguloDesejado = anguloAbsoluto + (Math.PI / 2);
        if (distancia > 350) {
            anguloDesejado -= 0.35 * direcaoMovimento;
        } else if (distancia < 200) {
            anguloDesejado += 0.35 * direcaoMovimento;
        }

        // Inverte a direção se a próxima posição ficar perto de uma parede.
        double proximaX = getX() + Math.sin(anguloDesejado) * distanciaMovimento * direcaoMovimento;
        double proximaY = getY() + Math.cos(anguloDesejado) * distanciaMovimento * direcaoMovimento;
        
        if (proximaX < 50 || proximaX > getBattleFieldWidth() - 50 ||
                proximaY < 50 || proximaY > getBattleFieldHeight() - 50) {
            direcaoMovimento = -direcaoMovimento;
            ultimaMudancaDirecao = getTime();
        }

        double anguloGiro = Utils.normalRelativeAngle(anguloDesejado - getHeadingRadians());
        
        // Prefere andar de ré quando isso evita uma curva longa.
        if (Math.abs(anguloGiro) > Math.PI / 2) {
            anguloGiro = Utils.normalRelativeAngle(anguloGiro + Math.PI);
            setTurnRightRadians(anguloGiro);
            setAhead(-distanciaMovimento * direcaoMovimento);
        } else {
            setTurnRightRadians(anguloGiro);
            setAhead(distanciaMovimento * direcaoMovimento);
        }
    }

    private void recuarEvadindo(double anguloAbsoluto) {
        talvezMudarDirecao();

        double anguloDesejado = anguloAbsoluto + Math.PI + 0.35 * direcaoMovimento;
        double proximaX = getX() + Math.sin(anguloDesejado) * distanciaMovimento;
        double proximaY = getY() + Math.cos(anguloDesejado) * distanciaMovimento;
        if (proximaX < 50 || proximaX > getBattleFieldWidth() - 50
                || proximaY < 50 || proximaY > getBattleFieldHeight() - 50) {
            anguloDesejado = Math.atan2(getBattleFieldWidth() / 2.0 - getX(),
                    getBattleFieldHeight() / 2.0 - getY());
            direcaoMovimento = -direcaoMovimento;
            ultimaMudancaDirecao = getTime();
        }

        double anguloGiro = Utils.normalRelativeAngle(anguloDesejado - getHeadingRadians());
        if (Math.abs(anguloGiro) > Math.PI / 2) {
            anguloGiro = Utils.normalRelativeAngle(anguloGiro + Math.PI);
            setTurnRightRadians(anguloGiro);
            setAhead(-distanciaMovimento);
        } else {
            setTurnRightRadians(anguloGiro);
            setAhead(distanciaMovimento);
        }
    }

    private void talvezMudarDirecao() {
        long tempoDecorrido = getTime() - ultimaMudancaDirecao;
        if (tempoDecorrido > 50 || (tempoDecorrido > 12 && Math.random() < 0.04)) {
            direcaoMovimento = -direcaoMovimento;
            ultimaMudancaDirecao = getTime();
        }
    }

    private void esquivarDisparo() {
        direcaoMovimento = -direcaoMovimento;
        distanciaMovimento = 120 + Math.random() * 120;
        ultimaMudancaDirecao = getTime();
    }

    private void mirarEAtirar(double distancia) {
        if (getEnergy() <= 1.0) return;

        double potenciaTiro = Math.min(3.0, Math.max(0.5, 380.0 / distancia));
        potenciaTiro = Math.min(potenciaTiro, (getEnergy() - 0.5) / 4);

        // Previsão linear ajustada ao tempo de voo do projétil.
        double velocidadeProjetil = Rules.getBulletSpeed(potenciaTiro);
        double xPrevisto = posicaoInimigoX;
        double yPrevisto = posicaoInimigoY;
        double tempoVoo;

        for (int i = 0; i < 4; i++) {
            tempoVoo = Math.hypot(xPrevisto - getX(), yPrevisto - getY()) / velocidadeProjetil;
            xPrevisto = limitar(posicaoInimigoX + Math.sin(direcaoInimigo)
                    * velocidadeInimigo * tempoVoo, 18, getBattleFieldWidth() - 18);
            yPrevisto = limitar(posicaoInimigoY + Math.cos(direcaoInimigo)
                    * velocidadeInimigo * tempoVoo, 18, getBattleFieldHeight() - 18);
        }

        double anguloMira = Math.atan2(xPrevisto - getX(), yPrevisto - getY());
        double giroCanhao = Utils.normalRelativeAngle(anguloMira - getGunHeadingRadians());

        setTurnGunRightRadians(giroCanhao);

        if (getGunHeat() == 0 && Math.abs(giroCanhao) < 0.06) {
            setFire(potenciaTiro);
        }
    }

    @Override
    public void onRobotDeath(RobotDeathEvent evento) {
        if (evento.getName().equals(nomeAlvo)) {
            nomeAlvo = null;
        }
    }

    @Override
    public void onHitWall(HitWallEvent evento) {
        direcaoMovimento = -direcaoMovimento;
    }

    @Override
    public void onHitByBullet(HitByBulletEvent evento) {
        esquivarDisparo();
    }

    @Override
    public void onHitRobot(HitRobotEvent evento) {
        direcaoMovimento = -direcaoMovimento;
    }

    private static double limitar(double valor, double minimo, double maximo) {
        return Math.max(minimo, Math.min(maximo, valor));
    }
}