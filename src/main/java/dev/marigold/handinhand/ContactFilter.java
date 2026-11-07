package dev.marigold.handinhand;
import dev.marigold.handinhand.HandGeometry.V;
/** Time-based shared target smoothing; callers project the result back onto reachable geometry. */
public final class ContactFilter {
 private V previous;
 public V update(V target,double seconds,boolean reset){
  if(target==null){previous=null;return null;}
  if(previous==null||reset||seconds<0||seconds>.25)previous=target;
  else previous=previous.add(target.sub(previous).mul(1-Math.exp(-Math.max(0,seconds)/.075)));
  return previous;
 }
 public void clear(){previous=null;}
}
