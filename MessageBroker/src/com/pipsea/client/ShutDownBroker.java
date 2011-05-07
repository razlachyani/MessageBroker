package com.pipsea.client;

import org.zeromq.ZMQ;

/**
 * Created by IntelliJ IDEA.
 * User: dev
 * Date: 5/8/11
 * Time: 12:04 AM
 * To change this template use File | Settings | File Templates.
 */
public class ShutDownBroker {

    public static void main(String[] args) {

        ZMQ.Context context = ZMQ.context(1);
        ZMQ.Socket clientSocket = context.socket(ZMQ.REQ);

        System.out.println("Connecting to server ...");
        clientSocket.connect("tcp://localhost:5559");
        clientSocket.setLinger(0);

        System.out.println("Sending terminate to broker");

        //for(int i =0 ; i< 3;i++){

            clientSocket.send("TERMINATE".getBytes(), 0);

//            byte [] res = clientSocket.recv(0);
//
//            if(new String(res).equals("GOT_TERMINATION")){
//
//                System.out.println("Got termination approval from broker ...");
//                break;
//            }
//        }

        clientSocket.close();
        context.term();


    }
}
