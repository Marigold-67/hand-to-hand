package dev.marigold.handinhand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.*;
import net.minecraft.core.BlockPos;
import java.util.*;
/** Small, cached collision-surface search. All resulting movement still uses Entity.move. */
public final class TerrainFollower {
 public record Step(Vec3 target,boolean hop){}
 private record Node(Vec3 p,Node parent,double cost,double score){}
 private List<Vec3> path=List.of();private Vec3 goal=Vec3.ZERO;private int cooldown;
 private static boolean clear(Player p,Vec3 from,Vec3 to){
  var box=p.getBoundingBox().move(from.subtract(p.position()));
  return p.level().noCollision(p,box.expandTowards(to.subtract(from)).deflate(.001));
 }
 private static Double surface(Player p,double x,double z,double y,boolean jumping){
  double half=p.getBbWidth()/2;var sample=new AABB(x-half+.002,y-1.06,z-half+.002,x+half-.002,y+1.06,z+half-.002);
  var heights=new TreeSet<Double>(Comparator.reverseOrder());
  for(var shape:p.level().getBlockCollisions(p,sample))for(var box:shape.toAabbs())if(box.maxX>sample.minX&&box.minX<sample.maxX&&box.maxZ>sample.minZ&&box.minZ<sample.maxZ)heights.add(box.maxY);
  for(double top:heights){
   if(top>y+(jumping?1.05:p.maxUpStep())+.002||top<y-1.01)continue;
   var body=p.getBoundingBox().move(x-p.getX(),top-p.getY(),z-p.getZ());
   if(!p.level().noCollision(p,body.deflate(.001)))continue;
   if(!p.level().getFluidState(BlockPos.containing(x,top+.05,z)).isEmpty())continue;
   return top;
  }
  return null;
 }
 private static Vec3 neighbor(Player p,Vec3 from,double dx,double dz,boolean hopping){
  Double y=surface(p,from.x+dx,from.z+dz,from.y,hopping);if(y==null)return null;
  var target=new Vec3(from.x+dx,y,from.z+dz);double rise=target.y-from.y;
  if(rise>p.maxUpStep()+.01){
   if(!hopping||!clear(p,from,from.add(0,rise+.01,0))||!clear(p,from.add(0,rise+.01,0),target.add(0,.01,0)))return null;
  }else if(rise>0){if(!clear(p,from.add(0,rise+.01,0),target.add(0,.01,0)))return null;}
  else if(!clear(p,from.add(0,.01,0),new Vec3(target.x,from.y+.01,target.z)))return null;
  return target;
 }
 private static String key(Vec3 p){return Math.round(p.x*2)+":"+Math.round(p.y*16)+":"+Math.round(p.z*2);}
 public Step step(Player p,Vec3 desired,double maximum){
  var start=p.position();boolean hopping=!p.isCrouching();var delta=desired.subtract(start).multiply(1,0,1);
  if(delta.horizontalDistance()<.025)return new Step(start,false);
  var direction=delta.normalize().scale(Math.min(delta.length(),Math.min(.5,Math.max(.25,maximum))));
  var direct=neighbor(p,start,direction.x,direction.z,hopping);
  if(direct!=null){path=List.of();cooldown=0;return new Step(direct,direct.y>start.y+p.maxUpStep()+.01);}
  if(cooldown--<=0||goal.distanceToSqr(desired)>.75*.75){
   goal=desired;cooldown=5;var queue=new PriorityQueue<Node>(Comparator.comparingDouble(Node::score));var seen=new HashSet<String>();
   var first=new Node(start,null,0,delta.length());queue.add(first);Node best=first;double bestDistance=delta.length();int visits=0;
   while(!queue.isEmpty()&&visits++<180){var current=queue.poll();if(!seen.add(key(current.p)))continue;
    double distance=current.p.subtract(desired).multiply(1,0,1).length();if(distance<bestDistance){best=current;bestDistance=distance;}if(distance<.32)break;
    for(double[] d:new double[][]{{.5,0},{-.5,0},{0,.5},{0,-.5}}){var next=neighbor(p,current.p,d[0],d[1],hopping);if(next==null||next.subtract(start).horizontalDistance()>3.5||Math.abs(next.y-start.y)>2.1||seen.contains(key(next)))continue;
     double cost=current.cost+.5+Math.max(0,next.y-current.p.y)*.4;double heuristic=next.subtract(desired).multiply(1,0,1).length();queue.add(new Node(next,current,cost,cost+heuristic*1.4));}
   }
   var route=new ArrayList<Vec3>();if(best!=first&&bestDistance<delta.length()-.05){for(var at=best;at.parent!=null;at=at.parent)route.add(at.p);Collections.reverse(route);}path=route;
  }
  while(!path.isEmpty()&&path.get(0).subtract(start).horizontalDistance()<.12&&Math.abs(path.get(0).y-start.y)<.2)path=new ArrayList<>(path.subList(1,path.size()));
  if(path.isEmpty())return new Step(start,false);
  var waypoint=path.get(0);if(!clear(p,start.add(0,Math.max(0,waypoint.y-start.y)+.01,0),waypoint.add(0,.01,0))){cooldown=0;return new Step(start,false);}
  return new Step(waypoint,waypoint.y>start.y+p.maxUpStep()+.01);
 }
}
