package com.example.android.sunshine;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.wearable.CapabilityApi;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.DataApi;
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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Created by Devin on 2/19/2017.
 */

class WearableCommunicationLayer implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        MessageApi.MessageListener{
    private static final String TAG = WearableCommunicationLayer.class.getSimpleName();

    private Context mContext;
    private SunshineWatchFace.Engine mEngine;
    private GoogleApiClient mGoogleApiClient;
    private String mCapableWeatherSyncRequestNodeId;

    public static String mWeatherId;
    public static String mMax;
    public static String mMin;

    private static final String WEATHER_SYNC_REQUEST_CAPABILITY_NAME = "weather_sync_request";
    private static final String WEATHER_SYNC_REQUEST_MESSAGE_PATH = "/weather_sync_request";
    private static final String WEATHER_SEND_REQUEST_MESSAGE_PATH = "/weather_send_request";
    private static final int CONNECTION_TIME_OUT_MS = 7500;

    WearableCommunicationLayer(Context context, SunshineWatchFace.Engine engine){
        mContext = context;
        mEngine = engine;

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
                    }
                });
            }
        }).start();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.e(TAG, "GoogleApiClient connection successful");
        //Add Message Listener and set Callbacks
        Wearable.MessageApi.addListener(mGoogleApiClient, this).setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(@NonNull Status status) {
                if(status.isSuccess()){
                    Log.e(TAG, "Message Listener successfully added");
                }else{
                    Log.e(TAG, "Message Listener unsuccessfully added");
                }
            }
        });
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.e(TAG, "GoogleApiClient connection suspended");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.e(TAG, "GoogleApiClient connection failed");
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.e(TAG, "message received");
        if(messageEvent.getPath().equals(WEATHER_SEND_REQUEST_MESSAGE_PATH)){
            Log.e(TAG, "weather_send_request message received");
            byte[] bytes = messageEvent.getData();
            String[] weatherData = new String(bytes, Charset.forName("UTF-8")).split(",");
            mWeatherId = weatherData[0].replaceAll("\\s", "");
            mMax = weatherData[1].replaceAll("\\s", "");
            mMin = weatherData[2].replaceAll("\\s", "");
            Log.e(TAG, "New Weather ID: " + mWeatherId + "\n" +
                    "New max temp: " + mMax + "\n" +
                    "New min temp: " + mMin);

            //Set new watchface background
            mEngine.mBackgroundBitmap = BitmapFactory.decodeResource(
                    mContext.getResources(),
                    mEngine.getBackgroundId());
            mEngine.invalidate();
        }else{
            Log.e(TAG, "Unknown message event received");
        }
    }
}
