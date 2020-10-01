const ws = new WebSocket("wss://"+location.host+"/upstream");

// websocket stuff=======================
let vid;
let display;
let webRtcPeer;

window.onload = function(){
    vid = document.getElementById('webcam')
    vid.autoplay = true;
    vid.controls = false;
    display = document.getElementById('display')
    start();
}
window.onbeforeunload = function(){
    ws.close();
}
function sendMessage(message){
    console.log("sending message", message)
    ws.send(JSON.stringify(message));
}

ws.onmessage = function(message){
    const jsonMessage = JSON.parse(message.data);
    console.log("received message", message.data);
    switch(jsonMessage.id){
        case "PROCESS_SDP_ANSWER":
            webRtcPeer.processAnswer(jsonMessage.sdpAnswer, (err)=>{

                console.log("err in processs answer", err);
                console.log("theoretically started displaying")
                display.play().catch((err)=>{
                    console.log("error displaying", err);
                });
                vid.play();
            })
            break;
        case "ADD_ICE_CANDIDATE":
            webRtcPeer.addIceCandidate(jsonMessage.candidate, (err)=>{console.log("err add ice candidate", err);});
            break;
        default:
            break;
    }
}
function start(){
//    function fail(){
//    	alert('failed')
//    }
//    function success(stream){
//        var vid = document.getElementById('webcam')
//    	vid.srcObject = stream;
//    	vid.play();
//    }
//    navigator.getUserMedia = ( navigator.getUserMedia || navigator.webkitGetUserMedia || navigator.mozGetUserMedia || navigator.msgGetUserMedia );
//    if(navigator.getUserMedia) navigator.getUserMedia({
//    	video: true,
//    	audio: false
//    }, success, fail);

    const options = {
        localVideo: vid,
//        remoteVideo: vid,
        mediaConstraints: {audio: true, video: {facingMode: "environment"}},
        onicecandidate: function(candidate){
            console.log("on ice candidate" + JSON.stringify(candidate))
            var message = {
                id: 'ADD_ICE_CANDIDATE',
                candidate: candidate
            };
            sendMessage(message);
        }
    }
    webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendrecv(options,
        function(error){
            if(error) return console.error(error);
            webRtcPeer.generateOffer(function(err, offer){
                // on sdp offer callback function
                console.log("generating offer");
                console.log("err generating offer", err);
                var message = {
    		    	id : 'PROCESS_SDP_OFFER',
    	        	sdpOffer : offer
                }
                sendMessage(message);
            });
        }
    )
}




