package com.example.demo;

import com.example.demo.hello.UserSession;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.kurento.client.*;
import org.kurento.jsonrpc.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;

public class Handler extends TextWebSocketHandler{
    private static final Logger log = LoggerFactory.getLogger(Handler.class);
    private static final Gson gson = new GsonBuilder().create();

    private final ConcurrentHashMap<String, UserSession> users =
            new ConcurrentHashMap<>();

    public ConcurrentHashMap<String, UserSession> getUsers() {
        return users;
    }

    @Autowired
    private KurentoClient kurento;

    @Override
    public void afterConnectionEstablished(WebSocketSession session)
            throws Exception
    {
        log.info("[Handler::afterConnectionEstablished] New WebSocket connection, sessionId: {}",
                session.getId());
    }
    @Override
    public void afterConnectionClosed(final WebSocketSession session,
                                      CloseStatus status) throws Exception
    {
        if (!status.equalsCode(CloseStatus.NORMAL)) {
            log.warn("[Handler::afterConnectionClosed] status: {}, sessionId: {}",
                    status, session.getId());
        }
        UserSession user = users.remove(session.getId());
        if(user != null){
            MediaPipeline mediaPipeline = user.getMediaPipeline();
            if(mediaPipeline != null){
                mediaPipeline.release();
            }
        }
    }
    @Override
    protected void handleTextMessage(WebSocketSession session,
                                     TextMessage message) throws Exception
    {
        JsonObject jsonMessage = gson.fromJson(message.getPayload(),
                JsonObject.class);

        log.info("[Handler::handleTextMessage] message: {}, sessionId: {}",
                jsonMessage, session.getId());

        try {
            final String messageId = jsonMessage.get("id").getAsString();
            final String sessionId = session.getId();
            switch (messageId) {
                case "PROCESS_SDP_OFFER":
                    // Start: Create user session and process SDP Offer
                    final UserSession user = new UserSession();
                    users.put(sessionId, user);
                    System.out.println("Users");
                    for(Enumeration iter = users.keys(); iter.hasMoreElements();){
                        System.out.println(iter.nextElement().toString());
                    }
                    // now create media pipeline
                    final MediaPipeline pipeline = kurento.createMediaPipeline();
                    user.setMediaPipeline(pipeline);
                    // create endpoint
                    final WebRtcEndpoint webRtcEp = new WebRtcEndpoint.Builder(pipeline).build();
                    user.setWebRtcEndpoint(webRtcEp);
//                    webRtcEp.connect(webRtcEp);

                    String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
                    webRtcEp.addIceCandidateFoundListener(new EventListener<IceCandidateFoundEvent>() {
                        @Override
                        public void onEvent(IceCandidateFoundEvent iceCandidateFoundEvent) {
                            JsonObject message = new JsonObject();
                            message.addProperty("id", "ADD_ICE_CANDIDATE");
                            message.add("candidate", JsonUtils.toJsonObject(iceCandidateFoundEvent.getCandidate()));
                            sendMessage(session, message.toString());
                        }
                    });
                    final String name = "user"+sessionId+"_webrtcendpoint";
                    webRtcEp.setName(name);

                    final String sdpAnswer = webRtcEp.processOffer(sdpOffer);

                    JsonObject returnMessage = new JsonObject();
                    returnMessage.addProperty("id","PROCESS_SDP_ANSWER");
                    returnMessage.addProperty("sdpAnswer",sdpAnswer);
                    sendMessage(session, returnMessage.toString());

                    webRtcEp.gatherCandidates();

                    break;
                case "ADD_ICE_CANDIDATE":
                    final UserSession user2 = users.get(sessionId);
                    final JsonObject jsonCandidate = jsonMessage.get("candidate").getAsJsonObject();
                    final IceCandidate candidate = new IceCandidate(jsonCandidate.get("candidate").getAsString(),
                            jsonCandidate.get("sdpMid").getAsString(),
                            jsonCandidate.get("sdpMLineIndex").getAsInt());
                    WebRtcEndpoint webRtcEp2 = user2.getWebRtcEndpoint();
                    webRtcEp2.addIceCandidate(candidate);
                    break;
                case "STOP":
                    break;
                case "ERROR":
                    break;
                default:
                    // Ignore the message
                    log.warn("[Handler::handleTextMessage] Skip, invalid message, id: {}",
                            messageId);
                    break;
            }
        } catch (Throwable ex) {
            log.error("[Handler::handleTextMessage] Exception: {}",
                    ex);
        }
    }


    private synchronized void sendMessage(final WebSocketSession session,
                                          String message)
    {
        log.debug("[Handler::sendMessage] {}", message);

        if (!session.isOpen()) {
            log.warn("[Handler::sendMessage] Skip, WebSocket session isn't open");
            return;
        }

        final String sessionId = session.getId();
        if (!users.containsKey(sessionId)) {
            log.warn("[Handler::sendMessage] Skip, unknown user, id: {}",
                    sessionId);
            return;
        }

        try {
            session.sendMessage(new TextMessage(message));
        } catch (IOException ex) {
            log.error("[Handler::sendMessage] Exception: {}", ex.getMessage());
        }
    }


}
