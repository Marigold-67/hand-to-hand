package dev.marigold.handinhand;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectPackage;
public class LocalTestRunner {
    public static void main(String[] args) {
        var listener=new SummaryGeneratingListener();
        var launcher=LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(LauncherDiscoveryRequestBuilder.request().selectors(selectPackage("dev.marigold.handinhand")).build());
        listener.getSummary().printTo(new java.io.PrintWriter(System.out,true));
        listener.getSummary().printFailuresTo(new java.io.PrintWriter(System.out,true));
        if(listener.getSummary().getTotalFailureCount()>0||listener.getSummary().getTestsFoundCount()==0)System.exit(1);
    }
}
