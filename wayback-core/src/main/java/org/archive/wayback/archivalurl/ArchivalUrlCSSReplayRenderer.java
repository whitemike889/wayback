/*
 *  This file is part of the Wayback archival access software
 *   (http://archive-access.sourceforge.net/projects/wayback/).
 *
 *  Licensed to the Internet Archive (IA) by one or more individual 
 *  contributors. 
 *
 *  The IA licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.archive.wayback.archivalurl;

import java.io.IOException;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.archive.wayback.ReplayRenderer;
import org.archive.wayback.ResultURIConverter;
import org.archive.wayback.core.CaptureSearchResult;
import org.archive.wayback.core.CaptureSearchResults;
import org.archive.wayback.core.Resource;
import org.archive.wayback.core.WaybackRequest;
import org.archive.wayback.exception.BadContentException;
import org.archive.wayback.replay.HttpHeaderOperation;
import org.archive.wayback.replay.HttpHeaderProcessor;
import org.archive.wayback.replay.TextDocument;
import org.archive.wayback.replay.TextReplayRenderer;

/**
 * {@link ReplayRenderer} that rewrites URLs found in CSS resource and inserts
 * {@code jspInserts} at the top of the document.
 * <p>This ReplayRenderer searches for URLs in CSS document, and rewrites
 * them with {@link ResultURIConverter} set to {@link TextDocument}.</p>
 * <p>In fact, this class simply calls {@link TextDocument#resolveCSSUrls()}
 * for URL rewrites.  Note that ResultURIConverter argument to {@code updatePage}
 * method is unused.</p>
 * <p>This class may be used in both Archival-URL and Proxy mode, despite its
 * name, by choosing appropriate {@code ResultURIConverter}.</p>
 * <p>There's separate classes for rewriting CSS text embedded
 * in HTML.  They use their own code for looking up URLs in CSS.</p>
 * @see TextDocument#resolveCSSUrls()
 * @see ResultURIConverter
 * @see org.archive.wayback.replay.html.transformer.BlockCSSStringTransformer
 * @see org.archive.wayback.replay.html.transformer.InlineCSSStringTransformer
 * @author brad
 *
 */
public class ArchivalUrlCSSReplayRenderer extends TextReplayRenderer {

	/**
	 * @param httpHeaderProcessor which should process HTTP headers
	 */
	public ArchivalUrlCSSReplayRenderer(HttpHeaderProcessor httpHeaderProcessor) {
		super(httpHeaderProcessor);
	}

	/* (non-Javadoc)
	 * @see org.archive.wayback.replay.HTMLReplayRenderer#updatePage(org.archive.wayback.replay.HTMLPage, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, org.archive.wayback.core.WaybackRequest, org.archive.wayback.core.CaptureSearchResult, org.archive.wayback.core.Resource, org.archive.wayback.ResultURIConverter, org.archive.wayback.core.CaptureSearchResults)
	 */
	@Override
	protected void updatePage(TextDocument page, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse, WaybackRequest wbRequest,
			CaptureSearchResult result, Resource resource,
			ResultURIConverter uriConverter, CaptureSearchResults results)
			throws ServletException, IOException {

		page.resolveCSSUrls();
		// if any CSS-specific jsp inserts are configured, run and insert...
		page.insertAtStartOfDocument(buildInsertText(page, httpRequest,
				httpResponse, wbRequest, results, result, resource));
	}

	@Override
    public void renderResource(HttpServletRequest httpRequest,
            HttpServletResponse httpResponse, WaybackRequest wbRequest,
            CaptureSearchResult result, Resource httpHeadersResource,
            Resource payloadResource, ResultURIConverter uriConverter,
            CaptureSearchResults results) throws ServletException,
            IOException, BadContentException {

        // Decode resource (such as if gzip encoded)
        Resource decodedResource = decodeResource(httpHeadersResource, payloadResource);

        HttpHeaderOperation.copyHTTPMessageHeader(httpHeadersResource, httpResponse);

        Map<String,String> headers = HttpHeaderOperation.processHeaders(
                httpHeadersResource, result, uriConverter, httpHeaderProcessor);

        String charSet = charsetDetector.getCharset(httpHeadersResource,
                decodedResource, wbRequest);

        ResultURIConverter pageConverter = uriConverter;
        // this feature was meant for using special ResultURIConverter for rewriting XML, but
        // turned out to be not useful. drop this unless we find other uses.
        if (pageConverterFactory != null) {
            // XXX: ad-hoc code - ContextResultURIConverterFactory should take ResultURIConverter
            // as argument, so that it can simply wrap the original.
            String replayURIPrefix = (uriConverter instanceof ArchivalUrlResultURIConverter ?
                    ((ArchivalUrlResultURIConverter)uriConverter).getReplayURIPrefix() : "");
            ResultURIConverter ruc = pageConverterFactory.getContextConverter(replayURIPrefix);
            if (ruc != null)
                pageConverter = ruc;
        }
        // Load content into an HTML page, and resolve load-time URLs:
        TextDocument page = new TextDocument(decodedResource, result,
                uriConverter);
        page.readFully(charSet);

        updatePage(page, httpRequest, httpResponse, wbRequest, result,
                decodedResource, pageConverter, results);

        // set the corrected length:
        int bytes = page.getBytes().length;
        headers.put(HttpHeaderOperation.HTTP_LENGTH_HEADER, String.valueOf(bytes));
        if (guessedCharsetHeader != null) {
            headers.put(guessedCharsetHeader, page.getCharSet());
        }

        // send back the headers:
        HttpHeaderOperation.sendHeaders(headers, httpResponse);

        // Tomcat will always send a charset... It's trying to be smarter than
        // we are. If the original page didn't include a "charset" as part of
        // the "Content-Type" HTTP header, then Tomcat will use the default..
        // who knows what that is, or what that will do to the page..
        // let's try explicitly setting it to what we used:
        httpResponse.setCharacterEncoding(page.getCharSet());
        httpResponse.setContentType("text/css");

        page.writeToOutputStream(httpResponse.getOutputStream());
    }
}
