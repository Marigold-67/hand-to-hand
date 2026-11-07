package dev.marigold.handinhand;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class Alpha7PolicyTest {
 @Test void angularWrapUsesTheShortTurn(){var f=new Formation(Math.toRadians(179),1);f.turnToward(Math.toRadians(-179),.2);assertEquals(2,Math.toDegrees(f.heading()-Math.toRadians(179)),1e-8);}
 @Test void aFullTurnCannotChangeTheHeldSideOrSnapTheBody(){var f=new Formation(0,-1);for(int i=0;i<100;i++){double old=f.heading();f.turnToward(i*.1,.2);assertTrue(Math.abs(f.heading()-old)<=.200001);assertEquals(-1,f.sign());}}
 @Test void invalidHeadingCannotPoisonTheFormation(){var f=new Formation(1,1);f.turnToward(Double.NaN,.2);assertEquals(1,f.heading());assertThrows(IllegalArgumentException.class,()->new Formation(Double.NaN,1));}
 @Test void aDeniedGrabDoesNotCreateAnyLink(){var l=new ConsentLedger();var a=UUID.randomUUID();var b=UUID.randomUUID();assertThrows(IllegalStateException.class,()->l.grab(a,b,0,20,false));assertNull(l.pair(a));assertNull(l.pair(b));}
 @Test void grabbingCannotStealAPendingOrAcceptedPartner(){var l=new ConsentLedger();var a=UUID.randomUUID();var b=UUID.randomUUID();var c=UUID.randomUUID();var r=l.request(a,b,0,100,0);assertThrows(IllegalStateException.class,()->l.grab(c,b,0,20,true));l.accept(b,r.token(),0);assertThrows(IllegalStateException.class,()->l.grab(c,b,1,20,true));}
 @Test void targetCanReleaseAndAnotherGrabberCannotImmediatelyReacquire(){var l=new ConsentLedger();var a=UUID.randomUUID();var b=UUID.randomUUID();var c=UUID.randomUUID();var pair=l.grab(a,b,0,20,true);assertTrue(pair.grab());assertEquals(a,pair.first());l.release(b,1,20);assertNull(l.pair(a));assertThrows(IllegalStateException.class,()->l.grab(c,b,2,20,true));assertNotNull(l.grab(c,b,21,20,true));}
 @Test void normalAcceptanceStillRequiresItsRecipientsNonce(){var l=new ConsentLedger();var a=UUID.randomUUID();var b=UUID.randomUUID();var c=UUID.randomUUID();var r=l.request(a,b,0,100,0);assertThrows(IllegalStateException.class,()->l.accept(c,r.token(),1));assertFalse(l.accept(b,r.token(),1).grab());}
 @Test void sameTickReacceptanceCannotReuseTheOldSession(){var l=new ConsentLedger();var a=UUID.randomUUID();var b=UUID.randomUUID();var r=l.request(a,b,0,100,0);var first=l.accept(b,r.token(),0);l.release(a);var next=l.request(a,b,0,100,0);assertNotEquals(first.started(),l.accept(b,next.token(),0).started());}
}
