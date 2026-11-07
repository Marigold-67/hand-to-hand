package dev.marigold.handinhand;
/** Keep a normal voluntary jump native until landing, not merely while Space is held. */
public final class JumpWindow {
 private long started=Long.MIN_VALUE;
 public boolean update(long tick,boolean grounded,boolean jumpHeld,boolean rising,boolean permitted){
  if(!permitted){clear();return false;}
  if(jumpHeld&&(grounded||rising)&&(started==Long.MIN_VALUE||grounded))started=tick;
  if(started==Long.MIN_VALUE)return false;
  long age=tick-started;
  if(age<0||age>=120||grounded&&!jumpHeld&&age>=2){clear();return false;}
  return true;
 }
 public void clear(){started=Long.MIN_VALUE;}
}
