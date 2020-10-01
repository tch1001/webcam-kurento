package com.example.demo;

import com.example.demo.hello.UserSession;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.catalina.User;
import org.apache.tomcat.websocket.WsRemoteEndpointAsync;
import org.kurento.client.*;
import org.kurento.jsonrpc.JsonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;

public class AdminHandler extends TextWebSocketHandler {
    private static final Gson gson = new GsonBuilder().create();
    @Autowired
    public KurentoClient kurentoClient;
    private final ConcurrentHashMap<String, UserSession> admins = new ConcurrentHashMap<>();
    @Override
    public void afterConnectionEstablished(WebSocketSession session)
            throws Exception
    {

    }
    @Override
    public void afterConnectionClosed(final WebSocketSession session,
                                      CloseStatus status) throws Exception
    {

    }

    @Override
    protected void handleTextMessage(WebSocketSession session,
                                     TextMessage message) throws Exception{
        JsonObject jsonMessage = gson.fromJson(message.getPayload(),
                JsonObject.class);
        try {
            final String messageId = jsonMessage.get("id").getAsString();
            final String sessionId = session.getId();
            Handler handler = DemoApplication.context.getBean(Handler.class);
            switch (messageId) {
                case "LIST_ALL_CAMERAS":
                    synchronized (session) {
//                        System.out.println("listing all cameras");
                        ArrayList<String> cameraIds = new ArrayList<String>();
                        for (Enumeration iter = handler.getUsers().keys(); iter.hasMoreElements(); ) {
//                        System.out.println(iter.nextElement().toString());
                            String cameraId = iter.nextElement().toString();
//                        System.out.println(cameraId);
                            cameraIds.add(cameraId);
                        }
                        JsonObject jsonObject = new JsonObject();
                        jsonObject.addProperty("id", "ALL_CAMERAS");
                        jsonObject.addProperty("cameras", gson.toJson(cameraIds));
                        synchronized (session) {
                            sendMessage(session, jsonObject.toString());
                        }
                    }
                    break;
                case "PLAY":
                    synchronized (session) {
                        String cameraId = jsonMessage.get("cameraId").toString().replaceAll("\"", "");
                        System.out.println(cameraId);
                        UserSession user = handler.getUsers().get(cameraId);
                        // user is the camera we are requesting for
                        WebRtcEndpoint webRtcEndpoint = user.getWebRtcEndpoint();
                        WebRtcEndpoint viewer = new WebRtcEndpoint.Builder(user.getMediaPipeline()).build();
                        UserSession admin = new UserSession();
                        admins.put(session.getId(), admin);
                        // assuming each request is separate "admin"
                        viewer.addIceCandidateFoundListener(new EventListener<IceCandidateFoundEvent>() {
                            @Override
                            public void onEvent(IceCandidateFoundEvent iceCandidateFoundEvent) {
                                JsonObject response = new JsonObject();
                                response.addProperty("id", "ADD_ICE_CANDIDATE");
                                response.addProperty("cameraId", cameraId);
                                response.add("candidate", JsonUtils.toJsonObject(iceCandidateFoundEvent.getCandidate()));
                                try {
                                    synchronized (session) {
                                        session.sendMessage(new TextMessage(response.toString()));
                                    }
                                } catch (IOException e) {

                                }
                            }
                        });
                        admin.setWebRtcEndpoint(viewer);
                        webRtcEndpoint.connect(viewer);
                        String sdpOffer = jsonMessage.getAsJsonPrimitive("sdpOffer").getAsString();
                        String sdpAnswer = viewer.processOffer(sdpOffer);
                        JsonObject response = new JsonObject();
                        response.addProperty("id", "startResponse");
                        response.addProperty("sdpAnswer", sdpAnswer);
                        response.addProperty("cameraId",cameraId);
//                        System.out.println(sdpAnswer);
                        synchronized (session) {
                            session.sendMessage(new TextMessage(response.toString()));
                        }
                        viewer.gatherCandidates();
                    }
                    break;
                case "onIceCandidate":
                    JsonObject candidate = jsonMessage.get("candidate").getAsJsonObject();
                    IceCandidate cand = new IceCandidate(candidate.get("candidate").getAsString(), candidate.get("sdpMid")
                                    .getAsString(), candidate.get("sdpMLineIndex").getAsInt());
                    UserSession admin2 = admins.get(session.getId());
                    admin2.getWebRtcEndpoint().addIceCandidate(cand);
                    break;
                case "RECORD":

                    String cameraId = jsonMessage.get("cameraId").toString().replaceAll("\"", "");
                    System.out.println("recording " + cameraId);
                    UserSession user = handler.getUsers().get(cameraId);

                    if(user.recording) break;
                    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
                    LocalDateTime now = LocalDateTime.now();
                    String RECORDER_FILE_PATH = "file:///home/tanfamily/Videos/"+dtf.format(now)+"_"+cameraId+".mp4";
                    RecorderEndpoint recorder = new RecorderEndpoint.Builder(user.getMediaPipeline(),
                            RECORDER_FILE_PATH)
                            .build();
                    user.setRecorderEndpoint(recorder);
                    recorder.record();
                    user.recording = true;

                    user.getWebRtcEndpoint().connect(recorder, MediaType.AUDIO);
                    user.getWebRtcEndpoint().connect(recorder, MediaType.VIDEO);
                    user.getWebRtcEndpoint().gatherCandidates(); // is this really necessary?

                    break;
                case "STOP_RECORDING":
                    cameraId = jsonMessage.get("cameraId").toString().replaceAll("\"", "");
                    System.out.println("stop recording " + cameraId);
                    user = handler.getUsers().get(cameraId);
                    if(!user.recording) break;
                    user.getRecorderEndpoint().stop();
                    user.recording = false;
                    break;
            }
        } catch (Throwable ex) {
            ex.printStackTrace();
        }
    }
    private synchronized void sendMessage(final WebSocketSession session,
                                          String message)
    {
        try {
            session.sendMessage(new TextMessage(message));
        }catch(Exception e) {
        }
    }

}

