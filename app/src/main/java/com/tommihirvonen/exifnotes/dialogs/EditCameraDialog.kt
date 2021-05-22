package com.tommihirvonen.exifnotes.dialogs

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.fragment.app.DialogFragment
import com.tommihirvonen.exifnotes.R
import com.tommihirvonen.exifnotes.databinding.DialogCameraBinding
import com.tommihirvonen.exifnotes.datastructures.Camera
import com.tommihirvonen.exifnotes.datastructures.Increment
import com.tommihirvonen.exifnotes.datastructures.Lens
import com.tommihirvonen.exifnotes.datastructures.PartialIncrement
import com.tommihirvonen.exifnotes.utilities.ExtraKeys
import com.tommihirvonen.exifnotes.utilities.Utilities
import com.tommihirvonen.exifnotes.utilities.Utilities.ScrollIndicatorNestedScrollViewListener
import com.tommihirvonen.exifnotes.utilities.database

/**
 * Dialog to edit Camera's information
 */
class EditCameraDialog : DialogFragment() {

    companion object {
        /**
         * Public constant used to tag the fragment when created
         */
        const val TAG = "EditCameraDialog"
        private const val EDIT_LENS = 1
    }

    private lateinit var binding: DialogCameraBinding

    private lateinit var newCamera: Camera

    /**
     * Stores the currently displayed shutter speed values.
     * Changes depending on the currently selected shutter increments
     */
    private lateinit var displayedShutterValues: Array<String>

    override fun onCreateDialog(SavedInstanceState: Bundle?): Dialog {
        val layoutInflater = requireActivity().layoutInflater
        binding = DialogCameraBinding.inflate(layoutInflater)

        val alert = AlertDialog.Builder(activity)
        val title = requireArguments().getString(ExtraKeys.TITLE)
        val positiveButton = requireArguments().getString(ExtraKeys.POSITIVE_BUTTON)
        val camera = requireArguments().getParcelable(ExtraKeys.CAMERA) ?: Camera()
        newCamera = camera.copy()

        val nestedScrollView = binding.nestedScrollView
        nestedScrollView.setOnScrollChangeListener(
                ScrollIndicatorNestedScrollViewListener(
                        nestedScrollView,
                        binding.scrollIndicatorUp,
                        binding.scrollIndicatorDown))

        alert.setCustomTitle(Utilities.buildCustomDialogTitleTextView(requireActivity(), title))
        alert.setView(binding.root)

        // EDIT TEXT FIELDS
        binding.makeEditText.setText(camera.make)
        binding.modelEditText.setText(camera.model)
        binding.serialNumberEditText.setText(camera.serialNumber)

        // SHUTTER SPEED INCREMENTS
        try {
            binding.shutterSpeedIncrementSpinner.setSelection(newCamera.shutterIncrements.ordinal)
        } catch (e: ArrayIndexOutOfBoundsException) {
            e.printStackTrace()
        }
        binding.shutterSpeedIncrementSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                //Shutter speed increments were changed, make update
                //Check if the new increments include both min and max values.
                //Otherwise reset them to null
                var minFound = false
                var maxFound = false
                displayedShutterValues = when (newCamera.shutterIncrements) {
                    Increment.THIRD -> requireActivity().resources.getStringArray(R.array.ShutterValuesThird)
                    Increment.HALF -> requireActivity().resources.getStringArray(R.array.ShutterValuesHalf)
                    Increment.FULL -> requireActivity().resources.getStringArray(R.array.ShutterValuesFull)
                }
                for (string in displayedShutterValues) {
                    if (!minFound && string == newCamera.minShutter) minFound = true
                    if (!maxFound && string == newCamera.maxShutter) maxFound = true
                    if (minFound && maxFound) break
                }
                //If either one wasn't found in the new values array, null them.
                if (!minFound || !maxFound) {
                    newCamera.minShutter = null
                    newCamera.maxShutter = null
                    updateShutterRangeTextView()
                }
            }
        }

        // SHUTTER RANGE BUTTON
        updateShutterRangeTextView()
        binding.shutterRangeLayout.setOnClickListener {
            val builder = AlertDialog.Builder(activity)
            val inflater = requireActivity().layoutInflater
            @SuppressLint("InflateParams")
            val dialogView = inflater.inflate(R.layout.dialog_double_numberpicker, null)
            val minShutterPicker = dialogView.findViewById<NumberPicker>(R.id.number_picker_one)
            val maxShutterPicker = dialogView.findViewById<NumberPicker>(R.id.number_picker_two)

            // To prevent text edit
            minShutterPicker.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            maxShutterPicker.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            initialiseShutterRangePickers(minShutterPicker, maxShutterPicker)
            builder.setView(dialogView)
            builder.setTitle(resources.getString(R.string.ChooseShutterRange))
            builder.setPositiveButton(resources.getString(R.string.OK), null)
            builder.setNegativeButton(resources.getString(R.string.Cancel)) { _: DialogInterface?, _: Int -> }
            val dialog = builder.create()
            dialog.show()
            // Override the positiveButton to check the range before accepting.
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (minShutterPicker.value == displayedShutterValues.size - 1 &&
                        maxShutterPicker.value != displayedShutterValues.size - 1
                        ||
                        minShutterPicker.value != displayedShutterValues.size - 1 &&
                        maxShutterPicker.value == displayedShutterValues.size - 1) {
                    // No min or max shutter was set
                    Toast.makeText(activity, resources.getString(R.string.NoMinOrMaxShutter),
                            Toast.LENGTH_LONG).show()
                } else {
                    if (minShutterPicker.value == displayedShutterValues.size - 1 &&
                            maxShutterPicker.value == displayedShutterValues.size - 1) {
                        newCamera.minShutter = null
                        newCamera.maxShutter = null
                    } else if (minShutterPicker.value < maxShutterPicker.value) {
                        newCamera.minShutter = displayedShutterValues[minShutterPicker.value]
                        newCamera.maxShutter = displayedShutterValues[maxShutterPicker.value]
                    } else {
                        newCamera.minShutter = displayedShutterValues[maxShutterPicker.value]
                        newCamera.maxShutter = displayedShutterValues[minShutterPicker.value]
                    }
                    updateShutterRangeTextView()
                }
                dialog.dismiss()
            }
        }


        // EXPOSURE COMPENSATION INCREMENTS
        try {
            binding.exposureCompIncrementSpinner.setSelection(newCamera.exposureCompIncrements.ordinal)
        } catch (e: ArrayIndexOutOfBoundsException) {
            e.printStackTrace()
        }

        // FIXED LENS
        binding.fixedLensHelp.setOnClickListener {
            AlertDialog.Builder(requireContext()).apply {
                setMessage(R.string.FixedLensHelp)
                setPositiveButton(R.string.Close) { _: DialogInterface, _: Int -> }
            }.create().show()
        }
        newCamera.lens?.let {
            binding.fixedLensText.text = it.name
            binding.lensClear.visibility = View.VISIBLE
        } ?: run {
            binding.fixedLensText.text = resources.getString(R.string.ClickToSet)
            binding.lensClear.visibility = View.GONE
        }
        binding.fixedLensLayout.setOnClickListener {
            val dialog = EditLensDialog()
            dialog.setTargetFragment(this@EditCameraDialog, EDIT_LENS)
            val arguments = Bundle()
            newCamera.lens?.let {
                arguments.putParcelable(ExtraKeys.LENS, it)
            }
            arguments.putString(ExtraKeys.TITLE, resources.getString(R.string.SetFixedLens))
            arguments.putString(ExtraKeys.POSITIVE_BUTTON, resources.getString(R.string.OK))
            dialog.arguments = arguments
            dialog.show(parentFragmentManager.beginTransaction(), EditLensDialog.TAG)
        }
        binding.lensClear.setOnClickListener {
            newCamera.lens = null
            binding.fixedLensText.text = resources.getString(R.string.ClickToSet)
            binding.lensClear.visibility = View.GONE
        }


        // FINALISE BUILDING THE DIALOG
        alert.setPositiveButton(positiveButton, null)
        alert.setNegativeButton(R.string.Cancel) { _: DialogInterface?, _: Int ->
            val intent = Intent()
            targetFragment?.onActivityResult(targetRequestCode, Activity.RESULT_CANCELED, intent)
        }
        val dialog = alert.create()

        // SOFT_INPUT_ADJUST_PAN: set to have a window pan when an input method is shown,
        // so it doesn't need to deal with resizing
        // but just panned by the framework to ensure the current input focus is visible
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)

        dialog.show()

        // We override the positive button onClick so that we can dismiss the dialog
        // only when both make and model are set.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val make = binding.makeEditText.text.toString()
            val model = binding.modelEditText.text.toString()
            val serialNumber = binding.serialNumberEditText.text.toString()
            if (make.isEmpty() && model.isEmpty()) {
                // No make or model was set
                Toast.makeText(activity, resources.getString(R.string.NoMakeOrModel), Toast.LENGTH_SHORT).show()
            } else if (make.isNotEmpty() && model.isEmpty()) {
                // No model was set
                Toast.makeText(activity, resources.getString(R.string.NoModel), Toast.LENGTH_SHORT).show()
            } else if (make.isEmpty()) {
                // No make was set
                Toast.makeText(activity, resources.getString(R.string.NoMake), Toast.LENGTH_SHORT).show()
            } else {

                camera.make = make
                camera.model = model
                camera.serialNumber = serialNumber
                camera.shutterIncrements = Increment.from(binding.shutterSpeedIncrementSpinner.selectedItemPosition)
                camera.minShutter = newCamera.minShutter
                camera.maxShutter = newCamera.maxShutter
                camera.exposureCompIncrements = PartialIncrement.from(binding.exposureCompIncrementSpinner.selectedItemPosition)
                val previousLens = camera.lens
                camera.lens = newCamera.lens
                val currentLens = camera.lens

                // If the fixed lens was removed.
                if (previousLens != null && currentLens == null) {
                    database.deleteLens(previousLens)
                }
                // If the fixed lens was set.
                else if (currentLens != null) {
                    if (database.updateLens(currentLens) == 0) {
                        // New fixed lens.
                        database.addLens(currentLens)
                    }
                    // Remove linked lenses for this camera
                    // because it was converted to a fixed lens camera.
                    database.getLinkedLenses(camera)
                        .forEach { database.deleteCameraLensLink(camera, it) }
                }

                if (database.updateCamera(camera) == 0) {
                    database.addCamera(camera)
                }

                // Return the new entered name to the calling activity
                val intent = Intent()
                intent.putExtra(ExtraKeys.CAMERA, camera)
                dialog.dismiss()
                targetFragment?.onActivityResult(targetRequestCode, Activity.RESULT_OK, intent)
            }
        }
        return dialog
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == EDIT_LENS && resultCode == Activity.RESULT_OK) {
            // After Ok code.
            val lens: Lens = data?.getParcelableExtra(ExtraKeys.LENS) ?: return
            newCamera.lens = lens
            binding.fixedLensText.text = lens.name
            binding.lensClear.visibility = View.VISIBLE
        }
    }

    /**
     * Called when the shutter speed range dialog is opened.
     * Sets the values for the NumberPickers.
     *
     * @param minShutterPicker NumberPicker associated with the minimum shutter speed
     * @param maxShutterPicker NumberPicker associated with the maximum shutter speed
     */
    private fun initialiseShutterRangePickers(minShutterPicker: NumberPicker,
                                              maxShutterPicker: NumberPicker) {
        displayedShutterValues = when (newCamera.shutterIncrements) {
            Increment.THIRD -> requireActivity().resources.getStringArray(R.array.ShutterValuesThird)
            Increment.HALF -> requireActivity().resources.getStringArray(R.array.ShutterValuesHalf)
            Increment.FULL -> requireActivity().resources.getStringArray(R.array.ShutterValuesFull)
        }
        if (displayedShutterValues[0] == resources.getString(R.string.NoValue)) {
            displayedShutterValues.reverse()
        }
        minShutterPicker.minValue = 0
        maxShutterPicker.minValue = 0
        minShutterPicker.maxValue = displayedShutterValues.size - 1
        maxShutterPicker.maxValue = displayedShutterValues.size - 1
        minShutterPicker.displayedValues = displayedShutterValues
        maxShutterPicker.displayedValues = displayedShutterValues
        minShutterPicker.value = displayedShutterValues.size - 1
        maxShutterPicker.value = displayedShutterValues.size - 1
        val initialMinValue = displayedShutterValues.indexOfFirst { it == newCamera.minShutter }
        if (initialMinValue != -1) minShutterPicker.value = initialMinValue
        val initialMaxValue = displayedShutterValues.indexOfFirst { it == newCamera.maxShutter }
        if (initialMaxValue != -1) maxShutterPicker.value = initialMaxValue
    }

    /**
     * Update the shutter speed range Button to display the currently selected shutter speed range.
     */
    private fun updateShutterRangeTextView() {
        binding.shutterRangeText.text =
                if (newCamera.minShutter == null || newCamera.maxShutter == null) resources.getString(R.string.ClickToSet)
                else "${newCamera.minShutter} - ${newCamera.maxShutter}"
    }

}