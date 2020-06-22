package com.tommihirvonen.exifnotes.activities

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.OnMapClickListener
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.tommihirvonen.exifnotes.R
import com.tommihirvonen.exifnotes.datastructures.Location
import com.tommihirvonen.exifnotes.utilities.ExtraKeys
import com.tommihirvonen.exifnotes.utilities.GeocodingAsyncTask
import com.tommihirvonen.exifnotes.utilities.GeocodingAsyncTask.AsyncResponse
import com.tommihirvonen.exifnotes.utilities.PreferenceConstants
import com.tommihirvonen.exifnotes.utilities.Utilities

/**
 * Allows the user to select a location for a frame on a map.
 */
class LocationPickActivity : AppCompatActivity(), OnMapReadyCallback, OnMapClickListener, View.OnClickListener, SearchView.OnQueryTextListener {
    /**
     * GoogleMap object to show the map and marker
     */
    private var googleMap: GoogleMap? = null

    /**
     * Marker object to hold the marker added/moved by the user.
     */
    private var marker: Marker? = null

    /**
     * ProgressBar object displayed when the formatted address is queried
     */
    private lateinit var progressBar: ProgressBar

    /**
     * TextView which displays the current formatted address
     */
    private lateinit var formattedAddressTextView: TextView

    /**
     * String to hold the current formatted address
     */
    private var formattedAddress: String? = null

    /**
     * Holds the current location.
     */
    private var latLngLocation: LatLng? = null

    private lateinit var googleMapsApiKey: String

    /**
     * Member to indicate whether this activity was continued or not.
     * Some animations will only be activated if this value is false.
     */
    private var continueActivity = false

    /**
     * Holds reference to the GoogleMap map type
     */
    private var mapType = 0

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If the device is locked, this activity can be shown regardless.
        // This way the user doesn't have to unlock the device with authentication
        // just to access this activity.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            val window = window
            @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
            @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        if (Utilities.isAppThemeDark(baseContext)) {
            setTheme(R.style.Theme_AppCompat)
        }
        if (savedInstanceState != null) continueActivity = true
        setContentView(R.layout.activity_location_pick)
        val confirmFab = findViewById<FloatingActionButton>(R.id.fab)
        confirmFab.setOnClickListener(this)
        val currentLocationFab = findViewById<FloatingActionButton>(R.id.fab_current_location)
        currentLocationFab.setOnClickListener(this)
        Utilities.setUiColor(this, true)
        supportActionBar?.title = resources.getString(R.string.PickLocation)
        val secondaryColor = Utilities.getSecondaryUiColor(baseContext)

        // Also change the floating action button color. Use the darker secondaryColor for this.
        confirmFab.backgroundTintList = ColorStateList.valueOf(secondaryColor)

        // In case the app's theme is dark, color the bottom bar dark grey
        if (Utilities.isAppThemeDark(baseContext)) {
            val bottomBarLayout = findViewById<FrameLayout>(R.id.bottom_bar)
            bottomBarLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_grey))
        }
        progressBar = findViewById(R.id.progress_bar)
        formattedAddressTextView = findViewById(R.id.formatted_address)
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(baseContext)
        mapType = sharedPreferences.getInt(PreferenceConstants.KEY_MAP_TYPE, GoogleMap.MAP_TYPE_NORMAL)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
        googleMapsApiKey = resources.getString(R.string.google_maps_key)

        // If the activity is continued, then savedInstanceState is not null.
        // Get the location from there.
        val location: Location?
        if (savedInstanceState != null) {
            location = savedInstanceState.getParcelable(ExtraKeys.LOCATION)
            formattedAddress = savedInstanceState.getString(ExtraKeys.FORMATTED_ADDRESS)
        } else {
            // Else get the location from Intent.
            val intent = intent
            location = intent.getParcelableExtra(ExtraKeys.LOCATION)
            formattedAddress = intent.getStringExtra(ExtraKeys.FORMATTED_ADDRESS)
        }
        if (location != null) {
            latLngLocation = location.latLng
            // If the formatted address is set, display it
            if (formattedAddress != null && formattedAddress?.isNotEmpty() == true) {
                formattedAddressTextView.text = formattedAddress
            } else {
                progressBar.visibility = View.VISIBLE
                GeocodingAsyncTask(AsyncResponse { _: String?, formatted_address: String ->
                    progressBar.visibility = View.INVISIBLE
                    formattedAddress = if (formatted_address.isNotEmpty()) {
                        formattedAddressTextView.text = formatted_address
                        formatted_address
                    } else {
                        formattedAddressTextView.setText(R.string.AddressNotFound)
                        null
                    }
                }).execute(location.decimalLocation, googleMapsApiKey)
            }
        }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_location_pick, menu)
        // Retrieve the SearchView and plug it into SearchManager
        val searchView = menu.findItem(R.id.action_search).actionView as SearchView
        // The SearchView's query hint is localization dependant by default.
        // Replace it with the English text.
        searchView.queryHint = getString(R.string.SearchWEllipsis)
        searchView.setOnQueryTextListener(this)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        when (mapType) {
            GoogleMap.MAP_TYPE_NORMAL -> menu.findItem(R.id.menu_item_normal).isChecked = true
            GoogleMap.MAP_TYPE_HYBRID -> menu.findItem(R.id.menu_item_hybrid).isChecked = true
            GoogleMap.MAP_TYPE_SATELLITE -> menu.findItem(R.id.menu_item_satellite).isChecked = true
            GoogleMap.MAP_TYPE_TERRAIN -> menu.findItem(R.id.menu_item_terrain).isChecked = true
            else -> menu.findItem(R.id.menu_item_normal).isChecked = true
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
            R.id.menu_item_normal -> {
                item.isChecked = true
                setMapType(GoogleMap.MAP_TYPE_NORMAL)
                return true
            }
            R.id.menu_item_hybrid -> {
                item.isChecked = true
                setMapType(GoogleMap.MAP_TYPE_HYBRID)
                return true
            }
            R.id.menu_item_satellite -> {
                item.isChecked = true
                setMapType(GoogleMap.MAP_TYPE_SATELLITE)
                return true
            }
            R.id.menu_item_terrain -> {
                item.isChecked = true
                setMapType(GoogleMap.MAP_TYPE_TERRAIN)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Sets the GoogleMap map type
     *
     * @param mapType One of the map type constants from class GoogleMap
     */
    private fun setMapType(mapType: Int) {
        this.mapType = mapType
        googleMap?.mapType = mapType
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val editor = preferences.edit()
        editor.putInt(PreferenceConstants.KEY_MAP_TYPE, mapType)
        editor.apply()
    }

    override fun onMapReady(googleMap_: GoogleMap) {
        googleMap = googleMap_
        googleMap?.let { googleMap ->
            googleMap.setOnMapClickListener(this)
            // If the app's theme is dark, stylize the map with the custom night mode
            if (Utilities.isAppThemeDark(baseContext)) {
                googleMap.setMapStyle(MapStyleOptions(resources.getString(R.string.style_json)))
            }
            googleMap.mapType = mapType
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED) {
                googleMap.isMyLocationEnabled = true
            }
            latLngLocation?.let {latLngLocation ->
                marker = googleMap.addMarker(MarkerOptions().position(latLngLocation))
                if (!continueActivity) {
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLngLocation, 15f))
                    Toast.makeText(baseContext, resources.getString(R.string.TapOnMap), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onMapClick(latLng: LatLng) {
        // Get the formatted address
        formattedAddressTextView.text = ""
        progressBar.visibility = View.VISIBLE
        val latitude = "" + latLng.latitude
        val longitude = "" + latLng.longitude
        val query = "$latitude $longitude"
        GeocodingAsyncTask(AsyncResponse { _: String?, formatted_address: String ->
            progressBar.visibility = View.INVISIBLE
            if (formatted_address.isNotEmpty()) {
                formattedAddressTextView.text = formatted_address
                formattedAddress = formatted_address
            } else {
                formattedAddressTextView.setText(R.string.AddressNotFound)
                formattedAddress = null
            }
        }).execute(query, googleMapsApiKey)

        // if the location was cleared before editing -> add marker to selected location
        if (marker == null) {
            marker = googleMap?.addMarker(MarkerOptions().position(latLng))
        } else {
            marker?.position = latLng
        }
        latLngLocation = latLng
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        // When the user enters the search string, use GeocodingAsyncTask to get
        // the formatted address and coordinates. Also move the marker if the result was valid.
        formattedAddressTextView.text = ""
        progressBar.visibility = View.VISIBLE
        GeocodingAsyncTask(AsyncResponse { output: String, formatted_address: String? ->
            progressBar.visibility = View.INVISIBLE
            if (output.isNotEmpty()) {
                val location = Location(output)
                val position = location.latLng
                if (position != null) {
                    // marker is null, if the search was made before the marker has been added
                    // -> add marker to selected location
                    if (marker == null) {
                        marker = googleMap?.addMarker(MarkerOptions().position(position))
                    } else {
                        // otherwise just set the location
                        marker?.position = position
                    }
                    latLngLocation = position
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 15f))
                }
                formattedAddressTextView.text = formatted_address
                formattedAddress = formatted_address
            } else {
                formattedAddressTextView.setText(R.string.AddressNotFound)
                formattedAddress = null
            }
        }).execute(query, googleMapsApiKey)
        return false
    }

    override fun onQueryTextChange(newText: String): Boolean {
        // Do nothing
        return false
    }

    override fun onClick(v: View) {
        if (v.id == R.id.fab) {
            latLngLocation?.let {
                val intent = Intent()
                intent.putExtra(ExtraKeys.LOCATION, Location(it))
                intent.putExtra(ExtraKeys.FORMATTED_ADDRESS, formattedAddress)
                setResult(Activity.RESULT_OK, intent)
                finish()
            } ?: Toast.makeText(baseContext, R.string.NoLocation, Toast.LENGTH_SHORT).show()
        } else if (v.id == R.id.fab_current_location) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling ActivityCompat.requestPermissions()
                return
            }
            fusedLocationClient.lastLocation.addOnSuccessListener(this) { location: android.location.Location? ->
                if (location != null) {
                    val position = LatLng(location.latitude, location.longitude)
                    onMapClick(position)
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 15f))
                }
            }
        }
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Store the currently set location to outState.
        latLngLocation?.let {
            outState.putParcelable(ExtraKeys.LOCATION, Location(it))
            outState.putString(ExtraKeys.FORMATTED_ADDRESS, formattedAddress)
        }
    }
}