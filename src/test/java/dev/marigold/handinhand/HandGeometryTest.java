package dev.marigold.handinhand;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Random;
class HandGeometryTest {
    @Test void wristsMeetForDifferentSizesAndOrientations(){var random=new Random(73);int valid=0;for(int i=0;i<10000;i++){var a=new HandGeometry.V(random.nextDouble(),random.nextDouble()*2,random.nextDouble());var b=new HandGeometry.V(random.nextDouble()*2,random.nextDouble()*2,random.nextDouble()*2);double ar=.25+random.nextDouble(),br=.25+random.nextDouble();var p=HandGeometry.contact(a,ar,b,br);if(p!=null){valid++;assertEquals(ar,p.sub(a).length(),1e-8);assertEquals(br,p.sub(b).length(),1e-8);}}assertTrue(valid>1000);}
    @Test void impossibleContactAndInvalidScaleAreRejected(){var a=new HandGeometry.V(0,0,0);assertNull(HandGeometry.contact(a,.6,new HandGeometry.V(2,0,0),.6));assertNull(HandGeometry.contact(a,2,new HandGeometry.V(.1,0,0),.1));assertNull(HandGeometry.contact(a,Double.NaN,new HandGeometry.V(1,0,0),.6));}
    @Test void verticallyAlignedArmsStayFinite(){var p=HandGeometry.contact(new HandGeometry.V(0,0,0),1,new HandGeometry.V(0,1,0),1);assertNotNull(p);assertTrue(Double.isFinite(p.z()));}
}
