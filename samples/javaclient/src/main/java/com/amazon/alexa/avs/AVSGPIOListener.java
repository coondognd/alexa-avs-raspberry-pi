package com.amazon.alexa.avs;


import java.awt.event.ActionEvent;

import javax.swing.JOptionPane;

import com.amazon.alexa.avs.auth.AccessTokenListener;
import com.amazon.alexa.avs.auth.AuthSetup;
import com.amazon.alexa.avs.auth.companionservice.RegCodeDisplayHandler;
import com.amazon.alexa.avs.config.DeviceConfig;
import com.amazon.alexa.avs.config.DeviceConfigUtils;
import com.amazon.alexa.avs.http.AVSClientFactory;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory; 
import com.pi4j.io.gpio.RaspiPin;

import com.pi4j.io.gpio.GpioPinDigitalInput;

public class AVSGPIOListener implements ExpectSpeechListener, RecordingRMSListener,
RegCodeDisplayHandler, AccessTokenListener {

    private final DeviceConfig deviceConfig;
    private Thread autoEndpoint = null; // used to auto-endpoint while listening
    private static final int ENDPOINT_THRESHOLD = 5;
    private static final int ENDPOINT_SECONDS = 2; // amount of silence time before endpointing
    private final AVSController controller;
    private AuthSetup authSetup;
	

    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            new AVSGPIOListener(args[0]);
        } else {
            new AVSGPIOListener();
        }
        
    }

    public AVSGPIOListener() throws Exception {
        this(DeviceConfigUtils.readConfigFile());
    }

    public AVSGPIOListener(String configName) throws Exception {
        this(DeviceConfigUtils.readConfigFile(configName));
    }

    private AVSGPIOListener(DeviceConfig config) throws Exception {
        deviceConfig = config;
        controller = new AVSController(this, new AVSAudioPlayerFactory(), new AlertManagerFactory(),
                getAVSClientFactory(deviceConfig), DialogRequestIdAuthority.getInstance());

        authSetup = new AuthSetup(config, this);
        authSetup.addAccessTokenListener(this);
        authSetup.addAccessTokenListener(controller);
        authSetup.startProvisioningThread();

        controller.startHandlingDirectives();
        
        


        // get a handle to the GPIO controller
    	final GpioController gpio = GpioFactory.getInstance(); 
    	
    	final GpioPinDigitalInput pin = gpio.provisionDigitalInputPin(RaspiPin.GPIO_01, "PinLED");
    	
    	pin.addListener(arg0);
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
    
    public void actionPerformed(ActionEvent e) {
        controller.onUserActivity();
        //if (actionButton.getText().equals(START_LABEL)) { // if in idle mode
        //    actionButton.setText(STOP_LABEL);

            RequestListener requestListener = new RequestListener() {

                @Override
                public void onRequestSuccess() {
                    finishProcessing();
                }

                @Override
                public void onRequestError(Throwable e) {
                    log.error("An error occured creating speech request", e);
                    JOptionPane.showMessageDialog(getContentPane(), e.getMessage(), "Error",
                            JOptionPane.ERROR_MESSAGE);
                    actionButton.doClick();
                    finishProcessing();
                }
            };

            controller.startRecording(rmsListener, requestListener);
        //} else { // else we must already be in listening
          //  actionButton.setText(PROCESSING_LABEL); // go into processing mode
          //  actionButton.setEnabled(false);
            visualizer.setIndeterminate(true);
            controller.stopRecording(); // stop the recording so the request can complete
        //}
    }
}
