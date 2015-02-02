package org.archive.cdxserver;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.archive.cdxserver.auth.AllAccessAuth;
import org.archive.cdxserver.auth.AuthChecker;
import org.archive.cdxserver.auth.AuthToken;
import org.archive.url.HandyURL;
import org.archive.url.URLParser;
import org.archive.url.UrlSurtRangeComputer;
import org.archive.url.UrlSurtRangeComputer.MatchType;
import org.archive.url.WaybackURLKeyMaker;
import org.archive.util.ArchiveUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Controller;

@Controller
public class BaseCDXServer implements InitializingBean {
	
	public final static String CDX_AUTH_TOKEN = "cdx_auth_token";

	protected String cookieAuthToken = CDX_AUTH_TOKEN;
	
	protected WaybackURLKeyMaker keyMaker = null;
	protected AuthChecker authChecker;
	protected String ajaxAccessControl;
	
	protected boolean surtMode = false;

	public boolean isSurtMode() {
		return surtMode;
	}

	public void setSurtMode(boolean surtMode) {
		this.surtMode = surtMode;
	}
	
	public String getCookieAuthToken() {
		return cookieAuthToken;
	}

	public void setCookieAuthToken(String cookieAuthToken) {
		this.cookieAuthToken = cookieAuthToken;
	}

	/**
	 * Set {@link WaybackURLKeyMaker}
	 * @param canonicalizer WaybackURLKeyMaker
	 * @deprecated use {@link #setKeyMaker(WaybackURLKeyMaker)}
	 */
	public void setCanonicalizer(WaybackURLKeyMaker canonicalizer) {
		this.keyMaker = canonicalizer;
	}

	/**
	 * Set {@link WaybackURLKeyMaker} used for building
	 * CDX lookup key from URL.
	 * @param keyMaker WaybackURLKeyMaker
	 */
	public void setKeyMaker(WaybackURLKeyMaker keyMaker) {
		this.keyMaker = keyMaker;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		if (authChecker == null) {
			authChecker = new AllAccessAuth();
		}
		if (keyMaker == null) {
			keyMaker = new WaybackURLKeyMaker(surtMode);
		}
	}
	
	public String canonicalize(String url) throws UnsupportedEncodingException, URISyntaxException {
		return canonicalize(url, surtMode);
	}

	public String canonicalize(String url, boolean surt) throws UnsupportedEncodingException, URISyntaxException
	{
		if ((keyMaker == null) || (url == null) || url.isEmpty()) {
			return url;
		}
		
		url = java.net.URLDecoder.decode(url, "UTF-8");
		
		if (surt) {
			return url;
		}
		
		int slashIndex = url.indexOf('/');
		// If true, assume this is already a SURT and skip
		if ((slashIndex > 0) && url.charAt(slashIndex - 1) == ')') {
			return url;
		}
						
		return keyMaker.makeKey(url);
	}
	
	protected void prepareResponse(HttpServletResponse response)
	{
		response.setContentType("text/plain; charset=\"UTF-8\"");
	}
	
	protected void handleAjax(HttpServletRequest request, HttpServletResponse response)
	{
	    String origin = request.getHeader("Origin");
	    
	    if (origin == null) {
	        return;
	    }
	    
	    response.setHeader("Access-Control-Allow-Credentials", "true");
	    response.setHeader("Access-Control-Allow-Origin", origin);
	}
		
	public AuthChecker getAuthChecker() {
		return authChecker;
	}

	public void setAuthChecker(AuthChecker authChecker) {
		this.authChecker = authChecker;
	}

    public String getAjaxAccessControl() {
        return ajaxAccessControl;
    }

    public void setAjaxAccessControl(String ajaxAccessControl) {
        this.ajaxAccessControl = ajaxAccessControl;
    }
    
    protected AuthToken createAuthToken(HttpServletRequest request)
    {
    	return new AuthToken(extractAuthToken(request, cookieAuthToken));
    }
    
    protected String extractAuthToken(HttpServletRequest request, String cookieAuthToken) {
		Cookie[] cookies = request.getCookies();

		if (cookies == null) {
			return null;
		}

		for (Cookie cookie : cookies) {
			if (cookie.getName().equals(cookieAuthToken)) {
				return cookie.getValue();
			}
		}

		return null;
	}

    /**
     * Return three keys that defines the span of captures to scan for the
     * query criteria given.
     * <p>
     * This is a copy of {@link UrlSurtRangeComputer#determineRange(String, MatchType, String, String)}
     * with slight change for sharing URL canonicalizer with BaseCDXServer.
     * Once code stabilizes, we may want to submit a change request to webarchive-commons, if that's appropriate.
     * </p>
     * <p>
     * {@code from} and {@code to} is effective only when {@code match} is
     * either {@code exact}.
     * @param url URL being queried
     * @param match match mode
     * @param from start timestamp, or empty (must not be {@code null})
     * @param to end timestamp, or or empty (must not be {@code null})
     * @return an array of length 2, ({@code startKey}, {@code endKey}), or
     * {@code null} if query criteria is invalid.
     * @throws URISyntaxException
     */
    protected String[] determineRange(String url, MatchType match, String from, String to) throws URISyntaxException {
		String startKey = null;
		String endKey = null;

		if (url.startsWith(".")) {
			url = url.substring(1);
		}

		HandyURL hURL = URLParser.parse(url);

		String host = hURL.getHost();

//		if (hURL.getPath().isEmpty()) {
//			hURL.setPath("/");
//		}

//		if ((match == MatchType.prefix) && hURL.getPath().equals("/")) {
//			match = MatchType.host;
//		}

		switch (match) {
		case exact:
			startKey = keyMaker.makeKey(url);
			endKey = to.isEmpty() ? startKey + "!" : startKey + " " + ArchiveUtils.dateToTimestamp(to);
			if (!from.isEmpty()) {
				startKey += " " + ArchiveUtils.dateToTimestamp(from);
			}
			break;

		case prefix:
			// for prefix match, we want to retain the trailing slash.
			// typical canonicalization strips it off.
			boolean urlEndsWithSlash = url.endsWith("/");
			startKey = keyMaker.makeKey(url);
			if (urlEndsWithSlash && !startKey.endsWith("/")) {
				startKey += "/";
			}
			endKey = incLastChar(startKey);
			break;

		case host:
			startKey = keyMaker.makeKey(host);
			endKey = incLastChar(startKey);
			break;

		case domain:
			// only supported in SURT-mode
			if (!surtMode) return null;
			startKey = keyMaker.makeKey(host);
			// assuming startKey ends with ")/"; replace it with "-", the
			// next char of ","
			endKey = startKey.substring(0, startKey.length() - 2) + "-";
			break;
		}
		return new String[] { startKey, endKey };
    }

	private static String incLastChar(String input) {
		StringBuilder sb = new StringBuilder(input);
		sb.setCharAt(sb.length() - 1, (char)(sb.charAt(sb.length() - 1) + 1));
		return sb.toString();
	}

}