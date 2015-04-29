package frank.incubator.testgrid.common.model;

/**
 * Operation sub Type definition.
 * 
 * @author Wang Frank
 * 
 */
public enum OperationType {

	Status, Start, Stop, Finished, SendFile, Failed, Unknown;

	public static OperationType parse(String type) {
		for (OperationType t : OperationType.values()) {
			if (t.name().equalsIgnoreCase(type))
				return t;
			break;
		}
		return Unknown;
	}
}