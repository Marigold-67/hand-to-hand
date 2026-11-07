package dev.marigold.handinhand.client;
import dev.marigold.handinhand.*;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
/** Full-block hills, real stairs, obstructed lanes, ledges and high-latency compound motion. */
final class TerrainNetwork {
 static final boolean ENABLED=Boolean.getBoolean("handinhand.terrainCheck");
 static int phase,tick,lastPhase=-1,lastJump=-1;static double highA,highB,gap,minY=80;static boolean gripA,gripB,probe;
 static final StringBuilder report=new StringBuilder(),rtt=new StringBuilder("phase,step,rtt_ms\n"),trace=new StringBuilder("phase,step,ax,ay,az,bx,by,bz,horizontalGap\n");
 static void note(String text){report.append(text).append('\n');PairNetworkTest.write("TERRAIN-RESULTS.txt",report.toString());}
 static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);note("PASS: "+text);}
 static void next(){note("MEASURE: phase="+phase+", maxHorizontalGap="+gap+", peaks="+highA+"/"+highB+", minY="+minY);phase++;tick=0;highA=highB=80;minY=80;gap=0;}
 static void clear(MinecraftServer s){for(int x=-20;x<=20;x++)for(int z=0;z<=31;z++)for(int y=80;y<=89;y++)s.overworld().setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());for(int x=-20;x<=20;x++)for(int z=0;z<=31;z++)s.overworld().setBlockAndUpdate(new BlockPos(x,79,z),Blocks.SMOOTH_STONE.defaultBlockState());}
 static void reset(MinecraftServer s,ServerPlayer a,ServerPlayer b){if(HandServer.state(s).pair(a.getUUID())!=null)HandServer.release(a,"");PairNetworkTest.resetPosition(a,-.45,80,0);PairNetworkTest.resetPosition(b,.45,80,0);a.yBodyRot=b.yBodyRot=0;}
 static void link(ServerPlayer a,ServerPlayer b,boolean reverse){var request=HandServer.ask(reverse?b:a,reverse?a:b);HandServer.accept(reverse?a:b,request.token());gripA=HandServer.heldSide(a);gripB=HandServer.heldSide(b);}
 static void hill(MinecraftServer s){clear(s);for(int x=-20;x<=20;x++)for(int z=3;z<=24;z++){int height=z<15?Math.min(4,(z-3)/3+1):Math.max(0,4-(z-15)/3);for(int y=80;y<80+height;y++)s.overworld().setBlockAndUpdate(new BlockPos(x,y,z),Blocks.GRASS_BLOCK.defaultBlockState());}}
 static void stairs(MinecraftServer s){clear(s);for(int x=-4;x<=4;x++)for(int z=3;z<=24;z++){int y=z<=10?80+z-3:z<17?87:87-(z-17);for(int by=80;by<y;by++)s.overworld().setBlockAndUpdate(new BlockPos(x,by,z),Blocks.STONE.defaultBlockState());s.overworld().setBlockAndUpdate(new BlockPos(x,y,z),z>10&&z<17?Blocks.STONE.defaultBlockState():Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,z<=10?Direction.SOUTH:Direction.NORTH));}}
 static void client(){if(!ENABLED)return;var mc=Minecraft.getInstance();if(PairNetworkTest.ROLE.equals("bravo"))mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);if(!probe){var before=ClockPayload.receiver;ClockPayload.receiver=p->{rtt.append(PairNetworkTest.stage).append(',').append(PairNetworkTest.step).append(',').append((System.nanoTime()-p.stamp())/1e6).append('\n');before.accept(p);};probe=true;}if(PairNetworkTest.localTick%100==0)PairNetworkTest.write(PairNetworkTest.ROLE+"-RTT.csv",rtt.toString());}
 static void input(MovementInputUpdateEvent e){
  var mc=Minecraft.getInstance();boolean alpha=PairNetworkTest.ROLE.equals("alpha");int p=PairNetworkTest.stage,n=PairNetworkTest.step;
  if(lastPhase!=p){lastPhase=p;lastJump=-1;}var in=e.getInput();in.forwardImpulse=in.leftImpulse=0;in.jumping=in.shiftKeyDown=false;mc.player.setSprinting(false);
  boolean leader=(p==4)?!alpha:alpha;float yaw=p==10?(float)(n*1.5):0;mc.player.setYRot(leader||p==12?yaw:(float)(Math.sin(n*.04)*65));mc.player.setXRot(0);
  if(p==9||p==10){if(n<140)return;n-=140;}
  if(p==2||p==4||p==12||p==14){if(leader||p==12){in.forwardImpulse=1;var d=new net.minecraft.world.phys.Vec3(0,0,.35);if(mc.player.onGround()&&!mc.level.noCollision(mc.player,mc.player.getBoundingBox().move(d)))in.jumping=true;}}
  if(p==6&&leader)in.forwardImpulse=1;
  if(p==8&&alpha){in.forwardImpulse=n<65||n>125?1:0;in.leftImpulse=n>=65&&n<=125?-1:0;}
  if(p==9){in.shiftKeyDown=true;if(alpha){in.forwardImpulse=n<80||n>160?1:0;in.leftImpulse=n>=80&&n<=160?-1:0;}}
  if(p==10){in.forwardImpulse=1;mc.player.setYRot(yaw);if(!alpha){in.leftImpulse=n%160<80?.4f:-.4f;int cycle=n/35;if(cycle!=lastJump&&n%35>=6){in.jumping=true;lastJump=cycle;}}}
 }
 static void server(MinecraftServer s)throws Exception{
  var a=PairNetworkTest.named(s,"Pair_Alpha");var b=PairNetworkTest.named(s,"Pair_Bravo");if(a==null||b==null)return;tick++;PairNetworkTest.write("state.txt",phase+" "+tick+" 0");
  PairNetworkTest.write("NETWORK.json",phase==10?"{\"name\":\"500ms terrain compound\",\"one_way_ms\":250,\"jitter_ms\":[0,20,-20]}":"{\"name\":\"terrain baseline\",\"one_way_ms\":20,\"jitter_ms\":[0]}");
  highA=Math.max(highA,a.getY());highB=Math.max(highB,b.getY());minY=Math.min(minY,Math.min(a.getY(),b.getY()));double h=a.position().subtract(b.position()).horizontalDistance();gap=Math.max(gap,h);
  trace.append(phase).append(',').append(tick).append(',').append(a.getX()).append(',').append(a.getY()).append(',').append(a.getZ()).append(',').append(b.getX()).append(',').append(b.getY()).append(',').append(b.getZ()).append(',').append(h).append('\n');if(tick%40==0)PairNetworkTest.write("TERRAIN-TRACE.csv",trace.toString());
  if(phase==2||phase==4||phase==6||phase==8||phase==9||phase==10||phase==12||phase==14){checkLive(s,a,b);}
  switch(phase){
   case 0->{if(tick<40)return;a.setGameMode(GameType.SURVIVAL);b.setGameMode(GameType.SURVIVAL);a.getAbilities().invulnerable=b.getAbilities().invulnerable=true;a.onUpdateAbilities();b.onUpdateAbilities();s.overworld().getGameRules().getRule(GameRules.RULE_FALL_DAMAGE).set(false,s);reset(s,a,b);hill(s);if(Boolean.getBoolean("handinhand.tetherScaled")){ClientSmoke.scale(a,1f);ClientSmoke.scale(b,1.15f);}next();if(Boolean.getBoolean("handinhand.terrainDirected"))phase=11;}
   case 1->{if(tick<120)return;link(a,b,false);next();}
   case 2->{if(tick<330)return;check(highA>83.9&&highB>83.9,"A leads and both climb four full-block hill rises: "+highA+" / "+highB);check(a.getZ()>28&&b.getZ()>27,"Both descend and leave hill");HandServer.release(a,"");reset(s,a,b);next();}
   case 3->{if(tick<120)return;link(a,b,true);next();}
   case 4->{if(tick<330)return;check(highA>83.9&&highB>83.9,"B leads and both climb the same full-block hill");check(a.getZ()>27&&b.getZ()>28,"Reverse leader descends and leaves hill");HandServer.release(a,"");reset(s,a,b);stairs(s);next();}
   case 5->{if(tick<120)return;link(a,b,false);next();}
   case 6->{if(tick<400)return;check(highA>87.9&&highB>87.9,"Both walk up actual stair blocks to an eight-block platform");check(a.getZ()>26&&b.getZ()>25,"Both walk down actual stair blocks");HandServer.release(a,"");reset(s,a,b);clear(s);s.overworld().setBlockAndUpdate(new BlockPos(0,80,3),Blocks.STONE.defaultBlockState());s.overworld().setBlockAndUpdate(new BlockPos(0,81,3),Blocks.STONE.defaultBlockState());next();}
   case 7->{if(tick<120)return;link(a,b,false);next();}
   case 8->{if(tick<280)return;check(gap<=2.05*HandTether.scale(a,b),"Blocked lane never produces the former 11-block gap: "+gap);check(s.overworld().noCollision(b,b.getBoundingBox().deflate(.001)),"Routing does not phase follower through a wall");check(a.getZ()>5&&b.getZ()>4,"Pair resumes past wall after going around");HandServer.release(a,"");reset(s,a,b);clear(s);for(int x=0;x<=3;x++)for(int z=3;z<=8;z++)s.overworld().setBlockAndUpdate(new BlockPos(x,79,z),Blocks.AIR.defaultBlockState());next();}
   case 9->{if(tick==1){/* cooldown */}if(tick==120){link(a,b,false);tick=121;highA=highB=80;minY=80;gap=0;}if(tick<=121)return;if(tick<400)return;check(minY>=79.8,"Sneaking pair stays on platform beside ledge");check(gap<=2.05*HandTether.scale(a,b),"Ledge cannot leave a partner fourteen blocks behind: "+gap);HandServer.release(a,"");reset(s,a,b);clear(s);next();}
   case 10->{if(tick==120){link(a,b,false);highA=highB=80;minY=80;gap=0;}if(tick<122)return;if(tick<1400)return;check(gap<=2.05*HandTether.scale(a,b),"Compound diagonal/jump/turn movement respects range at high ping: "+gap);check(highB>81.1,"Follower retains full native jumps during compound steering");HandServer.release(a,"");check(!HandServer.isControlled(a)&&!HandServer.isControlled(b),"Release clears control after terrain and high-ping tests");reset(s,a,b);hill(s);next();}
   case 11->{if(tick<120)return;link(a,b,false);next();}
   case 12->{if(tick<330)return;check(highA>83.9&&highB>83.9,"Both actively walking/jumping players climb the full-block hill");check(a.getZ()>28&&b.getZ()>27,"Cooperative pair descends and leaves hill");reset(s,a,b);hill(s);int clearance=Boolean.getBoolean("handinhand.tetherScaled")?4:3;for(int x=-4;x<=4;x++)for(int z=0;z<=31;z++){int height=z<3?0:z<=24?(z<15?Math.min(4,(z-3)/3+1):Math.max(0,4-(z-15)/3)):0;s.overworld().setBlockAndUpdate(new BlockPos(x,80+height+clearance,z),Blocks.STONE.defaultBlockState());}next();}
   case 13->{if(tick<120)return;link(a,b,false);next();}
   case 14->{if(tick<330)return;check(highA>83.9&&highB>83.9,"Uphill hops work with size-appropriate low ceiling clearance");check(a.getZ()>28&&b.getZ()>27,"Both finish the covered hill");HandServer.release(a,"");note("COMPLETE");PairNetworkTest.write("TERRAIN-TRACE.csv",trace.toString());PairNetworkTest.write("DONE.txt","COMPLETE");}
  }
 }
 static void checkLive(MinecraftServer s,ServerPlayer a,ServerPlayer b){if((phase==9||phase==10)&&tick<=121)return;if(HandServer.state(s).pair(a.getUUID())==null)throw new AssertionError("Link released in terrain phase "+phase);if(HandServer.heldSide(a)!=gripA||HandServer.heldSide(b)!=gripB)throw new AssertionError("Held hand changed");}
}
