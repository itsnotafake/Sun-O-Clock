package com.example.android.sunshine.wearable;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.example.android.sunshine.MainActivity;
import com.example.android.sunshine.data.WeatherContract;
import com.example.android.sunshine.sync.SunshineSyncTask;

import static com.example.android.sunshine.MainActivity.MAIN_FORECAST_PROJECTION;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
public class SendWeatherImmediateService extends IntentService {
    public static final String TAG = "SendWeatherService";
    public SendWeatherImmediateService() {
        super("SendWeatherImmediateService()");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.e(TAG, "Handling recieved Intent");
        //First make sure the weather data here is synced
        SunshineSyncTask.syncWeather(this);

        ContentValues cv = getWeatherCV();
        Intent broadcastWeather = new Intent("com.example.android.sunshine.BROADCAST_WEATHER");
        Bundle bundle = new Bundle();
        bundle.putParcelable("WEATHER", cv);
        broadcastWeather.putExtra("WEATHER", bundle);
        sendBroadcast(broadcastWeather);
    }

    private ContentValues getWeatherCV() {
        /* URI for all rows of weather data in our weather table */
        Uri forecastQueryUri = WeatherContract.WeatherEntry.CONTENT_URI;
                /* Sort order: Ascending by date */
        String sortOrder = WeatherContract.WeatherEntry.COLUMN_DATE + " ASC";
        ContentResolver contentResolver = this.getContentResolver();
        Cursor cursor = contentResolver.query(
                forecastQueryUri,
                MAIN_FORECAST_PROJECTION,
                null,
                null,
                sortOrder);

        ContentValues cv = new ContentValues();
        if(cursor.moveToFirst()){
            cv.put(
                    WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
                    cursor.getInt(MainActivity.INDEX_WEATHER_CONDITION_ID)
            );
            cv.put(
                    WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
                    cursor.getDouble(MainActivity.INDEX_WEATHER_MAX_TEMP)
            );
            cv.put(
                    WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
                    cursor.getDouble(MainActivity.INDEX_WEATHER_MIN_TEMP)
            );
        }else{
            Log.e("TAG", "Cursor is null");
            return null;
        }
        return cv;
    }
}
