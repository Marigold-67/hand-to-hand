package dev.marigold.handinhand;
import dev.marigold.handinhand.HandGeometry.V;

/** Kinematic following: copy displacement and close positional error, without inertia. */
public final class TetherMath {
    private TetherMath() {}
    public record Follow(V movement,double speed){}
    public static Follow follow(V position,V goal,V own,V pace,double maximum,boolean vertical,double correctionCap){
        V error=goal.sub(position);
        if(!vertical){error=new V(error.x(),0,error.z());pace=new V(pace.x(),0,pace.z());}
        double distance=error.length();
        if(!Double.isFinite(distance+maximum+pace.length()+correctionCap)||maximum<=0)
            return new Follow(own,0);
        // A remote position may already include this tick's pace. Do not count that advance twice.
        double paceSquared=pace.dot(pace);
        if(paceSquared>1e-12){double advance=Math.max(0,Math.min(1,error.dot(pace)/paceSquared));error=error.sub(pace.mul(advance));distance=error.length();}
        // No acceleration ramp, remembered velocity, or distance resistance.
        V correction=distance<.008?new V(0,0,0):error.mul(Math.min(.85,Math.max(0,correctionCap)/distance));
        V move=pace.add(correction);double speed=move.length();
        if(speed>maximum){move=move.mul(maximum/speed);speed=maximum;}
        return new Follow(vertical?move:new V(move.x(),own.y(),move.z()),speed);
    }
    /** Own controls always pass through verbatim, including strafing and opposite input. */
    public static V movement(boolean active,V own,Follow passive){return active?own:passive.movement();}
}
