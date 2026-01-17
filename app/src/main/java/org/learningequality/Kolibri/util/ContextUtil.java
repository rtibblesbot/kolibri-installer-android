package org.learningequality.Kolibri.util;

import android.content.Context;

/**
 * Utility to provide application context to both Java and Python code.
 *
 * <p>Must be initialized early in App.onCreate() before any other components try to access the
 * context.
 */
public class ContextUtil {
  private static Context applicationContext;

  /** Initialize with application context. Must be called from App.onCreate(). */
  public static void init(Context context) {
    applicationContext = context.getApplicationContext();
  }

  /**
   * Get the application context.
   *
   * @return Application context
   * @throws IllegalStateException if init() was not called
   */
  public static Context getApplicationContext() {
    if (applicationContext == null) {
      throw new IllegalStateException(
          "ContextUtil not initialized. Call ContextUtil.init() from App.onCreate() first.");
    }
    return applicationContext;
  }

  /**
   * Get the app's external files directory path. Called from Python via Chaquopy.
   *
   * @return Path to external files directory
   */
  public static String getExternalFilesDir() {
    return getApplicationContext().getExternalFilesDir(null).getAbsolutePath();
  }
}
