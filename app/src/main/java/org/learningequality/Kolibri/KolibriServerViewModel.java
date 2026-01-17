package org.learningequality.Kolibri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * Application-level state holder for Kolibri server status.
 *
 * <p>This is a singleton because Python (via Chaquopy) needs to call setServerReady() from the
 * server thread, and Activities need to observe the state via LiveData.
 *
 * <p>Note: This intentionally does NOT extend ViewModel because: 1. It's accessed from Python code
 * which can't use ViewModelProvider 2. The state needs to persist across Activity recreation 3.
 * It's application-scoped, not Activity-scoped
 *
 * <p>LiveData is still used for lifecycle-aware observation in Activities.
 */
public class KolibriServerViewModel {
  private static volatile KolibriServerViewModel instance;
  private final MutableLiveData<ServerInfo> serverReady =
      new MutableLiveData<>(new ServerInfo(false, 0, ""));

  // Private constructor for singleton
  private KolibriServerViewModel() {}

  /** Get the singleton instance. Thread-safe double-checked locking. */
  public static KolibriServerViewModel getInstance() {
    if (instance == null) {
      synchronized (KolibriServerViewModel.class) {
        if (instance == null) {
          instance = new KolibriServerViewModel();
        }
      }
    }
    return instance;
  }

  /**
   * Set server ready state. Called from Python when server starts. Uses postValue() for
   * thread-safety (can be called from any thread).
   */
  public void setServerReady(boolean ready, int port, String initUrl) {
    serverReady.postValue(new ServerInfo(ready, port, initUrl));
  }

  /**
   * Get LiveData for observing server state. Activities should observe this with their lifecycle.
   */
  public LiveData<ServerInfo> getServerReadyLiveData() {
    return serverReady;
  }

  /** Reset server state (e.g., when server stops). */
  public void resetServerState() {
    serverReady.postValue(new ServerInfo(false, 0, ""));
  }

  /** Immutable server info state. */
  public static class ServerInfo {
    private final boolean ready;
    private final int port;
    private final String initializationUrl;

    public ServerInfo(boolean ready, int port, String initializationUrl) {
      this.ready = ready;
      this.port = port;
      this.initializationUrl = initializationUrl;
    }

    public boolean isReady() {
      return ready;
    }

    public int getPort() {
      return port;
    }

    public String getInitializationUrl() {
      return initializationUrl;
    }
  }
}
