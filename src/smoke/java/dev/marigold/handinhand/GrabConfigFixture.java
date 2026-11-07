package dev.marigold.handinhand;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.*;
/** Dedicated-server config persistence and real-player permissions; excluded from production. */
@EventBusSubscriber(modid=HandInHand.ID)
public final class GrabConfigFixture {
 public static final boolean ENABLED=Boolean.getBoolean("handinhand.grabConfigCheck");
 static final Path DIR=Path.of(System.getProperty("handinhand.pairDir","."));
 static int phase,tick,total;static boolean ended;static final boolean RESTART=Boolean.getBoolean("handinhand.grabRestart");static final StringBuilder report=new StringBuilder();
 static void write(String name,String text){try{Files.createDirectories(DIR);Files.writeString(DIR.resolve(name),text);}catch(Exception e){throw new RuntimeException(e);}}
 static void pass(boolean ok,String text){if(!ok)throw new AssertionError(text);report.append("PASS: ").append(text).append('\n');write("GRAB-CONFIG-RESULTS.txt",report.toString());}
 static void command(ServerPlayer p,String text)throws Exception{p.server.getCommands().getDispatcher().execute(text,p.createCommandSourceStack());}
 static ServerPlayer named(MinecraftServer s,String n){return s.getPlayerList().getPlayers().stream().filter(p->p.getGameProfile().getName().equals(n)).findFirst().orElse(null);}
 static void next(){phase++;tick=0;}
 @SubscribeEvent public static void started(ServerStartedEvent e){if(!ENABLED)return;var s=e.getServer();if(!s.isDedicatedServer())return;write("PORT.txt",Integer.toString(s.getPort()));write("state.txt","0 0 0");for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)s.overworld().setBlockAndUpdate(new BlockPos(x,79,z),Blocks.SMOOTH_STONE.defaultBlockState());}
 @SubscribeEvent public static void tick(ServerTickEvent.Post e){if(!ENABLED||ended)return;var s=e.getServer();try{total++;if(total>1600)throw new AssertionError("Dedicated config check timed out");var a=named(s,"Pair_Alpha");var b=named(s,"Pair_Bravo");if(a==null||b==null)return;tick++;write("state.txt",phase+" "+tick+" 0");switch(phase){
  case 0->{if(tick<30)return;s.getPlayerList().op(a.getGameProfile());a.connection.teleport(-.45,80,0,0,0);b.connection.teleport(.45,80,0,0,0);a.yBodyRot=b.yBodyRot=0;next();}
  case 1->{if(tick<60)return;pass(a.createCommandSourceStack().hasPermission(2),"Actual server OP may configure grabbing");pass(!s.getPlayerList().isOp(b.getGameProfile()),"Other actual TCP participant remains non-OP");if(RESTART){pass(HandInHand.ROLEPLAY_GRAB.get()&&!HandInHand.GRAB_REQUIRES_OP.get()&&HandInHand.MOVEMENT_TETHER.get(),"Enabled/all-player/following policy survived an actual server-process restart");command(b,"handholding grab Pair_Alpha");pass(HandServer.state(s).pair(b.getUUID())!=null,"Non-OP can actually grab after restart");command(a,"handholding config grab false");phase=5;tick=0;}else{pass(!HandInHand.ROLEPLAY_GRAB.get(),"Fresh server defaults to disabled grabbing");command(a,"handholding config movementTether false");pass(!HandInHand.MOVEMENT_TETHER.get(),"Reproduce hidden prerequisite: movement following explicitly off");command(a,"handholding config grab true");pass(HandInHand.ROLEPLAY_GRAB.get()&&HandInHand.MOVEMENT_TETHER.get()&&HandServer.mayGrab(a),"Enable command activates its prerequisite and immediately makes actual OP eligible");pass(HandServer.grabBlock(b)==GrabPolicy.Block.OP_REQUIRED,"Enabled OP-only setting gives a distinct non-OP permission reason");command(a,"handholding grab Pair_Bravo");pass(HandServer.state(s).pair(a.getUUID())!=null,"Actual OP grab succeeds immediately after enable command");HandServer.release(a,"");next();}}
  case 2->{if(tick<120)return;pass(HandServer.mayGrab(a),"Enabled state remains effective after file-watch/reload interval");command(a,"handholding config grabOpOnly false");command(b,"handholding grab Pair_Alpha");pass(HandServer.state(s).pair(b.getUUID())!=null,"Non-OP grab succeeds after explicit all-player policy command");HandServer.release(b,"");next();}
  case 3->{if(tick<40)return;var disk=Files.readString(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("handinhand-server.toml"));var section=disk.substring(disk.indexOf("[roleplayGrab]"));pass(section.contains("enabled = true")&&section.contains("requireOp = false"),"Both policy values are persisted in loaded server TOML");report.append("COMPLETE\n");write("GRAB-CONFIG-RESULTS.txt",report.toString());write("DONE.txt","COMPLETE");ended=true;s.halt(false);}
  case 5->{if(tick<10)return;pass(HandServer.state(s).pair(a.getUUID())==null&&!HandServer.isControlled(a)&&!HandServer.isControlled(b),"Disable command revokes the live grab and movement control");pass(HandServer.grabBlock(b)==GrabPolicy.Block.GRAB_DISABLED,"Disabled feature now has its own accurate reason");report.append("COMPLETE\n");write("GRAB-CONFIG-RESULTS.txt",report.toString());write("DONE.txt","COMPLETE");ended=true;s.halt(false);}
 }}catch(Throwable ex){ended=true;write("FAIL.txt",ex.toString());ex.printStackTrace();s.halt(false);}}
}
