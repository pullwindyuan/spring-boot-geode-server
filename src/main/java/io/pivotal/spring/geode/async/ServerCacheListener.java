package io.pivotal.spring.geode.async;

import org.apache.geode.cache.*;
import org.apache.geode.cache.util.CacheListenerAdapter;
import org.apache.geode.pdx.PdxInstance;
import io.pivotal.spring.geode.async.lib.RouteProcessor;

import java.util.LinkedList;
import java.util.Properties;

public class ServerCacheListener<K,V> extends CacheListenerAdapter<K,V> implements Declarable {

    private GemFireCache gemFireCache;
    private Region regRouteCount;

    private Region<Integer, PdxInstance> regRouteTop;
    private Region regRouteTopTen;

    public void afterDestroy(EntryEvent<K,V> e) {
        try {
            if (e.getOperation().equals(Operation.EXPIRE_DESTROY)) {

                PdxInstance raw;
                Object oldValue = e.getOldValue();
                if (oldValue instanceof PdxInstance) {
                    raw = (PdxInstance)oldValue;
                } else {
                    throw new Exception("oldValue class:" + oldValue.getClass().getName());
                }


                Integer countDiff = -1;

                gemFireCache = CacheFactory.getAnyInstance();
                regRouteCount = gemFireCache.getRegion("RegRouteCount");
                regRouteTop = gemFireCache.getRegion("RegRouteTop");
                regRouteTopTen = gemFireCache.getRegion("RegRouteTopTen");

                RouteProcessor routeProcessor = new RouteProcessor(regRouteCount, regRouteTop, regRouteTopTen);

                // count & top process
                String route = (String)raw.getField("route");
                String pickupAddress = (String)raw.getField("pickupAddress");
                String dropoffAddress = (String)raw.getField("dropoffAddress");
                Long newTimestamp = (Long)raw.getField("timestamp");

                Integer originalCount = 0 ;
                Integer newCount = 0;
                Long originalTimestamp = 0L;

                PdxInstance originCountValue = (PdxInstance)regRouteCount.get(route);

                if(originCountValue==null){
                    newCount = 1;
                }
                else
                {
                    originalCount = ((Byte)originCountValue.getField("route_count")).intValue();
                    originalTimestamp = (Long)originCountValue.getField("timestamp");
                    newCount = originalCount + countDiff;
                }

                // top ten process
                Integer smallestToptenCount = 0;
                PdxInstance topTenValue = (PdxInstance)regRouteTopTen.get(1);
                if (topTenValue != null) {
                    LinkedList toptenList = (LinkedList)topTenValue.getField("toptenlist");
                    if (toptenList.size() != 0) {
                        smallestToptenCount = ((Byte)((PdxInstance)toptenList.getLast()).getField("count")).intValue();
                    }
                }

                Long keyTimestamp = 0L;
                String keyUuid = "";
                String keyRoute = "";
                String keyPickupAddress = "";
                String keyDropoffAddress = "";
                Integer keyCount = 0;
                Boolean incremental = false;



//                processor.processRegionCount(route, originalCount, originalTimestamp, newCount, newTimestamp);
                routeProcessor.processRouteCount(route, pickupAddress, dropoffAddress, originalCount, newCount, newTimestamp);

//                processor.processRegionTop(route, originalCount, originalTimestamp, newCount, newTimestamp);
                routeProcessor.processRouteTop(route, pickupAddress, dropoffAddress, originalCount, originalTimestamp, newCount, newTimestamp);

                if (newCount < originalCount) {

                    if (originalCount >= smallestToptenCount) {
                        keyTimestamp = (Long)raw.getField("timestamp");
                        keyUuid = (String)raw.getField("uuid");
                        keyRoute = route;
                        keyPickupAddress = pickupAddress;
                        keyDropoffAddress = dropoffAddress;
                        keyCount = newCount;
                        incremental = false;

//                        processor.processRegionTopTen(keyRoute, keyUuid, keyCount, keyTimestamp, incremental);
                        routeProcessor.processRouteTopTen(keyRoute, keyPickupAddress, keyDropoffAddress, keyUuid, keyCount, keyTimestamp, incremental);
                    }
                }

            }
            else {
                throw new Exception("operation:" + e.getOperation().toString());
            }

        }
        catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    public void init(Properties props) {
        // do nothing
    }
}
