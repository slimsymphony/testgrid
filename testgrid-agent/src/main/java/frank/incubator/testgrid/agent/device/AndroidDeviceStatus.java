package frank.incubator.testgrid.agent.device;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import frank.incubator.testgrid.common.model.BaseObject;

public class AndroidDeviceStatus extends BaseObject {

	public AndroidDeviceStatus(String id) {
		this.id = id;
	}

	private Map<String, Integer> memInfo = new HashMap<String, Integer>();

	private Map<Integer, Map<String, Object>> processes = new HashMap<Integer, Map<String, Object>>();

	private List<Integer> topCpuConsumingProcs = new ArrayList<Integer>();

	private List<Integer> topMemConsumingProcs = new ArrayList<Integer>();

	private long frequency;

	private String sim1Signal;

	private String sim2Signal;

	public void addProcessInfo(int pid, Map<String, Object> info) {
		processes.put(pid, info);
	}

	public Map<Integer, Map<String, Object>> getProcesses() {
		return processes;
	}

	public void setProcesses(Map<Integer, Map<String, Object>> processes) {
		this.processes = processes;
	}

	public List<Integer> getTopMemConsumingProcs() {
		return topMemConsumingProcs;
	}

	public void setTopMemConsumingProcs(List<Integer> topMemConsumingProcs) {
		this.topMemConsumingProcs = topMemConsumingProcs;
	}

	public List<Integer> getTopCpuConsumingProcs() {
		return topCpuConsumingProcs;
	}

	public void setTopCpuConsumingProcs(List<Integer> topCpuConsumingProcs) {
		this.topCpuConsumingProcs = topCpuConsumingProcs;
	}

	public long getFrequency() {
		return frequency;
	}

	public void setFrequency(long frequency) {
		this.frequency = frequency;
	}

	public String getSim1Signal() {
		return sim1Signal;
	}

	public void setSim1Signal(String sim1Signal) {
		this.sim1Signal = sim1Signal;
	}

	public String getSim2Signal() {
		return sim2Signal;
	}

	public void setSim2Signal(String sim2Signal) {
		this.sim2Signal = sim2Signal;
	}

	public Map<String, Integer> getMemInfo() {
		return memInfo;
	}

	public void setMemInfo(Map<String, Integer> memInfo) {
		this.memInfo = memInfo;
	}

	public void addMemInfo(String key, int value) {
		memInfo.put(key, value);
	}

}
