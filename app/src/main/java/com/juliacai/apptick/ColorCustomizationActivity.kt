//package com.juliacai.apptick
//
//import android.content.Context
//import android.graphics.Color
//import android.os.Bundle
//import android.view.View
//import androidx.appcompat.app.AppCompatActivity
//import androidx.core.content.ContextCompat
//import com.juliacai.apptick.databinding.ActivityColorCustomizationBinding
//import yuku.ambilwarna.AmbilWarnaDialog
//
//class ColorCustomizationActivity : AppCompatActivity() {
//
//    private lateinit var binding: ActivityColorCustomizationBinding
//    private var defaultColor: Int = 0
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        binding = ActivityColorCustomizationBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        defaultColor = ContextCompat.getColor(this, R.color.colorPrimary)
//
//        binding.colorPrimary.setOnClickListener { openColorPicker("colorPrimary") }
//        binding.colorPrimaryDark.setOnClickListener { openColorPicker("colorPrimaryDark") }
//        binding.colorAccent.setOnClickListener { openColorPicker("colorAccent") }
//        binding.resetButton.setOnClickListener { resetColors() }
//
//        loadCustomColors()
//    }
//
//    private fun openColorPicker(colorType: String) {
//        val colorPickerDialog = AmbilWarnaDialog(this, defaultColor, object : AmbilWarnaDialog.OnAmbilWarnaListener {
//            override fun onCancel(dialog: AmbilWarnaDialog?) {}
//
//            override fun onOk(dialog: AmbilWarnaDialog?, color: Int) {
//                saveColor(colorType, color)
//                recreate()
//            }
//        })
//        colorPickerDialog.show()
//    }
//
//    private fun saveColor(colorType: String, color: Int) {
//        val sharedPreferences = getSharedPreferences("ColorPrefs", Context.MODE_PRIVATE)
//        val editor = sharedPreferences.edit()
//        editor.putInt(colorType, color)
//        editor.apply()
//    }
//
//    private fun loadCustomColors() {
//        val sharedPreferences = getSharedPreferences("ColorPrefs", Context.MODE_PRIVATE)
//        val colorPrimary = sharedPreferences.getInt("colorPrimary", ContextCompat.getColor(this, R.color.colorPrimary))
//        val colorPrimaryDark = sharedPreferences.getInt("colorPrimaryDark", ContextCompat.getColor(this, R.color.colorPrimaryDark))
//        val colorAccent = sharedPreferences.getInt("colorAccent", ContextCompat.getColor(this, R.color.colorAccent))
//
//        binding.colorPrimary.setBackgroundColor(colorPrimary)
//        binding.colorPrimaryDark.setBackgroundColor(colorPrimaryDark)
//        binding.colorAccent.setBackgroundColor(colorAccent)
//    }
//
//    private fun resetColors() {
//        val sharedPreferences = getSharedPreferences("ColorPrefs", Context.MODE_PRIVATE)
//        val editor = sharedPreferences.edit()
//        editor.remove("colorPrimary")
//        editor.remove("colorPrimaryDark")
//        editor.remove("colorAccent")
//        editor.apply()
//        recreate()
//    }
//}
