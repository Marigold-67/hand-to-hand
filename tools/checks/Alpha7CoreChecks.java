import dev.marigold.handinhand.*;
import java.util.*;
public class Alpha7CoreChecks {
 static int checks;
 static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 static void rejects(Runnable task,String message){try{task.run();throw new AssertionError(message);}catch(IllegalStateException expected){checks++;}}
 public static void main(String[] args){
  UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();
  var ledger=new ConsentLedger();
  rejects(()->ledger.grab(a,b,0,100,false),"Unauthorized grab accepted");
  check(ledger.pair(a)==null,"Denied grab mutated state");
  rejects(()->ledger.grab(a,a,0,100,true),"Self grab accepted");
  var request=ledger.request(a,b,0,600,100);
  rejects(()->ledger.grab(c,b,0,100,true),"Grab stole pending request");
  rejects(()->ledger.accept(c,request.token(),0),"Normal nonce ownership bypassed");
  var normal=ledger.accept(b,request.token(),0);check(!normal.grab(),"Normal acceptance became grab");
  rejects(()->ledger.grab(c,b,1,100,true),"Grab stole existing partnership");ledger.release(a,1,100);
  var grab=ledger.grab(c,b,1,100,true);check(grab.grab()&&grab.first().equals(c),"Grab initiator not fixed first");
  check(ledger.pair(c).equals(ledger.pair(b)),"Grab missing on one side");
  rejects(()->ledger.grab(a,b,2,100,true),"Duplicate link allowed");
  check(ledger.release(b,2,100).grab(),"Target could not release");
  check(ledger.pair(c)==null&&ledger.pair(b)==null,"Release leaked a link");
  rejects(()->ledger.grab(a,b,3,100,true),"Another grabber bypassed target release cooldown");
  rejects(()->ledger.grab(c,a,3,100,true),"Released grabber bypassed cooldown");
  ledger.expire(103);check(ledger.grab(a,b,103,100,true).grab(),"Cooldown never expired");
  ledger.release(a,104,100);
  var f=new Formation(Math.toRadians(179),1);f.turnToward(Math.toRadians(-179),.2);
  check(Math.abs(Math.toDegrees(Formation.difference(f.heading(),Math.toRadians(179)))-2)<1e-8,"Yaw wrap spun long way");
  f=new Formation(0,-1);
  for(int i=0;i<32;i++){double old=f.heading();f.turnToward(Math.PI,.2);check(Math.abs(f.heading()-old)<=.200000001,"Body heading snapped");check(f.sign()==-1,"Formation side changed");}
  check(Math.abs(Formation.difference(Math.PI,f.heading()))<1e-8,"Deliberate turn failed to converge");
  double old=f.heading();f.turnToward(Double.NaN,.2);check(old==f.heading(),"Invalid orientation mutated heading");
  System.out.println("PASS: "+checks+" production-core assertions (Java 17 compiler module; not a full Java 21 mod build)");
 }
}
