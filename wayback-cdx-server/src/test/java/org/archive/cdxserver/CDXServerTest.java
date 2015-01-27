package org.archive.cdxserver;

import org.archive.url.NonMassagingIAURLCanonicalizer;
import org.archive.url.URLCanonicalizer;
import org.archive.url.UrlSurtRangeComputer.MatchType;
import org.archive.url.WaybackURLKeyMaker;

import junit.framework.TestCase;

public class CDXServerTest extends TestCase {

	CDXServer cut;

	protected void setUp() throws Exception {
		super.setUp();
		cut = new CDXServer();
		cut.setSurtMode(true);
		// using default URLKeyMaker
		cut.afterPropertiesSet();
	}

	protected void assertInRange(String[] range, String cdxline) {
		assertTrue(cdxline.compareTo(range[0]) >= 0);
		assertTrue(cdxline.compareTo(range[1]) < 0);
	}

	protected void assertNotInRange(String[] range, String cdxline) {
		assertTrue(cdxline.compareTo(range[0]) < 0 || cdxline.compareTo(range[1]) >= 0);
	}

	public void testDetermineRangeExact() throws Exception {
		final String url = "http://example.com/";

		String[] range = cut.determineRange(url, MatchType.exact, "", "");
		assertInRange(range, "com,example)/ 20010101000000");
		assertNotInRange(range, "com,example)/image/a.gif 20010101000000");
	}

	public void testDetermineRangeExactDated() throws Exception {
		final String url = "http://example.com/";

		String[] range = cut.determineRange(url, MatchType.exact, "01/01/2001", "01/01/2014");
		assertNotInRange(range, "com,example)/ 20001231235959");
		assertInRange(range, "com,example)/ 20010101000000");
		assertNotInRange(range, "com,example)/ 20140201000000");
		assertNotInRange(range, "com,example)/image/a.gif 20010101000000");
	}

	public void testDetermineRangePrefix() throws Exception {
		final String url = "http://example.com/image/";

		String[] range = cut.determineRange(url, MatchType.prefix, "", "");
		assertNotInRange(range, "com,example)/image 20010101000000");
		assertInRange(range, "com,example)/image/ 20010101000000");
		assertInRange(range, "com,example)/image/a.gif 20010101000000");
		assertNotInRange(range, "com,example)/internal/a.sec 20010101000000");
	}

	public void testDetermineRangeHost() throws Exception {
		final String url = "http://example.com/";

		String[] range = cut.determineRange(url, MatchType.host, "", "");
		assertNotInRange(range, "com,exampl)/zoo 20010101000000");
		assertInRange(range, "com,example)/ 20010101000000");
		assertInRange(range, "com,example)/image/a.gif 20010101000000");
		assertNotInRange(range, "com,example,blog)/image/x.gif 20010101000000");
		assertNotInRange(range, "com,example0)/search 20010101000000");
	}

	public void testDetermineRangeDomain() throws Exception {
		final String url = "http://example.com/";

		String[] range = cut.determineRange(url, MatchType.domain, "", "");
		assertNotInRange(range, "com,exampl)/zoo 20010101000000");
		assertInRange(range, "com,example)/ 20010101000000");
		assertInRange(range, "com,example)/image/a.gif 20010101000000");
		assertInRange(range, "com,example,blog)/image/x.gif 20010101000000");
		assertNotInRange(range, "com,example0)/search 20010101000000");
	}

	public void testNonDefaultKeyMaker() throws Exception {
		final String url = "http://www.example.com/";
		WaybackURLKeyMaker keyMaker = new WaybackURLKeyMaker(true);
		URLCanonicalizer canonicalizer = new NonMassagingIAURLCanonicalizer();
		keyMaker.setCanonicalizer(canonicalizer);
		cut.setKeyMaker(keyMaker);

		String[] range = cut.determineRange(url, MatchType.exact, "", "");
		assertNotInRange(range, "com,example)/ 20010101000000");
		assertInRange(range, "com,example,www)/ 20010101000000");
		assertNotInRange(range, "com,example,www)/a.html 20010101000000");
	}
}
