package com.example.android.sunshine.wearable;

import android.app.Service;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.example.android.sunshine.MainActivity;
import com.example.android.sunshine.data.WeatherContract;
import com.example.android.sunshine.utilities.SunshineWeatherUtils;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.wearable.CapabilityApi;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class MobileCommunicationService extends Service implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        MessageApi.MessageListener{
    private static final String TAG = MobileCommunicationService.class.getSimpleName();
    private static final String WEATHER_SYNC_REQUEST_MESSAGE_PATH = "/weather_sync_request";
    private static final String WEATHER_SEND_REQUEST_CAPABILITY_NAME = "weather_send_request";
    private static final String WEATHER_SEND_REQUEST_MESSAGE_PATH = "/weather_send_request";

    private Context mContext;
    private static GoogleApiClient mGoogleApiClient;
    private static String mCapableWeatherSendRequestNodeId;

    private static int mWeatherId;
    private static Double mMax;
    private static Double mMin;

    public MobileCommunicationService() {
    }

    public int onStartCommand(Intent intent, int flags, int startId){
        Log.e(TAG, "MobileCommunicationService is live");
        mContext = getApplicationContext();

        //Create new GoogleApiClient
        mGoogleApiClient = new GoogleApiClient.Builder(mContext)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        mGoogleApiClient.connect();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.e(TAG, "Connected to GoogleApiClient mobile side");

        //Get weather Data from Content Provider and send as message
        getWeatherCV();

        //Add our Message Listener
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
        Log.e(TAG, "GoogleApiClient ");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.e(TAG, "Connected to GoogleApiClient mobile side");
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.e(TAG, "message received");
        if(messageEvent.getPath().equals(WEATHER_SYNC_REQUEST_MESSAGE_PATH)){
            getWeatherCV();
        }else{
            Log.e(TAG, "Unknown message event received");
        }
    }

    //Fetches our Weather Content values from the Data Provider and calls method
    //to send them to wearable
   public static void getWeatherCV() {
        /* URI for all rows of weather data in our weather table */
        Uri forecastQueryUri = WeatherContract.WeatherEntry.CONTENT_URI;
                /* Sort order: Ascending by date */
        String sortOrder = WeatherContract.WeatherEntry.COLUMN_DATE + " ASC";
        ContentResolver contentResolver = MainActivity.mContext.getContentResolver();
        Cursor cursor = contentResolver.query(
                forecastQueryUri,
                MainActivity.MAIN_FORECAST_PROJECTION,
                null,
                null,
                sortOrder);

        if(cursor.moveToFirst()){
            mWeatherId = cursor.getInt(MainActivity.INDEX_WEATHER_CONDITION_ID);
            mMax = cursor.getDouble(MainActivity.INDEX_WEATHER_MAX_TEMP);;
            mMin = cursor.getDouble(MainActivity.INDEX_WEATHER_MIN_TEMP);
            sendWeatherMessage();
        }else{
            Log.e(TAG, "Cursor is null");
        }
        cursor.close();
    }

    //Send the weather Items in a message
    static void sendWeatherMessage(){
        //check node capabilities, get node, send message
        new Thread(new Runnable(){
            @Override
            public void run(){
                //Find nodes capable of weather_sync_request
                CapabilityApi.GetCapabilityResult capabilityResult =
                        Wearable.CapabilityApi.getCapability(
                                mGoogleApiClient,
                                WEATHER_SEND_REQUEST_CAPABILITY_NAME,
                                CapabilityApi.FILTER_REACHABLE).await();
                Set<Node> connectedNodes = capabilityResult.getCapability().getNodes();
                for(Node node : connectedNodes){
                    if(node.isNearby()){
                        mCapableWeatherSendRequestNodeId = node.getId();
                        Log.e(TAG, "mCapableWeatherSendRequestNode is : " + node.getDisplayName());
                        break;
                    }
                    mCapableWeatherSendRequestNodeId = node.getId();
                }

                //Set weather values and put into byte form
                String weatherId = "" + mWeatherId;
                String max = SunshineWeatherUtils.formatTemperature(MainActivity.mContext, mMax);
                String min = SunshineWeatherUtils.formatTemperature(MainActivity.mContext, mMin);
                byte[] bytes = (weatherId + "," + max + "," + min).getBytes(Charset.forName("UTF-8"));

                //Send the message
                Wearable.MessageApi.sendMessage(
                        mGoogleApiClient,
                        mCapableWeatherSendRequestNodeId,
                        WEATHER_SEND_REQUEST_MESSAGE_PATH,
                        bytes).setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                    @Override
                    public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult) {
                        if(!sendMessageResult.getStatus().isSuccess()){
                            Log.e(TAG, "weather_send_request message unsuccessfully sent");
                        }else{
                            Log.e(TAG, "weather_send_request message successfully sent");
                        }
                    }
                });
            }
        }).start();
    }
}
