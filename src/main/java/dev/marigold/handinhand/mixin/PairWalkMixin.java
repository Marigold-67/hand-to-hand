package dev.marigold.handinhand.mixin;
import dev.marigold.handinhand.client.PairClient;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LivingEntity.class)
public abstract class PairWalkMixin {
 @Inject(method="calculateEntityAnimation",at=@At("HEAD"),cancellable=true,remap=false)
 private void handinhandGait(boolean flying,CallbackInfo ci){var self=(LivingEntity)(Object)this;
  if(self instanceof Player player&&PairClient.ownsGait(player)){self.walkAnimation.update(PairClient.gait(self.getUUID()),PairClient.gaitResponse());ci.cancel();}
 }
}
