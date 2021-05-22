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
package eu.h2020.helios_social.modules.videocall;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import eu.h2020.helios_social.modules.videocall.connection.CustomPeerConnectionObserver;
import eu.h2020.helios_social.modules.videocall.connection.CustomSdpObserver;
import eu.h2020.helios_social.modules.videocall.connection.IceServer;
import eu.h2020.helios_social.modules.videocall.connection.SignallingClient;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;


public class VideoCallActivity extends AppCompatActivity
implements View.OnClickListener, SignallingClient.SignalingInterface
{
    // Definition of the WebRTC objects:
    PeerConnectionFactory peerConnectionFactory;
    MediaConstraints audioConstraints;
    MediaConstraints videoConstraints;
    VideoSource videoSource;
    VideoTrack localVideoTrack;
    AudioSource audioSource;
    AudioTrack localAudioTrack;
    SurfaceTextureHelper surfaceTextureHelper;
    VideoCapturer videoCapturerAndroid;

    SurfaceViewRenderer localVideoView;
    Map<String, SurfaceViewRenderer> remoteVideoViews = new HashMap<String, SurfaceViewRenderer>();
    Queue<SurfaceViewRenderer> remoteVideoViewsPool = new ArrayDeque<>();

    Button hangup;
    List<IceServer> iceServers;

    List<PeerConnection.IceServer> peerIceServers = new ArrayList<>();

    Map<String, PeerConnection> peerConnections = new HashMap<String, PeerConnection>();

    final int ALL_PERMISSIONS_CODE = 1;

    private static final String TAG = "VideoCallActivity";

    private String room_name = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_call);

        room_name = getIntent().getStringExtra("room_name");

        if (room_name == null) room_name = getString(R.string.room_name);

        // Check if the app has permissions to use the camera and microphone
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                },
                ALL_PERMISSIONS_CODE
            );
            return;
        }

        // All permissions already granted
        start();
    }

    @Override
    public void onRequestPermissionsResult(
        int requestCode,
        @NonNull String[] permissions,
        @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != ALL_PERMISSIONS_CODE) return;

        for (int i = 0; i < grantResults.length; i++)
            if(grantResults[i] == PackageManager.PERMISSION_GRANTED)
            {
                start();
                return;
            }

        Toast.makeText(this, "Permissions must be accepted", Toast.LENGTH_LONG).show();
        finish();
    }


    /**
     * START INTERNAL METHODS
     */

    private SurfaceViewRenderer initRemoteVideoView(EglBase rootEglBase, int id)
    {
        SurfaceViewRenderer remoteVideoView = findViewById(id);

        remoteVideoView.init(rootEglBase.getEglBaseContext(), null);
        remoteVideoView.setZOrderMediaOverlay(true);
        remoteVideoView.setMirror(false);

        return remoteVideoView;
    }

    public void start() {
        // keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Init the video views for the local and remote videos
        EglBase rootEglBase = EglBase.create();

        localVideoView = findViewById(R.id.local_gl_surface_view);

        localVideoView.init(rootEglBase.getEglBaseContext(), null);
        localVideoView.setZOrderMediaOverlay(true);
        localVideoView.setMirror(true);

        remoteVideoViewsPool.add(initRemoteVideoView(rootEglBase, R.id.remote_gl_surface_view0));
        remoteVideoViewsPool.add(initRemoteVideoView(rootEglBase, R.id.remote_gl_surface_view1));
        remoteVideoViewsPool.add(initRemoteVideoView(rootEglBase, R.id.remote_gl_surface_view2));

        // Prepares the hangup button
        hangup = findViewById(R.id.end_call);
        hangup.setOnClickListener(this);

        // Initialize IceServers array composed of the STUN server and the TURN server
        iceServers = Arrays.asList(new IceServer[]{
            new IceServer(getString(R.string.STUN_URL), null, null),
            new IceServer(getString(R.string.TURN_URL),
                getString(R.string.TURN_user),
                getString(R.string.TURN_credential)
            )
        });

        //Connect to the ICE servers
        for (IceServer iceServer : iceServers) {
            PeerConnection.IceServer.Builder builder = PeerConnection.IceServer.builder(iceServer.url);

            if (iceServer.credential != null)
                builder
                    .setUsername(iceServer.username)
                    .setPassword(iceServer.credential);

            peerIceServers.add(builder.createIceServer());
        }

        SignallingClient.getInstance().init(this, getString(R.string.API_endpoint), room_name);

        //Initialize PeerConnectionFactory globals.
        PeerConnectionFactory.InitializationOptions initializationOptions =
            PeerConnectionFactory.InitializationOptions.builder(this)
                .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        //Create a new PeerConnectionFactory instance - using Hardware encoder and decoder.
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();

        DefaultVideoEncoderFactory defaultVideoEncoderFactory = new DefaultVideoEncoderFactory(
                rootEglBase.getEglBaseContext(),
                /* enableIntelVp8Encoder */ true,
                /* enableH264HighProfile */ true);
        DefaultVideoDecoderFactory defaultVideoDecoderFactory = new DefaultVideoDecoderFactory(
                rootEglBase.getEglBaseContext());

        peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(defaultVideoEncoderFactory)
                .setVideoDecoderFactory(defaultVideoDecoderFactory)
                .createPeerConnectionFactory();

        //Now create a VideoCapturer instance.
        videoCapturerAndroid = createCameraCapturer(new Camera1Enumerator(false));

        //Create MediaConstraints - Will be useful for specifying video and audio constraints.
        audioConstraints = new MediaConstraints();
        videoConstraints = new MediaConstraints();

        //Create a VideoSource instance
        if (videoCapturerAndroid != null) {
            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.getEglBaseContext());
            videoSource = peerConnectionFactory.createVideoSource(videoCapturerAndroid.isScreencast());
            videoCapturerAndroid.initialize(surfaceTextureHelper, this, videoSource.getCapturerObserver());
        }
        localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource);

        //create an AudioSource instance
        audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource);

        if (videoCapturerAndroid != null) {
            videoCapturerAndroid.startCapture(1024, 720, 30);
        }

        localVideoView.setVisibility(View.VISIBLE);

        // And finally, with our VideoRenderer ready, we
        // can add our renderer to the VideoTrack.
        localVideoTrack.addSink(localVideoView);
    }

    /**
     * Received remote peer's media stream. we will get the first video track
     * and render it
     */
    private void gotRemoteStream(MediaStream stream, String id) {
        // we have remote video stream. add to the renderer
        final VideoTrack videoTrack = stream.videoTracks.get(0);

        runOnUiThread(() -> {
            SurfaceViewRenderer remoteVideoView = remoteVideoViewsPool.poll();
            if(remoteVideoView == null) {
                showToast("No available video slots in layout");
                return;
            }

            try {
                remoteVideoView.setVisibility(View.VISIBLE);
                videoTrack.addSink(remoteVideoView);
            } catch (Exception e) {
                showToast("Error setting remote video " + id);
                e.printStackTrace();
                return;
            }

            remoteVideoViews.put(id, remoteVideoView);
        });
    }


    //
    // SignalingInterface
    //

    /**
     * SignallingCallback - Called when remote peer sends answer to your offer
     */
    @Override
    public void onAnswerReceived(JSONObject data, String id) {
        showToast("Received Answer");

        String sdp;
        String type;

        try {
            sdp = data.getString("sdp");
            type = data.getString("type").toLowerCase();
        }
        catch (JSONException e) {
            showToast("Malformed answer SDP");
            e.printStackTrace();
            return;
        }

        PeerConnection peerConnection = peerConnections.get(id);
        if(peerConnection == null)
        {
            showToast("Unknown peer connection" + id);
            return;
        }

        peerConnection.setRemoteDescription(
            new CustomSdpObserver("onAnswerReceived_setRemoteDescription"),
            new SessionDescription(
                SessionDescription.Type.fromCanonicalForm(type),
                sdp
            )
        );
    }

    /**
     * SignallingCallback - called when the room is created - i.e. you are the
     * initiator
     */
    @Override
    public void onCreatedRoom() {
        showToast("You created the room (" + room_name + ")");
    }

    public void onDisconnect(String reason) {
        showToast("Disconnected (" + reason + ")");

        Iterator value = peerConnections.keySet().iterator();
        while (value.hasNext())
            closePeer(value.next().toString());
    }

    /**
     * Remote IceCandidate received
     */
    @Override
    public void onIceCandidateReceived(JSONObject data, String id) {
        String sdpCandidate;
        String sdpId;
        int    sdpLabel;

        try {
            sdpCandidate = data.getString("candidate");
            sdpId = data.getString("id");
            sdpLabel = data.getInt("label");
        } catch (JSONException e) {
            showToast("Malformed ICE candidate SDP");
            e.printStackTrace();
            return;
        }

        PeerConnection peerConnection = peerConnections.get(id);
        if(peerConnection == null)
        {
            showToast("Unknown peer connection" + id);
            return;
        }

        peerConnection.addIceCandidate(new IceCandidate(sdpId, sdpLabel,
            sdpCandidate));
    }

    /**
     * SignallingCallback - called when you join the room - you are a participant
     */
    @Override
    public void onJoinedRoom() {
        showToast("You joined the room (" + room_name + ") ");
    }

    @Override
    public void onNewPeerJoined(String id) {
        showToast("Remote Peer Joined with id "+id);

        PeerConnection pc = createPeerConnection(id);

        MediaConstraints sdpConstraints = new MediaConstraints();

        sdpConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        sdpConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

        pc.createOffer(
            new CustomSdpObserver("createOffer") {
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {
                    super.onCreateSuccess(sessionDescription);

                    pc.setLocalDescription(
                        new CustomSdpObserver("createOffer_setLocalDescription") {
                            @Override
                            public void onSetSuccess() {
                                super.onSetSuccess();

                                SignallingClient.getInstance()
                                    .emitMessage(sessionDescription, id);
                            }
                        },
                        sessionDescription
                    );
                }
            },
            sdpConstraints
        );
    }

    /**
     * SignallingCallback - Called when remote peer sends offer
     */
    @Override
    public void onOfferReceived(final JSONObject data, String id) {
        showToast("Received Offer");

        String sdp;

        try {
            sdp = data.getString("sdp");
        } catch (JSONException e) {
            showToast("Malformed offer SDP");
            e.printStackTrace();
            return;
        }

        PeerConnection pc = createPeerConnection(id);

        pc.setRemoteDescription(
            new CustomSdpObserver("onOffer_setRemoteDescription") {
                @Override
                public void onSetSuccess() {
                    super.onSetSuccess();

                    pc.createAnswer(
                        new CustomSdpObserver("createAnswer") {
                            @Override
                            public void onCreateSuccess(SessionDescription sessionDescription) {
                                super.onCreateSuccess(sessionDescription);

                                pc.setLocalDescription(
                                    new CustomSdpObserver("createAnswer_setLocalDescription") {
                                        @Override
                                        public void onSetSuccess() {
                                            super.onSetSuccess();

                                            SignallingClient.getInstance()
                                                .emitMessage(sessionDescription, id);
                                        }
                                    },
                                    sessionDescription
                                );
                            }
                        },
                        new MediaConstraints()
                    );
                }
            },
            new SessionDescription(SessionDescription.Type.OFFER, sdp)
        );
    }

    @Override
    public void onRemoteHangUp(String id) {
        showToast("Remote Peer hungup");

        closePeer(id);
    }

    private void closePeer(String id)
    {
        PeerConnection pc = peerConnections.get(id);
        if (pc != null) {
            pc.close();

            peerConnections.remove(id);
        }

        runOnUiThread(() -> {
            SurfaceViewRenderer remoteVideoView = remoteVideoViews.get(id);
            if(remoteVideoView == null) {
                showToast("Unknown remote video view: " + id);
                return;
            }

            try {
                remoteVideoView.setVisibility(View.GONE);
                // videoTrack.removeSink(remoteVideoView);
            } catch (Exception e) {
                showToast("Can't remove visibility of remote video view: " + id);
                e.printStackTrace();
            }

            remoteVideoViews.remove(id);
            remoteVideoViewsPool.add(remoteVideoView);
        });
    }

    /**
     * Creating the local peerconnection instance
     */
    private PeerConnection createPeerConnection(String id) {
        PeerConnection.RTCConfiguration rtcConfig =
            new PeerConnection.RTCConfiguration(peerIceServers);

        // TCP candidates are only useful when connecting to a server that
        // supports ICE-TCP.
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;

        // Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;

        PeerConnection peerConnection = peerConnectionFactory.createPeerConnection(
            rtcConfig,
            new CustomPeerConnectionObserver("localPeerCreation") {
                @Override
                public void onIceCandidate(IceCandidate iceCandidate) {
                    super.onIceCandidate(iceCandidate);

                    // Received local ice candidate. Send it to remote peer
                    // through signalling for negotiation
                    SignallingClient.getInstance().emitIceCandidate(iceCandidate, id);
                }

                @Override
                public void onAddStream(MediaStream mediaStream) {
                    showToast("Received Remote stream");

                    super.onAddStream(mediaStream);
                    gotRemoteStream(mediaStream, id);
                }
            }
        );

        // Creating local mediastream
        MediaStream stream = peerConnectionFactory.createLocalMediaStream("102");

        stream.addTrack(localAudioTrack);
        stream.addTrack(localVideoTrack);

        // Adding the stream to the localpeer
        peerConnection.addStream(stream);

        // TODO remove when PeerConnection gets clossed
        peerConnections.put(id, peerConnection);

        return peerConnection;
    }


    /**
     * Closing up - normal hangup and app destroy
     */

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.end_call) {
            hangup();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK)) {
            hangup();
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        Iterator value = peerConnections.keySet().iterator();
        while (value.hasNext())
            SignallingClient.getInstance().emitMessage("bye", value.next().toString());

        super.onDestroy();

        if (surfaceTextureHelper != null) {
            surfaceTextureHelper.dispose();
            surfaceTextureHelper = null;
        }

        SignallingClient.getInstance().close();
    }

    private void hangup() {
        Iterator value = peerConnections.keySet().iterator();
        while (value.hasNext())
            closePeer(value.next().toString());

        try {
            if (localVideoView != null)
                localVideoView.release();

            value = remoteVideoViews.values().iterator();
            while (value.hasNext())
            {
                SurfaceViewRenderer remoteVideoView = (SurfaceViewRenderer) value.next();

                if (remoteVideoView != null)
                    remoteVideoView.release();
            }

            for (SurfaceViewRenderer remoteVideoView : remoteVideoViewsPool) {
                if (remoteVideoView != null)
                    remoteVideoView.release();
            }

            if (videoCapturerAndroid != null)
                videoCapturerAndroid.dispose();
        } catch (Exception e) {
            e.printStackTrace();
        }

        finish();
    }


    /**
     * Util Methods
     */

    public int dpToPx(int dp) {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();

        return Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
    }

    public void showToast(final String msg) {
        runOnUiThread(() -> Toast.makeText(VideoCallActivity.this, msg, Toast.LENGTH_SHORT).show());
    }

    // This method creates a cameraCapturer to take the video from the device's camera:
    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        Logging.d(TAG, "Looking for front facing cameras.");

        for (String deviceName : deviceNames)
            if (enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating front facing camera capturer.");

                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null)
                    return videoCapturer;
            }

        // Front facing camera not found, try something else
        Logging.d(TAG, "Looking for other cameras.");

        for (String deviceName : deviceNames)
            if (!enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating other camera capturer.");

                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null)
                    return videoCapturer;
            }

        return null;
    }
}
