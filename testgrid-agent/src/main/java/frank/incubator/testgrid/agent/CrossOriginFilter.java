package frank.incubator.testgrid.agent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;

import frank.incubator.testgrid.common.log.LogUtils;

public class CrossOriginFilter implements Filter {
	private static final Logger LOG = LogUtils.getLog("web");

	// Request headers
	private static final String ORIGIN_HEADER = "Origin";
	public static final String ACCESS_CONTROL_REQUEST_METHOD_HEADER = "Access-Control-Request-Method";
	public static final String ACCESS_CONTROL_REQUEST_HEADERS_HEADER = "Access-Control-Request-Headers";
	// Response headers
	public static final String ACCESS_CONTROL_ALLOW_ORIGIN_HEADER = "Access-Control-Allow-Origin";
	public static final String ACCESS_CONTROL_ALLOW_METHODS_HEADER = "Access-Control-Allow-Methods";
	public static final String ACCESS_CONTROL_ALLOW_HEADERS_HEADER = "Access-Control-Allow-Headers";
	public static final String ACCESS_CONTROL_MAX_AGE_HEADER = "Access-Control-Max-Age";
	public static final String ACCESS_CONTROL_ALLOW_CREDENTIALS_HEADER = "Access-Control-Allow-Credentials";
	public static final String ACCESS_CONTROL_EXPOSE_HEADERS_HEADER = "Access-Control-Expose-Headers";
	// Implementation constants
	public static final String ALLOWED_ORIGINS_PARAM = "allowedOrigins";
	public static final String ALLOWED_METHODS_PARAM = "allowedMethods";
	public static final String ALLOWED_HEADERS_PARAM = "allowedHeaders";
	public static final String PREFLIGHT_MAX_AGE_PARAM = "preflightMaxAge";
	public static final String ALLOW_CREDENTIALS_PARAM = "allowCredentials";
	public static final String EXPOSED_HEADERS_PARAM = "exposedHeaders";
	public static final String OLD_CHAIN_PREFLIGHT_PARAM = "forwardPreflight";
	public static final String CHAIN_PREFLIGHT_PARAM = "chainPreflight";
	private static final String ANY_ORIGIN = "*";
	private static final List<String> SIMPLE_HTTP_METHODS = Arrays.asList("GET", "POST", "HEAD", "OPTIONS");

	private boolean anyOriginAllowed;
	private List<String> allowedOrigins = new ArrayList<String>();
	private List<String> allowedMethods = new ArrayList<String>();
	private List<String> allowedHeaders = new ArrayList<String>();
	private List<String> exposedHeaders = new ArrayList<String>();
	private int preflightMaxAge;
	private boolean allowCredentials;
	private boolean chainPreflight;

	public void init(FilterConfig config) throws ServletException {
		String allowedOriginsConfig = config.getInitParameter(ALLOWED_ORIGINS_PARAM);
		if (allowedOriginsConfig == null)
			allowedOriginsConfig = "*";
		String[] allowedOrigins = allowedOriginsConfig.split(",");
		for (String allowedOrigin : allowedOrigins) {
			allowedOrigin = allowedOrigin.trim();
			if (allowedOrigin.length() > 0) {
				if (ANY_ORIGIN.equals(allowedOrigin)) {
					anyOriginAllowed = true;
					this.allowedOrigins.clear();
					break;
				} else {
					this.allowedOrigins.add(allowedOrigin);
				}
			}
		}

		String allowedMethodsConfig = config.getInitParameter(ALLOWED_METHODS_PARAM);
		if (allowedMethodsConfig == null)
			allowedMethodsConfig = "GET,POST,HEAD";
		allowedMethods.addAll(Arrays.asList(allowedMethodsConfig.split(",")));

		String allowedHeadersConfig = config.getInitParameter(ALLOWED_HEADERS_PARAM);
		if (allowedHeadersConfig == null)
			allowedHeadersConfig = "X-Requested-With,Content-Type,Accept,Origin";
		allowedHeaders.addAll(Arrays.asList(allowedHeadersConfig.split(",")));

		String preflightMaxAgeConfig = config.getInitParameter(PREFLIGHT_MAX_AGE_PARAM);
		if (preflightMaxAgeConfig == null)
			preflightMaxAgeConfig = "1800"; // Default is 30 minutes
		try {
			preflightMaxAge = Integer.parseInt(preflightMaxAgeConfig);
		} catch (NumberFormatException x) {
			LOG.info("Cross-origin filter, could not parse '{}' parameter as integer: {}", PREFLIGHT_MAX_AGE_PARAM, preflightMaxAgeConfig);
		}

		String allowedCredentialsConfig = config.getInitParameter(ALLOW_CREDENTIALS_PARAM);
		if (allowedCredentialsConfig == null)
			allowedCredentialsConfig = "true";
		allowCredentials = Boolean.parseBoolean(allowedCredentialsConfig);

		String exposedHeadersConfig = config.getInitParameter(EXPOSED_HEADERS_PARAM);
		if (exposedHeadersConfig == null)
			exposedHeadersConfig = "";
		exposedHeaders.addAll(Arrays.asList(exposedHeadersConfig.split(",")));

		String chainPreflightConfig = config.getInitParameter(OLD_CHAIN_PREFLIGHT_PARAM);
		if (chainPreflightConfig != null) // TODO remove this
			LOG.warn("DEPRECATED CONFIGURATION: Use " + CHAIN_PREFLIGHT_PARAM + " instead of " + OLD_CHAIN_PREFLIGHT_PARAM);
		else
			chainPreflightConfig = config.getInitParameter(CHAIN_PREFLIGHT_PARAM);
		if (chainPreflightConfig == null)
			chainPreflightConfig = "true";
		chainPreflight = Boolean.parseBoolean(chainPreflightConfig);

		if (LOG.isDebugEnabled()) {
			LOG.debug("Cross-origin filter configuration: " + ALLOWED_ORIGINS_PARAM + " = " + allowedOriginsConfig + ", " + ALLOWED_METHODS_PARAM + " = "
					+ allowedMethodsConfig + ", " + ALLOWED_HEADERS_PARAM + " = " + allowedHeadersConfig + ", " + PREFLIGHT_MAX_AGE_PARAM + " = "
					+ preflightMaxAgeConfig + ", " + ALLOW_CREDENTIALS_PARAM + " = " + allowedCredentialsConfig + "," + EXPOSED_HEADERS_PARAM + " = "
					+ exposedHeadersConfig + "," + CHAIN_PREFLIGHT_PARAM + " = " + chainPreflightConfig);
		}
	}

	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		handle((HttpServletRequest) request, (HttpServletResponse) response, chain);
	}

	private void handle(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
		String origin = request.getHeader(ORIGIN_HEADER);
		// Is it a cross origin request ?
		if (origin != null && isEnabled(request)) {
			if (originMatches(origin)) {
				if (isSimpleRequest(request)) {
					LOG.debug("Cross-origin request to {} is a simple cross-origin request", request.getRequestURI());
					handleSimpleResponse(request, response, origin);
				} else if (isPreflightRequest(request)) {
					LOG.debug("Cross-origin request to {} is a preflight cross-origin request", request.getRequestURI());
					handlePreflightResponse(request, response, origin);
					if (chainPreflight)
						LOG.debug("Preflight cross-origin request to {} forwarded to application", request.getRequestURI());
					else
						return;
				} else {
					LOG.debug("Cross-origin request to {} is a non-simple cross-origin request", request.getRequestURI());
					handleSimpleResponse(request, response, origin);
				}
			} else {
				LOG.debug("Cross-origin request to " + request.getRequestURI() + " with origin " + origin + " does not match allowed origins " + allowedOrigins);
			}
		}

		chain.doFilter(request, response);
	}

	@SuppressWarnings("rawtypes")
	protected boolean isEnabled(HttpServletRequest request) {
		// WebSocket clients such as Chrome 5 implement a version of the
		// WebSocket
		// protocol that does not accept extra response headers on the upgrade
		// response
		for (Enumeration connections = request.getHeaders("Connection"); connections.hasMoreElements();) {
			String connection = (String) connections.nextElement();
			if ("Upgrade".equalsIgnoreCase(connection)) {
				for (Enumeration upgrades = request.getHeaders("Upgrade"); upgrades.hasMoreElements();) {
					String upgrade = (String) upgrades.nextElement();
					if ("WebSocket".equalsIgnoreCase(upgrade))
						return false;
				}
			}
		}
		return true;
	}

	private boolean originMatches(String originList) {
		if (anyOriginAllowed)
			return true;

		if (originList.trim().length() == 0)
			return false;

		String[] origins = originList.split(" ");
		for (String origin : origins) {
			if (origin.trim().length() == 0)
				continue;

			for (String allowedOrigin : allowedOrigins) {
				if (allowedOrigin.contains("*")) {
					Matcher matcher = createMatcher(origin, allowedOrigin);
					if (matcher.matches())
						return true;
				} else if (allowedOrigin.equals(origin)) {
					return true;
				}
			}
		}
		return false;
	}

	private Matcher createMatcher(String origin, String allowedOrigin) {
		String regex = parseAllowedWildcardOriginToRegex(allowedOrigin);
		Pattern pattern = Pattern.compile(regex);
		return pattern.matcher(origin);
	}

	private String parseAllowedWildcardOriginToRegex(String allowedOrigin) {
		String regex = allowedOrigin.replace(".", "\\.");
		return regex.replace("*", ".*"); // we want to be greedy here to match
											// multiple subdomains, thus we use
											// .*
	}

	private boolean isSimpleRequest(HttpServletRequest request) {
		String method = request.getMethod();
		if (SIMPLE_HTTP_METHODS.contains(method)) {
			// TODO: implement better detection of simple headers
			// The specification says that for a request to be simple, custom
			// request headers must be simple.
			// Here for simplicity I just check if there is a
			// Access-Control-Request-Method header,
			// which is required for preflight requests
			// return request.getHeader(ACCESS_CONTROL_REQUEST_METHOD_HEADER) ==
			// null;
			return true;
		}
		return false;
	}

	private boolean isPreflightRequest(HttpServletRequest request) {
		String method = request.getMethod();
		if (!"OPTIONS".equalsIgnoreCase(method))
			return false;
		if (request.getHeader(ACCESS_CONTROL_REQUEST_METHOD_HEADER) == null)
			return false;
		return true;
	}

	private void handleSimpleResponse(HttpServletRequest request, HttpServletResponse response, String origin) {
		response.setHeader(ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, origin);
		if (allowCredentials)
			response.setHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS_HEADER, "true");
		if (!exposedHeaders.isEmpty())
			response.setHeader(ACCESS_CONTROL_EXPOSE_HEADERS_HEADER, commify(exposedHeaders));
	}

	private void handlePreflightResponse(HttpServletRequest request, HttpServletResponse response, String origin) {
		boolean methodAllowed = isMethodAllowed(request);
		if (!methodAllowed)
			return;
		boolean headersAllowed = areHeadersAllowed(request);
		if (!headersAllowed)
			return;
		response.setHeader(ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, origin);
		if (allowCredentials)
			response.setHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS_HEADER, "true");
		if (preflightMaxAge > 0)
			response.setHeader(ACCESS_CONTROL_MAX_AGE_HEADER, String.valueOf(preflightMaxAge));
		response.setHeader(ACCESS_CONTROL_ALLOW_METHODS_HEADER, commify(allowedMethods));
		response.setHeader(ACCESS_CONTROL_ALLOW_HEADERS_HEADER, commify(allowedHeaders));
	}

	private boolean isMethodAllowed(HttpServletRequest request) {
		String accessControlRequestMethod = request.getHeader(ACCESS_CONTROL_REQUEST_METHOD_HEADER);
		LOG.debug("{} is {}", ACCESS_CONTROL_REQUEST_METHOD_HEADER, accessControlRequestMethod);
		boolean result = false;
		if (accessControlRequestMethod != null)
			result = allowedMethods.contains(accessControlRequestMethod);
		LOG.debug("Method {} is" + (result ? "" : " not") + " among allowed methods {}", accessControlRequestMethod, allowedMethods);
		return result;
	}

	private boolean areHeadersAllowed(HttpServletRequest request) {
		String accessControlRequestHeaders = request.getHeader(ACCESS_CONTROL_REQUEST_HEADERS_HEADER);
		LOG.debug("{} is {}", ACCESS_CONTROL_REQUEST_HEADERS_HEADER, accessControlRequestHeaders);
		boolean result = true;
		if (accessControlRequestHeaders != null) {
			String[] headers = accessControlRequestHeaders.split(",");
			for (String header : headers) {
				boolean headerAllowed = false;
				for (String allowedHeader : allowedHeaders) {
					if (header.trim().equalsIgnoreCase(allowedHeader.trim())) {
						headerAllowed = true;
						break;
					}
				}
				if (!headerAllowed) {
					result = false;
					break;
				}
			}
		}
		LOG.debug("Headers [{}] are" + (result ? "" : " not") + " among allowed headers {}", accessControlRequestHeaders, allowedHeaders);
		return result;
	}

	private String commify(List<String> strings) {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < strings.size(); ++i) {
			if (i > 0)
				builder.append(",");
			String string = strings.get(i);
			builder.append(string);
		}
		return builder.toString();
	}

	public void destroy() {
		anyOriginAllowed = false;
		allowedOrigins.clear();
		allowedMethods.clear();
		allowedHeaders.clear();
		preflightMaxAge = 0;
		allowCredentials = false;
	}
}