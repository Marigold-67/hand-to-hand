package dev.marigold.handinhand.client;

import com.mojang.logging.LogUtils;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import dev.marigold.handinhand.HandInHand;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import java.lang.reflect.Method;
import java.util.Map;

/** Optional EMF bridge. No EMF classes are linked when the dependency is absent. */
public final class EmfCompatibility {
    private static Method currentEntity, vanillaParts;
    private static boolean unavailable;
    private EmfCompatibility() {}

    public static void afterAnimation(Object root) {
        if(unavailable||HandClient.LINKS.isEmpty())return;
        try {
            if(currentEntity==null) {
                currentEntity=Class.forName("traben.entity_model_features.EMFAnimationApi").getMethod("getCurrentEntity");
                vanillaParts=root.getClass().getMethod("getAllVanillaPartsByNameEMF");
            }
            if(!(currentEntity.invoke(null) instanceof AbstractClientPlayer player))return;
            if(!(PlayerAnimationAccess.getPlayerAnimationLayer(player,ResourceLocation.fromNamespaceAndPath(HandInHand.ID,"hands")) instanceof HandClient.HandPose pose))return;
            if(!(vanillaParts.invoke(root) instanceof Map<?,?> parts))return;
            pose.applyAfterCustomAnimation(parts);
        }catch(ReflectiveOperationException|LinkageError error) {
            unavailable=true;
            LogUtils.getLogger().warn("Hand in Hand could not apply its optional EMF compatibility bridge",error);
        }
    }

    static void restoreChildren(ModelPart part) {
        part.getAllParts().filter(child->child!=part).forEach(ModelPart::resetPose);
    }
}
