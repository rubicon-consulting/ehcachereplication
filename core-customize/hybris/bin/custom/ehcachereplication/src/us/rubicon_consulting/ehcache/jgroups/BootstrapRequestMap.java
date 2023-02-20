package us.rubicon_consulting.ehcache.jgroups;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.sf.ehcache.distribution.jgroups.BootstrapRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class BootstrapRequestMap {
    private static final Logger LOG = LoggerFactory.getLogger(BootstrapRequestMap.class.getName());
    private final ConcurrentMap<String, Reference<BootstrapRequest>> bootstrapRequests = new ConcurrentHashMap();
    private final Object requestChangeNotifier = new Object();

    BootstrapRequestMap() {
    }

    public boolean waitForMapSize(int size, long duration) {
        long waitTime = Math.min(duration, 1000L);
        long start = System.currentTimeMillis();
        this.cleanBootstrapRequests();

        for(; this.bootstrapRequests.size() != size && System.currentTimeMillis() - start < duration; this.cleanBootstrapRequests()) {
            try {
                synchronized(this.requestChangeNotifier) {
                    this.requestChangeNotifier.wait(waitTime);
                }
            } catch (InterruptedException var11) {
                LOG.warn("Interrupted while waiting for BootstrapRequestMap to empty", var11);
            }
        }

        return this.bootstrapRequests.size() == size;
    }

    public Set<String> keySet() {
        this.cleanBootstrapRequests();
        return Collections.unmodifiableSet(this.bootstrapRequests.keySet());
    }

    public boolean isEmpty() {
        this.cleanBootstrapRequests();
        return this.bootstrapRequests.isEmpty();
    }

    public int size() {
        this.cleanBootstrapRequests();
        return this.bootstrapRequests.size();
    }

    public BootstrapRequest put(String cacheName, BootstrapRequest bootstrapRequest) {
        Reference<BootstrapRequest> oldReference = (Reference)this.bootstrapRequests.put(cacheName, new WeakReference(bootstrapRequest));
        synchronized(this.requestChangeNotifier) {
            this.requestChangeNotifier.notifyAll();
        }

        return oldReference != null ? (BootstrapRequest)oldReference.get() : null;
    }

    public BootstrapRequest get(String cacheName) {
        Reference<BootstrapRequest> reference = (Reference)this.bootstrapRequests.get(cacheName);
        if (reference == null) {
            return null;
        } else {
            BootstrapRequest bootstrapRequest = (BootstrapRequest)reference.get();
            if (bootstrapRequest == null) {
                LOG.info("BootstrapRequest for {} has been GCed, removing from requests map.", cacheName);
                if (this.bootstrapRequests.remove(cacheName, reference)) {
                    synchronized(this.requestChangeNotifier) {
                        this.requestChangeNotifier.notifyAll();
                    }
                }

                return null;
            } else {
                return bootstrapRequest;
            }
        }
    }

    public BootstrapRequest remove(String cacheName) {
        Reference<BootstrapRequest> reference = (Reference)this.bootstrapRequests.remove(cacheName);
        if (reference == null) {
            return null;
        } else {
            synchronized(this.requestChangeNotifier) {
                this.requestChangeNotifier.notifyAll();
            }

            return (BootstrapRequest)reference.get();
        }
    }

    public void cleanBootstrapRequests() {
        Iterator bootstrapRequestItr = this.bootstrapRequests.entrySet().iterator();

        while(bootstrapRequestItr.hasNext()) {
            Entry<String, Reference<BootstrapRequest>> bootstrapRequestEntry = (Entry)bootstrapRequestItr.next();
            if (((Reference)bootstrapRequestEntry.getValue()).get() == null) {
                LOG.info("BootstrapRequest for {} has been GCed, removing from requests map.", bootstrapRequestEntry.getKey());
                bootstrapRequestItr.remove();
                synchronized(this.requestChangeNotifier) {
                    this.requestChangeNotifier.notifyAll();
                }
            }
        }

    }
}
