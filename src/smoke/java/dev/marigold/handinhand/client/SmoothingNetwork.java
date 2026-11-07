package dev.marigold.handinhand.client;
import dev.marigold.handinhand.*;
import net.minecraft.client.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import java.util.*;
import java.lang.reflect.*;
/** Identical recorded paths for baseline/candidate. Diagnostic sources never ship in gameplay. */
@EventBusSubscriber(modid=HandInHand.ID,value=Dist.CLIENT)
public final class SmoothingNetwork {
 static final boolean ENABLED=Boolean.getBoolean("handinhand.smoothingCheck");
 static final boolean STAIRS_ONLY=Boolean.getBoolean("handinhand.smoothingStairs");
 public static Vec3 rawCamera=Vec3.ZERO;
 static int phase,tick,lastJump=-1,lastPhase=-1,divergence,diagnosticPhase=-1;static boolean probe,gripA,gripB;static double peakA,peakB,gap;
 static final StringBuilder report=new StringBuilder(),camera=new StringBuilder("nanos,phase,step,x,y,z,ownY,grounded,controlled,rawX,rawY,rawZ\n"),details=new StringBuilder("phase,step,latestTick,sampledY,latestY,sampleGround,latestGround,ownY,localGround,controlled\n"),rtt=new StringBuilder("phase,step,rtt_ms\n"),poses=new StringBuilder("nanos,phase,step,id,rx,ry,rz,lx,ly,lz,leg,gait\n");
 static final Map<UUID,float[]> angles=new HashMap<>();
 static void note(String s){report.append(s).append('\n');PairNetworkTest.write("SMOOTHING-RESULTS.txt",report.toString());}
 static void check(boolean yes,String s){if(!yes)throw new AssertionError(s);note("PASS: "+s);}
 static void next(){note("MEASURE phase="+phase+", peaks="+peakA+"/"+peakB+", horizontalGap="+gap);phase++;tick=0;peakA=peakB=80;gap=0;}
 static void reset(MinecraftServer s,ServerPlayer a,ServerPlayer b){TerrainNetwork.reset(s,a,b);}
 static void link(ServerPlayer a,ServerPlayer b){TerrainNetwork.link(a,b,false);gripA=HandServer.heldSide(a);gripB=HandServer.heldSide(b);}
 static void input(MovementInputUpdateEvent e){var mc=Minecraft.getInstance();boolean alpha=PairNetworkTest.ROLE.equals("alpha");int p=PairNetworkTest.stage,n=PairNetworkTest.step;var in=e.getInput();in.forwardImpulse=in.leftImpulse=0;in.jumping=in.shiftKeyDown=false;mc.player.setSprinting(false);mc.player.setYRot(0);mc.player.setXRot(0);if(p!=lastPhase){lastPhase=p;lastJump=-1;}
  if(alpha&&(p==2||p==4||p==6||p==8||p==10)){in.forwardImpulse=1;if(p!=4&&p!=6&&mc.player.onGround()&&!mc.level.noCollision(mc.player,mc.player.getBoundingBox().move(0,0,.35)))in.jumping=true;if(p==6&&mc.player.onGround()&&n/40!=lastJump){in.jumping=true;lastJump=n/40;}}
 }
 static void client(){if(!ENABLED)return;var mc=Minecraft.getInstance();if(!probe){var before=ClockPayload.receiver;ClockPayload.receiver=p->{rtt.append(PairNetworkTest.stage).append(',').append(PairNetworkTest.step).append(',').append((System.nanoTime()-p.stamp())/1e6).append('\n');before.accept(p);};probe=true;}if(mc.player==null)return;if(PairNetworkTest.ROLE.equals("bravo"))mc.options.setCameraType(CameraType.FIRST_PERSON);
  try{Method tr=PairClient.class.getDeclaredMethod("track",UUID.class);tr.setAccessible(true);Object t=tr.invoke(null,mc.player.getUUID());if(t!=null){Method sample=PairClient.class.getDeclaredMethod("sample",t.getClass(),UUID.class);sample.setAccessible(true);var b=(PairFrame.Body)sample.invoke(null,t,mc.player.getUUID());Field frames=t.getClass().getDeclaredField("frames");frames.setAccessible(true);var f=(PairFrame)((ArrayDeque<?>)frames.get(t)).peekLast();var latest=mc.player.getUUID().equals(f.first())?f.a():f.b();int phaseNow=PairNetworkTest.stage;if(phaseNow!=diagnosticPhase){diagnosticPhase=phaseNow;divergence=0;}if(latest.controlled()&&phaseNow>=2&&phaseNow%2==0&&PairNetworkTest.step>35){double error=mc.player.position().distanceTo(new Vec3(b.x(),b.y(),b.z()));divergence=error>1.5?divergence+1:0;if(divergence>20){PairNetworkTest.write("FAIL.txt",PairNetworkTest.ROLE+": passive/local position diverged from sampled physical actor by "+error+" blocks");flush();return;}}details.append(PairNetworkTest.stage).append(',').append(PairNetworkTest.step).append(',').append(f.tick()).append(',').append(b.y()).append(',').append(latest.y()).append(',').append(b.grounded()).append(',').append(latest.grounded()).append(',').append(mc.player.getY()).append(',').append(mc.player.onGround()).append(',').append(PairClient.controlled(mc.player.getUUID())).append('\n');}}
  catch(ReflectiveOperationException ex){throw new RuntimeException(ex);}
  if(PairNetworkTest.localTick%40==0)flush();
 }
 static void flush(){String role=PairNetworkTest.ROLE;PairNetworkTest.write(role+"-camera.csv",camera.toString());PairNetworkTest.write(role+"-sampling.csv",details.toString());PairNetworkTest.write(role+"-RTT.csv",rtt.toString());PairNetworkTest.write(role+"-pose.csv",poses.toString());}
 @SubscribeEvent public static void model(RenderPlayerEvent.Post e){if(!ENABLED)return;var m=e.getRenderer().getModel();angles.put(e.getEntity().getUUID(),new float[]{m.rightArm.xRot,m.rightArm.yRot,m.rightArm.zRot,m.leftArm.xRot,m.leftArm.yRot,m.leftArm.zRot,m.leftLeg.xRot,e.getEntity().walkAnimation.speed()});}
 @SubscribeEvent public static void rendered(RenderLevelStageEvent e){if(!ENABLED||e.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;var mc=Minecraft.getInstance();if(mc.player==null)return;long now=System.nanoTime();var c=mc.gameRenderer.getMainCamera().getPosition();camera.append(now).append(',').append(PairNetworkTest.stage).append(',').append(PairNetworkTest.step).append(',').append(c.x).append(',').append(c.y).append(',').append(c.z).append(',').append(mc.player.getY()).append(',').append(mc.player.onGround()).append(',').append(PairClient.controlled(mc.player.getUUID())).append(',').append(rawCamera.x).append(',').append(rawCamera.y).append(',').append(rawCamera.z).append('\n');for(var item:angles.entrySet()){poses.append(now).append(',').append(PairNetworkTest.stage).append(',').append(PairNetworkTest.step).append(',').append(item.getKey());for(float f:item.getValue())poses.append(',').append(f);poses.append('\n');}angles.clear();}
 @SubscribeEvent public static void terrainCamera(RenderLevelStageEvent e){
  if(!TerrainNetwork.ENABLED||!PairNetworkTest.ROLE.equals("bravo")||PairNetworkTest.stage<2||e.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
  var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||!HandClient.LINKS.containsKey(mc.player.getUUID()))return;
  var pos=mc.gameRenderer.getMainCamera().getPosition();var at=net.minecraft.core.BlockPos.containing(pos);
  for(var box:mc.level.getBlockState(at).getCollisionShape(mc.level,at).toAabbs())if(box.move(at).deflate(.002).contains(pos))PairNetworkTest.write("FAIL.txt","Passive first-person camera entered solid terrain");
 }
 static void server(MinecraftServer s){var a=PairNetworkTest.named(s,"Pair_Alpha");var b=PairNetworkTest.named(s,"Pair_Bravo");if(a==null||b==null)return;tick++;PairNetworkTest.write("state.txt",phase+" "+tick+" 0");int delay=phase>=9?250:phase>=7?150:20;PairNetworkTest.write("NETWORK.json","{\"one_way_ms\":"+delay+",\"jitter_ms\":[0]}");peakA=Math.max(peakA,a.getY());peakB=Math.max(peakB,b.getY());gap=Math.max(gap,a.position().subtract(b.position()).horizontalDistance());
  if(phase==2||phase==4||phase==6||phase==8||phase==10){if(HandServer.state(s).pair(a.getUUID())==null)throw new AssertionError("Smoothing scenario lost link");if(gripA!=HandServer.heldSide(a)||gripB!=HandServer.heldSide(b))throw new AssertionError("Smoothing scenario swapped hands");}
  switch(phase){
   case 0->{if(tick<40)return;a.setGameMode(GameType.SURVIVAL);b.setGameMode(GameType.SURVIVAL);a.getAbilities().invulnerable=b.getAbilities().invulnerable=true;a.onUpdateAbilities();b.onUpdateAbilities();s.overworld().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_FALL_DAMAGE).set(false,s);reset(s,a,b);TerrainNetwork.hill(s);next();if(STAIRS_ONLY){TerrainNetwork.stairs(s);phase=3;}}
   case 1,3,5,7,9->{if(tick<120)return;link(a,b);next();}
   case 2->{if(tick<300)return;check(peakA>83.9&&peakB>83.9,"Baseline-latency full-block hill climbs with passive follower");check(a.getZ()>28&&b.getZ()>27,"Both finish hill");reset(s,a,b);TerrainNetwork.stairs(s);next();}
   case 4->{if(tick<360)return;check(peakA>87.9&&peakB>87.9,"Both walk up and down stairs");if(STAIRS_ONLY){HandServer.release(a,"");note("COMPLETE");PairNetworkTest.write("DONE.txt","COMPLETE");return;}reset(s,a,b);TerrainNetwork.clear(s);next();}
   case 6->{if(tick<240)return;check(peakA>81.1,"Leader repeatedly jumps while follower stays passive");check(gap<2.05,"Repeated leader jumps retain horizontal range");reset(s,a,b);TerrainNetwork.hill(s);next();}
   case 8->{if(tick<320)return;check(peakA>83.9&&peakB>83.9,"Full-block hill at medium latency");reset(s,a,b);next();}
   case 10->{if(tick<320)return;check(peakA>83.9&&peakB>83.9,"Full-block hill at high latency");HandServer.release(a,"");check(!HandServer.isControlled(b),"Release ends passive control");note("COMPLETE");PairNetworkTest.write("DONE.txt","COMPLETE");}
  }
 }
}
