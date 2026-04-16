import time

class AdvancedOffloadDecisionEngine:
    def __init__(self):
        # --- Device Constraints (Simulated Phone Specs) ---
        self.CPU_SPEED_MOBILE = 2.0  # GHz (Approximate)
        self.CPU_SPEED_CLOUD = 10.0  # GHz (Cloud is much faster)
        self.WIFI_Bandwidth_MBps = 5.0 # Average upload speed
        
        # --- Energy Consumption Models (Watts) ---
        self.POWER_CPU_ACTIVE = 1.5  # Heavy load on phone
        self.POWER_WIFI_ACTIVE = 0.8 # Radio transmission power
        self.POWER_IDLE = 0.1        # Waiting for response

        # --- User Preference Weights (0.0 to 1.0) ---
        # If user wants speed, weight_time is high.
        # If user wants battery, weight_energy is high.
        self.WEIGHT_TIME = 0.5   
        self.WEIGHT_ENERGY = 0.5 

    def estimate_local_cost(self, task_complexity_cycles):
        """
        Calculate Cost of Local Execution
        Time = Cycles / CPU_Speed
        Energy = Power * Time
        """
        estimated_time = task_complexity_cycles / (self.CPU_SPEED_MOBILE * 1e9) # seconds
        estimated_energy = self.POWER_CPU_ACTIVE * estimated_time # Joules
        
        # Total Weighted Cost
        cost = (self.WEIGHT_TIME * estimated_time) + (self.WEIGHT_ENERGY * estimated_energy)
        return cost, estimated_time, estimated_energy

    def estimate_cloud_cost(self, data_size_mb, task_complexity_cycles):
        """
        Calculate Cost of Cloud Execution
        Time = Upload Time + Process Time + RTT
        Energy = WiFi Power * Upload Time + Idle Power * Process Time
        """
        # 1. Transmission Time (Upload)
        transmission_time = data_size_mb / self.WIFI_Bandwidth_MBps
        
        # 2. Remote Processing Time (Faster CPU)
        process_time = task_complexity_cycles / (self.CPU_SPEED_CLOUD * 1e9)
        
        # 3. Network Latency (RTT - Round Trip Time)
        latency_rtt = 0.050 # 50ms fixed overhead
        
        total_time = transmission_time + process_time + latency_rtt
        
        # Energy: High power usage during upload, low power while waiting idle
        energy_upload = self.POWER_WIFI_ACTIVE * transmission_time
        energy_idle = self.POWER_IDLE * (process_time + latency_rtt)
        total_energy = energy_upload + energy_idle
        
        # Total Weighted Cost
        cost = (self.WEIGHT_TIME * total_time) + (self.WEIGHT_ENERGY * total_energy)
        return cost, total_time, total_energy

    def make_decision(self, task_name, data_size_mb, complexity_cycles):
        local_cost, t_local, e_local = self.estimate_local_cost(complexity_cycles)
        cloud_cost, t_cloud, e_cloud = self.estimate_cloud_cost(data_size_mb, complexity_cycles)

        print(f"\n--- Decision Analysis for: {task_name} ---")
        print(f"[LOCAL] Est. Time: {t_local:.4f}s | Energy: {e_local:.4f}J | Cost Score: {local_cost:.4f}")
        print(f"[CLOUD] Est. Time: {t_cloud:.4f}s | Energy: {e_cloud:.4f}J | Cost Score: {cloud_cost:.4f}")

        if cloud_cost < local_cost:
            benefit = ((local_cost - cloud_cost) / local_cost) * 100
            print(f"DECISION: OFFLOAD TO CLOUD (Cost Benefit: {benefit:.1f}%)")
            return "CLOUD"
        else:
            print(f"DECISION: EXECUTE LOCALLY (Network overhead is too high)")
            return "LOCAL"

# --- Run Scenarios ---
if __name__ == "__main__":
    engine = AdvancedOffloadDecisionEngine()

    # Scenario 1: Identify Face (Small Image, Huge Computation)
    # 0.5 MB Image, 5 Billion CPU Cycles (Neural Net inference)
    engine.make_decision("Face Recognition", data_size_mb=0.5, complexity_cycles=5e9)

    # Scenario 2: Apply Simple Filter (Large Image, Simple Math)
    # 5.0 MB Image, 0.2 Billion CPU Cycles (Pixel loop)
    engine.make_decision("Sepia Filter", data_size_mb=5.0, complexity_cycles=0.2e9)
