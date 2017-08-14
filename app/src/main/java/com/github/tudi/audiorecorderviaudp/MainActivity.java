package com.github.tudi.audiorecorderviaudp;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.NoiseSuppressor;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import org.xiph.speex.SpeexDecoder;
import org.xiph.speex.SpeexEncoder;
import android.view.View;
import android.widget.Button;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class MainActivity extends AppCompatActivity {
    private static String TAG = "AudioClient";

    // the server information
    private static final String SERVER = "192.168.8.205";
    private static final int PORT = 50005;
    SpeexEncoder encoder = new SpeexEncoder();
    SpeexDecoder decoder = new SpeexDecoder();
    // the audio recording options
    private static final int RECORDING_RATE = 8000;
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int MY_PERMISSIONS_REQUEST_READ_CONTACTS = 100;
    private int raw_block_size;
    // the button the user presses to send the audio stream to the server
    private Button sendAudioButton;

    // the audio recorder
    private AudioRecord recorder;

    // the minimum buffer size needed for audio recording
    private static int BUFFER_SIZE =10240;//AudioRecord.getMinBufferSize(RECORDING_RATE, CHANNEL, FORMAT);
    private  boolean FLAG =false;
    // are we currently sending audio data
    private boolean currentlySendingAudio = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        encoder.init(1, 1, RECORDING_RATE, CHANNEL);
        decoder.init(1, RECORDING_RATE, CHANNEL, false);
         raw_block_size =5120; //encoder.getFrameSize() * (16/ 8);

        if (Build.VERSION.SDK_INT >= 23) {

            ///**********API23 Android version 6.0 up

            // Here, thisActivity is the current activity
            if (ContextCompat.checkSelfPermission(MainActivity.this,
                    Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED ) {
            /*if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                    Manifest.permission.RECORD_AUDIO)) {*/
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage("我真的沒有要做壞事, 給我權限吧?")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ActivityCompat.requestPermissions(MainActivity.this,
                                        new String[]{Manifest.permission.RECORD_AUDIO},
                                        MY_PERMISSIONS_REQUEST_READ_CONTACTS);
                            }
                        })
                        .setNegativeButton("No", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        })
                        .show();
           /* } else {

                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        MY_PERMISSIONS_REQUEST_READ_CONTACTS);
            }*/
            }

//********


        }
        RecvAudio();


        Log.i(TAG, "Creating the Audio Client with minimum buffer of "
                + BUFFER_SIZE + " bytes");

        // set up the button
        sendAudioButton = (Button) findViewById(R.id.start_button);
        sendAudioButton.setOnClickListener(new View.OnClickListener() {


            @Override
            public void onClick(View view) {
                FLAG=!FLAG;
                if (FLAG)startStreamingAudio();
                else stopStreamingAudio();
                sendAudioButton.setText(((FLAG)?"(SEND.)":"(STOP)"));
            }
        });

    }

    private void startStreamingAudio() {

        Log.i(TAG, "Starting the audio stream");
        currentlySendingAudio = true;
        startStreaming();
    }

    private void stopStreamingAudio() {

        Log.i(TAG, "Stopping the audio stream");
        currentlySendingAudio = false;
        recorder.release();
    }

    private void startStreaming() {

        Log.i(TAG, "Starting the background thread to stream the audio data");

        Thread streamThread = new Thread(new Runnable() {

            @Override
            public void run() {
                try {

                    Log.d(TAG, "Creating the datagram socket");
                    DatagramSocket socket = new DatagramSocket();

                    Log.d(TAG, "Creating the buffer of size " + BUFFER_SIZE);
                    byte[] buffer = new byte[BUFFER_SIZE];

                    Log.d(TAG, "Connecting to " + SERVER + ":" + PORT);
                    final InetAddress serverAddress = InetAddress
                            .getByName(SERVER);
                    Log.d(TAG, "Connected to " + SERVER + ":" + PORT);

                    Log.d(TAG, "Creating the reuseable DatagramPacket");
                    DatagramPacket packet;

                    Log.d(TAG, "Creating the AudioRecord");
                    recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                            RECORDING_RATE, CHANNEL, FORMAT, BUFFER_SIZE * 10);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        if (NoiseSuppressor.isAvailable()){
                            NoiseSuppressor.create(recorder.getAudioSessionId());
                        }
                    }
                    Log.d(TAG, "AudioRecord recording...");
                    recorder.startRecording();

                    while (currentlySendingAudio == true) {

                        // read the data into the buffer
                        int read = recorder.read(buffer, 0, buffer.length);
                        // place contents of buffer into the packet
                        if(encoder.processData(buffer, 0, BUFFER_SIZE)){
                        read = encoder.getProcessedData(buffer,0);
                        packet = new DatagramPacket(buffer, read,
                                serverAddress, PORT);

                        // send the packet
                        socket.send(packet);}
                    }

                    Log.d(TAG, "AudioRecord finished recording");

                } catch (Exception e) {
                    Log.e(TAG, "Exception: " + e);
                }
            }
        });

        // start the thread
        streamThread.start();
    }

    public void RecvAudio()
    {
        Thread thrd = new Thread(new Runnable() {
            @Override
            public void run()
            {
                Log.e(TAG, "start recv thread, thread id: "
                        + Thread.currentThread().getId());
                AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC,
                        RECORDING_RATE, AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, BUFFER_SIZE,
                        AudioTrack.MODE_STREAM);
                track.play();
                try
                {
                    DatagramSocket sock = new DatagramSocket(PORT);
                    byte[] buf = new byte[240];
                    Log.i(TAG, "success create socket on "+PORT);

                    while(true)
                    {

                        DatagramPacket pack = new DatagramPacket(buf, 240);
                        sock.receive(pack);
                        Log.d(TAG, "recv pack: " + pack.getLength());
                        decoder.processData(buf,0,240);
                        byte[] decoded_data = new byte[decoder.getProcessedDataByteSize()];
                        int decoded = decoder.getProcessedData(decoded_data, 0);
                        if (FLAG) track.write(decoded_data, 0, decoded);
                    }
                }
                catch (SocketException se)
                {
                    Log.e(TAG, "SocketException: " + se.toString());
                }
                catch (IOException ie)
                {
                    Log.e(TAG, "IOException" + ie.toString());
                }
            } // end run
        });
        thrd.start();

    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_READ_CONTACTS: {

                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    Log.i(TAG,"thak you ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
                    //RecvAudio();
                } else {
                    //finish();
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }


}