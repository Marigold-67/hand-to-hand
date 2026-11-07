package dev.marigold.handinhand;
import dev.marigold.handinhand.HandGeometry.V;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TetherMathTest {
    static final V ZERO=new V(0,0,0);
    @Test void startAtWalkingPaceWithoutAnAccelerationRamp(){assertEquals(new V(0,0,.22),TetherMath.follow(ZERO,ZERO,ZERO,new V(0,0,.22),.5,false,.18).movement());}
    @Test void stopAtTheSlotWithoutAStoredKick(){assertEquals(ZERO,TetherMath.follow(ZERO,ZERO,ZERO,ZERO,.5,true,.18).movement());}
    @Test void passiveCorrectionIsBoundedAndConverges(){V p=ZERO,goal=new V(4,0,0);for(int i=0;i<100;i++){var step=TetherMath.follow(p,goal,ZERO,ZERO,.5,true,.18);assertTrue(step.movement().length()<=.180000001);p=p.add(step.movement());}assertTrue(goal.sub(p).length()<.008);}
    @Test void followingMatchesPaceWithoutTrailingBehind(){V p=new V(.9,0,0),lead=ZERO,pace=new V(0,0,.22);for(int i=0;i<200;i++){var step=TetherMath.follow(p,lead.add(new V(.9,0,0)),ZERO,pace,.5,false,.18);p=p.add(step.movement());lead=lead.add(pace);assertEquals(lead.z(),p.z(),1e-9);assertEquals(.9,p.x()-lead.x(),1e-9);}}
    @Test void strafingWorksInBothDirections(){for(double x:new double[]{-.22,.22}){V pace=new V(x,0,0);assertEquals(pace,TetherMath.follow(ZERO,ZERO,ZERO,pace,.5,false,.18).movement());}}
    @Test void activeControlsNeverHaveWeightOrRangeResistance(){V own=new V(.22,-.078,-.22);var passive=TetherMath.follow(ZERO,new V(10,0,0),own,ZERO,.5,false,.18);assertSame(own,TetherMath.movement(true,own,passive));}
    @Test void gravityIsPreservedWhenGroundFollowing(){V own=new V(0,-.078,0);assertEquals(-.078,TetherMath.follow(ZERO,new V(2,4,0),own,ZERO,.5,false,.18).movement().y());}
    @Test void aPassiveLiftCanRiseHoverAndDescend(){for(double y:new double[]{-.15,0,.15})assertEquals(y,TetherMath.follow(ZERO,ZERO,new V(0,-2,0),new V(0,y,0),.5,true,.18).movement().y(),1e-12);}
    @Test void repeatedPlansCannotStackOrRetainVelocity(){for(int i=0;i<1000;i++)assertEquals(new V(.2,0,0),TetherMath.follow(ZERO,ZERO,ZERO,new V(.2,0,0),.5,true,.18).movement());}
    @Test void reversingChangesDirectionImmediately(){assertEquals(new V(-.22,0,0),TetherMath.follow(ZERO,ZERO,ZERO,new V(-.22,0,0),.5,false,.18).movement());}
    @Test void aSnapshotAlreadyAdvancedByOneTickDoesNotDoubleCountWalkingPace(){V pace=new V(0,0,.22);assertEquals(pace,TetherMath.follow(ZERO,pace,ZERO,pace,.5,false,.18).movement());}
    @Test void turnsTrackABesideSlotWithoutLag(){V lead=ZERO,p=new V(.9,0,0),side=p;for(int i=0;i<240;i++){double angle=(i+1)*.03;V nextSide=new V(Math.cos(angle)*.9,0,Math.sin(angle)*.9),pace=new V(-Math.sin(angle)*.2,0,Math.cos(angle)*.2);var step=TetherMath.follow(p,lead.add(side),ZERO,pace.add(nextSide.sub(side)),.6,false,.18);lead=lead.add(pace);p=p.add(step.movement());side=nextSide;assertTrue(p.sub(lead.add(side)).length()<1e-9);}}
    @Test void invalidPacketsCannotProduceNanMotion(){V own=new V(.1,-.078,0);assertEquals(own,TetherMath.follow(ZERO,new V(Double.NaN,0,0),own,ZERO,.5,false,.18).movement());}
}
