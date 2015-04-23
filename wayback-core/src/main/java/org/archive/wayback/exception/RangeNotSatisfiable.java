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

package org.archive.wayback.exception;

import javax.servlet.http.HttpServletResponse;

import org.archive.wayback.core.Resource;

/**
 * RangeNotSatisfiable is thrown when selected Resource does not have content data
 * that can satisfy requested range.
 * This happens when the Resource itself is a capture of range request that does not cover
 * requested range.
 * @see org.archive.wayback.replay.TransparentReplayRenderer
 */
public class RangeNotSatisfiable extends SpecificCaptureReplayException {
	private static final long serialVersionUID = 1L;

	private Resource origResource;
	private long[][] requestedRanges;

	public RangeNotSatisfiable(Resource origResource, long[][] requestedRanges, String message) {
		super(message, "RangeNotSatisfiable");
		this.origResource = origResource;
		this.requestedRanges = requestedRanges;
	}

	@Override
	public int getStatus() {
		return HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE;
	}

	public Resource getOrigResource() {
		return origResource;
	}

	public long[][] getRequestedRanges() {
		return requestedRanges;
	}
}
