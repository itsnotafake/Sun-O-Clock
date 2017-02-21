package com.example.android.sunshine.wearable;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.databinding.tool.util.L;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.example.android.sunshine.MainActivity;
import com.example.android.sunshine.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.CapabilityApi;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.common.base.Charsets;

import java.util.Set;

/**
 * Created by Devin on 2/19/2017.
 */

class MobileCommunicationLayer implements MessageApi.MessageListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener{

    private static final String TAG = MobileCommunicationLayer.class.getSimpleName();

    private Context mContext;
    private GoogleApiClient mGoogleApiClient;
    private String mWeatherSendRequestNodeId = null;

    private static final String WEATHER_SYNC_REQUEST_MESSAGE_PATH = "/weather_sync_request";
    private static final String WEATHER_SEND_REQUEST_CAPABILITY_NAME = "weather_send_request";
    private static final String WEATHER_SEND_REQUEST_MESSAGE_PATH = "/weather_send_request";

    private int mWeatherId;
    private double mMax;
    private double mMin;

    public MobileCommunicationLayer(){
    }

    void connectToGoogleApiClient(){
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
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.e(TAG, "Google API Client connection suspended");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.e(TAG, "Google API Client connection failed");
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if(messageEvent.getPath().equals(WEATHER_SYNC_REQUEST_MESSAGE_PATH)){
            Log.e(TAG, "weather_sync_request message received");

            mContext = new MainActivity().getApplicationContext();
            connectToGoogleApiClient();
            setupWeatherSendRequest();

            getWeatherCV();
            requestSendWeatherRequest(getWeatherByteArray());
        }
    }

    private void setupWeatherSendRequest(){
        CapabilityApi.GetCapabilityResult result =
                Wearable.CapabilityApi.getCapability(
                        mGoogleApiClient,
                        WEATHER_SEND_REQUEST_CAPABILITY_NAME,
                        CapabilityApi.FILTER_REACHABLE).await();
        updateSendWeatherSendCapability(result.getCapability());
    }

    //Of the capable nodes, pick the most capable node to use for weather sync request
    private void updateSendWeatherSendCapability(CapabilityInfo capabilityInfo){
        Set<Node> connectedNodes = capabilityInfo.getNodes();
        mWeatherSendRequestNodeId = pickBestNodeId(connectedNodes);
    }

    private void requestSendWeatherRequest(byte[] bytes) {
        if (mWeatherSendRequestNodeId != null) {
            Wearable.MessageApi.sendMessage(
                    mGoogleApiClient,
                    mWeatherSendRequestNodeId,
                    WEATHER_SEND_REQUEST_MESSAGE_PATH,
                    bytes).setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                @Override
                public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult) {
                    if (!sendMessageResult.getStatus().isSuccess()) {
                        Log.e(TAG, "Failed to send weather_send_request message");
                    }else{
                        Log.e(TAG, "Successfully sent weather_send_request message");
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
        return bestNodeId;
    }

    void getWeatherCV() {
        /* URI for all rows of weather data in our weather table */
        Uri forecastQueryUri = WeatherContract.WeatherEntry.CONTENT_URI;
                /* Sort order: Ascending by date */
        String sortOrder = WeatherContract.WeatherEntry.COLUMN_DATE + " ASC";
        ContentResolver contentResolver = mContext.getContentResolver();
        Cursor cursor = contentResolver.query(
                forecastQueryUri,
                MainActivity.MAIN_FORECAST_PROJECTION,
                null,
                null,
                sortOrder);

        ContentValues cv = new ContentValues();
        if(cursor.moveToFirst()){
            mWeatherId = cursor.getInt(MainActivity.INDEX_WEATHER_CONDITION_ID);
            cv.put(
                    WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
                    mWeatherId
            );

            mMax = cursor.getDouble(MainActivity.INDEX_WEATHER_MAX_TEMP);
            cv.put(
                    WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
                    mMax
            );

            mMin = cursor.getDouble(MainActivity.INDEX_WEATHER_MIN_TEMP);
            cv.put(
                    WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
                    mMin
            );
            updateWeatherDataItem(cv);
        }else{
            Log.e(TAG, "Cursor is null");
        }
        cursor.close();
    }

    private byte[] getWeatherByteArray(){
        String weatherToSend =
                String.valueOf(mWeatherId) + ","
                + String.valueOf(mMax) + ","
                + String.valueOf(mMin);
        return weatherToSend.getBytes(Charsets.UTF_8);
    }

    private void updateWeatherDataItem(ContentValues weatherValue){
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/weatherCV");

        putDataMapRequest.getDataMap().putInt(
                WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
                (int) weatherValue.get(WeatherContract.WeatherEntry.COLUMN_WEATHER_ID)
        );
        putDataMapRequest.getDataMap().putDouble(
                WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
                (double)  weatherValue.get(WeatherContract.WeatherEntry.COLUMN_MAX_TEMP)
        );
        putDataMapRequest.getDataMap().putDouble(
                WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
                (double)  weatherValue.get(WeatherContract.WeatherEntry.COLUMN_MIN_TEMP)
        );

        PutDataRequest request = putDataMapRequest.asPutDataRequest();
        request.setUrgent();
        Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(DataApi.DataItemResult dataItemResult){
                        if(!dataItemResult.getStatus().isSuccess()){
                            Log.e(TAG, "Failed to send weather Content Values");
                        }else{
                            Log.e(TAG, "Successfully sent weather Content values");
                        }
                    }
                });

    }
}
