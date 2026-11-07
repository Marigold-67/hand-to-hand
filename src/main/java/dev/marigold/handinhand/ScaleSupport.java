package dev.marigold.handinhand;
import net.minecraft.world.entity.Entity;
import java.lang.reflect.Method;

/** Optional Pehkui bridge. No linking to client classes, and no required Pehkui dependency. */
public final class ScaleSupport {
    private static final Method HEIGHT,WIDTH,MOTION;
    static {
        Method h=null,w=null,m=null;
        try {Class<?> c=Class.forName("virtuoel.pehkui.util.ScaleUtils");
            h=c.getMethod("getModelHeightScale",Entity.class,float.class);
            w=c.getMethod("getModelWidthScale",Entity.class,float.class);
            m=c.getMethod("getMotionScale",Entity.class,float.class);
        } catch(ReflectiveOperationException ignored) {}
        HEIGHT=h;WIDTH=w;MOTION=m;
    }
    private ScaleSupport() {}
    public static double motionScale(Entity entity) {
        if(MOTION!=null)try{double n=((Number)MOTION.invoke(null,entity,1f)).doubleValue();return Double.isFinite(n)&&n>=0?n:1;}catch(ReflectiveOperationException ignored){}
        return 1;
    }
    public static double modelScale(Entity entity,float partial) {
        double scale=entity instanceof net.minecraft.world.entity.LivingEntity l?l.getScale()*0.9375:0.9375;
        if(HEIGHT!=null)try {
            double h=((Number)HEIGHT.invoke(null,entity,partial)).doubleValue(),w=((Number)WIDTH.invoke(null,entity,partial)).doubleValue();
            if(Math.abs(h-w)>0.0001)return Double.NaN;
            scale*=h;
        }catch(ReflectiveOperationException ignored){return Double.NaN;}
        return scale>0&&Double.isFinite(scale)?scale:Double.NaN;
    }
    public static HandGeometry.V shoulder(Entity entity,boolean right) {
        var p=entity.position();double yaw=Math.toRadians(((net.minecraft.world.entity.LivingEntity)entity).yBodyRot),scale=modelScale(entity,1);
        double x=(right?-5:5)/16.0*scale;
        return new HandGeometry.V(p.x+x*Math.cos(yaw),p.y+(1.501-2/16.0)*scale,p.z+x*Math.sin(yaw));
    }
    public static boolean reachable(net.minecraft.world.entity.LivingEntity a,net.minecraft.world.entity.LivingEntity b) {
        var sides=HandTether.chooseHands(a,b);boolean ar=sides[0],br=sides[1];
        if(a instanceof net.minecraft.server.level.ServerPlayer sa&&b instanceof net.minecraft.server.level.ServerPlayer sb){
            var heldA=HandServer.heldSide(sa);var heldB=HandServer.heldSide(sb);
            if(heldA!=null&&heldB!=null){ar=heldA;br=heldB;}
        }
        double ra=Math.sqrt(101)/16*modelScale(a,1),rb=Math.sqrt(101)/16*modelScale(b,1);
        return HandGeometry.contact(shoulder(a,ar),ra,shoulder(b,br),rb)!=null;
    }
}
