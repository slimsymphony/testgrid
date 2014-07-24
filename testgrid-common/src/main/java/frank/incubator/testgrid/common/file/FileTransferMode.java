package frank.incubator.testgrid.common.file;

/**
 * Enumeration of FileTransferMode.
 * Including Active and Passive mode, which been used in different network situation.
 * E.g.: When working in internal network, not firewall between agents and clients, both should be ok.
 * But when working in port limited (with Firewall affected) situation, should be TARGET_HOST mode, which can be a dedicated socket port.
 * @author Wang Frank
 *
 */
public enum FileTransferMode {
	SOURCE_HOST, TARGET_HOST, THIRDPARTY_HOST
}
