/**
 *
 */
package org.archive.wayback.resourcestore;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.archive.wayback.core.CaptureSearchResult;
import org.archive.wayback.core.Resource;
import org.archive.wayback.exception.ResourceNotAvailableException;
import org.archive.wayback.resourcestore.FlexResourceStore.PathIndex;
import org.archive.wayback.resourcestore.FlexResourceStore.SourceResolver;

/**
 * Test for {@link FlexResourceStore}
 */
public class FlexResourceStoreTest extends TestCase {

	FlexResourceStore cut;

	/* (non-Javadoc)
	 * @see junit.framework.TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();

		cut = new FlexResourceStore();
	}

	/**
	 * Missing path index file shall be handled gracefully.
	 * <p>
	 * {@link FlexResourceStore#retrieveResource(CaptureSearchResult)}
	 * shall throw {@link ResourceNotAvailableException} with useful
	 * error message.
	 * </p>
	 */
	public void testMissingPathIndex() throws Exception {
		final String pathIndexPath = "/this/should/never/exist.txt";
		PathIndex pathIndex = new PathIndex();
		pathIndex.setPathIndex(pathIndexPath);
		List<SourceResolver> sources = new ArrayList<SourceResolver>();
		sources.add(pathIndex);
		cut.setSources(sources);

		CaptureSearchResult capture = new CaptureSearchResult();
		capture.setFile("aaaa.warc.gz");
		// arbitrary numbers
		capture.setOffset(1234);
		capture.setCompressedLength(333);

		try {
			Resource res = cut.retrieveResource(capture);
			fail("retrieveResource did not throw an exception, returned " + res);
		} catch (ResourceNotAvailableException ex) {
			// expected
		}
	}
}
