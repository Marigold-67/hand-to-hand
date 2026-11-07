package dev.marigold.handinhand;

/** Prediction multipliers only: never changes the player's saved flying speed or flight permission. */
public record FlightRates(float horizontalA,float horizontalB,float verticalA,float verticalB) {
    public static final FlightRates NORMAL=new FlightRates(1,1,1,1);
    public static FlightRates match(double speedA,double speedB,double motionA,double motionB,boolean sprintA,boolean sprintB) {
        double a=valid(speedA)*valid(motionA),b=valid(speedB)*valid(motionB);
        double ah=a*(sprintA?2:1),bh=b*(sprintB?2:1);
        return new FlightRates(ratio(Math.min(ah,bh),ah),ratio(Math.min(ah,bh),bh),ratio(Math.min(a,b),a),ratio(Math.min(a,b),b));
    }
    private static double valid(double n){return Double.isFinite(n)&&n>=0?n:0;}
    private static float ratio(double target,double original){return original>1e-9?(float)Math.clamp(target/original,0,1):1;}
}
