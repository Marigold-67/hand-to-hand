package dev.marigold.handinhand.client;

import dev.marigold.handinhand.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Exercises the release mod's actual S2C impulses and LocalPlayer.move hook; no dummy-follow helper. */
@EventBusSubscriber(modid=HandInHand.ID,value=Dist.CLIENT)
public final class TetherSmoke {
    static int stage,ticks,total,wallZ;static boolean complete;
    static volatile boolean running,driveReal,ascendReal,descendReal,strafeReal;
    static volatile Vec3 driveFake=Vec3.ZERO;
    static volatile double maxGap,highestReal,highestFake;
    static UUID owner;static FakePlayer other;static CompletableFuture<?> task;
    static double startReal,startOther,startY;static HandClient.Link oldLink;
    static float realYaw;
    static volatile double turnJerk;
    static volatile double sideError,passivePeak;
    static volatile Vec3 lastRealMotion=Vec3.ZERO;
    static Vec3 previousClientPosition;
    static Vec3 previousReal,previousOther,velocityReal=Vec3.ZERO,velocityOther=Vec3.ZERO;
    static final StringBuilder report=new StringBuilder();
    record Delayed(int due,TetherMotion packet){}
    static final ArrayDeque<Delayed> delayed=new ArrayDeque<>();
    static void check(boolean condition,String text){if(!condition)throw new AssertionError(text);}
    static void write()throws Exception{Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("TETHER-RESULTS.txt"),report);}
    static void pass(String text)throws Exception{report.append("PASS: ").append(text).append('\n');write();System.out.println("HAND_TETHER_PASS "+text);}
    static void next(){stage++;ticks=0;}
    static boolean linked(){return HandClient.LINKS.containsKey(owner);}
    @SubscribeEvent public static void input(MovementInputUpdateEvent e){
        if(!Boolean.getBoolean("handinhand.tethertest")||owner==null||!e.getEntity().getUUID().equals(owner))return;
        e.getInput().forwardImpulse=driveReal?1:0;e.getInput().leftImpulse=strafeReal?1:0;e.getInput().jumping=ascendReal;e.getInput().shiftKeyDown=descendReal;
        e.getEntity().setSprinting(running&&driveReal);
    }
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGH) public static void physics(ServerTickEvent.Post e){
        if(!Boolean.getBoolean("handinhand.tethertest")||complete||other==null||owner==null)return;
        var real=e.getServer().getPlayerList().getPlayer(owner);if(real==null)return;
        if(driveFake.lengthSqr()>0)other.setDeltaMovement(driveFake);
        if(driveFake.lengthSqr()>0)ClientSmoke.activeFakeClients.add(other.getUUID());else ClientSmoke.activeFakeClients.remove(other.getUUID());
        ClientSmoke.fakePhysics(other);
        if(previousReal!=null)lastRealMotion=real.position().subtract(previousReal);
        if(stage>=4&&stage<=11||stage==50){maxGap=Math.max(maxGap,real.distanceTo(other));highestReal=Math.max(highestReal,real.getY());highestFake=Math.max(highestFake,other.getY());}
        if(stage==50&&previousReal!=null&&previousOther!=null){var vr=real.position().subtract(previousReal);var vo=other.position().subtract(previousOther);
            if(ticks>25)turnJerk=Math.max(turnJerk,Math.max(vr.subtract(velocityReal).horizontalDistance(),vo.subtract(velocityOther).horizontalDistance()));velocityReal=vr;velocityOther=vo;}
        if(stage==50&&ticks>25){var plan=ClientSmoke.fakeTargets.get(other.getUUID());if(plan!=null&&plan.following())sideError=Math.max(sideError,other.position().subtract(real.position().add(plan.x(),plan.y(),plan.z())).horizontalDistance());}
        previousReal=real.position();previousOther=other.position();
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        if(!Boolean.getBoolean("handinhand.tethertest")||complete)return;
        var mc=Minecraft.getInstance();ticks++;total++;
        try {
            if(mc.player!=null){if(stage==4&&previousClientPosition!=null)passivePeak=Math.max(passivePeak,mc.player.position().subtract(previousClientPosition).horizontalDistance());previousClientPosition=mc.player.position();}
            while(!delayed.isEmpty()&&delayed.peek().due()<=total)HandClient.receiveTether(delayed.remove().packet());
            if(total>4000)throw new AssertionError("Timed out at stage "+stage);
            if(mc.player!=null){mc.player.setYRot(realYaw);mc.player.setXRot(0);}
            if(ticks<20&&stage!=9)return;
            if(task!=null&&!task.isDone())return;
            if(task!=null){task.join();task=null;}
            switch(stage){
                case 0->{
                    if(mc.screen instanceof AccessibilityOnboardingScreen){mc.options.onboardAccessibility=false;mc.options.save();mc.setScreen(new TitleScreen());return;}
                    if(!(mc.screen instanceof TitleScreen))return;
                    int delay=Integer.getInteger("handinhand.tetherDelay",0);
                    if(delay>0)TetherMotion.receiver=p->delayed.add(new Delayed(total+delay,p));
                    mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(3);
                    var settings=new LevelSettings("Hand tether disposable test",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
                    next();mc.createWorldOpenFlows().createFreshLevel("tether-test-"+System.currentTimeMillis(),settings,new WorldOptions(42,false,false),
                        r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),new TitleScreen());
                }
                case 1->{
                    if(mc.player==null||mc.getSingleplayerServer()==null)return;
                    owner=mc.player.getUUID();var server=mc.getSingleplayerServer();
                    task=server.submit(()->{try{
                        var level=server.overworld();var real=server.getPlayerList().getPlayer(owner);
                        for(int x=-50;x<=50;x++)for(int z=-80;z<=80;z++)level.setBlockAndUpdate(new BlockPos(x,79,z),Blocks.SMOOTH_STONE.defaultBlockState());
                        level.setDayTime(6000);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
                        real.teleportTo(0,80,0);real.getAbilities().flying=false;real.onUpdateAbilities();
                        other=ClientSmoke.fake(level,new GameProfile(UUID.fromString("e93cd0ba-099a-4fc0-a6a1-07e22ad795c4"),"Tether_Partner"));
                        ClientSmoke.place(other,.95,80,0,180);ClientSmoke.add(server,other,real);ClientSmoke.physicsClients.add(other.getUUID());
                        if(Boolean.getBoolean("handinhand.tetherScaled")){ClientSmoke.scale(real,.75f);ClientSmoke.scale(other,1.15f);}
                        HandInHand.MOVEMENT_TETHER.set(true);
                    }catch(Exception error){throw new RuntimeException(error);}});next();
                }
                case 2->{
                    if(mc.level.getPlayerByUUID(other.getUUID())==null)return;
                    task=mc.getSingleplayerServer().submit(()->{var real=mc.getSingleplayerServer().getPlayerList().getPlayer(owner);
                        other.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(.04);
                        double natural=real.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
                        var r=HandServer.ask(real,other);HandServer.accept(other,r.token());
                        check(Math.abs(real.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)-natural)<1e-8,"A slow partner reduced the leader's own movement speed");
                        check(Math.abs(other.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)-.04)<1e-8,"Following rewrote a natural movement attribute");
                    });next();
                }
                case 3->{
                    if(!linked())return;
                    oldLink=HandClient.LINKS.get(owner);startReal=mc.player.getZ();startOther=other.getZ();maxGap=0;
                    var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),mc.level.registryAccess());
                    try{var plan=new TetherMotion(other.getUUID(),oldLink.started(),123,true,.9,-.3,.05,.65,true,.2,.15,-.1,1234,88,-5678);TetherMotion.CODEC.encode(buffer,plan);check(plan.equals(TetherMotion.CODEC.decode(buffer)),"Lift/pace/anchor payload codec round-trip failed");}finally{buffer.release();}
                    pass("Natural movement attributes remain unchanged with an unusually slow partner; protocol 5 lift and pace fields round-trip through the registered codec");
                    driveFake=new Vec3(0,0,-.20);next();
                }
                case 4->{
                    if(ticks==40){
                        var field=HandClient.class.getDeclaredField("tetherTarget");field.setAccessible(true);var plan=(TetherMotion)field.get(null);
                        check(plan!=null,"No following plan reached the real client");var velocity=mc.player.getDeltaMovement();
                        for(int i=0;i<64;i++)HandClient.receiveTether(plan);
                        HandClient.receiveTether(new TetherMotion(plan.partner(),plan.started(),plan.frameTick()-1,true,-4,0,0,.4));
                        check(field.get(null).equals(plan)&&mc.player.getDeltaMovement().equals(velocity),"Duplicate or out-of-order frames changed velocity or replaced the plan");
                        pass("Duplicate and out-of-order following plans neither stack velocity nor replace the current plan");
                    }
                    if(ticks<140)return;driveFake=Vec3.ZERO;
                    check(linked(),"Recipient-led dragging released the link");
                    check(mc.player.getZ()<startReal-5,"The idle real client did not get dragged: "+mc.player.position());
                    check(maxGap<1.85,"Tether stretched too far while dragging: "+maxGap);
                    check(passivePeak<=.81,"Following exceeded its per-client-tick speed cap: "+passivePeak);
                    var field=HandClient.class.getDeclaredField("tetherTarget");field.setAccessible(true);var plan=(TetherMotion)field.get(null);
                    var offset=mc.player.position().subtract(mc.level.getPlayerByUUID(other.getUUID()).position());
                    check(plan!=null&&offset.x*plan.x()+offset.z*plan.z()>.05,"Following crossed to the wrong side: "+offset+" plan="+plan);
                    pass("The accepting partner drags an idle real LocalPlayer over five blocks using production motion packets; link and collision constraint remain active (max gap "+maxGap+")");
                    stage=40;ticks=0;
                }
                case 40->{
                    if(ticks<40)return;
                    check(lastRealMotion.horizontalDistance()<.035&&mc.player.getDeltaMovement().horizontalDistance()<.04,"Passive following retained movement after the leader stopped: "+lastRealMotion);
                    pass("The passive partner matches the leader's walking speed, stays on the same side, and settles without stored tether momentum (peak speed "+passivePeak+")");
                    startReal=mc.player.getZ();startOther=other.getZ();driveReal=true;running=true;maxGap=0;stage=5;ticks=0;
                }
                case 5->{
                    if(ticks<140)return;driveReal=false;running=false;
                    check(linked(),"Sender-led running released the link");
                    check(mc.player.getZ()>startReal+5&&other.getZ()>startOther+5,"Running real client did not pull its partner: "+startReal+" -> "+mc.player.getZ()+" / "+startOther+" -> "+other.getZ());
                    check(maxGap<1.85,"Running tether stretched too far: "+maxGap);
                    check(oldLink.started()==HandClient.LINKS.get(owner).started(),"Leading handover silently replaced the partnership");
                    pass("The original sender takes over and sprints, pulling its partner back without reacceptance (max gap "+maxGap+")");
                    driveReal=true;running=false;turnJerk=0;sideError=0;maxGap=0;stage=50;ticks=0;
                }
                case 50->{
                    realYaw=ticks*3;
                    if(ticks<120)return;
                    check(linked(),"Turning released the hand tether");
                    var link=HandClient.LINKS.get(owner);
                    check(link.started()==oldLink.started(),"Turning replaced the accepted partnership");
                    check(turnJerk<.30,"Turning caused an abrupt corrective displacement: "+turnJerk);
                    check(sideError<.45,"The passive partner lost its side position during the turn: "+sideError);
                    pass("A full turn retains the partnership and side-following position with a gradually adjusting grip (max slot error "+sideError+", horizontal velocity change "+turnJerk+")");
                    realYaw=0;driveReal=false;strafeReal=true;startReal=mc.player.getX();startOther=other.getX();stage=51;ticks=0;
                }
                case 51->{
                    if(ticks==30){var own=new Vec3(-.22,-.078,.08);var before=mc.player.getDeltaMovement();
                        check(HandClient.tetherMovement(mc.player,own).equals(own)&&before.equals(mc.player.getDeltaMovement()),"Active strafing was braked or changed stored velocity");
                    }
                    if(ticks<80)return;strafeReal=false;
                    check(mc.player.getX()>startReal+5&&other.getX()>startOther+5,"Sideways movement was obstructed: "+startReal+" -> "+mc.player.getX()+" / "+startOther+" -> "+other.getX());
                    check(Math.abs(mc.player.getZ()-other.getZ())<.35,"Strafing put the partner behind instead of beside the leader");
                    pass("Real A-key strafing moves both partners over five blocks; active movement and stored velocity pass through unchanged, with less than .35 blocks of fore-aft separation");
                    driveReal=true;driveFake=new Vec3(0,0,-.20);maxGap=0;stage=6;ticks=0;
                }
                case 6->{
                    if(ticks<20)return;driveReal=false;driveFake=Vec3.ZERO;
                    check(linked()&&maxGap>3,"Opposing inputs were artificially braked: "+maxGap);
                    pass("Opposing active controls remain free of distance braking; the partnership stays accepted");
                    wallZ=(int)Math.floor(mc.player.getZ())+3;
                    task=mc.getSingleplayerServer().submit(()->{var server=other.server;var real=server.getPlayerList().getPlayer(owner);
                        HandServer.release(real,"");ClientSmoke.place(other,real.getX()+.95,real.getY(),real.getZ(),0);other.setDeltaMovement(Vec3.ZERO);
                        var r=HandServer.ask(real,other);HandServer.accept(other,r.token());
                        var level=other.server.overworld();for(int x=-40;x<=40;x++)for(int y=80;y<=83;y++)level.setBlockAndUpdate(new BlockPos(x,y,wallZ),Blocks.STONE.defaultBlockState());});
                    driveReal=true;running=true;maxGap=0;next();
                }
                case 7->{
                    if(ticks<100||!mc.player.horizontalCollision&&ticks<220)return;driveReal=false;running=false;
                    check(linked(),"The wall released the tether");
                    check(mc.player.horizontalCollision,"The leader did not reach the collision wall: "+mc.player.position()+" wall z="+wallZ);
                    check(mc.player.getBoundingBox().maxZ<=wallZ+.001&&other.getBoundingBox().maxZ<=wallZ+.001,"A player's collision box passed through the wall");
                    pass("A solid wall blocks the tethered players through normal collision; no teleporting or unlinking");
                    task=mc.getSingleplayerServer().submit(()->{var level=other.server.overworld();for(int x=-40;x<=40;x++)for(int y=80;y<=83;y++)level.setBlockAndUpdate(new BlockPos(x,y,wallZ),Blocks.AIR.defaultBlockState());
                        var step=Boolean.getBoolean("handinhand.tetherScaled")?Blocks.SNOW.defaultBlockState().setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS,3):Blocks.SMOOTH_STONE_SLAB.defaultBlockState();
                        for(int x=-40;x<=40;x++)for(int z=wallZ;z<=wallZ+4;z++)level.setBlockAndUpdate(new BlockPos(x,80,z),step);});
                    highestReal=0;highestFake=0;driveReal=true;next();
                }
                case 8->{
                    double stepThreshold=Boolean.getBoolean("handinhand.tetherScaled")?80.2:80.4;
                    if(ticks<70||!(highestReal>stepThreshold&&highestFake>stepThreshold)&&ticks<180)return;driveReal=false;
                    check(linked()&&highestReal>stepThreshold&&highestFake>stepThreshold,"Tethered participants did not climb the step: "+highestReal+" / "+highestFake);
                    pass("The leader and dragged partner climb a size-appropriate step with ordinary step collision");
                    startY=mc.player.getY();ascendReal=true;
                    next();
                }
                case 9->{
                    if(mc.player.onGround()||mc.player.getY()<startY+.15)return;
                    // Creative flight normally starts during a jump; enabling it while grounded is canceled by vanilla.
                    mc.player.getAbilities().flying=true;mc.player.onUpdateAbilities();ascendReal=false;
                    task=mc.getSingleplayerServer().submit(()->{var real=other.server.getPlayerList().getPlayer(owner);real.getAbilities().flying=true;real.onUpdateAbilities();other.getAbilities().flying=true;other.setDeltaMovement(Vec3.ZERO);});
                    stage=90;ticks=0;
                }
                case 90->{
                    if(!HandClient.LINKS.get(owner).verticalTether()){if(ticks>80)throw new AssertionError("Flight mode did not synchronize");return;}
                    startY=mc.player.getY();maxGap=0;driveFake=new Vec3(0,.16,0);next();
                    stage=10;
                }
                case 10->{
                    if(ticks<100)return;driveFake=Vec3.ZERO;
                    check(linked()&&mc.player.getY()>startY+3,"Upright flight did not drag the idle real client upward");
                    check(maxGap<1.85,"Vertical tether stretched too far: "+maxGap);
                    pass("An already flying partner pulls the idle real client upward, with server-synchronized vertical tether state");
                    startY=other.getY();ascendReal=true;maxGap=0;next();
                }
                case 11->{
                    if(ticks<100)return;ascendReal=false;
                    check(linked()&&other.getY()>startY+3,"The real flying client did not pull the partner upward");
                    check(mc.player.getAbilities().flying&&Math.abs(mc.player.getAbilities().getFlyingSpeed()-.05f)<1e-6,"Tether changed saved flight abilities");
                    pass("The real flying client can take over and pull its partner vertically; saved flying speed and permissions are preserved");
                    startY=other.getY();descendReal=true;stage=110;ticks=0;
                }
                case 110->{
                    if(ticks<100)return;descendReal=false;
                    check(linked()&&other.getY()<startY-3,"Descending flight did not pull the partner downward");
                    pass("Descending in upright flight pulls the partner downward without treating the flight control as grounded crouching");
                    stage=109;ticks=0;
                }
                case 109->{
                    if(ticks<30)return;
                    task=mc.getSingleplayerServer().submit(()->{var real=other.server.getPlayerList().getPlayer(owner);
                        real.setGameMode(GameType.SURVIVAL);real.getAbilities().flying=false;real.getAbilities().mayfly=false;real.onUpdateAbilities();other.getAbilities().flying=true;});
                    driveFake=new Vec3(0,.12,0);startY=mc.player.getY();stage=111;ticks=0;
                }
                case 111->{
                    if(ticks<100)return;driveFake=Vec3.ZERO;
                    check(linked()&&mc.player.getY()>startY+5,"A flying leader did not lift the passive survival client: linked="+linked()+" y="+mc.player.getY()+" start="+startY+" other="+other.position()+" pose="+mc.player.getPose());
                    check(!mc.player.getAbilities().flying&&!mc.player.getAbilities().mayfly,"Lift granted flight permission");
                    task=mc.getSingleplayerServer().submit(()->{try{var real=other.server.getPlayerList().getPlayer(owner);
                        check(HandServer.isLifted(real),"Server did not recognize the current passive lift");
                        var floating=net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("clientIsFloating");floating.setAccessible(true);floating.setBoolean(real.connection,true);
                        var count=net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("aboveGroundTickCount");count.setAccessible(true);count.setInt(real.connection,79);
                    }catch(Exception ex){throw new RuntimeException(ex);}});stage=112;ticks=0;
                }
                case 112->{
                    if(ticks<100)return;
                    check(linked()&&mc.player.connection.getConnection().isConnected(),"Lifted player was kicked for floating");
                    check(!mc.player.getAbilities().flying&&!mc.player.getAbilities().mayfly,"Hover granted flight permission");
                    pass("A flying leader lifts and hovers a passive survival LocalPlayer with no flight ability; the scoped floating-kick exemption survives a forced 79-tick counter and 100 more ticks");
                    startY=mc.player.getY();mc.player.connection.sendCommand("handholding release");stage=113;ticks=0;
                }
                case 113->{
                    if(ticks<20)return;
                    check(!linked()&&mc.player.getY()<startY-2,"Releasing an aerial lift did not restore gravity");
                    check(!mc.player.getAbilities().flying&&!mc.player.getAbilities().mayfly,"Release left flight permissions behind");
                    task=mc.getSingleplayerServer().submit(()->{var real=other.server.getPlayerList().getPlayer(owner);check(!HandServer.isLifted(real),"Floating exemption leaked after release");real.setGameMode(GameType.CREATIVE);real.getAbilities().flying=true;real.onUpdateAbilities();});
                    pass("Releasing the aerial lift immediately restores gravity and removes the scoped floating exemption without granting flight");stage=12;ticks=0;
                }
                case 12->{
                    if(linked())return;
                    var velocity=mc.player.getDeltaMovement();
                    HandClient.receiveTether(new TetherMotion(other.getUUID(),oldLink.started(),.2,.2,.2));
                    check(mc.player.getDeltaMovement().equals(velocity),"A stale post-release motion packet affected the player");
                    pass("Explicit release removes the tether immediately and discards stale motion packets");
                    driveReal=true;startReal=mc.player.getZ();next();
                }
                case 13->{
                    if(ticks<60)return;driveReal=false;
                    check(!linked()&&mc.player.distanceTo(mc.level.getPlayerByUUID(other.getUUID()))>3,"Movement remained constrained after release");
                    pass("Normal independent movement resumes after releasing");
                    var server=mc.getSingleplayerServer();task=server.submit(()->{var real=server.getPlayerList().getPlayer(owner);real.teleportTo(other.getX()+.9,other.getY(),other.getZ());real.setDeltaMovement(Vec3.ZERO);});next();
                }
                case 14->{
                    var server=mc.getSingleplayerServer();task=server.submit(()->{var real=server.getPlayerList().getPlayer(owner);var r=HandServer.ask(real,other);HandServer.accept(other,r.token());});next();
                }
                case 15->{
                    if(!linked())return;
                    task=mc.getSingleplayerServer().submit(()->ClientSmoke.place(other,other.getX()+12,other.getY(),other.getZ(),180));next();
                }
                case 16->{
                    if(linked())return;
                    pass("A genuine twelve-block relocation releases the tether and restores independent movement");
                    var server=mc.getSingleplayerServer();task=server.submit(()->{try{ClientSmoke.physicsClients.remove(other.getUUID());ClientSmoke.remove(server,other);}catch(Exception error){throw new RuntimeException(error);}});next();
                }
                case 17->{report.append("COMPLETE\n");write();complete=true;mc.stop();}
            }
        }catch(Throwable error){complete=true;driveReal=false;driveFake=Vec3.ZERO;ascendReal=false;report.append("FAIL at stage ").append(stage).append(": ").append(error).append('\n');try{write();}catch(Exception ignored){}error.printStackTrace();mc.stop();}
    }
}
