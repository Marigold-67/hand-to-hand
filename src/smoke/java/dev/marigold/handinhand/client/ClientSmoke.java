package dev.marigold.handinhand.client;

import dev.marigold.handinhand.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Opt-in integration fixture. NEVER included in the release mod. */
@EventBusSubscriber(modid=HandInHand.ID,value=Dist.CLIENT)
public final class ClientSmoke {
    static int stage,ticks,total;
    static boolean complete;
    static FakePlayer a,b;
    static UUID request;
    static CompletableFuture<?> task;
    static final StringBuilder report=new StringBuilder();
    static final Set<UUID> physicsClients=new HashSet<>();
    static final Map<UUID,TetherMotion> fakeTargets=new HashMap<>();
    static final Set<UUID> activeFakeClients=new HashSet<>();
    static final Map<UUID,Double> fakeFollowSpeed=new HashMap<>();
    static void fakePacket(FakePlayer player,net.minecraft.network.protocol.Packet<?> packet) {
        if(physicsClients.contains(player.getUUID())&&packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom&&custom.payload() instanceof TetherMotion impulse) {
            var pair=HandServer.state(player.server).pair(player.getUUID());
            if(pair!=null&&pair.started()==impulse.started()&&pair.other(player.getUUID()).equals(impulse.partner()))
                fakeTargets.put(player.getUUID(),impulse);
        }
    }
    /** Fake players have no client loop. Integrate the same following plans and collision/friction as a client. */
    static void fakePhysics(FakePlayer p) {
        if(HandServer.isControlled(p))return;
        var move=p.getDeltaMovement();var ownMotion=move;var pair=HandServer.state(p.server).pair(p.getUUID());
        if(pair!=null&&HandInHand.MOVEMENT_TETHER.get()){
            boolean active=activeFakeClients.contains(p.getUUID());HandServer.intent(p,new TetherIntent(pair.other(p.getUUID()),pair.started(),active));
            var other=p.server.getPlayerList().getPlayer(pair.other(p.getUUID()));
            if(other!=null){double m=ScaleSupport.motionScale(p);
                var target=fakeTargets.get(p.getUUID());
                if(!active&&target!=null&&target.following()&&target.started()==pair.started()&&target.partner().equals(other.getUUID())){
                    double age=Math.max(0,Math.min(4,p.level().getGameTime()-target.frameTick()-1));
                    var anchor=new Vec3(target.ax(),target.ay(),target.az()).add(target.vx()*age,target.lift()?target.vy()*age:0,target.vz()*age);
                    var step=TetherMath.follow(HandTether.vector(p.position()),HandTether.vector(anchor.add(target.x(),target.y(),target.z())),HandTether.vector(move.scale(m)),
                        new HandGeometry.V(target.vx(),target.vy(),target.vz()),target.maximum(),target.lift(),.18*HandTether.scale(p,other));
                    move=HandTether.vector(step.movement()).scale(1/m);
                    if(target.lift()){ownMotion=new Vec3(ownMotion.x,0,ownMotion.z);p.resetFallDistance();}
                }
            }
        }
        p.setDeltaMovement(ownMotion);p.move(net.minecraft.world.entity.MoverType.SELF,move);
        var after=p.getDeltaMovement();
        double friction=p.getAbilities().flying?.91:(p.onGround()?.546:.91);
        p.setDeltaMovement(after.x*friction,p.getAbilities().flying?after.y*.6:(after.y-.08)*.98,after.z*friction);
    }
    static volatile boolean walking;
    static double renderedGap=Double.POSITIVE_INFINITY;
    static final Map<UUID,HandGeometry.V> modelEndpoints=new HashMap<>();
    static boolean customModelSeen;
    @SubscribeEvent public static void modelRendered(net.neoforged.neoforge.client.event.RenderPlayerEvent.Post e) {
        if(!(e.getEntity() instanceof net.minecraft.client.player.AbstractClientPlayer player))return;
        var link=HandClient.LINKS.get(player.getUUID());
        if(link==null)return;
        var other=player.level().getPlayerByUUID(link.other(player.getUUID()));if(other==null)return;
        var model=e.getRenderer().getModel();
        customModelSeen|=model.rightArm.getClass().getName().startsWith("traben.entity_model_features.");
        float partial=e.getPartialTick();
        float yawDegrees=net.minecraft.util.Mth.rotLerp(partial,player.yBodyRotO,player.yBodyRot);
        boolean right=link.right(player.getUUID());
        boolean slim=player.getSkin().model()==net.minecraft.client.resources.PlayerSkin.Model.SLIM;
        var part=right?model.rightArm:model.leftArm;
        var end=new org.joml.Vector3f((right?-1:1)*(slim?.5f:1)/16,10f/16,0);
        new org.joml.Matrix4f().rotationZYX(part.zRot,part.yRot,part.xRot).transformDirection(end);
        end.add(part.x/16,part.y/16,part.z/16);
        double yaw=Math.toRadians(yawDegrees),scale=ScaleSupport.modelScale(player,partial);
        var pos=player.getPosition(partial);
        modelEndpoints.put(player.getUUID(),new HandGeometry.V(pos.x+(end.x*Math.cos(yaw)+end.z*Math.sin(yaw))*scale,
            pos.y+(1.501-end.y)*scale,pos.z+(end.x*Math.sin(yaw)-end.z*Math.cos(yaw))*scale));
    }
    static volatile long graceProbeUntil;
    static volatile boolean graceProbeDone,graceProbePassed;
    static void check(boolean good,String why){if(!good)throw new AssertionError(why);}
    static void pass(String s)throws Exception{report.append("PASS: ").append(s).append('\n');write();System.out.println("HAND_TEST_PASS "+s);}
    static void write()throws Exception{Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("TEST-RESULTS.txt"),report);}
    static void next(){stage++;ticks=0;}
    static void shot(String name)throws Exception {
        var mc=Minecraft.getInstance();try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(mc.gameDirectory.toPath().resolve(name+".png"));}
    }
    static void place(ServerPlayer p,double x,double y,double z,float yaw){
        p.moveTo(x,y,z,yaw,0);p.xo=x;p.yo=y;p.zo=z;p.yBodyRot=yaw;p.yBodyRotO=yaw;p.setYHeadRot(yaw);
    }
    static void scale(ServerPlayer p,float value) {
        if(net.neoforged.fml.ModList.get().isLoaded("pehkui"))try{
            Object type=Class.forName("virtuoel.pehkui.api.ScaleTypes").getField("BASE").get(null);
            Object data=type.getClass().getMethod("getScaleData",net.minecraft.world.entity.Entity.class).invoke(type,p);
            data.getClass().getMethod("setScale",float.class).invoke(data,value);
        }catch(Exception error){throw new RuntimeException(error);}
        else p.getAttribute(Attributes.SCALE).setBaseValue(value);
    }
    static FakePlayer fake(net.minecraft.server.level.ServerLevel level,GameProfile profile) {
        var p=new FakePlayer(level,profile);
        p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),p,net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false)){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){fakePacket(p,packet);}
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener){fakePacket(p,packet);}
            @Override public boolean hasChannel(net.minecraft.resources.ResourceLocation channel){return false;}
            @Override public void tick(){}
        };
        return p;
    }
    @SuppressWarnings("unchecked") static void add(MinecraftServer server,FakePlayer fake,ServerPlayer viewer)throws Exception {
        var players=PlayerList.class.getDeclaredField("players");players.setAccessible(true);
        ((List<ServerPlayer>)players.get(server.getPlayerList())).add(fake);
        var field=PlayerList.class.getDeclaredField("playersByUUID");field.setAccessible(true);
        ((Map<UUID,ServerPlayer>)field.get(server.getPlayerList())).put(fake.getUUID(),fake);
        viewer.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(fake)));
        server.overworld().addNewPlayer(fake);
    }
    static void remove(MinecraftServer server,FakePlayer fake)throws Exception {
        var players=PlayerList.class.getDeclaredField("players");players.setAccessible(true);
        ((List<?>)players.get(server.getPlayerList())).remove(fake);
        var field=PlayerList.class.getDeclaredField("playersByUUID");field.setAccessible(true);
        ((Map<?,?>)field.get(server.getPlayerList())).remove(fake.getUUID());
        fake.discard();
    }
    static Style clickable(Component c,String command){
        if(c.getStyle().getClickEvent()!=null&&c.getStyle().getClickEvent().getValue().equals(command))return c.getStyle();
        for(Component child:c.getSiblings()){Style s=clickable(child,command);if(s!=null)return s;}return null;
    }
    static Style findAccept()throws Exception {
        var chat=Minecraft.getInstance().gui.getChat();var f=chat.getClass().getDeclaredField("allMessages");f.setAccessible(true);
        for(Object item:(List<?>)f.get(chat)){Component c=(Component)item.getClass().getMethod("content").invoke(item);Style style=clickable(c,"/handholding accept "+request);if(style!=null)return style;}
        return null;
    }
    static void verifyContact() {
        var mc=Minecraft.getInstance();
        var pa=(net.minecraft.client.player.AbstractClientPlayer)mc.level.getPlayerByUUID(a.getUUID());
        var pb=(net.minecraft.client.player.AbstractClientPlayer)mc.level.getPlayerByUUID(b.getUUID());
        check(pa!=null&&pb!=null,"Remote players unavailable");
        double gap=renderedGap;
        check(gap<.005,"Rendered arm endpoint gap: "+gap);
        check(modelEndpoints.containsKey(a.getUUID())&&modelEndpoints.containsKey(b.getUUID()),"No final model render samples");
        double modelGap=modelEndpoints.get(a.getUUID()).sub(modelEndpoints.get(b.getUUID())).length();
        check(modelGap<.005,"Final rendered model arm gap: "+modelGap+" (PAL-only checks do not detect model overrides)");
        if(Boolean.getBoolean("handinhand.expectEmf"))check(customModelSeen,"EMF resource-pack player model was not active");
        System.out.println("HAND_TEST_FINAL_MODEL_GAP "+modelGap);
        System.out.println("HAND_TEST_CONTACT_GAP "+gap);
    }
    static HandGeometry.V endpoint(net.minecraft.client.player.AbstractClientPlayer p,net.minecraft.client.player.AbstractClientPlayer other) throws Exception {
        var layer=com.zigythebird.playeranim.api.PlayerAnimationAccess.getPlayerAnimationLayer(p,net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(HandInHand.ID,"hands"));
        var f=HandClient.HandPose.class.getDeclaredField("partial");f.setAccessible(true);float partial=f.getFloat(layer);
        float yawDegrees=net.minecraft.util.Mth.rotLerp(partial,p.yBodyRotO,p.yBodyRot);
        boolean right=HandClient.LINKS.get(p.getUUID()).right(p.getUUID());
        boolean slim=p.getSkin().model()==net.minecraft.client.resources.PlayerSkin.Model.SLIM;
        check(layer!=null&&layer.isActive(),"PAL animation layer missing");
        var bone=layer.get3DTransform(new com.zigythebird.playeranimcore.bones.PlayerAnimBone(right?"right_arm":"left_arm"));
        var end=new org.joml.Vector3f((right?-1:1)*(slim?.5f:1)/16,10f/16,0);
        new org.joml.Matrix4f().rotationZYX(bone.rotZ,bone.rotY,bone.rotX).transformDirection(end);
        double scale=ScaleSupport.modelScale(p,partial),yaw=Math.toRadians(yawDegrees);
        var shoulder=HandClient.shoulder(p.getPosition(partial),yawDegrees,right,scale,slim);
        return shoulder.add(new HandGeometry.V((end.x*Math.cos(yaw)+end.z*Math.sin(yaw))*scale,-end.y*scale,(end.x*Math.sin(yaw)-end.z*Math.cos(yaw))*scale));
    }
    @SubscribeEvent public static void rendered(net.neoforged.neoforge.client.event.RenderFrameEvent.Post e) {
        var mc=Minecraft.getInstance();
        if(a==null||b==null||mc.level==null||!HandClient.LINKS.containsKey(a.getUUID())||!HandClient.LINKS.get(a.getUUID()).other(a.getUUID()).equals(b.getUUID()))return;
        var pa=mc.level.getPlayerByUUID(a.getUUID());var pb=mc.level.getPlayerByUUID(b.getUUID());
        if(pa instanceof net.minecraft.client.player.AbstractClientPlayer ca && pb instanceof net.minecraft.client.player.AbstractClientPlayer cb)
            try{renderedGap=endpoint(ca,cb).sub(endpoint(cb,ca)).length();}catch(Exception ignored){renderedGap=Double.POSITIVE_INFINITY;}
    }
    @SubscribeEvent public static void walkTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) {
        if(walking&&a!=null&&b!=null){place(a,a.getX(),80,a.getZ()-.025,180);place(b,b.getX(),80,b.getZ()-.025,180);}
        if(graceProbeUntil>0&&e.getServer().overworld().getGameTime()>=graceProbeUntil){
            graceProbePassed=HandServer.state(e.getServer()).pair(a.getUUID())!=null;
            place(b,.5,80,0,180);graceProbeUntil=0;graceProbeDone=true;
        }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("handinhand.smoke")||complete)return;
        var mc=Minecraft.getInstance();ticks++;total++;
        try {
            if(total>8000)throw new AssertionError("Timed out at stage "+stage);
            if(ticks<20)return;
            if(task!=null&&!task.isDone())return;
            if(task!=null){task.join();task=null;}
            switch(stage){
                case 0 -> {
                    if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen){mc.options.onboardAccessibility=false;mc.options.save();mc.setScreen(new TitleScreen());return;}
                    if(!(mc.screen instanceof TitleScreen))return;
                    mc.options.renderDistance().set(3);mc.options.simulationDistance().set(5);mc.options.pauseOnLostFocus=false;
                    mc.options.hideGui=false;mc.options.guiScale().set(2);mc.options.fov().set(60);
                    var settings=new LevelSettings("Hand in Hand disposable test",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
                    next();
                    mc.createWorldOpenFlows().createFreshLevel("hand-test-"+System.currentTimeMillis(),settings,new WorldOptions(42,false,false),
                        r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),new TitleScreen());
                }
                case 1 -> {
                    if(mc.player==null||mc.getSingleplayerServer()==null)return;
                    var server=mc.getSingleplayerServer();UUID viewer=mc.player.getUUID();
                    task=server.submit(()->{try{
                        var level=server.overworld();var real=server.getPlayerList().getPlayer(viewer);
                        for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)level.setBlockAndUpdate(new BlockPos(x,79,z),Blocks.SMOOTH_STONE.defaultBlockState());
                        level.setDayTime(6000);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
                        real.teleportTo(0,80,-3.6);real.setYRot(0);real.setXRot(8);
                        a=fake(level,new GameProfile(UUID.fromString("8e9eed7b-eaf4-4e75-9f65-4d1c7b572241"),"Marigold_Test"));
                        b=fake(level,new GameProfile(UUID.fromString("0a85b45a-fad3-4ae9-a45e-2d16524cc22f"),"Solomon_Test"));
                        place(a,-.5,80,0,180);place(b,.5,80,0,180);
                        add(server,a,real);add(server,b,real);
                        HandInHand.MOVEMENT_TETHER.set(false);
                        a.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(.15);b.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(.07);
                        var r=HandServer.ask(a,b);HandServer.accept(b,r.token());
                        check(Math.abs(a.getAttributeValue(Attributes.MOVEMENT_SPEED)-.07)<1e-6,"Different speed attributes did not match");
                        a.setSprinting(true);b.setSprinting(false);HandServer.updateSpeed(a,b);
                        check(Math.abs(a.getAttributeValue(Attributes.MOVEMENT_SPEED)-.07)<1e-6&&Math.abs(b.getAttributeValue(Attributes.MOVEMENT_SPEED)-.07)<1e-6,"Visual-only mode did not retain the slower passive walking pace");
                        a.setSprinting(true);b.setSprinting(true);HandServer.updateSpeed(a,b);
                        check(a.isSprinting()&&b.isSprinting(),"Sprinting was suppressed");
                        check(Math.abs(a.getAttributeValue(Attributes.MOVEMENT_SPEED)-.091)<1e-6,"Sprinting pace did not match");
                        check(Math.abs(b.getAttributeValue(Attributes.MOVEMENT_SPEED)-.091)<1e-6,"Partner sprinting pace did not match");
                        a.setSprinting(false);b.setSprinting(false);HandServer.updateSpeed(a,b);
                        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),server.registryAccess());
                        try{
                            var encoded=new LinkPayload(a.getUUID(),b.getUUID(),123,.25f,.75f,.5f,1f,true,false,true);
                            LinkPayload.CODEC.encode(buffer,encoded);
                            check(encoded.equals(LinkPayload.CODEC.decode(buffer)),"Pair payload codec round-trip failed");
                        }finally{buffer.release();}
                    }catch(Exception e){throw new RuntimeException(e);}});next();
                }
                case 2 -> {
                    if(mc.level.getPlayerByUUID(a.getUUID())==null||mc.level.getPlayerByUUID(b.getUUID())==null)return;
                    if(!HandClient.LINKS.containsKey(a.getUUID()))return;
                    mc.player.setYRot(0);mc.player.setXRot(8);mc.player.getAbilities().flying=false;
                    pass("NeoForge 21.1.250 + PAL load; server pairing state reaches an observing client and both remote player models are tracked");
                    pass("Unequal movement-speed attributes match the slower partner");
                    pass("Pair-state payload serializes and decodes through its registered stream codec");
                    pass("Both partners can sprint while their movement attributes remain matched");
                    next();
                }
                case 3 -> {
                    if(ticks==20){verifyContact();shot("01-standing-contact");pass("PAL arm transforms place both distal hand surfaces at the same point (gap below 0.005 blocks); third-player screenshot captured");walking=true;}
                    if(ticks<80)return;
                    verifyContact();shot("01b-walking-contact");walking=false;
                    pass("Server-driven side-by-side movement retains hand contact in the observing client's PAL layer");next();
                }
                case 4 -> {
                    var server=mc.getSingleplayerServer();
                    task=server.submit(()->{
                        scale(a,.75f);scale(b,1.15f);
                    });next();
                }
                case 5 -> {
                    verifyContact();shot("02-different-sizes");pass("Different "+(net.neoforged.fml.ModList.get().isLoaded("pehkui")?"Pehkui BASE":"vanilla model")+" scales retain a hand endpoint gap below 0.005 blocks; screenshot captured");
                    task=mc.getSingleplayerServer().submit(()->{
                        HandServer.updateSpeed(a,b);
                        double az=a.getZ(),bz=b.getZ();
                        a.move(net.minecraft.world.entity.MoverType.PLAYER,new net.minecraft.world.phys.Vec3(0,0,a.getAttributeValue(Attributes.MOVEMENT_SPEED)));
                        b.move(net.minecraft.world.entity.MoverType.PLAYER,new net.minecraft.world.phys.Vec3(0,0,b.getAttributeValue(Attributes.MOVEMENT_SPEED)));
                        double ad=a.getZ()-az,bd=b.getZ()-bz;
                        check(Math.abs(ad-bd)<1e-6,"Effective displacement mismatch under scaling: "+ad+" vs "+bd);
                        System.out.println("HAND_TEST_EFFECTIVE_MOVEMENT "+ad+" "+bd);
                    });next();
                }
                case 6 -> {
                    pass("Equal movement input produces equal world displacement with shared speed, including Pehkui motion multipliers when installed");
                    var server=mc.getSingleplayerServer();UUID viewer=mc.player.getUUID();
                    task=server.submit(()->{
                        HandServer.release(a,"");
                        check(Math.abs(a.getAttributeValue(Attributes.MOVEMENT_SPEED)-.15)<1e-6,"Speed was not restored after releasing");
                        check(a.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(HandServer.SPEED)==null,"Transient modifier leaked");
                        scale(a,1);scale(b,1);
                        place(a,-.5,80,0,180);place(b,.5,80,0,180);
                        var real=server.getPlayerList().getPlayer(viewer);real.teleportTo(.5,80,0);real.getAbilities().flying=false;real.onUpdateAbilities();
                    });pass("Release restores the exact original speed and removes the transient modifier");next();
                }
                case 7 -> {
                    var server=mc.getSingleplayerServer();UUID viewer=mc.player.getUUID();
                    task=server.submit(()->{var real=server.getPlayerList().getPlayer(viewer);request=HandServer.ask(a,real).token();});next();
                }
                case 8 -> {
                    Style accept=findAccept();if(accept==null)return;
                    mc.setScreen(new ChatScreen(""));shot("03-clickable-consent");
                    check(mc.screen.handleComponentClicked(accept),"Native chat component did not handle its click event");
                    next();
                }
                case 9 -> {
                    if(!HandClient.LINKS.containsKey(mc.player.getUUID()))return;
                    pass("The real client receives clickable chat consent; Minecraft's RUN_COMMAND click handler accepts it over the integrated connection");
                    mc.setScreen(null);
                    var server=mc.getSingleplayerServer();UUID viewer=mc.player.getUUID();
                    task=server.submit(()->{
                        var real=server.getPlayerList().getPlayer(viewer);
                        real.teleportTo(.5,83,0);real.xo=.5;real.yo=83;real.zo=0;place(a,-.5,83,0,180);
                        real.getAbilities().flying=true;real.getAbilities().setFlyingSpeed(.1f);real.onUpdateAbilities();
                        a.getAbilities().flying=true;a.getAbilities().setFlyingSpeed(.05f);
                    });stage=90;ticks=0;
                }
                case 90 -> {
                    if(Math.abs(HandClient.flightFactor(mc.player.getUUID(),false)-.5f)>.0001)return;
                    var method=net.minecraft.world.entity.player.Player.class.getDeclaredMethod("getFlyingSpeed");method.setAccessible(true);
                    check(Math.abs(((Number)method.invoke(mc.player)).floatValue()-.05f)<1e-6,"Client horizontal flight was not matched");
                    check(Math.abs(HandClient.flightFactor(mc.player.getUUID(),true)-.5f)<1e-6,"Vertical flight factor was not matched");
                    check(mc.player.getAbilities().flying,"Flight was disabled");
                    check(Math.abs(mc.player.getAbilities().getFlyingSpeed()-.1f)<1e-6,"Saved flying speed was modified");
                    pass("Upright flight stays linked; client flight prediction matches the slower partner without changing the saved flying speed");
                    mc.player.connection.sendCommand("handholding release");stage=91;ticks=0;
                }
                case 91 -> {
                    if(HandClient.LINKS.containsKey(mc.player.getUUID()))return;
                    check(HandClient.flightFactor(mc.player.getUUID(),false)==1,"Flight slowdown leaked after release");
                    check(Math.abs(mc.player.getAbilities().getFlyingSpeed()-.1f)<1e-6,"Flying speed was not preserved after release");
                    pass("Flight prediction returns immediately to its original speed after release");
                    var server=mc.getSingleplayerServer();UUID viewer=mc.player.getUUID();
                    task=server.submit(()->{var real=server.getPlayerList().getPlayer(viewer);real.getAbilities().flying=false;real.getAbilities().setFlyingSpeed(.05f);real.onUpdateAbilities();real.teleportTo(.5,80,0);a.getAbilities().flying=false;place(a,-.5,80,0,180);});
                    stage=10;ticks=0;
                }
                case 10 -> {
                    if(HandClient.LINKS.containsKey(mc.player.getUUID()))return;
                    pass("Release command synchronizes back to the real client");
                    var server=mc.getSingleplayerServer();
                    task=server.submit(()->{var r=HandServer.ask(b,a);HandServer.accept(a,r.token());place(b,1.38,80,0,180);graceProbeUntil=server.overworld().getGameTime()+4;});stage=92;ticks=0;
                }
                case 92 -> {
                    if(!graceProbeDone)return;
                    check(graceProbePassed,"A short loss of reach released the partnership");
                    pass("A four-tick reach gap recovers without releasing the link");
                    task=mc.getSingleplayerServer().submit(()->b.setPose(Pose.CROUCHING));stage=11;ticks=0;
                }
                case 11 -> {
                    var server=mc.getSingleplayerServer();
                    task=server.submit(()->{check(HandServer.state(server).pair(a.getUUID())==null,"Crouching did not release");b.setPose(Pose.STANDING);});
                    pass("Incompatible pose automatically releases and cleans up the partnership");next();
                }
                case 12 -> {
                    var server=mc.getSingleplayerServer();
                    // Wait for the normal request cooldown instead of bypassing it.
                    if(ticks<120)return;
                    task=server.submit(()->{HandInHand.MOVEMENT_TETHER.set(false);var r=HandServer.ask(a,b);HandServer.accept(b,r.token());place(b,5,80,0,180);});next();
                }
                case 13 -> {
                    var server=mc.getSingleplayerServer();
                    task=server.submit(()->{check(HandServer.state(server).pair(a.getUUID())==null,"Separation did not release");check(a.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(HandServer.SPEED)==null,"Separation left a modifier");});
                    pass("Separation/teleport automatically releases and restores speed");next();
                }
                case 14 -> {
                    var server=mc.getSingleplayerServer();
                    task=server.submit(()->{try{remove(server,a);remove(server,b);}catch(Exception ex){throw new RuntimeException(ex);}});next();
                }
                case 15 -> {
                    pass("Integration fixture completed; fake test participants are development-only");
                    report.append("COMPLETE\n");write();
                    complete=true;mc.stop();
                }
            }
        }catch(Throwable error){
            complete=true;report.append("FAIL at stage ").append(stage).append(": ").append(error).append('\n');
            try{write();}catch(Exception ignored){}error.printStackTrace();mc.stop();
        }
    }
}
