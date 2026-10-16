package com.alrex.parcool.client.animation;

import com.alrex.parcool.api.unstable.animation.AnimationOption;
import com.alrex.parcool.common.action.impl.Dive;
import com.alrex.parcool.common.attachment.client.Animation;
import com.alrex.parcool.common.attachment.common.Parkourability;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.player.Player;

public final class AnimationReplayChecks {
    private AnimationReplayChecks() {}

    public static void run() throws ReflectiveOperationException {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var action = new Dive();
        action.restoreAnimationCounters(true, 7, 2, 31);
        check(action.isDoing(), "doing restored without start callback");
        check(action.getDoingTick() == 7 && action.getNotDoingTick() == 2, "both tick counters");
        check(action.getTickFromLastStarted() == 31, "continuation counter");
        action.tick();
        check(action.getDoingTick() == 8 && action.getTickFromLastStarted() == 32, "one next tick");

        var animator =
                new Animator() {
                    @Override
                    public boolean shouldRemoved(Player player, Parkourability parkourability) {
                        throw new AssertionError("Render removal must not decide driven topology");
                    }
                };
        animator.restoreAnimationTick(17);
        check(animator.animationTick() == 17, "integer checkpoint replaced without a tick");
        var passive = new PassiveCustomAnimation();
        passive.restoreAnimationCounters(14, 2, 3);
        check(passive.fallingAnimationTick() == 14, "passive falling state");
        check(
                passive.flyingAnimationLevelOld() == 2 && passive.flyingAnimationLevel() == 3,
                "passive pair");

        var animation = new Animation();
        var remote = unconstructed(RemotePlayer.class);
        var local = unconstructed(LocalPlayer.class);
        try {
            animation.setDriven(local, true);
            throw new AssertionError("Local player must reject driven mode");
        } catch (IllegalArgumentException expected) {
            check(!animation.isDriven(), "local rejection is atomic");
        }
        animation.setDriven(remote, true);
        var option = new AnimationOption();
        animation.restoreDriven(animator, passive, option);
        animation.tick(remote, null);
        animation.onRenderTick(null, remote, null);
        animation.updateAnimationInfo(remote);
        animation.removeAnimator();
        animation.setAnimator(null);
        check(
                animation.getAnimator() == animator && animation.hasAnimator(),
                "driven selection retained");
        check(animator.animationTick() == 17, "client ticks cannot advance driven animation");
        check(
                animation.getPassiveAnimation() == passive && animation.getOption() == option,
                "imported scratch");
        var nativeSlot = Animation.class.getDeclaredField("animator");
        nativeSlot.setAccessible(true);
        nativeSlot.set(animation, null);
        check(animation.getAnimator() == animator, "native policy mixin slot is isolated");
        animation.setDriven(remote, false);
        check(!animation.hasAnimator(), "native selection view resumes after leaving driven mode");
        try {
            animation.restoreDriven(animator, passive, option);
            throw new AssertionError("Import requires driven mode");
        } catch (IllegalStateException expected) {
            check(!animation.isDriven(), "failed import cannot enable driving");
        }
    }

    private static <T> T unconstructed(Class<T> type) throws ReflectiveOperationException {
        // Entity allocation only: no client, world, window or constructor is started by the check.
        var allocator = Class.forName("sun.misc.Unsafe");
        var singleton = allocator.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        return type.cast(
                allocator
                        .getMethod("allocateInstance", Class.class)
                        .invoke(singleton.get(null), type));
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
