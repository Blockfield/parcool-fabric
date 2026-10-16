package com.alrex.parcool.common.attachment.client;

import com.alrex.parcool.api.unstable.animation.AnimationOption;
import com.alrex.parcool.api.unstable.animation.AnimationPart;
import com.alrex.parcool.api.unstable.animation.ParCoolAnimationInfoEvent;
import com.alrex.parcool.client.animation.Animator;
import com.alrex.parcool.client.animation.PassiveCustomAnimation;
import com.alrex.parcool.client.animation.PlayerModelRotator;
import com.alrex.parcool.client.animation.PlayerModelTransformer;
import com.alrex.parcool.common.attachment.ClientAttachments;
import com.alrex.parcool.common.attachment.common.Parkourability;
import com.alrex.parcool.config.ParCoolConfig;
import com.alrex.parcool.fabric.ParCoolEvents;
import com.alrex.parcool.fabric.RenderFrameEvent;

import io.github.fabricators_of_create.porting_lib.client_events.event.client.ViewportEvent;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;

@Environment(EnvType.CLIENT)
public class Animation {

    public static Animation get(Player player) {
        return player.getAttachedOrCreate(ClientAttachments.ANIMATION);
    }

    private Animator animator = null;
    private AnimationOption option = new AnimationOption();
    private PassiveCustomAnimation passiveAnimation = new PassiveCustomAnimation();
    private boolean driven = false;
    private Animator drivenAnimator;

    public boolean isDriven() {
        return driven;
    }

    public void setDriven(AbstractClientPlayer player, boolean driven) {
        if (player.isLocalPlayer()) {
            throw new IllegalArgumentException("The local player must retain native animation");
        }
        this.driven = driven;
    }

    public Animator getAnimator() {
        return driven ? drivenAnimator : animator;
    }

    public PassiveCustomAnimation getPassiveAnimation() {
        return passiveAnimation;
    }

    public AnimationOption getOption() {
        return option;
    }

    /** Materialized scratch, not a wire state. The owner must replace it for each native sample. */
    public void restoreDriven(
            Animator animator, PassiveCustomAnimation passiveAnimation, AnimationOption option) {
        if (!driven) throw new IllegalStateException("Animation is not driven");
        java.util.Objects.requireNonNull(passiveAnimation);
        java.util.Objects.requireNonNull(option);
        // Native compatibility mixins can still write the native slot directly.
        this.drivenAnimator = animator;
        this.passiveAnimation = passiveAnimation;
        this.option = option;
    }

    public void setAnimator(Animator animator) {
        if (driven) return;
        if (!ParCoolConfig.Client.Booleans.EnableAnimation.get()) return;
        if (!ParCoolConfig.Client.getInstance().canAnimate(animator.getClass()).get()) return;
        this.animator = animator;
    }

    public boolean animatePre(Player player, PlayerModelTransformer modelTransformer) {
        Parkourability parkourability = Parkourability.get(player);
        Animator animator = getAnimator();
        if (!driven && animator != null && animator.shouldRemoved(player, parkourability)) {
            this.animator = null;
            animator = null;
        }
        // Опцию ставим до выхода по animator == null: иначе PassiveCustomAnimation (махи руками
        // в свободном падении) идёт мимо неё и всё равно перебивает чужую позу.
        modelTransformer.setOption(option);
        if (animator == null) return false;
        if (shouldCancelAnimation(player)) return false;
        return animator.animatePre(player, parkourability, modelTransformer);
    }

    public void animatePost(Player player, PlayerModelTransformer modelTransformer) {
        Parkourability parkourability = Parkourability.get(player);
        Animator animator = getAnimator();
        if (shouldCancelAnimation(player)) return;
        if (animator == null) {
            passiveAnimation.animate(player, parkourability, modelTransformer);
            return;
        }
        animator.animatePost(player, parkourability, modelTransformer);
    }

    public boolean rotatePre(AbstractClientPlayer player, PlayerModelRotator rotator) {
        Parkourability parkourability = Parkourability.get(player);
        Animator animator = getAnimator();
        if (!driven && animator != null && animator.shouldRemoved(player, parkourability)) {
            this.animator = null;
            animator = null;
        }
        if (animator == null) return false;
        if (shouldCancelAnimation(player) || option.isCanceled(AnimationPart.ROTATION))
            return false;
        return animator.rotatePre(player, parkourability, rotator);
    }

    public void rotatePost(AbstractClientPlayer player, PlayerModelRotator rotator) {
        Parkourability parkourability = Parkourability.get(player);
        Animator animator = getAnimator();
        if (shouldCancelAnimation(player) || option.isCanceled(AnimationPart.ROTATION)) return;
        if (animator == null) {
            passiveAnimation.rotate(player, parkourability, rotator);
            return;
        }
        animator.rotatePost(player, parkourability, rotator);
    }

    public void cameraSetup(
            ViewportEvent.ComputeCameraAngles event,
            LocalPlayer player,
            Parkourability parkourability) {
        if (animator == null) return;
        if (option.isCanceled(AnimationPart.CAMERA)) return;
        if (animator.shouldRemoved(player, parkourability)) {
            animator = null;
            return;
        }
        animator.onCameraSetUp(event, player, parkourability);
    }

    public void tick(AbstractClientPlayer player, Parkourability parkourability) {
        if (driven) return;
        passiveAnimation.tick(player, parkourability);
        if (animator != null) {
            animator.tick(player);
        }
    }

    public void onRenderTick(RenderFrameEvent event, Player player, Parkourability parkourability) {
        if (driven) return;
        if (animator != null) {
            animator.onRenderTick(event, player, parkourability);
        }
        if (event instanceof RenderFrameEvent.Pre) {
            updateAnimationInfo((AbstractClientPlayer) player);
        }
    }

    public void updateAnimationInfo(AbstractClientPlayer player) {
        if (driven) return;
        ParCoolAnimationInfoEvent animationEvent = new ParCoolAnimationInfoEvent(player, animator);
        ParCoolEvents.post(animationEvent);
        option = animationEvent.getOption();
    }

    public boolean shouldCancelAnimation(Player player) {
        if (player.isLocalPlayer()
                && Minecraft.getInstance().options.getCameraType().isFirstPerson()
                && !ParCoolConfig.Client.Booleans.EnableFPVAnimation.get()) {
            return true;
        }
        return this.option.isAnimationCanceled();
    }

    public boolean hasAnimator() {
        return getAnimator() != null;
    }

    public void removeAnimator() {
        if (driven) return;
        animator = null;
    }
}
