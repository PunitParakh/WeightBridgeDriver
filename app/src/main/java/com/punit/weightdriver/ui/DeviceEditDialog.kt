package com.punit.weightdriver.ui

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.punit.weightdriver.R
import com.punit.weightdriver.core.UsbReaderService
import com.punit.weightdriver.data.DeviceProfile
import com.punit.weightdriver.data.DeviceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeviceEditDialog : DialogFragment() {

    private var profileId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        profileId = requireArguments().getLong(ARG_ID, 0L)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val v = requireActivity().layoutInflater.inflate(R.layout.dialog_device_edit, null, false)

        val edtName: EditText = v.findViewById(R.id.edtName)
        val edtRegex: EditText = v.findViewById(R.id.edtRegex)
        val edtTerm: EditText = v.findViewById(R.id.edtTerminator)
        val edtPortIdx: EditText = v.findViewById(R.id.edtPortIndex)

        val spBaud: Spinner = v.findViewById(R.id.spBaud)
        val spDataBits: Spinner = v.findViewById(R.id.spDataBits)
        val spStopBits: Spinner = v.findViewById(R.id.spStopBits)
        val spParity: Spinner = v.findViewById(R.id.spParity)

        val txtSerial: TextView = v.findViewById(R.id.txtSerial)

        spBaud.adapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.baud_rates,
            android.R.layout.simple_spinner_dropdown_item
        )
        spDataBits.adapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.data_bits,
            android.R.layout.simple_spinner_dropdown_item
        )
        spStopBits.adapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.stop_bits,
            android.R.layout.simple_spinner_dropdown_item
        )
        spParity.adapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.parity_modes,
            android.R.layout.simple_spinner_dropdown_item
        )

        val repo = DeviceRepository.getInstance(requireContext())

        // load profile
        lifecycleScope.launch(Dispatchers.IO) {
            val p = repo.byId(profileId)
            withContext(Dispatchers.Main) {
                p?.let { profile ->
                    // populate UI
                    edtName.setText(profile.displayName ?: "")
                    edtRegex.setText(profile.weightRegex)
                    edtTerm.setText(profile.lineTerminator)
                    edtPortIdx.setText(profile.portIndex.toString())

                    // show serial (null safe)
                    txtSerial.text = profile.serial ?: "Unknown"

                    // set spinner selections, use fallback indices
                    val baudArr = resources.getStringArray(R.array.baud_rates)
                    spBaud.setSelection(baudArr.indexOf(profile.baudRate.toString()).coerceAtLeast(0))
                    val dataArr = resources.getStringArray(R.array.data_bits)
                    spDataBits.setSelection(dataArr.indexOf(profile.dataBits.toString()).coerceAtLeast(0))
                    val stopArr = resources.getStringArray(R.array.stop_bits)
                    spStopBits.setSelection(stopArr.indexOf(profile.stopBits.toString()).coerceAtLeast(0))
                    spParity.setSelection(profile.parity.coerceIn(0, 4))
                }
            }
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Edit Device")
            .setView(v)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val btnSave: Button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btnSave.setOnClickListener {
                // Validate & save
                lifecycleScope.launch(Dispatchers.IO) {
                    val repoIO = DeviceRepository.getInstance(requireContext())
                    val existing = repoIO.byId(profileId) ?: return@launch
                    val updated = existing.copy(
                        displayName = edtName.text?.toString()?.takeIf { it.isNotBlank() },
                        baudRate = (spBaud.selectedItem as String).toIntOrNull() ?: existing.baudRate,
                        dataBits = (spDataBits.selectedItem as String).toIntOrNull() ?: existing.dataBits,
                        stopBits = (spStopBits.selectedItem as String).toIntOrNull() ?: existing.stopBits,
                        parity = spParity.selectedItemPosition,
                        lineTerminator = edtTerm.text?.toString()?.ifBlank { "\\r?\\n" } ?: "\\r?\\n",
                        weightRegex = edtRegex.text?.toString()?.ifBlank { "([0-9]+(?:\\.[0-9]+)?)" } ?: "([0-9]+(?:\\.[0-9]+)?)",
                        portIndex = edtPortIdx.text?.toString()?.toIntOrNull() ?: existing.portIndex
                    )
                    repoIO.update(updated)

                    // notify service to reload profile if needed
                    val i = Intent(UsbReaderService.ACTION_PROFILE_UPDATED).apply {
                        putExtra("vid", updated.vid)
                        putExtra("pid", updated.pid)
                        putExtra("serial", updated.serial) // may be null, but safe
                    }
                    requireContext().sendBroadcast(i)

                    withContext(Dispatchers.Main) {
                        dialog.dismiss()
                    }
                }
            }
        }

        return dialog
    }

    companion object {
        private const val ARG_ID = "id"
        fun newInstance(id: Long): DeviceEditDialog {
            val d = DeviceEditDialog()
            d.arguments = Bundle().apply { putLong(ARG_ID, id) }
            return d
        }
    }
}
