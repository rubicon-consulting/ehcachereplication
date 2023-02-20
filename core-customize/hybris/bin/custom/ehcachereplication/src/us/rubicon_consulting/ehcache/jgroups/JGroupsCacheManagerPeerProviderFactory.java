package us.rubicon_consulting.ehcache.jgroups;

import java.net.URL;
import java.util.Properties;

import de.hybris.platform.util.Config;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.distribution.CacheManagerPeerProvider;
import net.sf.ehcache.distribution.CacheManagerPeerProviderFactory;
import net.sf.ehcache.util.PropertyUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import us.rubicon_consulting.constants.EhcachereplicationConstants;

public class JGroupsCacheManagerPeerProviderFactory extends CacheManagerPeerProviderFactory {
    private static final Logger LOG = LoggerFactory.getLogger(JGroupsCacheManagerPeerProviderFactory.class);
    private static final String CHANNEL_NAME = "channelName";
    private static final String CONNECT = "connect";
    private static final String FILE = "file";

    public JGroupsCacheManagerPeerProviderFactory() {
    }

    public CacheManagerPeerProvider createCachePeerProvider(CacheManager cacheManager, Properties properties) {
        LOG.info("Creating JGroups CacheManagerPeerProvider for {} with properties:\n{}", cacheManager.getName(), properties);
        String connect = this.getProperty(CONNECT, properties);
        String file = this.getProperty(FILE, properties);
        String channelName = this.getProperty(CHANNEL_NAME, properties);

        System.setProperty("jgroups.udp.mcast_addr", Config.getString(EhcachereplicationConstants.EhcacheConfigConstants.JGROUPS_UDP_MCAST_ADDR, "224.0.0.2"));
        System.setProperty("jgroups.udp.mcast_port", Config.getString(EhcachereplicationConstants.EhcacheConfigConstants.JGROUPS_UDP_MCAST_PORT, "45588"));
        System.setProperty("jgroups.udp.ip_ttl", Config.getString(EhcachereplicationConstants.EhcacheConfigConstants.JGROUPS_UDP_IP_TTL, "1"));

        JGroupsCacheManagerPeerProvider peerProvider;
        if (file != null) {
            if (connect != null) {
                LOG.warn("Both 'connect' and 'file' properties set. 'connect' will be ignored");
            }

            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            URL configUrl = contextClassLoader.getResource(file);
            LOG.info("Creating JGroups CacheManagerPeerProvider for {} with configuration file: {}", cacheManager.getName(), configUrl);
            peerProvider = new us.rubicon_consulting.ehcache.jgroups.JGroupsCacheManagerPeerProvider(cacheManager, configUrl);
        } else {
            LOG.info("Creating JGroups CacheManagerPeerProvider for {} with configuration:\n{}", cacheManager.getName(), connect);
            peerProvider = new us.rubicon_consulting.ehcache.jgroups.JGroupsCacheManagerPeerProvider(cacheManager, connect);
        }

        peerProvider.setChannelName(channelName);
        return peerProvider;
    }

    private String getProperty(String name, Properties properties) {
        String property = PropertyUtil.extractAndLogProperty(name, properties);
        if (property != null) {
            property = property.trim();
            property = property.replaceAll(" ", "");
            if (property.equals("")) {
                property = null;
            }
        }

        return property;
    }
}
