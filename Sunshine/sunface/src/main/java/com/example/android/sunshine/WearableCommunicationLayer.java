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
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Created by Devin on 2/19/2017.
 */

class WearableCommunicationLayer implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        MessageApi.MessageListener{
    private static final String TAG = WearableCommunicationLayer.class.getSimpleName();

    private Context mContext;
    private GoogleApiClient mGoogleApiClient;
    private String mWeatherSyncRequestNodeId = null;

    public static int mWeatherId;
    public static double mMax;
    public static double mMin;

    private static final String WEATHER_SYNC_REQUEST_CAPABILITY_NAME = "weather_sync_request";
    private static final String WEATHER_SYNC_REQUEST_MESSAGE_PATH = "/weather_sync_request";
    private static final String WEATHER_SEND_REQUEST_MESSAGE_PATH = "/weather_send_request";

    public WearableCommunicationLayer(Context context){
        mContext = context;

        mGoogleApiClient = new GoogleApiClient.Builder(mContext)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.e(TAG, "Connected to Google API Client");
        setupWeatherSyncRequest();
        requestSyncWeatherRequest();
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.e(TAG, "Google API Client connection suspended");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.e(TAG, "Google API Client connection failed");
    }

    //Get nodes capable of sending a weather sync request
    void setupWeatherSyncRequest(){
        CapabilityApi.CapabilityListener capabilityListener =
                new CapabilityApi.CapabilityListener(){
                    @Override
                    public void onCapabilityChanged(CapabilityInfo capabilityInfo){
                        Log.e(TAG, "capabilityChange detected");
                        updateSendWeatherSyncCapability(capabilityInfo);
                    }
                };
        Wearable.CapabilityApi.addCapabilityListener(
                mGoogleApiClient,
                capabilityListener,
                WEATHER_SYNC_REQUEST_CAPABILITY_NAME
        );
    }

    //Of the capable nodes, pick the most capable node to use for weather sync request
    private void updateSendWeatherSyncCapability(CapabilityInfo capabilityInfo){
        Set<Node> connectedNodes = capabilityInfo.getNodes();
        mWeatherSyncRequestNodeId = pickBestNodeId(connectedNodes);
    }

    //Send the weather sync request
    void requestSyncWeatherRequest() {
        byte[] bytes = new byte[1];
        if (mWeatherSyncRequestNodeId != null) {
            Wearable.MessageApi.sendMessage(
                    mGoogleApiClient,
                    mWeatherSyncRequestNodeId,
                    WEATHER_SYNC_REQUEST_MESSAGE_PATH,
                    bytes).setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                @Override
                public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult) {
                    if (!sendMessageResult.getStatus().isSuccess()) {
                        Log.e(TAG, "Failed to send weather_sync_request message");
                    }else{
                        Log.e(TAG, "Successfully sent weather_sync_request message");
                    }
                }
            });
        } else {
            Log.e(TAG, "Unable to retrieve node with weather sync request capability");
        }
    }

    private String pickBestNodeId(Set<Node> nodes){
        String bestNodeId = null;
        for(Node node : nodes){
            if(node.isNearby()){
                return node.getId();
            }
            bestNodeId = node.getId();
        }
        Log.e(TAG, "No nearby nodes");
        return bestNodeId;
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if(messageEvent.getPath().equals(WEATHER_SEND_REQUEST_MESSAGE_PATH)){
            Log.e(TAG, "weather_send_request message received");
            byte[] bytes = messageEvent.getData();
            String weatherData = new String(bytes, StandardCharsets.UTF_8);
            String[] weatherDataArray = weatherData.split(",");
            mWeatherId = Integer.valueOf(weatherDataArray[0]);
            mMax = Double.valueOf(weatherDataArray[1]);
            mMin = Double.valueOf(weatherDataArray[2]);

            Log.e(TAG, "Current Weather ID: " + mWeatherId + "\n" +
                    "Current max temp: " + mMax + "\n" +
                    "Current min temp: " + mMin);
        }
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
