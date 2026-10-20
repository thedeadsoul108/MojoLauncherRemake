package git.artdeell.mojo.content;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

public class ModrinthQueryTest {

    @Test
    public void searchBuildsEncodedFacetGroups() throws Exception {
        String url = ModrinthQuery.search("sodium", "mod", "1.20.1", "fabric",
                "optimization", "client", "downloads", 20, 40);
        assertTrue(url.startsWith(ModrinthQuery.BASE + "/search?"));
        assertEquals("sodium", queryParam(url, "query"));
        assertEquals("20", queryParam(url, "limit"));
        assertEquals("40", queryParam(url, "offset"));
        assertEquals("downloads", queryParam(url, "index"));
        assertEquals("[[\"project_type:mod\"],[\"versions:1.20.1\"],"
                        + "[\"categories:fabric\"],[\"categories:optimization\"],"
                        + "[\"client_side:required\"]]",
                queryParam(url, "facets"));
    }

    @Test
    public void versionsEncodeJsonArrays() throws Exception {
        String url = ModrinthQuery.versions("sodium", "1.20.1", "fabric");
        assertEquals("[\"1.20.1\"]", queryParam(url, "game_versions"));
        assertEquals("[\"fabric\"]", queryParam(url, "loaders"));
    }

    @Test
    public void emptyFiltersOnlyKeepProjectType() throws Exception {
        String url = ModrinthQuery.search("", "shader", null, null, null, null,
                "relevance", 10, 0);
        assertEquals("[[\"project_type:shader\"]]", queryParam(url, "facets"));
    }

    @Test
    public void loaderAndGameVersionAreDerivedFromVersionId() {
        assertEquals("fabric",
                ModrinthQuery.loaderForVersionId("fabric-loader-0.15.11-1.20.1"));
        assertEquals("quilt",
                ModrinthQuery.loaderForVersionId("quilt-loader-0.23.1-1.20.1"));
        assertEquals("forge",
                ModrinthQuery.loaderForVersionId("1.20.1-forge-47.2.0"));
        assertEquals("neoforge",
                ModrinthQuery.loaderForVersionId("1.20.1-neoforge-47.1.0"));
        assertEquals("1.20.1",
                ModrinthQuery.gameVersionForVersionId("fabric-loader-0.15.11-1.20.1"));
        assertNull(ModrinthQuery.gameVersionForVersionId("latest_release"));
    }

    private static String queryParam(String url, String key) throws UnsupportedEncodingException {
        int queryStart = url.indexOf('?');
        String query = url.substring(queryStart + 1);
        for (String pair : query.split("&")) {
            int equals = pair.indexOf('=');
            if (equals > 0 && pair.substring(0, equals).equals(key)) {
                return URLDecoder.decode(pair.substring(equals + 1), "UTF-8");
            }
        }
        return null;
    }
}
