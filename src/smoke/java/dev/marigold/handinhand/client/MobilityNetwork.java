package dev.marigold.handinhand.client;
import dev.marigold.handinhand.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
/** Focused capability check; input comes from each independent participant, not server impulses. */
final class MobilityNetwork {
 static final boolean ENABLED=Boolean.getBoolean("handinhand.mobilityCheck");
 static int phase,tick,clientPhase=-1;static boolean tapped,released,gripA,gripB;static int failures;
 static double peak,reference,startZ,startX,steerStart,steerEnd,steerPeak,airStartA,airStartB,airWalkA,airWalkB;
 static final StringBuilder report=new StringBuilder();
 static void line(String s){report.append(s).append('\n');PairNetworkTest.write("MOBILITY-RESULTS.txt",report.toString());}
 static void check(boolean ok,String s){line((ok?"PASS: ":"FAIL: ")+s);if(!ok)failures++;}
 static void next(){phase++;tick=0;peak=80;}
 static boolean window(int step,int first,int last){return step>=first&&step<=last;}
 static void input(MovementInputUpdateEvent e){
  var mc=Minecraft.getInstance();boolean alpha=PairNetworkTest.ROLE.equals("alpha");int p=PairNetworkTest.stage,n=PairNetworkTest.step;
  if(!alpha)mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
  if(p!=clientPhase){clientPhase=p;tapped=released=false;}
  boolean lead=alpha&&(window(p,2,5)||window(p,8,10));
  e.getInput().forwardImpulse=lead?1:0;
  e.getInput().leftImpulse=!alpha&&(p==5||p==10)&&window(n,15,18)?1:0;
  e.getInput().shiftKeyDown=false;e.getInput().jumping=false;mc.player.setSprinting(false);
  if(!alpha&&(p==1||p==3||p==9||p==12)&&n>=15&&!tapped){e.getInput().jumping=true;tapped=true;}
  if(!alpha&&p==4&&window(n,15,20))e.getInput().jumping=true;
  mc.player.setYRot(!alpha&&(p==2||p==8)?75:0);mc.player.setXRot(!alpha&&(p==2||p==8)?-25:0);
  if(!alpha&&p==11&&n>=10&&!released){KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_H));released=true;}
 }
 static void reset(ServerPlayer a,ServerPlayer b){PairNetworkTest.resetPosition(a,-.45,80,0);PairNetworkTest.resetPosition(b,.45,80,0);a.yBodyRot=b.yBodyRot=0;}
 static boolean linked(MinecraftServer s,ServerPlayer a){return HandServer.state(s).pair(a.getUUID())!=null;}
 static void server(MinecraftServer s,ServerPlayer a,ServerPlayer b)throws Exception{
  tick++;PairNetworkTest.write("state.txt",phase+" "+tick+" 0");peak=Math.max(peak,b.getY());
  switch(phase){
   case 0->{if(tick<40)return;a.setGameMode(GameType.SURVIVAL);b.setGameMode(GameType.SURVIVAL);a.getAbilities().invulnerable=b.getAbilities().invulnerable=true;a.onUpdateAbilities();b.onUpdateAbilities();reset(a,b);if(Boolean.getBoolean("handinhand.tetherScaled")){ClientSmoke.scale(a,.75f);ClientSmoke.scale(b,1.15f);}next();}
   case 1->{if(tick<65)return;reference=peak-80;check(reference>1.1&&b.onGround(),"Unlinked one-tick jump reference: height="+reference+", landed="+b.onGround());var r=HandServer.ask(a,b);HandServer.accept(b,r.token());gripA=HandServer.heldSide(a);gripB=HandServer.heldSide(b);startZ=b.getZ();next();}
   case 2->{if(tick<65)return;check(linked(s,a),"Normal holding remains linked during passive free-look");check(Math.abs(net.minecraft.util.Mth.wrapDegrees(b.getYRot()-75))<5&&Math.abs(b.getXRot()+25)<5,"Passive camera turns yaw/pitch while the other player walks");check(b.getZ()>startZ+3,"Passive participant is actually being led during free-look");next();}
   case 3->{if(tick==14){airStartA=a.getZ();airStartB=b.getZ();}if(tick==31){airWalkA=a.getZ()-airStartA;airWalkB=b.getZ()-airStartB;}if(tick<65)return;double height=peak-80;check(linked(s,a),"Normal holding survives a one-tick jump tap");check(height>=reference*.9,"Normal holding preserves a full tapped jump: height="+height+", reference="+reference);check(airWalkB>=airWalkA*.75,"Jumping without W keeps moving with the leader: "+airWalkB+" vs "+airWalkA);line("MEASURE: tapped jump delta="+height);next();}
   case 4->{if(tick<65)return;double height=peak-80;check(height>=reference*.9,"Normal holding preserves a held jump: height="+height+", reference="+reference);check(linked(s,a),"Jumping did not cancel the normal link");next();}
   case 5->{if(tick==14){steerStart=b.getX();steerPeak=0;}if(tick>=14&&tick<=28)steerPeak=Math.max(steerPeak,Math.abs(b.getX()-steerStart));if(tick==24)steerEnd=b.getX();if(tick<65)return;check(steerPeak>.15,"Participant can steer sideways while being led: peak excursion="+steerPeak+", later return delta="+(steerEnd-steerStart));check(linked(s,a)&&HandServer.heldSide(a)==gripA&&HandServer.heldSide(b)==gripB,"Steering keeps the normal link and selected hands");HandServer.release(a,"");next();}
   case 6->{if(tick<120)return;reset(a,b);next();}
   case 7->{if(tick<40)return;HandInHand.ROLEPLAY_GRAB.set(true);HandInHand.GRAB_REQUIRES_OP.set(false);HandServer.grab(a,b);startZ=b.getZ();next();}
   case 8->{if(tick<65)return;check(linked(s,a)&&b.getZ()>startZ+3,"Grab participant is actually being dragged during free-look");check(Math.abs(net.minecraft.util.Mth.wrapDegrees(b.getYRot()-75))<5&&Math.abs(b.getXRot()+25)<5,"Grab target retains yaw/pitch camera control");next();}
   case 9->{if(tick<65)return;line("OBSERVED: roleplay-grab tapped jump height="+(peak-80)+"; horizontal/vertical movement is intentionally owned by the grab initiator in roleplay-grab mode");next();}
   case 10->{if(tick==14)steerStart=b.getX();if(tick==24)steerEnd=b.getX();if(tick<65)return;line("OBSERVED: roleplay-grab strafe displacement="+(steerEnd-steerStart));check(linked(s,a)&&HandServer.state(s).pair(a.getUUID()).first().equals(a.getUUID()),"Grab does not switch leader when target tries to move");next();}
   case 11->{if(tick<45)return;check(!linked(s,a)&&!HandServer.isControlled(b),"Target H release clears movement control");next();}
   case 12->{if(tick<65)return;check(peak-80>=reference*.9,"After releasing grab, a tapped jump is fully native again: height="+(peak-80));line("CHECK COMPLETE: "+failures+" failed expectation(s)");PairNetworkTest.write("DONE.txt",failures==0?"COMPLETE":"CHECK COMPLETE: findings present");}
  }
 }
}
