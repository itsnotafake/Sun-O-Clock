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
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

public class MobileCommunicationService extends Service implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        MessageApi.MessageListener{
    private static final String TAG = MobileCommunicationService.class.getSimpleName();
    private static final String WEATHER_SYNC_REQUEST_MESSAGE_PATH = "/weather_sync_request";

    private Context mContext;
    private GoogleApiClient mGoogleApiClient;

    private int mWeatherId;
    private double mMax;
    private double mMin;

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

        //Flicker Data Items so Wear sees Data Item change
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

        if(cursor.moveToFirst()){
            mWeatherId = cursor.getInt(MainActivity.INDEX_WEATHER_CONDITION_ID);
            mMax = cursor.getDouble(MainActivity.INDEX_WEATHER_MAX_TEMP);;
            mMin = cursor.getDouble(MainActivity.INDEX_WEATHER_MIN_TEMP);
            updateWeatherDataItem();
        }else{
            Log.e(TAG, "Cursor is null");
        }
        cursor.close();
    }

    //Sends weather data to wearable and flickers our data item to make sure
    //onDataChanged() is called
    private void updateWeatherDataItem(){
        //First set(and send) Data Items to bogus numbers
        // to make sure onDateChanged is called
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/weatherCV");
        putDataMapRequest.getDataMap().putInt(
                WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
                0
        );
        putDataMapRequest.getDataMap().putDouble(
                WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
                0
        );
        putDataMapRequest.getDataMap().putDouble(
                WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
                0
        );
        PutDataRequest request = putDataMapRequest.asPutDataRequest();
        request.setUrgent();
        Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(DataApi.DataItemResult dataItemResult){
                        if(!dataItemResult.getStatus().isSuccess()){
                            Log.e(TAG, "Failed to send BAD weather Content Values");
                        }else{
                            Log.e(TAG, "Successfully sent BAD weather Content values");
                        }
                    }
                });

        //Now set them to their real numbers
        putDataMapRequest.getDataMap().putInt(
                WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
                mWeatherId
        );
        putDataMapRequest.getDataMap().putDouble(
                WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
                mMax
        );
        putDataMapRequest.getDataMap().putDouble(
                WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
                mMin
        );
        request = putDataMapRequest.asPutDataRequest();
        request.setUrgent();
        Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(DataApi.DataItemResult dataItemResult){
                        if(!dataItemResult.getStatus().isSuccess()){
                            Log.e(TAG, "Failed to send GOOD weather Content Values");
                        }else{
                            Log.e(TAG, "Successfully sent GOOD weather Content values");
                        }
                    }
                });
    }
}
