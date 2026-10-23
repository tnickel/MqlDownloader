package rest;

import config.ConfigurationManager;
import database.DatabaseManager;
import database.SubscriberStat;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CatalogScopeTest {
    @TempDir Path temp;

    @Test void fullCatalogueSurvivesTop200AndEveryPageDeclaresItsScope() throws Exception {
        ConfigurationManager config = new ConfigurationManager(temp.toString());
        config.initializeDirectories();
        DatabaseManager db = new DatabaseManager(temp.toString());
        String dbPath=temp.resolve("config/subscribers").toString().replace('\\','/');
        try (Connection keepOpen=DriverManager.getConnection("jdbc:h2:file:"+dbPath+";AUTO_SERVER=TRUE","sa","")) {
            for(int id=1;id<=205;id++) db.checkAndUpdateSubscribers(String.valueOf(id),"mql4","Full "+id,1,null);
            try(ServerSocket socket=new ServerSocket(0)) { config.setApiPort(socket.getLocalPort()); }
            RestApiServer server=new RestApiServer(db,config);
            try {
                server.start();
                Response initial=get(config.getApiPort(),"catalogScope=subscribers");
                assertEquals(503,initial.status);
                assertTrue(get(config.getApiPort(),"").body.contains("\"catalogScope\":\"full\""));
                List<SubscriberStat> selected=new ArrayList<>();
                for(int id=1;id<=200;id++) selected.add(new SubscriberStat(String.valueOf(id),"mql4","Selected "+id,9,0,null,null,null,null));
                db.publishSubscriberCatalogue(selected);
                Response legacy=get(config.getApiPort(),"minSubscribers=0");
                assertTrue(legacy.body.contains("\"total\":200"));
                assertTrue(legacy.body.contains("\"catalogScope\":\"subscribers\""));
                for(String page: new String[]{"offset=0&limit=1","offset=200&limit=5","offset=999&limit=1"}) {
                    Response full=get(config.getApiPort(),"catalogScope=full&minSubscribers=0&"+page);
                    assertEquals(200,full.status);
                    assertTrue(full.body.contains("\"total\":205"),full.body);
                    assertTrue(full.body.contains("\"catalogScope\":\"full\""));
                }
                Response tail=get(config.getApiPort(),"catalogScope=full&minSubscribers=0&offset=200&limit=5");
                assertTrue(tail.body.contains("Full 205"));
                for(String invalid:new String[]{"unknown","all","FULL",""})
                    assertEquals(400,get(config.getApiPort(),"catalogScope="+invalid).status);
                db.publishSubscriberCatalogue(Collections.emptyList());
                Response empty=get(config.getApiPort(),"catalogScope=subscribers");
                assertEquals(200,empty.status);
                assertTrue(empty.body.contains("\"total\":0"));
                assertTrue(empty.body.contains("\"catalogScope\":\"subscribers\""));
                assertTrue(get(config.getApiPort(),"catalogScope=full&minSubscribers=0").body.contains("\"total\":205"));
                try(Statement q=keepOpen.createStatement()) { q.execute("DROP TABLE signal_subscribers"); }
                assertEquals(500,get(config.getApiPort(),"catalogScope=full").status,
                        "A failed storage read must not publish an empty full catalogue");
                assertEquals(500,get(config.getApiPort(),"catalogScope=subscribers").status,
                        "A failed storage read must not publish an empty subscriber selection");
            } finally { server.stop(); }
            try(Statement q=keepOpen.createStatement()) { q.execute("SHUTDOWN"); }
        }
    }

    private static Response get(int port,String query) throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL("http://127.0.0.1:"+port+"/api/v1/providers?"+query).openConnection();
        conn.setConnectTimeout(5000);conn.setReadTimeout(10000);
        int status=conn.getResponseCode();
        try(java.io.InputStream stream=status<400?conn.getInputStream():conn.getErrorStream()) {
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
            while((n=stream.read(buffer))!=-1)bytes.write(buffer,0,n);
            return new Response(status,new String(bytes.toByteArray(),StandardCharsets.UTF_8));
        } finally { conn.disconnect(); }
    }
    private static class Response {
        final int status; final String body;
        Response(int status,String body){this.status=status;this.body=body;}
    }
}
