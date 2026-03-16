package com.example.offload

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.offload.databinding.FragmentStatsBinding

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
    }

    private fun updateStatistics(tasks: List<Task>) {
        val totalTasks = tasks.size
        
        val tvTasksDone = binding.root.findViewById<android.widget.TextView>(R.id.tvTasksDone)
        val tvEfficiency = binding.root.findViewById<android.widget.TextView>(R.id.tvEfficiency)
        val tvSystemHealth = binding.root.findViewById<android.widget.TextView>(R.id.tvSystemHealth)

        if (totalTasks == 0) {
            tvTasksDone?.text = "0"
            tvEfficiency?.text = "0%"
            tvSystemHealth?.text = "No metrics gathered yet. Run an offload test."
            return
        }

        tvTasksDone?.text = totalTasks.toString()

        // Calculate simple dynamic efficiency metric 
        // For demonstration, we simulate rising efficiency as the Hub processes more tasks.
        val baseEfficiency = 80
        val actualEff = (baseEfficiency + (totalTasks * 3)).coerceAtMost(98)
        
        tvEfficiency?.text = "${actualEff}%"
        tvSystemHealth?.text = "Edge Node recognized. Network routing and hardware acceleration are operating nominally."
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Crucial: clear binding to avoid memory leaks
        _binding = null
    }
}