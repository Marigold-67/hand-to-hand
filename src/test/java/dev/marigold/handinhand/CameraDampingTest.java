package dev.marigold.handinhand;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CameraDampingTest {
 @Test void staysInsideLegalEyeRange(){var f=new CameraDamping();f.update(0,0,-1,1,true);assertEquals(.8,f.update(1,.025,.8,1.1,false));}
 @Test void responseIsFrameRateIndependent(){var a=new CameraDamping();var b=new CameraDamping();a.update(0,0,-10,10,true);b.update(0,0,-10,10,true);double x=0,y=0;for(int i=0;i<4;i++)x=a.update(1,.025,-10,10,false);for(int i=0;i<12;i++)y=b.update(1,1./120,-10,10,false);assertEquals(x,y,1e-10);}
 @Test void releaseAndTeleportResetImmediately(){var f=new CameraDamping();f.update(0,0,-10,10,true);assertEquals(4,f.update(4,.025,3.8,4.2,true));f.clear();assertEquals(2,f.update(2,.025,1.8,2.2,false));}
 @Test void doesNotOvershootATarget(){var f=new CameraDamping();f.update(0,0,-10,10,true);for(int i=0;i<20;i++){double x=f.update(1,.025,-10,10,false);assertTrue(x>=0&&x<=1);}}
}
