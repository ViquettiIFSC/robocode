package robozinho;

import robocode.*;
import static robocode.util.Utils.normalRelativeAngle;

/** Estágio 2: movimento simultâneo, um alvo e mira antecipada simples. */
public class ArenaSurvivorV2 extends AdvancedRobot {
    private int direction = 1;
    private String target;
    private double enemyX, enemyY, enemyHeading, enemyVelocity;
    private long seen;

    @Override
    public void run() {
        // Desacopla os três componentes para procurar e atirar durante o movimento.
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true);
        target = null;
        direction = 1;
        while (true) {
            setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
            setTurnRight(6 * direction); // Movimento em arco; ainda não avalia riscos.
            if (Math.abs(getDistanceRemaining()) < 20) setAhead(160 * direction);
            aimAndFire();
            execute(); // Executa juntas as ordens de corpo, canhão e radar.
        }
    }

    @Override
    public void onScannedRobot(ScannedRobotEvent event) {
        // Mantém o alvo, mas aceita outro 20% mais próximo ou substitui leitura antiga.
        double oldDistance = Math.hypot(enemyX - getX(), enemyY - getY());
        if (target != null && !target.equals(event.getName())
                && getTime() - seen <= 10 && event.getDistance() >= oldDistance * 0.8) return;
        target = event.getName();
        double bearing = getHeadingRadians() + event.getBearingRadians();
        enemyX = getX() + Math.sin(bearing) * event.getDistance();
        enemyY = getY() + Math.cos(bearing) * event.getDistance();
        enemyHeading = event.getHeadingRadians();
        enemyVelocity = event.getVelocity();
        seen = getTime();
    }

    private void aimAndFire() {
        // Evita gastar energia com um alvo perdido ou esgotar nossa reserva.
        if (target == null || getTime() - seen > 10 || getEnergy() <= 1.1) return;
        double range = Math.hypot(enemyX - getX(), enemyY - getY());
        double power = Math.max(0.5, Math.min(2, 400 / Math.max(1, range)));
        power = Math.min(power, (getEnergy() - 1) / 5);
        if (power < Rules.MIN_BULLET_POWER) return;

        // Uma aproximação do tempo de voo: não recalcula a distância prevista.
        double ticks = getTime() - seen + range / Rules.getBulletSpeed(power);
        double px = clamp(enemyX + Math.sin(enemyHeading) * enemyVelocity * ticks,
                18, getBattleFieldWidth() - 18);
        double py = clamp(enemyY + Math.cos(enemyHeading) * enemyVelocity * ticks,
                18, getBattleFieldHeight() - 18);
        double error = normalRelativeAngle(Math.atan2(px - getX(), py - getY())
                - getGunHeadingRadians());
        setTurnGunRightRadians(error);
        // Só dispara se a direção atual do canhão já estiver próxima da mira.
        if (getGunHeat() == 0 && Math.abs(error) < 0.08) setFire(power);
    }

    @Override
    public void onRobotDeath(RobotDeathEvent event) {
        // Libera o alvo morto para escolher outro na próxima leitura.
        if (event.getName().equals(target)) target = null;
    }

    private void reverse() {
        // Dá meia-volta na marcha depois de uma colisão, sem precisar girar o corpo.
        direction = -direction;
        setAhead(160 * direction);
    }

    @Override public void onHitWall(HitWallEvent event) { reverse(); }
    @Override public void onHitRobot(HitRobotEvent event) {
        if (event.isMyFault()) reverse();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
