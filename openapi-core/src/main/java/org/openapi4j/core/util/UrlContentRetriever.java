package org.openapi4j.core.util;

import org.openapi4j.core.exception.ResolutionException;
import org.openapi4j.core.model.AuthOption;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.openapi4j.core.model.AuthOption.Type.HEADER;
import static org.openapi4j.core.model.AuthOption.Type.QUERY;

/**
 * Utility class to assign authentication values to a request.
 * This class works for any handlers File, HTTP, ...
 */
public final class UrlContentRetriever {
  private static final String ACCEPT_HEADER_VALUE = "application/json, application/yaml, */*";
  private static final int MAX_REDIRECTS = 5;

  private static final UrlContentRetriever INSTANCE = new UrlContentRetriever();

  private UrlContentRetriever() {}

  public static UrlContentRetriever instance() {
    return INSTANCE;
  }

  /**
   * Get the content of the targeted URL with the given authentication values if any.
   * For HTTP requests, HTTP method GET is used
   * Also, this method allows following up to 5 redirects.
   *
   * For other handlers, these options are ignored.
   *
   * @param url         The url to request from.
   * @param authOptions The authentication values.
   * @return The content of the resource.
   * @throws Exception
   */
  public InputStream get(final URL url, final List<AuthOption> authOptions) throws ResolutionException {
    URLConnection conn;
    URL inUrl = url;
    int nbRedirects = 0;
    boolean loop;

    try {
      do {
        loop = false;

        // fill auth options for the current URL
        List<AuthOption> queryParams = new ArrayList<>();
        List<AuthOption> headerParams = new ArrayList<>();
        fillAuthOptions(authOptions, inUrl, queryParams, headerParams);

        // Setup query string auth option if any and make a new URL
        if (!queryParams.isEmpty()) {
          inUrl = setupQueryInUrl(inUrl, queryParams);
        }

        // Open connection
        conn = inUrl.openConnection();

        // Setup header auth options if any
        for (AuthOption item : headerParams) {
          conn.setRequestProperty(item.getKey(), item.getValue());
        }

        conn.setRequestProperty("Accept", ACCEPT_HEADER_VALUE);
        conn.connect();

        // Handle redirection for HTTP connection
        if (conn instanceof HttpURLConnection) {
          int statusCode = ((HttpURLConnection) conn).getResponseCode();
          String newLocation = conn.getHeaderField("Location");
          if ((statusCode == 301 || statusCode == 302) || newLocation != null) {
            loop = MAX_REDIRECTS > ++nbRedirects;
            if (!loop) {
              throw new ResolutionException("Too many redirects.");
            }
            inUrl = new URL(cleanUrl(newLocation));
          }
        }
      } while (loop);

      return conn.getInputStream();

    } catch(Exception ex) {
      throw new ResolutionException(ex);
    }
  }

  private void fillAuthOptions(List<AuthOption> authOptions,
                               URL inUrl,
                               List<AuthOption> queryParams,
                               List<AuthOption> headerParams) {

    if (authOptions != null) {
      for (AuthOption auth : authOptions) {
        if (auth.getUrlMatcher().test(inUrl)) {
          if (QUERY.equals(auth.getType())) {
            queryParams.add(auth);
          } else if (HEADER.equals(auth.getType())) {
            headerParams.add(auth);
          }
        }
      }
    }
  }

  private URL setupQueryInUrl(URL inUrl, List<AuthOption> queryParams) throws URISyntaxException, UnsupportedEncodingException, MalformedURLException {
    URI inUri = inUrl.toURI();
    StringBuilder newQuery = new StringBuilder(inUri.getQuery() == null ? "" : inUri.getQuery());
    for (AuthOption param : queryParams) {
      if (newQuery.length() > 0) newQuery.append("&");

      newQuery
        .append(URLEncoder.encode(param.getKey(), UTF_8.name()))
        .append("=")
        .append(URLEncoder.encode(param.getValue(), UTF_8.name()));
    }

    return new URI(
      inUri.getScheme(), inUri.getAuthority(), inUri.getPath(),
      newQuery.toString(), inUri.getFragment()).toURL();
  }


  private String cleanUrl(String url) {
    return url
      .replace("{", "%7B")
      .replace("}", "%7D")
      .replace(" ", "%20");
  }
}
