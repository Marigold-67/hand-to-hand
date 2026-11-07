package dev.marigold.handinhand;
import dev.marigold.handinhand.HandGeometry.V;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SampleMotionTest {
 SampleMotion.State state(double y,boolean ground){return new SampleMotion.State(new V(0,y,0),new V(.2,.3,0),ground,false);}
 @Test void takeoffDoesNotUseFutureGroundFlag(){var s=SampleMotion.interpolate(state(0,true),state(.42,false),.5,1);assertEquals(.21,s.position().y(),1e-9);assertEquals(.42,s.velocity().y(),1e-9);assertFalse(s.grounded());}
 @Test void landingRemainsAirborneUntilLandingTime(){assertFalse(SampleMotion.interpolate(state(.2,false),state(0,true),.8,1).grounded());assertTrue(SampleMotion.interpolate(state(.2,false),state(0,true),1,1).grounded());}
 @Test void supportedStairStillHasVerticalDisplacement(){var s=SampleMotion.interpolate(state(0,true),state(.5,true),.5,1);assertTrue(s.grounded());assertEquals(.5,s.velocity().y());}
 @Test void velocityUsesActualFrameInterval(){assertEquals(.25,SampleMotion.interpolate(state(0,true),state(.5,true),.5,2).velocity().y());}
 @Test void airPredictionFallsInsteadOfFreezingY(){var s=SampleMotion.predict(new SampleMotion.State(new V(0,2,0),new V(.2,-.2,0),false,false),new V(.2,0,0),1);assertEquals(1.7256,s.position().y(),1e-9);}
 @Test void groundPredictionDoesNotSink(){assertEquals(0,SampleMotion.predict(state(0,true),new V(.2,0,0),1.5).position().y());}
 @Test void presentationWeightVariesContinuouslyWithHorizontalReach(){assertEquals(1,SampleMotion.presentationWeight(.9,.9,1));assertEquals(0,SampleMotion.presentationWeight(1.8,.9,1));assertTrue(Math.abs(SampleMotion.presentationWeight(1.3,.9,1)-SampleMotion.presentationWeight(1.30001,.9,1))<.001);}
}
