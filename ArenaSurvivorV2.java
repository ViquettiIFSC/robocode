package robozinho;

import robocode.*;
import static robocode.util.Utils.normalRelativeAngle;

/** Estágio 2: movimento variável, um alvo e mira antecipada simples. */
public class ArenaSurvivorV2 extends AdvancedRobot {
    private int direction = 1;
    private double arc = 6;
    private long nextMoveChange;
    private long lastDodge = -100;
    private String target;
    private double enemyX, enemyY, enemyHeading, enemyVelocity;
    private long lastSeen;

    @Override
    public void run() {
        // Desacopla os três componentes para procurar e atirar durante o movimento.
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true);
        while (true) {
            if (Math.abs(getDistanceRemaining()) < 20 || getTime() >= nextMoveChange) {
                chooseMovement(false);
            }
            setTurnRight(arc * direction);
            updateRadar();
            aimAndFire();
            execute(); // Executa juntas as ordens de corpo, canhão e radar.
        }
    }

    private void updateRadar() {
        // Em batalhas com vários robôs ou sem leitura recente, busca a arena toda.
        if (getOthers() != 1 || target == null || getTime() - lastSeen > 2) {
            setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
            return;
        }

        // No duelo, ultrapassa ligeiramente o alvo para varrer os dois lados dele.
        double distance = Math.hypot(enemyX - getX(), enemyY - getY());
        double bearing = Math.atan2(enemyX - getX(), enemyY - getY());
        double radarTurn = normalRelativeAngle(bearing - getRadarHeadingRadians());
        double overshoot = Math.atan(36 / Math.max(1, distance));
        setTurnRadarRightRadians(radarTurn + Math.copySign(overshoot, radarTurn));
    }

    @Override
    public void onScannedRobot(ScannedRobotEvent event) {
        // Mantém o alvo, mas aceita outro 20% mais próximo ou substitui leitura antiga.
        double oldDistance = Math.hypot(enemyX - getX(), enemyY - getY());
        if (target != null && !target.equals(event.getName())
                && getTime() - lastSeen <= 10 && event.getDistance() >= oldDistance * 0.8) return;
        target = event.getName();
        double bearing = getHeadingRadians() + event.getBearingRadians();
        enemyX = getX() + Math.sin(bearing) * event.getDistance();
        enemyY = getY() + Math.cos(bearing) * event.getDistance();
        enemyHeading = event.getHeadingRadians();
        enemyVelocity = event.getVelocity();
        lastSeen = getTime();
    }

    private void aimAndFire() {
        // Evita gastar energia com um alvo perdido ou esgotar nossa reserva.
        long scanAge = getTime() - lastSeen;
        if (target == null || scanAge > 10) return;
        double distance = Math.hypot(enemyX - getX(), enemyY - getY());
        double power = Math.min(clamp(400 / Math.max(1, distance), 0.5, 2),
                (getEnergy() - 1) / 5);
        if (power < Rules.MIN_BULLET_POWER) return;

        // Uma aproximação do tempo de voo: não recalcula a distância prevista.
        double ticks = scanAge + distance / Rules.getBulletSpeed(power);
        double predictedX = clamp(enemyX + Math.sin(enemyHeading) * enemyVelocity * ticks,
                18, getBattleFieldWidth() - 18);
        double predictedY = clamp(enemyY + Math.cos(enemyHeading) * enemyVelocity * ticks,
                18, getBattleFieldHeight() - 18);
        double gunTurn = normalRelativeAngle(Math.atan2(predictedX - getX(), predictedY - getY())
                - getGunHeadingRadians());
        setTurnGunRightRadians(gunTurn);
        // Só dispara se a direção atual do canhão já estiver próxima da mira.
        if (getGunHeat() == 0 && Math.abs(gunTurn) < 0.08) setFire(power);
    }

    @Override
    public void onRobotDeath(RobotDeathEvent event) {
        // Libera o alvo morto para escolher outro na próxima leitura.
        if (event.getName().equals(target)) target = null;
    }

    private void chooseMovement(boolean forceReverse) {
        // Varia cada trecho para evitar repetir sempre a mesma trajetória.
        if (forceReverse || Math.random() < 0.5) direction = -direction;
        arc = 4 + Math.random() * 5;
        setAhead((120 + Math.random() * 140) * direction);
        nextMoveChange = getTime() + 18 + (long) (Math.random() * 25);
    }

    @Override
    public void onHitByBullet(HitByBulletEvent event) {
        // Evita inverter sem parar quando recebe vários tiros em sequência.
        if (getTime() - lastDodge >= 8) {
            chooseMovement(true);
            lastDodge = getTime();
        }
    }

    @Override public void onHitWall(HitWallEvent event) { chooseMovement(true); }
    @Override public void onHitRobot(HitRobotEvent event) {
        if (event.isMyFault()) chooseMovement(true);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
