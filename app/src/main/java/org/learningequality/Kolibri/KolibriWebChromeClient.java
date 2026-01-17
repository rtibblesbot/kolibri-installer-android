package org.learningequality.Kolibri;

import android.app.Activity;
import android.view.View;
import android.webkit.WebChromeClient;
import android.widget.FrameLayout;

/** Custom WebChromeClient that handles fullscreen video playback */
public class KolibriWebChromeClient extends WebChromeClient {
  private static final String TAG = "Kolibri.WebChromeClient";

  private final Activity activity;
  private final FrameLayout fullscreenContainer;
  private View customView;
  private CustomViewCallback customViewCallback;

  public KolibriWebChromeClient(Activity activity, FrameLayout fullscreenContainer) {
    this.activity = activity;
    this.fullscreenContainer = fullscreenContainer;
  }

  @Override
  public void onShowCustomView(View view, CustomViewCallback callback) {
    // If a view already exists, hide it
    if (customView != null) {
      onHideCustomView();
      return;
    }

    // Store the custom view and callback
    customView = view;
    customViewCallback = callback;

    // Hide system UI for immersive fullscreen
    activity
        .getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

    // Add the custom view to the fullscreen container
    fullscreenContainer.addView(customView);
    fullscreenContainer.setVisibility(View.VISIBLE);
  }

  @Override
  public void onHideCustomView() {
    // Remove the custom view
    if (customView != null) {
      fullscreenContainer.removeView(customView);
      customView = null;
    }

    // Hide the fullscreen container
    fullscreenContainer.setVisibility(View.GONE);

    // Restore system UI
    activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);

    // Notify the callback
    if (customViewCallback != null) {
      customViewCallback.onCustomViewHidden();
      customViewCallback = null;
    }
  }
}
