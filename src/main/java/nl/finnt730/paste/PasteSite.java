package nl.finnt730.paste;

import java.util.ServiceLoader;
import java.util.HashMap;
import java.util.Map;

public interface PasteSite {
    public String getId();
    public boolean largeEnough(String content);
    public boolean supportsGZip();
    public String getResultURL(String content) throws java.io.IOException;
    
    static PasteSite get(String userId, String content) {
        return PasteSiteRegistry.getInstance().get(userId, content);
    }
    
    static PasteSite getPure(String id) {
        return PasteSiteRegistry.getInstance().getPure(id);
    }
    
    static boolean isValidSiteId(String id) {
        return PasteSiteRegistry.getInstance().isValidSiteId(id);
    }
    
    static String getAvailableSites() {
        return PasteSiteRegistry.getInstance().getAvailableSites();
    }
    
    class PasteSiteRegistry {
        private static final PasteSiteRegistry INSTANCE = new PasteSiteRegistry();
        private final Map<String, PasteSite> sitesById = new HashMap<>();
        private final PasteSite defaultSite;
        private final PasteSite fallbackSite;
        
        private PasteSiteRegistry() {
            ServiceLoader<PasteSite> loader = ServiceLoader.load(PasteSite.class);
            for (PasteSite site : loader) {
                sitesById.put(site.getId(), site);
            }
            
            this.defaultSite = sitesById.getOrDefault("mclogs", 
                sitesById.values().stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("No paste sites available!")));
            
            this.fallbackSite = sitesById.getOrDefault("cdpaste", defaultSite);
        }
        
        static PasteSiteRegistry getInstance() {
            return INSTANCE;
        }
        
        PasteSite get(String userId, String content) {
            String preferredId = nl.finnt730.UserDB.pasteSite(userId);
            PasteSite preferredSite = sitesById.getOrDefault(preferredId, defaultSite);
            
            if (preferredSite.largeEnough(content)) {
                return preferredSite;
            }
            
            return fallbackSite;
        }
        
        PasteSite getPure(String id) {
            return sitesById.getOrDefault(id, defaultSite);
        }
        
        boolean isValidSiteId(String id) {
            return sitesById.containsKey(id);
        }
        
        String getAvailableSites() {
            return sitesById.values().stream()
                .map(site -> "`" + site.getId() + "`")
                .reduce((a, b) -> a + ", " + b)
                .orElse("No paste sites available");
        }
    }
}