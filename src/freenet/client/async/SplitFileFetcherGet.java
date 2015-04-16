package freenet.client.async;

import java.io.IOException;

import freenet.client.FetchContext;
import freenet.client.FetchException;
import freenet.keys.ClientKey;
import freenet.keys.Key;
import freenet.node.KeysFetchingLocally;
import freenet.node.LowLevelGetException;
import freenet.node.RequestClient;
import freenet.node.SendableGet;
import freenet.node.SendableRequestItem;
import freenet.node.SendableRequestItemKey;
import freenet.support.Logger;

/** Actually does the splitfile fetch. Only one fetcher object for an entire splitfile.
 * 
 * PERSISTENCE: Not persistent, recreated on startup by SplitFileFetcher. */
@SuppressWarnings("serial")
public class SplitFileFetcherGet extends SendableGet {
    
    private static volatile boolean logMINOR;
    static {
        Logger.registerClass(SplitFileFetcherGet.class);
    }

    final SplitFileFetcher parent;
    final SplitFileFetcherSegmentStorage storage;
    final ClientContext context;
    private boolean checkedStore;

    public SplitFileFetcherGet(SplitFileFetcher fetcher, SplitFileFetcherSegmentStorage storage, 
            ClientContext context) {
        super(fetcher.parent, fetcher.realTimeFlag);
        this.parent = fetcher;
        this.storage = storage;
        this.context = context;
    }
    
    final class MyKey implements SendableRequestItem, SendableRequestItemKey {

        public MyKey(int n, SplitFileFetcherSegmentStorage storage) {
            this.blockNumber = n;
            this.get = storage;
            hashCode = initialHashCode();
        }

        final int blockNumber;
        final SplitFileFetcherSegmentStorage get;
        final int hashCode;

        @Override
        public void dump() {
            // Ignore.
        }

        @Override
        public SendableRequestItemKey getKey() {
            return this;
        }
        
        @Override
        public boolean equals(Object o) {
            if(this == o) return true;
            if(!(o instanceof MyKey)) return false;
            MyKey k = (MyKey)o;
            return k.blockNumber == blockNumber && k.get == get;
        }
        
        public int hashCode() {
            return hashCode;
        }

        private int initialHashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + blockNumber;
            result = prime * result + ((get == null) ? 0 : get.hashCode());
            return result;
        }
        
        public String toString() {
            return "MyKey:"+get.segNo+":"+blockNumber;
        }

    }

    @Override
    public ClientKey getKey(SendableRequestItem token) {
        MyKey key = (MyKey) token;
        if(key.get != storage) throw new IllegalArgumentException();
        return storage.getKey(key.blockNumber);
    }

    @Override
    public Key[] listKeys() {
        try {
            return storage.listUnfetchedKeys();
        } catch (IOException e) {
            storage.parent.failOnDiskError(e);
            return new Key[0];
        }
    }

    @Override
    public FetchContext getContext() {
        return parent.blockFetchContext;
    }

    @Override
    public void onFailure(LowLevelGetException e, SendableRequestItem token,
            ClientContext context) {
        FetchException fe = translateException(e);
        if(fe.isDefinitelyFatal()) {
            // If the error is definitely-fatal it means there is either a serious local problem
            // or the inserted data was corrupt. So we fail the entire splitfile immediately.
            // We don't track which blocks have fatally failed.
            if(logMINOR) Logger.minor(this, "Fatal failure: "+fe+" for "+token);
            parent.fail(fe);
        } else {
            MyKey key = (MyKey) token;
            if(key.get != storage) throw new IllegalArgumentException();
            long wakeupTime = storage.parent.onFailure(key, fe);
            if (wakeupTime != Long.MAX_VALUE)
                reduceWakeupTime(wakeupTime, context);
        }
    }
    
    @Override
    public long getWakeupTime(ClientContext context, long now) {
        long ret = storage.getOverallCooldownTime();
        if(ret < now) return 0;
        return ret;
    }

    @Override
    public long getCooldownWakeup(SendableRequestItem token, ClientContext context) {
        MyKey key = (MyKey) token;
        return storage.getCooldownTime(key.blockNumber);
    }

    @Override
    public boolean preRegister(ClientContext context, boolean toNetwork) {
        if(!toNetwork) return false;
        if(!storage.setHasCheckedStore(context)) return false;
        // Notify clients of all the work we've done checking the datastore.
        if(parent.localRequestOnly()) {
            storage.parent.finishedCheckingDatastoreOnLocalRequest(context);
            return true;
        }
        parent.toNetwork();
        parent.parent.notifyClients(context);
        return false;
    }

    @Override
    public short getPriorityClass() {
        return parent.getPriorityClass();
    }

    @Override
    /** Choose a random key to fetch. Must not modify anything that is persisted. */
    public SendableRequestItem chooseKey(KeysFetchingLocally keys, ClientContext context) {
        int block = storage.chooseRandomKey();
        if(block == -1) {
            return null;
        }
        return new MyKey(block, storage);
    }

    @Override
    public long countAllKeys(ClientContext context) {
        return storage.countUnfetchedKeys();
    }

    @Override
    public long countSendableKeys(ClientContext context) {
        return storage.countSendableKeys(System.currentTimeMillis(), storage.parent.maxRetries());
    }

    @Override
    public boolean isCancelled() {
        // FIXME locking on this is a bit different to the old code ... is it safe?
        return parent.hasFinished();
    }

    @Override
    public RequestClient getClient() {
        return parent.parent.getClient();
    }

    @Override
    public ClientRequester getClientRequest() {
        return parent.parent;
    }

    @Override
    public boolean isSSK() {
        return false;
    }

    /**
     * Schedule the fetch.
     * @param context
     * @param ignoreStore If true, don't check the datastore before re-registering the requests to
     * run. Should be true when rescheduling after a normal cooldown, false after recovering from
     * data corruption (the blocks may still be in the store), false otherwise.
     * @throws KeyListenerConstructionException
     */
    public void schedule(ClientContext context, boolean ignoreStore) throws KeyListenerConstructionException {
        ClientRequestScheduler sched = context.getChkFetchScheduler(realTimeFlag);
        BlockSet blocks = parent.blockFetchContext.blocks;
        sched.register(null, new SendableGet[] { this }, persistent, blocks, ignoreStore);
    }

    public void cancel(ClientContext context) {
        unregister(context, parent.getPriorityClass());
    }

    /** Has preRegister() been called? */
    public boolean hasQueued() {
        return storage.hasCheckedStore();
    }

    @Override
    protected ClientGetState getClientGetState() {
        return parent;
    }

}
