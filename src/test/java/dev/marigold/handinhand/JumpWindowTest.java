package dev.marigold.handinhand;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class JumpWindowTest {
 @Test void tappingSpaceRetainsNativeAirborneMotion(){var j=new JumpWindow();assertTrue(j.update(10,true,true,true,true));for(int i=11;i<25;i++)assertTrue(j.update(i,false,false,i<16,true));assertFalse(j.update(25,true,false,false,true));}
 @Test void blockedJumpDoesNotLeaveControlActive(){var j=new JumpWindow();j.update(10,true,true,true,true);assertTrue(j.update(11,true,false,false,true));assertFalse(j.update(12,true,false,false,true));}
 @Test void grabAndFlightCannotStartAVoluntaryJumpLease(){var j=new JumpWindow();assertFalse(j.update(0,true,true,true,false));assertFalse(j.update(1,false,false,true,true));}
 @Test void changingModeClearsAnExistingLease(){var j=new JumpWindow();j.update(0,true,true,true,true);assertFalse(j.update(1,false,false,true,false));assertFalse(j.update(2,false,false,true,true));}
 @Test void sessionResetAndExpiryClearTheLease(){var j=new JumpWindow();j.update(0,true,true,true,true);assertFalse(j.update(120,false,false,false,true));j.update(130,true,true,true,true);j.clear();assertFalse(j.update(131,false,false,true,true));}
 @Test void aLaterJumpCanStartNormally(){var j=new JumpWindow();j.update(0,true,true,true,true);j.update(12,true,false,false,true);assertTrue(j.update(20,true,true,true,true));}
}
