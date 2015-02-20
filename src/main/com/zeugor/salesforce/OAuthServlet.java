package com.zeugor.salesforce;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

public class OAuthServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

	private static final String ACCESS_TOKEN = "ACCESS_TOKEN";
	private static final String INSTANCE_URL = "INSTANCE_URL";

	private String clientId;
	private String clientSecret;
	private String redirectUri;
	private String environment;
	private String authUrl;
	private String tokenUrl;

	@Override
	public void init() throws ServletException {

		InputStream input = null;
		try {
			Properties prop = new Properties();
			prop.load(getServletContext().getResourceAsStream("/conf/config.properties"));

			clientId = prop.getProperty("clientId");
			System.out.println("clientId: " + clientId);
			clientSecret = prop.getProperty("clientSecret");
			redirectUri = prop.getProperty("redirectUri");
			environment = prop.getProperty("environment");
		} catch (IOException ex) {
			ex.printStackTrace();
		} catch (Exception e) {
			// show log4j message telling you dont have the config.properties file
			System.out.println(">> /WEB-INF/conf.properties no found.");
			e.printStackTrace();
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		try {
			authUrl = environment
					+ "/services/oauth2/authorize?response_type=code&client_id="
					+ clientId + "&redirect_uri="
					+ URLEncoder.encode(redirectUri, "UTF-8");
			System.out.println("authUrl: " + authUrl);
		} catch (UnsupportedEncodingException e) {
			throw new ServletException(e);
		}

		tokenUrl = environment + "/services/oauth2/token";
	}

	@Override
	protected void doGet(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		String accessToken = (String) request.getSession().getAttribute(
				ACCESS_TOKEN);

		if (accessToken == null) {
			String instanceUrl = null;

			if (request.getRequestURI().endsWith("oauth")) {
				// we need to send the user to authorize
				response.sendRedirect(authUrl);
				return;
			} else {
				System.out.println("Auth successful - got callback");

				String code = request.getParameter("code");

				HttpClient httpclient = new HttpClient();

				PostMethod post = new PostMethod(tokenUrl);
				post.addParameter("code", code);
				post.addParameter("grant_type", "authorization_code");
				post.addParameter("client_id", clientId);
				post.addParameter("client_secret", clientSecret);
				post.addParameter("redirect_uri", redirectUri);

				try {
					httpclient.executeMethod(post);

					try {
						JSONObject authResponse = new JSONObject(
								new JSONTokener(new InputStreamReader(
										post.getResponseBodyAsStream())));
						System.out.println("Auth response: "
								+ authResponse.toString(2));

						accessToken = authResponse.getString("access_token");
						instanceUrl = authResponse.getString("instance_url");

						System.out.println("Got access token: " + accessToken);
					} catch (JSONException e) {
						e.printStackTrace();
						throw new ServletException(e);
					}
				} finally {
					post.releaseConnection();
				}
			}

			// Set a session attribute so that other servlets can get the access
			// token
			request.getSession().setAttribute(ACCESS_TOKEN, accessToken);

			// We also get the instance URL from the OAuth response, so set it
			// in the session too
			request.getSession().setAttribute(INSTANCE_URL, instanceUrl);
		}

		response.sendRedirect(request.getContextPath() + "/DemoREST");
	}
}
