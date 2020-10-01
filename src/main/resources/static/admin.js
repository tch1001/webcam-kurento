const ws = new WebSocket("wss://"+location.host+"/downstream");
ws.onopen = function(){
    start();
};

// websocket stuff=======================
let display;
let participants = {};

window.onload = function(){
    display = document.getElementById('display')

}
window.onbeforeunload = function(){
    ws.close();
}
function sendMessage(message){
    console.log("sending message", message)
    ws.send(JSON.stringify(message));
}
let cameras = [];
ws.onmessage = function(message){
    var jsonMessage = JSON.parse(message.data);
    console.log("received message", message.data);
    switch(jsonMessage.id){
        case "ALL_CAMERAS":
            cameras = jsonMessage.cameras.replaceAll('[','').replaceAll(']','').replaceAll('"','').split(',')
            setupVideoDisplays();
            console.log('all cameras setup', participants);
            break;
        case "ADD_ICE_CANDIDATE":
            console.log('need to me non empty now!', participants);
            console.log('cameraId', jsonMessage.cameraId)
            participants[jsonMessage.cameraId].rtcPeer.addIceCandidate(jsonMessage.candidate,
                function(error){
                    if(error){ console.error(err) }
                }
            )
            break;
        case "startResponse":
//            console.log(jsonMessage.sdpAnswer)
            participants[jsonMessage.cameraId].rtcPeer.processAnswer(jsonMessage.sdpAnswer,
                function(error){
                    if(error){
                        console.log("sdpAnswer", error)
                    }
                    var video = document.getElementById(jsonMessage.cameraId);
                    console.log(video);
                    video.play();
                }
            )
            break;
        default:
            break;
    }
}
function setupVideoDisplays(){
    var videoDisplays = document.getElementById('video-displays');
    var cam = 0;
    for(i of cameras){
        var vid = document.createElement('video');
        var recordButton = document.createElement('button');
        recordButton.innerHTML="record"
        recordButton.id = i;
        var stopButton = document.createElement('button');
        stopButton.innerHTML="stop"
        stopButton.id = i;
        vid.id = i;
        vid.muted = true;
        var tempStr = vid.id;
        recordButton.onclick = function(){
            sendMessage({
                id: "RECORD",
                cameraId: this.id
            })
            console.log(this.id);
        }
        stopButton.onclick = function(){
            sendMessage({
                id: "STOP_RECORDING",
                cameraId: this.id
            })
            console.log(this.id);
        }
        videoDisplays.appendChild(vid);
        videoDisplays.appendChild(recordButton);
        videoDisplays.appendChild(stopButton);
        downstreamVideos(cam++);
        vid.play();
    }
}
function reloadVideos(){
    var videos = document.getElementsByTagName("video");
    for(var i = 0; i<videos.length; i++){
        videos[i].play();
    }
}
function Participant(videoId){
    this.videoId = videoId;
    this.video = document.getElementById(videoId);
    this.offerToReceiveVideo = function(error, offerSdp, wp){
        var msg = {
            id:"PLAY",
            cameraId: videoId,
            sdpOffer: offerSdp
        }
        sendMessage(msg);
    }
    this.onIceCandidate = function(candidate, wp){
        var msg = {
            id: 'onIceCandidate',
            candidate: candidate
        }
        sendMessage(msg);
    }
    Object.defineProperty(this, 'rtcPeer', {writable: true});
}
function downstreamVideos(j){
    var videos = document.getElementsByTagName("video");
    for(var i = j; i<=j; i++) if(videos[i].id != ""){
        var video = videos[i];
        var videoId = video.id;
        var participant = new Participant(videoId);
        participants[videoId] = participant;
        console.log(video)
        var options = {
            remoteVideo: participant.video,
            mediaConstraints: {
                audio: true,
                video: true
            },
            onicecandidate: participant.onIceCandidate.bind(participant)
        }
        participant.rtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options,
            function(error){
                if(error) return console.error(error);
                this.generateOffer(participant.offerToReceiveVideo.bind(participant));
            }
        );
        video.play();
    }
}


function start(){
    sendMessage({id : 'LIST_ALL_CAMERAS'})
}




