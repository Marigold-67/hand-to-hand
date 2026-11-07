package dev.marigold.handinhand;
import dev.marigold.handinhand.HandGeometry.V;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FollowMotionTest {
 @Test void jumpingWithoutDirectionKeepsThePartnersForwardPace(){var move=FollowMotion.cooperate(new V(0,0,0),new V(0,0,.22),new V(0,0,0),.04,true);assertEquals(new V(0,0,.22),move);}
 @Test void steadyPaceDoesNotGetAddedTwice(){assertEquals(new V(.2,0,0),FollowMotion.follow(new V(.2,0,0),new V(.2,0,0),.4,.08));}
 @Test void aLateSnapshotCannotSnapTheCameraAcrossTheGap(){var move=FollowMotion.follow(new V(2,0,0),new V(.2,0,0),.4,.06);assertEquals(.26,move.x(),1e-9);}
 @Test void catchUpConvergesWithoutUnboundedAcceleration(){V position=new V(0,0,0);for(int i=0;i<100;i++){var step=FollowMotion.follow(new V(1,0,0).sub(position),new V(0,0,0),.3,.08);assertTrue(step.length()<=.080001);position=position.add(step);}assertEquals(1,position.x(),.012);}
 @Test void invalidInputCannotCreateMotion(){assertEquals(0,FollowMotion.follow(new V(Double.NaN,0,0),new V(0,0,0),.3,.08).length());}
 @Test void alignedPlayersMatchPaceWithoutDeletingStrafing(){var move=FollowMotion.cooperate(new V(.05,0,.12),new V(0,0,.22),new V(0,0,0),.04);assertEquals(.05,move.x(),1e-9);assertEquals(.22,move.z(),1e-9);}
 @Test void opposedInputsDoNotBecomeForcedDragging(){var own=new V(0,0,-.2);assertEquals(own,FollowMotion.cooperate(own,new V(0,0,.2),new V(2,0,2),.04));}
 @Test void cooperativeCorrectionIsSmallAndBounded(){var move=FollowMotion.cooperate(new V(0,0,.2),new V(0,0,.2),new V(50,0,50),.04);assertTrue(move.sub(new V(0,0,.2)).length()<=.040001);}
 @Test void alternatingZeroAndBurstArrivalsDoNotBecomeCameraStopStart(){
  double target=0,position=0,lastStep=.2,maxChange=0;
  for(int tick=0;tick<100;tick++){target+=tick%2==0?0:.4;var step=FollowMotion.follow(new V(target-position,0,0),new V(.2,0,0),.4,.08);position+=step.x();if(tick>20)maxChange=Math.max(maxChange,Math.abs(step.x()-lastStep));lastStep=step.x();}
  assertTrue(maxChange<.08,"The camera reproduced the 0/.4 packet pulse");assertTrue(Math.abs(position-target)<.25);
 }
}
