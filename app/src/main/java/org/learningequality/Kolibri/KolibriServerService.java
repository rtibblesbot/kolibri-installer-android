package org.learningequality.Kolibri;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;

/**
 * Background service that starts the Kolibri HTTP server
 *
 * <p>Server runs in background thread and signals readiness via ViewModel. Handles both local
 * WebView and remote peer connections.
 */
public class KolibriServerService extends Service {
  private static final String TAG = "KolibriServerService";

  private Thread serverThread;
  private volatile boolean isRunning = false;

  @Override
  public void onCreate() {
    super.onCreate();
    Log.d(TAG, "KolibriServerService onCreate");

    // Initialize Kolibri environment
    KolibriEnvironmentSetup.initializeEnv(this);

    // Start HTTP server in background thread
    startHttpServer();
  }

  private synchronized void startHttpServer() {
    if (isRunning || (serverThread != null && serverThread.isAlive())) {
      Log.w(TAG, "Server already running");
      return;
    }

    serverThread =
        new Thread(
            () -> {
              try {
                Log.i(TAG, "Starting Kolibri HTTP server");
                isRunning = true;

                Python py = Python.getInstance();
                PyObject mainModule = py.getModule("main");

                // This blocks until server stops
                mainModule.callAttr("start_server");

                Log.i(TAG, "Kolibri HTTP server stopped");
              } catch (Exception e) {
                Log.e(TAG, "Error running Kolibri HTTP server", e);
              } finally {
                isRunning = false;
              }
            },
            "KolibriServerThread");

    serverThread.start();
    Log.d(TAG, "HTTP server thread started");
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    Log.d(TAG, "KolibriServerService onDestroy");

    isRunning = false;

    // Call Python to stop the server gracefully
    try {
      Python py = Python.getInstance();
      PyObject mainModule = py.getModule("main");
      mainModule.callAttr("stop_server");
      Log.d(TAG, "Called Python stop_server");
    } catch (Exception e) {
      Log.w(TAG, "Error calling stop_server (may already be stopped)", e);
    }

    if (serverThread != null && serverThread.isAlive()) {
      try {
        serverThread.join(5000);
        if (serverThread.isAlive()) {
          Log.w(TAG, "Server thread did not stop in time, interrupting");
          serverThread.interrupt();
        }
      } catch (InterruptedException e) {
        Log.w(TAG, "Interrupted waiting for server thread");
        Thread.currentThread().interrupt();
      }
    }

    serverThread = null;
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    // This is a started service, not a bound service
    return null;
  }

  public boolean isRunning() {
    return isRunning;
  }
}
