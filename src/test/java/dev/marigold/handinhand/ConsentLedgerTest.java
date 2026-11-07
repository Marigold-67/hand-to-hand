package dev.marigold.handinhand;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
class ConsentLedgerTest {
    final UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();
    @Test void onlyRecipientCanAcceptAndNonceCannotBeReplayed(){var l=new ConsentLedger();var r=l.request(a,b,0,600,100);assertThrows(IllegalStateException.class,()->l.accept(c,r.token(),1));assertThrows(IllegalStateException.class,()->l.accept(a,r.token(),1));assertNotNull(l.accept(b,r.token(),1));assertThrows(IllegalStateException.class,()->l.accept(b,r.token(),2));assertEquals(a,l.pair(b).other(b));}
    @Test void exclusiveRequestsAndPairing(){var l=new ConsentLedger();var r=l.request(a,b,0,600,100);assertThrows(IllegalStateException.class,()->l.request(c,b,1,600,100));l.accept(b,r.token(),2);assertThrows(IllegalStateException.class,()->l.request(c,a,101,600,100));l.release(a);assertNull(l.pair(b));assertNotNull(l.request(c,b,102,600,100));}
    @Test void expiryAndCancellationInvalidateButtons(){var l=new ConsentLedger();var r=l.request(a,b,0,600,100);assertThrows(IllegalStateException.class,()->l.accept(b,r.token(),600));assertEquals(1,l.expire(600).size());var next=l.request(a,b,601,600,100);assertEquals(next,l.cancel(a));assertThrows(IllegalStateException.class,()->l.accept(b,next.token(),602));}
    @Test void cooldownSurvivesCancellation(){var l=new ConsentLedger();l.request(a,b,0,600,100);l.cancel(b);assertThrows(IllegalStateException.class,()->l.request(a,b,99,600,100));assertNotNull(l.request(a,b,100,600,100));}
    @Test void declineDoesNotPairAndSelfIsRejected(){var l=new ConsentLedger();assertThrows(IllegalStateException.class,()->l.request(a,a,0,600,100));var r=l.request(a,b,0,600,100);l.decline(b,r.token(),1);assertNull(l.pair(a));assertNull(l.pending(a));}
}
