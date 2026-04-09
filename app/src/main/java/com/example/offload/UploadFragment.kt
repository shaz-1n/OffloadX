package com.example.offload

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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.offload.databinding.FragmentUploadBinding


class UploadFragment : Fragment() {

    private lateinit var logRepo: OffloadLogRepository

    private var _binding: FragmentUploadBinding? = null
    private val binding get() = _binding!!
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

    // File picker launcher
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            analyzeFile(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUploadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        logRepo = OffloadLogRepository(requireContext())

        val prefs = requireActivity().getSharedPreferences("OffloadXPrefs", Context.MODE_PRIVATE)
        binding.etHubIp.setText(prefs.getString("hub_ip", "192.168.1.100:8000"))

        // Automatically generate a Task ID when the screen opens
        binding.etTaskId.setText("TASK_${System.currentTimeMillis() / 1000}")
        binding.etProcessId.setText("PROC_${java.util.UUID.randomUUID().toString().take(8)}")

        // --- DIRECT REDIRECTION: Listen for shared files from outside the app ---
        viewModel.sharedUri.observe(viewLifecycleOwner) { uri ->
            if (uri != null) {
                selectedMode = "DIRECT REDIRECTION"
                selectedUri = uri
                binding.tvFileStatus.text = "Mode: Direct Redirection (Shared File)"
                analyzeFile(uri)
                viewModel.setSharedUri(null)
            }
        }

        // ── Routing Strategy Toggle ────────────────────────────────────────────
        binding.btnRoutingAuto.setOnClickListener {
            isAutoRouting = true
            setRoutingUI(auto = true)
        }

        binding.btnRoutingManual.setOnClickListener {
            isAutoRouting = false
            setRoutingUI(auto = false)
        }

        // Manual sub-option: mutual exclusion between Local and Hub/Cloud
        binding.cbLocal.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.cbCloud.isChecked = false
                binding.tvManualRouteInfo.text = "📱 Local: Process entirely on-device."
            }
        }

        binding.cbCloud.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.cbLocal.isChecked = false
                binding.tvManualRouteInfo.text = "🖥️ Hub/Cloud: Send to edge node or cloud server."
            }
        }

        // ── Execution Mode Buttons ─────────────────────────────────────────────
        binding.btnModeComposite.setOnClickListener {
            selectedMode = "COMPOSITE"
            binding.tvFileStatus.text = "Select File for Upload"
            activateModeButton(composite = true)
        }

        binding.btnModeComplex.setOnClickListener {
            selectedMode = "COMPLEX"
            binding.tvFileStatus.text = "Select File for Upload"
            activateModeButton(composite = false)
        }

        // ── File Picker ────────────────────────────────────────────────────────
        binding.btnSearchFile.setOnClickListener {
            filePickerLauncher.launch(arrayOf("*/*"))
        }

        // ── Upload Button ──────────────────────────────────────────────────────
        binding.btnFinalUpload.setOnClickListener {
            if (selectedUri == null) {
                Toast.makeText(context, "Please select a file first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val title = binding.etTaskTitle.text.toString()
            if (title.isEmpty()) {
                binding.etTaskTitle.error = "Title Required"
                return@setOnClickListener
            }

            val ipAddress = binding.etHubIp.text.toString()
            if (ipAddress.isEmpty()) {
                binding.etHubIp.error = "IP Required for Hub"
                return@setOnClickListener
            }

            // Validate manual routing selection
            if (!isAutoRouting && !binding.cbLocal.isChecked && !binding.cbCloud.isChecked) {
                Toast.makeText(context, "Please select Local or Hub/Cloud in Manual mode", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val mimeType = requireContext().contentResolver.getType(selectedUri!!) ?: ""
            dispatchProcessingDialog(mimeType, title, ipAddress)
        }
    }

    // ─── Routing UI helper ────────────────────────────────────────────────────

    private fun setRoutingUI(auto: Boolean) {
        val ctx = requireContext()
        if (auto) {
            binding.btnRoutingAuto.backgroundTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(ctx, R.color.primary_color))
            binding.btnRoutingAuto.setTextColor(
                androidx.core.content.ContextCompat.getColor(ctx, R.color.white_color))
            binding.btnRoutingManual.backgroundTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(ctx, R.color.input_bg_color))
            binding.btnRoutingManual.setTextColor(
                androidx.core.content.ContextCompat.getColor(ctx, R.color.text_secondary))
            binding.tvAutoRouteInfo.visibility = View.VISIBLE
            binding.layoutManualOptions.visibility = View.GONE
        } else {
            binding.btnRoutingManual.backgroundTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(ctx, R.color.primary_color))
            binding.btnRoutingManual.setTextColor(
                androidx.core.content.ContextCompat.getColor(ctx, R.color.white_color))
            binding.btnRoutingAuto.backgroundTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(ctx, R.color.input_bg_color))
            binding.btnRoutingAuto.setTextColor(
                androidx.core.content.ContextCompat.getColor(ctx, R.color.text_secondary))
            binding.tvAutoRouteInfo.visibility = View.GONE
            binding.layoutManualOptions.visibility = View.VISIBLE
        }
    }

    private fun activateModeButton(composite: Boolean) {
        val ctx = requireContext()
        if (composite) {
            binding.btnModeComposite.backgroundTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(ctx, R.color.primary_color))
            binding.btnModeComposite.setTextColor(
                androidx.core.content.ContextCompat.getColor(ctx, R.color.white_color))
            binding.btnModeComplex.backgroundTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(ctx, R.color.input_bg_color))
            binding.btnModeComplex.setTextColor(
                androidx.core.content.ContextCompat.getColor(ctx, R.color.text_secondary))
        } else {
            binding.btnModeComplex.backgroundTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(ctx, R.color.primary_color))
            binding.btnModeComplex.setTextColor(
                androidx.core.content.ContextCompat.getColor(ctx, R.color.white_color))
            binding.btnModeComposite.backgroundTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(ctx, R.color.input_bg_color))
            binding.btnModeComposite.setTextColor(
                androidx.core.content.ContextCompat.getColor(ctx, R.color.text_secondary))
        }
    }

    // ─── File-type-specific processing dialog dispatcher ─────────────────────

    /**
     * Detects the file type from MIME type and shows the appropriate
     * processing-mode dialog (or goes directly for unknown types).
     */
    private fun dispatchProcessingDialog(mimeType: String, title: String, ipAddress: String) {
        when {
            mimeType.contains("image") -> showImageModeDialog { mode ->
                selectedImageMode = mode
                startUpload(title, ipAddress)
            }
            mimeType.contains("video") -> showVideoModeDialog { mode ->
                selectedVideoMode = mode
                startUpload(title, ipAddress)
            }
            mimeType.contains("pdf") -> showPdfModeDialog { mode ->
                selectedPdfMode = mode
                startUpload(title, ipAddress)
            }
            mimeType.contains("text") || mimeType.contains("json") ||
            mimeType.contains("csv")  || mimeType.contains("xml") -> showTextModeDialog { mode ->
                selectedTextMode = mode
                startUpload(title, ipAddress)
            }
            else -> startUpload(title, ipAddress) // docx, xlsx, zip, etc.
        }
    }

    // ─── Processing-mode dialogs ──────────────────────────────────────────────

    private fun showImageModeDialog(onModeChosen: (String) -> Unit) {
        val modeLabels = arrayOf(
            "🌑  Grayscale",
            "🔍  Object Detection (Face)",
            "📐  Edge Detection",
            "💧  Blur (Gaussian)",
            "✨  Sharpen",
            "🌅  Sepia",
            "🔁  Invert (Negative)"
        )
        val modeValues = arrayOf(
            "GRAYSCALE", "OBJECT_DETECTION", "EDGE_DETECT", "BLUR", "SHARPEN", "SEPIA", "INVERT"
        )
        var idx = modeValues.indexOf(selectedImageMode).takeIf { it >= 0 } ?: 0

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("🖼️ Choose Image Processing")
            .setSingleChoiceItems(modeLabels, idx) { _, which -> idx = which }
            .setPositiveButton("Process") { _, _ -> onModeChosen(modeValues[idx]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showVideoModeDialog(onModeChosen: (String) -> Unit) {
        val modeLabels = arrayOf(
            "🎯  Face Detection (AI — Best for demos)",
            "📊  Frame Analytics (Edge density report)",
            "🎞️  Thumbnail Extract (First clear frame)",
            "🔁  Passthrough (Store + link)"
        )
        val modeValues = arrayOf(
            "FACE_DETECTION", "FRAME_ANALYTICS", "THUMBNAIL", "PASSTHROUGH"
        )
        var idx = modeValues.indexOf(selectedVideoMode).takeIf { it >= 0 } ?: 0

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("🎬 Choose Video Processing")
            .setSingleChoiceItems(modeLabels, idx) { _, which -> idx = which }
            .setPositiveButton("Process") { _, _ -> onModeChosen(modeValues[idx]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPdfModeDialog(onModeChosen: (String) -> Unit) {
        val modeLabels = arrayOf(
            "📄  Analyze (Word / page count + metadata)",
            "🔍  Text Extract (Pull all readable text)",
            "💾  Store & Link (Save original, return URL)"
        )
        val modeValues = arrayOf(
            "ANALYZE", "TEXT_EXTRACT", "STORE"
        )
        var idx = modeValues.indexOf(selectedPdfMode).takeIf { it >= 0 } ?: 0

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("📑 Choose PDF Processing")
            .setSingleChoiceItems(modeLabels, idx) { _, which -> idx = which }
            .setPositiveButton("Process") { _, _ -> onModeChosen(modeValues[idx]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTextModeDialog(onModeChosen: (String) -> Unit) {
        val modeLabels = arrayOf(
            "📊  Word Count (Words, lines, chars)",
            "🔎  Keyword Frequency (Top-20 words)",
            "📈  Sentiment Scan (Positive / Negative ratio)",
            "💾  Store & Link (Save original, return URL)"
        )
        val modeValues = arrayOf(
            "WORD_COUNT", "KEYWORD_FREQ", "SENTIMENT", "STORE"
        )
        var idx = modeValues.indexOf(selectedTextMode).takeIf { it >= 0 } ?: 0

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("📝 Choose Text Processing")
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
    private fun startUpload(title: String, ipAddress: String) {
        requireActivity().getSharedPreferences("OffloadXPrefs", Context.MODE_PRIVATE)
            .edit().putString("hub_ip", ipAddress).apply()

        val battery = getBatteryPercentage()
        val netType = getNetworkType()
        val mimeType = requireContext().contentResolver.getType(selectedUri!!) ?: ""
        // Combine all chosen modes into a single extra param map
        val chosenMode = resolveFileProcessingMode(mimeType)

        val fileToUpload = selectedUri!!

        binding.btnFinalUpload.isEnabled = false
        binding.btnFinalUpload.text = "Processing..."

        lifecycleScope.launch(Dispatchers.Default) {
            val start = System.currentTimeMillis()
            val taskStartMs = start

            // ── Determine execution route ──────────────────────────────────────
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
                if (binding.cbCloud.isChecked) ExecutionRoute.HUB else ExecutionRoute.LOCAL
            }

            var routeDuration = System.currentTimeMillis() - start
            var hubComputeTimeMs: Long = 0L
            var executionStatusMsg = "Decided Route"
            var finalProcessedUrl = ""

            if (decision == ExecutionRoute.HUB || decision == ExecutionRoute.CLOUD) {
                val client = HubOffloadClient(requireContext())
                val tier = if (decision == ExecutionRoute.CLOUD) "CLOUD" else "HUB"
                val offloadResponse = client.offloadToHub(
                    ipAddress, "android_device_1", "IMAGE_GRAYSCALE", fileToUpload, tier,
                    imageMode   = selectedImageMode,
                    pdfMode     = selectedPdfMode,
                    textMode    = selectedTextMode,
                    videoMode   = selectedVideoMode
                )

                routeDuration    = System.currentTimeMillis() - start
                hubComputeTimeMs = offloadResponse.serverProcessingTimeMs

                if (offloadResponse.success) {
                    val tierLabel = if (tier == "CLOUD") "Cloud ☁" else "Hub 🖥"
                    executionStatusMsg = "[$tierLabel ✓] Round-trip: ${routeDuration}ms | Server compute: ${hubComputeTimeMs}ms"
                    finalProcessedUrl  = offloadResponse.resultMsg ?: ""
                } else {
                    val tierLabel = if (tier == "CLOUD") "Cloud" else "Hub"
                    executionStatusMsg = "[$tierLabel ✗] ${offloadResponse.errorMessage}"
                }
            } else {
                // Local processing benchmark
                val localStart = System.currentTimeMillis()
                runLocalBenchmarkCompute()
                val localDone = System.currentTimeMillis()
                routeDuration           = localDone - localStart
                lastLocalBenchmarkMs    = routeDuration
                executionStatusMsg      = "[Local ✓] On-device compute: ${routeDuration}ms"
            }

            // ── Switch to Main thread to update UI ────────────────────────────
            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext   // fragment gone

                binding.btnFinalUpload.isEnabled = true
                binding.btnFinalUpload.text = "Start Upload Process"

                val logStr = "Mode: $selectedMode | Route: $decision | " +
                             "Size: ${"%.2f".format(actualFileSizeMB)}MB | Processing: $chosenMode\n$executionStatusMsg"

                viewModel.addTask(title, logStr)

                val dbNode = when (decision) {
                    ExecutionRoute.LOCAL -> "LOCAL"
                    ExecutionRoute.HUB   -> "HUB"
                    ExecutionRoute.CLOUD -> "CLOUD"
                }

                // Determine what the server actually returned — prefer server result's file_type
                // because e.g. video THUMBNAIL → image, FRAME_ANALYTICS → data, PASSTHROUGH → video
                val (prettyFileType, extStr) = when {
                    mimeType.contains("image")   -> Pair("image", ".jpg")
                    mimeType.contains("video")   -> Pair("video", ".mp4")  // fixed from "image"
                    mimeType.contains("pdf")     -> Pair("pdf",   ".pdf")
                    mimeType.contains("text")    -> Pair("text",  ".txt")
                    mimeType.contains("csv")     -> Pair("text",  ".csv")
                    mimeType.contains("json")    -> Pair("text",  ".json")
                    mimeType.contains("word") ||
                    mimeType.contains("wordprocessingml") -> Pair("doc", ".docx")
                    mimeType.contains("spreadsheetml") ||
                    mimeType.contains("ms-excel")        -> Pair("doc", ".xlsx")
                    else                         -> Pair("doc",  "")
                }

                val modeSuffix = " [$chosenMode]"
                val today = java.text.SimpleDateFormat("MMM dd, yyyy",
                    java.util.Locale.getDefault()).format(java.util.Date())
                val recordedMs = if (hubComputeTimeMs > 0L) hubComputeTimeMs else routeDuration

                viewModel.addFile(
                    FileModel(
                        fileName       = "$title (Processed)$modeSuffix$extStr",
                        fileSize       = "${"%.2f".format(actualFileSizeMB)} MB",
                        fileDate       = today,
                        fileType       = prettyFileType,
                        processedUrl   = finalProcessedUrl,
                        processingTimeMs = recordedMs,
                        dataSizeMB     = actualFileSizeMB,
                        taskType       = selectedMode,
                        executionNode  = dbNode
                    )
                )

                // ── Log to SQLite ─────────────────────────────────────────────
                val taskEndMs = System.currentTimeMillis()
                val dbDataType = when (selectedMode) {
                    "COMPOSITE"          -> "COMPOSITE"
                    "COMPLEX"            -> "COMPLEX"
                    "DIRECT REDIRECTION" -> "SIMPLE"
                    else                 -> "COMPOSITE"
                }
                val dbStatus = if (executionStatusMsg.contains("✗")) "FAILURE" else "SUCCESS"
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    logRepo.insertLog(
                        taskName       = title,
                        dataType       = dbDataType,
                        processingNode = dbNode,
                        startTimeMs    = taskStartMs,
                        endTimeMs      = taskEndMs,
                        status         = dbStatus,
                        inputSizeMB    = actualFileSizeMB
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
        mimeType.contains("pdf")   -> selectedPdfMode
        mimeType.contains("text") || mimeType.contains("json") ||
        mimeType.contains("csv")  || mimeType.contains("xml")  -> selectedTextMode
        else                       -> "DEFAULT"
    }

    // ─── Local CPU benchmark simulation ──────────────────────────────────────

    private fun runLocalBenchmarkCompute() {
        val size = 400
        val matrix = Array(size) { row -> IntArray(size) { col -> row * size + col } }
        val temp   = Array(size) { IntArray(size) }
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

                if (actualFileSizeMB > 200.0) {
                    Toast.makeText(context,
                        "File massive (>200MB): $fileName. Stream Processing needed.",
                        Toast.LENGTH_LONG).show()
                }

                val typHint = when {
                    mimeType.contains("image") -> "🖼 Image"
                    mimeType.contains("video") -> "🎬 Video"
                    mimeType.contains("pdf")   -> "📑 PDF"
                    mimeType.contains("text") || mimeType.contains("csv") ||
                    mimeType.contains("json")  -> "📝 Text"
                    else                       -> "📁 Document"
                }

                binding.tvFileStatus.text =
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
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)     -> NetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
            else                                                       -> NetworkType.OFFLINE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}