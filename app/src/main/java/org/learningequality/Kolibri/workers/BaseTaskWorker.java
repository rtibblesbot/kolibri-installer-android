package org.learningequality.Kolibri.workers;

import android.app.Notification;
import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import java.util.zip.CRC32;
import org.learningequality.Kolibri.KolibriEnvironmentSetup;
import org.learningequality.Kolibri.notification.NotificationRef;
import org.learningequality.Kolibri.notification.Notifier;
import org.learningequality.Kolibri.task.Observer;
import org.learningequality.Kolibri.task.TaskWorkerImpl;

/**
 * Base class for Kolibri task workers
 *
 * <p>Provides common functionality for executing Python tasks via Chaquopy. Sets up TaskWorkerImpl
 * with observer pattern for progress notifications. Subclasses implement getWorkerType() to
 * differentiate behavior.
 */
public abstract class BaseTaskWorker extends Worker implements Notifier {
  private static final String TAG = "BaseTaskWorker";
  private int lastProgressUpdateHash;
  private Notification lastNotification;

  public BaseTaskWorker(@NonNull Context context, @NonNull WorkerParameters params) {
    super(context, params);
  }

  /**
   * Get the worker type for Python
   *
   * @return "foreground" or "background"
   */
  protected abstract String getWorkerType();

  @NonNull
  @Override
  public Result doWork() {
    String jobId = null;
    TaskWorkerImpl workerImpl = null;

    try {
      Log.d(TAG, "Starting " + getWorkerType() + " task execution");

      // Initialize Python and Kolibri environment in task_worker process
      KolibriEnvironmentSetup.initializeEnv(getApplicationContext());

      // Get job ID from input data
      jobId = getInputData().getString("job_id");
      if (jobId == null || jobId.isEmpty()) {
        Log.e(TAG, "No job_id provided");
        return Result.failure();
      }

      Log.i(TAG, "Executing job: " + jobId + " (type: " + getWorkerType() + ")");

      // Create TaskWorkerImpl - this sets up the ThreadLocal so Python can notify us
      workerImpl = new TaskWorkerImpl(getId(), getApplicationContext());
      workerImpl.addObserver(
          new Observer<TaskWorkerImpl.Message>() {
            @Override
            public void update(TaskWorkerImpl.Message message) {
              onProgressUpdate(message);
            }
          });

      // Execute the task via Python
      Python py = Python.getInstance();
      PyObject taskWorker = py.getModule("taskworker");
      PyObject executeJob = taskWorker.get("execute_job");

      // Call Python: execute_job(job_id)
      // Note: foreground/background distinction is handled on Java side via worker class
      PyObject result = executeJob.call(jobId);

      boolean success = result.toBoolean();
      Log.i(TAG, "Task " + jobId + " completed: " + (success ? "SUCCESS" : "FAILURE"));

      return success ? Result.success() : Result.failure();

    } catch (Exception e) {
      Log.e(TAG, "Error executing " + getWorkerType() + " task", e);
      return Result.failure();
    } finally {
      // Clean up TaskWorkerImpl
      if (workerImpl != null) {
        workerImpl.close();
      }
      // Hide notification when task completes
      hideNotification();
    }
  }

  /** Handle progress update from Python via TaskWorkerImpl observer */
  protected void onProgressUpdate(TaskWorkerImpl.Message message) {
    Log.d(
        TAG,
        "onProgressUpdate called: title="
            + message.notificationTitle
            + ", text="
            + message.notificationText);
    Data updateData = message.toData();
    // Only update progress if it has changed
    if (updateData.hashCode() == lastProgressUpdateHash) {
      Log.d(TAG, "Progress unchanged, skipping notification update");
      return;
    }
    lastProgressUpdateHash = updateData.hashCode();
    // Log and track progress
    setProgressAsync(updateData);
    try {
      lastNotification =
          sendNotification(
              message.notificationTitle,
              message.notificationText,
              message.progress,
              message.totalProgress);
      Log.d(TAG, "Notification sent: " + (lastNotification != null ? "success" : "null returned"));
    } catch (Exception e) {
      Log.e(TAG, "Failed to update task progress for: " + getId(), e);
    }
  }

  /** Get the last notification sent (for foreground service) */
  protected Notification getLastNotification() {
    return lastNotification;
  }

  @Override
  public NotificationRef getNotificationRef() {
    // Use CRC32 to generate a unique notification ID from the work request ID
    CRC32 crc = new CRC32();
    crc.update(getId().toString().getBytes());
    int notificationId = (int) crc.getValue();
    return new NotificationRef(NotificationRef.REF_CHANNEL_DEFAULT, notificationId);
  }
}
