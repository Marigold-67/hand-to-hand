package dev.marigold.handinhand.mixin;

import dev.marigold.handinhand.client.EmfCompatibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** EMF evaluates its resource-pack pose after PAL; restore just the held arm afterward. */
@Pseudo
@Mixin(targets="traben.entity_model_features.models.parts.EMFModelPartRoot",remap=false)
public abstract class EmfModelRootMixin {
    @Inject(method={"animate","animateNoPause"},at=@At("RETURN"),require=0,remap=false)
    private void handinhand$afterEmf(CallbackInfo ci) {EmfCompatibility.afterAnimation(this);}
}
