package projetoRobocode;

import java.util.HashMap;
import java.util.Map;

import robocode.AdvancedRobot;
import robocode.BulletHitEvent;
import robocode.HitByBulletEvent;
import robocode.HitRobotEvent;
import robocode.HitWallEvent;
import robocode.RobotDeathEvent;
import robocode.Rules;
import robocode.ScannedRobotEvent;
import robocode.util.Utils;

public class Scpectre extends AdvancedRobot {
    private static final double MARGEM_PAREDE = 36;
    private static final double DISTANCIA_IDEAL = 350;
    private static final long VALIDADE_RADAR = 12;

    private final Map<String, Inimigo> inimigos = new HashMap<>();
    private int ladoOrbita = 1;
    private long ultimaInversao;
    private long proximaInversao = 60;
    private long fimDaManobra;
    private double rumoDeFuga;
    private Inimigo alvo;

    @Override
    public void run() {
        // Cada parte gira por conta própria.
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setTurnRadarRightRadians(Double.POSITIVE_INFINITY);

        while (true) {
            alvo = escolherAlvo();
            if (getOthers() != 1 || alvo == null || getTime() - alvo.turno > 2
                    || getRadarTurnRemainingRadians() == 0) {
                setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
            }

            mover(alvo);
            if (alvo != null) {
                mirarEDisparar(alvo);
            }
            execute();
        }
    }

    @Override
    public void onScannedRobot(ScannedRobotEvent evento) {
        double angulo = getHeadingRadians() + evento.getBearingRadians();
        Inimigo inimigo = inimigos.computeIfAbsent(evento.getName(), nome -> new Inimigo());
        long intervalo = evento.getTime() - inimigo.turno;
        inimigo.giroPorTurno = 0;

        if (inimigo.turno >= 0 && intervalo > 0 && intervalo <= VALIDADE_RADAR) {
            double variacao = Utils.normalRelativeAngle(evento.getHeadingRadians() - inimigo.rumo);
            inimigo.giroPorTurno = variacao / intervalo;

            // Em duelo, uma queda pequena de energia costuma indicar um disparo.
            double queda = inimigo.energia - evento.getEnergy();
            if (getOthers() == 1 && intervalo <= 2 && queda >= 0.09 && queda <= 3.01) {
                inverterOrbita();
            }
        }

        inimigo.x = getX() + Math.sin(angulo) * evento.getDistance();
        inimigo.y = getY() + Math.cos(angulo) * evento.getDistance();
        inimigo.rumo = evento.getHeadingRadians();
        inimigo.velocidade = evento.getVelocity();
        if (inimigo.turno < 0 || inimigo.velocidade != 0 || intervalo > VALIDADE_RADAR) {
            inimigo.paradoDesde = evento.getTime();
        }
        inimigo.energia = evento.getEnergy();
        inimigo.turno = evento.getTime();

        if (getOthers() == 1) {
            // Passa um pouco do alvo para cruzá-lo de novo na próxima leitura.
            double ajuste = Utils.normalRelativeAngle(angulo - getRadarHeadingRadians());
            setTurnRadarRightRadians(ajuste + Math.copySign(Math.PI / 8, ajuste));
        }
    }

    @Override
    public void onBulletHit(BulletHitEvent evento) {
        Inimigo inimigo = inimigos.get(evento.getName());
        if (inimigo != null) {
            inimigo.energia -= Rules.getBulletDamage(evento.getBullet().getPower());
        }
    }

    @Override
    public void onHitByBullet(HitByBulletEvent evento) {
        Inimigo inimigo = inimigos.get(evento.getName());
        if (inimigo != null) {
            inimigo.energia += Rules.getBulletHitBonus(evento.getPower());
        }
        inverterOrbita();
    }

    @Override
    public void onHitWall(HitWallEvent evento) {
        fugirNaDirecao(Math.atan2(getBattleFieldWidth() / 2 - getX(),
                getBattleFieldHeight() / 2 - getY()));
    }

    @Override
    public void onHitRobot(HitRobotEvent evento) {
        fugirNaDirecao(getHeadingRadians() + evento.getBearingRadians() + Math.PI);
    }

    @Override
    public void onRobotDeath(RobotDeathEvent evento) {
        inimigos.remove(evento.getName());
    }

    private Inimigo escolherAlvo() {
        Inimigo melhor = null;
        double menorDistancia = Double.POSITIVE_INFINITY;
        for (Inimigo inimigo : inimigos.values()) {
            if (getTime() - inimigo.turno > VALIDADE_RADAR) {
                continue;
            }

            // Dá uma vantagem ao alvo atual para evitar trocar de mira a toda hora.
            double distancia = Math.hypot(inimigo.x - getX(), inimigo.y - getY());
            if (inimigo == alvo) {
                distancia -= 45;
            }
            if (distancia < menorDistancia) {
                melhor = inimigo;
                menorDistancia = distancia;
            }
        }
        return melhor;
    }

    private void mover(Inimigo inimigo) {
        double x = getX();
        double y = getY();
        double rumo = Math.atan2(getBattleFieldWidth() / 2 - x, getBattleFieldHeight() / 2 - y);

        if (getTime() < fimDaManobra) {
            rumo = rumoDeFuga;
        } else if (inimigo != null) {
            if (getTime() >= proximaInversao) {
                inverterOrbita();
            }

            double distancia = Math.hypot(inimigo.x - x, inimigo.y - y);
            double anguloDoAlvo = Math.atan2(inimigo.x - x, inimigo.y - y);
            double ajuste = limitar(-0.85, (DISTANCIA_IDEAL - distancia) / 220, 0.85);
            rumo = anguloDoAlvo + ladoOrbita * (Math.PI / 2 + ajuste);
        }

        // Olha alguns passos à frente e curva antes de chegar na borda.
        double alcance = Math.min(140, Math.min(getBattleFieldWidth(), getBattleFieldHeight()) / 3);
        for (int passo = 0; passo < 72; passo++) {
            if (dentroDaArena(x + Math.sin(rumo) * alcance,
                    y + Math.cos(rumo) * alcance, MARGEM_PAREDE)) {
                break;
            }
            rumo += ladoOrbita * Math.PI / 36;
        }

        double giro = Utils.normalRelativeAngle(rumo - getHeadingRadians());
        double distanciaMovimento = 120;
        if (Math.abs(giro) > Math.PI / 2) {
            giro = Utils.normalRelativeAngle(giro + Math.PI);
            distanciaMovimento = -distanciaMovimento;
        }
        setTurnRightRadians(giro);
        setAhead(distanciaMovimento);
    }

    private void inverterOrbita() {
        if (getTime() - ultimaInversao >= 12) {
            ladoOrbita = -ladoOrbita;
            ultimaInversao = getTime();
            proximaInversao = getTime() + 45 + (long) (Math.random() * 31);
        }
    }

    private void fugirNaDirecao(double rumo) {
        rumoDeFuga = rumo;
        fimDaManobra = getTime() + 12;
        inverterOrbita();
    }

    private void mirarEDisparar(Inimigo inimigo) {
        double x = getX();
        double y = getY();
        double distancia = Math.hypot(inimigo.x - x, inimigo.y - y);
        double potencia = limitar(1.0, 550 / Math.max(distancia, 1), Rules.MAX_BULLET_POWER);
        if (inimigo.velocidade == 0 && inimigo.turno - inimigo.paradoDesde >= 6) {
            potencia = Rules.MAX_BULLET_POWER;
        }

        // Poupa energia e evita gastar um tiro forte para finalizar um alvo enfraquecido.
        double potenciaParaFinalizar = Math.min(inimigo.energia / 4, (inimigo.energia + 2) / 6);
        potencia = Math.min(potencia, Math.max(Rules.MIN_BULLET_POWER, potenciaParaFinalizar));
        potencia = Math.min(potencia, getEnergy() / 5);
        if (potencia < Rules.MIN_BULLET_POWER) {
            return;
        }

        double velocidadeBala = Rules.getBulletSpeed(potencia);
        double previstoX = inimigo.x;
        double previstoY = inimigo.y;
        double rumo = inimigo.rumo;
        long idadeDaLeitura = getTime() - inimigo.turno;

        // Avança o adversário turno a turno até a bala conseguir alcançá-lo.
        for (long turno = 0; turno <= idadeDaLeitura
                || (turno - idadeDaLeitura) * velocidadeBala
                < Math.hypot(previstoX - x, previstoY - y); turno++) {
            rumo += inimigo.giroPorTurno;
            previstoX += Math.sin(rumo) * inimigo.velocidade;
            previstoY += Math.cos(rumo) * inimigo.velocidade;
            if (!dentroDaArena(previstoX, previstoY, 18)) {
                previstoX = limitar(18, previstoX, getBattleFieldWidth() - 18);
                previstoY = limitar(18, previstoY, getBattleFieldHeight() - 18);
                break;
            }
        }

        double mira = Math.atan2(previstoX - x, previstoY - y);
        double erro = Utils.normalRelativeAngle(mira - getGunHeadingRadians());
        setTurnGunRightRadians(erro);
        double tolerancia = Math.atan2(10, Math.hypot(previstoX - x, previstoY - y));
        if (getGunHeat() == 0 && Math.abs(erro) <= tolerancia) {
            setFire(potencia);
        }
    }

    private boolean dentroDaArena(double x, double y, double margem) {
        return x >= margem && x <= getBattleFieldWidth() - margem
                && y >= margem && y <= getBattleFieldHeight() - margem;
    }

    private static double limitar(double minimo, double valor, double maximo) {
        return Math.max(minimo, Math.min(valor, maximo));
    }

    private static class Inimigo {
        double x;
        double y;
        double rumo;
        double giroPorTurno;
        double velocidade;
        double energia;
        long paradoDesde;
        long turno = -1;
    }
}
