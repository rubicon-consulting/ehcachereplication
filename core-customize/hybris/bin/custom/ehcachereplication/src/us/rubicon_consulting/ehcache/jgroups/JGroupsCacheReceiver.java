package us.rubicon_consulting.ehcache.jgroups;


import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Iterator;
import java.util.List;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.util.CacheTransactionHelper;
import net.sf.ehcache.distribution.jgroups.JGroupEventMessage;
import org.jgroups.Address;
import org.jgroups.Message;
import org.jgroups.Receiver;
import org.jgroups.View;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JGroupsCacheReceiver implements Receiver {
    private static final Logger LOG = LoggerFactory.getLogger(JGroupsCacheReceiver.class.getName());
    private final CacheManager cacheManager;
    private final JGroupsBootstrapManager bootstrapManager;

    public JGroupsCacheReceiver(CacheManager cacheManager, JGroupsBootstrapManager bootstrapManager) {
        this.cacheManager = cacheManager;
        this.bootstrapManager = bootstrapManager;
    }

    public void receive(Message msg) {
        if (msg != null && msg.getLength() != 0) {
            Object object = msg.getObject();
            if (object == null) {
                LOG.warn("Recieved a Message with a null object: {}", msg);
            } else {
                if (object instanceof JGroupEventMessage) {
                    this.safeHandleJGroupNotification((JGroupEventMessage)object);
                } else if (object instanceof List) {
                    List<?> messages = (List)object;
                    LOG.trace("Recieved List of {} JGroupEventMessages", messages.size());
                    Iterator i$ = messages.iterator();

                    while(i$.hasNext()) {
                        Object message = i$.next();
                        if (message != null) {
                            if (message instanceof JGroupEventMessage) {
                                this.safeHandleJGroupNotification((JGroupEventMessage)message);
                            } else {
                                LOG.warn("Recieved message of type " + List.class + " but member was of type '" + message.getClass() + "' and not " + JGroupEventMessage.class + ". Member ignored: " + message);
                            }
                        }
                    }
                } else {
                    LOG.warn("Recieved message with payload of type " + object.getClass() + " and not " + JGroupEventMessage.class + " or List<" + JGroupEventMessage.class.getSimpleName() + ">. Message: " + msg + " payload " + object);
                }

            }
        } else {
            LOG.warn("Recieved an empty or null Message: {}", msg);
        }
    }

    private void safeHandleJGroupNotification(JGroupEventMessage message) {
        String cacheName = message.getCacheName();
        Ehcache cache = this.cacheManager.getEhcache(cacheName);
        boolean started = cache != null && CacheTransactionHelper.isTransactionStarted(cache);
        if (cache != null && !started) {
            CacheTransactionHelper.beginTransactionIfNeeded(cache);
        }

        try {
            this.handleJGroupNotification(message);
        } catch (Exception var9) {
            LOG.error("Failed to handle message " + message, var9);
        } finally {
            if (cache != null && !started) {
                CacheTransactionHelper.commitTransactionIfNeeded(cache);
            }

        }

    }

    private void handleJGroupNotification(JGroupEventMessage message) {
        String cacheName = message.getCacheName();
        switch(message.getEvent()) {
            case 10:
                LOG.debug("received bootstrap request:    from {} for cache={}", message.getSerializableKey(), cacheName);
                this.bootstrapManager.sendBootstrapResponse(message);
                break;
            case 11:
                Serializable serializableKey = message.getSerializableKey();
                LOG.debug("received bootstrap reply:      cache={}, key={}", cacheName, serializableKey);
                this.bootstrapManager.handleBootstrapResponse(message);
                break;
            case 12:
                LOG.debug("received bootstrap complete:   cache={}", cacheName);
                this.bootstrapManager.handleBootstrapComplete(message);
                break;
            case 13:
                LOG.debug("received bootstrap incomplete: cache={}", cacheName);
                this.bootstrapManager.handleBootstrapIncomplete(message);
                break;
            default:
                this.handleEhcacheNotification(message, cacheName);
        }

    }

    private void handleEhcacheNotification(JGroupEventMessage message, String cacheName) {
        Ehcache cache = this.cacheManager.getEhcache(cacheName);
        if (cache == null) {
            LOG.warn("Received message {} for cache that does not exist: {}", message, cacheName);
        } else {
            Serializable serializableKey;
            switch(message.getEvent()) {
                case 0:
                    serializableKey = message.getSerializableKey();
                    LOG.debug("received put:             cache={}, key={}", cacheName, serializableKey);
                    cache.put(message.getElement(), true);
                    break;
                case 1:
                    serializableKey = message.getSerializableKey();
                    if (cache.getQuiet(serializableKey) != null) {
                        LOG.debug("received remove:          cache={}, key={}", cacheName, serializableKey);
                        cache.remove(serializableKey, true);
                    } else if (LOG.isTraceEnabled()) {
                        LOG.trace("received remove:          cache={}, key={} - Ignoring, key is not in the local cache.", cacheName, serializableKey);
                    }
                    break;
                case 2:
                default:
                    LOG.warn("Unknown JGroupsEventMessage type recieved, ignoring message: " + message);
                    break;
                case 3:
                    LOG.debug("received remove all:      cache={}", cacheName);
                    cache.removeAll(true);
            }

        }
    }

    public void getState(OutputStream output) {
    }

    public void setState(InputStream input) {
    }

    public void block() {
    }

    public void unblock() {
    }

    public void suspect(Address suspectedMbr) {
    }

    public void viewAccepted(View newView) {
    }
}
