package org.archive.wayback.accesscontrol.oracleclient;

import java.util.Date;
import java.util.logging.Logger;

import org.archive.accesscontrol.RobotsUnavailableException;
import org.archive.accesscontrol.RuleOracleUnavailableException;
import org.archive.util.ArchiveUtils;
import org.archive.wayback.accesspoint.AccessPointAdapter;
import org.archive.wayback.core.CaptureSearchResult;
import org.archive.wayback.util.ObjectFilter;

/**
 * Oracle Filter Implementation that supports custom policies in addition to
 * allow, block, block-message and robots
 *
 * The policy is stored in the CaptureSearchResult
 *
 * <p>
 * Note: this class is being re-designed to allow for run-time customization
 * (i.e. with Spring config):
 * <ul>
 * <li>Redefine {@code Policy} as an interface + abstract implementation.</li>
 * <li>Define concrete instances for well-known policies like {@code block},
 * {@code allow} and {@code robots}.</li>
 * <li>Add a property for configurable list of {@code Policy}s, in a class
 * instantiating this object (factory?)</li>
 * </ul>
 * {@code Policy} enum below is re-designed toward in this direction.
 * The second argument of {@link Policy#apply(CaptureSearchResult, OracleExclusionFilter)}
 * is very likely to be changed to more abstract interface.
 * </p>
 * <p>
 * Although there's a factory for this class,
 * {@link CustomPolicyOracleFilterFactory}, creation is hard-coded in
 * {@link AccessPointAdapter} currently.
 * </p>
 * @see CustomPolicyOracleFilterFactory
 * @see AccessPointAdapter
 */
public class CustomPolicyOracleFilter extends OracleExclusionFilter {

	private static final Logger LOGGER = Logger
			.getLogger(CustomPolicyOracleFilter.class.getName());

	// TODO: redefine this enum as ordinary base class with well-known
	// instalces to make CustomPolicyOracleFilter runtime-configurable.
	enum Policy {
		ALLOW("allow"),
		BLOCK_HIDDEN("block") {
			@Override
			int apply(CaptureSearchResult capture, OracleExclusionFilter filter) {
				// mark capture blocked, and include in the result (see ARI-3879).
				// no message is given to user.
				capture.setRobotFlag(CaptureSearchResult.CAPTURE_ROBOT_BLOCKED);
				//return FILTER_EXCLUDE;
				return FILTER_INCLUDE;
			}
		},
		BLOCK_MESSAGE("block-message") {
			@Override
			int apply(CaptureSearchResult capture, OracleExclusionFilter filter) {
				return filter.handleBlock();
			}
		},
		ROBOTS("robots") {
			@Override
			int apply(CaptureSearchResult capture, OracleExclusionFilter filter) {
				return filter.handleRobots();
			}
		}
		;

		Policy(String policy) {
			this.policy = policy;
		}

		boolean matches(String other) {
			return (other.equals(this.policy));
		}

		final String policy;

		/**
		 * Apply policy. Bare minimum required is to return one of {@link ObjectFilter}
		 * result code. It may call {@code handle*} methods on {@code filter} for
		 * common policy handling, and/or modify {@code capture}.
		 * <p>TODO: define abstract interface for allow/block notifications defined
		 * in {@code OracleExclusionFilter}.</p>
		 * @param capture CaptureSearchResult
		 * @param filter OracleExclusionFilter object calling this method.
		 * @return one of {@link ObjectFilter} result codes.
		 */
		int apply(CaptureSearchResult capture, OracleExclusionFilter filter) {
			return filter.handleAllow();
		}
	}

	protected int defaultFilter = FILTER_INCLUDE;



	public CustomPolicyOracleFilter(String oracleUrl, String accessGroup,
			String proxyHostPort) {
		super(oracleUrl, accessGroup, proxyHostPort);
	}

	@Override
	public int filterObject(CaptureSearchResult o) {
		String url = o.getOriginalUrl();
		Date captureDate = o.getCaptureDate();
		Date retrievalDate = new Date();

		String policy;
		try {
			policy = client.getPolicy(
				ArchiveUtils.addImpliedHttpIfNecessary(url), captureDate,
				retrievalDate, accessGroup);

			o.setOraclePolicy(policy);

			if (policy == null) {
				return defaultFilter;
			}
			for (Policy handler : Policy.values()) {
				if (handler.matches(policy)) {
					return handler.apply(o, this);
				}
			}
			// unhandled policy is okay. it's just passed to upper-level
			// through CaptureSearchResult#oraclePolicy.
		} catch (RobotsUnavailableException e) {
			e.printStackTrace();
		} catch (RuleOracleUnavailableException e) {
			LOGGER.warning(
				"Oracle Unavailable/not running, default to allow all until it responds. Details: " +
						e.toString());
		}

		return defaultFilter;
	}
}
