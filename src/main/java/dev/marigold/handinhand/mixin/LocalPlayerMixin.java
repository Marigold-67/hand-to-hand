// SPDX-License-Identifier: GPL-3.0-only
package dev.marigold.handinhand.mixin;

import dev.marigold.handinhand.client.HandClient;
import dev.marigold.handinhand.HandInHand;
import net.minecraft.world.entity.player.Abilities;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents sprint prediction while linked without changing the player's physical key state. */
@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {
    @ModifyArgs(method="move",at=@At(value="INVOKE",target="Lnet/minecraft/client/player/AbstractClientPlayer;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"),remap=false)
    private void handinhandTetherMovement(Args args) {
        if(args.get(0)==net.minecraft.world.entity.MoverType.SELF)
            args.set(1,HandClient.tetherMovement((LocalPlayer)(Object)this,args.get(1)));
    }
    @Inject(method="canStartSprinting",at=@At("HEAD"),cancellable=true,remap=false)
    private void handinhandWalkTogether(CallbackInfoReturnable<Boolean> callback) {
        LocalPlayer player=(LocalPlayer)(Object)this;
        if(HandClient.LINKS.containsKey(player.getUUID())&&!HandInHand.ALLOW_SPRINTING.get())callback.setReturnValue(false);
    }
    @Redirect(method="aiStep",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Abilities;getFlyingSpeed()F"),remap=false)
    private float handinhandVerticalFlight(Abilities abilities){
        LocalPlayer p=(LocalPlayer)(Object)this;
        return abilities.getFlyingSpeed()*HandClient.flightFactor(p.getUUID(),true);
    }
}
