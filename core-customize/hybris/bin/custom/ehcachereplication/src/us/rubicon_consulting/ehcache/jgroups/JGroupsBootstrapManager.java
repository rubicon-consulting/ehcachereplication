package us.rubicon_consulting.ehcache.jgroups;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;
import java.util.concurrent.atomic.AtomicBoolean;
import net.sf.ehcache.distribution.jgroups.BootstrapRequest;
import net.sf.ehcache.distribution.jgroups.JGroupEventMessage;
import net.sf.ehcache.distribution.jgroups.ThreadNamingRunnable;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.util.NamedThreadFactory;
import org.jgroups.Address;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JGroupsBootstrapManager {
    private static final Logger LOG = LoggerFactory.getLogger(JGroupsBootstrapManager.class);
    private static final int BOOTSTRAP_CORE_THREADS = 0;
    private static final int BOOTSTRAP_MAX_THREADS = 50;
    private static final int BOOTSTRAP_THREAD_TIMEOUT = 60;
    private static final long BOOTSTRAP_REQUEST_CLEANUP_INTERVAL = 60000L;
    private static final Random BOOTSTRAP_PEER_CHOOSER = new Random();
    private static final long BOOTSTRAP_RESPONSE_TIMEOUT = 30000L;
    private static final long BOOTSTRAP_RESPONSE_TRIES = 10L;
    private static final long BOOTSTRAP_RESPONSE_MAX_TIMEOUT = 300000L;
    private static final int BOOTSTRAP_CHUNK_SIZE = 100;
    private volatile boolean alive = true;
    private final AtomicBoolean referenceTimerScheduled = new AtomicBoolean(false);
    private final BootstrapRequestMap bootstrapRequests = new BootstrapRequestMap();
    private Timer bootstrapRequestCleanupTimer;
    private final ThreadPoolExecutor bootstrapThreadPool;
    private final String clusterName;
    private final JGroupsCachePeer cachePeer;
    private final CacheManager cacheManager;

    public JGroupsBootstrapManager(String clusterName, JGroupsCachePeer cachePeer, CacheManager cacheManager) {
        this.clusterName = clusterName;
        this.cachePeer = cachePeer;
        this.cacheManager = cacheManager;
        this.bootstrapThreadPool = new ThreadPoolExecutor(0, 50, 60L, TimeUnit.SECONDS, new SynchronousQueue(true), new NamedThreadFactory(clusterName + " Bootstrap"), new CallerRunsPolicy());
    }

    public boolean waitForCompleteBootstrap(long duration) {
        return this.bootstrapRequests.waitForMapSize(0, duration);
    }

    public void dispose() {
        this.alive = false;
        if (!this.bootstrapRequests.isEmpty()) {
            LOG.debug("Waiting for BootstrapRequests to complete");
            this.bootstrapRequests.waitForMapSize(0, 30000L);
            if (!this.bootstrapRequests.isEmpty()) {
                LOG.warn("Shutting down bootstrap manager while there are still {} bootstrap requests pending", this.bootstrapRequests.size());
            }
        }

        this.bootstrapThreadPool.shutdown();

        try {
            if (!this.bootstrapThreadPool.awaitTermination(30000L, TimeUnit.MILLISECONDS)) {
                LOG.warn("Not all bootstrap threads shutdown within {}ms window", 30000L);
            }
        } catch (InterruptedException var2) {
            LOG.warn("Interrupted while waiting for bootstrap threads to complete", var2);
        }

        if (this.bootstrapRequestCleanupTimer != null) {
            this.bootstrapRequestCleanupTimer.cancel();
            this.bootstrapRequestCleanupTimer.purge();
        }

    }

    public void setBootstrapThreads(int bootstrapThreads) {
        this.bootstrapThreadPool.setMaximumPoolSize(bootstrapThreads);
    }

    public boolean isPendingBootstrapRequests() {
        return !this.bootstrapRequests.isEmpty();
    }

    public void handleBootstrapRequest(BootstrapRequest bootstrapRequest) {
        if (!this.alive) {
            LOG.warn("dispose has been called, no new BootstrapRequests will be handled, ignoring: {}", bootstrapRequest);
        } else {
            if (!this.referenceTimerScheduled.getAndSet(true)) {
                this.bootstrapRequestCleanupTimer = new Timer(this.clusterName + " Bootstrap Request Cleanup Thread", true);
                this.bootstrapRequestCleanupTimer.schedule(new JGroupsBootstrapManager.BootstrapRequestCleanerTimerTask(), 60000L, 60000L);
                LOG.debug("Scheduled BootstrapRequest Reference cleanup timer with {}ms period", 60000L);
            }

            Ehcache cache = bootstrapRequest.getCache();
            String cacheName = cache.getName();
            BootstrapRequest oldRequest = this.bootstrapRequests.put(cacheName, bootstrapRequest);
            if (oldRequest != null) {
                LOG.warn("There is already a BootstrapRequest registered for {} with value {}, it has been replaced with the current request.", cacheName, oldRequest);
            }

            LOG.debug("Registered {}", bootstrapRequest);
            JGroupsBootstrapManager.BootstrapRequestRunnable bootstrapRequestRunnable = new JGroupsBootstrapManager.BootstrapRequestRunnable(bootstrapRequest);
            Future<?> future = this.bootstrapThreadPool.submit(bootstrapRequestRunnable);
            if (!bootstrapRequest.isAsynchronous()) {
                LOG.debug("Waiting up to {}ms for BootstrapRequest of {} to complete", 300000L, cacheName);

                try {
                    future.get(300000L, TimeUnit.MILLISECONDS);
                } catch (InterruptedException var8) {
                    LOG.warn("Interrupted while waiting for bootstrap of " + cacheName + " to complete", var8);
                } catch (ExecutionException var9) {
                    LOG.warn("Exception thrown while bootstrapping " + cacheName, var9);
                } catch (TimeoutException var10) {
                    LOG.warn("Timed out waiting 300000ms for bootstrap of " + cacheName + " to complete", var10);
                }
            }

        }
    }

    public void sendBootstrapResponse(JGroupEventMessage message) {
        if (!this.alive) {
            LOG.warn("dispose has been called, no new BootstrapResponses will be handled");
        } else {
            JGroupsBootstrapManager.BootstrapResponseRunnable bootstrapResponseRunnable = new JGroupsBootstrapManager.BootstrapResponseRunnable(message);
            this.bootstrapThreadPool.submit(bootstrapResponseRunnable);
        }
    }

    public void handleBootstrapComplete(JGroupEventMessage message) {
        String cacheName = message.getCacheName();
        BootstrapRequest bootstrapRequestStatus = this.bootstrapRequests.get(cacheName);
        if (bootstrapRequestStatus != null) {
            bootstrapRequestStatus.boostrapComplete(BootstrapRequest.BootstrapStatus.COMPLETE);
        } else {
            LOG.warn("No BootstrapRequest registered for cache {}, the event will have no effect: {}", cacheName, message);
        }

    }

    public void handleBootstrapIncomplete(JGroupEventMessage message) {
        String cacheName = message.getCacheName();
        BootstrapRequest bootstrapRequestStatus = this.bootstrapRequests.get(cacheName);
        if (bootstrapRequestStatus != null) {
            bootstrapRequestStatus.boostrapComplete(BootstrapRequest.BootstrapStatus.INCOMPLETE);
        } else {
            LOG.warn("No BootstrapRequest registered for cache {}, the event will have no effect: {}", cacheName, message);
        }

    }

    public void handleBootstrapResponse(JGroupEventMessage message) {
        String cacheName = message.getCacheName();
        BootstrapRequest bootstrapRequestStatus = this.bootstrapRequests.get(cacheName);
        if (bootstrapRequestStatus != null) {
            Ehcache cache = bootstrapRequestStatus.getCache();
            cache.put(message.getElement(), true);
            bootstrapRequestStatus.countReplication();
        } else {
            LOG.warn("No BootstrapRequest registered for cache {}, the event will have no effect: {}", cacheName, message);
        }

    }

    private final class BootstrapResponseRunnable extends ThreadNamingRunnable {
        private final JGroupEventMessage message;

        public BootstrapResponseRunnable(JGroupEventMessage message) {
            super(" - Response for " + message.getCacheName());
            this.message = message;
        }

        public void runInternal() {
            Address requestAddress = (Address)this.message.getSerializableKey();
            String cacheName = this.message.getCacheName();
            Ehcache cache = cacheManager.getEhcache(cacheName);
            JGroupEventMessage bootstrapCompleteMessagex;
            if (cache == null) {
                LOG.warn("ignoring bootstrap request:   from {} for cache {} which does not exist on this memeber", requestAddress, cacheName);
                bootstrapCompleteMessagex = new JGroupEventMessage(13, (Serializable)null, (Element)null, cacheName);
                cachePeer.send(requestAddress, Arrays.asList(bootstrapCompleteMessagex));
            } else {
                LOG.debug("servicing bootstrap request: from {} for cache={}", requestAddress, cacheName);
                if (bootstrapRequests.get(cacheName) != null) {
                    LOG.debug("This group member is currently bootstrapping {} from another node and cannot respond to a bootstrap request for this cache. Notifying requester of incomplete bootstrap", cacheName);
                    bootstrapCompleteMessagex = new JGroupEventMessage(13, (Serializable)null, (Element)null, cacheName);
                    cachePeer.send(requestAddress, Arrays.asList(bootstrapCompleteMessagex));
                }

                List<?> keys = cache.getKeys();
                if (keys != null && keys.size() != 0) {
                    List<JGroupEventMessage> messageList = new ArrayList(Math.min(keys.size(), 100));
                    Iterator i$ = keys.iterator();

                    while(i$.hasNext()) {
                        Object key = i$.next();
                        Element element = cache.getQuiet(key);
                        if (element != null && !element.isExpired()) {
                            JGroupEventMessage groupEventMessage = new JGroupEventMessage(11, (Serializable)key, element, cacheName);
                            messageList.add(groupEventMessage);
                            if (messageList.size() == 100) {
                                this.sendResponseChunk(cache, requestAddress, messageList);
                                messageList.clear();
                            }
                        }
                    }

                    if (messageList.size() > 0) {
                        this.sendResponseChunk(cache, requestAddress, messageList);
                    }
                } else {
                    LOG.debug("no keys to reply to {} to bootstrap cache {}", requestAddress, cacheName);
                }

                JGroupEventMessage bootstrapCompleteMessage = new JGroupEventMessage(12, (Serializable)null, (Element)null, cacheName);
                cachePeer.send(requestAddress, Arrays.asList(bootstrapCompleteMessage));
            }
        }

        private void sendResponseChunk(Ehcache cache, Address requestAddress, List<JGroupEventMessage> events) {
            LOG.debug("reply {} elements to {} to bootstrap cache {}", new Object[]{events.size(), requestAddress, cache.getName()});
            cachePeer.send(requestAddress, events);
        }

        public String toString() {
            return "BootstrapResponseRunnable [name=" + this.threadNameSuffix + ", message=" + this.message + "]";
        }
    }

    private final class BootstrapRequestRunnable extends ThreadNamingRunnable {
        private final BootstrapRequest bootstrapRequest;

        public BootstrapRequestRunnable(BootstrapRequest bootstrapRequest) {
            super(" - Request for " + bootstrapRequest.getCache().getName());
            this.bootstrapRequest = bootstrapRequest;
        }

        public void runInternal() {
            Ehcache cache = this.bootstrapRequest.getCache();
            String cacheName = cache.getName();
            boolean var12 = false;

            label113: {
                try {
                    var12 = true;
                    List addresses = cachePeer.getOtherGroupMembers();
                    if (addresses != null && addresses.size() != 0) {
                        Address localAddress = cachePeer.getLocalAddress();
                        LOG.debug("Loading cache {} with local address {} from peers: {}", new Object[]{cacheName, localAddress, addresses});
                        int replicationCount = 0;

                        do {
                            this.bootstrapRequest.reset();
                            int randomPeerNumber = BOOTSTRAP_PEER_CHOOSER.nextInt(addresses.size());
                            Address address = (Address)addresses.remove(randomPeerNumber);
                            //JGroupEventMessage event = new JGroupEventMessage(10, localAddress, (Element)null, cacheName);
                            JGroupEventMessage event = new JGroupEventMessage(10, UUID.randomUUID().toString(), (Element)null, cacheName);
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Requesting bootstrap of {} from {}", cacheName, address);
                            }

                            cachePeer.send(address, Arrays.asList(event));
                            this.waitForBootstrap(cacheName, address);
                            replicationCount = (int)((long)replicationCount + this.bootstrapRequest.getReplicationCount());
                        } while(this.bootstrapRequest.getBootstrapStatus() != BootstrapRequest.BootstrapStatus.COMPLETE && addresses.size() > 0);

                        if (BootstrapRequest.BootstrapStatus.COMPLETE == this.bootstrapRequest.getBootstrapStatus()) {
                            LOG.info("Bootstrap for cache {} is complete, loaded {} elements", cacheName, replicationCount);
                            var12 = false;
                        } else {
                            LOG.info("Bootstrap for cache {} ended with status {}, loaded {} elements", new Object[]{cacheName, this.bootstrapRequest.getBootstrapStatus(), replicationCount});
                            var12 = false;
                        }
                        break label113;
                    }

                    LOG.info("There are no other nodes in the cluster to bootstrap {} from", cacheName);
                    var12 = false;
                } finally {
                    if (var12) {
                        BootstrapRequest removedRequestxx = bootstrapRequests.remove(cacheName);
                        if (removedRequestxx == null) {
                            LOG.warn("No BootstrapRequest for {} to remove", cacheName);
                            return;
                        }

                        LOG.debug("Removed {}", removedRequestxx);
                    }
                }

                BootstrapRequest removedRequestx = bootstrapRequests.remove(cacheName);
                if (removedRequestx == null) {
                    LOG.warn("No BootstrapRequest for {} to remove", cacheName);
                    return;
                }

                LOG.debug("Removed {}", removedRequestx);
                return;
            }

            BootstrapRequest removedRequest = bootstrapRequests.remove(cacheName);
            if (removedRequest == null) {
                LOG.warn("No BootstrapRequest for {} to remove", cacheName);
            } else {
                LOG.debug("Removed {}", removedRequest);
            }
        }

        protected void waitForBootstrap(String cacheName, Address address) {
            for(int waitTry = 1; (long)waitTry <= 10L; ++waitTry) {
                try {
                    if (this.bootstrapRequest.waitForBoostrap(30000L, TimeUnit.MILLISECONDS)) {
                        return;
                    }

                    LOG.debug("Bootstrap of {} did not complete in {}ms, will wait {} more times.", new Object[]{cacheName, 30000L * (long)waitTry, 10L - (long)waitTry});
                } catch (InterruptedException var5) {
                    LOG.warn("Interrupted while waiting for bootstrap of " + cacheName + " to complete", var5);
                }
            }

            LOG.warn("Bootstrap of {} did not complete in {}ms, giving up on bootstrap request to {}.", new Object[]{cacheName, 300000L, address});
        }

        public String toString() {
            return "BootstrapRequestRunnable [name=" + this.threadNameSuffix + ", message=" + this.bootstrapRequest + "]";
        }
    }

    private final class BootstrapRequestCleanerTimerTask extends TimerTask {
        private BootstrapRequestCleanerTimerTask() {
        }

        public void run() {
            bootstrapRequests.cleanBootstrapRequests();
        }
    }
}
