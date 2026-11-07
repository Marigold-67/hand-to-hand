package dev.marigold.handinhand.smokemixin;

import io.netty.channel.DefaultEventLoopGroup;
import net.minecraft.server.network.ServerConnectionListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.function.Supplier;

/** Test fixture only: use socket-free threads for Minecraft's existing in-memory channel. */
@Mixin(ServerConnectionListener.class)
public class MemoryTransportMixin {
    private static final DefaultEventLoopGroup HAND_TEST_MEMORY=new DefaultEventLoopGroup(2,r->{Thread t=new Thread(r,"HandTest Memory");t.setDaemon(true);return t;});
    @Redirect(method="startMemoryChannel",at=@At(value="INVOKE",target="Ljava/util/function/Supplier;get()Ljava/lang/Object;"),remap=false)
    private Object handTestMemory(Supplier<?> original){return HAND_TEST_MEMORY;}
}
