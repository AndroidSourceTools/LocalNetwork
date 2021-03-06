package dev.jokr.localnet;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.NotificationCompat;
import android.text.format.Formatter;

import dev.jokr.localnet.discovery.models.DiscoveryReply;
import dev.jokr.localnet.models.IncomingServerMessage;
import dev.jokr.localnet.models.Payload;
import dev.jokr.localnet.models.RegisterMessage;
import dev.jokr.localnet.models.SessionMessage;
import dev.jokr.localnet.utils.MessageType;

/**
 * Created by JoKr on 8/29/2016.
 */
public class ClientService extends Service implements ClientSocketThread.ServiceCallback {

    public static final String ACTION = "action";
    public static final String DISCOVERY_REPLY = "reply";
    public static final int NOTIFICATION_ID = 521;

    // Keys for extras
    public static final String PAYLOAD = "payload";

    // Possible service actions:
    public static final int DISCOVERY_REQUEST = 1;
    public static final int SESSION_MESSAGE = 2;

    private Payload<?> registerPayload;
    private ClientSocketThread clientSocketThread;
    private SendHandler sendHandler;

    private DiscoveryReply reply;
    private LocalBroadcastManager manager;

    @Override
    public void onCreate() {
        super.onCreate();

        this.manager = LocalBroadcastManager.getInstance(this);
        Thread t = new Thread(new ClientSocketThread(this));
        t.start();

        runServiceInForeground();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        DiscoveryReply reply = (DiscoveryReply) intent.getSerializableExtra(DISCOVERY_REPLY);
        if (reply != null) {
            this.reply = reply;
        }

        int action = intent.getIntExtra(ACTION, 0);
        processAction(action, intent);

        return START_STICKY;
    }

    private void processAction(int action, Intent intent) {
        if (action == 0)
            return;
        if (action == DISCOVERY_REQUEST)
            registerPayload = (Payload<?>) intent.getSerializableExtra(PAYLOAD);
        else if (action == SESSION_MESSAGE)
            sendSessionMessage((Payload<?>) intent.getSerializableExtra(PAYLOAD));
    }

    @Override
    public void onInitializedSocket(int port) {
        RegisterMessage message = new RegisterMessage(registerPayload, getLocalIp(), port);
        Thread t = new Thread(new SendHandler(new IncomingServerMessage(MessageType.REGISTER, message), reply.getIp(), reply.getPort()));
        t.start();
    }

    @Override
    public void onSessionMessage(SessionMessage message) {
        Intent i = new Intent(LocalClient.SESSION_MESSAGE);
        i.putExtra(SessionMessage.class.getName(), message);
        manager.sendBroadcast(i);
    }

    private void runServiceInForeground() {
        Notification notification = new NotificationCompat.Builder(this)
                .setContentTitle("LocalNet Session")
                .setContentText("Session is currently running")
                .setSmallIcon(R.drawable.ic_play_circle_filled_black_24dp)
                .build();
        startForeground(NOTIFICATION_ID, notification);
    }

    private void sendSessionMessage(Payload<?> payload){
        SessionMessage message = new SessionMessage(payload);
        Thread t = new Thread(new SendHandler(new IncomingServerMessage(MessageType.SESSION, message), reply.getIp(), reply.getPort()));
        t.start();
    }

    private String getLocalIp() {
        WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
        return Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
    }
}
