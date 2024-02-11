/*
 * Exif Notes
 * Copyright (C) 2024  Tommi Hirvonen
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

package com.tommihirvonen.exifnotes.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.widget.Toast
import androidx.core.database.getLongOrNull
import com.tommihirvonen.exifnotes.core.decimalString
import com.tommihirvonen.exifnotes.core.entities.*
import com.tommihirvonen.exifnotes.core.latLngOrNull
import com.tommihirvonen.exifnotes.core.localDateTimeOrNull
import com.tommihirvonen.exifnotes.core.sortableDateTime
import com.tommihirvonen.exifnotes.database.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FilmDbHelper is the SQL database class that holds all the information
 * the user stores in the app. This class provides all necessary CRUD operations as well as
 * export and import functionality.
 */
@Singleton
class Database @Inject constructor(@ApplicationContext private val context: Context)
    : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    // ******************** CRUD operations for the frames table ********************
    /**
     * Adds a new frame to the database.
     * @param frame the new frame to be added to the database
     */
    fun addFrame(frame: Frame): Boolean {
        val values = buildFrameContentValues(frame)
        val rowId = writableDatabase.use { db ->
            db.insert(TABLE_FRAMES, null, values)
        }
        frame.id = rowId
        // Add the filter links, if the frame was inserted successfully.
        return if (rowId != -1L) {
            frame.filters.forEach { filter -> addFrameFilterLink(frame, filter) }
            true
        } else {
            false
        }
    }

    /**
     * Gets all the frames from a specified roll.
     * @param roll Roll object whose frames should be fetched
     * @return an array of Frames
     */
    fun getFrames(roll: Roll): List<Frame> = select(
        TABLE_FRAMES,
        selection = "$KEY_ROLL_ID=?",
        selectionArgs = listOf(roll.id.toString()),
        orderBy = KEY_COUNT
    ) { row ->
        Frame(
            roll = roll,
            id = row.getLong(KEY_FRAME_ID),
            count = row.getInt(KEY_COUNT),
            shutter = row.getStringOrNull(KEY_SHUTTER),
            aperture = row.getStringOrNull(KEY_APERTURE),
            note = row.getStringOrNull(KEY_FRAME_NOTE),
            focalLength = row.getInt(KEY_FOCAL_LENGTH),
            exposureComp = row.getStringOrNull(KEY_EXPOSURE_COMP),
            noOfExposures = row.getInt(KEY_NO_OF_EXPOSURES),
            flashComp = row.getStringOrNull(KEY_FLASH_COMP),
            meteringMode = row.getInt(KEY_METERING_MODE),
            formattedAddress = row.getStringOrNull(KEY_FORMATTED_ADDRESS),
            pictureFilename = row.getStringOrNull(KEY_PICTURE_FILENAME),
            lightSource = LightSource.from(row.getInt(KEY_LIGHT_SOURCE)),
            flashUsed = row.getInt(KEY_FLASH_USED) > 0,
            flashPower = row.getStringOrNull(KEY_FLASH_POWER),
            location = row.getStringOrNull(KEY_LOCATION)?.let(::latLngOrNull),
            date = row.getStringOrNull(KEY_DATE)?.let(::localDateTimeOrNull) ?: LocalDateTime.now(),
            lens = row.getLongOrNull(KEY_LENS_ID)?.let(::getLens)
        ).apply {
            filters = getLinkedFilters(this)
        }
    }

    /**
     * Updates the information of a frame.
     * @param frame the frame to be updated.
     */
    fun updateFrame(frame: Frame): Int {
        val contentValues = buildFrameContentValues(frame)
        val rows = writableDatabase.update(TABLE_FRAMES, contentValues, "$KEY_FRAME_ID=?", arrayOf(frame.id.toString()))
        if (rows > 0) {
            deleteFrameFilterLinks(frame)
            frame.filters.forEach { filter -> addFrameFilterLink(frame, filter) }
        }
        return rows
    }

    /**
     * Deletes a frame from the database.
     * @param frame the frame to be deleted
     */
    fun deleteFrame(frame: Frame): Int = writableDatabase.delete(
        TABLE_FRAMES,
        "$KEY_FRAME_ID = ?",
        arrayOf(frame.id.toString()))

    /**
     * Gets all complementary picture filenames from the frames table.
     * @return List of all complementary picture filenames
     */
    val complementaryPictureFilenames: List<String> get() {
        return select(
            TABLE_FRAMES,
            columns = listOf(KEY_PICTURE_FILENAME),
            selection = "$KEY_PICTURE_FILENAME IS NOT NULL") { row ->
            row.getString(KEY_PICTURE_FILENAME)
        }
    }

    // ******************** CRUD operations for the lenses table ********************
    /**
     * Adds a new lens to the database.
     * @param lens the lens to be added to the database
     */
    fun addLens(lens: Lens, database: SQLiteDatabase = writableDatabase): Long {
        val id = database.insert(TABLE_LENSES, null, buildLensContentValues(lens))
        lens.id = id
        return id
    }

    /**
     * Gets a lens corresponding to the id.
     * @param lensId the id of the lens
     * @return a Lens corresponding to the id
     */
    private fun getLens(lensId: Long): Lens? {
        val filters = select(
            TABLE_LINK_LENS_FILTER,
            columns = listOf(KEY_FILTER_ID),
            selection = "$KEY_LENS_ID=?",
            selectionArgs = listOf(lensId.toString())) { row ->
            row.getLong(KEY_FILTER_ID)
        }.toHashSet()
        val cameras = select(
            TABLE_LINK_CAMERA_LENS,
            columns = listOf(KEY_CAMERA_ID),
            selection = "$KEY_LENS_ID=?",
            selectionArgs = listOf(lensId.toString())) { row ->
            row.getLong(KEY_CAMERA_ID)
        }.toHashSet()
        return selectFirstOrNull(
            TABLE_LENSES,
            selection = "$KEY_LENS_ID=?",
            selectionArgs = listOf(lensId.toString())) { row ->
            lensMapper(row).apply {
                filterIds = filters
                cameraIds = cameras
            }
        }
    }

    private val lensMapper = { row: Cursor ->
        Lens(
            id = row.getLong(KEY_LENS_ID),
            make = row.getString(KEY_LENS_MAKE),
            model = row.getString(KEY_LENS_MODEL),
            serialNumber = row.getStringOrNull(KEY_LENS_SERIAL_NO),
            minAperture = row.getStringOrNull(KEY_LENS_MIN_APERTURE),
            maxAperture = row.getStringOrNull(KEY_LENS_MAX_APERTURE),
            minFocalLength = row.getInt(KEY_LENS_MIN_FOCAL_LENGTH),
            maxFocalLength = row.getInt(KEY_LENS_MAX_FOCAL_LENGTH),
            apertureIncrements = row.getInt(KEY_LENS_APERTURE_INCREMENTS).let(Increment::from),
            customApertureValues = row.getStringOrNull(KEY_LENS_CUSTOM_APERTURE_VALUES)
                ?.let(Json::decodeFromString)
                ?: emptyList()
        )
    }

    /**
     * Get lenses from the database.
     */
    val lenses: List<Lens> get() {
        val filters = select(TABLE_LINK_LENS_FILTER) { row ->
            row.getLong(KEY_LENS_ID) to row.getLong(KEY_FILTER_ID)
        }.groupBy(Pair<Long, Long>::first, Pair<Long, Long>::second)
        val cameras = select(TABLE_LINK_CAMERA_LENS) { row ->
            row.getLong(KEY_LENS_ID) to row.getLong(KEY_CAMERA_ID)
        }.groupBy(Pair<Long, Long>::first, Pair<Long, Long>::second)

        val selection = "$KEY_LENS_ID not in (select $KEY_LENS_ID from $TABLE_CAMERAS where $KEY_LENS_ID is not null)"
        val orderBy = "$KEY_LENS_MAKE collate nocase,$KEY_LENS_MODEL collate nocase"
        return select(TABLE_LENSES, selection = selection, orderBy = orderBy) { row ->
            lensMapper(row).apply {
                filterIds = filters[id]?.toHashSet() ?: HashSet()
                cameraIds = cameras[id]?.toHashSet() ?: HashSet()
            }
        }
    }

    /**
     * Deletes Lens from the database.
     * @param lens the Lens to be deleted
     */
    fun deleteLens(lens: Lens, database: SQLiteDatabase = writableDatabase): Int =
            database.delete(TABLE_LENSES, "$KEY_LENS_ID = ?", arrayOf(lens.id.toString()))

    /**
     * Checks if the lens is being used in some frame.
     * @param lens Lens to be checked
     * @return true if the lens is in use, false if not
     */
    fun isLensInUse(lens: Lens) = selectFirstOrNull(
        TABLE_FRAMES,
        selection = "$KEY_LENS_ID=?",
        selectionArgs = listOf(lens.id.toString())) { true } ?: false


    /**
     * Updates the information of a lens
     * @param lens the Lens to be updated
     */
    fun updateLens(lens: Lens, database: SQLiteDatabase = writableDatabase): Int {
        val contentValues = buildLensContentValues(lens)
        return database.update(TABLE_LENSES, contentValues, "$KEY_LENS_ID=?", arrayOf(lens.id.toString()))
    }

    // ******************** CRUD operations for the cameras table ********************
    /**
     * Adds a new camera to the database.
     * @param camera the camera to be added to the database
     */
    fun addCamera(camera: Camera): Long {
        camera.lens?.let { lens ->
            lens.make = camera.make
            lens.model = camera.model
            val lensId = addLens(lens)
            lens.id = lensId
        }
        val cameraId = writableDatabase.insert(TABLE_CAMERAS, null, buildCameraContentValues(camera))
        camera.id = cameraId
        return cameraId
    }

    private val cameraMapper = { cursor: Cursor ->
        Camera(
            id = cursor.getLong(KEY_CAMERA_ID),
            make = cursor.getStringOrNull(KEY_CAMERA_MAKE),
            model = cursor.getStringOrNull(KEY_CAMERA_MODEL),
            serialNumber = cursor.getStringOrNull(KEY_CAMERA_SERIAL_NO),
            minShutter = cursor.getStringOrNull(KEY_CAMERA_MIN_SHUTTER),
            maxShutter = cursor.getStringOrNull(KEY_CAMERA_MAX_SHUTTER),
            shutterIncrements = Increment.from(cursor.getInt(KEY_CAMERA_SHUTTER_INCREMENTS)),
            exposureCompIncrements = PartialIncrement.from(cursor.getInt(
                KEY_CAMERA_EXPOSURE_COMP_INCREMENTS
            )),
            format = Format.from(cursor.getInt(KEY_CAMERA_FORMAT)),
            lens = cursor.getLongOrNull(KEY_LENS_ID)?.let(::getLens)
        )
    }

    /**
     * Gets the Camera corresponding to the camera id
     * @param cameraId the id of the Camera
     * @return the Camera corresponding to the given id
     */
    private fun getCamera(cameraId: Long): Camera? {
        val lenses = select(
            TABLE_LINK_CAMERA_LENS,
            columns = listOf(KEY_LENS_ID),
            selection = "$KEY_CAMERA_ID=?",
            selectionArgs = listOf(cameraId.toString())) { row ->
            row.getLong(KEY_LENS_ID)
        }.toHashSet()
        return selectFirstOrNull(
            TABLE_CAMERAS,
            selection = "$KEY_CAMERA_ID=?",
            selectionArgs = listOf(cameraId.toString())) { row ->
                cameraMapper(row).apply { lensIds = lenses }
            }
    }

    val cameras: List<Camera> get() {
        val lenses = select(TABLE_LINK_CAMERA_LENS) { row ->
            row.getLong(KEY_CAMERA_ID) to row.getLong(KEY_LENS_ID)
        }.groupBy(Pair<Long, Long>::first, Pair<Long, Long>::second)
        return select(
            TABLE_CAMERAS,
            orderBy = "$KEY_CAMERA_MAKE collate nocase,$KEY_CAMERA_MODEL collate nocase") { row ->
            cameraMapper(row).apply {
                lensIds = lenses[id]?.toHashSet() ?: HashSet()
            }
        }
    }

    /**
     * Deletes the specified camera from the database
     * @param camera the camera to be deleted
     */
    fun deleteCamera(camera: Camera): Int {
        // In case of fixed lens cameras, also delete the lens from database.
        camera.lens?.let(::deleteLens)
        return writableDatabase.delete(TABLE_CAMERAS, "$KEY_CAMERA_ID = ?", arrayOf(camera.id.toString()))
    }

    /**
     * Checks if a camera is being used in some roll.
     * @param camera the camera to be checked
     * @return true if the camera is in use, false if not
     */
    fun isCameraBeingUsed(camera: Camera) = selectFirstOrNull(
        TABLE_ROLLS,
        selection = "$KEY_CAMERA_ID=?",
        selectionArgs = listOf(camera.id.toString())) { true } ?: false

    /**
     * Updates the information of the specified camera.
     * @param camera the camera to be updated
     */
    fun updateCamera(camera: Camera): Int {
        // Check if the camera previously had a fixed lens.
        val previousLensId = selectFirstOrNull(
            TABLE_CAMERAS,
            columns = listOf(KEY_LENS_ID),
            selection = "$KEY_CAMERA_ID=?",
            selectionArgs = listOf(camera.id.toString())) { row ->
            row.getLong(KEY_LENS_ID)
        } ?: 0

        val database = writableDatabase
        database.beginTransaction()
        try {
            // If the camera currently has a fixed lens, update/add it to the database.
            // This needs to be done first since the cameras table references the lenses table.
            camera.lens?.let { lens ->
                lens.make = camera.make
                lens.model = camera.model
                if (updateLens(lens, database) == 0) {
                    addLens(lens, database)
                }
            }
            val contentValues = buildCameraContentValues(camera)
            val affectedRows = database.update(TABLE_CAMERAS, contentValues, "$KEY_CAMERA_ID=?", arrayOf(camera.id.toString()))
            if (affectedRows == 0) {
                // If there are no affected rows, then the camera does not yet exist
                // in the database. Returning here rolls back the transaction.
                return affectedRows
            }

            // If the camera's current lens is null, delete the old fixed lens from the database.
            if (previousLensId > 0 && camera.lens == null) {
                deleteLens(Lens(id = previousLensId), database)
            }

            // Camera and possible fixed lens were updated completely.
            // Commit transaction and return affected row count.
            database.setTransactionSuccessful()
            return affectedRows
        } finally {
            database.endTransaction()
        }
    }

    // ******************** CRUD operations for the camera-lens link table ********************
    /**
     * Adds a mountable combination of camera and lens to the database.
     * @param camera the camera that can be mounted with the lens
     * @param lens the lens that can be mounted with the camera
     */
    fun addCameraLensLink(camera: Camera, lens: Lens) {
        //Here it is safe to use a raw query, because we only use id values, which are database generated.
        //So there is no danger of SQL injection.
        val query = ("INSERT INTO " + TABLE_LINK_CAMERA_LENS + "(" + KEY_CAMERA_ID + "," + KEY_LENS_ID
                + ") SELECT " + camera.id + ", " + lens.id
                + " WHERE NOT EXISTS(SELECT 1 FROM " + TABLE_LINK_CAMERA_LENS + " WHERE "
                + KEY_CAMERA_ID + "=" + camera.id + " AND " + KEY_LENS_ID + "=" + lens.id + ");")
        writableDatabase.execSQL(query)
    }

    /**
     * Deletes a mountable combination from the database
     * @param camera the camera that can be mounted with the lens
     * @param lens the lens that can be mounted with the camera
     */
    fun deleteCameraLensLink(camera: Camera, lens: Lens) =
            writableDatabase.delete(
                TABLE_LINK_CAMERA_LENS,
                    "$KEY_CAMERA_ID = ? AND $KEY_LENS_ID = ?", arrayOf(camera.id.toString(), lens.id.toString()))

    /**
     * Gets all the lenses that can be mounted to the specified camera
     * @param camera the camera whose lenses we want to get
     * @return a List of all linked lenses
     */
    fun getLinkedLenses(camera: Camera): List<Lens> {
        val lensIds = select(
            TABLE_LINK_CAMERA_LENS,
            columns = listOf(KEY_LENS_ID),
            selection = "$KEY_CAMERA_ID = ?",
            selectionArgs = listOf(camera.id.toString())) { row ->
            row.getLong(KEY_LENS_ID)
        }
        return lensIds
            .mapNotNull(::getLens)
            .sortedWith(compareBy(Lens::make).thenBy(Lens::model))
    }

    // ******************** CRUD operations for the rolls table ********************
    /**
     * Adds a new roll to the database.
     * @param roll the roll to be added
     */
    fun addRoll(roll: Roll): Long {
        val id = writableDatabase.insert(TABLE_ROLLS, null, buildRollContentValues(roll))
        roll.id = id
        return id
    }

    /**
     * Gets all the rolls in the database
     * @return a List of all the rolls in the database
     */
    fun getRolls(filterMode: RollFilterMode?): List<Roll> {
        val selection: String? = when (filterMode) {
            RollFilterMode.ACTIVE -> "$KEY_ROLL_ARCHIVED=0"
            RollFilterMode.ARCHIVED -> "$KEY_ROLL_ARCHIVED>0"
            RollFilterMode.ALL -> null
            else -> "$KEY_ROLL_ARCHIVED=0"
        }
        return select(
            TABLE_ROLLS,
            selection = selection,
            orderBy = "$KEY_ROLL_DATE DESC") { row ->
            Roll(
                id = row.getLong(KEY_ROLL_ID),
                name = row.getStringOrNull(KEY_ROLLNAME),
                date = row.getStringOrNull(KEY_ROLL_DATE)?.let(::localDateTimeOrNull) ?: LocalDateTime.now(),
                unloaded = row.getStringOrNull(KEY_ROLL_UNLOADED)?.let(::localDateTimeOrNull),
                developed = row.getStringOrNull(KEY_ROLL_DEVELOPED)?.let(::localDateTimeOrNull),
                note = row.getStringOrNull(KEY_ROLL_NOTE),
                camera = row.getLongOrNull(KEY_CAMERA_ID)?.let(::getCamera),
                iso = row.getInt(KEY_ROLL_ISO),
                pushPull = row.getStringOrNull(KEY_ROLL_PUSH),
                format = Format.from(row.getInt(KEY_ROLL_FORMAT)),
                archived = row.getInt(KEY_ROLL_ARCHIVED) > 0,
                filmStock = row.getLongOrNull(KEY_FILM_STOCK_ID)?.let(::getFilmStock)
            )
        }
    }

    val rollCounts: Pair<Int, Int> get() {
        val (active, archived) = arrayOf("$KEY_ROLL_ARCHIVED <= 0", "$KEY_ROLL_ARCHIVED > 0").map {
            selectFirstOrNull(
                TABLE_ROLLS,
                columns = listOf("COUNT(*)"),
                selection = it) { row ->
                row.getInt(0)
            } ?: 0
        }
        return Pair(active, archived)
    }

    /**
     * Deletes a roll from the database.
     * @param roll the roll to be deleted from the database
     */
    fun deleteRoll(roll: Roll): Int =
            writableDatabase.delete(TABLE_ROLLS, "$KEY_ROLL_ID = ?", arrayOf(roll.id.toString()))

    /**
     * Updates the specified roll's information
     * @param roll the roll to be updated
     */
    fun updateRoll(roll: Roll): Int {
        val contentValues = buildRollContentValues(roll)
        return writableDatabase.update(TABLE_ROLLS, contentValues, "$KEY_ROLL_ID=?", arrayOf(roll.id.toString()))
    }

    /**
     * Gets the number of frames on a specified roll.
     * @param roll the roll whose frame count we want
     * @return an integer of the the number of frames on that roll
     */
    fun getNumberOfFrames(roll: Roll): Int = selectFirstOrNull(
        TABLE_FRAMES,
        columns = listOf("COUNT(*)"),
        selection = "$KEY_ROLL_ID=?",
        selectionArgs = listOf(roll.id.toString())) { row -> row.getInt(0) } ?: 0


    // ******************** CRUD operations for the filters table ********************
    /**
     * Adds a new filter to the database.
     * @param filter the filter to be added to the database
     */
    fun addFilter(filter: Filter): Long {
        val id = writableDatabase.insert(TABLE_FILTERS, null, buildFilterContentValues(filter))
        filter.id = id
        return id
    }

    private val filterMapper = { cursor: Cursor ->
        Filter(
            id = cursor.getLong(KEY_FILTER_ID),
            make = cursor.getStringOrNull(KEY_FILTER_MAKE),
            model = cursor.getStringOrNull(KEY_FILTER_MODEL)
        )
    }

    /**
     * Gets all the filters from the database
     * @return a List of all the filters in the database
     */
    val filters: List<Filter> get() {
        val lenses = select(TABLE_LINK_LENS_FILTER) { row ->
            row.getLong(KEY_FILTER_ID) to row.getLong(KEY_LENS_ID)
        }.groupBy(Pair<Long, Long>::first, Pair<Long, Long>::second)
        return select(
            TABLE_FILTERS,
            orderBy = "$KEY_FILTER_MAKE collate nocase,$KEY_FILTER_MODEL collate nocase") { row ->
            filterMapper(row).apply {
                lensIds = lenses[id]?.toHashSet() ?: HashSet()
            }
        }
    }

    /**
     * Deletes the specified filter from the database
     * @param filter the filter to be deleted
     */
    fun deleteFilter(filter: Filter): Int =
            writableDatabase.delete(TABLE_FILTERS, "$KEY_FILTER_ID = ?", arrayOf(filter.id.toString()))

    /**
     * Checks if a filter is being used in some roll.
     * @param filter the filter to be checked
     * @return true if the filter is in use, false if not
     */
    fun isFilterBeingUsed(filter: Filter) = selectFirstOrNull(
        TABLE_LINK_FRAME_FILTER,
        columns = listOf(KEY_FILTER_ID),
        selection = "$KEY_FILTER_ID=?",
        selectionArgs = listOf(filter.id.toString())) { true } ?: false

    /**
     * Updates the information of the specified filter.
     * @param filter the filter to be updated
     */
    fun updateFilter(filter: Filter): Int {
        val contentValues = buildFilterContentValues(filter)
        return writableDatabase.update(TABLE_FILTERS, contentValues, "$KEY_FILTER_ID=?", arrayOf(filter.id.toString()))
    }

    // ******************** CRUD operations for the lens-filter link table ********************
    /**
     * Adds a mountable combination of filter and lens to the database.
     * @param filter the filter that can be mounted with the lens
     * @param lens the lens that can be mounted with the filter
     */
    fun addLensFilterLink(filter: Filter, lens: Lens) {
        //Here it is safe to use a raw query, because we only use id values, which are database generated.
        //So there is no danger of SQL injection.
        val query = ("INSERT INTO " + TABLE_LINK_LENS_FILTER + "(" + KEY_FILTER_ID + "," + KEY_LENS_ID
                + ") SELECT " + filter.id + ", " + lens.id
                + " WHERE NOT EXISTS(SELECT 1 FROM " + TABLE_LINK_LENS_FILTER + " WHERE "
                + KEY_FILTER_ID + "=" + filter.id + " AND " + KEY_LENS_ID + "=" + lens.id + ");")
        writableDatabase.execSQL(query)
    }

    /**
     * Deletes a mountable combination from the database
     * @param filter the filter that can be mounted with the lens
     * @param lens the lens that can be mounted with the filter
     */
    fun deleteLensFilterLink(filter: Filter, lens: Lens): Int =
        writableDatabase.delete(
            TABLE_LINK_LENS_FILTER,
            "$KEY_FILTER_ID = ? AND $KEY_LENS_ID = ?",
            arrayOf(filter.id.toString(), lens.id.toString()))

    /**
     * Gets all the filters that can be mounted to the specified lens
     * @param lens the lens whose filters we want to get
     * @return a List of all linked filters
     */
    fun getLinkedFilters(lens: Lens) = select(
        TABLE_FILTERS,
            selection = "$KEY_FILTER_ID IN (SELECT $KEY_FILTER_ID FROM $TABLE_LINK_LENS_FILTER WHERE $KEY_LENS_ID = ?)",
            selectionArgs = listOf(lens.id.toString()),
            transform = filterMapper)

    // ******************** CRUD operations for the frame-filter link table ********************
    /**
     * Adds a new link between a frame and a filter object
     *
     * @param frame frame that is linked to the filter
     * @param filter filter that is linked to the frame
     */
    private fun addFrameFilterLink(frame: Frame, filter: Filter) {
        //Here it is safe to use a raw query, because we only use id values, which are database generated.
        //So there is no danger of SQL injection.
        val query = ("insert into " + TABLE_LINK_FRAME_FILTER + " ("
                + KEY_FRAME_ID + ", " + KEY_FILTER_ID + ") "
                + "select " + frame.id + ", " + filter.id + " "
                + "where not exists (select * from " + TABLE_LINK_FRAME_FILTER + " "
                + "where " + KEY_FRAME_ID + " = " + frame.id + " and "
                + KEY_FILTER_ID + " = " + filter.id + ");")
        writableDatabase.execSQL(query)
    }

    /**
     * Deletes all links between a single frame and all its linked filters.
     *
     * @param frame Frame object whose filter links should be deleted
     */
    private fun deleteFrameFilterLinks(frame: Frame): Int =
            writableDatabase.delete(TABLE_LINK_FRAME_FILTER, "$KEY_FRAME_ID = ?", arrayOf(frame.id.toString()))

    /**
     * Gets all filter objects that are linked to a specific Frame object
     *
     * @param frame the Frame object whose linked filters we want to get
     * @return List object containing all Filter objects linked to the specified Frame object
     */
    private fun getLinkedFilters(frame: Frame) = select(
        TABLE_FILTERS,
        selection = "$KEY_FILTER_ID IN (SELECT $KEY_FILTER_ID FROM $TABLE_LINK_FRAME_FILTER WHERE $KEY_FRAME_ID = ?)",
        selectionArgs = listOf(frame.id.toString()),
        transform = filterMapper)

    // ******************** CRUD operations for the film stock table ********************

    fun addFilmStock(filmStock: FilmStock): Long {
        val id = writableDatabase.insert(TABLE_FILM_STOCKS, null, buildFilmStockContentValues(filmStock))
        filmStock.id = id
        return id
    }

    private val filmStockMapper = { row: Cursor ->
        FilmStock(
            id = row.getLong(KEY_FILM_STOCK_ID),
            make = row.getStringOrNull(KEY_FILM_MANUFACTURER_NAME),
            model = row.getStringOrNull(KEY_FILM_STOCK_NAME),
            iso = row.getInt(KEY_FILM_ISO),
            type = FilmType.from(row.getInt(KEY_FILM_TYPE)),
            process = FilmProcess.from(row.getInt(KEY_FILM_PROCESS)),
            isPreadded = row.getInt(KEY_FILM_IS_PREADDED) > 0
        )
    }

    private fun getFilmStock(filmStockId: Long) = selectFirstOrNull(
        TABLE_FILM_STOCKS,
        selection = "$KEY_FILM_STOCK_ID=?",
        selectionArgs = listOf(filmStockId.toString()),
        transform = filmStockMapper)

    val filmStocks: List<FilmStock> get() = select(
        TABLE_FILM_STOCKS,
        orderBy = "$KEY_FILM_MANUFACTURER_NAME collate nocase,$KEY_FILM_STOCK_NAME collate nocase",
        transform = filmStockMapper)

    fun isFilmStockBeingUsed(filmStock: FilmStock) = selectFirstOrNull(
        TABLE_ROLLS,
        selection = "$KEY_FILM_STOCK_ID=?",
        selectionArgs = listOf(filmStock.id.toString())) { true } ?: false


    fun deleteFilmStock(filmStock: FilmStock) =
            writableDatabase.delete(TABLE_FILM_STOCKS, "$KEY_FILM_STOCK_ID=?", arrayOf(filmStock.id.toString()))

    fun updateFilmStock(filmStock: FilmStock): Int {
        val contentValues = buildFilmStockContentValues(filmStock)
        return writableDatabase.update(TABLE_FILM_STOCKS, contentValues, "$KEY_FILM_STOCK_ID=?", arrayOf(filmStock.id.toString()))
    }


    //*********************** METHODS TO BUILD CONTENT VALUES **********************************
    /**
     * Builds ContentValues container from a Frame object.
     *
     * @param frame Frame object of which the ContentValues is created.
     * @return ContentValues containing the attributes of the Frame object.
     */
    private fun buildFrameContentValues(frame: Frame) = ContentValues().apply {
        put(KEY_ROLL_ID, frame.roll.id)
        put(KEY_COUNT, frame.count)
        put(KEY_DATE, frame.date.sortableDateTime)

        val lens = frame.lens
        if (lens != null) put(KEY_LENS_ID, lens.id)
        else putNull(KEY_LENS_ID)

        put(KEY_SHUTTER, frame.shutter)
        put(KEY_APERTURE, frame.aperture)
        put(KEY_FRAME_NOTE, frame.note)
        put(KEY_LOCATION, frame.location?.decimalString)
        put(KEY_FOCAL_LENGTH, frame.focalLength)
        put(KEY_EXPOSURE_COMP, frame.exposureComp)
        put(KEY_NO_OF_EXPOSURES, frame.noOfExposures)
        put(KEY_FLASH_USED, frame.flashUsed)
        put(KEY_FLASH_POWER, frame.flashPower)
        put(KEY_FLASH_COMP, frame.flashComp)
        put(KEY_METERING_MODE, frame.meteringMode)
        put(KEY_FORMATTED_ADDRESS, frame.formattedAddress)
        put(KEY_PICTURE_FILENAME, frame.pictureFilename)
        put(KEY_LIGHT_SOURCE, frame.lightSource.ordinal)
    }

    /**
     * Builds ContentValues container from a Lens object.
     *
     * @param lens Lens object of which the ContentValues is created.
     * @return ContentValues containing the attributes of the lens object.
     */
    private fun buildLensContentValues(lens: Lens) = ContentValues().apply {
        put(KEY_LENS_MAKE, lens.make)
        put(KEY_LENS_MODEL, lens.model)
        put(KEY_LENS_SERIAL_NO, lens.serialNumber)
        put(KEY_LENS_MIN_APERTURE, lens.minAperture)
        put(KEY_LENS_MAX_APERTURE, lens.maxAperture)
        put(KEY_LENS_MIN_FOCAL_LENGTH, lens.minFocalLength)
        put(KEY_LENS_MAX_FOCAL_LENGTH, lens.maxFocalLength)
        put(KEY_LENS_APERTURE_INCREMENTS, lens.apertureIncrements.ordinal)
        put(KEY_LENS_CUSTOM_APERTURE_VALUES, Json.encodeToString(lens.customApertureValues))
    }

    /**
     * Builds ContentValues container from a Camera object.
     *
     * @param camera Camera object of which the ContentValues is created.
     * @return ContentValues containing the attributes of the Camera object.
     */
    private fun buildCameraContentValues(camera: Camera) = ContentValues().apply {
        put(KEY_CAMERA_MAKE, camera.make)
        put(KEY_CAMERA_MODEL, camera.model)
        put(KEY_CAMERA_SERIAL_NO, camera.serialNumber)
        put(KEY_CAMERA_MIN_SHUTTER, camera.minShutter)
        put(KEY_CAMERA_MAX_SHUTTER, camera.maxShutter)
        put(KEY_CAMERA_SHUTTER_INCREMENTS, camera.shutterIncrements.ordinal)
        put(KEY_CAMERA_EXPOSURE_COMP_INCREMENTS, camera.exposureCompIncrements.ordinal)
        put(KEY_CAMERA_FORMAT, camera.format.ordinal)
        val lens = camera.lens
        if (lens != null) put(KEY_LENS_ID, lens.id)
        else putNull(KEY_LENS_ID)
    }

    /**
     * Builds ContentValues container from a Roll object.
     *
     * @param roll Roll object of which the ContentValues is created.
     * @return ContentValues containing the attributes of the Roll object.
     */
    private fun buildRollContentValues(roll: Roll) = ContentValues().apply {
        put(KEY_ROLLNAME, roll.name)
        put(KEY_ROLL_DATE, roll.date.sortableDateTime)
        put(KEY_ROLL_NOTE, roll.note)

        val camera = roll.camera
        if (camera != null) put(KEY_CAMERA_ID, camera.id)
        else putNull(KEY_CAMERA_ID)

        put(KEY_ROLL_ISO, roll.iso)
        put(KEY_ROLL_PUSH, roll.pushPull)
        put(KEY_ROLL_FORMAT, roll.format.ordinal)
        put(KEY_ROLL_ARCHIVED, roll.archived)

        val filmStock = roll.filmStock
        if (filmStock != null) put(KEY_FILM_STOCK_ID, filmStock.id)
        else putNull(KEY_FILM_STOCK_ID)

        put(KEY_ROLL_UNLOADED, roll.unloaded?.sortableDateTime)
        put(KEY_ROLL_DEVELOPED, roll.developed?.sortableDateTime)
    }

    /**
     * Builds ContentValues container from a Filter object.
     *
     * @param filter Filter object of which the ContentValues is created.
     * @return ContentValues containing the attributes of the Filter object.
     */
    private fun buildFilterContentValues(filter: Filter) = ContentValues().apply {
        put(KEY_FILTER_MAKE, filter.make)
        put(KEY_FILTER_MODEL, filter.model)
    }

    /**
     * Builds ContentValues container from a FilmStock object
     *
     * @param filmStock FilmStock object of which the ContentValues is created
     * @return ContentValues containing the attributes of the FilmStock object
     */
    private fun buildFilmStockContentValues(filmStock: FilmStock) = ContentValues().apply {
        put(KEY_FILM_MANUFACTURER_NAME, filmStock.make)
        put(KEY_FILM_STOCK_NAME, filmStock.model)
        put(KEY_FILM_ISO, filmStock.iso)
        put(KEY_FILM_TYPE, filmStock.type.ordinal)
        put(KEY_FILM_PROCESS, filmStock.process.ordinal)
        put(KEY_FILM_IS_PREADDED, filmStock.isPreadded)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        // The only time foreign key constraints are enforced, is when something is written
        // to the database. Only enable foreign keys, if the database may be written to.
        if (!db.isReadOnly) {
            db.execSQL("PRAGMA foreign_keys=ON;")
        }
    }

    override fun onCreate(database: SQLiteDatabase) {
        // Enable foreign key support, since we aren't overriding onConfigure() (added in API 16).
        database.execSQL("PRAGMA foreign_keys=ON;")
        database.execSQL(CREATE_FILM_STOCKS_TABLE)
        database.execSQL(CREATE_LENS_TABLE)
        database.execSQL(CREATE_CAMERA_TABLE)
        database.execSQL(CREATE_FILTER_TABLE)
        database.execSQL(CREATE_ROLL_TABLE)
        database.execSQL(CREATE_FRAME_TABLE)
        database.execSQL(CREATE_LINK_CAMERA_LENS_TABLE)
        database.execSQL(CREATE_LINK_LENS_FILTER_TABLE)
        database.execSQL(CREATE_LINK_FRAME_FILTER_TABLE)
        populateFilmStocks(database)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Run all the required upgrade scripts consecutively.
        // New if blocks should be added whenever the database version is raised and new
        // columns and/or tables are added.
        if (oldVersion < 14) {
            //TABLE_FRAMES
            db.execSQL(ALTER_TABLE_FRAMES_1)
            db.execSQL(ALTER_TABLE_FRAMES_2)
            db.execSQL(ALTER_TABLE_FRAMES_3)
            db.execSQL(ALTER_TABLE_FRAMES_4)
            db.execSQL(ALTER_TABLE_FRAMES_5)
            db.execSQL(ALTER_TABLE_FRAMES_6)
            db.execSQL(ALTER_TABLE_FRAMES_7)
            db.execSQL(ALTER_TABLE_FRAMES_8)
            db.execSQL(ALTER_TABLE_FRAMES_9)
            //TABLE_LENSES
            db.execSQL(ALTER_TABLE_LENSES_1)
            db.execSQL(ALTER_TABLE_LENSES_2)
            db.execSQL(ALTER_TABLE_LENSES_3)
            db.execSQL(ALTER_TABLE_LENSES_4)
            db.execSQL(ALTER_TABLE_LENSES_5)
            db.execSQL(ALTER_TABLE_LENSES_6)
            //TABLE_CAMERAS
            db.execSQL(ALTER_TABLE_CAMERAS_1)
            db.execSQL(ALTER_TABLE_CAMERAS_2)
            db.execSQL(ALTER_TABLE_CAMERAS_3)
            db.execSQL(ALTER_TABLE_CAMERAS_4)
            //TABLE_ROLLS
            db.execSQL(ALTER_TABLE_ROLLS_1)
            db.execSQL(ALTER_TABLE_ROLLS_2)
            db.execSQL(ALTER_TABLE_ROLLS_3)
            //In an earlier version special chars were not allowed.
            //Instead quote marks were changed to 'q' when stored in the SQLite database.
            db.execSQL(REPLACE_QUOTE_CHARS)
            //TABLE_FILTERS
            db.execSQL(ON_UPGRADE_CREATE_FILTER_TABLE)
            //TABLE MOUNTABLES
            db.execSQL(ON_UPGRADE_CREATE_LINK_LENS_FILTER_TABLE)
        }
        if (oldVersion < 15) {
            db.execSQL(ALTER_TABLE_FRAMES_10)
        }
        if (oldVersion < 16) {
            db.execSQL(ALTER_TABLE_ROLLS_4)
        }
        if (oldVersion < 17) {
            db.execSQL(ALTER_TABLE_FRAMES_11)
        }
        if (oldVersion < 18) {
            db.execSQL(ALTER_TABLE_CAMERAS_5)
        }
        if (oldVersion < 19) {
            // Enable foreign key support, since we aren't overriding onConfigure() (added in API 16).
            db.execSQL("PRAGMA foreign_keys=ON;")
            // Alter statements
            db.beginTransaction()
            try {
                // execSQL() does not support multiple SQL commands separated with a semi-colon.
                // Separate the upgrade commands into single SQL commands.
                db.execSQL(ROLLS_TABLE_REVISION_1)
                db.execSQL(ROLLS_TABLE_REVISION_2)
                db.execSQL(ROLLS_TABLE_REVISION_3)
                db.execSQL(ROLLS_TABLE_REVISION_4)
                db.execSQL(FRAMES_TABLE_REVISION_1)
                db.execSQL(FRAMES_TABLE_REVISION_2)
                db.execSQL(FRAMES_TABLE_REVISION_3)
                db.execSQL(ON_UPGRADE_CREATE_LINK_FRAME_FILTER_TABLE)
                db.execSQL(FRAMES_TABLE_REVISION_4)
                db.execSQL(FRAMES_TABLE_REVISION_5)
                db.execSQL(CAMERA_LENS_LINK_TABLE_REVISION_1)
                db.execSQL(CAMERA_LENS_LINK_TABLE_REVISION_2)
                db.execSQL(CAMERA_LENS_LINK_TABLE_REVISION_3)
                db.execSQL(CAMERA_LENS_LINK_TABLE_REVISION_4)
                db.execSQL(LENS_FILTER_LINK_TABLE_REVISION_1)
                db.execSQL(LENS_FILTER_LINK_TABLE_REVISION_2)
                db.execSQL(LENS_FILTER_LINK_TABLE_REVISION_3)
                db.execSQL(LENS_FILTER_LINK_TABLE_REVISION_4)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        if (oldVersion < 20) {
            db.execSQL(CREATE_FILM_STOCKS_TABLE)
            db.execSQL(ALTER_TABLE_FRAMES_12)
            db.execSQL(ALTER_TABLE_ROLLS_5)
            populateFilmStocks(db)
        }
        if (oldVersion < 21) {
            db.execSQL(ALTER_TABLE_ROLLS_6)
            db.execSQL(ALTER_TABLE_ROLLS_7)
        }
        if (oldVersion < 22) {
            db.execSQL(ALTER_TABLE_CAMERAS_6)
        }
        if (oldVersion < 23) {
            db.execSQL(ALTER_TABLE_CAMERAS_7)
        }
        if (oldVersion < 24) {
            db.execSQL(ALTER_TABLE_LENSES_7)
        }
    }

    /**
     * Imports a .db database file to be used as the database for this app.
     * If the new database file cannot be copied, then throw IOException.
     * If it cannot be opened as an SQLite database, return false and notify.
     * If the file is corrupted, then it is discarded and the old database will be used.
     * Return false and notify.
     *
     * @param context the application's context
     * @param importDatabasePath the path to the file which should be imported
     * @return true if the database was imported successfully
     * @throws IOException is thrown if the new database file cannot be opened
     */
    @Throws(IOException::class)
    fun importDatabase(context: Context, importDatabasePath: String): Boolean {
        val newDb = File(importDatabasePath)
        val oldDbBackup = File(context.getDatabasePath(DATABASE_NAME).absolutePath + "_backup")
        val oldDb = getDatabaseFile(context)
        if (newDb.exists()) {
            close()

            //Backup the old database file in case the new file is corrupted.
            oldDb.copyTo(oldDbBackup, overwrite = true)

            //Replace the old database file with the new one.
            newDb.copyTo(oldDb, overwrite = true)

            // Access the copied database so SQLiteHelper will cache it and mark it as created.
            val success = booleanArrayOf(true)
            try {
                SQLiteDatabase.openDatabase(
                    getDatabaseFile(context).absolutePath, null,
                        SQLiteDatabase.OPEN_READWRITE
                ) {
                    // If the database was corrupt, try to replace with the old backup.
                    try {
                        oldDbBackup.copyTo(oldDb, overwrite = true)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                    success[0] = false
                }
            } catch (e: SQLiteException) {
                Toast.makeText(context, context.resources.getString(R.string.CouldNotReadDatabase),
                        Toast.LENGTH_LONG).show()
                return false
            }
            if (!success[0]) {
                // If the new database file was corrupt, let the user know.
                Toast.makeText(context, context.resources.getString(R.string.CouldNotReadDatabase),
                        Toast.LENGTH_LONG).show()
                return false
            }
            if (!runIntegrityCheck()) {
                //If the new database file failed the integrity check, replace it with the backup.
                close()
                oldDbBackup.copyTo(oldDb, overwrite = true)
                Toast.makeText(context, context.resources.getString(R.string.IntegrityCheckFailed),
                        Toast.LENGTH_LONG).show()
                return false
            }
            return true
        }
        return false
    }

    /**
     * Check the integrity of the current database.
     * The database is valid if it can be used with this app.
     *
     * The integrity of the database is checked AFTER a new database is imported.
     * New columns added during onUpgrade() will be present, which is why
     * checkColumnProperties() should be run for all new columns as well.
     *
     * @return true if the database is a valid database to be used with this app
     */
    private fun runIntegrityCheck(): Boolean {
        val integer = "int"
        val text = "text"
        val cascade = "CASCADE"
        val setNull = "SET NULL"
        //Run integrity checks to see if the current database is whole
        return checkColumnProperties(
            TABLE_CAMERAS, KEY_CAMERA_ID, integer, 0,
                primaryKeyInput = true, autoIncrementInput = true) &&
                checkColumnProperties(TABLE_CAMERAS, KEY_CAMERA_MAKE, text, 1) &&
                checkColumnProperties(TABLE_CAMERAS, KEY_CAMERA_MODEL, text, 1) &&
                checkColumnProperties(TABLE_CAMERAS, KEY_CAMERA_MAX_SHUTTER, text, 0) &&
                checkColumnProperties(TABLE_CAMERAS, KEY_CAMERA_MIN_SHUTTER, text, 0) &&
                checkColumnProperties(TABLE_CAMERAS, KEY_CAMERA_SERIAL_NO, text, 0) &&
                checkColumnProperties(TABLE_CAMERAS, KEY_CAMERA_SHUTTER_INCREMENTS, integer, 1) &&
                checkColumnProperties(TABLE_CAMERAS, KEY_CAMERA_EXPOSURE_COMP_INCREMENTS, integer, 1) &&
                checkColumnProperties(
                    TABLE_CAMERAS, KEY_LENS_ID, integer, 0,
                    primaryKeyInput = false, autoIncrementInput = false, foreignKeyInput = true,
                    referenceTableNameInput = TABLE_LENSES, onDeleteActionInput = setNull) &&
                checkColumnProperties(TABLE_CAMERAS, KEY_CAMERA_FORMAT, integer, 0) &&
                checkColumnProperties(
                    TABLE_LENSES, KEY_LENS_ID, integer, 0,
                        primaryKeyInput = true, autoIncrementInput = true) &&
                checkColumnProperties(TABLE_LENSES, KEY_LENS_MAKE, text, 1) &&
                checkColumnProperties(TABLE_LENSES, KEY_LENS_MODEL, text, 1) &&
                checkColumnProperties(TABLE_LENSES, KEY_LENS_MAX_APERTURE, text, 0) &&
                checkColumnProperties(TABLE_LENSES, KEY_LENS_MIN_APERTURE, text, 0) &&
                checkColumnProperties(TABLE_LENSES, KEY_LENS_MAX_FOCAL_LENGTH, integer, 0) &&
                checkColumnProperties(TABLE_LENSES, KEY_LENS_MIN_FOCAL_LENGTH, integer, 0) &&
                checkColumnProperties(TABLE_LENSES, KEY_LENS_SERIAL_NO, text, 0) &&
                checkColumnProperties(TABLE_LENSES, KEY_LENS_APERTURE_INCREMENTS, integer, 1) &&
                checkColumnProperties(TABLE_LENSES, KEY_LENS_CUSTOM_APERTURE_VALUES, text, 0) &&
                checkColumnProperties(
                    TABLE_FILTERS, KEY_FILTER_ID, integer, 0,
                        primaryKeyInput = true, autoIncrementInput = true) &&
                checkColumnProperties(TABLE_FILTERS, KEY_FILTER_MAKE, text, 1) &&
                checkColumnProperties(TABLE_FILTERS, KEY_FILTER_MODEL, text, 1) &&
                checkColumnProperties(
                    TABLE_LINK_CAMERA_LENS, KEY_CAMERA_ID, integer, 1,
                        primaryKeyInput = true, autoIncrementInput = false, foreignKeyInput = true,
                        referenceTableNameInput = TABLE_CAMERAS, onDeleteActionInput = cascade) &&
                checkColumnProperties(
                    TABLE_LINK_CAMERA_LENS, KEY_LENS_ID, integer, 1,
                        primaryKeyInput = true, autoIncrementInput = false, foreignKeyInput = true,
                        referenceTableNameInput = TABLE_LENSES, onDeleteActionInput = cascade) &&
                checkColumnProperties(
                    TABLE_LINK_LENS_FILTER, KEY_LENS_ID, integer, 1,
                        primaryKeyInput = true, autoIncrementInput = false, foreignKeyInput = true,
                        referenceTableNameInput = TABLE_LENSES, onDeleteActionInput = cascade) &&
                checkColumnProperties(
                    TABLE_LINK_LENS_FILTER, KEY_FILTER_ID, integer, 1,
                        primaryKeyInput = true, autoIncrementInput = false, foreignKeyInput = true,
                        referenceTableNameInput = TABLE_FILTERS, onDeleteActionInput = cascade) &&
                checkColumnProperties(
                    TABLE_LINK_FRAME_FILTER, KEY_FRAME_ID, integer, 1,
                        primaryKeyInput = true, autoIncrementInput = false, foreignKeyInput = true,
                        referenceTableNameInput = TABLE_FRAMES, onDeleteActionInput = cascade) &&
                checkColumnProperties(
                    TABLE_LINK_FRAME_FILTER, KEY_FILTER_ID, integer, 1,
                        primaryKeyInput = true, autoIncrementInput = false, foreignKeyInput = true,
                        referenceTableNameInput = TABLE_FILTERS, onDeleteActionInput = cascade) &&
                checkColumnProperties(
                    TABLE_ROLLS, KEY_ROLL_ID, integer, 0,
                        primaryKeyInput = true, autoIncrementInput = true) &&
                checkColumnProperties(TABLE_ROLLS, KEY_ROLLNAME, text, 1) &&
                checkColumnProperties(TABLE_ROLLS, KEY_ROLL_DATE, text, 1) &&
                checkColumnProperties(TABLE_ROLLS, KEY_ROLL_NOTE, text, 0) &&
                checkColumnProperties(
                    TABLE_ROLLS, KEY_CAMERA_ID, integer, 0,
                        primaryKeyInput = false, autoIncrementInput = false, foreignKeyInput = true,
                        referenceTableNameInput = TABLE_CAMERAS, onDeleteActionInput = setNull) &&
                checkColumnProperties(TABLE_ROLLS, KEY_ROLL_ISO, integer, 0) &&
                checkColumnProperties(TABLE_ROLLS, KEY_ROLL_PUSH, text, 0) &&
                checkColumnProperties(TABLE_ROLLS, KEY_ROLL_FORMAT, integer, 0) &&
                checkColumnProperties(TABLE_ROLLS, KEY_ROLL_ARCHIVED, integer, 1) &&
                checkColumnProperties(TABLE_ROLLS, KEY_FILM_STOCK_ID, integer, 0) &&
                checkColumnProperties(TABLE_ROLLS, KEY_ROLL_UNLOADED, text, 0) &&
                checkColumnProperties(TABLE_ROLLS, KEY_ROLL_DEVELOPED, text, 0) &&
                checkColumnProperties(
                    TABLE_FRAMES, KEY_FRAME_ID, integer, 0,
                        primaryKeyInput = true, autoIncrementInput = true) &&
                checkColumnProperties(
                    TABLE_FRAMES, KEY_ROLL_ID, integer, 1,
                        primaryKeyInput = false, autoIncrementInput = false, foreignKeyInput = true,
                        referenceTableNameInput = TABLE_ROLLS, onDeleteActionInput = cascade) &&
                checkColumnProperties(TABLE_FRAMES, KEY_COUNT, integer, 1) &&
                checkColumnProperties(TABLE_FRAMES, KEY_DATE, text, 1) &&
                checkColumnProperties(
                    TABLE_FRAMES, KEY_LENS_ID, integer, 0,
                        primaryKeyInput = false, autoIncrementInput = false, foreignKeyInput = true,
                        referenceTableNameInput = TABLE_LENSES, onDeleteActionInput = setNull) &&
                checkColumnProperties(TABLE_FRAMES, KEY_SHUTTER, text, 0) &&
                checkColumnProperties(TABLE_FRAMES, KEY_APERTURE, text, 0) &&
                checkColumnProperties(TABLE_FRAMES, KEY_FRAME_NOTE, text, 0) &&
                checkColumnProperties(TABLE_FRAMES, KEY_LOCATION, text, 0) &&
                checkColumnProperties(TABLE_FRAMES, KEY_FOCAL_LENGTH, integer, 0) &&
                checkColumnProperties(TABLE_FRAMES, KEY_EXPOSURE_COMP, text, 0) &&
                checkColumnProperties(TABLE_FRAMES, KEY_NO_OF_EXPOSURES, integer, 0) &&
                checkColumnProperties(TABLE_FRAMES, KEY_FLASH_USED, integer, 0) &&
                checkColumnProperties(TABLE_FRAMES, KEY_FLASH_POWER, text, 0) &&
                checkColumnProperties(TABLE_FRAMES, KEY_FLASH_COMP, text, 0) &&
                checkColumnProperties(TABLE_FRAMES, KEY_FRAME_SIZE, text, 0) &&
                checkColumnProperties(TABLE_FRAMES, KEY_METERING_MODE, integer, 0) &&
                checkColumnProperties(TABLE_FRAMES, KEY_FORMATTED_ADDRESS, text, 0) &&
                checkColumnProperties(TABLE_FRAMES, KEY_PICTURE_FILENAME, text, 0) &&
                checkColumnProperties(TABLE_FRAMES, KEY_LIGHT_SOURCE, integer, 0) &&
                checkColumnProperties(
                    TABLE_FILM_STOCKS, KEY_FILM_STOCK_ID, integer, 0,
                        primaryKeyInput = true, autoIncrementInput = true) &&
                checkColumnProperties(TABLE_FILM_STOCKS, KEY_FILM_STOCK_NAME, text, 1) &&
                checkColumnProperties(TABLE_FILM_STOCKS, KEY_FILM_MANUFACTURER_NAME, text, 1) &&
                checkColumnProperties(TABLE_FILM_STOCKS, KEY_FILM_ISO, integer, 0) &&
                checkColumnProperties(TABLE_FILM_STOCKS, KEY_FILM_TYPE, integer, 0) &&
                checkColumnProperties(TABLE_FILM_STOCKS, KEY_FILM_PROCESS, integer, 0) &&
                checkColumnProperties(TABLE_FILM_STOCKS, KEY_FILM_IS_PREADDED, integer, 0)
    }
    /**
     * Checks the properties of a table column. If all the parameters match to the
     * database's properties, then the method returns true.
     *
     * @param tableNameInput the name of the table
     * @param columnNameInput the name of the column in the table
     * @param columnTypeInput the data type of the column
     * @param notNullInput 1 if the column should be 'not null', 0 if null
     * @param primaryKeyInput 1 if the column should be a primary key, 0 if not
     * @param autoIncrementInput true if the table should be autoincrement
     * @return true if the parameter properties match the database
     */
    private fun checkColumnProperties(tableNameInput: String, columnNameInput: String, columnTypeInput: String,
                                      notNullInput: Int, primaryKeyInput: Boolean = false, autoIncrementInput: Boolean = false,
                                      foreignKeyInput: Boolean = false, referenceTableNameInput: String? = null,
                                      onDeleteActionInput: String? = null): Boolean {
        val db = this.readableDatabase

        //Check for possible autoincrement
        if (autoIncrementInput) {
            // We can check that the table is autoincrement from the master tables.
            // Column 'sql' is the query with which the table was created.
            // If a table is autoincrement, then it can only have one primary key.
            // If the primary key matches, then also the autoincrement column is correct.
            // The primary key will be checked later in this method.
            val incrementQuery = "SELECT * FROM sqlite_master WHERE type = 'table' AND name = '$tableNameInput' AND sql LIKE '%AUTOINCREMENT%'"
            val incrementCursor = db.rawQuery(incrementQuery, null)
            if (!incrementCursor.moveToFirst()) {
                //No rows were returned. The table has no autoincrement. Integrity check fails.
                incrementCursor.close()
                return false
            }
            incrementCursor.close()
        }

        //Check for possible foreign key reference
        if (foreignKeyInput) {
            // We can check that the column is a foreign key column using one of the SQLite pragma statements.
            val foreignKeyQuery = "PRAGMA FOREIGN_KEY_LIST('$tableNameInput')"
            val foreignKeyCursor = db.rawQuery(foreignKeyQuery, null)
            var foreignKeyFound = false
            //Iterate through the tables foreign key columns and get the properties.
            while (foreignKeyCursor.moveToNext()) {
                val table = foreignKeyCursor.getString(foreignKeyCursor.getColumnIndexOrThrow("table"))
                val from = foreignKeyCursor.getString(foreignKeyCursor.getColumnIndexOrThrow("from"))
                val onDelete = foreignKeyCursor.getString(foreignKeyCursor.getColumnIndexOrThrow("on_delete"))
                val to = foreignKeyCursor.getString(foreignKeyCursor.getColumnIndexOrThrow("to"))
                //If the table, from-column and on-delete actions match to those defined
                //by the parameters, the foreign key is correct. The to-column value
                //should be null, because during table creation we have used the shorthand form
                //to reference the parent table's primary key.
                if (table == referenceTableNameInput && from == columnNameInput
                        && onDelete.equals(onDeleteActionInput, ignoreCase = true) && to == null) {
                    foreignKeyFound = true
                    break
                }
            }
            foreignKeyCursor.close()
            //If foreign key was not defined, integrity check fails -> return false.
            if (!foreignKeyFound) return false
        }
        val query = "PRAGMA TABLE_INFO('$tableNameInput');"
        val cursor = db.rawQuery(query, null)

        //Iterate the result rows...
        while (cursor.moveToNext()) {
            val columnName = cursor.getString(cursor.getColumnIndexOrThrow("name"))
            // ...until the name checks.
            if (columnName == columnNameInput) {
                val columnType = cursor.getString(cursor.getColumnIndexOrThrow("type"))
                val notNull = cursor.getInt(cursor.getColumnIndexOrThrow("notnull"))
                //If the column is defined as primary key, the pk value is 1.
                val primaryKey = cursor.getInt(cursor.getColumnIndexOrThrow("pk")) > 0
                cursor.close()

                //Check that the attributes are correct and return the result
                return columnType.startsWith(columnTypeInput, ignoreCase = true) && //type can be int or integer
                        notNull == notNullInput && primaryKey == primaryKeyInput
            }
        }
        //We get here if no matching column names were found
        cursor.close()
        return false
    }

    /**
     * Populate films from resource arrays to the database
     */
    private fun populateFilmStocks(db: SQLiteDatabase) {
        val filmStocks = context.resources.getStringArray(R.array.FilmStocks)
        var counter = 0
        for (s in filmStocks) {
            try {
                val components = s.split(",")
                val filmStock = FilmStock()
                filmStock.make = components[0]
                filmStock.model = components[1]
                filmStock.iso = components[2].toInt()
                filmStock.type = FilmType.from(components[3].toInt())
                filmStock.process = FilmProcess.from(components[4].toInt())
                filmStock.isPreadded = true
                val values = buildFilmStockContentValues(filmStock)

                // Insert film stocks if they do not already exist.
                val cursor = db.query(
                    TABLE_FILM_STOCKS, null,
                        "$KEY_FILM_MANUFACTURER_NAME=? AND $KEY_FILM_STOCK_NAME=?",
                        arrayOf(filmStock.make, filmStock.model), null, null, null)
                if (cursor.moveToFirst()) {
                    cursor.close()
                } else {
                    db.insert(TABLE_FILM_STOCKS, null, values)
                }
            } catch (e: ArrayIndexOutOfBoundsException) {
                counter++
            } catch (e: NumberFormatException) {
                counter++
            }
        }
        if (counter > 0) {
            Toast.makeText(context, R.string.ErrorAddingFilmStocks, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        /**
         * Returns a File object referencing the .db database file used to store this database.
         *
         * @param context the application's context
         * @return File referencing the database file used by this SQLite database
         */
        fun getDatabaseFile(context: Context): File {
            val databasePath = context.getDatabasePath(DATABASE_NAME).absolutePath
            return File(databasePath)
        }

        //=============================================================================================
        //Table names
        private const val TABLE_FRAMES = "frames"
        private const val TABLE_LENSES = "lenses"
        private const val TABLE_ROLLS = "rolls"
        private const val TABLE_CAMERAS = "cameras"
        private const val TABLE_LINK_CAMERA_LENS = "link_camera_lens"

        //Added in database version 14
        private const val TABLE_FILTERS = "filters"
        private const val TABLE_LINK_LENS_FILTER = "link_lens_filter"

        //Added in database version 19
        private const val TABLE_LINK_FRAME_FILTER = "link_frame_filter"

        //Added in database version 20
        private const val TABLE_FILM_STOCKS = "film_stocks"

        //=============================================================================================
        //Column names
        //Frame
        private const val KEY_FRAME_ID = "frame_id"
        private const val KEY_COUNT = "count"
        private const val KEY_DATE = "date"
        private const val KEY_SHUTTER = "shutter"
        private const val KEY_APERTURE = "aperture"
        private const val KEY_FRAME_NOTE = "frame_note"
        private const val KEY_LOCATION = "location"

        //Added in database version 14
        private const val KEY_FOCAL_LENGTH = "focal_length"
        private const val KEY_EXPOSURE_COMP = "exposure_comp"
        private const val KEY_NO_OF_EXPOSURES = "no_of_exposures"
        private const val KEY_FLASH_USED = "flash_used"
        private const val KEY_FLASH_POWER = "flash_power"
        private const val KEY_FLASH_COMP = "flash_comp"
        private const val KEY_FRAME_SIZE = "frame_size"
        private const val KEY_METERING_MODE = "metering_mode"

        //Added in database version 15
        private const val KEY_FORMATTED_ADDRESS = "formatted_address"

        //Added in database version 17
        private const val KEY_PICTURE_FILENAME = "picture_filename"

        // Added in database version 20
        private const val KEY_LIGHT_SOURCE = "light_source"

        //Lens
        private const val KEY_LENS_ID = "lens_id"
        private const val KEY_LENS_MAKE = "lens_make"
        private const val KEY_LENS_MODEL = "lens_model"

        //Added in database version 14
        private const val KEY_LENS_MAX_APERTURE = "lens_max_aperture"
        private const val KEY_LENS_MIN_APERTURE = "lens_min_aperture"
        private const val KEY_LENS_MAX_FOCAL_LENGTH = "lens_max_focal_length"
        private const val KEY_LENS_MIN_FOCAL_LENGTH = "lens_min_focal_length"
        private const val KEY_LENS_SERIAL_NO = "lens_serial_no"
        private const val KEY_LENS_APERTURE_INCREMENTS = "aperture_increments"
        private const val KEY_LENS_CUSTOM_APERTURE_VALUES = "custom_aperture_values"

        //Camera
        private const val KEY_CAMERA_ID = "camera_id"
        private const val KEY_CAMERA_MAKE = "camera_make"
        private const val KEY_CAMERA_MODEL = "camera_model"
        private const val KEY_CAMERA_FORMAT = "camera_format"

        //Added in database version 14
        private const val KEY_CAMERA_MAX_SHUTTER = "camera_max_shutter"
        private const val KEY_CAMERA_MIN_SHUTTER = "camera_min_shutter"
        private const val KEY_CAMERA_SERIAL_NO = "camera_serial_no"
        private const val KEY_CAMERA_SHUTTER_INCREMENTS = "shutter_increments"

        //Added in database version 18
        private const val KEY_CAMERA_EXPOSURE_COMP_INCREMENTS = "exposure_comp_increments"

        //Roll
        private const val KEY_ROLL_ID = "roll_id"
        private const val KEY_ROLLNAME = "rollname"
        private const val KEY_ROLL_DATE = "roll_date"
        private const val KEY_ROLL_NOTE = "roll_note"

        //Added in database version 14
        private const val KEY_ROLL_ISO = "roll_iso"
        private const val KEY_ROLL_PUSH = "roll_push"
        private const val KEY_ROLL_FORMAT = "roll_format"

        //Added in database version 16
        private const val KEY_ROLL_ARCHIVED = "roll_archived"

        //Added in database version 21
        private const val KEY_ROLL_UNLOADED = "roll_unloaded"
        private const val KEY_ROLL_DEVELOPED = "roll_developed"

        //Filter
        //Added in database version 14
        private const val KEY_FILTER_ID = "filter_id"
        private const val KEY_FILTER_MAKE = "filter_make"
        private const val KEY_FILTER_MODEL = "filter_model"

        //Film stocks
        //Added in database version 20
        private const val KEY_FILM_STOCK_ID = "film_stock_id"
        private const val KEY_FILM_MANUFACTURER_NAME = "film_manufacturer_name"
        private const val KEY_FILM_STOCK_NAME = "film_stock_name"
        private const val KEY_FILM_ISO = "film_iso"
        private const val KEY_FILM_TYPE = "film_type"
        private const val KEY_FILM_PROCESS = "film_process"
        private const val KEY_FILM_IS_PREADDED = "film_is_preadded"

        //=============================================================================================
        //Database information
        private const val DATABASE_NAME = "filmnotes.db"

        //Updated version from 13 to 14 - 2016-12-03 - v1.7.0
        //Updated version from 14 to 15 - 2017-04-29 - v1.9.0
        //Updated version from 15 to 16 - 2018-02-17 - v1.9.5
        //Updated version from 16 to 17 - 2018-03-26 - v1.11.0
        //Updated version from 17 to 18 - 2018-07-08 - v1.12.0
        //Updated version from 18 to 19 - 2018-07-17 - awaiting
        private const val DATABASE_VERSION = 24

        //=============================================================================================
        //onCreate strings
        private const val CREATE_FILM_STOCKS_TABLE = ("create table " + TABLE_FILM_STOCKS
                + "(" + KEY_FILM_STOCK_ID + " integer primary key autoincrement, "
                + KEY_FILM_MANUFACTURER_NAME + " text not null, "
                + KEY_FILM_STOCK_NAME + " text not null, "
                + KEY_FILM_ISO + " integer,"
                + KEY_FILM_TYPE + " integer,"
                + KEY_FILM_PROCESS + " integer,"
                + KEY_FILM_IS_PREADDED + " integer"
                + ");")
        private const val CREATE_LENS_TABLE = ("create table " + TABLE_LENSES
                + "(" + KEY_LENS_ID + " integer primary key autoincrement, "
                + KEY_LENS_MAKE + " text not null, "
                + KEY_LENS_MODEL + " text not null, "
                + KEY_LENS_MAX_APERTURE + " text, "
                + KEY_LENS_MIN_APERTURE + " text, "
                + KEY_LENS_MAX_FOCAL_LENGTH + " integer, "
                + KEY_LENS_MIN_FOCAL_LENGTH + " integer, "
                + KEY_LENS_SERIAL_NO + " text, "
                + KEY_LENS_APERTURE_INCREMENTS + " integer not null, "
                + KEY_LENS_CUSTOM_APERTURE_VALUES + " text"
                + ");")
        private const val CREATE_CAMERA_TABLE = ("create table " + TABLE_CAMERAS
                + "(" + KEY_CAMERA_ID + " integer primary key autoincrement, "
                + KEY_CAMERA_MAKE + " text not null, "
                + KEY_CAMERA_MODEL + " text not null, "
                + KEY_CAMERA_MAX_SHUTTER + " text, "
                + KEY_CAMERA_MIN_SHUTTER + " text, "
                + KEY_CAMERA_SERIAL_NO + " text, "
                + KEY_CAMERA_SHUTTER_INCREMENTS + " integer not null, "
                + KEY_CAMERA_EXPOSURE_COMP_INCREMENTS + " integer not null default 0, "
                + KEY_LENS_ID + " integer references " + TABLE_LENSES + " on delete set null, "
                + KEY_CAMERA_FORMAT + " integer"
                + ");")
        private const val CREATE_FILTER_TABLE = ("create table " + TABLE_FILTERS
                + "(" + KEY_FILTER_ID + " integer primary key autoincrement, "
                + KEY_FILTER_MAKE + " text not null, "
                + KEY_FILTER_MODEL + " text not null"
                + ");")
        private const val CREATE_ROLL_TABLE = ("create table " + TABLE_ROLLS
                + "(" + KEY_ROLL_ID + " integer primary key autoincrement, "
                + KEY_ROLLNAME + " text not null, "
                + KEY_ROLL_DATE + " text not null, "
                + KEY_ROLL_NOTE + " text, "
                + KEY_CAMERA_ID + " integer references " + TABLE_CAMERAS + " on delete set null, "
                + KEY_ROLL_ISO + " integer, "
                + KEY_ROLL_PUSH + " text, "
                + KEY_ROLL_FORMAT + " integer, "
                + KEY_ROLL_ARCHIVED + " integer not null default 0,"
                + KEY_FILM_STOCK_ID + " integer references " + TABLE_FILM_STOCKS + " on delete set null, "
                + KEY_ROLL_UNLOADED + " text, "
                + KEY_ROLL_DEVELOPED + " text"
                + ");")
        private const val CREATE_FRAME_TABLE = ("create table " + TABLE_FRAMES
                + "(" + KEY_FRAME_ID + " integer primary key autoincrement, "
                + KEY_ROLL_ID + " integer not null references " + TABLE_ROLLS + " on delete cascade, "
                + KEY_COUNT + " integer not null, "
                + KEY_DATE + " text not null, "
                + KEY_LENS_ID + " integer references " + TABLE_LENSES + " on delete set null, "
                + KEY_SHUTTER + " text, "
                + KEY_APERTURE + " text, "
                + KEY_FRAME_NOTE + " text, "
                + KEY_LOCATION + " text, "
                + KEY_FOCAL_LENGTH + " integer, "
                + KEY_EXPOSURE_COMP + " text, "
                + KEY_NO_OF_EXPOSURES + " integer, "
                + KEY_FLASH_USED + " integer, "
                + KEY_FLASH_POWER + " text, "
                + KEY_FLASH_COMP + " text, "
                + KEY_FRAME_SIZE + " text, "
                + KEY_METERING_MODE + " integer, "
                + KEY_FORMATTED_ADDRESS + " text, "
                + KEY_PICTURE_FILENAME + " text, "
                + KEY_LIGHT_SOURCE + " integer"
                + ");")
        private const val CREATE_LINK_CAMERA_LENS_TABLE = ("create table " + TABLE_LINK_CAMERA_LENS
                + "(" + KEY_CAMERA_ID + " integer not null references " + TABLE_CAMERAS + " on delete cascade, "
                + KEY_LENS_ID + " integer not null references " + TABLE_LENSES + " on delete cascade, "
                + "primary key(" + KEY_CAMERA_ID + ", " + KEY_LENS_ID + ")"
                + ");")
        private const val CREATE_LINK_LENS_FILTER_TABLE = ("create table " + TABLE_LINK_LENS_FILTER
                + "(" + KEY_LENS_ID + " integer not null references " + TABLE_LENSES + " on delete cascade, "
                + KEY_FILTER_ID + " integer not null references " + TABLE_FILTERS + " on delete cascade, "
                + "primary key(" + KEY_LENS_ID + ", " + KEY_FILTER_ID + ")"
                + ");")
        private const val CREATE_LINK_FRAME_FILTER_TABLE = ("create table " + TABLE_LINK_FRAME_FILTER
                + "(" + KEY_FRAME_ID + " integer not null references " + TABLE_FRAMES + " on delete cascade, "
                + KEY_FILTER_ID + " integer not null references " + TABLE_FILTERS + " on delete cascade, "
                + "primary key(" + KEY_FRAME_ID + ", " + KEY_FILTER_ID + ")"
                + ");")

        //=============================================================================================
        //onUpgrade strings
        //Legacy table names for onUpgrade() statements.
        //These table names were used in pre 19 versions of the database.
        private const val LEGACY_TABLE_LINK_CAMERA_LENS = "mountables"
        private const val LEGACY_TABLE_LINK_LENS_FILTER = "mountable_filters_lenses"
        private const val ON_UPGRADE_CREATE_FILTER_TABLE = ("create table " + TABLE_FILTERS
                + "(" + KEY_FILTER_ID + " integer primary key autoincrement, "
                + KEY_FILTER_MAKE + " text not null, "
                + KEY_FILTER_MODEL + " text not null"
                + ");")
        private const val ON_UPGRADE_CREATE_LINK_LENS_FILTER_TABLE = ("create table " + LEGACY_TABLE_LINK_LENS_FILTER
                + "(" + KEY_LENS_ID + " integer not null, "
                + KEY_FILTER_ID + " integer not null"
                + ");")
        private const val ALTER_TABLE_FRAMES_1 = ("ALTER TABLE " + TABLE_FRAMES
                + " ADD COLUMN " + KEY_FOCAL_LENGTH + " integer;")
        private const val ALTER_TABLE_FRAMES_2 = ("ALTER TABLE " + TABLE_FRAMES
                + " ADD COLUMN " + KEY_EXPOSURE_COMP + " text;")
        private const val ALTER_TABLE_FRAMES_3 = ("ALTER TABLE " + TABLE_FRAMES
                + " ADD COLUMN " + KEY_NO_OF_EXPOSURES + " integer;")
        private const val ALTER_TABLE_FRAMES_4 = ("ALTER TABLE " + TABLE_FRAMES
                + " ADD COLUMN " + KEY_FLASH_USED + " integer;")
        private const val ALTER_TABLE_FRAMES_5 = ("ALTER TABLE " + TABLE_FRAMES
                + " ADD COLUMN " + KEY_FLASH_POWER + " text;")
        private const val ALTER_TABLE_FRAMES_6 = ("ALTER TABLE " + TABLE_FRAMES
                + " ADD COLUMN " + KEY_FLASH_COMP + " text;")
        private const val ALTER_TABLE_FRAMES_7 = ("ALTER TABLE " + TABLE_FRAMES
                + " ADD COLUMN " + KEY_FRAME_SIZE + " text;")
        private const val ALTER_TABLE_FRAMES_8 = ("ALTER TABLE " + TABLE_FRAMES
                + " ADD COLUMN " + KEY_FILTER_ID + " integer;")
        private const val ALTER_TABLE_FRAMES_9 = ("ALTER TABLE " + TABLE_FRAMES
                + " ADD COLUMN " + KEY_METERING_MODE + " integer;")
        private const val ALTER_TABLE_FRAMES_10 = ("ALTER TABLE " + TABLE_FRAMES
                + " ADD COLUMN " + KEY_FORMATTED_ADDRESS + " text;")
        private const val ALTER_TABLE_FRAMES_11 = ("ALTER TABLE " + TABLE_FRAMES
                + " ADD COLUMN " + KEY_PICTURE_FILENAME + " text;")
        private const val ALTER_TABLE_FRAMES_12 = ("ALTER TABLE " + TABLE_FRAMES
                + " ADD COLUMN " + KEY_LIGHT_SOURCE + " integer;")
        private const val ALTER_TABLE_LENSES_1 = ("ALTER TABLE " + TABLE_LENSES
                + " ADD COLUMN " + KEY_LENS_MAX_APERTURE + " text;")
        private const val ALTER_TABLE_LENSES_2 = ("ALTER TABLE " + TABLE_LENSES
                + " ADD COLUMN " + KEY_LENS_MIN_APERTURE + " text;")
        private const val ALTER_TABLE_LENSES_3 = ("ALTER TABLE " + TABLE_LENSES
                + " ADD COLUMN " + KEY_LENS_MAX_FOCAL_LENGTH + " integer;")
        private const val ALTER_TABLE_LENSES_4 = ("ALTER TABLE " + TABLE_LENSES
                + " ADD COLUMN " + KEY_LENS_MIN_FOCAL_LENGTH + " integer;")
        private const val ALTER_TABLE_LENSES_5 = ("ALTER TABLE " + TABLE_LENSES
                + " ADD COLUMN " + KEY_LENS_SERIAL_NO + " text;")
        private const val ALTER_TABLE_LENSES_6 = ("ALTER TABLE " + TABLE_LENSES
                + " ADD COLUMN " + KEY_LENS_APERTURE_INCREMENTS + " integer not null default 0;")
        private const val ALTER_TABLE_CAMERAS_1 = ("ALTER TABLE " + TABLE_CAMERAS
                + " ADD COLUMN " + KEY_CAMERA_MAX_SHUTTER + " text;")
        private const val ALTER_TABLE_CAMERAS_2 = ("ALTER TABLE " + TABLE_CAMERAS
                + " ADD COLUMN " + KEY_CAMERA_MIN_SHUTTER + " text;")
        private const val ALTER_TABLE_CAMERAS_3 = ("ALTER TABLE " + TABLE_CAMERAS
                + " ADD COLUMN " + KEY_CAMERA_SERIAL_NO + " text;")
        private const val ALTER_TABLE_CAMERAS_4 = ("ALTER TABLE " + TABLE_CAMERAS
                + " ADD COLUMN " + KEY_CAMERA_SHUTTER_INCREMENTS + " integer not null default 0;")
        private const val ALTER_TABLE_CAMERAS_5 = ("ALTER TABLE " + TABLE_CAMERAS
                + " ADD COLUMN " + KEY_CAMERA_EXPOSURE_COMP_INCREMENTS + " integer not null default 0;")
        private const val ALTER_TABLE_CAMERAS_6 = ("ALTER TABLE " + TABLE_CAMERAS
                + " ADD COLUMN " + KEY_LENS_ID + " integer references " + TABLE_LENSES + " on delete set null;")
        private const val ALTER_TABLE_ROLLS_1 = ("ALTER TABLE " + TABLE_ROLLS
                + " ADD COLUMN " + KEY_ROLL_ISO + " integer;")
        private const val ALTER_TABLE_ROLLS_2 = ("ALTER TABLE " + TABLE_ROLLS
                + " ADD COLUMN " + KEY_ROLL_PUSH + " text;")
        private const val ALTER_TABLE_ROLLS_3 = ("ALTER TABLE " + TABLE_ROLLS
                + " ADD COLUMN " + KEY_ROLL_FORMAT + " integer;")
        private const val ALTER_TABLE_ROLLS_4 = ("ALTER TABLE " + TABLE_ROLLS
                + " ADD COLUMN " + KEY_ROLL_ARCHIVED + " integer not null default 0;")
        private const val ALTER_TABLE_ROLLS_5 = ("ALTER TABLE " + TABLE_ROLLS
                + " ADD COLUMN " + KEY_FILM_STOCK_ID + " integer references " + TABLE_FILM_STOCKS + " on delete set null;")
        private const val ALTER_TABLE_ROLLS_6 = ("ALTER TABLE " + TABLE_ROLLS
                + " ADD COLUMN " + KEY_ROLL_UNLOADED + " text;")
        private const val ALTER_TABLE_ROLLS_7 = ("ALTER TABLE " + TABLE_ROLLS
                + " ADD COLUMN " + KEY_ROLL_DEVELOPED + " text;")
        private const val REPLACE_QUOTE_CHARS = ("UPDATE " + TABLE_FRAMES
                + " SET " + KEY_SHUTTER + " = REPLACE(" + KEY_SHUTTER + ", \'q\', \'\"\')"
                + " WHERE " + KEY_SHUTTER + " LIKE \'%q\';")
        private const val ALTER_TABLE_CAMERAS_7 =
            "ALTER TABLE $TABLE_CAMERAS ADD COLUMN $KEY_CAMERA_FORMAT integer;"
        private const val ALTER_TABLE_LENSES_7 =
            "ALTER TABLE $TABLE_LENSES ADD COLUMN $KEY_LENS_CUSTOM_APERTURE_VALUES text;"

        // (1) Rename the table, (2) create a new table with new structure,
        // (3) insert data from the renamed table to the new table and (4) drop the renamed table.
        private const val ROLLS_TABLE_REVISION_1 = "alter table $TABLE_ROLLS rename to temp_rolls;"
        private const val ROLLS_TABLE_REVISION_2 = ("create table " + TABLE_ROLLS
                + "(" + KEY_ROLL_ID + " integer primary key autoincrement, "
                + KEY_ROLLNAME + " text not null, "
                + KEY_ROLL_DATE + " text not null, "
                + KEY_ROLL_NOTE + " text, "
                + KEY_CAMERA_ID + " integer references " + TABLE_CAMERAS + " on delete set null, "
                + KEY_ROLL_ISO + " integer, "
                + KEY_ROLL_PUSH + " text, "
                + KEY_ROLL_FORMAT + " integer, "
                + KEY_ROLL_ARCHIVED + " integer not null default 0"
                + ");")
        private const val ROLLS_TABLE_REVISION_3 = ("insert into " + TABLE_ROLLS + " ("
                + KEY_ROLL_ID + ", " + KEY_ROLLNAME + ", " + KEY_ROLL_DATE + ", " + KEY_ROLL_NOTE + ", "
                + KEY_CAMERA_ID + ", " + KEY_ROLL_ISO + ", " + KEY_ROLL_PUSH + ", "
                + KEY_ROLL_FORMAT + ", " + KEY_ROLL_ARCHIVED + ") "
                + "select "
                + KEY_ROLL_ID + ", " + KEY_ROLLNAME + ", " + KEY_ROLL_DATE + ", " + KEY_ROLL_NOTE + ", "
                + "case when " + KEY_CAMERA_ID + " not in (select " + KEY_CAMERA_ID + " from " + TABLE_CAMERAS + ") then null else " + KEY_CAMERA_ID + " end" + ", "
                + KEY_ROLL_ISO + ", " + KEY_ROLL_PUSH + ", "
                + KEY_ROLL_FORMAT + ", " + KEY_ROLL_ARCHIVED + " "
                + "from temp_rolls;")
        private const val ROLLS_TABLE_REVISION_4 = "drop table temp_rolls;"

        // (1) Rename the table, (2) create a new table with new structure,
        // (3) insert data from the renamed table to the new table,
        // (4) create a new link table between frames and filters,
        // (5) insert data from the renamed table to the new link table and
        // (6) drop the renamed table.
        private const val FRAMES_TABLE_REVISION_1 = "alter table $TABLE_FRAMES rename to temp_frames;"
        private const val FRAMES_TABLE_REVISION_2 = ("create table " + TABLE_FRAMES
                + "(" + KEY_FRAME_ID + " integer primary key autoincrement, "
                + KEY_ROLL_ID + " integer not null references " + TABLE_ROLLS + " on delete cascade, "
                + KEY_COUNT + " integer not null, "
                + KEY_DATE + " text not null, "
                + KEY_LENS_ID + " integer references " + TABLE_LENSES + " on delete set null, "
                + KEY_SHUTTER + " text, "
                + KEY_APERTURE + " text, "
                + KEY_FRAME_NOTE + " text, "
                + KEY_LOCATION + " text, "
                + KEY_FOCAL_LENGTH + " integer, "
                + KEY_EXPOSURE_COMP + " text, "
                + KEY_NO_OF_EXPOSURES + " integer, "
                + KEY_FLASH_USED + " integer, "
                + KEY_FLASH_POWER + " text, "
                + KEY_FLASH_COMP + " text, "
                + KEY_FRAME_SIZE + " text, "
                + KEY_METERING_MODE + " integer, "
                + KEY_FORMATTED_ADDRESS + " text, "
                + KEY_PICTURE_FILENAME + " text"
                + ");")
        private const val FRAMES_TABLE_REVISION_3 = ("insert into " + TABLE_FRAMES + " ("
                + KEY_FRAME_ID + ", " + KEY_ROLL_ID + ", " + KEY_COUNT + ", " + KEY_DATE + ", "
                + KEY_LENS_ID + ", " + KEY_SHUTTER + ", " + KEY_APERTURE + ", " + KEY_FRAME_NOTE + ", "
                + KEY_LOCATION + ", " + KEY_FOCAL_LENGTH + ", " + KEY_EXPOSURE_COMP + ", "
                + KEY_NO_OF_EXPOSURES + ", " + KEY_FLASH_USED + ", " + KEY_FLASH_POWER + ", "
                + KEY_FLASH_COMP + ", " + KEY_FRAME_SIZE + ", " + KEY_METERING_MODE + ", "
                + KEY_FORMATTED_ADDRESS + ", " + KEY_PICTURE_FILENAME + ") "
                + "select "
                + KEY_FRAME_ID + ", " + KEY_ROLL_ID + ", " + KEY_COUNT + ", " + KEY_DATE + ", "
                + "case when " + KEY_LENS_ID + " not in (select " + KEY_LENS_ID + " from " + TABLE_LENSES + ") then null else " + KEY_LENS_ID + " end" + ", "
                + "nullif(" + KEY_SHUTTER + ", '<empty>')" + ", "
                + "nullif(" + KEY_APERTURE + ", '<empty>')" + ", "
                + KEY_FRAME_NOTE + ", " + KEY_LOCATION + ", " + KEY_FOCAL_LENGTH + ", "
                + KEY_EXPOSURE_COMP + ", " + KEY_NO_OF_EXPOSURES + ", " + KEY_FLASH_USED + ", "
                + KEY_FLASH_POWER + ", " + KEY_FLASH_COMP + ", " + KEY_FRAME_SIZE + ", "
                + KEY_METERING_MODE + ", " + KEY_FORMATTED_ADDRESS + ", " + KEY_PICTURE_FILENAME + " "
                + "from temp_frames "
                + "where " + KEY_ROLL_ID + " in (select " + KEY_ROLL_ID + " from " + TABLE_ROLLS + ")" + ";")
        private const val ON_UPGRADE_CREATE_LINK_FRAME_FILTER_TABLE = ("create table " + TABLE_LINK_FRAME_FILTER
                + "(" + KEY_FRAME_ID + " integer not null references " + TABLE_FRAMES + " on delete cascade, "
                + KEY_FILTER_ID + " integer not null references " + TABLE_FILTERS + " on delete cascade, "
                + "primary key(" + KEY_FRAME_ID + ", " + KEY_FILTER_ID + ")"
                + ");")
        private const val FRAMES_TABLE_REVISION_4 = ("insert into " + TABLE_LINK_FRAME_FILTER + " ("
                + KEY_FRAME_ID + ", " + KEY_FILTER_ID + ") "
                + "select " + KEY_FRAME_ID + ", " + KEY_FILTER_ID + " "
                + "from temp_frames "
                + "where " + KEY_FILTER_ID + " in (select " + KEY_FILTER_ID + " from " + TABLE_FILTERS + ")"
                + "and " + KEY_ROLL_ID + " in (select " + KEY_ROLL_ID + " from " + TABLE_ROLLS + ")" + ";")
        private const val FRAMES_TABLE_REVISION_5 = "drop table temp_frames;"

        // (1) Rename the table (in pre database 19 versions called "mountables"),
        // (2) create a new table with new structure,
        // (3) insert data from the renamed table to the new table and (4) drop the renamed table.
        private const val CAMERA_LENS_LINK_TABLE_REVISION_1 = "alter table $LEGACY_TABLE_LINK_CAMERA_LENS rename to temp_mountables;"
        private const val CAMERA_LENS_LINK_TABLE_REVISION_2 = ("create table " + TABLE_LINK_CAMERA_LENS
                + "(" + KEY_CAMERA_ID + " integer not null references " + TABLE_CAMERAS + " on delete cascade, "
                + KEY_LENS_ID + " integer not null references " + TABLE_LENSES + " on delete cascade, "
                + "primary key(" + KEY_CAMERA_ID + ", " + KEY_LENS_ID + ")"
                + ");")
        private const val CAMERA_LENS_LINK_TABLE_REVISION_3 = ("insert into " + TABLE_LINK_CAMERA_LENS + " ("
                + KEY_CAMERA_ID + ", " + KEY_LENS_ID + ") "
                + "select " + KEY_CAMERA_ID + ", " + KEY_LENS_ID + " "
                + "from temp_mountables "
                + "where " + KEY_CAMERA_ID + " in (select " + KEY_CAMERA_ID + " from " + TABLE_CAMERAS + ") "
                + "and " + KEY_LENS_ID + " in (select " + KEY_LENS_ID + " from " + TABLE_LENSES + ")" + ";")
        private const val CAMERA_LENS_LINK_TABLE_REVISION_4 = "drop table temp_mountables;"

        // (1) Rename the table (in pre database 19 versions called "mountable_filters_lenses"),
        // (2) create a new table with new structure,
        // (3) insert data from the renamed table to the new table and (4) drop the renamed table.
        private const val LENS_FILTER_LINK_TABLE_REVISION_1 = "alter table $LEGACY_TABLE_LINK_LENS_FILTER rename to temp_mountable_filters_lenses;"
        private const val LENS_FILTER_LINK_TABLE_REVISION_2 = ("create table " + TABLE_LINK_LENS_FILTER
                + "(" + KEY_LENS_ID + " integer not null references " + TABLE_LENSES + " on delete cascade, "
                + KEY_FILTER_ID + " integer not null references " + TABLE_FILTERS + " on delete cascade, "
                + "primary key(" + KEY_LENS_ID + ", " + KEY_FILTER_ID + ")"
                + ");")
        private const val LENS_FILTER_LINK_TABLE_REVISION_3 = ("insert into " + TABLE_LINK_LENS_FILTER + " ("
                + KEY_LENS_ID + ", " + KEY_FILTER_ID + ") "
                + "select " + KEY_LENS_ID + ", " + KEY_FILTER_ID + " "
                + "from temp_mountable_filters_lenses "
                + "where " + KEY_LENS_ID + " in (select " + KEY_LENS_ID + " from " + TABLE_LENSES + ") "
                + "and " + KEY_FILTER_ID + " in (select " + KEY_FILTER_ID + " from " + TABLE_FILTERS + ")" + ";")
        private const val LENS_FILTER_LINK_TABLE_REVISION_4 = "drop table temp_mountable_filters_lenses;"

    }

}