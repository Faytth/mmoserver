package org.unallied.mmoserver.monsters;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;

import org.newdawn.slick.util.ResourceLoader;
import org.unallied.mmocraft.constants.ClientConstants;
import org.unallied.mmocraft.monsters.MonsterData;
import org.unallied.mmocraft.monsters.MonsterManager;
import org.unallied.mmocraft.tools.IOUtils;
import org.unallied.mmocraft.tools.input.ByteArrayByteStream;
import org.unallied.mmocraft.tools.input.GenericSeekableLittleEndianAccessor;

public class ServerMonsterManager extends MonsterManager {
    private ServerMonsterManager() {
        init();
    }
    
    private static class ServerMonsterManagerHolder {
        private static final ServerMonsterManager instance = new ServerMonsterManager();
    }
    
    private void init() {
    }
    
    public static ServerMonsterManager getInstance() {
        return ServerMonsterManagerHolder.instance;
    }
    
    @Override
    public MonsterData getMonsterData(Integer monsterId) {
        if (monsterId == null) {
            return null;
        }
        MonsterData result = monsters.get(monsterId);
        
        return result;
    }
    
    @Override
    public void load(String filename) {
        InputStream is = null;
        try {
            is = ResourceLoader.getResourceAsStream(filename);
            byte[] bytes = IOUtils.toByteArray(is);
            ByteArrayByteStream babs = new ByteArrayByteStream(bytes);
            GenericSeekableLittleEndianAccessor slea = new GenericSeekableLittleEndianAccessor(babs);
            if (slea.readInt() != ClientConstants.MAGIC_MONSTER_PACK_NUMBER) {
                System.err.println("Unable to load Unallied Monster Pack file.  Bad magic number: " + filename);
                return;
            }
            MonsterData monsterData;
            while ((monsterData = ServerMonsterData.fromBytes(slea)) != null) {
                add(monsterData);
            }
            System.out.println("Successfully loaded Unallied Monster Pack from: " + filename);
        } catch (Throwable t) {
            System.out.println("Failed to load Unallied Monster Pack.");
            t.printStackTrace();
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Collection<ServerMonsterData> getAllServerMonsterData() {
        // I know what I'm doing.  This can ONLY contain ServerMonsterData.
        return ((Map)monsters).values();
    }
}
