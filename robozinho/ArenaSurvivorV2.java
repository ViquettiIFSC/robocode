package robozinho;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import robocode.*;
import static robocode.util.Utils.normalRelativeAngle;

/** Battle Royale: escolher uma rota segura e atacar sem desperdiçar energia. */
public class ArenaSurvivorV2 extends AdvancedRobot {
    private final Map<String, Enemy> enemies = new HashMap<String, Enemy>();
    private Enemy target;
    private double goalX, goalY, previousX, previousY;
    private long nextMove;
    private long lastDodge = -20;
    private static final double WALL_MARGIN = 50;
    private static final int MOVE_INTERVAL = 24;

    private static class Enemy {
        double x, y, heading, velocity, energy;
        long seen;
    }

    @Override
    public void run() {
        setColors(new Color(30, 45, 60), new Color(0, 190, 160), Color.WHITE);
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true);
        goalX = previousX = getX();
        goalY = previousY = getY();
        while (true) {
            selectTarget();
            updateRadar();
            if (getTime() >= nextMove || distance(goalX, goalY) < 35) {
                chooseDestination();
            }
            moveToDestination();
            aimAndFire();
            execute();
        }
    }

    @Override
    public void onScannedRobot(ScannedRobotEvent event) {
        Enemy enemy = enemies.get(event.getName());
        if (enemy == null) {
            enemy = new Enemy();
            enemies.put(event.getName(), enemy);
        }
        double angle = getHeadingRadians() + event.getBearingRadians();
        enemy.x = getX() + Math.sin(angle) * event.getDistance();
        enemy.y = getY() + Math.cos(angle) * event.getDistance();
        enemy.heading = event.getHeadingRadians();
        enemy.velocity = event.getVelocity();
        enemy.energy = event.getEnergy();
        enemy.seen = getTime();
    }

    private void selectTarget() {
        Enemy best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (Enemy enemy : enemies.values()) {
            long age = getTime() - enemy.seen;
            if (age > 16) continue;
            double score = distance(enemy.x, enemy.y) * (1 + enemy.energy / 100);
            score *= 1 + age * 0.03;
            if (enemy == target) score *= 0.8;
            if (score < bestScore) {
                bestScore = score;
                best = enemy;
            }
        }
        target = best;
    }

    private void updateRadar() {
        if (getOthers() > 1 || target == null || getTime() - target.seen > 2) {
            setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
            return;
        }
        double angle = Math.atan2(target.x - getX(), target.y - getY());
        double turn = normalRelativeAngle(angle - getRadarHeadingRadians());
        double extra = Math.atan(40 / Math.max(1, distance(target.x, target.y)));
        setTurnRadarRightRadians(turn + Math.copySign(extra, turn));
    }

    // Compara 32 destinos; não muda de ideia a cada turno.
    private void chooseDestination() {
        double bestRisk = Double.POSITIVE_INFINITY;
        double radius = Math.min(160 + Math.random() * 80,
                Math.min(getBattleFieldWidth(), getBattleFieldHeight()) / 3);
        double offset = Math.random() * Math.PI * 2;
        goalX = getBattleFieldWidth() / 2;
        goalY = getBattleFieldHeight() / 2;
        for (int i = 0; i < 32; i++) {
            double angle = offset + i * Math.PI / 16;
            double x = getX() + Math.sin(angle) * radius;
            double y = getY() + Math.cos(angle) * radius;
            if (!inside(x, y, WALL_MARGIN)) continue;
            double risk = riskAt(x, y, angle);
            if (risk < bestRisk) {
                bestRisk = risk;
                goalX = x;
                goalY = y;
            }
        }
        previousX = getX();
        previousY = getY();
        nextMove = getTime() + MOVE_INTERVAL;
    }

    private double riskAt(double x, double y, double travelAngle) {
        double wall = Math.min(Math.min(x, getBattleFieldWidth() - x),
                Math.min(y, getBattleFieldHeight() - y));
        double risk = 1200 / square(wall);
        risk += 1500 / (square(x - previousX) + square(y - previousY) + 100);
        for (Enemy enemy : enemies.values()) {
            if (getTime() - enemy.seen > 40) continue;
            double d2 = Math.max(900, square(x - enemy.x) + square(y - enemy.y));
            double bearing = Math.atan2(enemy.x - getX(), enemy.y - getY());
            double exposure = 1 + 0.7 * Math.abs(Math.cos(bearing - travelAngle));
            double strength = 1 + Math.min(2, enemy.energy / Math.max(1, getEnergy()));
            risk += 10000 * strength * exposure / d2;
            if (getOthers() == 1) {
                risk += 0.6 * square((Math.sqrt(d2) - 320) / 320);
            }
        }
        return risk;
    }

    // Andar de ré pode exigir menos giro que virar o corpo inteiro.
    private void moveToDestination() {
        double angle = Math.atan2(goalX - getX(), goalY - getY());
        double turn = normalRelativeAngle(angle - getHeadingRadians());
        int direction = 1;
        if (Math.abs(turn) > Math.PI / 2) {
            turn = normalRelativeAngle(turn + Math.PI);
            direction = -1;
        }
        setTurnRightRadians(turn);
        setMaxVelocity(Math.abs(turn) > 0.7 ? 5 : 8);
        setAhead(distance(goalX, goalY) * direction);
    }

    private void aimAndFire() {
        if (target == null || getTime() - target.seen > 12) return;
        double range = distance(target.x, target.y);
        double power = Math.max(0.7, Math.min(2.5, 450 / Math.max(1, range)));
        power = Math.min(power, Math.max(0.1, target.energy / 4));
        power = Math.min(power, Math.max(0.1, (getEnergy() - 2) / 6));
        power = Math.min(power, getEnergy() - 0.2);
        if (power < Rules.MIN_BULLET_POWER) return;
        double x = target.x, y = target.y;
        double vx = Math.sin(target.heading) * target.velocity;
        double vy = Math.cos(target.heading) * target.velocity;
        long age = getTime() - target.seen;
        double bulletSpeed = Rules.getBulletSpeed(power);
        // Simula o avanço do alvo até o tiro alcançá-lo, ou até a parede.
        for (int tick = 0; tick < 160; tick++) {
            if (tick >= age && (tick - age) * bulletSpeed >= distance(x, y)) break;
            double nx = x + vx, ny = y + vy;
            if (!inside(nx, ny, 18)) break;
            x = nx;
            y = ny;
        }
        double turn = normalRelativeAngle(Math.atan2(x - getX(), y - getY())
                - getGunHeadingRadians());
        setTurnGunRightRadians(turn);
        double tolerance = Math.atan(18 / Math.max(1, distance(x, y)));
        if (getGunHeat() == 0 && Math.abs(turn) < tolerance) setFire(power);
    }

    @Override
    public void onRobotDeath(RobotDeathEvent event) {
        if (enemies.remove(event.getName()) == target) target = null;
        nextMove = 0;
    }

    @Override
    public void onHitByBullet(HitByBulletEvent event) {
        if (getTime() - lastDodge >= 12) {
            nextMove = 0;
            lastDodge = getTime();
        }
    }

    @Override
    public void onHitWall(HitWallEvent event) {
        nextMove = 0;
    }

    @Override
    public void onHitRobot(HitRobotEvent event) {
        nextMove = 0;
    }

    private boolean inside(double x, double y, double margin) {
        return x >= margin && y >= margin
                && x <= getBattleFieldWidth() - margin
                && y <= getBattleFieldHeight() - margin;
    }

    private double distance(double x, double y) {
        return Math.hypot(x - getX(), y - getY());
    }

    private static double square(double value) {
        return value * value;
    }
}
