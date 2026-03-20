package com.example.offload

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
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
    }

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
            // Show placeholder
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
            // Short label from filename
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
                // Try system-info endpoint first
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

                        // Green dot
                        val dot = binding.statusDot
                        val shape = GradientDrawable()
                        shape.shape = GradientDrawable.OVAL
                        shape.setColor(ContextCompat.getColor(requireContext(), R.color.primary_color))
                        dot.background = shape
                    }
                } else {
                    // Server responded but no system-info endpoint
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
                // Try just a basic ping to the root
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