/*************************************************************************
 *
 * ATOS CONFIDENTIAL
 * __________________
 *
 *  Copyright (2020) Atos Spain SA
 *  All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of Atos Spain SA and other companies of the Atos group.
 * The intellectual and technical concepts contained
 * herein are proprietary to Atos Spain SA
 * and other companies of the Atos group and may be covered by Spanish regulations
 * and are protected by copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Atos Spain SA.
 */
package eu.h2020.helios_social.modules.videocall.connection;

import android.annotation.SuppressLint;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.socket.client.IO;
import io.socket.client.Socket;
import okhttp3.OkHttpClient;


public class SignallingClient {
    private static SignallingClient instance;
    private Socket socket;
    private SignalingInterface callback;

    // This piece of code should not go into production!!
    // This will help in cases where the node server is running in non-https
    // server and you want to ignore the warnings
    @SuppressLint("TrustAllX509TrustManager")
    private final TrustManager[] trustAllCerts = new TrustManager[]{
        new X509TrustManager() {
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return new java.security.cert.X509Certificate[]{};
            }

            public void checkClientTrusted(X509Certificate[] chain,
                                        String authType) {}

            public void checkServerTrusted(X509Certificate[] chain,
                                        String authType) {}
        }
    };

    public static SignallingClient getInstance() {
        if (instance == null) {
            instance = new SignallingClient();
        }

        return instance;
    }

    public void init(SignalingInterface signalingInterface, String apiEndpoint,
        String room_name
    ) {
        Log.d("SignallingClient.init", apiEndpoint + "/" + room_name);

        this.callback = signalingInterface;

        // Based on code from https://stackoverflow.com/a/57313375/586382
        // HACK we should be accepting all certificates, provide a valid one in
        //      the signaling server instead
        HostnameVerifier myHostnameVerifier = new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        };
        TrustManager[] trustAllCerts= new TrustManager[] {
            new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                public void checkServerTrusted(X509Certificate[] chain, String authType) {}

                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }
        };

        SSLContext mySSLContext = null;
        try {
            mySSLContext = SSLContext.getInstance("TLS");
            mySSLContext.init(null, trustAllCerts, null);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .hostnameVerifier(myHostnameVerifier)
            .sslSocketFactory(mySSLContext.getSocketFactory())
        .build();

        // default settings for all sockets
        IO.setDefaultOkHttpWebSocketFactory(okHttpClient);
        IO.setDefaultOkHttpCallFactory(okHttpClient);

        // set as an option
        IO.Options opts = new IO.Options();
        opts.callFactory = okHttpClient;
        opts.webSocketFactory = okHttpClient;

        try {
            // set the socket.io url here
            socket = IO.socket(apiEndpoint + "/" + room_name, opts);
            socket.connect();
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }

        //room created event.
        socket.on("created", args -> {
            Log.d("SignallingClient.created", Arrays.toString(args));

            callback.onCreatedRoom();
        });

        socket.on("disconnect", args -> {
            Log.d("SignallingClient.disconnect", Arrays.toString(args));

            callback.onDisconnect(args[0].toString());
        });

        //room is full event
        socket.on("full", args -> {
            Log.d("SignallingClient.full", Arrays.toString(args));
        });

        //peer joined event
        socket.on("join", args -> {
            Log.d("SignallingClient.join", Arrays.toString(args));

            callback.onNewPeerJoined(args[0].toString());
        });

        //when you joined a chat room successfully
        socket.on("joined", args -> {
            Log.d("SignallingClient.joined", Arrays.toString(args));

            callback.onJoinedRoom();
        });

        //log event
        socket.on("log", args -> {
            Log.d("SignallingClient.log", Arrays.toString(args));
        });

        //messages - SDP and ICE candidates are transferred through this
        socket.on("message", args -> {
            Log.d("SignallingClient.message", Arrays.toString(args));

            String id = args[1].toString();

            if (args[0] instanceof String) {
                String data = (String) args[0];

                Log.d("SignallingClient.String received:", data);

                if (data.equalsIgnoreCase("bye")) {
                    Log.d("SignallingClient", "Bye received");

                    callback.onRemoteHangUp(id);
                }
            } else if (args[0] instanceof JSONObject) {
                JSONObject data;
                String type;

                try {
                    data = (JSONObject) args[0];

                    Log.d("SignallingClient.Json Received:", data.toString());

                    type = data.getString("type");
                } catch (JSONException e) {
                    e.printStackTrace();
                    return;
                }

                if (type.equalsIgnoreCase("offer")) {
                    callback.onOfferReceived(data, id);
                    return;
                }

                if (type.equalsIgnoreCase("answer")) {
                    callback.onAnswerReceived(data, id);
                    return;
                }

                if (type.equalsIgnoreCase("candidate")) {
                    callback.onIceCandidateReceived(data, id);
                    return;
                }
            }
        });
    }

    public void emitMessage(String message, String id) {
        Log.d("SignallingClient.emitMessage", message);

        socket.emit("message", message, id);
    }

    public void emitMessage(SessionDescription message, String id) {
        JSONObject obj = new JSONObject();

        try {
            obj.put("type", message.type.canonicalForm());
            obj.put("sdp", message.description);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        Log.d("emitMessage", obj.toString());
        socket.emit("message", obj, id);
    }

    public void emitIceCandidate(IceCandidate iceCandidate, String id) {
        JSONObject object = new JSONObject();

        try {
            object.put("type", "candidate");
            object.put("label", iceCandidate.sdpMLineIndex);
            object.put("id", iceCandidate.sdpMid);
            object.put("candidate", iceCandidate.sdp);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        socket.emit("message", object, id);
    }

    public void close() {
        Log.d("SignallingClient", "close");

        socket.disconnect();
        socket.close();

        instance = null;
    }


    public interface SignalingInterface {
        void onAnswerReceived(JSONObject data, String id);
        void onCreatedRoom();
        void onDisconnect(String reason);
        void onIceCandidateReceived(JSONObject data, String id);
        void onJoinedRoom();
        void onNewPeerJoined(String id);
        void onOfferReceived(JSONObject data, String id);
        void onRemoteHangUp(String id);
    }
}
