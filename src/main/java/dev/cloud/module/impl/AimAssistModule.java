package dev.cloud.module.impl;

import dev.cloud.Weapons;
import dev.cloud.module.BoolSetting;
import dev.cloud.module.Category;
import dev.cloud.module.ModeSetting;
import dev.cloud.module.Module;
import dev.cloud.module.NumSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

public class AimAssistModule extends Module {
    public final NumSetting speed = add(new NumSetting("Speed", 6, 1, 20, 0.1, ""));
    public final NumSetting smoothing = add(new NumSetting("Smoothing", 60, 0, 100, 1, "%"));
    public final NumSetting fov = add(new NumSetting("FOV", 70, 10, 180, 1, "\u00B0"));
    public final NumSetting range = add(new NumSetting("Range", 4.5, 2, 8, 0.1, "m"));
    public final BoolSetting stopOnHitbox = add(new BoolSetting("Stop On Hitbox", true));
    public final BoolSetting sticky = add(new BoolSetting("Sticky Target", true));
    public final BoolSetting vertical = add(new BoolSetting("Vertical", true));
    public final NumSetting verticalSpeed = add(new NumSetting("Vertical Speed", 50, 0, 100, 1, "%").showIf(() -> vertical.get()));
    public final ModeSetting priority = add(new ModeSetting("Priority", "Angle", "Angle", "Distance", "Health"));
    public final BoolSetting playersOnly = add(new BoolSetting("Players Only", true));
    public final BoolSetting weaponOnly = add(new BoolSetting("Weapon Only", true));
    public final BoolSetting requireClick = add(new BoolSetting("Require Click", false));
    public final BoolSetting throughWalls = add(new BoolSetting("Through Walls", false));
    public final BoolSetting ignoreInvisible = add(new BoolSetting("Ignore Invisible", true));

    private LivingEntity target;
    private float carryYaw, carryPitch;

    public AimAssistModule() {
        super("Aim Assist", "Smoothly pulls your aim toward the best nearby target", Category.COMBAT, false);
    }

    @Override
    protected void onDisable() { target = null; carryYaw = carryPitch = 0f; }

    @Override
    public String suffix() { return priority.get(); }

    @Override
    public void frame(MinecraftClient mc, float dt) {
        dt = Math.min(dt, 0.05f);
        ClientPlayerEntity p = mc.player;
        if (!isEnabled() || p == null || mc.world == null || mc.currentScreen != null || p.isSpectator() || dt <= 0f) {
            target = null;
            return;
        }
        if (weaponOnly.get() && !Weapons.isWeapon(p.getMainHandStack())) { target = null; return; }
        if (requireClick.get() && !mc.options.attackKey.isPressed()) return;

        float limit = (float) fov.get();
        // sticky: keep the current target while it stays valid (with a looser FOV so it isn't dropped on fast strafes)
        if (!(sticky.get() && target != null && valid(p, target, limit * 1.5f))) target = find(mc, p, limit);
        if (target == null) return;

        if (stopOnHitbox.get() && mc.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() == target) {
            carryYaw = carryPitch = 0f;
            return;
        }

        float[] rot = rotationTo(p, target);
        float dYaw = MathHelper.wrapDegrees(rot[0] - p.getYaw());
        float dPitch = rot[1] - p.getPitch();

        // exponential smoothing: frame-rate independent, higher smoothing = softer pull
        float sm = (float) smoothing.get() / 100f;
        float rate = (float) speed.get() * (1.25f - sm) * 1.6f;
        float k = 1f - (float) Math.exp(-rate * dt);
        float maxStep = (float) speed.get() * 45f * dt;
        float my = MathHelper.clamp(dYaw * k, -maxStep, maxStep);
        float mp = vertical.get() ? MathHelper.clamp(dPitch * k * (float) verticalSpeed.get() / 100f, -maxStep, maxStep) : 0f;

        // snap to the mouse sensitivity grid like real mouse input
        double s = mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
        float gcd = (float) (s * s * s * 8.0 * 0.15);
        if (gcd <= 0f) gcd = 0.01f;
        carryYaw += my;
        carryPitch += mp;
        float ay = Math.round(carryYaw / gcd) * gcd;
        float ap = Math.round(carryPitch / gcd) * gcd;
        carryYaw -= ay;
        carryPitch -= ap;
        if (ay != 0f) p.setYaw(p.getYaw() + ay);
        if (ap != 0f) p.setPitch(MathHelper.clamp(p.getPitch() + ap, -90f, 90f));
    }

    private LivingEntity find(MinecraftClient mc, ClientPlayerEntity p, float limit) {
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity e : mc.world.getEntities()) {
            if (!valid(p, e, limit)) continue;
            LivingEntity le = (LivingEntity) e;
            double score = switch (priority.get()) {
                case "Distance" -> p.squaredDistanceTo(e);
                case "Health" -> le.getHealth();
                default -> angleTo(p, e);
            };
            if (score < bestScore) {
                bestScore = score;
                best = le;
            }
        }
        return best;
    }

    private boolean valid(ClientPlayerEntity p, Entity e, float limit) {
        if (!(e instanceof LivingEntity le) || e == p || !le.isAlive() || le.isSpectator() || le instanceof ArmorStandEntity) return false;
        if (playersOnly.get() && !(e instanceof PlayerEntity)) return false;
        if (ignoreInvisible.get() && e.isInvisible()) return false;
        double r = range.get();
        if (p.squaredDistanceTo(e) > r * r) return false;
        if (!throughWalls.get() && !p.canSee(e)) return false;
        return angleTo(p, e) <= limit / 2f;
    }

    private static float angleTo(ClientPlayerEntity p, Entity e) {
        float[] r = rotationTo(p, e);
        float dy = MathHelper.wrapDegrees(r[0] - p.getYaw());
        float dp = r[1] - p.getPitch();
        return (float) Math.sqrt(dy * dy + dp * dp);
    }

    /** Aims at the hitbox centre horizontally and at your eye height clamped into the hitbox vertically. */
    private static float[] rotationTo(ClientPlayerEntity p, Entity e) {
        Box b = e.getBoundingBox();
        double ex = p.getX(), ey = p.getEyeY(), ez = p.getZ();
        double tx = (b.minX + b.maxX) * 0.5, tz = (b.minZ + b.maxZ) * 0.5;
        double hgt = b.maxY - b.minY;
        double ty = MathHelper.clamp(ey, b.minY + hgt * 0.2, b.maxY - hgt * 0.1);
        double dx = tx - ex, dy = ty - ey, dz = tz - ez;
        double dh = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dh));
        return new float[]{yaw, pitch};
    }
}
