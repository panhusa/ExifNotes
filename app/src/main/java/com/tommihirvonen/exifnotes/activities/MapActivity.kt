/*
 * Exif Notes
 * Copyright (C) 2022  Tommi Hirvonen
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.tommihirvonen.exifnotes.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.InfoWindowAdapter
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.tommihirvonen.exifnotes.R
import com.tommihirvonen.exifnotes.databinding.ActivityMapBinding
import com.tommihirvonen.exifnotes.datastructures.Frame
import com.tommihirvonen.exifnotes.datastructures.Roll
import com.tommihirvonen.exifnotes.fragments.EditFrameFragment
import com.tommihirvonen.exifnotes.fragments.EditFrameFragmentCallback
import com.tommihirvonen.exifnotes.preferences.PreferenceConstants
import com.tommihirvonen.exifnotes.utilities.*
import kotlin.math.roundToInt

/**
 * Activity to display all the frames from a list of rolls on a map.
 */
class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private class Triple<T, U, V>(val first: T, var second: U, val third: V)

    private lateinit var allRolls: List<Triple<Roll, Boolean, List<Frame>>>
    private val selectedRolls = mutableListOf<Triple<Roll, Bitmap?, List<Frame>>>()

    /**
     * GoogleMap object to show the map and to hold all the markers for all frames
     */
    private var googleMap: GoogleMap? = null

    /**
     * Member to indicate whether this activity was continued or not.
     * Some animations will only be activated if this value is false.
     */
    private var continueActivity = false

    /**
     * Holds reference to the GoogleMap map type
     */
    private var mapType = 0
    private val markerList = mutableListOf<Marker>()
    private lateinit var markerBitmaps: List<Bitmap?>
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var adapter: RollMarkerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // In onSaveInstanceState a dummy boolean was put into outState.
        // savedInstanceState is not null if the activity was continued.
        if (savedInstanceState != null) continueActivity = true

        val binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.topAppBar.subtitle = intent.getStringExtra(ExtraKeys.MAPS_ACTIVITY_SUBTITLE)
        binding.topAppBar.title = intent.getStringExtra(ExtraKeys.MAPS_ACTIVITY_TITLE)
        binding.topAppBar.setNavigationOnClickListener { onBackPressed() }
        binding.topAppBar.setOnMenuItemClickListener(onMenuItemSelected)

        // Set the bottom sheet
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)
        val peekHeightOffset = resources.getDimensionPixelSize(R.dimen.MapActivityBottomSheetPeekHeight).toFloat()
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetCallback() {
            override fun onStateChanged(view: View, i: Int) {}
            override fun onSlide(view: View, v: Float) {
                val offset = binding.bottomSheet.height * v + peekHeightOffset - peekHeightOffset * v
                when (bottomSheetBehavior.state) {
                    BottomSheetBehavior.STATE_DRAGGING, BottomSheetBehavior.STATE_SETTLING -> {
                        googleMap?.setPadding(0, 0, 0, offset.roundToInt())
                    }
                    BottomSheetBehavior.STATE_COLLAPSED, BottomSheetBehavior.STATE_EXPANDED,
                    BottomSheetBehavior.STATE_HALF_EXPANDED, BottomSheetBehavior.STATE_HIDDEN -> { }
                }
            }
        })

        // Get map type from preferences
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(baseContext)
        mapType = sharedPreferences.getInt(PreferenceConstants.KEY_MAP_TYPE, GoogleMap.MAP_TYPE_NORMAL)

        val menu = binding.topAppBar.menu
        when (mapType) {
            GoogleMap.MAP_TYPE_NORMAL -> menu.findItem(R.id.menu_item_normal).isChecked = true
            GoogleMap.MAP_TYPE_HYBRID -> menu.findItem(R.id.menu_item_hybrid).isChecked = true
            GoogleMap.MAP_TYPE_SATELLITE -> menu.findItem(R.id.menu_item_satellite).isChecked = true
            GoogleMap.MAP_TYPE_TERRAIN -> menu.findItem(R.id.menu_item_terrain).isChecked = true
            else -> menu.findItem(R.id.menu_item_normal).isChecked = true
        }

        // Set the roll list and other arrays
        val rolls = intent.getParcelableArrayListExtra<Roll>(ExtraKeys.ARRAY_LIST_ROLLS)
        allRolls = rolls?.map { Triple(it, true, database.getFrames(it)) } ?: emptyList()

        // If only one roll can be displayed, hide the bottom sheet.
        if (allRolls.size == 1) {
            bottomSheetBehavior.isHideable = true
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            menu.findItem(R.id.menu_item_filter).isVisible = false
        }
        markerBitmaps = markerBitmapList

        // Set the list view and adapter
        val listView = findViewById<ListView>(R.id.rolls_list_view)
        adapter = RollMarkerAdapter(this, selectedRolls)
        listView.adapter = adapter
        updateSelectedRolls()

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
    }

    private val onMenuItemSelected = { item: MenuItem ->
        when (item.itemId) {
            R.id.menu_item_normal -> {
                item.isChecked = true
                setMapType(GoogleMap.MAP_TYPE_NORMAL)
                true
            }
            R.id.menu_item_hybrid -> {
                item.isChecked = true
                setMapType(GoogleMap.MAP_TYPE_HYBRID)
                true
            }
            R.id.menu_item_satellite -> {
                item.isChecked = true
                setMapType(GoogleMap.MAP_TYPE_SATELLITE)
                true
            }
            R.id.menu_item_terrain -> {
                item.isChecked = true
                setMapType(GoogleMap.MAP_TYPE_TERRAIN)
                true
            }
            R.id.menu_item_filter -> {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                val builder = AlertDialog.Builder(this)
                val listItems = allRolls.map { it.first.name }.toTypedArray()
                val checkedItems = allRolls.map { it.second }.toBooleanArray()
                builder.setMultiChoiceItems(listItems, checkedItems) { _: DialogInterface?, which: Int, isChecked: Boolean ->
                    checkedItems[which] = isChecked
                }
                builder.setNegativeButton(R.string.Cancel) { _: DialogInterface?, _: Int -> }
                builder.setPositiveButton(R.string.FilterNoColon) { _: DialogInterface?, _: Int ->
                    checkedItems.forEachIndexed { index, b ->
                        allRolls[index].second = b
                    }
                    updateSelectedRolls()
                    updateMarkers()
                }
                builder.setNeutralButton(R.string.DeselectAll, null)
                val dialog = builder.create()
                dialog.show()
                // Override the neutral button onClick listener after the dialog is shown.
                // This way the dialog isn't dismissed when the button is pressed.
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    // Deselect all items
                    val listView = dialog.listView
                    var i = 0
                    while (i < listView.count) {
                        listView.setItemChecked(i, false)
                        i++
                    }
                    checkedItems.fill(false)
                }
                true
            }
            else -> false
        }
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        // Insert dummy boolean so that outState is not null.
        outState.putBoolean(ExtraKeys.CONTINUE, true)
    }

    override fun onMapReady(googleMap_: GoogleMap) {
        googleMap = googleMap_

        // If only one roll can be displayed, the bottom sheet will be hidden.
        // Therefore there is no need to add the bottom padding to the map.
        if (allRolls.size > 1) {
            val peekHeightOffset = resources.getDimensionPixelSize(R.dimen.MapActivityBottomSheetPeekHeight)
            googleMap?.setPadding(0, 0, 0, peekHeightOffset)
        }

        // If the app's theme is dark, stylize the map with the custom night mode
        val prefs = PreferenceManager.getDefaultSharedPreferences(baseContext)
        when (resources?.configuration?.uiMode?.and(Configuration.UI_MODE_NIGHT_MASK)) {
            Configuration.UI_MODE_NIGHT_YES -> {
                googleMap?.setMapStyle(MapStyleOptions(resources.getString(R.string.style_json)))
            }
        }
        googleMap?.mapType = prefs.getInt(PreferenceConstants.KEY_MAP_TYPE, GoogleMap.MAP_TYPE_NORMAL)
        updateMarkers()
        if (allRolls.size == 1) {
            googleMap?.setInfoWindowAdapter(InfoWindowAdapterSingleRoll())
        } else {
            googleMap?.setInfoWindowAdapter(InfoWindowAdapterMultipleRolls())
        }
        googleMap?.setOnInfoWindowClickListener(OnInfoWindowClickListener())
    }

    private fun updateSelectedRolls() {
        selectedRolls.clear()
        var bitmapIndex = 0
        allRolls.filter { it.second }.forEach { triple ->
            selectedRolls.add(Triple(triple.first, markerBitmaps[bitmapIndex], triple.third))
            bitmapIndex++
            bitmapIndex %= markerBitmaps.size
        }
        adapter.notifyDataSetChanged()
    }

    private fun updateMarkers() {
        markerList.forEach { it.remove() }
        markerList.clear()
        selectedRolls.forEach { triple ->
            val frames = triple.third
            val bitmap = triple.second ?: return
            frames.forEach frames@ { frame ->
                val location = frame.location ?: return@frames
                val position = location.latLng ?: return@frames
                val rollName = triple.first.name
                val frameCount = "#" + frame.count
                val bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(bitmap)
                val marker = googleMap?.addMarker(MarkerOptions()
                        .icon(bitmapDescriptor)
                        .position(position)
                        .title(rollName)
                        .snippet(frameCount)
                        .anchor(0.5f, 1.0f)) // Since we use a custom marker icon, set offset.
                        ?: return@frames
                marker.tag = frame
                markerList.add(marker)
            }
        }
        if (markerList.isNotEmpty() && !continueActivity) {
            val builder = LatLngBounds.Builder()
            markerList.forEach { builder.include(it.position) }
            val bounds = builder.build()
            val width = resources.displayMetrics.widthPixels
            val height = resources.displayMetrics.heightPixels
            val padding = (width * 0.12).toInt() // offset from edges of the map 12% of screen
            // We use this command where the map's dimensions are specified.
            // This is because on some devices, the map's layout may not have yet occurred
            // (map size is 0).
            val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, width, height, padding)
            googleMap?.moveCamera(cameraUpdate)
        } else {
            Toast.makeText(this, resources.getString(R.string.NoFramesToShow), Toast.LENGTH_LONG).show()
        }
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

    private inner class InfoWindowAdapterMultipleRolls : InfoWindowAdapter {
        override fun getInfoWindow(marker: Marker): View? {
            return null
        }

        override fun getInfoContents(marker: Marker): View? {
            return if (marker.tag is Frame) {
                val frame = marker.tag as Frame? ?: return null
                @SuppressLint("InflateParams")
                val view = layoutInflater.inflate(R.layout.info_window_all_frames, null)
                val rollTextView = view.findViewById<TextView>(R.id.roll_name)
                val cameraTextView = view.findViewById<TextView>(R.id.camera)
                val frameCountTextView = view.findViewById<TextView>(R.id.frame_count)
                val dateTimeTextView = view.findViewById<TextView>(R.id.date_time)
                val lensTextView = view.findViewById<TextView>(R.id.lens)
                val noteTextView = view.findViewById<TextView>(R.id.note)
                rollTextView.text = frame.roll.name
                cameraTextView.text = frame.roll.camera?.name ?: getString(R.string.NoCamera)
                val frameCountText = "#" + frame.count
                frameCountTextView.text = frameCountText
                dateTimeTextView.text = frame.date?.dateTimeAsText ?: ""
                lensTextView.text = frame.lens?.name ?: getString(R.string.NoLens)
                noteTextView.text = frame.note
                view
            } else {
                null
            }
        }
    }

    private inner class InfoWindowAdapterSingleRoll : InfoWindowAdapter {
        override fun getInfoWindow(marker: Marker): View? {
            return null
        }

        override fun getInfoContents(marker: Marker): View? {
            return if (marker.tag is Frame) {
                val frame = marker.tag as Frame? ?: return null
                @SuppressLint("InflateParams")
                val view = layoutInflater.inflate(R.layout.info_window, null)
                val frameCountTextView = view.findViewById<TextView>(R.id.frame_count)
                val dateTimeTextView = view.findViewById<TextView>(R.id.date_time)
                val lensTextView = view.findViewById<TextView>(R.id.lens)
                val noteTextView = view.findViewById<TextView>(R.id.note)
                val frameCountText = "#" + frame.count
                frameCountTextView.text = frameCountText
                dateTimeTextView.text = frame.date?.dateTimeAsText ?: ""
                lensTextView.text = frame.lens?.name
                    ?: if (frame.roll.camera?.isNotFixedLens == true) getString(R.string.NoLens)
                    else ""
                noteTextView.text = frame.note
                view
            } else {
                null
            }
        }
    }

    private inner class OnInfoWindowClickListener : GoogleMap.OnInfoWindowClickListener {
        override fun onInfoWindowClick(marker: Marker) {
            // TODO To be refactored
//            if (marker.tag is Frame) {
//                val frame = marker.tag as Frame? ?: return
//                val arguments = Bundle()
//                val title = "" + resources.getString(R.string.EditFrame) + frame.count
//                val positiveButton = resources.getString(R.string.OK)
//                arguments.putString(ExtraKeys.TITLE, title)
//                arguments.putString(ExtraKeys.POSITIVE_BUTTON, positiveButton)
//                arguments.putParcelable(ExtraKeys.FRAME, frame)
//                val dialog = EditFrameFragmentCallback { intent ->
//                    val editedFrame: Frame = intent.getParcelableExtra(ExtraKeys.FRAME) ?: return@EditFrameFragmentCallback
//                    database.updateFrame(editedFrame)
//                    marker.tag = editedFrame
//                    marker.hideInfoWindow()
//                    marker.showInfoWindow()
//                    setResult(RESULT_OK)
//                }
//                dialog.arguments = arguments
//                dialog.show(supportFragmentManager.beginTransaction(), EditFrameFragment.TAG)
//            }
        }
    }

    private fun getMarkerBitmap(context: Context): Bitmap? {
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_marker_red) ?: return null
        val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth,
                drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun getMarkerBitmap(context: Context, hue: Float): Bitmap? {
        val bitmap = getMarkerBitmap(context)
        return bitmap?.let { setBitmapHue(it, hue) }
    }

    private fun setBitmapHue(bitmap: Bitmap, hue: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val hvs = FloatArray(3)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hvs)
                hvs[0] = hue
                bitmap.setPixel(x, y, Color.HSVToColor(Color.alpha(pixel), hvs))
            }
        }
        return bitmap
    }

    private class RollMarkerAdapter(
            context: Context,
            private val rollList: List<Triple<Roll, Bitmap?, List<Frame>>>)
        : ArrayAdapter<Triple<Roll, Bitmap?, List<Frame>>>(context, android.R.layout.simple_list_item_1, rollList) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val pair = rollList[position]
            val roll = pair.first
            val markerBitmap = pair.second
            val holder: ViewHolder
            val view = if (convertView != null) {
                holder = convertView.tag as ViewHolder
                convertView
            } else {
                val tempView = LayoutInflater.from(context).inflate(R.layout.item_roll_map_activity, parent, false)
                holder = ViewHolder()
                holder.rollNameTextView = tempView.findViewById(R.id.roll_text_view)
                holder.markerImageView = tempView.findViewById(R.id.marker_image_view)
                tempView.tag = holder
                tempView
            }
            holder.rollNameTextView.text = roll.name
            holder.markerImageView.setImageBitmap(markerBitmap)
            return view
        }

        override fun isEnabled(position: Int): Boolean {
            // Disable onClick events for the adapter. We only display the visible rolls.
            return false
        }

        private class ViewHolder {
            lateinit var rollNameTextView: TextView
            lateinit var markerImageView: ImageView
        }

    }

    private val markerBitmapList: List<Bitmap?> get() = arrayListOf(
            getMarkerBitmap(this),
            getMarkerBitmap(this, BitmapDescriptorFactory.HUE_AZURE),
            getMarkerBitmap(this, BitmapDescriptorFactory.HUE_GREEN),
            getMarkerBitmap(this, BitmapDescriptorFactory.HUE_ORANGE),
            getMarkerBitmap(this, BitmapDescriptorFactory.HUE_YELLOW),
            getMarkerBitmap(this, BitmapDescriptorFactory.HUE_BLUE),
            getMarkerBitmap(this, BitmapDescriptorFactory.HUE_ROSE),
            getMarkerBitmap(this, BitmapDescriptorFactory.HUE_CYAN),
            getMarkerBitmap(this, BitmapDescriptorFactory.HUE_VIOLET),
            getMarkerBitmap(this, BitmapDescriptorFactory.HUE_MAGENTA)
    )

}