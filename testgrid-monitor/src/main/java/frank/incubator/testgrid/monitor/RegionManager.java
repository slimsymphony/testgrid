/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package frank.incubator.testgrid.monitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author larryang
 */
public class RegionManager {
    
    private static List<String> mqRegions = new ArrayList();
    private static Map<String, MonitorCache> regionCacheMap = new HashMap();
    private static Map<String, AdminControler> regionControlerMap = new HashMap();
    private static String currentRegion;
    
    public static List<String> getMQRegions(){
        return mqRegions;
    }
    
    public static MonitorCache addMQ(String uri){
        if (StringUtils.isEmpty(currentRegion)){
            currentRegion = uri;
        }

        if (!mqRegions.contains(uri)){
            mqRegions.add(uri);
        }
        
        if (!regionCacheMap.containsKey(uri)){
            MonitorCache monitorCache = new MonitorCache();
            monitorCache.startUpdater();
            regionCacheMap.put(uri, monitorCache);
            return monitorCache;
        }else{
            return regionCacheMap.get(uri);
        }
    }
    
    public static MonitorCache getMQCache(String uri){
        if (regionCacheMap.containsKey(uri)){
            return regionCacheMap.get(uri);
        }else{
            return null;
        }
    }
    
    public static void addControler(String mqUri, AdminControler adminControler){
        regionControlerMap.put(mqUri, adminControler);
    }
    
    public static AdminControler getControler(String mqUri){
        if (regionControlerMap.containsKey(mqUri)){
            return regionControlerMap.get(mqUri);
        }else{
            return null;
        }
    }

    public static String getCurrentRegion() {
        return currentRegion;
    }

    public static void setCurrentRegion(String currentRegion) {
        RegionManager.currentRegion = currentRegion;
    }
    
    public static void triggerLoopbackHeartbeat(){
        
        for (Map.Entry<String, AdminControler> entry : regionControlerMap.entrySet()){
            AdminControler adminControler = entry.getValue();
            adminControler.sendLoopbackHeartbeat();
        }
        
    }
 
}
