package dev.marigold.handinhand.client;

import dev.marigold.handinhand.*;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Interactive disposable demonstration, excluded from every normal release build. */
@EventBusSubscriber(modid=HandInHand.ID,value=Dist.CLIENT)
public final class ManualDemo {
    private static int stage,ticks;
    private static CompletableFuture<?> setup;
    private static FakePlayer dummy,showA,showB;
    private static UUID owner;
    private static UUID lastRequest;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("handinhand.demo"))return;
        var mc=Minecraft.getInstance();ticks++;
        try {
            if(stage==0&&ticks>20&&mc.screen instanceof TitleScreen){
                mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(4);mc.options.simulationDistance().set(5);
                mc.options.guiScale().set(2);mc.options.fov().set(70);mc.options.onboardAccessibility=false;
                var settings=new LevelSettings("Hand in Hand playground",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
                stage=1;
                mc.createWorldOpenFlows().createFreshLevel("hand-playground-"+System.currentTimeMillis(),settings,new WorldOptions(42,false,false),
                    r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),new TitleScreen());
            }
            if(stage==1&&mc.player!=null&&mc.getSingleplayerServer()!=null){
                owner=mc.player.getUUID();var server=mc.getSingleplayerServer();stage=2;
                setup=server.submit(()->{try{
                    var level=server.overworld();var real=server.getPlayerList().getPlayer(owner);
                    for(int x=-24;x<=24;x++)for(int z=-24;z<=24;z++)level.setBlockAndUpdate(new BlockPos(x,79,z),Blocks.SMOOTH_STONE.defaultBlockState());
                    level.setDayTime(6000);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
                    level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false,server);
                    real.teleportTo(0,80,-3);real.getAbilities().flying=false;real.onUpdateAbilities();
                    dummy=ClientSmoke.fake(level,new GameProfile(UUID.fromString("9799cabb-cc63-4eed-9fe5-a2b6150cfce0"),"Hand_Dummy"));
                    showA=ClientSmoke.fake(level,new GameProfile(UUID.fromString("8e9eed7b-eaf4-4e75-9f65-4d1c7b572241"),"Showcase_A"));
                    showB=ClientSmoke.fake(level,new GameProfile(UUID.fromString("0a85b45a-fad3-4ae9-a45e-2d16524cc22f"),"Showcase_B"));
                    ClientSmoke.place(dummy,.95,80,-3,0);ClientSmoke.place(showA,-.5,80,1,180);ClientSmoke.place(showB,.5,80,1,180);
                    ClientSmoke.add(server,dummy,real);ClientSmoke.add(server,showA,real);ClientSmoke.add(server,showB,real);
                    ClientSmoke.physicsClients.add(dummy.getUUID());
                    var r=HandServer.ask(showA,showB);HandServer.accept(showB,r.token());
                    real.sendSystemMessage(Component.literal("Hand in Hand playground ready! The two players ahead show the pose."));
                    real.sendSystemMessage(Component.literal("Look at Hand_Dummy beside you and press H. It automatically accepts for this demo. The movement tether is the same one used between real players."));
                    real.sendSystemMessage(Component.literal("Press F5 for third person; H releases. /handdemo offer makes the dummy ask YOU. /handdemo reset brings it back."));
                    real.sendSystemMessage(Component.literal("Walk or Ctrl+W to lead; your partner follows beside you at a matched, capped speed. Double-tap Space for upright flight. H releases; walls still block movement."));
                }catch(Exception e){throw new RuntimeException(e);}});
            }
            if(stage==2&&setup!=null&&setup.isDone()){
                setup.join();
                if(mc.level.getPlayerByUUID(dummy.getUUID())==null)return;
                mc.player.setYRot(0);mc.player.setXRot(5);mc.player.setYHeadRot(0);
                Files.writeString(mc.gameDirectory.toPath().resolve("DEMO-READY.txt"),"READY: 0.1.0-alpha.7, Hand_Dummy, weightless side-by-side following, speed matching, upright flight\n");
                mc.getWindow().setTitle("Hand in Hand alpha.7 - Weightless side-by-side playground");
                stage=3;
            }
        }catch(Throwable error){error.printStackTrace();stage=-1;try{Files.writeString(mc.gameDirectory.toPath().resolve("DEMO-ERROR.txt"),error.toString());}catch(Exception ignored){}}
    }
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGH) public static void server(ServerTickEvent.Post event) {
        if(!Boolean.getBoolean("handinhand.demo")||dummy==null||owner==null)return;
        var real=event.getServer().getPlayerList().getPlayer(owner);if(real==null)return;
        var state=HandServer.state(event.getServer());var request=state.pending(dummy.getUUID());
        if(request!=null&&request.recipient().equals(dummy.getUUID())&&!request.token().equals(lastRequest)) {
            try{HandServer.accept(dummy,request.token());lastRequest=request.token();}catch(IllegalStateException ignored){}
        }
        var pair=state.pair(dummy.getUUID());
        if(pair!=null&&pair.other(dummy.getUUID()).equals(owner)){
            // A headless fake has no LocalPlayer physics loop. It only integrates the common tether packets.
            dummy.setSprinting(real.isSprinting());dummy.getAbilities().flying=false;
        }
        ClientSmoke.fakePhysics(dummy);
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        if(!Boolean.getBoolean("handinhand.demo"))return;
        event.getDispatcher().register(Commands.literal("handdemo")
            .then(Commands.literal("reset").executes(c->{
                var p=c.getSource().getPlayerOrException();if(dummy==null)return 0;
                HandServer.release(p,"");HandServer.release(dummy,"");HandServer.state(p.server).cancel(dummy.getUUID());
                ClientSmoke.scale(dummy,1);p.teleportTo(0,80,-3);ClientSmoke.place(dummy,.95,80,-3,0);
                dummy.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                HandServer.tell(p,"Dummy reset beside the starting point.");return 1;
            }))
            .then(Commands.literal("offer").executes(c->{
                var p=c.getSource().getPlayerOrException();if(dummy==null)return 0;
                try{HandServer.ask(dummy,p);}catch(IllegalStateException e){HandServer.tell(p,e.getMessage());}return 1;
            }))
            .then(Commands.literal("size").then(Commands.argument("scale",FloatArgumentType.floatArg(.5f,1.5f)).executes(c->{
                if(dummy==null)return 0;ClientSmoke.scale(dummy,FloatArgumentType.getFloat(c,"scale"));return 1;
            }))));
    }
}
