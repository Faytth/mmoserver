package org.unallied.mmoserver.server;

import org.unallied.mmoserver.constants.ServerConstants;
import org.unallied.mmoserver.monsters.MonsterSpawner;

/**
 * Constantly updates the server.  This is used for server logic, such as
 * restoring HP of blocks, moving monsters, and so on.
 * @author Alexandria
 *
 */
public class ServerUpdater implements Runnable {

    /** The number of milliseconds per update tick. */
    private static final long UPDATE_TICK = 10;
    
    /** Keeps track of the elapsed time in milliseconds since the last global character save. */
    private static long characterSaveElapsedTime = 0;
    
    @Override
    public void run() {
        long updateTime = System.currentTimeMillis();
        while (Server.getInstance().isOnline()) {
            long curTime = System.currentTimeMillis();
            long delta = curTime - updateTime;
            delta = delta < 0 ? 0 : delta; // This should NEVER happen.
            
            // Perform updates
            try {
                World.getInstance().update(delta);
            } catch (Throwable t) {
                t.printStackTrace();
            }
            try {
                MonsterSpawner.getInstance().update(delta);
            } catch (Throwable t) {
                t.printStackTrace();
            }
            
            // Save all players every minute
            characterSaveElapsedTime += delta;
            if (characterSaveElapsedTime > ServerConstants.SAVE_ALL_CHARACTERS_FREQUENCY) {
                Server.getInstance().saveCharacters();
                characterSaveElapsedTime = 0;
            }
            
            updateTime = curTime;
            // Sleep for the remainder of the update tick
            if (delta < UPDATE_TICK) {
                try {
                    Thread.sleep(UPDATE_TICK - delta);
                } catch (InterruptedException e) {
                }
            }
        }
        System.out.println("Server is not online.  Server Updater has stopped.");
    }

}
