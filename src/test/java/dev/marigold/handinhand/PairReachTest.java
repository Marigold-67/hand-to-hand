package dev.marigold.handinhand;
import dev.marigold.handinhand.HandGeometry.V;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PairReachTest {
 @Test void waitingForAPartnerDoesNotInventSidewaysMotion(){var move=PairReach.move(new V(.9,0,1.7),new V(0,0,0),new V(0,0,.2),1.3,2,false);assertEquals(0,move.x(),1e-12);}
 @Test void normalSpacingHasNoBrake(){var move=new V(0,0,.22);assertEquals(move,PairReach.move(new V(.9,0,0),new V(0,0,0),move,1.3,2,true));}
 @Test void verticalJumpIsNeverClipped(){assertEquals(.42,PairReach.move(new V(2,0,0),new V(0,0,0),new V(.2,.42,0),1.3,2,true).y());}
 @Test void hardBoundCannotBeCrossedFromInside(){var self=new V(1.9,0,0);var move=PairReach.move(self,new V(0,0,0),new V(.3,0,.1),1.3,2,false);assertTrue(self.add(move).length()<=2.000001);}
 @Test void tangentialAndInwardMovementRemainPossible(){var move=PairReach.move(new V(1.8,0,0),new V(0,0,0),new V(-.2,0,.2),1.3,2,true);assertEquals(-.2,move.x());assertEquals(.2,move.z());}
 @Test void AStaleOutsidePositionReturnsGently(){var move=PairReach.move(new V(10,0,0),new V(0,0,0),new V(.3,0,0),1.3,2,true);assertTrue(move.x()<0&&move.length()<=.100001);}
}
