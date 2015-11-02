package frank.incubator.testgrid.common.file;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * DuplicatedOutputStream can be set multiple outputStreams, once incoming data
 * been written, all the outputStreams would be written same bytes.
 * 
 * @author peiyang.wy
 *
 */
public class DuplicatedOutputStream extends OutputStream {

	public DuplicatedOutputStream addOutputStream(OutputStream os) {
		if (os == null)
			throw new NullPointerException("Given OutputStream contains NULL pointer.");
		if (!realTargets.contains(os))
			realTargets.add(os);
		return this;
	}

	private List<OutputStream> realTargets = new ArrayList<OutputStream>();

	public DuplicatedOutputStream(OutputStream... outputStreams) {
		if (outputStreams != null) {
			for (OutputStream os : outputStreams) {
				if (os != null) {
					realTargets.add(os);
				} else {
					throw new NullPointerException("Given OutputStream contains NULL pointer.");
				}
			}
		}
	}

	public DuplicatedOutputStream remove(OutputStream os) {
		if (os != null)
			realTargets.remove(os);
		return this;
	}

	public boolean contains(OutputStream os) {
		if (os == null)
			return false;
		if (realTargets.contains(os)) {
			return true;
		}
		return false;
	}

	@Override
	public void write(int b) throws IOException {
		for (OutputStream os : realTargets) {
			os.write(b);
		}
	}

	@Override
	public void flush() throws IOException {
		for (OutputStream os : realTargets) {
			os.flush();
		}
	}

	@Override
	public void close() throws IOException {
		for (OutputStream os : realTargets) {
			os.close();
		}
	}
}
