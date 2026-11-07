package dev.marigold.handinhand;
/** Pair-relative side and continuous body heading; independent of Minecraft/render APIs. */
public final class Formation {
    private final int sign;
    private double heading;
    public Formation(double heading,int sign){
        if(!Double.isFinite(heading)||(sign!=1&&sign!=-1))throw new IllegalArgumentException("Invalid formation");
        this.heading=heading;this.sign=sign;
    }
    public int sign(){return sign;}
    public double heading(){return heading;}
    public static double difference(double target,double current){return Math.atan2(Math.sin(target-current),Math.cos(target-current));}
    public void turnToward(double target,double maximum){
        if(!Double.isFinite(target+maximum)||maximum<0)return;
        double delta=difference(target,heading);heading+=Math.max(-maximum,Math.min(maximum,delta));
    }
}
