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
    private String mCapableWeatherSyncRequestNodeId;
    private String mANodeId;

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

        //Setup up stuff and send message
        new Thread(new Runnable(){
            @Override
            public void run(){
                //Connect to the GoogleApiClient
                mGoogleApiClient.blockingConnect(
                        CONNECTION_TIME_OUT_MS, TimeUnit.MILLISECONDS);

                //Find nodes capable of weather_sync_request
                CapabilityApi.GetCapabilityResult capabilityResult =
                        Wearable.CapabilityApi.getCapability(
                                mGoogleApiClient,
                                WEATHER_SYNC_REQUEST_CAPABILITY_NAME,
                                CapabilityApi.FILTER_REACHABLE).await();
                Set<Node> connectedNodes = capabilityResult.getCapability().getNodes();
                for(Node node : connectedNodes){
                    if(node.isNearby()){
                        mCapableWeatherSyncRequestNodeId = node.getId();
                        Log.e(TAG, "mCapableWeatherSyncRequestNode is : " + node.getDisplayName());
                        break;
                    }
                    mCapableWeatherSyncRequestNodeId = node.getId();
                }

                //Send the message
                Wearable.MessageApi.sendMessage(
                        mGoogleApiClient,
                        mCapableWeatherSyncRequestNodeId,
                        WEATHER_SYNC_REQUEST_MESSAGE_PATH,
                        null).setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                    @Override
                    public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult) {
                        if(!sendMessageResult.getStatus().isSuccess()){
                            Log.e(TAG, "weather_sync_request message unsuccessfully sent");
                        }else{
                            Log.e(TAG, "weather_sync_request message successfully sent");
                        }
                        mGoogleApiClient.disconnect();
                    }
                });
            }
        }).start();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.e(TAG, "GoogleApiClient connection successful");
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.e(TAG, "GoogleApiClient connection suspended");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.e(TAG, "GoogleApiClient connection failed");
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
