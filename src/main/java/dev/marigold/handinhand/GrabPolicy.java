package dev.marigold.handinhand;
/** Distinct server-authoritative reasons; enabling the feature never silently removes OP policy. */
public final class GrabPolicy {
 public enum Block { NONE,HOLDING_DISABLED,GRAB_DISABLED,FOLLOWING_DISABLED,OP_REQUIRED }
 private GrabPolicy(){}
 public static Block check(boolean holding,boolean enabled,boolean following,boolean opOnly,boolean permission){
  if(!holding)return Block.HOLDING_DISABLED;
  if(!enabled)return Block.GRAB_DISABLED;
  if(!following)return Block.FOLLOWING_DISABLED;
  if(opOnly&&!permission)return Block.OP_REQUIRED;
  return Block.NONE;
 }
 public static String message(Block block){return switch(block){
  case NONE->"Roleplay grabbing is available.";
  case HOLDING_DISABLED->"Hand holding is disabled on this server. OP setup: /handholding config enabled true";
  case GRAB_DISABLED->"Roleplay grabbing is disabled. OP setup: /handholding config grab true";
  case FOLLOWING_DISABLED->"Roleplay grabbing is enabled, but movement following is disabled. OP setup: /handholding config movementTether true";
  case OP_REQUIRED->"Roleplay grabbing is enabled, but only server OPs may initiate grabs. An OP can allow other players with /handholding config grabOpOnly false";
 };}
}
