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

package com.tommihirvonen.exifnotes.fragments

import android.app.AlertDialog
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.tommihirvonen.exifnotes.R
import com.tommihirvonen.exifnotes.adapters.FilmStockAdapter
import com.tommihirvonen.exifnotes.databinding.FragmentFilmsBinding
import com.tommihirvonen.exifnotes.datastructures.FilmStock
import com.tommihirvonen.exifnotes.dialogs.EditFilmStockDialog
import com.tommihirvonen.exifnotes.utilities.ExtraKeys
import com.tommihirvonen.exifnotes.utilities.database
import com.tommihirvonen.exifnotes.utilities.secondaryUiColor

class FilmStocksFragment : Fragment(), View.OnClickListener {

    companion object {
        const val SORT_MODE_NAME = 1
        const val SORT_MODE_ISO = 2
        const val FILTER_MODE_ALL = 0
        const val FILTER_MODE_ADDED_BY_USER = 1
        const val FILTER_MODE_PREADDED = 2
    }

    private lateinit var binding: FragmentFilmsBinding
    private lateinit var allFilmStocks: MutableList<FilmStock>
    private lateinit var filteredFilmStocks: MutableList<FilmStock>
    private var fragmentVisible = false
    private lateinit var filmStockAdapter: FilmStockAdapter
    var sortMode = SORT_MODE_NAME
        private set
    private var manufacturerFilterList = emptyList<String>().toMutableList()
    private var isoFilterList = emptyList<Int>().toMutableList()
    private var filmTypeFilterList = emptyList<Int>().toMutableList()
    private var filmProcessFilterList = emptyList<Int>().toMutableList()
    private var addedByFilterMode = FILTER_MODE_ALL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        allFilmStocks = database.allFilmStocks.toMutableList()
        filteredFilmStocks = allFilmStocks.toMutableList()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        binding = FragmentFilmsBinding.inflate(inflater, container, false)
        binding.fabFilms.setOnClickListener(this)
        // Also change the floating action button color. Use the darker secondaryColor for this.
        binding.fabFilms.backgroundTintList = ColorStateList.valueOf(secondaryUiColor)

        val layoutManager = LinearLayoutManager(activity)
        binding.filmsRecyclerView.layoutManager = layoutManager
        binding.filmsRecyclerView.addItemDecoration(
                DividerItemDecoration(
                        binding.filmsRecyclerView.context, layoutManager.orientation
                )
        )
        filmStockAdapter = FilmStockAdapter(requireActivity())
        filmStockAdapter.filmStocks = filteredFilmStocks
        binding.filmsRecyclerView.adapter = filmStockAdapter
        filmStockAdapter.notifyDataSetChanged()

        return binding.root
    }

    override fun onResume() {
        fragmentVisible = true
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
        fragmentVisible = false
    }

    override fun onClick(v: View) {
        if (v.id == R.id.fab_films) {
            val dialog = EditFilmStockDialog()
            val arguments = Bundle()
            arguments.putString(ExtraKeys.TITLE, resources.getString(R.string.AddNewFilmStock))
            arguments.putString(ExtraKeys.POSITIVE_BUTTON, resources.getString(R.string.Add))
            dialog.arguments = arguments
            dialog.show(parentFragmentManager.beginTransaction(), null)
            dialog.setFragmentResultListener("EditFilmStockDialog") { _, bundle ->
                val filmStock: FilmStock = bundle.getParcelable(ExtraKeys.FILM_STOCK)
                    ?: return@setFragmentResultListener
                database.addFilmStock(filmStock)
                // Add the new film stock to both lists.
                filteredFilmStocks.add(filmStock) // The new film stock is shown immediately regardless of filters.
                allFilmStocks.add(filmStock) // The new film stock is shown after new filters are applied and they match.
                sortFilmStocks()
                val position = filteredFilmStocks.indexOf(filmStock)
                filmStockAdapter.notifyItemInserted(position)
                binding.filmsRecyclerView.scrollToPosition(position)
            }
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        if (fragmentVisible) {
            val position = item.order
            val filmStock = filteredFilmStocks[position]
            when (item.itemId) {
                FilmStockAdapter.MENU_ITEM_DELETE -> {
                    val builder = AlertDialog.Builder(activity)
                    builder.setTitle(
                            resources.getString(R.string.DeleteFilmStock) + " " + filmStock.name
                    )
                    if (database.isFilmStockBeingUsed(filmStock)) {
                        builder.setMessage(R.string.FilmStockIsInUseConfirmation)
                    }
                    builder.setNegativeButton(R.string.Cancel) { _: DialogInterface?, _: Int -> }
                    builder.setPositiveButton(R.string.OK) { _: DialogInterface?, _: Int ->
                        database.deleteFilmStock(filmStock)
                        filteredFilmStocks.remove(filmStock)
                        allFilmStocks.remove(filmStock)
                        filmStockAdapter.notifyDataSetChanged()
                    }
                    builder.create().show()
                    return true
                }
                FilmStockAdapter.MENU_ITEM_EDIT -> {
                    val dialog = EditFilmStockDialog()
                    val arguments = Bundle()
                    arguments.putString(ExtraKeys.TITLE, resources.getString(R.string.EditFilmStock))
                    arguments.putString(ExtraKeys.POSITIVE_BUTTON, resources.getString(R.string.OK))
                    arguments.putParcelable(ExtraKeys.FILM_STOCK, filmStock)
                    dialog.arguments = arguments
                    dialog.show(parentFragmentManager.beginTransaction(), null)
                    dialog.setFragmentResultListener("EditFilmStockDialog") { _, bundle ->
                        val filmStock1: FilmStock = bundle.getParcelable(ExtraKeys.FILM_STOCK)
                            ?: return@setFragmentResultListener
                        database.updateFilmStock(filmStock1)
                        val oldPosition = filteredFilmStocks.indexOf(filmStock1)
                        sortFilmStocks()
                        val newPosition = filteredFilmStocks.indexOf(filmStock1)
                        filmStockAdapter.notifyItemChanged(oldPosition)
                        filmStockAdapter.notifyItemMoved(oldPosition, newPosition)
                    }
                    return true
                }
            }
        }
        return false
    }

    private fun sortFilmStocks() {
        setSortMode(sortMode, false)
    }

    fun setSortMode(sortMode_: Int, notifyDataSetChanged: Boolean) {
        when (sortMode_) {
            SORT_MODE_NAME -> {
                sortMode = SORT_MODE_NAME
                filteredFilmStocks.sortWith { o1, o2 -> o1.name.compareTo(o2.name, ignoreCase = true) }
            }
            SORT_MODE_ISO -> {
                sortMode = SORT_MODE_ISO
                filteredFilmStocks.sortWith { o1, o2 -> o1.iso.compareTo(o2.iso) }
            }
        }

        if (notifyDataSetChanged) filmStockAdapter.notifyDataSetChanged()
    }

    fun resetFilters() {
        manufacturerFilterList.clear()
        isoFilterList.clear()
        filmTypeFilterList.clear()
        filmProcessFilterList.clear()
        addedByFilterMode = FILTER_MODE_ALL
        filterFilmStocks()
    }

    private fun filterFilmStocks() {
        // First filter the list based on manufacturer. No filtering is done if manufacturers is null.
        filteredFilmStocks = allFilmStocks.filter {
            // Filter based on manufacturers
            (manufacturerFilterList.contains(it.make) || manufacturerFilterList.isEmpty()) &&
            // Filter based on type
            (filmTypeFilterList.contains(it.type) || filmTypeFilterList.isEmpty()) &&
            // Filter based on process
            (filmProcessFilterList.contains(it.process) || filmProcessFilterList.isEmpty()) &&
            //Then filter based on filter mode.
            when (addedByFilterMode) {
                FILTER_MODE_PREADDED -> it.isPreadded
                FILTER_MODE_ADDED_BY_USER -> !it.isPreadded
                FILTER_MODE_ALL -> true
                else -> throw IllegalArgumentException("Illegal argument filterModeAddedBy: $addedByFilterMode")
            }
            // Finally filter based on ISO values.
            && (isoFilterList.contains(it.iso) || isoFilterList.isEmpty())
        }.toMutableList()
        sortFilmStocks()

        filmStockAdapter.filmStocks = filteredFilmStocks
        filmStockAdapter.notifyDataSetChanged()
    }

    // Possible ISO values are filtered based on currently selected manufacturers and filter mode.
    private val possibleIsoValues get() = allFilmStocks.filter {
            (manufacturerFilterList.contains(it.make) || manufacturerFilterList.isEmpty()) &&
            (filmTypeFilterList.contains(it.type) || filmTypeFilterList.isEmpty()) &&
            (filmProcessFilterList.contains(it.process) || filmProcessFilterList.isEmpty()) &&
            when (addedByFilterMode) {
                FILTER_MODE_PREADDED -> it.isPreadded
                FILTER_MODE_ADDED_BY_USER -> !it.isPreadded
                FILTER_MODE_ALL -> true
                else -> throw IllegalArgumentException("Illegal argument filterModeAddedBy: $addedByFilterMode")
            }
    }.map { it.iso }.distinct().sorted()

    fun showManufacturerFilterDialog() {
        val builder = AlertDialog.Builder(requireActivity())
        // Get all filter items.
        val items = database.allFilmManufacturers.toTypedArray()
        // Create a boolean array of same size with selected items marked true.
        val checkedItems = items.map { manufacturerFilterList.contains(it) }.toBooleanArray()
        builder.setMultiChoiceItems(items, checkedItems) { _: DialogInterface?, which: Int, isChecked: Boolean ->
            checkedItems[which] = isChecked
        }
        builder.setNegativeButton(R.string.Cancel) { _: DialogInterface?, _: Int -> }
        builder.setPositiveButton(R.string.FilterNoColon) { _: DialogInterface?, _: Int ->
            // Get the indices of items that were marked true and their corresponding strings.
            manufacturerFilterList = checkedItems
                    .mapIndexed { index, selected -> index to selected }
                    .filter { it.second }.map { it.first }.map { items[it] }.toMutableList()
            filterFilmStocks()
        }
        builder.setNeutralButton(R.string.Reset) { _: DialogInterface?, _: Int ->
            manufacturerFilterList.clear()
            filterFilmStocks()
        }
        builder.create().show()
    }

    fun showIsoValuesFilterDialog() {
        val builder = AlertDialog.Builder(requireActivity())
        // Get all filter items.
        val items = possibleIsoValues.toTypedArray()
        val itemStrings = items.map { it.toString() }.toTypedArray()
        // Create a boolean array of same size with selected items marked true.
        val checkedItems = items.map { isoFilterList.contains(it) }.toBooleanArray()
        builder.setMultiChoiceItems(itemStrings, checkedItems) { _: DialogInterface?, which: Int, isChecked: Boolean ->
            checkedItems[which] = isChecked
        }
        builder.setNegativeButton(R.string.Cancel) { _: DialogInterface?, _: Int -> }
        builder.setPositiveButton(R.string.FilterNoColon) { _: DialogInterface?, _: Int ->
            // Get the indices of items that were marked true and their corresponding int values.
            isoFilterList = checkedItems
                    .mapIndexed { index, selected -> index to selected }
                    .filter { it.second }.map { it.first }.map { items[it] }.toMutableList()
            filterFilmStocks()
        }
        builder.setNeutralButton(R.string.Reset) { _: DialogInterface?, _: Int ->
            isoFilterList.clear()
            filterFilmStocks()
        }
        builder.create().show()
    }

    fun showFilmTypeFilterDialog() {
        val builder = AlertDialog.Builder(requireActivity())
        // Get all filter items.
        val items = resources.getStringArray(R.array.FilmTypes)
        // Create a boolean array of same size with selected items marked true.
        val checkedItems = items.indices.map { filmTypeFilterList.contains(it) }.toBooleanArray()
        builder.setMultiChoiceItems(items, checkedItems) { _: DialogInterface?, which: Int, isChecked: Boolean ->
            checkedItems[which] = isChecked
        }
        builder.setNegativeButton(R.string.Cancel) { _: DialogInterface?, _: Int -> }
        builder.setPositiveButton(R.string.FilterNoColon) { _: DialogInterface?, _: Int ->
            // Get the indices of items that were marked true.
            filmTypeFilterList = checkedItems
                    .mapIndexed { index, selected -> index to selected }
                    .filter { it.second }.map { it.first }.toMutableList()
            filterFilmStocks()
        }
        builder.setNeutralButton(R.string.Reset) { _: DialogInterface?, _: Int ->
            filmTypeFilterList.clear()
            filterFilmStocks()
        }
        builder.create().show()
    }

    fun showFilmProcessFilterDialog() {
        val builder = AlertDialog.Builder(requireActivity())
        // Get all filter items.
        val items = resources.getStringArray(R.array.FilmProcesses)
        // Create a boolean array of same size with selected items marked true.
        val checkedItems = items.indices.map { filmProcessFilterList.contains(it) }.toBooleanArray()
        builder.setMultiChoiceItems(items, checkedItems) { _: DialogInterface?, which: Int, isChecked: Boolean ->
            checkedItems[which] = isChecked
        }
        builder.setNegativeButton(R.string.Cancel) { _: DialogInterface?, _: Int -> }
        builder.setPositiveButton(R.string.FilterNoColon) { _: DialogInterface?, _: Int ->
            // Get the indices of items that were marked true.
            filmProcessFilterList = checkedItems
                    .mapIndexed { index, selected -> index to selected }
                    .filter { it.second }.map { it.first }.toMutableList()
            filterFilmStocks()
        }
        builder.setNeutralButton(R.string.Reset) { _: DialogInterface?, _: Int ->
            filmProcessFilterList.clear()
            filterFilmStocks()
        }
        builder.create().show()
    }

    fun showAddedByFilterDialog() {
        val builder = AlertDialog.Builder(requireActivity())
        val checkedItem: Int
        val filterModeAddedBy = addedByFilterMode
        checkedItem = when (filterModeAddedBy) {
            FILTER_MODE_PREADDED -> 1
            FILTER_MODE_ADDED_BY_USER -> 2
            else -> 0
        }
        builder.setSingleChoiceItems(R.array.FilmStocksFilterMode, checkedItem) { dialog: DialogInterface, which: Int ->
            when (which) {
                0 -> addedByFilterMode = FILTER_MODE_ALL
                1 -> addedByFilterMode = FILTER_MODE_PREADDED
                2 -> addedByFilterMode = FILTER_MODE_ADDED_BY_USER
            }
            filterFilmStocks()
            dialog.dismiss()
        }
        builder.setNegativeButton(R.string.Cancel) { _: DialogInterface?, _: Int -> }
        builder.create().show()
    }

}