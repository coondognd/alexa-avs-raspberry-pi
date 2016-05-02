package com.amazon.alexa.avs;


import com.amazon.alexa.avs.auth.AccessTokenListener;
import com.amazon.alexa.avs.auth.AuthSetup;
import com.amazon.alexa.avs.auth.companionservice.RegCodeDisplayHandler;
import com.amazon.alexa.avs.config.DeviceConfig;
import com.amazon.alexa.avs.config.DeviceConfigUtils;
import com.amazon.alexa.avs.http.AVSClientFactory;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory; 
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;

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
    	
    	final GpioPinDigitalInput myButton = gpio.provisionDigitalInputPin(RaspiPin.GPIO_18, PinPullResistance.PULL_DOWN);
        // create and register gpio pin listener
    	
        myButton.addListener(new GpioPinListenerDigital() {
            @Override
            public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event) {
                // display pin state on console
                System.out.println(" --> GPIO PIN STATE CHANGE: " + event.getPin() + " = " + event.getState());
                actionPerformed(event);
            }
            
        });
        System.out.println(" ... complete the GPIO #02 circuit and see the listener feedback here in the console.");
        
        // keep program running until user aborts (CTRL-C)
        for (;;) {
            Thread.sleep(500);
        }
        
        // stop all GPIO activity/threads by shutting down the GPIO controller
        // (this method will forcefully shutdown all GPIO monitoring threads and scheduled tasks)
        // gpio.shutdown();   <--- implement this method call if you wish to terminate the Pi4J GPIO controller  
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

        try
        {
            System.in.read();
        }
        catch(Exception e)
        {}
    }

    @Override
    public synchronized void onAccessTokenReceived(String accessToken) {
    	System.out.println("Received token: " + accessToken);
    }
    

    public void finishProcessing() {
        controller.processingFinished();

    }
    
    public void actionPerformed(GpioPinDigitalStateChangeEvent event) {
        final RecordingRMSListener rmsListener = this;
        controller.onUserActivity();
        //if (actionButton.getText().equals(START_LABEL)) { // if in idle mode
        if (event.getState() == PinState.HIGH) {
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
        } else { // else we must already be in listening
          //  actionButton.setText(PROCESSING_LABEL); // go into processing mode
          //  actionButton.setEnabled(false);
           // visualizer.setIndeterminate(true);
            controller.stopRecording(); // stop the recording so the request can complete
        }
    }
}
