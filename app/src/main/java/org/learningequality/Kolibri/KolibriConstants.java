package org.learningequality.Kolibri;

/** Application-wide constants for Kolibri Android */
public class KolibriConstants {
  // Kolibri URLs
  public static final String KOLIBRI_SCHEME = "http";
  public static final String KOLIBRI_HOST = "kolibri.app";
  public static final String KOLIBRI_BASE_URL = KOLIBRI_SCHEME + "://" + KOLIBRI_HOST + "/";

  public static final String ZIPCONTENT_HOST = "zipcontent.app";
  public static final String ZIPCONTENT_BASE_URL = KOLIBRI_SCHEME + "://" + ZIPCONTENT_HOST + "/";

  // Localhost URLs (for HTTP server)
  public static final String LOCALHOST_HOST = "127.0.0.1";
  public static final int DEFAULT_KOLIBRI_PORT = 5000;

  public static String getLocalUrl(int port) {
    return KOLIBRI_SCHEME + "://" + LOCALHOST_HOST + ":" + port + "/";
  }

  // Session cookie
  public static final String KOLIBRI_SESSION_COOKIE = "kolibri";

  // Prevent instantiation
  private KolibriConstants() {
    throw new AssertionError("No instances");
  }
}
