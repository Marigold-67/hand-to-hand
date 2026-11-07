package dev.marigold.handinhand;

/** Brief reach/visibility losses may recover, while explicit release and hard separation stay immediate. */
public final class LinkStability {
    private int missedTicks;
    public boolean shouldRelease(boolean reachableAndVisible,boolean hardSeparation,int graceTicks) {
        if(hardSeparation)return true;
        if(reachableAndVisible){missedTicks=0;return false;}
        return ++missedTicks>Math.max(0,graceTicks);
    }
}
