package dev.marigold.handinhand;
import dev.marigold.handinhand.HandGeometry.V;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ContactFilterTest {
 @Test void targetResponseDoesNotDependOnFrameRate(){V target=new V(1,2,3),zero=new V(0,0,0);var a=new ContactFilter();var b=new ContactFilter();a.update(zero,0,false);b.update(zero,0,false);V x=null,y=null;for(int i=0;i<4;i++)x=a.update(target,.025,false);for(int i=0;i<12;i++)y=b.update(target,1./120,false);assertEquals(x.x(),y.x(),1e-10);assertEquals(x.y(),y.y(),1e-10);}
 @Test void repeatedPassDoesNotAdvanceFilter(){var f=new ContactFilter();f.update(new V(0,0,0),0,false);var x=f.update(new V(1,0,0),.025,false);assertEquals(x,f.update(new V(1,0,0),0,false));}
 @Test void resetReleaseAndLongGapsDoNotCarryOldPose(){var f=new ContactFilter();f.update(new V(0,0,0),0,false);var target=new V(4,0,0);assertEquals(target,f.update(target,.05,true));f.clear();assertEquals(target,f.update(target,.05,false));assertEquals(new V(2,0,0),f.update(new V(2,0,0),1,false));}
 @Test void projectedSharedTargetPreservesBothArmLengths(){V a=new V(0,1,0),b=new V(.8,1.2,.1);var c=HandGeometry.projectContact(a,.625,b,.7,new V(.3,1.1,5));assertNotNull(c);assertEquals(.625,c.sub(a).length(),1e-9);assertEquals(.7,c.sub(b).length(),1e-9);}
 @Test void unreachableContactStaysUnreachable(){assertNull(HandGeometry.projectContact(new V(0,0,0),.6,new V(4,0,0),.6,new V(1,0,0)));}
 @Test void verticalAxisProjectionRemainsFinite(){var c=HandGeometry.projectContact(new V(0,0,0),.7,new V(0,.8,0),.7,new V(0,.4,0));assertNotNull(c);assertTrue(Double.isFinite(c.length()));}
}
