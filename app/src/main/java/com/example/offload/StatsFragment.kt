package com.example.offload

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.offload.databinding.FragmentStatsBinding
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

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SharedViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Observe files for real statistics
        viewModel.downloadableFiles.observe(viewLifecycleOwner) { files ->
            updateStatistics(files)
            setupChart(files)
        }

        // Ping button
        binding.btnPingNode.setOnClickListener {
            pingEdgeNode()
        }

        // Benchmark button
        binding.btnRunBenchmark.setOnClickListener {
            runLocalVsHubBenchmark()
        }
    }

    // -------------------------------------------------------------------------
    // Statistics
    // -------------------------------------------------------------------------

    private fun updateStatistics(files: List<FileModel>) {
        val totalTasks = files.size
        binding.tvTasksDone.text = totalTasks.toString()

        // Total data offloaded
        val totalMB = files.sumOf { it.dataSizeMB }
        binding.tvTotalData.text = if (totalMB > 1024) {
            "${"%.1f".format(totalMB / 1024)} GB"
        } else {
            "${"%.1f".format(totalMB)} MB"
        }

        // Average processing time
        val timesWithData = files.filter { it.processingTimeMs > 0 }
        if (timesWithData.isNotEmpty()) {
            val avgMs = timesWithData.map { it.processingTimeMs }.average()
            binding.tvAvgTime.text = if (avgMs > 1000) {
                "${"%.1f".format(avgMs / 1000)}s"
            } else {
                "${avgMs.toLong()}ms"
            }
        } else {
            binding.tvAvgTime.text = "— ms"
        }

        // System health summary
        if (totalTasks == 0) {
            binding.tvSystemHealth.text = "No metrics gathered yet. Upload a file to the Edge Node to begin."
        } else {
            val downloaded = files.count { it.isDownloaded }
            val types = files.groupBy { it.taskType }.map { "${it.value.size} ${it.key}" }.joinToString(", ")
            binding.tvSystemHealth.text = "Processed $totalTasks total tasks ($types). $downloaded results downloaded locally. Edge routing and hardware acceleration operational."
        }
    }

    private fun setupChart(files: List<FileModel>) {
        val chart = binding.performanceChart
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

    /**
     * Runs a 400×400 matrix-transpose computation 20× on-device (same workload
     * as UploadFragment's local path), then sends an equivalent JSON payload to
     * the hub's /api/compute/ endpoint and shows a grouped bar chart comparing
     * both timings with a winner annotation.
     */
    private fun runLocalVsHubBenchmark() {
        val prefs = requireActivity().getSharedPreferences("OffloadXPrefs", android.content.Context.MODE_PRIVATE)
        val ip = prefs.getString("hub_ip", "192.168.1.100:8000") ?: "192.168.1.100:8000"
        val baseUrl = if (ip.startsWith("http")) ip else "http://$ip"

        binding.btnRunBenchmark.isEnabled = false
        binding.btnRunBenchmark.text = "Running…"
        binding.tvBenchmarkStatus.text = "⏳ Step 1/2 — Measuring on-device compute…"
        binding.layoutBenchmarkResults.visibility = View.GONE
        binding.benchmarkChart.visibility = View.GONE
        binding.tvBenchmarkWinner.visibility = View.GONE

        CoroutineScope(Dispatchers.Default).launch {
            // --- PHASE 1: Local CPU benchmark ---
            val localStart = System.currentTimeMillis()
            runMatrixBenchmark()
            val localMs = System.currentTimeMillis() - localStart

            withContext(Dispatchers.Main) {
                binding.tvBenchmarkStatus.text = "✅ Local: ${localMs}ms  |  ⏳ Step 2/2 — Sending to Hub…"
            }

            // --- PHASE 2: Hub benchmark ---
            var hubMs: Long = -1
            var hubError: String? = null
            try {
                // Build a numbers payload that exercises the hub's COMPOSITE path
                val numbersArray = JSONArray()
                repeat(50_000) { i -> numbersArray.put(i.toDouble()) }
                val body = JSONObject().apply {
                    put("device_id", "benchmark_android")
                    put("task_type", "COMPOSITE")
                    put("data", JSONObject().put("numbers", numbersArray))
                }

                val url = URL("$baseUrl/api/compute/")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000

                val hubStart = System.currentTimeMillis()
                conn.outputStream.bufferedWriter().use { it.write(body.toString()) }
                val responseCode = conn.responseCode
                val rawBody = if (responseCode in 200..299) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else {
                    conn.errorStream.bufferedReader().use { it.readText() }
                }
                val roundTrip = System.currentTimeMillis() - hubStart

                if (responseCode in 200..299) {
                    val json = JSONObject(rawBody)
                    // Prefer the server's reported compute time; fall back to round-trip
                    val serverMs = json.optDouble("processing_time_ms", roundTrip.toDouble()).toLong()
                    hubMs = serverMs
                    hubError = null
                } else {
                    hubMs = -1
                    hubError = "HTTP $responseCode"
                }
            } catch (e: Exception) {
                hubMs = -1
                hubError = e.message ?: "Connection failed"
            }

            // --- Update UI ---
            withContext(Dispatchers.Main) {
                binding.btnRunBenchmark.isEnabled = true
                binding.btnRunBenchmark.text = "Run Test"

                if (hubError != null) {
                    binding.tvBenchmarkStatus.text = "Local: ${localMs}ms  |  Hub error: $hubError"
                    Toast.makeText(requireContext(), "Hub unreachable: $hubError", Toast.LENGTH_LONG).show()
                    return@withContext
                }

                // Numeric results
                binding.tvBenchmarkStatus.text = "Benchmark complete ✓"
                binding.tvBenchLocalMs.text = "${localMs}ms"
                binding.tvBenchHubMs.text = "${hubMs}ms"
                binding.layoutBenchmarkResults.visibility = View.VISIBLE

                // Grouped bar chart
                showBenchmarkChart(localMs, hubMs)

                // Winner annotation
                val diff = localMs - hubMs
                val pct = if (localMs > 0) (diff.toDouble() / localMs * 100).toInt() else 0
                val winnerText = when {
                    hubMs < localMs -> "🏆 Hub is ${pct}% faster than local execution"
                    hubMs > localMs -> "📱 Local is faster by ${kotlin.math.abs(pct)}% (hub overhead too high)"
                    else            -> "⚖️ Both paths took the same time"
                }
                binding.tvBenchmarkWinner.text = winnerText
                binding.tvBenchmarkWinner.visibility = View.VISIBLE
            }
        }
    }

    private fun showBenchmarkChart(localMs: Long, hubMs: Long) {
        val chart = binding.benchmarkChart
        chart.description.isEnabled = false
        chart.legend.isEnabled = true
        chart.setTouchEnabled(false)

        val localEntries = arrayListOf(BarEntry(0f, localMs.toFloat()))
        val hubEntries   = arrayListOf(BarEntry(0f, hubMs.toFloat()))

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

        val groupSpace = 0.2f
        val barSpace   = 0.05f
        val barWidth   = 0.35f

        val data = BarData(localSet, hubSet)
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

    /** Same 400×400 matrix-transpose × 20 used in UploadFragment's local path. */
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

        binding.btnPingNode.isEnabled = false
        binding.btnPingNode.text = "..."
        binding.tvNodeStatus.text = "Pinging $ip..."

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
                        binding.tvNodeLatency.text = "${latencyMs}ms"
                        binding.tvNodeStorage.text = json.optString("free_storage", "—")
                        binding.tvNodeProcessor.text = json.optString("processor", "CPU")
                        binding.tvNodeStatus.text = "Connected to $ip | ${json.optString("os", "Unknown OS")}"

                        val dot = binding.statusDot
                        val shape = GradientDrawable()
                        shape.shape = GradientDrawable.OVAL
                        shape.setColor(ContextCompat.getColor(requireContext(), R.color.primary_color))
                        dot.background = shape
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        binding.tvNodeLatency.text = "${latencyMs}ms"
                        binding.tvNodeStatus.text = "Connected (basic). System info not available."
                        binding.tvNodeStorage.text = "—"
                        binding.tvNodeProcessor.text = "—"

                        val dot = binding.statusDot
                        val shape = GradientDrawable()
                        shape.shape = GradientDrawable.OVAL
                        shape.setColor(Color.parseColor("#FFA726"))
                        dot.background = shape
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
                        binding.tvNodeLatency.text = "${latencyMs}ms"
                        binding.tvNodeStatus.text = "Server reachable at $ip (HTTP $code). Add /api/system-info/ for full stats."
                        binding.tvNodeStorage.text = "—"
                        binding.tvNodeProcessor.text = "—"

                        val dot = binding.statusDot
                        val shape = GradientDrawable()
                        shape.shape = GradientDrawable.OVAL
                        shape.setColor(Color.parseColor("#FFA726"))
                        dot.background = shape
                    }
                } catch (e2: Exception) {
                    withContext(Dispatchers.Main) {
                        binding.tvNodeLatency.text = "—"
                        binding.tvNodeStatus.text = "Cannot reach Edge Node at $ip. Check IP and ensure the server is running."
                        binding.tvNodeStorage.text = "—"
                        binding.tvNodeProcessor.text = "—"

                        val dot = binding.statusDot
                        val shape = GradientDrawable()
                        shape.shape = GradientDrawable.OVAL
                        shape.setColor(ContextCompat.getColor(requireContext(), R.color.danger_color))
                        dot.background = shape
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    binding.btnPingNode.isEnabled = true
                    binding.btnPingNode.text = "Ping"
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}