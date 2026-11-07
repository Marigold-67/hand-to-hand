package dev.marigold.handinhand;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GrabPolicyTest {
 @Test void defaultDisabledReportsDisabled(){assertEquals(GrabPolicy.Block.GRAB_DISABLED,GrabPolicy.check(true,false,true,true,true));}
 @Test void enabledOpOnlyIsNotReportedDisabledForNonOp(){var b=GrabPolicy.check(true,true,true,true,false);assertEquals(GrabPolicy.Block.OP_REQUIRED,b);assertTrue(GrabPolicy.message(b).contains("is enabled"));}
 @Test void permittedOperatorCanGrab(){assertEquals(GrabPolicy.Block.NONE,GrabPolicy.check(true,true,true,true,true));}
 @Test void allowingPlayersDoesNotRequireOp(){assertEquals(GrabPolicy.Block.NONE,GrabPolicy.check(true,true,true,false,false));}
 @Test void followingDependencyHasItsOwnReason(){assertEquals(GrabPolicy.Block.FOLLOWING_DISABLED,GrabPolicy.check(true,true,false,false,true));}
 @Test void globalDisableHasItsOwnReason(){assertEquals(GrabPolicy.Block.HOLDING_DISABLED,GrabPolicy.check(false,true,true,false,true));}
}
