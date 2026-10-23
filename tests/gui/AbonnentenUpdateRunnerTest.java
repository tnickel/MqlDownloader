package gui;
import database.SubscriberStat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import rest.UpdateJob;
import static org.junit.jupiter.api.Assertions.*;

class AbonnentenUpdateRunnerTest {
    @TempDir Path dir;
    private SubscriberStat p(int id,String version,int subs) { return new SubscriberStat(""+id,version,"Provider"+id,subs,0,null,null,null,"https://www.mql5.com/en/signals/"+id); }
    @Test void globallyHighest200AcrossBothPlatformsAndNoFullFreshnessMarker() throws Exception {
        Path marker=dir.resolve("marker");Files.writeString(marker,"old full-profile freshness");
        List<SubscriberStat> published=new ArrayList<>();List<String> calls=new ArrayList<>();UpdateJob job=new UpdateJob("test");job.reservieren();
        new AbonnentenUpdateRunner(job,new AbonnentenUpdateRunner.Services(){
            public List<SubscriberStat> collect(String version,int target) {
                calls.add(version);assertEquals(200,target);List<SubscriberStat> rows=new ArrayList<>();
                for(int i=0;i<200;i++) rows.add(p(i+1,version.toLowerCase(),version.equals("MQL4")?200-i:400-i));return rows;
            }
            public void publish(List<SubscriberStat> rows) { published.addAll(rows); }
        },marker,200).run();
        assertEquals(Arrays.asList("MQL4","MQL5"),calls);assertEquals(UpdateJob.State.DONE,job.state());
        assertEquals(200,published.size());assertTrue(published.stream().allMatch(p -> p.getMqlVersion().equals("mql5")));
        assertEquals(400,published.get(0).getSubscribers());assertFalse(Files.exists(marker));
        Map<?,?> result=(Map<?,?>)job.statusJson().get("ergebnis");assertEquals(true,result.get("nurAbonnenten"));assertEquals(200,result.get("signaleGeliefert"));
    }
    @Test void interleavedPlatformsProduceTheHighest200InsteadOfTakingOnePlatformFirst() {
        List<SubscriberStat> candidates=new ArrayList<>();
        // Both lists are subscriber-sorted; the global ranking alternates the platforms.
        for(int i=0;i<200;i++) candidates.add(p(i+1,"mql4",1000-2*i));
        for(int i=0;i<200;i++) candidates.add(p(i+1,"mql5",999-2*i));
        List<SubscriberStat> selected=AbonnentenUpdateRunner.top(candidates,200);
        assertEquals(200,selected.size());
        assertEquals(100,selected.stream().filter(p->p.getMqlVersion().equals("mql4")).count());
        assertEquals(100,selected.stream().filter(p->p.getMqlVersion().equals("mql5")).count());
        for(int i=0;i<200;i++) assertEquals(1000-i,selected.get(i).getSubscribers());
        assertTrue(selected.stream().allMatch(p->Integer.parseInt(p.getSignalId())<=100));
    }
    @Test void failedSecondListPublishesNothingAndKeepsOldMarker() throws Exception {
        Path marker=dir.resolve("marker");Files.writeString(marker,"old");UpdateJob job=new UpdateJob("test");
        new AbonnentenUpdateRunner(job,new AbonnentenUpdateRunner.Services(){
            public List<SubscriberStat> collect(String version,int target) throws Exception {
                if(version.equals("MQL5"))throw new Exception("page not verified");return Arrays.asList(p(1,"mql4",12));
            }
            public void publish(List<SubscriberStat> rows) { fail("No partial publication"); }
        },marker,200).run();assertEquals(UpdateJob.State.ERROR,job.state());assertTrue(Files.exists(marker));
    }
    @Test void mergedRankingKeepsPlatformIdentityAndRejectsDuplicateOrZero() {
        List<SubscriberStat> rows=AbonnentenUpdateRunner.top(Arrays.asList(p(1,"mql4",7),p(1,"mql5",9),p(2,"mql4",8)),2);
        assertEquals("mql5",rows.get(0).getMqlVersion());assertEquals("2",rows.get(1).getSignalId());
        assertThrows(IllegalArgumentException.class,()->AbonnentenUpdateRunner.top(Arrays.asList(p(1,"mql4",0)),200));
        assertThrows(IllegalArgumentException.class,()->AbonnentenUpdateRunner.top(Arrays.asList(p(1,"mql4",7),p(1,"mql4",7)),200));
    }
}
