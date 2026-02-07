//package com.juliacai.apptick
//
//import android.content.Context
//import android.net.Uri
//import android.os.Bundle
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.AdapterView
//import android.widget.ArrayAdapter
//import android.widget.Button
//import android.widget.Toast
//import androidx.core.view.isVisible
//import androidx.fragment.app.Fragment
//import com.google.android.material.dialog.MaterialAlertDialogBuilder
//import com.juliacai.apptick.databinding.FragmentSetTimeLimitsBinding
//
//class SetTimeLimitsFragment : Fragment(), AdapterView.OnItemSelectedListener {
//
//    private var _binding: FragmentSetTimeLimitsBinding? = null
//    private val binding get() = _binding!!
//
//    private lateinit var settings: AppLimitSettings
//    private var listener: OnFragmentInteractionListener? = null
//
//    private lateinit var dayButtons: List<Button>
//
//    override fun onAttach(context: Context) {
//        super.onAttach(context)
//        settings = context as? AppLimitSettings
//            ?: throw RuntimeException("$context must implement AppLimitSettings")
//        listener = context as? OnFragmentInteractionListener
//            ?: throw RuntimeException("$context must implement OnFragmentInteractionListener")
//    }
//
//    override fun onCreateView(
//        inflater: LayoutInflater, container: ViewGroup?,
//        savedInstanceState: Bundle?
//    ): View {
//        _binding = FragmentSetTimeLimitsBinding.inflate(inflater, container, false)
//        return binding.root
//    }
//
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//
//        dayButtons = listOf(
//            binding.Button, binding.Button2, binding.Button3, binding.Button4,
//            binding.Button5, binding.Button6, binding.Button7
//        )
//
//        setupListeners()
//        setupSpinner()
//        loadInitialData()
//    }
//
//    private fun setupListeners() {
//        binding.useTimeLimitSwitch.setOnCheckedChangeListener { _, isChecked ->
//            binding.timeLimitLayout.isVisible = isChecked
//            if (!isChecked) {
//                showNoTimeLimitWarning()
//            }
//        }
//
//        binding.useTimeRangeSwitch.setOnCheckedChangeListener { _, isChecked ->
//            binding.timeRangeLayout.isVisible = isChecked
//            settings.useTimeRange = isChecked
//        }
//
//        binding.startTimePicker.is24HourView = true
//        binding.endTimePicker.is24HourView = true
//
//        binding.startTimePicker.setOnTimeChangedListener { _, hour, minute ->
//            settings.startHour = hour
//            settings.startMinute = minute
//        }
//        binding.endTimePicker.setOnTimeChangedListener { _, hour, minute ->
//            settings.endHour = hour
//            settings.endMinute = minute
//        }
//
//        dayButtons.forEachIndexed { index, button ->
//            button.setOnClickListener { view ->
//                view.isSelected = !view.isSelected
//                val day = index + 1 // Monday is 1, etc.
//                val newWeekDays = settings.weekDays.toMutableList()
//                if (view.isSelected) {
//                    if (!newWeekDays.contains(day)) newWeekDays.add(day)
//                } else {
//                    newWeekDays.remove(day)
//                }
//                settings.weekDays = newWeekDays.sorted()
//            }
//        }
//
//        binding.checkBoxCumulative.setOnCheckedChangeListener { _, isChecked ->
//            settings.cumulativeTime = isChecked
//            if (isChecked) {
//                binding.checkBoxHourlyReset.isChecked = true
//                binding.checkBoxHourlyReset.isEnabled = false
//                binding.hourlyResetLayout.isVisible = true
//                binding.checkBoxHourlyReset.text = "Add More Time Periodically"
//                binding.resetDescription.text = "Additional time will be added after each interval. Unused time carries over."
//            } else {
//                binding.checkBoxHourlyReset.isEnabled = true
//                binding.checkBoxHourlyReset.text = "Reset Limits Periodically"
//                binding.resetDescription.text = "Reset time limits after a specified number of hours."
//            }
//        }
//
//        binding.checkBoxHourlyReset.setOnCheckedChangeListener { _, isChecked ->
//            binding.hourlyResetLayout.isVisible = isChecked
//            if (!isChecked) {
//                binding.editTextResetHours.text?.clear()
//                settings.resetHours = 0
//            }
//        }
//
//        binding.buttonFinish.setOnClickListener {
//            if (saveFinalSettings()) {
//                settings.finished()
//            }
//        }
//    }
//
//    private fun setupSpinner() {
//        ArrayAdapter.createFromResource(
//            requireContext(), R.array.dwm_array, android.R.layout.simple_spinner_item
//        ).also { adapter ->
//            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
//            binding.spinnerDwm.adapter = adapter
//        }
//        binding.spinnerDwm.onItemSelectedListener = this
//    }
//
//    private fun loadInitialData() {
//        binding.editTextName.setText(settings.groupName)
//
//        val hasTimeLimit = settings.timeHrLimit > 0 || settings.timeMinLimit > 0
//        binding.useTimeLimitSwitch.isChecked = hasTimeLimit
//        binding.timeLimitLayout.isVisible = hasTimeLimit
//        binding.editTextTimeLimitHr.setText(settings.timeHrLimit.toString())
//        binding.editTextTimeLimitMin.setText(settings.timeMinLimit.toString())
//        binding.eachAppSwitch.isChecked = settings.limitEach
//
//        binding.useTimeRangeSwitch.isChecked = settings.useTimeRange
//        binding.timeRangeLayout.isVisible = settings.useTimeRange
//        binding.startTimePicker.hour = settings.startHour
//        binding.startTimePicker.minute = settings.startMinute
//        binding.endTimePicker.hour = settings.endHour
//        binding.endTimePicker.minute = settings.endMinute
//
//        settings.weekDays.forEach { day ->
//            if (day in 1..7) dayButtons[day - 1].isSelected = true
//        }
//
//        binding.checkBoxCumulative.isChecked = settings.cumulativeTime
//        binding.checkBoxHourlyReset.isChecked = settings.resetHours > 0
//        binding.hourlyResetLayout.isVisible = settings.resetHours > 0
//        if (settings.resetHours > 0) {
//            binding.editTextResetHours.setText(settings.resetHours.toString())
//        }
//        binding.checkBoxCumulative.let { it.onCheckedChanged(it, it.isChecked) } // Trigger listener
//
//        val dwmArray = resources.getStringArray(R.array.dwm_array)
//        val dwmPosition = dwmArray.indexOf(settings.dwm).coerceAtLeast(0)
//        binding.spinnerDwm.setSelection(dwmPosition)
//    }
//
//    private fun saveFinalSettings(): Boolean {
//        settings.groupName = binding.editTextName.text.toString()
//
//        if (binding.useTimeLimitSwitch.isChecked) {
//            settings.timeHrLimit = binding.editTextTimeLimitHr.text.toString().toIntOrNull() ?: 0
//            settings.timeMinLimit = binding.editTextTimeLimitMin.text.toString().toIntOrNull() ?: 0
//            settings.limitEach = binding.eachAppSwitch.isChecked
//        } else {
//            settings.timeHrLimit = 0
//            settings.timeMinLimit = 0
//            settings.limitEach = false
//        }
//
//        if (binding.checkBoxHourlyReset.isChecked) {
//            val hours = binding.editTextResetHours.text.toString().toIntOrNull() ?: 0
//            if (hours <= 0) {
//                Toast.makeText(requireContext(), "Please enter a valid reset period in hours.", Toast.LENGTH_SHORT).show()
//                return false
//            }
//            settings.resetHours = hours
//        } else {
//            settings.resetHours = 0
//        }
//        return true
//    }
//
//    private fun showNoTimeLimitWarning() {
//        MaterialAlertDialogBuilder(requireContext())
//            .setTitle("No Time Limit")
//            .setMessage("Without a time limit, apps in this group will not be blocked. Continue?")
//            .setPositiveButton("Continue", null)
//            .setNegativeButton("Cancel") { _, _ -> binding.useTimeLimitSwitch.isChecked = true }
//            .show()
//    }
//
//    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
//        settings.dwm = parent?.getItemAtPosition(position).toString()
//    }
//
//    override fun onNothingSelected(parent: AdapterView<*>?) {
//        settings.dwm = null
//    }
//
//    override fun onDestroyView() {
//        super.onDestroyView()
//        saveFinalSettings()
//        _binding = null
//    }
//
//    override fun onDetach() {
//        super.onDetach()
//        listener = null
//    }
//
//    interface OnFragmentInteractionListener {
//        fun onFragmentInteraction(uri: Uri)
//    }
//}