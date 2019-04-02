package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.nfc.Tag;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.widget.Button;
import android.widget.TextView;
import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnKeyListener;
import android.widget.EditText;
import android.widget.TextView;
import android.net.Uri;

import java.io.EOFException;
import java.io.IOException;
import java.io.StreamCorruptedException;
import java.net.ServerSocket;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Comparator;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 *
 */

class Msg implements Comparable <Msg>{ //Will be later used to create an datatypes of Msg Type
    private String msg;
    private float msgSeq;
    private boolean isFinalized = false;
    private String fromAvd;

    @Override
    public String toString()
    {
        return ("MsgContent: "+this.getMsg()+"; SeqNo: "+ this.getMsgSeq()+"; Finalized: "+ this.IsFinalized()+"; From Avd: "+this.getFromAvd());
    }

    public String getMsg()
    {
        return this.msg;
    }

    public void setMsg(String Message)
    {
        this.msg = Message;
    }

    public float getMsgSeq()
    {
        return this.msgSeq;
    }

    public void setMsgSeq(float messageSeq)
    {
        this.msgSeq = messageSeq;
    }

    public String getFromAvd() { return this.fromAvd; }

    public void setFromAvd(String fromAvd) {this.fromAvd = fromAvd;}

    public void setFinalized(boolean flag)
    {
        this.isFinalized = flag;
    }

    public boolean IsFinalized()
    {
        return this.isFinalized;
    }

    @Override
    public int compareTo(Msg o)
    {
        // TODO Auto-generated method stub
        if (this.equals(o))
            return 0;
        else if (this.msgSeq > o.getMsgSeq())
            return 1;
        else
            return -1;
    }

}


public class GroupMessengerActivity extends Activity {

    private static final String TAG = GroupMessengerActivity.class.getSimpleName();
    private static ArrayList <Integer> REMOTE_PORTS = new ArrayList <Integer>();
    private static String myPort = null;
    private static final int SERVER_PORT = 10000;
    private static final String S = "S"; // Flag seeking proposal from Server
    private static final String RP = "R"; // Proposal from server
    private static final String F = "F"; // Flag for final msg broadcast
    private static final String C = "C"; // Flag for broadcasting Crash Even
    private static int sc = 0; // sc is Proposal Counter
    private static int c = 0; //Insertion Counter for Content Provider
    private static int no_of_avds;
    private static ConcurrentHashMap<String, List<Float>> s_buff = new ConcurrentHashMap<String, List<Float>>(); // Buffer for Proposals for Self Generated Messages
    private static PriorityQueue <Msg> dqueue = new PriorityQueue<Msg>(); //Holdback Queue for storing proposals
    private static PriorityQueue<Msg> finalq = new PriorityQueue<Msg>(); //Delivery Queue of Confirmed messages to deliver
    Uri providerUri = Uri.parse("content://edu.buffalo.cse.cse486586.groupmessenger2.provider");


    public void set_remote_ports()
    {
        int start=11108, end=11124;

        for (int i = start; i <= end; i+=4)
        {
            REMOTE_PORTS.add(i);
        }

        no_of_avds = REMOTE_PORTS.size();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        /*
         * Calculate the port number that this AVD listens on.
         * It is just a hack that I came up with to get around the networking limitations of AVDs.
         * The explanation is provided in the PA1 spec.
         */

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        set_remote_ports(); //Sets Remote Ports of AVD's

        Log.d(TAG, "Remote ports Obtained");

        Log.v("URI", providerUri.toString());

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        final TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);

            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);

        } catch (IOException e) {
            Log.e(TAG, "Can't Create a ServerSocket");
            Log.e(TAG, e.getMessage());
            Log.e(TAG, Log.getStackTraceString(e));
            return;
        }

        final Button b = (Button) findViewById(R.id.button4); //Refers to Send Button
        final EditText editText = (EditText) findViewById(R.id.editText1); //Text that is typed

        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = editText.getText().toString();
                String msg_structure = S + ";" + Integer.toString(sc) + ";" + msg + ";" + myPort;
                editText.setText(""); //Reset
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg_structure); //msg_structure contains delimited string for C.T
            }
        });
    }


    public void sendMsgCT(String msgToSend){
        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msgToSend);
    }


    public void upon_crash(int failed_port) //Failure Handling
    {
        if (failed_port != -1)
        {
            PriorityQueue<Msg> temp = new PriorityQueue<Msg>(dqueue); //Will keep hold of all msgs that have been assigned a sequence.
            String key;
            Float max;

            REMOTE_PORTS.remove(Integer.valueOf(failed_port));
            no_of_avds = REMOTE_PORTS.size();
            Log.d(TAG, "Main: " + myPort + " Removed Port " + failed_port);

            for (Msg m : temp) //Cleaning up the state related to the failed avd
            {
                if (m.getFromAvd().equals(Integer.toString(failed_port))) {
                    dqueue.remove(m);
                    Log.d(TAG, "Main: " + myPort + " Message " + m.getMsg() + " from " + m.getFromAvd() + " removed from Holdback queue");
                }
            }

            for (Msg m : dqueue) //Checking whether any message received>=4 proposals
            {
                key = m.getMsg();
                List<Float> sequence = s_buff.get(key);
                if (sequence !=null && sequence.size() >= no_of_avds)
                {
                    max = Collections.max(sequence); //Fetching Max Sequence Number from all proposals
                    String msgToSend = F + ";" + max.toString() + ";" + key + ";" + m.getFromAvd();
                    Log.d(TAG, "Main: "+myPort+" Message: "+msgToSend+" can be Delivered");
                    sendMsgCT(msgToSend);
                    Log.d(TAG, "Main: " + myPort + " Sent Final Msg: " + msgToSend + " to all avds");
                    s_buff.remove(key);
                }
            }
        }
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void>{

        @Override
        protected Void doInBackground(ServerSocket... sockets)
        {
            ServerSocket serverSocket = sockets[0];
            String msgFromClient, msgToSend;
            String [] pieces;
            int sender_port = -1;

            while (true) {
                try {
                        Socket server = serverSocket.accept();
                        server.setSoTimeout(500);
                        InputStreamReader input = new InputStreamReader(server.getInputStream());
                        BufferedReader in = new BufferedReader(input);

                        msgFromClient = in.readLine();
                        pieces = msgFromClient.split(";"); //Splits msg_structure as per delim chunks
                        sender_port = Integer.parseInt(pieces[3]); // This will be Message Sender's port.

                        Log.d(TAG, "Server: " +myPort+ " Input Msg Received: " + msgFromClient);

                        if (msgFromClient != null) {
                            PrintWriter pw = new PrintWriter(server.getOutputStream(), true);
                            pw.println(msgFromClient);
                            Log.d(TAG, "Server: " +myPort+ " Sent Ack to Client: "+msgFromClient);
                        }


                        if (pieces[0].equals(S)) // Incoming message is a Fresh Message Seeking a Proposal
                        {
                            Log.d(TAG, "Server: " +myPort+ " Message: " +msgFromClient+ " from avd "+pieces[3]+" is a New Message");
                            msgToSend = RP + ";" + Integer.toString(++sc) + "." + myPort + ";" + pieces[2] + ";" + pieces[3]; //Flag;counter.localport(Seq.No);actual message;Senderport
                            Msg m = new Msg();
                            m.setMsgSeq(Float.parseFloat(Integer.toString(sc) + "." + myPort)); //Suggested Sequence Number. Store my proposals to the avd's message.
                            m.setMsg(pieces[2]); //Msg Content
                            m.setFromAvd(pieces[3]); // Sender's Port from where Msg Originated
                            dqueue.add(m);
                            sendMsgCT(msgToSend);
                            Log.d(TAG, "Server: " +myPort+ " Sent Proposal: " +msgToSend+ " to avd "+pieces[3]);
                        }

                        else
                        {
                            if (pieces[0].equals(RP)) // Incoming message is a Proposal
                            {
                                Log.d(TAG, "Server: " +myPort+ " Message: " +msgFromClient+ " from avd "+pieces[3]+"is a Proposal");
                                List <Float> sequences = s_buff.get(pieces[2]);

                                if (sequences == null) // First Proposal
                                {    // https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ConcurrentHashMap.html
                                    sequences = new ArrayList<Float>();
                                    sequences.add(Float.parseFloat(pieces[1]));
                                    s_buff.put(pieces[2], sequences);
                                }

                                else
                                {
                                    sequences.add(Float.parseFloat(pieces[1]));
                                }

                                String currentKey = pieces[2];
                                List <Float> sequenceList = s_buff.get(currentKey);
                                if (sequenceList.size() >= no_of_avds)
                                {
                                    Float max = Collections.max(sequenceList); //Fetching Max Sequence Number from all proposals
                                    Log.d(TAG, "Server: " +myPort+ " Max Generated for Msg " +msgFromClient);
                                    msgToSend = F + ";" + max.toString() + ";" + pieces[2] + ";" + pieces[3];
                                    sendMsgCT(msgToSend);
                                    Log.d(TAG, "Server: " +myPort+ " Sent Final Msg: " +msgToSend+" to all avds");
                                    s_buff.remove(currentKey);
                                }
                            }

                            else //Incoming message is a declaration of a final sequence (pieces[0].equals(F))
                            {
                                Log.d(TAG, "Server: " + myPort + " Message: " + msgFromClient + " from avd " + pieces[3] + " is a Final Message");

                                for (Msg m : dqueue) {
                                    if (m.getMsg().equals(pieces[2])) {
                                        Log.d(TAG, "Server: " + myPort + " Message " + m.getMsg() + " located in Holdback Queue");
                                        dqueue.remove(m);
                                        Log.d(TAG, "Server: " + myPort + " Removed Msg " + m.toString());
                                        break;
                                    }
                                }

                                Msg m = new Msg();
                                m.setMsgSeq(Float.parseFloat(pieces[1])); //Suggested Sequence Number
                                m.setMsg(pieces[2]); //Msg Content
                                m.setFromAvd(pieces[3]); // Sender's Port from where Msg Originated
                                m.setFinalized(true);
                                dqueue.add(m);

                                Log.d(TAG, "Server: " + myPort + " Current Holdback Queue " + dqueue.toString());

                                while (!dqueue.isEmpty()) {
                                    Msg p = dqueue.peek();

                                    if (!p.IsFinalized()) {
                                        Log.d(TAG, "Awaiting Final Sequence No. for " + p.getMsg());
                                        break;
                                    }

                                    finalq.add(p);
                                    dqueue.remove(p);
                                }

                                while (!finalq.isEmpty()) //All messages in finalq are deliverable
                                {
                                    Msg p = finalq.poll();
                                    publishProgress(p.getMsg());
                                    Log.d(TAG, "Server: " + myPort + " Delivered Message " + p.getMsg());
                                }
                            }
                        }
                    }

                catch (NullPointerException e)
                {
                    Log.e(TAG, "Server: " +myPort+ " Null Pointer Exception Occurred. Avd= "+Integer.toString(sender_port)+" has crashed");
                    e.printStackTrace();
                    upon_crash(sender_port);
                }

                catch (SocketTimeoutException e)
                {
                    Log.e(TAG, "Server: " +myPort+ " Socket Timeout Occurred. Avd= "+Integer.toString(sender_port)+" has crashed");
                    e.printStackTrace();
                    upon_crash(sender_port);
                }

                catch (SocketException e)
                {
                    Log.e(TAG, "Server: " +myPort+ " Unable to Create a Socket. Avd= "+Integer.toString(sender_port)+" has crashed");
                    e.printStackTrace();
                    upon_crash(sender_port);
                }

                catch (IOException e)
                {
                    Log.e(TAG, "Server: " +myPort+ " IO Exception Occurred. Avd= "+Integer.toString(sender_port)+" has crashed");
                    e.printStackTrace();
                    upon_crash(sender_port);
                }

                catch (Exception e)
                {
                    Log.e(TAG, "Server: " +myPort+ " Exception Occurred. Avd= "+Integer.toString(sender_port)+" has crashed");
                    e.printStackTrace();
                    upon_crash(sender_port);
                }
            }
        }


        protected void onProgressUpdate(String... strings)
        {
            String msgReceived = strings[0].trim(); //Actual Message
            TextView tv = (TextView) findViewById(R.id.textView1);
            tv.append(msgReceived+"\t\n");

            //Inserting Key and Value
            Log.d(TAG, "Server: " +myPort+ " Inserting Msg: " + Integer.toString(c)+" -> "+msgReceived);
            ContentValues keyValueToInsert = new ContentValues();
            keyValueToInsert.put("key", Integer.toString(c++));
            keyValueToInsert.put("value", msgReceived);
            Uri newUri = getContentResolver().insert(providerUri, keyValueToInsert);
            Log.d("Insertion Successful", newUri.toString());
        }
    }

    private class ClientTask extends AsyncTask<String, Void, Void>
    {
        @Override
        protected Void doInBackground(String... msgs)
        {
            String msgToSend = msgs[0]; //Gets Delimited String
            String [] chunks = msgToSend.split(";");
            ArrayList <Integer> ports = new ArrayList<Integer>(REMOTE_PORTS);
            Socket client=null;
            InputStreamReader ip=null;

            if (chunks[0].equals(RP)) //send message to sender only
            {
                ports.clear();
                ports.add(Integer.parseInt(chunks[3]));
            }

            if (chunks[0].equals(C)) //send message to all except self and failed avd
            {
                ports.remove(Integer.valueOf(chunks[2]));  //sender avd which detected failure
            }

            for (Integer i : ports)
                {
                    try
                    {
                        client = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), i);
                        client.setSoTimeout(500);

                        PrintWriter output = new PrintWriter(client.getOutputStream(), true);
                        Log.d(TAG, "Client: " +myPort+ " Message to Send: "+msgToSend+"; Ports: "+ports.toString());
                        output.println(msgToSend);
                        Log.d(TAG, "Client: " +myPort+ " Sends to Server: " + Integer.toString(i)+ " Msg: " +msgToSend);
                        output.flush();

                        ip = new InputStreamReader(client.getInputStream());
                        BufferedReader bf = new BufferedReader(ip);
                        String recvd = bf.readLine();

                        while (!recvd.equals(msgToSend)) {
                            continue;
                        }

                        Log.d(TAG, "Client: " +myPort+ " receives Ack: " +recvd+ " from Server " + Integer.toString(i));
                        Log.d(TAG, "Client: " +myPort+ " Client Socket Closed");
                    }

                    catch (SocketTimeoutException e)
                    {
                        Log.e(TAG, "Client: " +myPort+ " Socket Timeout Occurred. Avd= "+Integer.toString(i)+" has crashed");
                        e.printStackTrace();
                        upon_crash(i);
                    }

                    catch (SocketException e)
                    {
                        Log.e(TAG, "Client: " +myPort+ " Unable to Create a Socket. Avd= "+Integer.toString(i)+" has crashed");
                        e.printStackTrace();
                        upon_crash(i);
                    }

                    catch (IOException e)
                    {
                        Log.e(TAG, "Client: " +myPort+ " IO Exception Occurred. Avd= "+Integer.toString(i)+" has crashed");
                        e.printStackTrace();
                        upon_crash(i);
                    }

                    catch (NullPointerException e)
                    {
                        Log.e(TAG, "Client: " +myPort+ " IO Exception Occurred. Avd= "+Integer.toString(i)+" has crashed");
                        e.printStackTrace();
                        upon_crash(i);
                    }

                    catch (Exception e)
                    {
                        Log.e(TAG, "Client: " +myPort+ " IO Exception Occurred. Avd= "+Integer.toString(i)+" has crashed");
                        e.printStackTrace();
                        upon_crash(i);
                    }

                    finally {

                        try {
                                ip.close();
                                client.close();
                            }

                        catch(Exception e)
                        {
                            Log.e(TAG, "Client: "+myPort+" Error closing Client Socket while sending message");
                            e.printStackTrace();
                        }
                    }

                }

            return null;
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }
}
