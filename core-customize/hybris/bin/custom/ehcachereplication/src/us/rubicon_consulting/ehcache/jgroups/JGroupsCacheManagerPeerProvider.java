package us.rubicon_consulting.ehcache.jgroups;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import javax.management.MBeanServer;
import net.sf.ehcache.CacheException;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Status;
import net.sf.ehcache.distribution.CacheManagerPeerProvider;
import net.sf.ehcache.distribution.CachePeer;
import net.sf.ehcache.management.ManagedCacheManagerPeerProvider;
import org.jgroups.JChannel;
import org.jgroups.jmx.JmxConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JGroupsCacheManagerPeerProvider implements ManagedCacheManagerPeerProvider {
    public static final String SCHEME_NAME = "JGroups";
    private static final String JMX_DOMAIN_NAME = "JGroupsReplication";
    private static final Logger LOG = LoggerFactory.getLogger(JGroupsCacheManagerPeerProvider.class);
    private final CacheManager cacheManager;
    private final String groupProperties;
    private final URL groupUrl;
    private String channelName;
    private JChannel channel;
    private JGroupsCachePeer cachePeer;
    private JGroupsCacheReceiver cacheReceiver;
    private List<CachePeer> cachePeersListCache;
    private JGroupsBootstrapManager bootstrapManager;
    private MBeanServer mBeanServer;

    public JGroupsCacheManagerPeerProvider(CacheManager cacheManager, String properties) {
        this.cacheManager = cacheManager;
        this.groupProperties = properties;
        this.groupUrl = null;
    }

    public JGroupsCacheManagerPeerProvider(CacheManager cacheManager, URL configUrl) {
        this.cacheManager = cacheManager;
        this.groupProperties = null;
        this.groupUrl = configUrl;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public static JGroupsCacheManagerPeerProvider getCachePeerProvider(Ehcache cache) {
        CacheManager cacheManager = cache.getCacheManager();
        return getCachePeerProvider(cacheManager);
    }

    public static JGroupsCacheManagerPeerProvider getCachePeerProvider(CacheManager cacheManager) {
        CacheManagerPeerProvider provider = cacheManager.getCacheManagerPeerProvider("JGroups");
        if (provider == null) {
            LOG.warn("No CacheManagerPeerProvider registered for {} scheme.", "JGroups");
            return null;
        } else if (!(provider instanceof net.sf.ehcache.distribution.jgroups.JGroupsCacheManagerPeerProvider)) {
            LOG.warn("{} for scheme {} cannot be cast to {}.", new Object[]{provider.getClass(), "JGroups", JGroupsCacheManagerPeerProvider.class});
            return null;
        } else {
            return (JGroupsCacheManagerPeerProvider)provider;
        }
    }

    public void init() {
        try {
            if (this.groupProperties != null) {
                this.channel = new JChannel(this.groupProperties);
            } else if (this.groupUrl != null) {
                this.channel = new JChannel(this.groupUrl);
            } else {
                this.channel = new JChannel();
            }
        } catch (Exception var4) {
            LOG.error("Failed to create JGroups Channel, replication will not function. JGroups properties:\n" + this.groupProperties, var4);
            this.dispose();
            return;
        }

        String clusterName = this.getClusterName();
        this.cachePeer = new JGroupsCachePeer(this.channel, clusterName);
        this.bootstrapManager = new JGroupsBootstrapManager(clusterName, this.cachePeer, this.cacheManager);
        this.cacheReceiver = new JGroupsCacheReceiver(this.cacheManager, this.bootstrapManager);
        this.channel.setReceiver(this.cacheReceiver);
        this.channel.setDiscardOwnMessages(true);

        try {
            this.channel.connect(clusterName);
        } catch (Exception var3) {
            LOG.error("Failed to connect to JGroups cluster '" + clusterName + "', replication will not function. JGroups properties:\n" + this.groupProperties, var3);
            this.dispose();
            return;
        }

        this.cachePeersListCache = Collections.singletonList(this.cachePeer);
        LOG.info("JGroups Replication started for '" + clusterName + "'. JChannel: {}", this.channel.toString(true));
    }

    public void register(MBeanServer mBeanServer) {
        this.mBeanServer = mBeanServer;

        try {
            String clusterName = this.getClusterName();
            JmxConfigurator.registerChannel(this.channel, mBeanServer, "JGroupsReplication", clusterName, true);
            LOG.debug("Registered JGroups channel with MBeanServer under domain {} with name {}", "JGroupsReplication", clusterName);
        } catch (Exception var3) {
            LOG.error("Error occured while registering MBeans. Management of JGroups will not be enabled.", var3);
        }

    }

    public void dispose() throws CacheException {
        if (this.bootstrapManager != null) {
            this.bootstrapManager.dispose();
            this.bootstrapManager = null;
        }

        this.shutdownCachePeer();
        this.shutdownChannel();
    }

    private void shutdownCachePeer() {
        if (this.cachePeer != null) {
            this.cachePeersListCache = null;
            this.cacheReceiver = null;
            this.cachePeer.dispose();
            this.cachePeer = null;
        }

    }

    private void shutdownChannel() {
        if (this.channel != null) {
            String clusterName = this.getClusterName();
            if (this.mBeanServer != null) {
                try {
                    JmxConfigurator.unregisterChannel(this.channel, this.mBeanServer, "JGroupsReplication", clusterName);
                    LOG.debug("Unregistered JGroups channel with MBeanServer under domain {} with name {}", "JGroupsReplication", clusterName);
                } catch (Exception var4) {
                    LOG.error("Error unregistering JGroups channel with MBeanServer under domain JGroupsReplication with name " + clusterName, var4);
                }
            }

            if (this.channel.isConnected()) {
                try {
                    this.channel.close();
                    LOG.debug("Closing JChannel for cluster {}", clusterName);
                } catch (Exception var3) {
                    LOG.error("Error closing JChannel for cluster " + clusterName, var3);
                }
            }

            this.channel = null;
        }

    }

    public long getTimeForClusterToForm() {
        return 0L;
    }

    public String getScheme() {
        return "JGroups";
    }

    public List<CachePeer> listRemoteCachePeers(Ehcache cache) throws CacheException {
        return this.cachePeersListCache == null ? Collections.emptyList() : this.cachePeersListCache;
    }

    public void registerPeer(String rmiUrl) {
    }

    public void unregisterPeer(String rmiUrl) {
    }

    public JGroupsBootstrapManager getBootstrapManager() {
        return this.bootstrapManager;
    }

    public Status getStatus() {
        if (this.channel == null) {
            return Status.STATUS_UNINITIALISED;
        } else {
            return !this.channel.isConnected() ? Status.STATUS_SHUTDOWN : Status.STATUS_ALIVE;
        }
    }

    public String getClusterName() {
        if (this.channelName != null) {
            return this.channelName;
        } else {
            return this.cacheManager.isNamed() ? this.cacheManager.getName() : "EH_CACHE";
        }
    }
}
