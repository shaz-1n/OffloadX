package com.example.offload

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.offload.databinding.FragmentStatsBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate

class StatsFragment : Fragment() {

    // View Binding reference
    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!

    // Link to the SharedViewModel to get the task data
    private val viewModel: SharedViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Initialize the binding
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Observe the tasks list in the ViewModel
        viewModel.tasks.observe(viewLifecycleOwner) { tasks ->
            updateStatistics(tasks)
        }
        
        setupChart()
    }

    private fun setupChart() {
        val chart = binding.performanceChart
        chart.description.isEnabled = false
        chart.setDrawGridBackground(false)
        
        // Setup X-Axis
        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.valueFormatter = IndexAxisValueFormatter(listOf("Cloud", "Local"))
        xAxis.granularity = 1f
        xAxis.textSize = 12f

        // Setup Y-Axis (Left)
        val leftAxis = chart.axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.axisMinimum = 0f
        
        // Disable Right Y-Axis
        chart.axisRight.isEnabled = false

        // Dummy Data: Time Taken (ms)
        // Cloud might be slower due to network, Local might be faster for small tasks
        val entries = ArrayList<BarEntry>()
        entries.add(BarEntry(0f, 120f)) // Cloud
        entries.add(BarEntry(1f, 45f))  // Local

        val dataSet = BarDataSet(entries, "Time Taken (ms)")
        dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList()
        dataSet.valueTextSize = 12f

        val data = BarData(dataSet)
        data.barWidth = 0.5f // Set custom bar width

        chart.data = data
        chart.invalidate() // refresh
        chart.animateY(1000)
    }

    private fun updateStatistics(tasks: List<Task>) {
        val totalTasks = tasks.size

        // Assuming you have TextViews in your fragment_stats.xml with these IDs:
        // Adjust these IDs if they match what you actually named them in XML
        binding.cardTopStats.findViewById<android.widget.TextView>(android.R.id.text1)?.text =
            "Total Offloaded: $totalTasks"

        // You can add more logic here to calculate efficiency or server response times
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Crucial: clear binding to avoid memory leaks
        _binding = null
    }
}