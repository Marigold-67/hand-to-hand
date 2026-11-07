package dev.marigold.handinhand.mixin;
import dev.marigold.handinhand.client.HandClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Abilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Client-side speed prediction only. The saved ability and permission to fly are untouched. */
@Mixin(Player.class)
public class PlayerFlightMixin {
    @Redirect(method="getFlyingSpeed",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Abilities;getFlyingSpeed()F"),remap=false)
    private float handinhandHorizontalFlight(Abilities abilities){
        Player p=(Player)(Object)this;
        return abilities.getFlyingSpeed()*(p==Minecraft.getInstance().player?HandClient.flightFactor(p.getUUID(),false):1);
    }
}
