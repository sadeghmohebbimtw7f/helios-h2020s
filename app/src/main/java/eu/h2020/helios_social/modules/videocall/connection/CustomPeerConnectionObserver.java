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

import android.util.Log;

import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.RtpReceiver;

public class CustomPeerConnectionObserver implements PeerConnection.Observer {

    private String logTag;

    public CustomPeerConnectionObserver(String logTag) {
        this.logTag = this.getClass().getCanonicalName();
        this.logTag = this.logTag+" "+logTag;
    }

    @Override
    public void onSignalingChange(PeerConnection.SignalingState signalingState) {
        Log.d(logTag, "onSignalingChange() called with: signalingState = [" + signalingState + "]");
    }

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
        Log.d(logTag, "onIceConnectionChange() called with: iceConnectionState = [" + iceConnectionState + "]");
    }

    @Override
    public void onIceConnectionReceivingChange(boolean b) {
        Log.d(logTag, "onIceConnectionReceivingChange() called with: b = [" + b + "]");
    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
        Log.d(logTag, "onIceGatheringChange() called with: iceGatheringState = [" + iceGatheringState + "]");
    }

    @Override
    public void onIceCandidate(IceCandidate iceCandidate) {
        Log.d(logTag, "onIceCandidate() called with: iceCandidate = [" + iceCandidate + "]");
    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
        Log.d(logTag, "onIceCandidatesRemoved() called with: iceCandidates = [" + iceCandidates + "]");
    }

    @Override
    public void onAddStream(MediaStream mediaStream) {
        Log.d(logTag, "onAddStream() called with: mediaStream = [" + mediaStream + "]");
    }

    @Override
    public void onRemoveStream(MediaStream mediaStream) {
        Log.d(logTag, "onRemoveStream() called with: mediaStream = [" + mediaStream + "]");
    }

    @Override
    public void onDataChannel(DataChannel dataChannel) {
        Log.d(logTag, "onDataChannel() called with: dataChannel = [" + dataChannel + "]");
    }

    @Override
    public void onRenegotiationNeeded() {
        Log.d(logTag, "onRenegotiationNeeded() called");
    }

    @Override
    public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
        Log.d(logTag, "onAddTrack() called with: rtpReceiver = [" + rtpReceiver + "], mediaStreams = [" + mediaStreams + "]");
    }
}
