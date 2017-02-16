package templar.sunoclockface;

import android.util.Log;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.WearableListenerService;

public class WeatherListenerService extends WearableListenerService {
    private static final String TAG = "WeatherListenerService";
    private static final String DATA_EVENT_PATH = "/weatherCV";

    public static int mWeatherId = 0;
    public static double mMax = 0;
    public static double mMin = 0;

    @Override
    public void onDataChanged(DataEventBuffer dataEvents){
        Log.e(TAG, "dataEvent occured");


        for(DataEvent dataEvent : dataEvents){
            if(dataEvent.getType() == DataEvent.TYPE_CHANGED){
                DataMap dataMap = DataMapItem.fromDataItem(dataEvent.getDataItem()).getDataMap();
                String path = dataEvent.getDataItem().getUri().getPath();
                if(path.equals(DATA_EVENT_PATH)){
                    mWeatherId = dataMap.getInt("weather_id");
                    mMax = dataMap.getDouble("max");
                    mMin = dataMap.getDouble("min");
                    Log.e(TAG, "Weather data successfully received");
                }
            }
        }
        Log.e(TAG, "Weather Id: " + WeatherListenerService.mWeatherId);
        Log.e(TAG, "Max Temp: " + WeatherListenerService.mMax);
        Log.e(TAG, "Min Temp: " + WeatherListenerService.mMin);
    }
}
