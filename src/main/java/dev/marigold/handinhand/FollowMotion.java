package dev.marigold.handinhand;
import dev.marigold.handinhand.HandGeometry.V;
/** Bounded feed-forward reconciliation. No accumulated spring force or speed modifier. */
public final class FollowMotion {
 private FollowMotion(){}
 public static V limit(V v,double max){return v.length()>max?v.mul(max/v.length()):v;}
 public static V follow(V error,V pace,double maximum,double correction){
  if(!Double.isFinite(error.length()+pace.length()+maximum+correction)||maximum<=0)return new V(0,0,0);
  // A sampled pose already contains this tick's pace; correct only the remaining discrepancy.
  var move=pace.add(limit(error.sub(pace).mul(.22),correction));
  if(error.length()<.012&&pace.length()<.015)return error;
  return limit(move,maximum);
 }
 public static V cooperate(V own,V partner,V formationError,double correction){
  return cooperate(own,partner,formationError,correction,false);
 }
 public static V cooperate(V own,V partner,V formationError,double correction,boolean carryJumpPace){
  double speed=partner.length(),mine=own.length();
  V matched=carryJumpPace?partner:own;
  if(speed>.015&&mine>.01&&own.dot(partner)/(speed*mine)>.5){
   var direction=partner.mul(1/speed);
   // Match forward pace while retaining the player's lateral steering.
   matched=own.sub(direction.mul(own.dot(direction))).add(partner);
  }
  // Opposed controls remain the player's own; do not convert disagreement into a tug-of-war.
  if(mine>.01&&speed>.015&&own.dot(partner)<0)return own;
  return matched.add(limit(formationError.mul(.12),correction));
 }
}
