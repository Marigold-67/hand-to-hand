package dev.marigold.handinhand;
import dev.marigold.handinhand.HandGeometry.V;
/** Position, resolved velocity and support state evaluated on one snapshot interval. */
public final class SampleMotion {
 private SampleMotion(){}
 public record State(V position,V velocity,boolean grounded,boolean flying){}
 public static State interpolate(State a,State b,double fraction,double ticks){
  double t=Math.max(0,Math.min(1,fraction)),span=Math.max(1,ticks);
  V delta=b.position.sub(a.position);
  return new State(a.position.add(delta.mul(t)),delta.mul(1/span),t>=1?b.grounded:a.grounded&&b.grounded,t>=1?b.flying:a.flying);
 }
 public static State predict(State a,V horizontalPace,double ticks){
  double ahead=Math.max(0,Math.min(1.5,ticks));
  double dy=0,velocity=a.velocity.y();
  if(a.flying)dy=velocity*ahead;
  else if(!a.grounded){double first=(velocity-.08)*.98,second=(first-.08)*.98;dy=first*Math.min(1,ahead)+second*Math.max(0,ahead-1);velocity=ahead<=1?first:second;}
  else velocity=0;
  return new State(a.position.add(new V(horizontalPace.x()*ahead,dy,horizontalPace.z()*ahead)),new V(horizontalPace.x(),velocity,horizontalPace.z()),a.grounded,a.flying);
 }
 public static double presentationWeight(double horizontalGap,double spacing,double scale){
  double t=Math.max(0,Math.min(1,(horizontalGap-spacing-.2*scale)/Math.max(.01,1.8*scale-spacing-.2*scale)));
  return 1-t*t*(3-2*t);
 }
}
