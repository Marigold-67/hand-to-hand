package dev.marigold.handinhand.client;
import dev.marigold.handinhand.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.server.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import java.nio.file.*;
import java.util.*;

/** Long-lived, real-TCP pair and directed movement/lifecycle scenarios; never in release builds. */
final class EnduranceNetwork {
 static final boolean ENABLED=Boolean.getBoolean("handinhand.endurance");
 static int phase,tick,total,clientPhase=-1,lastJump=-1;static long began=System.nanoTime(),session;
 static boolean gripA,gripB,probeInstalled,departed,reconnecting,logoutObserved;static int disconnectedAt;
 static boolean grabReleaseSent,airReached;static int flightTicks,liftTicks,landings;static double targetPeak;
 static int checks,failures,releases,unexpected;static double maxGap,minY=80,maxY=80,coursePeak=80;static String lastNetwork="";
 static final StringBuilder report=new StringBuilder(),serverTrace=new StringBuilder("total,phase,step,ax,ay,az,bx,by,bz,gap,controlA,controlB,groundA,groundB\n"),rtts=new StringBuilder("phase,step,rtt_ms\n"),camera=new StringBuilder("tick,phase,step,x,y,z,yaw,pitch,firstperson\n");
 static final Path dir=PairNetworkTest.SHARED;
 static void write(String n,String s){PairNetworkTest.write(n,s);}
 static void note(String s){report.append(s).append('\n');write("ENDURANCE-RESULTS.txt",report.toString());}
 static void expect(boolean ok,String s){checks++;if(!ok)failures++;note((ok?"PASS: ":"FAIL: ")+s);}
 static void next(){note("PHASE "+phase+" END: ticks="+tick+", peakGap="+maxGap+", Y range="+minY+".."+maxY);phase++;tick=0;maxGap=0;minY=80;maxY=80;lastNetwork="";}
 static void network(String name,int ms,String jitter,int extra){String value="{\"name\":\""+name+"\",\"one_way_ms\":"+ms+",\"jitter_ms\":"+jitter+",\"extra_delay_ms\":"+extra+"}";if(!value.equals(lastNetwork)){write("NETWORK.json",value);lastNetwork=value;}}
 static void reset(MinecraftServer s,ServerPlayer a,ServerPlayer b){if(HandServer.state(s).pair(a.getUUID())!=null)HandServer.release(a,"");PairNetworkTest.resetPosition(a,-.45,80,0);PairNetworkTest.resetPosition(b,.45,80,0);a.yBodyRot=b.yBodyRot=0;}
 static void link(ServerPlayer a,ServerPlayer b){var r=HandServer.ask(a,b);var p=HandServer.accept(b,r.token());session=p.started();gripA=HandServer.heldSide(a);gripB=HandServer.heldSide(b);}
 static boolean linked(MinecraftServer s,ServerPlayer a){return HandServer.state(s).pair(a.getUUID())!=null;}
 static int duration(){return phase>=2&&phase<=7?2000:phase==11?800:phase==12?600:400;}
 static float route(ServerPlayer active){
  double yaw=phase==2?(tick%480<=240?(tick%480)*1.5:(480-tick%480)*1.5):tick*1.5;
  if(Math.abs(active.getX())>28||Math.abs(active.getZ())>28)yaw=Math.toDegrees(Math.atan2(active.getX(),-active.getZ()));
  if(phase>=8)yaw=0;return(float)yaw;
 }
 static void input(MovementInputUpdateEvent e){
  var mc=Minecraft.getInstance();boolean alpha=PairNetworkTest.ROLE.equals("alpha");int p=PairNetworkTest.stage,n=PairNetworkTest.step;
  if(clientPhase!=p){clientPhase=p;lastJump=-1;grabReleaseSent=false;}
  var in=e.getInput();in.forwardImpulse=in.leftImpulse=0;in.jumping=in.shiftKeyDown=false;
  boolean activeA=p!=6||n%240<120,leader=alpha==activeA;
  float yaw=PairNetworkTest.yaw;mc.player.setYRot(leader?yaw:(float)(Math.sin(n*.025)*110));mc.player.setXRot(leader?0:(float)(Math.sin(n*.019)*35));
  mc.player.setSprinting(false);
  if(p>=8&&p<=12){if(n<140)return;n-=140;}
  if(p>=2&&p<=7){
   if(p==5){in.forwardImpulse=1;in.leftImpulse=alpha?0:(n%160<80?.25f:-.25f);mc.player.setYRot(yaw);}
   else if(leader){in.forwardImpulse=1;if(p==3){in.forwardImpulse=n%30<20?1:0;in.leftImpulse=n%30<20?(n%80<40?.7f:-.7f):0;mc.player.setSprinting(n%160<80);}if(p==7&&n%120<60){in.forwardImpulse=-1;mc.player.setYRot(yaw+180);}}
   if(p==7)in.shiftKeyDown=alpha?n%240<80:n%160<80;
   if(!alpha&&(p==4||p==5)){int cycle=n/35;if(cycle!=lastJump&&n%35>=6){in.jumping=true;lastJump=cycle;}}
  }
  if(p==8){if(alpha){in.forwardImpulse=n<65||n>125?1:0;in.leftImpulse=n>=65&&n<=125?-1:0;}mc.player.setYRot(alpha?0:60);}
  if(p==9&&alpha)in.forwardImpulse=1;
  if(p==10){in.shiftKeyDown=true;if(alpha){in.forwardImpulse=n<80||n>160?1:0;in.leftImpulse=n>=80&&n<=160?-1:0;}}
  if(p==11){if(alpha){int cycle=n%160;in.jumping=cycle>=6&&cycle<30;if(cycle>=9&&cycle<=14&&!mc.player.getAbilities().flying){mc.player.getAbilities().flying=true;mc.player.onUpdateAbilities();}in.forwardImpulse=cycle<100?.3f:0;mc.player.setYRot(n*2);in.shiftKeyDown=cycle>=60&&cycle<95;}else in.shiftKeyDown=n%160>=35&&n%160<50;}
  if(p==12){boolean positioning=n>=190&&n<270;if(alpha){in.forwardImpulse=positioning?0:.6f;mc.player.setYRot(n*3);}else{in.leftImpulse=positioning?0:n%60<30?1:-1;in.jumping=!positioning&&n%35==5;in.shiftKeyDown=!positioning&&n%120>80;if(n>=80&&!grabReleaseSent){grabReleaseSent=true;KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_H));}}}
  if(!alpha)mc.options.setCameraType(p%2==0?CameraType.FIRST_PERSON:CameraType.THIRD_PERSON_BACK);
 }
 static void client(Minecraft mc){
  if(!probeInstalled&&!PairNetworkTest.ROLE.isEmpty()){
   var previous=ClockPayload.receiver;ClockPayload.receiver=p->{double ms=(System.nanoTime()-p.stamp())/1_000_000d;if(ms>=0&&ms<10000)rtts.append(PairNetworkTest.stage).append(',').append(PairNetworkTest.step).append(',').append(ms).append('\n');previous.accept(p);};probeInstalled=true;
  }
  int p=PairNetworkTest.stage,n=PairNetworkTest.step;
  try{
   if(mc.player!=null&&mc.level!=null){camera.append(PairNetworkTest.localTick).append(',').append(p).append(',').append(n).append(',').append(mc.player.getX()).append(',').append(mc.player.getY()).append(',').append(mc.player.getZ()).append(',').append(mc.player.getYRot()).append(',').append(mc.player.getXRot()).append(',').append(mc.options.getCameraType()==CameraType.FIRST_PERSON).append('\n');}
   if(PairNetworkTest.localTick%100==0){write(PairNetworkTest.ROLE+"-RTT.csv",rtts.toString());write(PairNetworkTest.ROLE+"-CAMERA.csv",camera.toString());}
   if(PairNetworkTest.ROLE.equals("bravo")&&p==13&&!departed&&mc.getConnection()!=null){departed=true;disconnectedAt=PairNetworkTest.localTick;mc.getConnection().getConnection().disconnect(Component.literal("Disposable endurance reconnect test"));}
   if(departed&&!reconnecting&&mc.player==null&&PairNetworkTest.localTick-disconnectedAt>40){reconnecting=true;String address="127.0.0.1:"+System.getProperty("handinhand.pairPort");ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString(address),new ServerData("Endurance reconnect",address,ServerData.Type.LAN),false,null);}
  }catch(Exception err){write("FAIL.txt","Endurance client: "+err);}
 }
 static void server(MinecraftServer s)throws Exception{
  var a=PairNetworkTest.named(s,"Pair_Alpha");var b=PairNetworkTest.named(s,"Pair_Bravo");total++;tick++;
  if(phase==13&&a!=null&&b==null&&!logoutObserved){logoutObserved=true;expect(!linked(s,a)&&!HandServer.isControlled(a)&&!HandServer.isLifted(a),"Real target disconnect clears relationship/control/lift");}
  if(a==null||b==null){if(tick>1000)throw new AssertionError("Participants did not join/rejoin");return;}
  float heading=route(phase==6&&tick%240>=120?b:a);write("state.txt",phase+" "+tick+" "+heading);
  if(phase<=2)network("150ms-target",75,"[0]",0);
  else if(phase<=4)network("300ms-target",150,"[0]",0);
  else if(phase<=6)network("500ms-target",250,"[0]",0);
  else if(phase==7)network(tick%600>=450&&tick%600<455?"stall-burst":"300ms-jitter",150,"[-40,0,30,70,0,-20]",tick%600>=450&&tick%600<455?650:0);
  else network("200ms-jitter",100,"[-20,0,30,0]",0);
  double gap=a.distanceTo(b);maxGap=Math.max(maxGap,gap);minY=Math.min(minY,Math.min(a.getY(),b.getY()));maxY=Math.max(maxY,Math.max(a.getY(),b.getY()));
  serverTrace.append(total).append(',').append(phase).append(',').append(tick).append(',').append(a.getX()).append(',').append(a.getY()).append(',').append(a.getZ()).append(',').append(b.getX()).append(',').append(b.getY()).append(',').append(b.getZ()).append(',').append(gap).append(',').append(HandServer.isControlled(a)).append(',').append(HandServer.isControlled(b)).append(',').append(a.onGround()).append(',').append(b.onGround()).append('\n');
  if(total%100==0)write("SERVER-TRACE.csv",serverTrace.toString());
  if(phase>=2&&phase<=11&&tick>(phase>=8?140:20)){
   if(!linked(s,a)){unexpected++;note("FAIL: unexpected release phase="+phase+", tick="+tick);failures++;next();return;}
   if(HandServer.heldSide(a)!=gripA||HandServer.heldSide(b)!=gripB)throw new AssertionError("Held hand changed during linked scenario");
   if(!Double.isFinite(gap)||Math.abs(a.getX())>10000||Math.abs(b.getZ())>10000)throw new AssertionError("Non-finite or runaway position");
  }
  switch(phase){
   case 0->{if(tick<60)return;a.setGameMode(GameType.SURVIVAL);b.setGameMode(GameType.SURVIVAL);a.getAbilities().invulnerable=b.getAbilities().invulnerable=true;a.onUpdateAbilities();b.onUpdateAbilities();s.overworld().getGameRules().getRule(GameRules.RULE_FALL_DAMAGE).set(false,s);reset(s,a,b);next();}
   case 1->{if(tick<100)return;if(Boolean.getBoolean("handinhand.enduranceDirected")){note("START: directed flight and confirmed linked-disconnect recheck.");phase=11;tick=0;began=System.nanoTime();}else{link(a,b);note("START: ten-minute continuous pair. Actual ClockPayload echo RTT is captured from each client.");began=System.nanoTime();next();}}
   case 2,3,4,5,6,7->{if(tick%400==0)note("CHECKPOINT: phase="+phase+", step="+tick+", elapsed="+((System.nanoTime()-began)/1e9)+", gap="+gap);if(tick<duration())return;expect(linked(s,a),"Continuous movement phase "+phase+" retained the session and fixed hands");if(phase==7){expect(total>=12000,"Completed at least 12000 linked movement ticks before directed courses");HandServer.release(a,"");}next();}
   case 8->{if(tick==1){reset(s,a,b);s.overworld().setBlockAndUpdate(new BlockPos(0,80,3),Blocks.STONE.defaultBlockState());s.overworld().setBlockAndUpdate(new BlockPos(0,81,3),Blocks.STONE.defaultBlockState());}if(tick==120)link(a,b);if(tick<120)return;
    // First 120 ticks are setup/cooldown; the route then naturally goes past and around the obstruction.
    if(tick<400)return;expect(linked(s,a),"One-sided wall obstruction retains the link");expect(s.overworld().noCollision(b,b.getBoundingBox().deflate(.001)),"Follower does not phase through solid wall");note("OBSERVED: wall scenario peak gap="+maxGap+", final="+gap);HandServer.release(a,"");s.overworld().setBlockAndUpdate(new BlockPos(0,80,3),Blocks.AIR.defaultBlockState());s.overworld().setBlockAndUpdate(new BlockPos(0,81,3),Blocks.AIR.defaultBlockState());next();}
   case 9->{if(tick==1){reset(s,a,b);for(int x=-2;x<=2;x++)for(int z=3;z<=18;z++)s.overworld().setBlockAndUpdate(new BlockPos(x,79,z),(z<7?Blocks.PACKED_ICE:z<11?Blocks.HONEY_BLOCK:Blocks.SOUL_SAND).defaultBlockState());s.overworld().setBlockAndUpdate(new BlockPos(0,80,13),Blocks.COBWEB.defaultBlockState());}if(tick==120)link(a,b);if(tick<400)return;expect(linked(s,a),"Mixed ice/honey/soul-sand/cobweb course retains the link");note("OBSERVED: terrain peak gap="+maxGap+", final="+gap);HandServer.release(a,"");for(int x=-2;x<=2;x++)for(int z=3;z<=18;z++)s.overworld().setBlockAndUpdate(new BlockPos(x,79,z),Blocks.SMOOTH_STONE.defaultBlockState());s.overworld().setBlockAndUpdate(new BlockPos(0,80,13),Blocks.AIR.defaultBlockState());next();}
   case 10->{if(tick==1){reset(s,a,b);for(int x=0;x<=3;x++)for(int z=3;z<=8;z++)s.overworld().setBlockAndUpdate(new BlockPos(x,79,z),Blocks.AIR.defaultBlockState());}if(tick==120)link(a,b);if(tick<400)return;expect(minY>=79.8,"Sneaking edge course avoids falling off the cutout: minY="+minY);HandServer.release(a,"");for(int x=0;x<=3;x++)for(int z=3;z<=8;z++)s.overworld().setBlockAndUpdate(new BlockPos(x,79,z),Blocks.SMOOTH_STONE.defaultBlockState());next();}
   case 11->{if(tick==1){reset(s,a,b);a.setGameMode(GameType.CREATIVE);targetPeak=80;}if(tick==120)link(a,b);if(tick>140){targetPeak=Math.max(targetPeak,b.getY());if(a.getAbilities().flying)flightTicks++;if(HandServer.isLifted(b))liftTicks++;if(b.getY()>82)airReached=true;if(airReached&&b.getY()<=80.05&&b.onGround()){landings++;airReached=false;}}if(tick>120&&(!b.getAbilities().mayfly&&!b.getAbilities().flying)==false)throw new AssertionError("Lift granted survival target flight");if(tick<800)return;expect(targetPeak>83&&flightTicks>40&&liftTicks>40,"Actual flight lifts the survival target: peak="+targetPeak+", flightTicks="+flightTicks+", liftTicks="+liftTicks);expect(landings>=3,"Repeated grounded landings after lift: "+landings);expect(!b.getAbilities().mayfly&&!b.getAbilities().flying,"Survival follower never receives flight abilities");HandServer.release(a,"");a.getAbilities().flying=false;a.onUpdateAbilities();next();}
   case 12->{if(tick==1){reset(s,a,b);a.setGameMode(GameType.SURVIVAL);HandInHand.ROLEPLAY_GRAB.set(true);HandInHand.GRAB_REQUIRES_OP.set(false);}if(tick==120)HandServer.grab(a,b);if(tick==290)expect(!linked(s,a),"Target H releases grab during opposing-input stress");if(tick==350)reset(s,a,b);if(tick==390){HandServer.grab(a,b);expect(linked(s,a),"Regrab established after cooldown and reposition");}if(tick<600)return;expect(linked(s,a)&&HandServer.state(s).pair(a.getUUID()).grab(),"Confirmed active grab immediately before disconnect test");next();}
   case 13->{if(tick<240)return;expect(logoutObserved,"A real disconnect/reconnect was observed");expect(!linked(s,a),"Rejoined target has no ghost partnership");reset(s,a,b);link(a,b);next();}
   case 14->{if(tick==30)a.connection.teleport(20,80,0,0,0);if(tick<100)return;expect(!linked(s,a)&&!HandServer.isControlled(a)&&!HandServer.isControlled(b)&&!HandServer.isLifted(b),"Large native teleport releases link and clears control/lift");HandInHand.ROLEPLAY_GRAB.set(false);note("COMPLETE: checks="+checks+", failures="+failures+", unexpectedReleases="+unexpected+", totalTicks="+total+", elapsedSeconds="+((System.nanoTime()-began)/1e9));write("SERVER-TRACE.csv",serverTrace.toString());write("DONE.txt",failures==0?"COMPLETE":"COMPLETE WITH FINDINGS");}
  }
 }
}
