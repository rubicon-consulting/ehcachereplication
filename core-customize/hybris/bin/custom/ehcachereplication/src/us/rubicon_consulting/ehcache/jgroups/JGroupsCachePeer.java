package us.rubicon_consulting.ehcache.jgroups;

import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import net.sf.ehcache.Element;
import net.sf.ehcache.distribution.CachePeer;
import net.sf.ehcache.distribution.jgroups.JGroupEventMessage;
import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.View;
import org.jgroups.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JGroupsCachePeer implements CachePeer {
    private static final Logger LOG = LoggerFactory.getLogger(JGroupsCachePeer.class.getName());
    private static final int CHUNK_SIZE = 100;
    private final JChannel channel;
    private final ConcurrentMap<Long, Queue<JGroupEventMessage>> asyncReplicationQueues = new ConcurrentHashMap();
    private final Timer timer;
    private volatile boolean alive;

    public JGroupsCachePeer(JChannel channel, String clusterName) {
        this.channel = channel;
        this.alive = true;
        this.timer = new Timer(clusterName + " Async Replication Thread", true);
    }

    public void send(List eventMessages) throws RemoteException {
        this.send((Address)null, eventMessages);
    }

    public List<Address> getGroupMembership() {
        View view = this.channel.getView();
        return view.getMembers();
    }

    public List<Address> getOtherGroupMembers() {
        Address localAddress = this.getLocalAddress();
        List<Address> members = this.getGroupMembership();
        List<Address> addresses = new ArrayList(members.size() - 1);
        Iterator i$ = members.iterator();

        while(i$.hasNext()) {
            Address member = (Address)i$.next();
            if (!member.equals(localAddress)) {
                addresses.add(member);
            }
        }

        return addresses;
    }

    public Address getLocalAddress() {
        return this.channel.getAddress();
    }

    public void dispose() {
        this.alive = false;
        this.disposeTimer();
        this.flushAllQueues();
        this.asyncReplicationQueues.clear();
    }

    private void disposeTimer() {
        if (this.timer != null) {
            this.timer.cancel();
            this.timer.purge();
        }

    }

    public void send(Address dest, List<JGroupEventMessage> eventMessages) {
        if (this.alive && eventMessages != null && !eventMessages.isEmpty()) {
            LinkedList<JGroupEventMessage> synchronousEventMessages = new LinkedList();
            Iterator i$ = eventMessages.iterator();

            while(i$.hasNext()) {
                JGroupEventMessage groupEventMessage = (JGroupEventMessage)i$.next();
                if (groupEventMessage.isAsync()) {
                    long asyncTime = groupEventMessage.getAsyncTime();
                    Queue<JGroupEventMessage> queue = this.getMessageQueue(asyncTime);
                    queue.offer(groupEventMessage);
                    LOG.trace("Queued {} for asynchronous sending.", groupEventMessage);
                } else {
                    synchronousEventMessages.add(groupEventMessage);
                    LOG.trace("Sending {} synchronously.", groupEventMessage);
                }
            }

            if (synchronousEventMessages.size() != 0) {
                LOG.debug("Sending {} JGroupEventMessages synchronously.", synchronousEventMessages.size());
                this.sendData(dest, synchronousEventMessages);
            }
        } else {
            LOG.warn("Ignoring send request of {} messages. Replicator alive = {}", eventMessages == null ? null : eventMessages.size(), this.alive);
        }
    }

    private Queue<JGroupEventMessage> getMessageQueue(long asyncTime) {
        Queue<JGroupEventMessage> queue = (Queue)this.asyncReplicationQueues.get(asyncTime);
        if (queue == null) {
            Queue<JGroupEventMessage> newQueue = new ConcurrentLinkedQueue();
            queue = (Queue)this.asyncReplicationQueues.putIfAbsent(asyncTime, newQueue);
            if (queue == null) {
                LOG.debug("Created asynchronous message queue for {}ms period", asyncTime);
                JGroupsCachePeer.AsyncTimerTask task = new JGroupsCachePeer.AsyncTimerTask(newQueue);
                this.timer.schedule(task, asyncTime, asyncTime);
                return newQueue;
            }
        }

        return queue;
    }

    private void sendData(Address dest, List<? extends Serializable> dataList) {
        Serializable toSend;
        if (dataList.size() == 1) {
            toSend = (Serializable)dataList.get(0);
        } else {
            toSend = (Serializable)dataList;
        }

        byte[] data;
        try {
            data = Util.objectToByteBuffer(toSend);
        } catch (Exception var9) {
            LOG.error("Error serializing data, it will not be sent: " + toSend, var9);
            return;
        }

        Message msg = new Message(dest, data);

        try {
            this.channel.send(msg);
        } catch (IllegalStateException var7) {
            LOG.error("Failed to send message(s) due to the channel being disconnected or closed: " + toSend, var7);
        } catch (Exception var8) {
            LOG.error("Failed to send message(s) : " + toSend, var8);
        }

    }

    private void flushAllQueues() {
        Iterator i$ = this.asyncReplicationQueues.values().iterator();

        while(i$.hasNext()) {
            Queue<JGroupEventMessage> queue = (Queue)i$.next();
            this.flushQueue(queue);
        }

    }

    private void flushQueue(Queue<JGroupEventMessage> queue) {
        ArrayList events = new ArrayList(100);

        while(!queue.isEmpty()) {
            events.clear();

            while(!queue.isEmpty() && events.size() < 100) {
                JGroupEventMessage event = (JGroupEventMessage)queue.poll();
                if (event == null) {
                    break;
                }

                if (event.isValid()) {
                    events.add(event);
                } else {
                    LOG.warn("Collected soft reference during asynchronous queue flush, this event will not be replicated: " + event);
                }
            }

            LOG.debug("Sending {} JGroupEventMessages from the asynchronous queue.", events.size());
            this.sendData((Address)null, events);
        }

    }

    public List<?> getElements(List keys) throws RemoteException {
        return null;
    }

    public String getGuid() throws RemoteException {
        return null;
    }

    public List<?> getKeys() throws RemoteException {
        return null;
    }

    public String getName() throws RemoteException {
        return null;
    }

    public Element getQuiet(Serializable key) throws RemoteException {
        return null;
    }

    public String getUrl() throws RemoteException {
        return null;
    }

    public String getUrlBase() throws RemoteException {
        return null;
    }

    public void put(Element element) throws IllegalArgumentException, IllegalStateException, RemoteException {
    }

    public boolean remove(Serializable key) throws IllegalStateException, RemoteException {
        return false;
    }

    public void removeAll() throws RemoteException, IllegalStateException {
    }

    private final class AsyncTimerTask extends TimerTask {
        private final Queue<JGroupEventMessage> queue;

        private AsyncTimerTask(Queue<JGroupEventMessage> newQueue) {
            this.queue = newQueue;
        }

        public void run() {
            if (!JGroupsCachePeer.this.alive) {
                this.cancel();
            } else {
                JGroupsCachePeer.this.flushQueue(this.queue);
                if (!JGroupsCachePeer.this.alive) {
                    this.cancel();
                }

            }
        }
    }
}
