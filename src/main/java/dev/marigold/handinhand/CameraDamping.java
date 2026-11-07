package dev.marigold.handinhand;
/** Small render-only eye-height response. No world position, force or stored velocity. */
public final class CameraDamping {
 private double value=Double.NaN;
 private final double response;
 public CameraDamping(){this(.065);}
 public CameraDamping(double response){this.response=Math.max(.025,Math.min(.2,response));}
 public double update(double target,double seconds,double minimum,double maximum,boolean reset){
  if(reset||!Double.isFinite(value)||seconds<0||seconds>.25)value=target;
  else value+=(target-value)*(1-Math.exp(-Math.max(0,seconds)/response));
  value=Math.max(minimum,Math.min(maximum,value));return value;
 }
 public void clear(){value=Double.NaN;}
}
