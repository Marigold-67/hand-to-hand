package dev.marigold.handinhand;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MotionPolicyTest {
    @Test void shortLossRecoversButPersistentLossReleases(){var s=new LinkStability();for(int i=0;i<12;i++)assertFalse(s.shouldRelease(false,false,12));assertFalse(s.shouldRelease(true,false,12));for(int i=0;i<12;i++)assertFalse(s.shouldRelease(false,false,12));assertTrue(s.shouldRelease(false,false,12));}
    @Test void hardDistanceAndZeroGraceReleaseImmediately(){assertTrue(new LinkStability().shouldRelease(true,true,12));assertTrue(new LinkStability().shouldRelease(false,false,0));}
    @Test void flightFactorsMatchAxesWithoutBoostingAnyone(){var f=FlightRates.match(.1,.05,1,1,true,false);assertEquals(.25,f.horizontalA(),1e-6);assertEquals(.5,f.verticalA(),1e-6);assertEquals(1,f.horizontalB());assertEquals(1,f.verticalB());}
    @Test void flightHandlesScalingAndInvalidValues(){var f=FlightRates.match(.05,.05,.75,1.15,true,true);assertEquals(1,f.horizontalA());assertEquals(.75/1.15,f.horizontalB(),1e-6);assertEquals(f.horizontalB(),f.verticalB());var zero=FlightRates.match(Double.NaN,.05,1,1,false,false);assertTrue(Float.isFinite(zero.horizontalA()));assertEquals(0,zero.horizontalB());}
}
