package chat.rocket.android.notification;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;
import bolts.Task;
import chat.rocket.android.R;
import chat.rocket.android.activity.MainActivity;
import chat.rocket.android.helper.Avatar;
import chat.rocket.android.model.ServerConfig;
import chat.rocket.android.realm_helper.RealmStore;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * utility class for notification.
 */
public class StreamNotifyUserNotifier implements Notifier {
  private final Context context;
  private final String hostname;
  private final String title;
  private final String text;
  private final JSONObject payload;

  public StreamNotifyUserNotifier(Context context, String hostname,
      String title, String text, JSONObject payload) {
    this.context = context;
    this.hostname = hostname;
    this.title = title;
    this.text = text;
    this.payload = payload;
  }

  @Override public void publishNotificationIfNeeded() {
    if (!shouldNotify()) {
      return;
    }

    generateNotificationAsync().onSuccess(task -> {
      NotificationManagerCompat.from(context)
          .notify(generateNotificationId(), task.getResult());
      return null;
    });
  }

  private boolean shouldNotify() {
    // TODO: should check if target message is already read or not.
    return true;
  }

  private int generateNotificationId() {
    // TODO: should summary notification by user or room.
    return (int) (System.currentTimeMillis() % Integer.MAX_VALUE);
  }

  private Task<Notification> generateNotificationAsync() {
    int size = context.getResources().getDimensionPixelSize(R.dimen.notification_avatar_size);
    return getUsername()
        .onSuccessTask(task -> new Avatar(hostname, task.getResult()).getBitmap(context, size))
        .continueWithTask(task -> {
          Bitmap icon = task.isFaulted() ? null : task.getResult();
          return Task.forResult(generateNotification(icon));
        });
  }

  private Task<String> getUsername() {
    try {
      return Task.forResult(payload.getJSONObject("sender").getString("username"));
    } catch (Exception exception) {
      return Task.forError(exception);
    }
  }

  private Notification generateNotification(Bitmap largeIcon) {
    Intent intent = new Intent(context, MainActivity.class);
    intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    ServerConfig config = RealmStore.getDefault().executeTransactionForRead(realm ->
      realm.where(ServerConfig.class).equalTo("hostname", hostname).findFirst());
    if (config != null) {
      intent.putExtra("serverConfigId", config.getServerConfigId());
      try {
        intent.putExtra("roomId", payload.getString("rid"));
      } catch (JSONException exception) {
      }
    }
    PendingIntent pendingIntent = PendingIntent.getActivity(context.getApplicationContext(),
        (int) (System.currentTimeMillis() % Integer.MAX_VALUE),
        intent, PendingIntent.FLAG_ONE_SHOT);

    NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
        .setContentTitle(title)
        .setContentText(text)
        .setAutoCancel(true)
        .setColor(ContextCompat.getColor(context, R.color.colorPrimary))
        .setSmallIcon(R.drawable.rocket_chat_notification_24dp)
        .setContentIntent(pendingIntent);
    if (largeIcon != null) {
      builder.setLargeIcon(largeIcon);
    }
    if (text.length() > 20) {
      return new NotificationCompat.BigTextStyle(builder)
          .bigText(text)
          .build();
    } else {
      return builder.build();
    }
  }
}
