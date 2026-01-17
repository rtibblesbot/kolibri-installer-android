package org.learningequality.Kolibri.notification;

import android.Manifest;
import android.app.Notification;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.ForegroundInfo;
import org.learningequality.Kolibri.util.ContextUtil;

public class Manager {
  private final Context context;
  private final NotificationRef ref;

  public Manager(Context context, NotificationRef ref) {
    this.context = context;
    this.ref = ref;
  }

  public void send() {
    send(null, null, -1, -1);
  }

  public Notification prepare(
      String notificationTitle,
      String notificationText,
      int notificationProgress,
      int notificationTotal) {
    if (ref == null) {
      return null;
    }
    Builder builder = new Builder(context, ref);
    if (notificationTitle != null) {
      builder.setContentTitle(notificationTitle);
    }
    if (notificationText != null) {
      builder.setContentText(notificationText);
    }
    if (notificationProgress != -1 && notificationTotal != -1) {
      builder.setProgress(notificationTotal, notificationProgress, false);
    }
    return builder.build();
  }

  public Notification send(
      String notificationTitle,
      String notificationText,
      int notificationProgress,
      int notificationTotal) {
    if (ref == null) {
      Log.w("Notification.Manager", "NotificationRef is null, cannot send notification");
      return null;
    }
    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED) {
      Log.w(
          "Notification.Manager",
          "POST_NOTIFICATIONS permission not granted, skipping notification");
      return null;
    }
    Log.d("Notification.Manager", "Sending notification: " + notificationTitle);
    Notification notification =
        prepare(notificationTitle, notificationText, notificationProgress, notificationTotal);
    NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
    notificationManager.notify(ref.getTag(), ref.getId(), notification);
    return notification;
  }

  public void hide() {
    if (ref == null) {
      return;
    }
    NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
    notificationManager.cancel(ref.getTag(), ref.getId());
  }

  /** Static methods callable from Python via Chaquopy */

  /**
   * Send or update notification Called from Python via android_notifications.py
   *
   * @param taskId Task identifier (used as notification tag)
   * @param title Notification title
   * @param text Notification text
   * @param progress Current progress value
   * @param total Total progress value (0 = no progress bar, -1 = indeterminate)
   */
  public static void send(String taskId, String title, String text, int progress, int total) {
    try {
      Context context = ContextUtil.getApplicationContext();
      if (context == null) {
        Log.e("Notification.Manager", "Context is null, cannot send notification");
        return;
      }

      // Create NotificationRef using task ID as tag
      NotificationRef ref = new NotificationRef(NotificationRef.REF_CHANNEL_DEFAULT, taskId);

      // Create manager instance and send notification
      Manager manager = new Manager(context, ref);
      manager.send(title, text, progress, total);

    } catch (Exception e) {
      Log.e("Notification.Manager", "Error sending notification", e);
    }
  }

  /**
   * Cancel notification Called from Python via android_notifications.py
   *
   * @param taskId Task identifier
   */
  public static void cancel(String taskId) {
    try {
      Context context = ContextUtil.getApplicationContext();
      if (context == null) {
        Log.e("Notification.Manager", "Context is null, cannot cancel notification");
        return;
      }

      // Create NotificationRef using task ID as tag
      NotificationRef ref = new NotificationRef(NotificationRef.REF_CHANNEL_DEFAULT, taskId);

      // Create manager instance and hide notification
      Manager manager = new Manager(context, ref);
      manager.hide();

    } catch (Exception e) {
      Log.e("Notification.Manager", "Error canceling notification", e);
    }
  }

  /**
   * Create ForegroundInfo for WorkManager foreground service
   *
   * @param context Application context
   * @param jobId Job identifier for notification
   * @param foregroundServiceType Service type flags for API 29+
   * @return ForegroundInfo for the foreground worker
   */
  public static ForegroundInfo createForegroundInfo(
      Context context, String jobId, int foregroundServiceType) {
    // Create NotificationRef using job ID
    NotificationRef ref =
        new NotificationRef(NotificationRef.REF_CHANNEL_DEFAULT, jobId != null ? jobId : "task");

    // Create notification
    Builder builder = new Builder(context, ref);
    builder.setContentTitle("Kolibri Task Running");
    builder.setContentText("Processing task in background...");
    builder.setOngoing(true);

    Notification notification = builder.build();

    // Return ForegroundInfo with or without service type
    if (foregroundServiceType != 0) {
      return new ForegroundInfo(ref.getId(), notification, foregroundServiceType);
    } else {
      return new ForegroundInfo(ref.getId(), notification);
    }
  }
}
