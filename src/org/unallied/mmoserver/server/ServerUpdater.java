package org.unallied.mmoserver.server;

/**
 * Constantly updates the server.  This is used for server logic, such as
 * restoring HP of blocks, moving monsters, and so on.
 * @author Alexandria
 *
 */
public class ServerUpdater implements Runnable {

    /** The number of milliseconds per update tick. */
    private static final long UPDATE_TICK = 100;
    
    @Override
    public void run() {
        long updateTime = System.currentTimeMillis();
        while (Server.getInstance().isOnline()) {
            long curTime = System.currentTimeMillis();
            long delta = curTime - updateTime;
            delta = delta < 0 ? 0 : delta; // This should NEVER happen.
            
            // Perform updates
            World.getInstance().update(delta);
            
            updateTime = curTime;
            // Sleep for the remainder of the update tick
            if (delta < UPDATE_TICK) {
                try {
                    Thread.sleep(UPDATE_TICK - delta);
                } catch (InterruptedException e) {
                }
            }
        }
    }

}
