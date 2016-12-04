package com.tommihirvonen.exifnotes.Activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.preference.PreferenceManager;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.tommihirvonen.exifnotes.Datastructures.Frame;
import com.tommihirvonen.exifnotes.Utilities.FilmDbHelper;
import com.tommihirvonen.exifnotes.Fragments.FramesFragment;
import com.tommihirvonen.exifnotes.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback {

    FilmDbHelper database;
    long rollId;
    ArrayList<Frame> mFrameClassList = new ArrayList<>();
    private GoogleMap mMap;

    boolean continue_activity = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if ( savedInstanceState != null ) continue_activity = true;

        setContentView(R.layout.activity_maps);
        Intent intent = getIntent();
        rollId = intent.getLongExtra(FramesFragment.ROLLINFO_EXTRA_MESSAGE, -1);

        // If the rollId is -1, then something went wrong.
        if ( rollId == -1 ) finish();

        database = new FilmDbHelper(this);
        mFrameClassList = database.getAllFramesFromRoll(rollId);

        // ********** Commands to get the action bar and color it **********
        // Get preferences to determine UI color
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String UIColor = prefs.getString("UIColor", "#ef6c00,#e65100");
        List<String> colors = Arrays.asList(UIColor.split(","));
        String primaryColor = colors.get(0);
        String secondaryColor = colors.get(1);
        if ( getSupportActionBar() != null ) {
            getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE);
            getSupportActionBar().setElevation(0);
            getSupportActionBar().setTitle(database.getRoll(rollId).getName());
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setBackgroundDrawable(new ColorDrawable(Color.parseColor(primaryColor)));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().setStatusBarColor(Color.parseColor(secondaryColor));
        }
        // *****************************************************************


        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        LatLng position;
        ArrayList<Marker> markerArrayList = new ArrayList<>();

        for (Frame frame : mFrameClassList) {

            // Parse the latlng_location string
            String location = frame.getLocation();
            if ( location.length() > 0 && !location.equals("null")) {
                String latString = location.substring(0, location.indexOf(" "));
                String lngString = location.substring(location.indexOf(" ") + 1, location.length() - 1);
                double lat = Double.parseDouble(latString.replace(",", "."));
                double lng = Double.parseDouble(lngString.replace(",", "."));
                position = new LatLng(lat, lng);
                String title = "#" + frame.getCount();
                String snippet = frame.getDate();
                markerArrayList.add(mMap.addMarker(new MarkerOptions().position(position).title(title).snippet(snippet)));
            }
        }

        if ( markerArrayList.size() > 0 ) {
            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            for (Marker marker : markerArrayList) {
                builder.include(marker.getPosition());
            }
            final LatLngBounds bounds = builder.build();

            if ( !continue_activity ) mMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
                @Override
                public void onMapLoaded() {
                    int padding = 100;
                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
                }
            });
        }
        else {
            Toast.makeText(this, getResources().getString(R.string.NoFramesToShow), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("CONTINUE", true);
    }
}
