package com.myra.assistant.ui.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.myra.assistant.R
import com.myra.assistant.service.AccessibilityHelperService
import org.json.JSONArray
import org.json.JSONObject

data class PrimeContact(val name: String, val number: String)

class PrimeContactAdapter(
    private val contacts: MutableList<PrimeContact>,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<PrimeContactAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.primeItemName)
        val number: TextView = v.findViewById(R.id.primeItemNumber)
        val delete: ImageButton = v.findViewById(R.id.primeItemDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_prime_contact, parent, false))

    override fun getItemCount() = contacts.size

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val c = contacts[pos]
        holder.name.text = c.name
        holder.number.text = c.number
        holder.delete.setOnClickListener { onDelete(pos) }
    }
}

class SettingsActivity : AppCompatActivity() {

    private lateinit var apiKeyInput: EditText
    private lateinit var nameInput: EditText
    private lateinit var modelSpinner: Spinner
    private lateinit var voiceSpinner: Spinner
    private lateinit var personalityGroup: RadioGroup
    private lateinit var primeRecycler: RecyclerView
    private lateinit var accessibilityStatus: TextView
    private lateinit var saveButton: Button
    private lateinit var addPrimeButton: Button

    private val primeContacts = mutableListOf<PrimeContact>()
    private lateinit var primeAdapter: PrimeContactAdapter

    private val models = listOf(
        "models/gemini-2.5-flash-native-audio-preview-12-2025",
        "models/gemini-2.0-flash-live-001",
        "models/gemini-2.5-flash-preview-native-audio-dialog"
    )
    private val modelLabels = listOf("Native Audio (Default)", "Flash Live (Fast)", "Pro Audio Dialog")
    private val voices = listOf("Aoede", "Charon", "Kore", "Fenrir", "Puck", "Leda", "Orus", "Zephyr")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        apiKeyInput = findViewById(R.id.apiKeyInput)
        nameInput = findViewById(R.id.nameInput)
        modelSpinner = findViewById(R.id.modelSpinner)
        voiceSpinner = findViewById(R.id.voiceSpinner)
        personalityGroup = findViewById(R.id.personalityGroup)
        primeRecycler = findViewById(R.id.primeRecycler)
        accessibilityStatus = findViewById(R.id.accessibilityStatus)
        saveButton = findViewById(R.id.saveButton)
        addPrimeButton = findViewById(R.id.addPrimeButton)

        setupSpinners()
        setupPrimeContacts()
        loadSettings()
        updateAccessibilityStatus()

        saveButton.setOnClickListener { saveSettings() }
        addPrimeButton.setOnClickListener { showAddContactDialog() }
        accessibilityStatus.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun setupSpinners() {
        modelSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modelLabels)
        voiceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, voices)
    }

    private fun setupPrimeContacts() {
        primeAdapter = PrimeContactAdapter(primeContacts) { pos ->
            primeContacts.removeAt(pos)
            primeAdapter.notifyItemRemoved(pos)
        }
        primeRecycler.layoutManager = LinearLayoutManager(this)
        primeRecycler.adapter = primeAdapter
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("myra_prefs", 0)
        apiKeyInput.setText(prefs.getString("api_key", ""))
        nameInput.setText(prefs.getString("user_name", ""))

        val savedModel = prefs.getString("gemini_model", models[0]) ?: models[0]
        modelSpinner.setSelection(models.indexOf(savedModel).coerceAtLeast(0))

        val savedVoice = prefs.getString("gemini_voice", "Aoede") ?: "Aoede"
        voiceSpinner.setSelection(voices.indexOf(savedVoice).coerceAtLeast(0))

        val personality = prefs.getString("personality_mode", "GF") ?: "GF"
        when (personality) {
            "GF" -> personalityGroup.check(R.id.radioGf)
            "Professional" -> personalityGroup.check(R.id.radioProfessional)
            "Assistant" -> personalityGroup.check(R.id.radioAssistant)
        }

        // Load prime contacts
        val json = prefs.getString("prime_contacts_json", null)
        if (json != null) {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                primeContacts.add(PrimeContact(obj.getString("name"), obj.getString("number")))
            }
            primeAdapter.notifyDataSetChanged()
        }
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences("myra_prefs", 0).edit()
        prefs.putString("api_key", apiKeyInput.text.toString())
        prefs.putString("user_name", nameInput.text.toString())
        prefs.putString("gemini_model", models[modelSpinner.selectedItemPosition])
        prefs.putString("gemini_voice", voices[voiceSpinner.selectedItemPosition])

        val personality = when (personalityGroup.checkedRadioButtonId) {
            R.id.radioGf -> "GF"
            R.id.radioProfessional -> "Professional"
            else -> "Assistant"
        }
        prefs.putString("personality_mode", personality)

        val arr = JSONArray()
        for (c in primeContacts) {
            arr.put(JSONObject().put("name", c.name).put("number", c.number))
        }
        prefs.putString("prime_contacts_json", arr.toString())
        prefs.apply()

        Toast.makeText(this, "Saved! Restart app to apply changes", Toast.LENGTH_LONG).show()
    }

    private fun showAddContactDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_prime_contact, null)
        val nameEt = view.findViewById<EditText>(R.id.dialogNameInput)
        val numEt = view.findViewById<EditText>(R.id.dialogNumberInput)
        AlertDialog.Builder(this)
            .setTitle("Add Prime Contact")
            .setView(view)
            .setPositiveButton("Add") { _, _ ->
                val n = nameEt.text.toString()
                val num = numEt.text.toString()
                if (n.isNotEmpty() && num.isNotEmpty()) {
                    primeContacts.add(PrimeContact(n, num))
                    primeAdapter.notifyItemInserted(primeContacts.size - 1)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateAccessibilityStatus() {
        val enabled = AccessibilityHelperService.isEnabled(this)
        accessibilityStatus.text = if (enabled) "Accessibility: ✅ Enabled" else "Accessibility: ❌ Disabled (Tap to enable)"
        accessibilityStatus.setTextColor(if (enabled) 0xFF00E676.toInt() else 0xFFFF1744.toInt())
    }
}
