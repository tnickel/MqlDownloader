package database;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class SubscriberCatalogueTest {
    @TempDir Path dir;
    private SubscriberStat p(String id,String version,int subs,String url) { return new SubscriberStat(id,version,"Name"+id,subs,0,null,null,null,url); }
    private Connection open() throws Exception { return DriverManager.getConnection("jdbc:h2:file:"+dir.resolve("config/subscribers").toString().replace('\\','/')+";DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE","sa",""); }
    @Test void scopeReplacementPreservesArchiveRiskUrlAndAllMeasurements() throws Exception {
        DatabaseManager db=new DatabaseManager(dir.toString());
        db.checkAndUpdateSubscribers("1","mql4","Old1",10,"https://www.mql5.com/en/signals/1");db.updateSignalRisk("1","mql4","risk proof");
        db.checkAndUpdateSubscribers("2","mql5","Old2",20,"https://example.test/2");
        db.publishSubscriberCatalogue(Arrays.asList(p("1","mql4",12,null),p("3","mql5",30,"https://example.test/3")));
        assertEquals(2,db.getCatalogueSubscriberStatistics().size());assertEquals(3,db.getAllSubscriberStatistics().size());
        SubscriberStat one=db.getAllSubscriberStatistics().stream().filter(p->p.getSignalId().equals("1")).findFirst().get();
        assertEquals("risk proof",one.getRisk());assertEquals("https://www.mql5.com/en/signals/1",one.getUrl());assertEquals(2,one.getLatestChange());
        assertEquals(2,db.getSubscriberHistory("1","mql4").size());
        db.publishSubscriberCatalogue(Collections.singletonList(p("3","mql5",31,null)));
        assertEquals(1,db.getCatalogueSubscriberStatistics().size());assertEquals(3,db.getAllSubscriberStatistics().size());
        assertEquals(2,db.getSubscriberHistory("1","mql4").size());
        db.publishSubscriberCatalogue(Collections.emptyList());assertTrue(db.getCatalogueSubscriberStatistics().isEmpty());
        db.clearSubscriberCatalogueSelection();assertEquals(3,db.getCatalogueSubscriberStatistics().size());
    }
    @Test void failedSqlRollsBackMembershipCountersAndHistory() throws Exception {
        DatabaseManager db=new DatabaseManager(dir.toString());db.publishSubscriberCatalogue(Collections.singletonList(p("1","mql4",10,"old")));
        try(Connection c=open();Statement q=c.createStatement()) { q.execute("ALTER TABLE signal_subscribers ADD CONSTRAINT forbid_999 CHECK(signal_id <> '999')"); }
        assertThrows(SQLException.class,()->db.publishSubscriberCatalogue(Arrays.asList(p("1","mql4",11,null),p("999","mql5",40,null))));
        assertEquals(1,db.getCatalogueSubscriberStatistics().size());assertEquals(10,db.getCatalogueSubscriberStatistics().get(0).getSubscribers());
        assertEquals(1,db.getSubscriberHistory("1","mql4").size());assertTrue(db.getSubscriberHistory("999","mql5").isEmpty());
    }
}
