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