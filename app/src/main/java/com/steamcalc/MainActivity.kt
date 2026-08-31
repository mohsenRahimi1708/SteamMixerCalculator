package com.steamcalc

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.app.Activity
import com.steamcalc.calculator.SteamMixerCalculator

class MainActivity : Activity() {

    private lateinit var editSteamFlow: EditText
    private lateinit var editSteamPressure: EditText
    private lateinit var editSteamTemp: EditText
    private lateinit var editSprayFlow: EditText
    private lateinit var editSprayTemp: EditText
    private lateinit var btnCalculate: Button
    private lateinit var cardResults: LinearLayout
    private lateinit var cardWarning: LinearLayout
    private lateinit var textResultTemp: TextView
    private lateinit var textResultDelta: TextView
    private lateinit var textResultFlow: TextView
    private lateinit var textWarning: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editSteamFlow = findViewById(R.id.editSteamFlow)
        editSteamPressure = findViewById(R.id.editSteamPressure)
        editSteamTemp = findViewById(R.id.editSteamTemp)
        editSprayFlow = findViewById(R.id.editSprayFlow)
        editSprayTemp = findViewById(R.id.editSprayTemp)
        btnCalculate = findViewById(R.id.btnCalculate)
        cardResults = findViewById(R.id.cardResults)
        cardWarning = findViewById(R.id.cardWarning)
        textResultTemp = findViewById(R.id.textResultTemp)
        textResultDelta = findViewById(R.id.textResultDelta)
        textResultFlow = findViewById(R.id.textResultFlow)
        textWarning = findViewById(R.id.textWarning)

        btnCalculate.setOnClickListener { onCalculate() }
    }

    private fun onCalculate() {
        val steamFlow = editSteamFlow.text?.toString()?.toDoubleOrNull()
        val steamPressure = editSteamPressure.text?.toString()?.toDoubleOrNull()
        val steamTemp = editSteamTemp.text?.toString()?.toDoubleOrNull()
        val sprayFlow = editSprayFlow.text?.toString()?.toDoubleOrNull()
        val sprayTemp = editSprayTemp.text?.toString()?.toDoubleOrNull()

        if (steamFlow == null || steamPressure == null || steamTemp == null ||
            sprayFlow == null || sprayTemp == null
        ) {
            showError("Please fill in all fields with valid numbers.")
            return
        }

        // Validate
        val validation = SteamMixerCalculator.validate(
            steamFlow, steamPressure, steamTemp, sprayFlow, sprayTemp
        )
        if (validation is SteamMixerCalculator.ValidationResult.Invalid) {
            showError(validation.message)
            return
        }

        // Calculate
        val result = SteamMixerCalculator.calculate(
            steamFlow, steamPressure, steamTemp, sprayFlow, sprayTemp
        )

        if (result == null) {
            showError("Calculation failed. Check inputs.")
            return
        }

        if (result.warning != null) {
            showWarning(result.warning)
            cardResults.visibility = View.GONE
        } else {
            cardWarning.visibility = View.GONE
            cardResults.visibility = View.VISIBLE

            textResultTemp.text = "Outlet steam temperature: ${"%.2f".format(result.outletTempC)} °C"
            textResultDelta.text = "Temperature drop (ΔT): ${"%.2f".format(result.deltaT)} °C"
            textResultFlow.text = "Combined mass flow: ${"%.2f".format(result.combinedFlow)} t/h"
        }
    }

    private fun showError(message: String) {
        cardResults.visibility = View.GONE
        cardWarning.visibility = View.VISIBLE
        textWarning.text = message
        textWarning.setTextColor(getColor(android.R.color.holo_red_dark))
    }

    private fun showWarning(message: String) {
        cardWarning.visibility = View.VISIBLE
        textWarning.text = message
        textWarning.setTextColor(getColor(android.R.color.holo_red_dark))
    }
}
