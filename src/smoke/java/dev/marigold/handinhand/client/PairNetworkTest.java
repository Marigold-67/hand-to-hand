package dev.marigold.handinhand.client;
import dev.marigold.handinhand.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Three independent JVM clients: observing host and two real TCP participants. Release excludes this. */
@EventBusSubscriber(modid=HandInHand.ID,value=Dist.CLIENT)
public final class PairNetworkTest {
 static final String ROLE=System.getProperty("handinhand.pairRole","");
 static final Path SHARED=Path.of(System.getProperty("handinhand.pairDir","."));
 static int boot,localTick,phase,phaseTick;static boolean ready,finished,keySent;
 static volatile int stage,step;static volatile float yaw;
 static CompletableFuture<?> task;static ServerPlayer a,b;static int wallZ;static double highA,highB;
 static final StringBuilder report=new StringBuilder(),trace=new StringBuilder("localTick,stage,serverStep,ownX,ownY,ownZ,otherX,otherY,otherZ,bodyYaw,gait,controlled,ownGround,otherGround\n");
 static int renderFrame;
 static final Map<UUID,Integer> seen=new HashMap<>();
 static final Map<UUID,Float> legAngles=new HashMap<>();
 static final StringBuilder renderTrace=new StringBuilder("stage,step,gap,yawA,yawB,legA,legB\n");
 @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST) public static void model(RenderPlayerEvent.Post e){
  if(ROLE.isEmpty())return;seen.put(e.getEntity().getUUID(),renderFrame);legAngles.put(e.getEntity().getUUID(),e.getRenderer().getModel().leftLeg.xRot);
 }
 @SubscribeEvent public static void rendered(RenderLevelStageEvent e){
  if(ROLE.isEmpty()||e.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
  try{var mc=Minecraft.getInstance();var pa=mc.level.players().stream().filter(p->p.getGameProfile().getName().equals("Pair_Alpha")).findFirst().orElse(null);var pb=mc.level.players().stream().filter(p->p.getGameProfile().getName().equals("Pair_Bravo")).findFirst().orElse(null);
   if(pa!=null&&pb!=null&&Objects.equals(seen.get(pa.getUUID()),renderFrame)&&Objects.equals(seen.get(pb.getUUID()),renderFrame)){
    var ea=ClientSmoke.modelEndpoints.get(pa.getUUID());var eb=ClientSmoke.modelEndpoints.get(pb.getUUID());
    if(ea!=null&&eb!=null)renderTrace.append(stage).append(',').append(step).append(',').append(ea.sub(eb).length()).append(',').append(pa.yBodyRot).append(',').append(pb.yBodyRot).append(',').append(legAngles.get(pa.getUUID())).append(',').append(legAngles.get(pb.getUUID())).append('\n');
    if(renderFrame%40==0)Files.writeString(SHARED.resolve(ROLE+"-render.csv"),renderTrace);
   }
  }catch(Exception ignored){}renderFrame++;
 }
 static boolean initialA,initialB;static double maxAlong,maxSide,maxYawStep;static float lastYaw;static int samples;
 static void write(String name,String value){try{Files.createDirectories(SHARED);Files.writeString(SHARED.resolve(name),value);}catch(Exception e){throw new RuntimeException(e);}}
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 static void pass(String value){report.append("PASS: ").append(value).append('\n');write("SERVER-RESULTS.txt",report.toString());System.out.println("HAND_PAIR_PASS "+value);}
 static void next(){phase++;phaseTick=0;maxAlong=maxSide=0;samples=0;}
 static ServerPlayer named(net.minecraft.server.MinecraftServer server,String name){return server.getPlayerList().getPlayers().stream().filter(p->p.getGameProfile().getName().equals(name)).findFirst().orElse(null);}
 static void resetPosition(ServerPlayer player,double x,double y,double z){player.setDeltaMovement(Vec3.ZERO);player.connection.teleport(x,y,z,0,0);}
 @SubscribeEvent public static void input(MovementInputUpdateEvent e){
  if(ROLE.isEmpty()||ROLE.equals("host")||dev.marigold.handinhand.GrabConfigFixture.ENABLED)return;
  if(SmoothingNetwork.ENABLED){SmoothingNetwork.input(e);return;}if(TerrainNetwork.ENABLED){TerrainNetwork.input(e);return;}
  if(EnduranceNetwork.ENABLED){EnduranceNetwork.input(e);return;}
  if(MobilityNetwork.ENABLED){MobilityNetwork.input(e);return;}
  var mc=Minecraft.getInstance();boolean alpha=ROLE.equals("alpha");
  e.getInput().forwardImpulse=alpha&&(stage==2||stage==5||stage==63||stage==66)?1:alpha&&(stage==4||stage==64)?-1:!alpha&&(stage==6||stage==61||stage==62||stage==63||stage==64||stage==66)?1:!alpha&&stage>=9&&stage<=11?1:0;
  e.getInput().leftImpulse=alpha&&stage==3?1:0;
  e.getInput().jumping=alpha&&(stage==10&&step<8||stage==11);e.getInput().shiftKeyDown=stage==65&&(alpha||step>30)||stage==66;
  mc.player.setSprinting(alpha&&stage==2&&step>90);
  mc.player.setYRot(alpha&&stage==5?yaw:alpha&&stage==65&&step<=30?70:0);mc.player.setXRot(0);
  if(alpha&&stage==10&&step==9){mc.player.getAbilities().flying=true;mc.player.onUpdateAbilities();}
  if(alpha&&stage==9&&!keySent){var other=mc.level.players().stream().filter(p->p.getGameProfile().getName().equals("Pair_Bravo")).findFirst().orElse(null);
   if(other!=null){mc.hitResult=new EntityHitResult(other);net.minecraft.client.KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_G));keySent=true;}}
  if(!alpha&&stage==12&&!keySent){net.minecraft.client.KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_H));keySent=true;}
 }
 @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST) public static void client(ClientTickEvent.Post e){
  if(ROLE.isEmpty()||finished)return;var mc=Minecraft.getInstance();localTick++;if(SmoothingNetwork.ENABLED)SmoothingNetwork.client();if(TerrainNetwork.ENABLED)TerrainNetwork.client();if(EnduranceNetwork.ENABLED)EnduranceNetwork.client(mc);
  try{
   if(Files.exists(SHARED.resolve("FAIL.txt"))||Files.exists(SHARED.resolve("DONE.txt"))){finish();return;}
   if(localTick>24000)throw new AssertionError("Pair test timed out at stage "+stage);
   if(boot==0&&mc.screen instanceof TitleScreen){
    mc.getWindow().setTitle("Hand in Hand alpha.10 - "+ROLE+" TCP test");mc.options.hideGui=true;mc.options.pauseOnLostFocus=false;mc.options.onboardAccessibility=false;mc.options.renderDistance().set(ROLE.equals("host")?6:4);mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    if(ROLE.equals("host")){
     boot=1;var settings=new LevelSettings("Hand in Hand TCP test",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
     mc.createWorldOpenFlows().createFreshLevel("pair-"+System.currentTimeMillis(),settings,new WorldOptions(42,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),new TitleScreen());
    }else if(Files.exists(SHARED.resolve("PORT.txt"))){
     String address="127.0.0.1:"+System.getProperty("handinhand.pairPort");boot=2;
     ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString(address),new ServerData("Hand pair local test",address,ServerData.Type.LAN),false,null);
    }
   }
   if(ROLE.equals("host")&&boot==1&&mc.player!=null&&mc.getSingleplayerServer()!=null){boot=2;task=mc.getSingleplayerServer().submit(()->{try{
    var server=mc.getSingleplayerServer();var level=server.overworld();server.setUsesAuthentication(false);
    server.getConnection().startTcpServerListener(java.net.InetAddress.getByName("127.0.0.1"),Integer.getInteger("handinhand.hostPort"));
    for(int x=-40;x<=40;x++)for(int z=-40;z<=160;z++)level.setBlockAndUpdate(new BlockPos(x,79,z),Blocks.SMOOTH_STONE.defaultBlockState());
    level.setDayTime(6000);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
    var host=server.getPlayerList().getPlayer(mc.player.getUUID());resetPosition(host,0,81,-4);
    write("PORT.txt",System.getProperty("handinhand.hostPort"));ready=true;
   }catch(Exception error){throw new RuntimeException(error);}});}
   if(task!=null&&task.isDone()){task.join();task=null;}
   if(mc.player==null||mc.level==null)return;
   if(Files.exists(SHARED.resolve("state.txt"))){var parts=Files.readString(SHARED.resolve("state.txt")).trim().split(" ");if(parts.length==3){int old=stage;stage=Integer.parseInt(parts[0]);step=Integer.parseInt(parts[1]);yaw=Float.parseFloat(parts[2]);if(old!=stage)keySent=false;}}
   var pa=mc.level.players().stream().filter(p->p.getGameProfile().getName().equals("Pair_Alpha")).findFirst().orElse(null);
   var pb=mc.level.players().stream().filter(p->p.getGameProfile().getName().equals("Pair_Bravo")).findFirst().orElse(null);
   if(ROLE.equals("host")){if(pa!=null)mc.setCameraEntity(pa);return;}
   var other=ROLE.equals("alpha")?pb:pa;if(other==null)return;
   trace.append(localTick).append(',').append(stage).append(',').append(step).append(',').append(mc.player.getX()).append(',').append(mc.player.getY()).append(',').append(mc.player.getZ()).append(',').append(other.getX()).append(',').append(other.getY()).append(',').append(other.getZ()).append(',').append(mc.player.yBodyRot).append(',').append(mc.player.walkAnimation.speed()).append(',').append(PairClient.controlled(mc.player.getUUID())).append(',').append(mc.player.onGround()).append(',').append(other.onGround()).append('\n');
   if(localTick%20==0)Files.writeString(SHARED.resolve(ROLE+"-trace.csv"),trace);
   if((stage==2||stage==5||stage==63||stage==66||stage==11)&&step>=70&&step<72)ClientSmoke.shot(ROLE+"-stage-"+stage);
  }catch(Throwable error){write("FAIL.txt",ROLE+": "+error);error.printStackTrace();finish();}
 }
 @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST) public static void server(ServerTickEvent.Post e){
  if(!ROLE.equals("host")||!ready||finished||dev.marigold.handinhand.GrabConfigFixture.ENABLED)return;
  try{
   var server=e.getServer();if(SmoothingNetwork.ENABLED){SmoothingNetwork.server(server);return;}if(TerrainNetwork.ENABLED){TerrainNetwork.server(server);return;}if(EnduranceNetwork.ENABLED){EnduranceNetwork.server(server);return;}if(a==null)a=named(server,"Pair_Alpha");if(b==null)b=named(server,"Pair_Bravo");if(a==null||b==null)return;
   if(MobilityNetwork.ENABLED){MobilityNetwork.server(server,a,b);return;}
   phaseTick++;write("state.txt",phase+" "+phaseTick+" "+(phase==5?phaseTick*3:0));
   if(phase>=2&&phase<=6&&phaseTick>30){
    var pair=HandServer.state(server).pair(a.getUUID());check(pair!=null,"Partnership disappeared during phase "+phase);
    check(Objects.equals(HandServer.heldSide(a),initialA)&&Objects.equals(HandServer.heldSide(b),initialB),"Held hand changed");
    double angle=Math.toRadians(phase==6?b.yBodyRot:a.yBodyRot);var delta=b.position().subtract(a.position());
    maxAlong=Math.max(maxAlong,Math.abs(-delta.x*Math.sin(angle)+delta.z*Math.cos(angle)));samples++;
   }
   switch(phase){
    case 0->{if(phaseTick<20)return;
     a.setGameMode(GameType.CREATIVE);b.setGameMode(GameType.CREATIVE);resetPosition(a,-.45,80,0);resetPosition(b,.45,80,0);a.setYRot(0);b.setYRot(0);a.yBodyRot=b.yBodyRot=0;
     if(Boolean.getBoolean("handinhand.tetherScaled")){ClientSmoke.scale(a,.75f);ClientSmoke.scale(b,1.15f);}
     a.getAbilities().flying=false;b.getAbilities().flying=false;a.onUpdateAbilities();b.onUpdateAbilities();
     a.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(.1);b.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(.06);
     next();}
    case 1->{if(phaseTick<40)return;HandInHand.ROLEPLAY_GRAB.set(false);check(!HandServer.mayGrab(a),"Disabled grab accepted");
     HandInHand.ROLEPLAY_GRAB.set(true);HandInHand.GRAB_REQUIRES_OP.set(true);check(!HandServer.mayGrab(a),"Non-OP passed OP-only grab");
     boolean denied=false;try{server.getCommands().getDispatcher().execute("handholding config grab true",a.createCommandSourceStack());}catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){denied=true;}check(denied,"Non-OP changed server grab configuration");
     server.getCommands().getDispatcher().execute("handholding config grab true",server.createCommandSourceStack());
     check(HandInHand.ROLEPLAY_GRAB.get(),"Console enable command failed");
     server.getCommands().getDispatcher().execute("handholding config grabOpOnly false",server.createCommandSourceStack());check(!HandInHand.GRAB_REQUIRES_OP.get(),"Console OP-policy command failed");
     pass("OP-only configuration commands enforce permission and save settings");var request=HandServer.ask(a,b);HandServer.accept(b,request.token());initialA=HandServer.heldSide(a);initialB=HandServer.heldSide(b);pass("Real TCP clients joined; disabled and OP-only grab policies checked; normal consent accepted");next();}
    case 2->{if(phaseTick<140)return;check(samples>50&&maxAlong<.4,"Straight/sprint fore-aft separation "+maxAlong);pass("Walking and sprinting retain fixed hands and side-by-side server geometry (max fore-aft "+maxAlong+")");next();}
    case 3->{if(phaseTick<100)return;check(maxAlong<.4,"Strafing lagged behind "+maxAlong);pass("Actual sideways input remains free; passive partner follows alongside");next();}
    case 4->{if(phaseTick<80)return;check(maxAlong<.4,"Backwards following rotated or trailed "+maxAlong);pass("Backpedaling retains formation and fixed hands");next();}
    case 5->{if(phaseTick<120)return;check(maxAlong<.75,"Full turn lost side slot "+maxAlong);pass("Full turn keeps the same held hands and continuous formation");next();}
    case 6->{if(phaseTick<140)return;check(maxAlong<.4,"Leadership handover separation "+maxAlong);pass("The other real client takes over with the same grip and pair side ordering");
     wallZ=(int)Math.floor(b.getZ())+3;for(int x=-40;x<=40;x++)for(int y=80;y<=83;y++)server.overworld().setBlockAndUpdate(new BlockPos(x,y,wallZ),Blocks.STONE.defaultBlockState());phase=61;phaseTick=0;}
    case 61->{if(phaseTick<100)return;check(b.getBoundingBox().maxZ<=wallZ+.002&&a.getBoundingBox().maxZ<=wallZ+.002,"A collision box crossed the solid wall");check(Math.abs(b.getBoundingBox().maxZ-wallZ)<.1,"Leader did not reach wall");pass("Server-authoritative following retains normal wall collision");
     for(int x=-40;x<=40;x++)for(int y=80;y<=83;y++)server.overworld().setBlockAndUpdate(new BlockPos(x,y,wallZ),Blocks.AIR.defaultBlockState());
     var stepBlock=Boolean.getBoolean("handinhand.tetherScaled")?Blocks.SNOW.defaultBlockState().setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS,3):Blocks.SMOOTH_STONE_SLAB.defaultBlockState();
     for(int x=-40;x<=40;x++)for(int z=wallZ;z<=wallZ+4;z++)server.overworld().setBlockAndUpdate(new BlockPos(x,80,z),stepBlock);
     highA=highB=0;phase=62;phaseTick=0;}
    case 62->{highA=Math.max(highA,a.getY());highB=Math.max(highB,b.getY());if(phaseTick<100)return;check(highA>80.2&&highB>80.2,"Both actual players did not cross the step: "+highA+" / "+highB);pass("Both real players cross a size-appropriate step using normal collision");phase=63;phaseTick=0;maxAlong=0;}
    case 63->{if(phaseTick>40)maxAlong=Math.max(maxAlong,Math.abs(a.getZ()-b.getZ()));if(phaseTick<120)return;
     check(HandServer.state(server).pair(a.getUUID())!=null,"Both moving lost the link");check(maxAlong<.65,"Both moving with unequal speed lost shared pace: "+maxAlong);check(!HandServer.isControlled(a)&&!HandServer.isControlled(b),"Cooperative native inputs were still server-controlled");pass("Both real players steer together at unequal natural speeds; native ownership and shared pace retained (max fore-aft "+maxAlong+")");highA=a.getZ();phase=64;phaseTick=0;}
    case 64->{if(phaseTick<30)return;check(a.getZ()<highA-.1,"Opposed native input was overridden inside available reach");check(a.position().subtract(b.position()).horizontalDistance()<=2.05*HandTether.scale(a,b),"Opposed inputs stretched past the reach boundary");check(!HandServer.isControlled(a)&&!HandServer.isControlled(b),"Opposed inputs became force grabbing");check(HandServer.state(server).pair(a.getUUID())!=null,"Opposing inputs released holding");pass("Opposed controls remain native inside the reach boundary without releasing or becoming force grabbing");phase=65;phaseTick=0;}
    case 65->{if(phaseTick==30){check(a.isCrouching()&&!b.isCrouching(),"Single-player crouch input failed");check(HandServer.state(server).pair(a.getUUID())!=null,"One player sneaking released holding");check(Math.abs(net.minecraft.util.Mth.wrapDegrees(a.getYRot()-70))<5,"Passive free-look was overridden");pass("One participant can sneak and freely turn their camera while remaining linked");}if(phaseTick<60)return;check(a.isCrouching()&&b.isCrouching(),"Both test clients did not crouch");check(HandServer.state(server).pair(a.getUUID())!=null,"Sneaking released holding");check(Objects.equals(HandServer.heldSide(a),initialA)&&Objects.equals(HandServer.heldSide(b),initialB),"Sneaking switched hands");pass("Both clients can sneak without releasing or changing hands");phase=66;phaseTick=0;maxAlong=0;}
    case 66->{if(phaseTick>40)maxAlong=Math.max(maxAlong,Math.abs(a.getZ()-b.getZ()));if(phaseTick<100)return;check(HandServer.state(server).pair(a.getUUID())!=null,"Sneak-walking released holding");check(maxAlong<.65,"Cooperative sneak pace separated players: "+maxAlong);pass("Both clients can sneak-walk together at different natural speeds");HandServer.release(a,"");phase=7;phaseTick=0;}
    case 7->{if(phaseTick<100)return;resetPosition(a,-.45,80,0);resetPosition(b,.45,80,0);a.setYRot(0);b.setYRot(0);a.yBodyRot=b.yBodyRot=0;next();}
    case 8->{if(phaseTick<40)return;next();}
    case 9->{if(phaseTick<80)return;var pair=HandServer.state(server).pair(a.getUUID());check(pair!=null&&pair.grab()&&pair.first().equals(a.getUUID()),"G did not establish roleplay grab");pass("Separate G key establishes a server-authorized grab; target input cannot reverse leadership");b.setGameMode(GameType.SURVIVAL);next();}
    case 10->{if(phaseTick<30)return;next();}
    case 11->{if(phaseTick<100)return;check(b.getY()>85&&!b.getAbilities().mayfly&&!b.getAbilities().flying,"Flying leader did not lift the non-flying real target: "+b.position());pass("Real flying client lifts a real survival target without granting flight");highB=b.getY();
     // Preserve this disposable actor through the fall so the next policy test has a live target.
     b.getAbilities().invulnerable=true;b.onUpdateAbilities();next();}
    case 12->{if(phaseTick<40)return;check(HandServer.state(server).pair(b.getUUID())==null,"Target H did not release grab");check(!HandServer.isControlled(b)&&!HandServer.isLifted(b),"Release left movement authority behind");check(b.getY()<highB-1,"Released target did not fall");pass("Target H releases grab, gravity resumes, and authoritative movement state ends");
     next();}
    case 13->{if(phaseTick<110)return;a.getAbilities().flying=false;a.onUpdateAbilities();resetPosition(a,-.45,80,0);resetPosition(b,.45,80,0);next();}
    case 14->{if(phaseTick<40)return;HandServer.grab(a,b);server.getCommands().getDispatcher().execute("handholding config grab false",server.createCommandSourceStack());next();}
    case 15->{if(phaseTick<20)return;check(HandServer.state(server).pair(a.getUUID())==null,"Disabling roleplay grab did not end the active grab");pass("Disabling the server grab setting releases an existing grab and clears control");
     var configPath=net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("handinhand-server.toml");var disk=Files.readString(configPath);check(disk.substring(disk.indexOf("[roleplayGrab]")).contains("enabled = false"),"Command setting was not persisted to server config");pass("Disable command revokes live grab and persists to disk");
     report.append("COMPLETE\n");write("SERVER-RESULTS.txt",report.toString());write("DONE.txt","COMPLETE");}
   }
  }catch(Throwable error){write("FAIL.txt","server: "+error);error.printStackTrace();}
 }
 static void finish(){if(SmoothingNetwork.ENABLED)SmoothingNetwork.flush();finished=true;try{if(!ROLE.equals("host"))Files.writeString(SHARED.resolve(ROLE+"-trace.csv"),trace);Files.writeString(SHARED.resolve(ROLE+"-render.csv"),renderTrace);}catch(Exception ignored){}Minecraft.getInstance().stop();}
}
