package com.amazon.alexa.avs;



import java.io.*;
import java.net.*;

import com.amazon.alexa.avs.auth.AccessTokenListener;
import com.amazon.alexa.avs.auth.AuthSetup;
import com.amazon.alexa.avs.auth.companionservice.RegCodeDisplayHandler;
import com.amazon.alexa.avs.config.DeviceConfig;
import com.amazon.alexa.avs.config.DeviceConfigUtils;
import com.amazon.alexa.avs.http.AVSClientFactory;

public class AVSSocketListener implements ExpectSpeechListener, RecordingRMSListener, 
RegCodeDisplayHandler, AccessTokenListener {


    private final DeviceConfig deviceConfig;
    private Thread autoEndpoint = null; // used to auto-endpoint while listening
    private static final int ENDPOINT_THRESHOLD = 5;
    private static final int ENDPOINT_SECONDS = 2; // amount of silence time before endpointing
    private final AVSController controller;
    private AuthSetup authSetup;
    

    ServerSocket providerSocket;
    Socket connection = null;
    ObjectOutputStream out;
    ObjectInputStream in;
    String message;
	

    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            new AVSSocketListener(args[0]);
        } else {
            new AVSSocketListener();
        }
        
    }

    public AVSSocketListener() throws Exception {
        this(DeviceConfigUtils.readConfigFile());
    }

    public AVSSocketListener(String configName) throws Exception {
        this(DeviceConfigUtils.readConfigFile(configName));
    }

    private AVSSocketListener(DeviceConfig config) throws Exception {
        deviceConfig = config;
        controller = new AVSController(this, new AVSAudioPlayerFactory(), new AlertManagerFactory(),
                getAVSClientFactory(deviceConfig), DialogRequestIdAuthority.getInstance());

        authSetup = new AuthSetup(config, this);
        authSetup.addAccessTokenListener(this);
        authSetup.addAccessTokenListener(controller);
        authSetup.startProvisioningThread();

        controller.startHandlingDirectives();

        while(true){
        
	        try{
	            //1. creating a server socket
	            providerSocket = new ServerSocket(41384, 10);
	            //2. Wait for connection
	            System.out.println("Waiting for connection");
	            connection = providerSocket.accept();
	            System.out.println("Connection received from " + connection.getInetAddress().getHostName());
	            //3. get Input and Output streams
	            out = new ObjectOutputStream(connection.getOutputStream());
	            out.flush();
	            in = new ObjectInputStream(connection.getInputStream());
	            sendMessage("Connection successful");
	            /*
	            //4. The two parts communicate via the input and output streams
	            do{
	                try{
	                    message = (String)in.readObject();
	                    System.out.println("client>" + message);
	                    if (message.equals("bye"))
	                        sendMessage("bye");
	                }
	                catch(ClassNotFoundException classnot){
	                    System.err.println("Data received in unknown format");
	                }
	            }while(!message.equals("bye"));
	            */
                actionPerformed();
	        }
	        catch(IOException ioException){
	            ioException.printStackTrace();
	        }
	        finally{
	            //4: Closing connection
	            try{
	                in.close();
	                out.close();
	                providerSocket.close();
	            }
	            catch(IOException ioException){
	                ioException.printStackTrace();
	            }
	        }
        }
    }

    protected AVSClientFactory getAVSClientFactory(DeviceConfig config) {
        return new AVSClientFactory(config);
    }
    

    @Override
    public void rmsChanged(int rms) { // AudioRMSListener callback
        // if greater than threshold or not recording, kill the autoendpoint thread
        if ((rms == 0) || (rms > ENDPOINT_THRESHOLD)) {
            if (autoEndpoint != null) {
                autoEndpoint.interrupt();
                autoEndpoint = null;
            }
        } else if (rms < ENDPOINT_THRESHOLD) {
            // start the autoendpoint thread if it isn't already running
            if (autoEndpoint == null) {
                autoEndpoint = new Thread() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(ENDPOINT_SECONDS * 1000);
                           // actionButton.doClick(); // hit stop if we get through the autoendpoint
                           //                         // time
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                };
                autoEndpoint.start();
            }
        }

        //visualizer.setValue(rms); // update the visualizer
    }

    @Override
    public void onExpectSpeechDirective() {
        Thread thread = new Thread() {
            @Override
            public void run() {
                while ( //!actionButton.isEnabled() || !actionButton.getText().equals(START_LABEL)
                        // || 
                        controller.isSpeaking()) {
                    try {
                        Thread.sleep(500);
                    } catch (Exception e) {
                    }
                }
                //actionButton.doClick();
            }
        };
        thread.start();

    }

    
    @Override
    public void displayRegCode(String regCode) {
        String regUrl =
                deviceConfig.getCompanionServiceInfo().getServiceUrl() + "/provision/" + regCode;
        System.out.println("Please register your device by visiting the following website on "
                + "any system and following the instructions:\n" + regUrl
                + "\n\n Hit OK once completed.");
    }

    @Override
    public synchronized void onAccessTokenReceived(String accessToken) {
    	System.out.println("Received token: " + accessToken);
    }
    

    public void finishProcessing() {
        controller.processingFinished();

    }
    
    public void actionPerformed() {
        final RecordingRMSListener rmsListener = this;
        controller.onUserActivity();
        //if (actionButton.getText().equals(START_LABEL)) { // if in idle mode
        //if (event.getState() == PinState.HIGH) {
        //    actionButton.setText(STOP_LABEL);

            RequestListener requestListener = new RequestListener() {

                @Override
                public void onRequestSuccess() {
                    finishProcessing();
                }

                @Override
                public void onRequestError(Throwable e) {
                	
                	System.out.println("An error occured creating speech request" + e.getMessage());
                    finishProcessing();
                }
            };

            controller.startRecording(rmsListener, requestListener);
        //} else { // else we must already be in listening
          //  actionButton.setText(PROCESSING_LABEL); // go into processing mode
          //  actionButton.setEnabled(false);
           // visualizer.setIndeterminate(true);
            controller.stopRecording(); // stop the recording so the request can complete
        //}
    }
    void sendMessage(String msg)
    {
        try{
            out.writeObject(msg);
            out.flush();
            System.out.println("server>" + msg);
        }
        catch(IOException ioException){
            ioException.printStackTrace();
        }
    }
}

/*


public class Provider{
    ServerSocket providerSocket;
    Socket connection = null;
    ObjectOutputStream out;
    ObjectInputStream in;
    String message;
    Provider(){}
    void run()
    {
        try{
            //1. creating a server socket
            providerSocket = new ServerSocket(2004, 10);
            //2. Wait for connection
            System.out.println("Waiting for connection");
            connection = providerSocket.accept();
            System.out.println("Connection received from " + connection.getInetAddress().getHostName());
            //3. get Input and Output streams
            out = new ObjectOutputStream(connection.getOutputStream());
            out.flush();
            in = new ObjectInputStream(connection.getInputStream());
            sendMessage("Connection successful");
            //4. The two parts communicate via the input and output streams
            do{
                try{
                    message = (String)in.readObject();
                    System.out.println("client>" + message);
                    if (message.equals("bye"))
                        sendMessage("bye");
                }
                catch(ClassNotFoundException classnot){
                    System.err.println("Data received in unknown format");
                }
            }while(!message.equals("bye"));
        }
        catch(IOException ioException){
            ioException.printStackTrace();
        }
        finally{
            //4: Closing connection
            try{
                in.close();
                out.close();
                providerSocket.close();
            }
            catch(IOException ioException){
                ioException.printStackTrace();
            }
        }
    }
    public static void main(String args[])
    {
        Provider server = new Provider();
    }
}
*/