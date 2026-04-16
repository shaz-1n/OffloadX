package com.example.offload.ui.main
import com.example.offload.R

import com.example.offload.ui.auth.*
import com.example.offload.ui.main.*
import com.example.offload.ui.adapter.*
import com.example.offload.viewmodel.*
import com.example.offload.model.*
import com.example.offload.data.local.*
import com.example.offload.data.remote.*
import com.example.offload.engine.*



import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.content.Context
import android.os.BatteryManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class UploadFragment : Fragment() {

    private lateinit var logRepo: OffloadLogRepository

    private val viewModel: SharedViewModel by activityViewModels()

    private var selectedMode: String = "COMPOSITE"
    private var selectedUri: Uri? = null
    private var actualFileSizeMB: Double = 0.0
    private var lastLocalBenchmarkMs: Long = 0L

    // Processing modes per file type (chosen by dialogs)
    private var selectedImageMode: String = "GRAYSCALE"
    private var selectedPdfMode: String = "ANALYZE"
    private var selectedTextMode: String = "WORD_COUNT"
    private var selectedVideoMode: String = "FACE_DETECTION"

    // Routing: true = Auto (decision engine), false = Manual
    private var isAutoRouting: Boolean = true

    // View references (using findViewById instead of ViewBinding to avoid stale binding class crashes)

    private var etHubIp: TextInputEditText? = null
    private var etTaskTitle: TextInputEditText? = null
    private var tvFileStatus: TextView? = null
    private var btnRoutingAuto: Button? = null
    private var btnRoutingManual: Button? = null
    private var tvAutoRouteInfo: TextView? = null
    private var layoutManualOptions: LinearLayout? = null
    private var cbLocal: CheckBox? = null
    private var cbCloud: CheckBox? = null
    private var tvManualRouteInfo: TextView? = null
    private var btnModeComposite: Button? = null
    private var btnModeComplex: Button? = null
    private var btnSearchFile: MaterialCardView? = null
    private var btnFinalUpload: Button? = null

    // File picker launcher
    private val filePickerLauncher = registerForActivityResult(
    ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
    if (uri != null) {
    selectedUri = uri
    analyzeFile(uri)
    // Enable the upload button now that a file is selected
    btnFinalUpload?.isEnabled = true
    btnFinalUpload?.alpha = 1.0f
    }
    }

    override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
    ): View? {
    return inflater.inflate(R.layout.fragment_upload, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    logRepo = OffloadLogRepository(requireContext())

    // Find all views manually — avoids any generated binding class issues

    etHubIp = view.findViewById(R.id.etHubIp)
    etTaskTitle = view.findViewById(R.id.etTaskTitle)
    tvFileStatus = view.findViewById(R.id.tvFileStatus)
    btnRoutingAuto = view.findViewById(R.id.btnRoutingAuto)
    btnRoutingManual = view.findViewById(R.id.btnRoutingManual)
    tvAutoRouteInfo = view.findViewById(R.id.tvAutoRouteInfo)
    layoutManualOptions = view.findViewById(R.id.layoutManualOptions)
    cbLocal = view.findViewById(R.id.cbLocal)
    cbCloud = view.findViewById(R.id.cbCloud)
    tvManualRouteInfo = view.findViewById(R.id.tvManualRouteInfo)
    btnModeComposite = view.findViewById(R.id.btnModeComposite)
    btnModeComplex = view.findViewById(R.id.btnModeComplex)
    btnSearchFile = view.findViewById(R.id.btnSearchFile)
    btnFinalUpload = view.findViewById(R.id.btnFinalUpload)

    // Disable upload button initially — requires a file to be selected first
    btnFinalUpload?.isEnabled = false
    btnFinalUpload?.alpha = 0.4f

    val prefs = requireActivity().getSharedPreferences("OffloadXPrefs", Context.MODE_PRIVATE)
    etHubIp?.setText(prefs.getString("hub_ip", "192.168.1.100:8000"))



    // --- DIRECT REDIRECTION: Listen for shared files from outside the app ---
    viewModel.sharedUri.observe(viewLifecycleOwner) { uri ->
    if (uri != null) {
    selectedMode = "DIRECT REDIRECTION"
    selectedUri = uri
    tvFileStatus?.text = "Mode: Direct Redirection (Shared File)"
    analyzeFile(uri)
    btnFinalUpload?.isEnabled = true
    btnFinalUpload?.alpha = 1.0f
    viewModel.setSharedUri(null)
    }
    }

    // ── Routing Strategy Toggle ────────────────────────────────────────────
    btnRoutingAuto?.setOnClickListener {
    isAutoRouting = true
    setRoutingUI(auto = true)
    }

    btnRoutingManual?.setOnClickListener {
    isAutoRouting = false
    setRoutingUI(auto = false)
    }

    // Manual sub-option: mutual exclusion between Local and Hub/Cloud
    cbLocal?.setOnCheckedChangeListener { _, isChecked ->
    if (isChecked) {
    cbCloud?.isChecked = false
    tvManualRouteInfo?.text = "Local: Process entirely on-device."
    }
    }

    cbCloud?.setOnCheckedChangeListener { _, isChecked ->
    if (isChecked) {
    cbLocal?.isChecked = false
    tvManualRouteInfo?.text = "Hub/Cloud: Send to edge node or cloud server."
    }
    }

    // ── Execution Mode Buttons ─────────────────────────────────────────────
    btnModeComposite?.setOnClickListener {
    selectedMode = "COMPOSITE"
    tvFileStatus?.text = "Select File for Upload"
    activateModeButton(composite = true)
    }

    btnModeComplex?.setOnClickListener {
    selectedMode = "COMPLEX"
    tvFileStatus?.text = "Select File for Upload"
    activateModeButton(composite = false)
    }

    // ── File Picker ────────────────────────────────────────────────────────
    btnSearchFile?.setOnClickListener {
    filePickerLauncher.launch(arrayOf("*/*"))
    }

    // ── Upload Button ──────────────────────────────────────────────────────
    btnFinalUpload?.setOnClickListener {
    if (selectedUri == null) {
    Toast.makeText(context, "Select a file first", Toast.LENGTH_SHORT).show()
    return@setOnClickListener
    }

    val title = etTaskTitle?.text.toString()
    if (title.isEmpty()) {
    etTaskTitle?.error = "Title Required"
    return@setOnClickListener
    }

    val ipAddress = etHubIp?.text.toString()
    if (ipAddress.isEmpty()) {
    etHubIp?.error = "IP Required for Hub"
    return@setOnClickListener
    }

    // Validate manual routing selection
    if (!isAutoRouting && cbLocal?.isChecked != true && cbCloud?.isChecked != true) {
    Toast.makeText(context, "Pick Local or Hub/Cloud", Toast.LENGTH_SHORT).show()
    return@setOnClickListener
    }

    val mimeType = requireContext().contentResolver.getType(selectedUri!!) ?: ""
    val taskDesc = view?.findViewById<TextInputEditText>(R.id.etTaskDesc)?.text?.toString() ?: ""
    dispatchProcessingDialog(mimeType, title, ipAddress, taskDesc)
    }
    }

    // ─── Routing UI helper ────────────────────────────────────────────────────

    private fun setRoutingUI(auto: Boolean) {
    val ctx = requireContext()
    if (auto) {
    btnRoutingAuto?.backgroundTintList = android.content.res.ColorStateList.valueOf(
    androidx.core.content.ContextCompat.getColor(ctx, R.color.primary_color))
    btnRoutingAuto?.setTextColor(
    androidx.core.content.ContextCompat.getColor(ctx, R.color.white_color))
    btnRoutingManual?.backgroundTintList = android.content.res.ColorStateList.valueOf(
    androidx.core.content.ContextCompat.getColor(ctx, R.color.input_bg_color))
    btnRoutingManual?.setTextColor(
    androidx.core.content.ContextCompat.getColor(ctx, R.color.text_secondary))
    tvAutoRouteInfo?.visibility = View.VISIBLE
    layoutManualOptions?.visibility = View.GONE
    } else {
    btnRoutingManual?.backgroundTintList = android.content.res.ColorStateList.valueOf(
    androidx.core.content.ContextCompat.getColor(ctx, R.color.primary_color))
    btnRoutingManual?.setTextColor(
    androidx.core.content.ContextCompat.getColor(ctx, R.color.white_color))
    btnRoutingAuto?.backgroundTintList = android.content.res.ColorStateList.valueOf(
    androidx.core.content.ContextCompat.getColor(ctx, R.color.input_bg_color))
    btnRoutingAuto?.setTextColor(
    androidx.core.content.ContextCompat.getColor(ctx, R.color.text_secondary))
    tvAutoRouteInfo?.visibility = View.GONE
    layoutManualOptions?.visibility = View.VISIBLE
    }
    }

    private fun activateModeButton(composite: Boolean) {
    val ctx = requireContext()
    if (composite) {
    btnModeComposite?.backgroundTintList = android.content.res.ColorStateList.valueOf(
    androidx.core.content.ContextCompat.getColor(ctx, R.color.primary_color))
    btnModeComposite?.setTextColor(
    androidx.core.content.ContextCompat.getColor(ctx, R.color.white_color))
    btnModeComplex?.backgroundTintList = android.content.res.ColorStateList.valueOf(
    androidx.core.content.ContextCompat.getColor(ctx, R.color.input_bg_color))
    btnModeComplex?.setTextColor(
    androidx.core.content.ContextCompat.getColor(ctx, R.color.text_secondary))
    } else {
    btnModeComplex?.backgroundTintList = android.content.res.ColorStateList.valueOf(
    androidx.core.content.ContextCompat.getColor(ctx, R.color.primary_color))
    btnModeComplex?.setTextColor(
    androidx.core.content.ContextCompat.getColor(ctx, R.color.white_color))
    btnModeComposite?.backgroundTintList = android.content.res.ColorStateList.valueOf(
    androidx.core.content.ContextCompat.getColor(ctx, R.color.input_bg_color))
    btnModeComposite?.setTextColor(
    androidx.core.content.ContextCompat.getColor(ctx, R.color.text_secondary))
    }
    }

    // ─── File-type-specific processing dialog dispatcher ─────────────────────

    /**
    * Detects the file type from MIME type and shows the appropriate
    * processing-mode dialog (or goes directly for unknown types).
    */
    private fun dispatchProcessingDialog(mimeType: String, title: String, ipAddress: String, taskDesc: String) {
    when {
    mimeType.contains("image") -> showImageModeDialog { mode ->
    selectedImageMode = mode
    startUpload(title, ipAddress, taskDesc)
    }
    mimeType.contains("video") -> showVideoModeDialog { mode ->
    selectedVideoMode = mode
    startUpload(title, ipAddress, taskDesc)
    }
    mimeType.contains("pdf") -> showPdfModeDialog { mode ->
    selectedPdfMode = mode
    startUpload(title, ipAddress, taskDesc)
    }
    mimeType.contains("text") || mimeType.contains("json") ||
    mimeType.contains("csv") || mimeType.contains("xml") -> showTextModeDialog { mode ->
    selectedTextMode = mode
    startUpload(title, ipAddress, taskDesc)
    }
    else -> startUpload(title, ipAddress, taskDesc) // docx, xlsx, zip, etc.
    }
    }

    // ─── Processing-mode dialogs ──────────────────────────────────────────────

    private fun showImageModeDialog(onModeChosen: (String) -> Unit) {
    val modeLabels = arrayOf(
    "Grayscale",
    "Object Detection (Face)",
    "Edge Detection",
    "Blur (Gaussian)",
    "Sharpen",
    "Sepia",
    "Invert (Negative)"
    )
    val modeValues = arrayOf(
    "GRAYSCALE", "OBJECT_DETECTION", "EDGE_DETECT", "BLUR", "SHARPEN", "SEPIA", "INVERT"
    )
    var idx = modeValues.indexOf(selectedImageMode).takeIf { it >= 0 } ?: 0

    com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
    .setTitle("Choose Image Processing")
    .setSingleChoiceItems(modeLabels, idx) { _, which -> idx = which }
    .setPositiveButton("Process") { _, _ -> onModeChosen(modeValues[idx]) }
    .setNegativeButton("Cancel", null)
    .show()
    }

    private fun showVideoModeDialog(onModeChosen: (String) -> Unit) {
    val modeLabels = arrayOf(
    "Face Detection (AI)",
    "Frame Analytics (Edge density report)",
    "Thumbnail Extract (First clear frame)",
    "Passthrough (Store + link)"
    )
    val modeValues = arrayOf(
    "FACE_DETECTION", "FRAME_ANALYTICS", "THUMBNAIL", "PASSTHROUGH"
    )
    var idx = modeValues.indexOf(selectedVideoMode).takeIf { it >= 0 } ?: 0

    com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
    .setTitle("Choose Video Processing")
    .setSingleChoiceItems(modeLabels, idx) { _, which -> idx = which }
    .setPositiveButton("Process") { _, _ -> onModeChosen(modeValues[idx]) }
    .setNegativeButton("Cancel", null)
    .show()
    }

    private fun showPdfModeDialog(onModeChosen: (String) -> Unit) {
    val modeLabels = arrayOf(
    "Analyze (Word / page count + metadata)",
    "Text Extract (Pull all readable text)",
    "Store & Link (Save original, return URL)"
    )
    val modeValues = arrayOf(
    "ANALYZE", "TEXT_EXTRACT", "STORE"
    )
    var idx = modeValues.indexOf(selectedPdfMode).takeIf { it >= 0 } ?: 0

    com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
    .setTitle("Choose PDF Processing")
    .setSingleChoiceItems(modeLabels, idx) { _, which -> idx = which }
    .setPositiveButton("Process") { _, _ -> onModeChosen(modeValues[idx]) }
    .setNegativeButton("Cancel", null)
    .show()
    }

    private fun showTextModeDialog(onModeChosen: (String) -> Unit) {
    val modeLabels = arrayOf(
    "Word Count (Words, lines, chars)",
    "Keyword Frequency (Top-20 words)",
    "Sentiment Scan (Positive / Negative ratio)",
    "Store & Link (Save original, return URL)"
    )
    val modeValues = arrayOf(
    "WORD_COUNT", "KEYWORD_FREQ", "SENTIMENT", "STORE"
    )
    var idx = modeValues.indexOf(selectedTextMode).takeIf { it >= 0 } ?: 0

    com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
    .setTitle("Choose Text Processing")
    .setSingleChoiceItems(modeLabels, idx) { _, which -> idx = which }
    .setPositiveButton("Process") { _, _ -> onModeChosen(modeValues[idx]) }
    .setNegativeButton("Cancel", null)
    .show()
    }

    // ─── Core upload / routing logic ─────────────────────────────────────────

    /**
    * Determines the execution route (based on Auto/Manual selection and
    * decision engine), then performs the upload or local processing.
    */
    private fun startUpload(title: String, ipAddress: String, taskDesc: String = "") {
    requireActivity().getSharedPreferences("OffloadXPrefs", Context.MODE_PRIVATE)
    .edit().putString("hub_ip", ipAddress).apply()

    val battery = getBatteryPercentage()
    val netType = getNetworkType()
    val ctx = requireContext()
    val mimeType = ctx.contentResolver.getType(selectedUri!!) ?: ""
    // Combine all chosen modes into a single extra param map
    val chosenMode = resolveFileProcessingMode(mimeType)

    val fileToUpload = selectedUri!!

    btnFinalUpload?.isEnabled = false
    btnFinalUpload?.alpha = 0.4f
    btnFinalUpload?.text = "Processing..."  // overrides "Upload Task"

    lifecycleScope.launch(Dispatchers.Default) {
    val start = System.currentTimeMillis()
    val taskStartMs = start

    // ── Quick connectivity pre-check for Hub/Cloud routes ──────────────
    val decision: ExecutionRoute = if (isAutoRouting) {
    // Decision engine decides automatically
    val engine = DecisionEngine()
    engine.decide(
    taskName = title,
    dataSizeMB = actualFileSizeMB,
    batteryPercent = battery,
    networkType = netType,
    isCloudBackupNeeded = false
    )
    } else {
    // Manual: honour user checkbox
    if (cbCloud?.isChecked == true) ExecutionRoute.HUB else ExecutionRoute.LOCAL
    }

    var routeDuration = System.currentTimeMillis() - start
    var hubComputeTimeMs: Long = 0L
    var executionStatusMsg = "Decided Route"
    var finalProcessedUrl = ""

    if (decision == ExecutionRoute.HUB || decision == ExecutionRoute.CLOUD) {
    // Fast reachability check — fail in ~2s instead of 60s timeout
    val baseUrl = if (ipAddress.startsWith("http")) ipAddress else "http://$ipAddress"
    val reachable = withContext(Dispatchers.IO) {
        var probe: java.net.HttpURLConnection? = null
        try {
            probe = java.net.URL("$baseUrl/api/health/").openConnection() as java.net.HttpURLConnection
            probe.connectTimeout = 2000
            probe.readTimeout = 2000
            probe.requestMethod = "GET"
            probe.responseCode in 200..299
        } catch (_: Exception) { false }
        finally { probe?.disconnect() }
    }

    if (!reachable) {
        withContext(Dispatchers.Main) {
            if (!isAdded) return@withContext
            btnFinalUpload?.isEnabled = true
            btnFinalUpload?.alpha = 1.0f
            btnFinalUpload?.text = "⬆  Upload Task"
            Toast.makeText(context, "Hub unreachable at $ipAddress", Toast.LENGTH_LONG).show()
        }
        return@launch
    }

    val client = HubOffloadClient(ctx)
    val tier = if (decision == ExecutionRoute.CLOUD) "CLOUD" else "HUB"
    val offloadResponse = client.offloadToHub(
    ipAddress, "android_device_1", "IMAGE_GRAYSCALE", fileToUpload, tier,
    imageMode = selectedImageMode,
    pdfMode = selectedPdfMode,
    textMode = selectedTextMode,
    videoMode = selectedVideoMode
    )

    routeDuration = System.currentTimeMillis() - start
    hubComputeTimeMs = offloadResponse.serverProcessingTimeMs

    if (offloadResponse.success) {
    val tierLabel = if (tier == "CLOUD") "Cloud" else "Hub"
    executionStatusMsg = "[$tierLabel OK] Round-trip: ${routeDuration}ms | Server compute: ${hubComputeTimeMs}ms"
    finalProcessedUrl = offloadResponse.resultMsg ?: ""
    } else {
    val tierLabel = if (tier == "CLOUD") "Cloud" else "Hub"
    executionStatusMsg = "[$tierLabel FAIL] ${offloadResponse.errorMessage}"
    }
    } else {
    // Local processing benchmark
    val localStart = System.currentTimeMillis()
    runLocalBenchmarkCompute()
    val localDone = System.currentTimeMillis()
    routeDuration = localDone - localStart
    lastLocalBenchmarkMs = routeDuration
    executionStatusMsg = "[Local OK] On-device compute: ${routeDuration}ms"
    }

    // ── Switch to Main thread to update UI ────────────────────────────
    withContext(Dispatchers.Main) {
    if (!isAdded) return@withContext // fragment gone

    btnFinalUpload?.isEnabled = true
    btnFinalUpload?.alpha = 1.0f
    btnFinalUpload?.text = "⬆  Upload Task"

    val logStr = "Mode: $selectedMode | Route: $decision | "+
    "Size: ${"%.2f".format(actualFileSizeMB)}MB | Processing: $chosenMode\n$executionStatusMsg"

    viewModel.addTask(title, logStr)

    val dbNode = when (decision) {
    ExecutionRoute.LOCAL -> "LOCAL"
    ExecutionRoute.HUB -> "HUB"
    ExecutionRoute.CLOUD -> "CLOUD"
    }

    // Determine what the server actually returned — prefer server result's file_type
    // because e.g. video THUMBNAIL → image, FRAME_ANALYTICS → data, PASSTHROUGH → video
    val (prettyFileType, extStr) = when {
    mimeType.contains("image") -> Pair("image", ".jpg")
    mimeType.contains("video") -> Pair("video", ".mp4") // fixed from "image"
    mimeType.contains("pdf") -> Pair("pdf", ".pdf")
    mimeType.contains("text") -> Pair("text", ".txt")
    mimeType.contains("csv") -> Pair("text", ".csv")
    mimeType.contains("json") -> Pair("text", ".json")
    mimeType.contains("word") ||
    mimeType.contains("wordprocessingml") -> Pair("doc", ".docx")
    mimeType.contains("spreadsheetml") ||
    mimeType.contains("ms-excel") -> Pair("doc", ".xlsx")
    else -> Pair("doc", "")
    }

    val modeSuffix = "[$chosenMode]"
    val today = java.text.SimpleDateFormat("MMM dd, yyyy",
    java.util.Locale.getDefault()).format(java.util.Date())
    val recordedMs = if (hubComputeTimeMs > 0L) hubComputeTimeMs else routeDuration

    viewModel.addFile(
    FileModel(
    fileName = "$title$extStr",
    fileSize = "${"%.2f".format(actualFileSizeMB)} MB",
    fileDate = today,
    fileType = prettyFileType,
    processedUrl = finalProcessedUrl,
    description = taskDesc.ifEmpty { "$chosenMode processing" },
    processingTimeMs = recordedMs,
    dataSizeMB = actualFileSizeMB,
    taskType = selectedMode,
    executionNode = dbNode
    )
    )

    // ── Log to SQLite ─────────────────────────────────────────────
    val taskEndMs = System.currentTimeMillis()
    val dbDataType = when (selectedMode) {
    "COMPOSITE"-> "COMPOSITE"
    "COMPLEX"-> "COMPLEX"
    "DIRECT REDIRECTION"-> "SIMPLE"
    else -> "COMPOSITE"
    }
    val dbStatus = if (executionStatusMsg.contains("FAIL")) "FAILURE" else "SUCCESS"
    kotlinx.coroutines.withContext(Dispatchers.IO) {
    logRepo.insertLog(
    taskName = title,
    dataType = dbDataType,
    processingNode = dbNode,
    startTimeMs = taskStartMs,
    endTimeMs = taskEndMs,
    status = dbStatus,
    inputSizeMB = actualFileSizeMB
    )
    }

    Toast.makeText(context, executionStatusMsg, Toast.LENGTH_LONG).show()
    }
    }
    }

    /** Returns human-readable processing mode for the selected file type. */
    private fun resolveFileProcessingMode(mimeType: String): String = when {
    mimeType.contains("image") -> selectedImageMode
    mimeType.contains("video") -> selectedVideoMode
    mimeType.contains("pdf") -> selectedPdfMode
    mimeType.contains("text") || mimeType.contains("json") ||
    mimeType.contains("csv") || mimeType.contains("xml") -> selectedTextMode
    else -> "DEFAULT"
    }

    // ─── Local CPU benchmark simulation ──────────────────────────────────────

    private fun runLocalBenchmarkCompute() {
    val size = 400
    val matrix = Array(size) { row -> IntArray(size) { col -> row * size + col } }
    val temp = Array(size) { IntArray(size) }
    repeat(20) {
    for (r in 0 until size) for (c in 0 until size) temp[c][r] = matrix[r][c]
    for (r in 0 until size) for (c in 0 until size) matrix[r][c] = temp[r][c]
    }
    }

    // ─── File analysis ────────────────────────────────────────────────────────

    private fun analyzeFile(uri: Uri) {
    val mimeType = requireContext().contentResolver.getType(uri) ?: ""

    requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
    if (sizeIndex != -1 && cursor.moveToFirst()) {
    val sizeBytes = cursor.getLong(sizeIndex)
    actualFileSizeMB = sizeBytes / (1024.0 * 1024.0)
    val fileName = cursor.getString(nameIndex) ?: "Unknown"

    // No file size limit - OffloadX is designed for heavy workloads

    val typHint = when {
    mimeType.contains("image") -> "Image"
    mimeType.contains("video") -> "Video"
    mimeType.contains("pdf") -> "PDF"
    mimeType.contains("text") || mimeType.contains("csv") ||
    mimeType.contains("json") -> "Text"
    else -> "Document"
    }

    tvFileStatus?.text =
    "$typHint: $fileName\nSize: ${"%.2f".format(actualFileSizeMB)} MB"
    }
    }
    }

    // ─── System helpers ───────────────────────────────────────────────────────

    private fun getBatteryPercentage(): Int {
    val bm = requireContext().getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun getNetworkType(): NetworkType {
    val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val activeNetwork = cm.activeNetwork ?: return NetworkType.OFFLINE
    val caps = cm.getNetworkCapabilities(activeNetwork) ?: return NetworkType.OFFLINE
    return when {
    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
    else -> NetworkType.OFFLINE
    }
    }

    override fun onDestroyView() {
    super.onDestroyView()
    // Clear all view references

    etHubIp = null
    etTaskTitle = null
    tvFileStatus = null
    btnRoutingAuto = null
    btnRoutingManual = null
    tvAutoRouteInfo = null
    layoutManualOptions = null
    cbLocal = null
    cbCloud = null
    tvManualRouteInfo = null
    btnModeComposite = null
    btnModeComplex = null
    btnSearchFile = null
    btnFinalUpload = null
    }
}