package dev.marigold.handinhand.client;
import dev.marigold.handinhand.*;
import dev.marigold.handinhand.HandGeometry.V;
import java.util.*;
/** One target per pair and render sample, shared by both PAL poses and the optional EMF pass. */
final class HandContacts {
 private static final Map<UUID,Track> tracks=new HashMap<>();
 private static final class Track {long session;Object level;double frame=Double.NaN;V raw,result;final ContactFilter filter=new ContactFilter();}
 static V contact(HandClient.Link link,Object level,double frame,V a,double ra,V b,double rb){
  var t=tracks.computeIfAbsent(link.first(),id->new Track());
  if(t.session!=link.started()||t.level!=level){t.session=link.started();t.level=level;t.frame=Double.NaN;t.filter.clear();}
  if(t.frame==frame)return t.result;
  var raw=HandGeometry.contact(a,ra,b,rb);double elapsed=Double.isNaN(t.frame)?0:(frame-t.frame)/20;
  boolean reset=t.raw!=null&&raw!=null&&t.raw.sub(raw).length()>2*Math.max(ra,rb);
  // Filter in the moving pair's frame, so ordinary walking cannot drag the hands behind the bodies.
  var anchor=a.add(b).mul(.5);var preferred=t.filter.update(raw==null?null:raw.sub(anchor),elapsed,reset);
  t.raw=raw;t.frame=frame;t.result=HandGeometry.projectContact(a,ra,b,rb,preferred==null?null:preferred.add(anchor));
  // Stay near the natural lower contact solution; a near-tangent circle must not flip the bend upward.
  if(raw!=null&&t.result!=null){var delta=t.result.sub(raw);double cap=.04*Math.max(ra,rb)/.625;if(delta.length()>cap)t.result=HandGeometry.projectContact(a,ra,b,rb,raw.add(delta.mul(cap/delta.length())));}
  return t.result;
 }
 static void remove(UUID first){tracks.remove(first);}
 static void clear(){tracks.clear();}
}
