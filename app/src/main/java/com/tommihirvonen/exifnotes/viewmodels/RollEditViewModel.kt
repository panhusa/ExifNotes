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

package com.tommihirvonen.exifnotes.viewmodels

import android.app.Application
import android.view.View
import android.widget.AdapterView
import androidx.databinding.BaseObservable
import androidx.databinding.Bindable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.tommihirvonen.exifnotes.BR
import com.tommihirvonen.exifnotes.R
import com.tommihirvonen.exifnotes.core.entities.Camera
import com.tommihirvonen.exifnotes.core.entities.FilmStock
import com.tommihirvonen.exifnotes.core.entities.Format
import com.tommihirvonen.exifnotes.core.entities.Roll
import com.tommihirvonen.exifnotes.data.repositories.FilmStockRepository
import com.tommihirvonen.exifnotes.utilities.validate
import java.time.LocalDateTime

class RollEditViewModel(application: Application,
                        private val filmStockRepository: FilmStockRepository,
                        val roll: Roll)
    :AndroidViewModel(application) {

    var cameras: List<Camera> = emptyList()
        set(value) {
            field = value
            observable.notifyPropertyChanged(BR.cameraItems)
        }

    val context get() = getApplication<Application>()

    val observable = Observable()

    fun validate(): Boolean = roll.validate(nameValidation)

    private val nameValidation = { roll: Roll ->
        if (roll.name.isNullOrEmpty()) {
            observable.nameError = context.resources.getString(R.string.Required)
            false
        } else {
            true
        }
    }

    fun addFilmStock(filmStock: FilmStock) {
        filmStockRepository.addFilmStock(filmStock)
        observable.setFilmStock(filmStock)
    }

    inner class Observable : BaseObservable() {

        @get:Bindable
        val cameraItems get() = listOf(context.resources.getString(R.string.NoCamera))
            .plus(cameras.map { c ->
                val nameIsNotDistinct = cameras.any { it.id != c.id && it.name == c.name }
                if (nameIsNotDistinct && !c.serialNumber.isNullOrEmpty())
                    "${c.name} (${c.serialNumber})"
                else
                    c.name
            })
            .toTypedArray()

        @get:Bindable
        val isoValues: Array<String> get() = context.resources.getStringArray(R.array.ISOValues)

        @get:Bindable
        val pushPullValues: Array<String> get() = context.resources.getStringArray(R.array.CompValues)

        @Bindable
        fun getName() = roll.name
        fun setName(value: String?) {
            if (roll.name != value) {
                roll.name = value
                notifyPropertyChanged(BR.name)
            }
        }

        @Bindable
        var nameError: String? = null
            set(value) {
                field = value
                notifyPropertyChanged(BR.nameError)
            }

        @Bindable
        fun getFilmStock() = roll.filmStock?.name ?: ""
        fun setFilmStock(filmStock: FilmStock?) {
            roll.filmStock = filmStock
            notifyPropertyChanged(BR.filmStock)
            notifyPropertyChanged(BR.clearFilmStockVisibility)
            notifyPropertyChanged(BR.addFilmStockVisibility)
            if (filmStock != null && filmStock.iso != 0) {
                setIso(filmStock.iso.toString())
                if (roll.name.isNullOrEmpty()) {
                    roll.name = filmStock.name
                    notifyPropertyChanged(BR.name)
                }
            }
        }

        @Bindable
        fun getLoadedOn() = roll.date
        fun setLoadedOn(date: LocalDateTime) {
            roll.date = date
            notifyPropertyChanged(BR.loadedOn)
        }

        @Bindable
        fun getUnloadedOn() = roll.unloaded
        fun setUnloadedOn(date: LocalDateTime?) {
            roll.unloaded = date
            notifyPropertyChanged(BR.unloadedOn)
        }

        @Bindable
        fun getDevelopedOn() = roll.developed
        fun setDevelopedOn(date: LocalDateTime?) {
            roll.developed = date
            notifyPropertyChanged(BR.developedOn)
        }

        @Bindable
        fun getCamera() = roll.camera?.name
        fun setCamera(value: Camera?) {
            if (roll.camera != value) {
                roll.camera = value
                roll.format = value?.format ?: roll.format
                notifyPropertyChanged(BR.camera)
                notifyPropertyChanged(BR.format)
            }
        }

        @Bindable
        fun getNote() = roll.note
        fun setNote(value: String?) {
            if (roll.note != value) {
                roll.note = value
                notifyPropertyChanged(BR.note)
            }
        }

        @Bindable
        fun getIso() = roll.iso.toString()
        fun setIso(value: String?) {
            val iso = value?.toIntOrNull() ?: 0
            if (roll.iso != iso) {
                roll.iso = iso
                notifyPropertyChanged(BR.iso)
            }
        }

        @Bindable
        fun getPushPull() = roll.pushPull
        fun setPushPull(value: String?) {
            if (roll.pushPull != value) {
                roll.pushPull = value
                notifyPropertyChanged(BR.pushPull)
            }
        }

        @get:Bindable
        val format: String get() =
            roll.format.description(context) ?: "Unrecognized"

        @get:Bindable
        val clearFilmStockVisibility: Int get() =
            if (roll.filmStock == null) View.INVISIBLE else View.VISIBLE

        @get:Bindable
        val addFilmStockVisibility: Int get() =
            if (roll.filmStock != null) View.INVISIBLE else View.VISIBLE

        val onCameraItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            // Account for the "No camera" option
            roll.camera = if (position > 0) {
                cameras[position - 1]
            } else {
                null
            }
            roll.format = roll.camera?.format ?: roll.format
            notifyPropertyChanged(BR.camera)
            notifyPropertyChanged(BR.format)
        }

        val isoOnClickListener = View.OnClickListener { view: View ->
            val currentIndex = isoValues.indexOf(roll.iso.toString())
            if (currentIndex >= 0) {
                (view as MaterialAutoCompleteTextView).listSelection = currentIndex
            }
        }

        val pushPullOnClickListener = View.OnClickListener { view: View ->
            val currentIndex = pushPullValues.indexOf(roll.pushPull)
            if (currentIndex >= 0) {
                (view as MaterialAutoCompleteTextView).listSelection = currentIndex
            }
        }

        val onFormatItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            roll.format = Format.from(position)
        }
    }
}

class RollEditViewModelFactory(private val application: Application,
                               private val filmStockRepository: FilmStockRepository,
                               private val roll: Roll
)
    : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        if (modelClass.isAssignableFrom(RollEditViewModel::class.java)) {
            return RollEditViewModel(application, filmStockRepository, roll) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}