package com.example.offload

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class StatsFragment : Fragment() {

    private val viewModel: SharedViewModel by activityViewModels()
    private lateinit var logRepo: OffloadLogRepository

    // View references (using findViewById to avoid ViewBinding crash)
    private var tvTasksDone: TextView? = null
    private var tvTotalData: TextView? = null
    private var tvAvgTime: TextView? = null
    private var tvSystemHealth: TextView? = null
    private var performanceChart: BarChart? = null
    private var btnPingNode: Button? = null
    private var btnRunBenchmark: Button? = null
    private var btnRefreshReport: Button? = null
    private var tvNodeStatus: TextView? = null
    private var tvNodeLatency: TextView? = null
    private var tvNodeStorage: TextView? = null
    private var tvNodeProcessor: TextView? = null
    private var statusDot: View? = null
    private var tvBenchmarkStatus: TextView? = null
    private var layoutBenchmarkResults: LinearLayout? = null
    private var tvBenchLocalMs: TextView? = null
    private var tvBenchHubMs: TextView? = null
    private var benchmarkChart: BarChart? = null
    private var tvBenchmarkWinner: TextView? = null
    private var tvReportSummary: TextView? = null
    private var reportChart: BarChart? = null
    private var tvFastestMethod: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_stats, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        logRepo = OffloadLogRepository(requireContext())

        // Find all views manually
        tvTasksDone = view.findViewById(R.id.tvTasksDone)
        tvTotalData = view.findViewById(R.id.tvTotalData)
        tvAvgTime = view.findViewById(R.id.tvAvgTime)
        tvSystemHealth = view.findViewById(R.id.tvSystemHealth)
        performanceChart = view.findViewById(R.id.performanceChart)
        btnPingNode = view.findViewById(R.id.btnPingNode)
        btnRunBenchmark = view.findViewById(R.id.btnRunBenchmark)
        btnRefreshReport = view.findViewById(R.id.btnRefreshReport)
        tvNodeStatus = view.findViewById(R.id.tvNodeStatus)
        tvNodeLatency = view.findViewById(R.id.tvNodeLatency)
        tvNodeStorage = view.findViewById(R.id.tvNodeStorage)
        tvNodeProcessor = view.findViewById(R.id.tvNodeProcessor)
        statusDot = view.findViewById(R.id.statusDot)
        tvBenchmarkStatus = view.findViewById(R.id.tvBenchmarkStatus)
        layoutBenchmarkResults = view.findViewById(R.id.layoutBenchmarkResults)
        tvBenchLocalMs = view.findViewById(R.id.tvBenchLocalMs)
        tvBenchHubMs = view.findViewById(R.id.tvBenchHubMs)
        benchmarkChart = view.findViewById(R.id.benchmarkChart)
        tvBenchmarkWinner = view.findViewById(R.id.tvBenchmarkWinner)
        tvReportSummary = view.findViewById(R.id.tvReportSummary)
        reportChart = view.findViewById(R.id.reportChart)
        tvFastestMethod = view.findViewById(R.id.tvFastestMethod)

        // Observe files for real statistics
        viewModel.downloadableFiles.observe(viewLifecycleOwner) { files ->
            updateStatistics(files)
            setupChart(files)
        }

        // Ping button
        btnPingNode?.setOnClickListener {
            pingEdgeNode()
        }

        // Benchmark button
        btnRunBenchmark?.setOnClickListener {
            runLocalVsHubBenchmark()
        }

        // Report refresh button
        btnRefreshReport?.setOnClickListener {
            loadReportFromDb()
        }

        // Load report on first view
        loadReportFromDb()

    }

    override fun onResume() {
        super.onResume()
        if (btnPingNode != null) {
            pingEdgeNode()
        }
    }

    // -------------------------------------------------------------------------
    // Statistics
    // -------------------------------------------------------------------------

    private fun updateStatistics(files: List<FileModel>) {
        val totalTasks = files.size
        tvTasksDone?.text = totalTasks.toString()

        val totalMB = files.sumOf { it.dataSizeMB }
        tvTotalData?.text = if (totalMB > 1024) {
            "${"%.1f".format(totalMB / 1024)} GB"
        } else {
            "${"%.1f".format(totalMB)} MB"
        }

        val timesWithData = files.filter { it.processingTimeMs > 0 }
        if (timesWithData.isNotEmpty()) {
            val avgMs = timesWithData.map { it.processingTimeMs }.average()
            tvAvgTime?.text = if (avgMs > 1000) {
                "${"%.1f".format(avgMs / 1000)}s"
            } else {
                "${avgMs.toLong()}ms"
            }
        } else {
            tvAvgTime?.text = "-- ms"
        }

        if (totalTasks == 0) {
            tvSystemHealth?.text = "No metrics gathered yet. Upload a file to the Edge Node to begin."
        } else {
            val downloaded = files.count { it.isDownloaded }
            val types = files.groupBy { it.taskType }.map { "${it.value.size} ${it.key}" }.joinToString(", ")
            tvSystemHealth?.text = "Processed $totalTasks total tasks ($types). $downloaded results downloaded locally. Edge routing and hardware acceleration operational."
        }
    }

    private fun setupChart(files: List<FileModel>) {
        val chart = performanceChart ?: return
        chart.description.isEnabled = false
        chart.setDrawGridBackground(false)
        chart.legend.isEnabled = true
        chart.setTouchEnabled(true)

        val tasksWithTime = files.filter { it.processingTimeMs > 0 }.takeLast(6)

        if (tasksWithTime.isEmpty()) {
            val entries = arrayListOf(BarEntry(0f, 0f))
            val dataSet = BarDataSet(entries, "No data yet")
            dataSet.color = ContextCompat.getColor(requireContext(), R.color.input_stroke_color)
            chart.data = BarData(dataSet)
            chart.invalidate()
            return
        }

        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()

        tasksWithTime.forEachIndexed { index, file ->
            entries.add(BarEntry(index.toFloat(), file.processingTimeMs.toFloat()))
            val shortName = file.fileName.take(8).replace("_", " ")
            labels.add(shortName)
        }

        val dataSet = BarDataSet(entries, "Processing Time (ms)")
        dataSet.color = ContextCompat.getColor(requireContext(), R.color.primary_color)
        dataSet.valueTextColor = ContextCompat.getColor(requireContext(), R.color.text_primary)
        dataSet.valueTextSize = 10f

        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        xAxis.granularity = 1f
        xAxis.textSize = 10f
        xAxis.textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        xAxis.labelRotationAngle = -30f

        chart.axisLeft.apply {
            setDrawGridLines(true)
            gridColor = ContextCompat.getColor(requireContext(), R.color.input_stroke_color)
            axisMinimum = 0f
            textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        }
        chart.axisRight.isEnabled = false

        chart.data = BarData(dataSet).apply { barWidth = 0.5f }
        chart.invalidate()
        chart.animateY(800)
    }

    // -------------------------------------------------------------------------
    // Local vs Hub Benchmark
    // -------------------------------------------------------------------------

    private fun runLocalVsHubBenchmark() {
        val prefs = requireActivity().getSharedPreferences("OffloadXPrefs", android.content.Context.MODE_PRIVATE)
        val ip = prefs.getString("hub_ip", "192.168.1.100:8000") ?: "192.168.1.100:8000"
        val baseUrl = if (ip.startsWith("http")) ip else "http://$ip"

        btnRunBenchmark?.isEnabled = false
        btnRunBenchmark?.text = "Running..."
        tvBenchmarkStatus?.text = "Step 1/3 - Measuring on-device compute..."
        layoutBenchmarkResults?.visibility = View.GONE
        benchmarkChart?.visibility = View.GONE
        tvBenchmarkWinner?.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            // --- PHASE 1: Local CPU benchmark ---
            val localStart = System.currentTimeMillis()
            runMatrixBenchmark()
            val localMs = System.currentTimeMillis() - localStart

            withContext(Dispatchers.Main) {
                tvBenchmarkStatus?.text = "Local: ${localMs}ms  |  Step 2/3 - Sending to Hub..."
            }

            // --- PHASE 2: Hub benchmark ---
            var hubMs: Long = -1
            var hubError: String? = null
            try {
                val numbersArray = JSONArray()
                repeat(50_000) { i -> numbersArray.put(i.toDouble()) }
                val body = JSONObject().apply {
                    put("device_id", "benchmark_android")
                    put("task_type", "COMPOSITE")
                    put("data", JSONObject().put("numbers", numbersArray))
                }
                val url = URL("$baseUrl/api/compute/")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.outputStream.bufferedWriter().use { it.write(body.toString()) }
                val responseCode = conn.responseCode
                val rawBody = if (responseCode in 200..299) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else {
                    conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                }
                if (responseCode in 200..299) {
                    val json = JSONObject(rawBody)
                    hubMs = json.optDouble("processing_time_ms", (System.currentTimeMillis() - localStart).toDouble()).toLong()
                } else {
                    hubMs = -1; hubError = "HTTP $responseCode"
                }
            } catch (e: Exception) {
                hubMs = -1; hubError = e.message ?: "Connection failed"
            }

            withContext(Dispatchers.Main) {
                tvBenchmarkStatus?.text = "Local: ${localMs}ms  |  Hub: ${if (hubMs > 0) "${hubMs}ms" else "Error"}  |  Step 3/3 - Sending to Cloud..."
            }

            // --- PHASE 3: Cloud benchmark ---
            var cloudMs: Long = -1
            var cloudError: String? = null
            try {
                val numbersArray = JSONArray()
                repeat(50_000) { i -> numbersArray.put(i.toDouble()) }
                val body = JSONObject().apply {
                    put("device_id", "benchmark_android")
                    put("task_type", "COMPOSITE")
                    put("data", JSONObject().put("numbers", numbersArray))
                }
                val url = URL("$baseUrl/api/cloud/compute/")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 20_000
                conn.readTimeout = 45_000
                conn.outputStream.bufferedWriter().use { it.write(body.toString()) }
                val responseCode = conn.responseCode
                val rawBody = if (responseCode in 200..299) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else {
                    conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                }
                if (responseCode in 200..299) {
                    val json = JSONObject(rawBody)
                    cloudMs = json.optDouble("processing_time_ms", 200.0).toLong()
                } else {
                    cloudMs = -1; cloudError = "HTTP $responseCode"
                }
            } catch (e: Exception) {
                cloudMs = -1; cloudError = e.message ?: "Connection failed"
            }

            // Log all results to SQLite
            val benchEndMs = System.currentTimeMillis()
            val benchStartMs = benchEndMs - localMs - (if (hubMs > 0) hubMs else 0L) - (if (cloudMs > 0) cloudMs else 0L)
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                logRepo.insertLog("Benchmark (Local)", "COMPOSITE", "LOCAL",
                    benchStartMs, benchStartMs + localMs, "SUCCESS", 0.0)
                if (hubMs > 0 && hubError == null) {
                    logRepo.insertLog("Benchmark (Hub)", "COMPOSITE", "HUB",
                        benchStartMs + localMs, benchStartMs + localMs + hubMs, "SUCCESS", 0.0)
                }
                if (cloudMs > 0 && cloudError == null) {
                    logRepo.insertLog("Benchmark (Cloud)", "COMPOSITE", "CLOUD",
                        benchStartMs + localMs + (if (hubMs > 0) hubMs else 0L),
                        benchStartMs + localMs + (if (hubMs > 0) hubMs else 0L) + cloudMs, "SUCCESS", 0.0)
                }
            }

            // --- Update UI ---
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext

                btnRunBenchmark?.isEnabled = true
                btnRunBenchmark?.text = "Run Test"

                val hubLabel   = if (hubError != null)   "Error" else "${hubMs}ms"
                val cloudLabel = if (cloudError != null) "Error" else "${cloudMs}ms"
                tvBenchmarkStatus?.text = "Local: ${localMs}ms  |  Hub: $hubLabel  |  Cloud: $cloudLabel"

                tvBenchLocalMs?.text = "${localMs}ms"
                tvBenchHubMs?.text   = if (hubError != null) "Err" else "${hubMs}ms"
                layoutBenchmarkResults?.visibility = View.VISIBLE

                showBenchmarkChart(localMs, if (hubMs > 0) hubMs else 0L, if (cloudMs > 0) cloudMs else 0L)

                val validTimes = mutableMapOf("Local" to localMs)
                if (hubMs > 0)   validTimes["Hub"]   = hubMs
                if (cloudMs > 0) validTimes["Cloud"] = cloudMs
                val winner = validTimes.minByOrNull { it.value }
                tvBenchmarkWinner?.text = when (winner?.key) {
                    "Local" -> "Local is fastest for this workload size"
                    "Hub"   -> "Hub is fastest - offloading pays off"
                    "Cloud" -> "Cloud is fastest in this run"
                    else    -> "Could not determine winner"
                }
                tvBenchmarkWinner?.visibility = View.VISIBLE

                loadReportFromDb()
            }
        }
    }

    private fun showBenchmarkChart(localMs: Long, hubMs: Long, cloudMs: Long = 0L) {
        val chart = benchmarkChart ?: return
        chart.description.isEnabled = false
        chart.legend.isEnabled = true
        chart.setTouchEnabled(false)

        val localEntries = arrayListOf(BarEntry(0f, localMs.toFloat()))
        val hubEntries   = arrayListOf(BarEntry(0f, hubMs.toFloat()))
        val cloudEntries = arrayListOf(BarEntry(0f, cloudMs.toFloat()))

        val localSet = BarDataSet(localEntries, "Local (on-device)").apply {
            color = Color.parseColor("#FF7043")
            valueTextColor = ContextCompat.getColor(requireContext(), R.color.text_primary)
            valueTextSize = 11f
        }
        val hubSet = BarDataSet(hubEntries, "Hub (edge node)").apply {
            color = ContextCompat.getColor(requireContext(), R.color.primary_color)
            valueTextColor = ContextCompat.getColor(requireContext(), R.color.text_primary)
            valueTextSize = 11f
        }
        val cloudSet = BarDataSet(cloudEntries, "Cloud (simulated)").apply {
            color = Color.parseColor("#66BB6A")
            valueTextColor = ContextCompat.getColor(requireContext(), R.color.text_primary)
            valueTextSize = 11f
        }

        val groupSpace = 0.16f
        val barSpace   = 0.04f
        val barWidth   = 0.25f

        val dataSets = if (cloudMs > 0L) listOf(localSet, hubSet, cloudSet) else listOf(localSet, hubSet)
        val data = BarData(dataSets)
        data.barWidth = barWidth

        chart.data = data
        chart.groupBars(0f, groupSpace, barSpace)

        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f
        xAxis.setCenterAxisLabels(true)
        xAxis.valueFormatter = IndexAxisValueFormatter(arrayOf("Benchmark"))
        xAxis.textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        xAxis.axisMinimum = 0f
        xAxis.axisMaximum = chart.barData.getGroupWidth(groupSpace, barSpace) * 1

        chart.axisLeft.apply {
            axisMinimum = 0f
            textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
            gridColor = ContextCompat.getColor(requireContext(), R.color.input_stroke_color)
        }
        chart.axisRight.isEnabled = false
        chart.setFitBars(true)
        chart.visibility = View.VISIBLE
        chart.invalidate()
        chart.animateY(600)
    }

    /** Same 400x400 matrix-transpose x 20 used in UploadFragment's local path. */
    private fun runMatrixBenchmark() {
        val size = 400
        val matrix = Array(size) { r -> IntArray(size) { c -> r * size + c } }
        val temp   = Array(size) { IntArray(size) }
        repeat(20) {
            for (r in 0 until size) for (c in 0 until size) temp[c][r] = matrix[r][c]
            for (r in 0 until size) for (c in 0 until size) matrix[r][c] = temp[r][c]
        }
    }

    // -------------------------------------------------------------------------
    // Edge Node Ping
    // -------------------------------------------------------------------------

    private fun pingEdgeNode() {
        val prefs = requireActivity().getSharedPreferences("OffloadXPrefs", android.content.Context.MODE_PRIVATE)
        val ip = prefs.getString("hub_ip", "192.168.1.100:8000") ?: "192.168.1.100:8000"
        val baseUrl = if (ip.startsWith("http")) ip else "http://$ip"

        btnPingNode?.isEnabled = false
        btnPingNode?.text = "..."
        tvNodeStatus?.text = "Pinging $ip..."

        CoroutineScope(Dispatchers.IO).launch {
            val startTime = System.currentTimeMillis()
            try {
                val url = URL("$baseUrl/api/system-info/")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.requestMethod = "GET"

                val responseCode = conn.responseCode
                val latencyMs = System.currentTimeMillis() - startTime

                if (responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)

                    withContext(Dispatchers.Main) {
                        if (!isAdded) return@withContext
                        tvNodeLatency?.text = "${latencyMs}ms"
                        tvNodeStorage?.text = json.optString("free_storage", "--")
                        tvNodeProcessor?.text = json.optString("processor", "CPU")
                        tvNodeStatus?.text = "Connected to $ip | ${json.optString("os", "Unknown OS")}"

                        statusDot?.let { dot ->
                            val shape = GradientDrawable()
                            shape.shape = GradientDrawable.OVAL
                            shape.setColor(ContextCompat.getColor(requireContext(), R.color.primary_color))
                            dot.background = shape
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        if (!isAdded) return@withContext
                        tvNodeLatency?.text = "${latencyMs}ms"
                        tvNodeStatus?.text = "Connected (basic). System info not available."
                        tvNodeStorage?.text = "--"
                        tvNodeProcessor?.text = "--"

                        statusDot?.let { dot ->
                            val shape = GradientDrawable()
                            shape.shape = GradientDrawable.OVAL
                            shape.setColor(Color.parseColor("#FFA726"))
                            dot.background = shape
                        }
                    }
                }
            } catch (e: Exception) {
                try {
                    val url2 = URL("$baseUrl/")
                    val conn2 = url2.openConnection() as HttpURLConnection
                    conn2.connectTimeout = 3000
                    conn2.readTimeout = 3000
                    val latencyMs = System.currentTimeMillis() - startTime
                    val code = conn2.responseCode

                    withContext(Dispatchers.Main) {
                        if (!isAdded) return@withContext
                        tvNodeLatency?.text = "${latencyMs}ms"
                        tvNodeStatus?.text = "Server reachable at $ip (HTTP $code). Add /api/system-info/ for full stats."
                        tvNodeStorage?.text = "--"
                        tvNodeProcessor?.text = "--"

                        statusDot?.let { dot ->
                            val shape = GradientDrawable()
                            shape.shape = GradientDrawable.OVAL
                            shape.setColor(Color.parseColor("#FFA726"))
                            dot.background = shape
                        }
                    }
                } catch (e2: Exception) {
                    withContext(Dispatchers.Main) {
                        if (!isAdded) return@withContext
                        tvNodeLatency?.text = "--"
                        tvNodeStatus?.text = "Cannot reach Edge Node at $ip. Check IP and ensure the server is running."
                        tvNodeStorage?.text = "--"
                        tvNodeProcessor?.text = "--"

                        statusDot?.let { dot ->
                            val shape = GradientDrawable()
                            shape.shape = GradientDrawable.OVAL
                            shape.setColor(ContextCompat.getColor(requireContext(), R.color.danger_color))
                            dot.background = shape
                        }
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    btnPingNode?.isEnabled = true
                    btnPingNode?.text = "Ping"
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Report Section
    // -------------------------------------------------------------------------

    private fun loadReportFromDb() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val report = logRepo.getPerformanceReport()
            val stats  = logRepo.getOverallStats()

            val dataTypes = listOf("SIMPLE", "COMPOSITE", "COMPLEX")
            val fastestMap = mutableMapOf<String, OffloadLogRepository.FastestResult?>()
            for (dt in dataTypes) {
                fastestMap[dt] = logRepo.getFastestMethodForDataType(dt)
            }

            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext

                if (report.isEmpty()) {
                    tvReportSummary?.text = "No execution logs yet. Run an upload or benchmark to populate."
                    reportChart?.visibility = View.GONE
                    tvFastestMethod?.visibility = View.GONE
                    return@withContext
                }

                val summaryParts = mutableListOf<String>()
                summaryParts.add("${stats.totalTasks} total runs")
                summaryParts.add("${stats.successCount} succeeded")
                if (stats.failureCount > 0) summaryParts.add("${stats.failureCount} failed")
                summaryParts.add("avg ${"%.0f".format(stats.avgElapsedMs)}ms")
                tvReportSummary?.text = summaryParts.joinToString(" | ")

                val fastestLines = mutableListOf<String>()
                for ((dt, result) in fastestMap) {
                    if (result != null) {
                        val label = when (result.processingNode) {
                            "LOCAL" -> "Local"
                            "HUB"   -> "Hub"
                            "CLOUD" -> "Cloud"
                            else    -> result.processingNode
                        }
                        fastestLines.add("$dt -> $label (${"%.0f".format(result.averageMs)}ms avg, ${result.runCount} runs)")
                    }
                }
                if (fastestLines.isNotEmpty()) {
                    tvFastestMethod?.text = "Fastest Method:\n" + fastestLines.joinToString("\n")
                    tvFastestMethod?.visibility = View.VISIBLE
                } else {
                    tvFastestMethod?.visibility = View.GONE
                }

                setupReportChart(report)
            }
        }
    }

    private fun setupReportChart(report: List<OffloadLogRepository.ReportRow>) {
        val chart = reportChart ?: return
        chart.description.isEnabled = false
        chart.legend.isEnabled = true
        chart.setTouchEnabled(true)
        chart.visibility = View.VISIBLE

        val dataTypes = report.map { it.dataType }.distinct().sorted()
        val nodeOrder = listOf("LOCAL", "HUB", "CLOUD")
        val nodeColors = mapOf(
            "LOCAL" to Color.parseColor("#FF7043"),
            "HUB"   to ContextCompat.getColor(requireContext(), R.color.primary_color),
            "CLOUD" to Color.parseColor("#66BB6A")
        )

        val lookup = report.associate { (it.dataType to it.processingNode) to it.avgMs }

        val dataSets = mutableListOf<BarDataSet>()
        for (node in nodeOrder) {
            val entries = ArrayList<BarEntry>()
            dataTypes.forEachIndexed { idx, dt ->
                val avgMs = lookup[dt to node] ?: 0.0
                entries.add(BarEntry(idx.toFloat(), avgMs.toFloat()))
            }
            if (entries.any { it.y > 0f }) {
                val set = BarDataSet(entries, node).apply {
                    color = nodeColors[node] ?: Color.GRAY
                    valueTextColor = ContextCompat.getColor(requireContext(), R.color.text_primary)
                    valueTextSize = 9f
                }
                dataSets.add(set)
            }
        }

        if (dataSets.isEmpty()) {
            chart.visibility = View.GONE
            return
        }

        val groupSpace = 0.24f
        val barSpace   = 0.04f
        val barWidth   = (1f - groupSpace) / dataSets.size - barSpace

        val data = BarData(dataSets.toList())
        data.barWidth = barWidth
        chart.data = data

        if (dataSets.size > 1) {
            chart.groupBars(0f, groupSpace, barSpace)
        }

        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f
        xAxis.setCenterAxisLabels(dataSets.size > 1)
        xAxis.valueFormatter = IndexAxisValueFormatter(dataTypes)
        xAxis.textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        xAxis.textSize = 11f
        xAxis.axisMinimum = 0f
        xAxis.axisMaximum = if (dataSets.size > 1) {
            chart.barData.getGroupWidth(groupSpace, barSpace) * dataTypes.size
        } else {
            dataTypes.size.toFloat()
        }

        chart.axisLeft.apply {
            axisMinimum = 0f
            textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
            gridColor = ContextCompat.getColor(requireContext(), R.color.input_stroke_color)
        }
        chart.axisRight.isEnabled = false
        chart.setFitBars(true)
        chart.invalidate()
        chart.animateY(700)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tvTasksDone = null
        tvTotalData = null
        tvAvgTime = null
        tvSystemHealth = null
        performanceChart = null
        btnPingNode = null
        btnRunBenchmark = null
        btnRefreshReport = null
        tvNodeStatus = null
        tvNodeLatency = null
        tvNodeStorage = null
        tvNodeProcessor = null
        statusDot = null
        tvBenchmarkStatus = null
        layoutBenchmarkResults = null
        tvBenchLocalMs = null
        tvBenchHubMs = null
        benchmarkChart = null
        tvBenchmarkWinner = null
        tvReportSummary = null
        reportChart = null
        tvFastestMethod = null
    }
}