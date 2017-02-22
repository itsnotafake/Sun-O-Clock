package com.example.android.sunshine;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.CapabilityApi;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Created by Devin on 2/19/2017.
 */

class WearableCommunicationLayer implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener{
    private static final String TAG = WearableCommunicationLayer.class.getSimpleName();

    private Context mContext;
    private GoogleApiClient mGoogleApiClient;
    private String mWeatherSyncRequestNodeId = null;

    public static int mWeatherId;
    public static double mMax;
    public static double mMin;

    private static final String WEATHER_SYNC_REQUEST_CAPABILITY_NAME = "weather_sync_request";
    private static final String WEATHER_SYNC_REQUEST_MESSAGE_PATH = "/weather_sync_request";
    private static final int CONNECTION_TIME_OUT_MS = 7500;

    public WearableCommunicationLayer(Context context){
        mContext = context;

        //Create new GoogleApiClient
        mGoogleApiClient = new GoogleApiClient.Builder(mContext)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        //Retrieve device node
        new Thread(new Runnable(){
            @Override
            public void run(){
                //Connect to the GoogleApiClient
                mGoogleApiClient.blockingConnect(
                        CONNECTION_TIME_OUT_MS, TimeUnit.MILLISECONDS);

                //Get and set the first found node
                NodeApi.GetConnectedNodesResult result =
                        Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();
                List<Node> nodes = result.getNodes();
                if(nodes.size() > 0 && nodes.size() < 2) {
                    mWeatherSyncRequestNodeId = nodes.get(0).getId();
                    Log.e(TAG, "Node connected" + "\n" + "Node is: " +
                            mWeatherSyncRequestNodeId);
                }else{
                    Log.e(TAG, "No nodes found");
                }

                //Send the message
                Wearable.MessageApi.sendMessage(
                        mGoogleApiClient,
                        mWeatherSyncRequestNodeId,
                        WEATHER_SYNC_REQUEST_MESSAGE_PATH,
                        null).setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                    @Override
                    public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult) {
                        if(!sendMessageResult.getStatus().isSuccess()){
                            Log.e(TAG, "weather_sync_request message unsuccessfully sent");
                        }else{
                            Log.e(TAG, "weather_sync_request message successfully sent");
                        }
                    }
                });
            }
        }).start();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    public class WeatherListenerService extends WearableListenerService {
        private static final String TAG = "WeatherListenerService";
        private static final String DATA_EVENT_PATH = "/weatherCV";

        @Override
        public void onDataChanged(DataEventBuffer dataEvents){
            Log.e(TAG, "dataEvent occured");

            for(DataEvent dataEvent : dataEvents){
                if(dataEvent.getType() == DataEvent.TYPE_CHANGED){
                    DataMap dataMap = DataMapItem.fromDataItem(dataEvent.getDataItem()).getDataMap();
                    String path = dataEvent.getDataItem().getUri().getPath();
                    if(path.equals(DATA_EVENT_PATH)){
                        WearableCommunicationLayer.mWeatherId = dataMap.getInt("weather_id");
                        WearableCommunicationLayer.mMax = dataMap.getDouble("max");
                        WearableCommunicationLayer.mMin = dataMap.getDouble("min");
                        Log.e(TAG, "Weather data successfully received");

                        Log.e(TAG, "New Weather ID: " +WearableCommunicationLayer.mWeatherId + "\n" +
                                "New max temp: " + WearableCommunicationLayer.mMax + "\n" +
                                "New min temp: " + WearableCommunicationLayer.mMin);
                    }
                }
            }
        }
    }
}
