package dev.marigold.handinhand;
import dev.marigold.handinhand.HandGeometry.V;
/** Horizontal positional constraint; keeps vertical jump/flight physics and tangential steering. */
public final class PairReach {
 private PairReach(){}
 public static V move(V self,V other,V movement,double soft,double hard,boolean ease){
  if(!Double.isFinite(self.length()+other.length()+movement.length()+soft+hard)||soft<=0||hard<soft)return movement;
  V radius=new V(self.x()-other.x(),0,self.z()-other.z());double distance=radius.length();
  V horizontal=new V(movement.x(),0,movement.z());
  if(distance>1e-6){
   V outward=radius.mul(1/distance);double speed=horizontal.dot(outward);
   if(speed>0){double factor=ease?Math.max(0,Math.min(1,(hard-distance)/(hard-soft))):distance>=hard?0:1;horizontal=horizontal.mul(factor);}
   if(distance>hard+.02)horizontal=horizontal.add(outward.mul(-Math.min(.1,(distance-hard)*.2)));
  }
  V proposed=radius.add(horizontal);
  if(distance<=hard+.02&&proposed.length()>hard){double a=horizontal.dot(horizontal),b=radius.dot(horizontal);if(a>1e-12){double t=(-b+Math.sqrt(Math.max(0,b*b+a*(hard*hard-distance*distance))))/a;horizontal=horizontal.mul(Math.max(0,Math.min(1,t)));}}
  return new V(horizontal.x(),movement.y(),horizontal.z());
 }
}
