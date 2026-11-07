package dev.marigold.handinhand;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

@Mod(HandInHand.ID)
public final class HandInHand {
    public static final String ID="handinhand";
    public static final ModConfigSpec CONFIG;
    public static final ModConfigSpec.BooleanValue ENABLED, ALLOW_SPRINTING, ALLOW_FLIGHT, MOVEMENT_TETHER, ROLEPLAY_GRAB, GRAB_REQUIRES_OP;
    public static final ModConfigSpec.IntValue EXPIRY, COOLDOWN, REACH_GRACE;
    public static final ModConfigSpec.DoubleValue REQUEST_DISTANCE, RELEASE_DISTANCE, TETHER_LENGTH;
    static {
        var b=new ModConfigSpec.Builder();
        ENABLED=b.comment("Allow hand holding on this server.").define("enabled",true);
        ALLOW_SPRINTING=b.comment("Allow sprinting together. Both partners keep their own sprint controls.").define("allowSprinting",true);
        ALLOW_FLIGHT=b.comment("Allow hand holding during upright flight. Does not grant flight or support Elytra gliding.").define("allowUprightFlight",true);
        MOVEMENT_TETHER=b.comment("Let a passive partner follow beside the leader with matched, bounded movement. Ordinary distance/reach gaps do not release this tether.").define("movementTether",true);
        TETHER_LENGTH=b.comment("Resting tether distance at normal size. Uniform player scaling is applied automatically.").defineInRange("tetherLength",.90,.65,1.10);
        EXPIRY=b.defineInRange("requestExpirySeconds",30,5,120);
        COOLDOWN=b.defineInRange("requestCooldownSeconds",5,1,120);
        REQUEST_DISTANCE=b.defineInRange("requestDistance",1.7,0.5,3.0);
        RELEASE_DISTANCE=b.defineInRange("releaseDistance",2.6,0.5,4.0);
        REACH_GRACE=b.comment("Ticks allowed to recover from a brief loss of arm reach or line of sight (20 ticks = 1 second).").defineInRange("reachGraceTicks",12,0,40);
        b.push("roleplayGrab");
        ROLEPLAY_GRAB=b.comment("Allow immediate roleplay grabs. Checked on the server.").define("enabled",false);
        GRAB_REQUIRES_OP=b.comment("Restrict initiation to server OP players.").define("requireOp",true);
        b.pop();
        CONFIG=b.build();
    }
    public HandInHand(IEventBus bus, ModContainer container) {
        bus.addListener(LinkPayload::register);
        container.registerConfig(ModConfig.Type.SERVER,CONFIG);
    }
}
